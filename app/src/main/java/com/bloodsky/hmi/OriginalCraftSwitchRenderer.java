package com.bloodsky.hmi;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseArray;

import org.json.JSONObject;

/** Renders the original eight on/off image pairs from Craft 1.0.7. */
public final class OriginalCraftSwitchRenderer {
    private static final SparseArray<Bitmap> BITMAPS = new SparseArray<>();
    private OriginalCraftSwitchRenderer() { }

    public static void draw(Canvas canvas, Resources resources, RectF bounds,
                            JSONObject widgetFields, boolean on, Paint paint) {
        String name = OriginalCraftSwitch.drawableName(widgetFields, on);
        int resource = resources.getIdentifier(name, "drawable", "com.bloodsky.hmi");
        if (resource == 0) return;
        Bitmap bitmap;
        synchronized (BITMAPS) {
            bitmap = BITMAPS.get(resource);
            if (bitmap == null || bitmap.isRecycled()) {
                bitmap = BitmapFactory.decodeResource(resources, resource);
                if (bitmap != null) BITMAPS.put(resource, bitmap);
            }
        }
        if (bitmap == null) return;
        boolean filter = paint.isFilterBitmap();
        paint.setFilterBitmap(true);
        canvas.drawBitmap(bitmap, null, bounds, paint);
        paint.setFilterBitmap(filter);
    }
}
