package com.bloodsky.hmi;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Minimal Telegram Bot API sender. Tokens stay in app preferences, never in project exports. */
public final class TelegramBotClient {
    private TelegramBotClient() {}

    public static boolean isValidToken(String token) {
        if (token == null || token.length() > 128) return false;
        return token.matches("[0-9]{5,15}:[A-Za-z0-9_-]{20,80}");
    }

    public static boolean isValidChatId(String chatId) {
        if (chatId == null) return false;
        return chatId.matches("-?[0-9]{1,20}") || chatId.matches("@[A-Za-z0-9_]{5,32}");
    }

    public static String requestJson(String chatId, String message) {
        if (!isValidChatId(chatId)) throw new IllegalArgumentException("Некорректный Telegram chat_id");
        if (message == null || message.trim().isEmpty() || message.length() > 4096)
            throw new IllegalArgumentException("Сообщение Telegram должно содержать 1–4096 символов");
        try {
            return new JSONObject().put("chat_id", chatId).put("text", message)
                    .put("disable_web_page_preview", true).toString();
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Performs one bounded HTTPS request. Error messages intentionally never include the token. */
    public static void sendMessage(String token, String chatId, String message) throws IOException {
        if (!isValidToken(token)) throw new IllegalArgumentException("Некорректный токен Telegram-бота");
        String payload = requestJson(chatId, message);
        HttpURLConnection connection = null;
        try {
            URL endpoint = new URL("https://api.telegram.org/bot" + token + "/sendMessage");
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] body = payload.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            int status = connection.getResponseCode();
            InputStream response = status >= 200 && status < 400
                    ? connection.getInputStream() : connection.getErrorStream();
            if (response == null) throw new IOException("Telegram Bot API HTTP " + status);
            byte[] responseBytes;
            try (InputStream input = response; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > 16384) throw new IOException("Слишком большой ответ Telegram");
                    bytes.write(buffer, 0, count);
                }
                responseBytes = bytes.toByteArray();
            }
            if (status < 200 || status >= 300) throw new IOException("Telegram Bot API HTTP " + status);
            try {
                JSONObject result = new JSONObject(new String(responseBytes, StandardCharsets.UTF_8));
                if (!result.optBoolean("ok", false)) throw new IOException("Telegram не принял сообщение");
            } catch (org.json.JSONException invalidResponse) {
                throw new IOException("Некорректный ответ Telegram Bot API", invalidResponse);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
