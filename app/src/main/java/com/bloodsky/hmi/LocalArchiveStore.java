package com.bloodsky.hmi;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Device-local history for explicitly configured Craft archive widgets. */
public final class LocalArchiveStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "bloodsky_local_archive.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "archive_samples";

    public static final class Sample {
        public final long timestamp;
        public final String value;
        Sample(long timestamp, String value) { this.timestamp = timestamp; this.value = value; }
    }

    public LocalArchiveStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " project_id TEXT NOT NULL, widget_key TEXT NOT NULL,"
                + " sampled_at INTEGER NOT NULL, value TEXT NOT NULL)");
        db.execSQL("CREATE INDEX archive_lookup ON " + TABLE
                + " (project_id, widget_key, sampled_at DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is the first local archive schema; future upgrades must preserve history.
    }

    /** Inserts at most once per interval and keeps a bounded number of rows per widget. */
    public boolean record(String projectId, String widgetKey, long timestamp, String value,
                          int intervalSeconds, int maxRows) {
        if (blank(projectId) || blank(widgetKey) || blank(value)
                || value.startsWith("ERR") || value.startsWith("CFG")) return false;
        int interval = Math.max(1, Math.min(86400, intervalSeconds));
        int limit = Math.max(100, Math.min(100000, maxRows));
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long previous = -1L;
            try (Cursor cursor = db.rawQuery("SELECT MAX(sampled_at) FROM " + TABLE
                    + " WHERE project_id=? AND widget_key=?", new String[]{projectId, widgetKey})) {
                if (cursor.moveToFirst() && !cursor.isNull(0)) previous = cursor.getLong(0);
            }
            if (previous >= 0L && timestamp - previous < interval * 1000L) return false;
            ContentValues row = new ContentValues();
            row.put("project_id", projectId);
            row.put("widget_key", widgetKey);
            row.put("sampled_at", timestamp);
            row.put("value", value);
            db.insertOrThrow(TABLE, null, row);
            db.execSQL("DELETE FROM " + TABLE + " WHERE project_id=? AND widget_key=? AND _id NOT IN "
                    + "(SELECT _id FROM " + TABLE + " WHERE project_id=? AND widget_key=? "
                    + "ORDER BY sampled_at DESC, _id DESC LIMIT ?)",
                    new Object[]{projectId, widgetKey, projectId, widgetKey, limit});
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public List<Sample> latest(String projectId, String widgetKey, int count) {
        List<Sample> samples = new ArrayList<>();
        if (blank(projectId) || blank(widgetKey)) return samples;
        int limit = Math.max(1, Math.min(500, count));
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT sampled_at,value FROM " + TABLE
                + " WHERE project_id=? AND widget_key=? ORDER BY sampled_at DESC,_id DESC LIMIT ?",
                new String[]{projectId, widgetKey, Integer.toString(limit)})) {
            while (cursor.moveToNext()) samples.add(new Sample(cursor.getLong(0), cursor.getString(1)));
        }
        return samples;
    }

    public void writeCsv(String projectId, String widgetKey, OutputStream output) throws IOException {
        if (output == null) throw new IOException("CSV output stream unavailable");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, "UTF-8"));
             Cursor cursor = getReadableDatabase().rawQuery("SELECT sampled_at,value FROM " + TABLE
                     + " WHERE project_id=? AND widget_key=? ORDER BY sampled_at ASC,_id ASC",
                     new String[]{projectId, widgetKey})) {
            writer.write("timestamp,value\n");
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
            while (cursor.moveToNext()) {
                writer.write(csv(format.format(new Date(cursor.getLong(0)))));
                writer.write(',');
                writer.write(csv(cursor.getString(1)));
                writer.write('\n');
            }
            writer.flush();
        }
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
