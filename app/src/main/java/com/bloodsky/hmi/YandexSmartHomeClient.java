package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Yandex Smart Home user API adapter for discovery and guarded scenario launch. */
public final class YandexSmartHomeClient {
    private static final String API = "https://api.iot.yandex.net/v1.0";
    private YandexSmartHomeClient() {}

    public static final class Item {
        public final String id, name;
        public final boolean scenario;
        Item(String id, String name, boolean scenario) { this.id=id; this.name=name; this.scenario=scenario; }
        @Override public String toString() { return (scenario ? "Сценарий · " : "Устройство · ") + name; }
    }

    public static List<Item> getHome(String token) throws Exception {
        requireToken(token);
        JSONObject root = new JSONObject(request(API + "/user/info", token, false));
        if (!"ok".equalsIgnoreCase(root.optString("status"))) throw new IllegalStateException("Yandex Smart Home API вернул ошибку");
        return parseHome(root);
    }

    public static List<Item> parseHome(JSONObject root) {
        ArrayList<Item> result = new ArrayList<>();
        appendItems(result, root == null ? null : root.optJSONArray("devices"), false);
        appendItems(result, root == null ? null : root.optJSONArray("scenarios"), true);
        Collections.sort(result, Comparator.comparing((Item item) -> item.scenario).thenComparing(item -> item.name.toLowerCase(Locale.ROOT)));
        return result;
    }

    private static void appendItems(List<Item> result, JSONArray array, boolean scenario) {
        for (int i=0; array != null && i<array.length(); i++) {
            JSONObject item=array.optJSONObject(i); if(item==null) continue;
            String id=item.optString("id", "").trim();
            if (id.isEmpty() || id.length()>512) continue;
            String name=item.optString("name", "").trim(); if(name.isEmpty()) name=id;
            result.add(new Item(id,name,scenario));
        }
    }

    public static boolean isValidScenarioId(String id) {
        return id != null && id.matches("[A-Za-z0-9_.:-]{1,512}");
    }

    public static void launchScenario(String token, String scenarioId) throws Exception {
        requireToken(token);
        if (!isValidScenarioId(scenarioId)) throw new IllegalArgumentException("Некорректный идентификатор сценария");
        JSONObject response=new JSONObject(request(API + "/scenarios/" + scenarioId + "/actions", token, true));
        if (!"ok".equalsIgnoreCase(response.optString("status")))
            throw new IllegalStateException("Yandex Smart Home не запустил сценарий");
    }

    private static void requireToken(String token) {
        if(token==null || token.trim().isEmpty() || token.length()>8192 || token.contains("\n") || token.contains("\r"))
            throw new IllegalArgumentException("Укажи OAuth-токен Yandex Smart Home");
    }

    private static String request(String endpoint, String token, boolean post) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(endpoint).openConnection();
        connection.setRequestMethod(post ? "POST" : "GET");
        connection.setConnectTimeout(7000); connection.setReadTimeout(10000); connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Authorization", "Bearer " + token.trim());
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status=connection.getResponseCode();
            InputStream stream=status>=400 ? connection.getErrorStream() : connection.getInputStream();
            if(stream==null) throw new IllegalStateException("Yandex Smart Home HTTP " + status);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream input=stream) {
                byte[] buffer=new byte[4096]; int count;
                while((count=input.read(buffer))!=-1) {
                    if(bytes.size()+count>2*1024*1024) throw new IllegalStateException("Слишком большой ответ Yandex Smart Home");
                    bytes.write(buffer,0,count);
                }
            }
            String body=new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            if(status<200 || status>=300) throw new IllegalStateException("Yandex Smart Home HTTP " + status);
            return body;
        } finally { connection.disconnect(); }
    }
}
