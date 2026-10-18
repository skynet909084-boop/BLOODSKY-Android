package com.bloodsky.hmi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Read-only Home Assistant REST adapter. Credentials and host are never logged. */
public final class HomeAssistantClient {
    private HomeAssistantClient() {}

    public static final class Entity {
        public final String entityId;
        public final String name;
        public final String state;
        public Entity(String entityId, String name, String state) {
            this.entityId = entityId; this.name = name; this.state = state;
        }
        public boolean canTogglePower() { return HomeAssistantClient.canTogglePower(entityId, state); }
        @Override public String toString() { return name + " — " + state + "  (" + entityId + ")"; }
    }

    public static String normalizeBaseUrl(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Укажи адрес Home Assistant");
        String raw = value.trim();
        if (!raw.contains("://")) raw = "http://" + raw;
        try {
            URI uri = URI.create(raw);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if (!("http".equals(scheme) || "https".equals(scheme)) || host == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())))
                throw new IllegalArgumentException("Нужен адрес вида http://homeassistant.local:8123 или https://…");
            if ("http".equals(scheme) && !isLocalHost(host))
                throw new IllegalArgumentException("Для нелокального адреса требуется HTTPS");
            int port = uri.getPort();
            if (port < -1 || port > 65535 || port == 0) throw new IllegalArgumentException("Некорректный порт");
            return scheme + "://" + host + (port < 0 ? "" : ":" + port);
        } catch (IllegalArgumentException invalid) {
            if (invalid.getMessage() != null && invalid.getMessage().startsWith("Для ")) throw invalid;
            throw new IllegalArgumentException(invalid.getMessage() == null ? "Некорректный адрес Home Assistant" : invalid.getMessage());
        }
    }

    private static boolean isLocalHost(String host) {
        String name = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(name) || name.endsWith(".local")) return true;
        String[] p = name.split("\\.");
        if (p.length != 4) return false;
        try {
            int a = Integer.parseInt(p[0]), b = Integer.parseInt(p[1]), c = Integer.parseInt(p[2]), d = Integer.parseInt(p[3]);
            if (a < 0 || a > 255 || b < 0 || b > 255 || c < 0 || c > 255 || d < 0 || d > 255) return false;
            return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b >= 16 && b <= 31);
        } catch (NumberFormatException invalid) { return false; }
    }

    public static String testConnection(String baseUrl, String token) throws Exception {
        requireToken(token);
        HttpResult response = request(normalizeBaseUrl(baseUrl) + "/api/", token);
        JSONObject json = new JSONObject(response.body);
        if (response.status != 200 || !"API running.".equals(json.optString("message")))
            throw new IllegalStateException("Home Assistant API не подтвердил соединение (HTTP " + response.status + ")");
        return json.optString("message");
    }

    public static List<Entity> getEntities(String baseUrl, String token) throws Exception {
        requireToken(token);
        HttpResult response = request(normalizeBaseUrl(baseUrl) + "/api/states", token);
        if (response.status != 200) throw new IllegalStateException("Home Assistant вернул HTTP " + response.status);
        return parseEntities(new JSONArray(response.body));
    }

    public static boolean canTogglePower(String entityId, String state) {
        if (entityId == null || state == null) return false;
        String[] parts = entityId.split("\\.", 2);
        if (parts.length != 2 || !("light".equals(parts[0]) || "switch".equals(parts[0])
                || "fan".equals(parts[0]) || "input_boolean".equals(parts[0]))) return false;
        return "on".equalsIgnoreCase(state) || "off".equalsIgnoreCase(state);
    }

    public static void setPower(String baseUrl, String token, Entity entity, boolean turnOn) throws Exception {
        requireToken(token);
        if (entity == null || !entity.canTogglePower()) throw new IllegalArgumentException("Это устройство нельзя безопасно включить или выключить");
        String domain = entity.entityId.substring(0, entity.entityId.indexOf('.'));
        String service = turnOn ? "turn_on" : "turn_off";
        JSONObject payload = new JSONObject().put("entity_id", entity.entityId);
        HttpResult response = request(normalizeBaseUrl(baseUrl) + "/api/services/" + domain + "/" + service,
                token, "POST", payload.toString());
        if (response.status < 200 || response.status >= 300)
            throw new IllegalStateException("Home Assistant вернул HTTP " + response.status);
    }

    public static List<Entity> parseEntities(JSONArray states) {
        ArrayList<Entity> result = new ArrayList<>();
        for (int i = 0; states != null && i < states.length(); i++) {
            JSONObject item = states.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("entity_id", "").trim();
            if (!id.matches("[a-z0-9_]+\\.[a-z0-9_]+")) continue;
            JSONObject attrs = item.optJSONObject("attributes");
            String name = attrs == null ? "" : attrs.optString("friendly_name", "").trim();
            if (name.isEmpty()) name = id;
            String state = item.optString("state", "unknown");
            result.add(new Entity(id, name, state));
        }
        Collections.sort(result, Comparator.comparing(e -> e.name.toLowerCase(Locale.ROOT)));
        return result;
    }

    private static void requireToken(String token) {
        if (token == null || token.trim().isEmpty() || token.length() > 8192 || token.contains("\n") || token.contains("\r"))
            throw new IllegalArgumentException("Укажи корректный Home Assistant access token");
    }

    private static HttpResult request(String endpoint, String token) throws Exception {
        return request(endpoint, token, "GET", null);
    }

    private static HttpResult request(String endpoint, String token, String method, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Authorization", "Bearer " + token.trim());
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (java.io.OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) return new HttpResult(status, "{}");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = stream) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > 3 * 1024 * 1024) throw new IllegalStateException("Слишком большой ответ Home Assistant");
                    bytes.write(buffer, 0, count);
                }
            }
            return new HttpResult(status, new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } finally { connection.disconnect(); }
    }

    private static final class HttpResult {
        final int status; final String body;
        HttpResult(int status, String body) { this.status = status; this.body = body; }
    }
}
