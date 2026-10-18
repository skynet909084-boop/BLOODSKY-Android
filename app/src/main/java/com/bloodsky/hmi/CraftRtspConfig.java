package com.bloodsky.hmi;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Validated endpoint boundary for the future Craft ID 23 RTSP widget.
 * This parser intentionally rejects URI user-info so passwords are not
 * silently copied into ordinary project JSON or exported project archives.
 */
public final class CraftRtspConfig {
    private static final int MAX_URL_LENGTH = 2048;

    private final String url;

    private CraftRtspConfig(String url) {
        this.url = url;
    }

    public static CraftRtspConfig parse(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_URL_LENGTH
                || !value.equals(value.trim())) {
            throw new IllegalArgumentException("Укажи RTSP-адрес длиной до 2048 символов");
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i)) || Character.isISOControl(value.charAt(i))) {
                throw new IllegalArgumentException("RTSP-адрес не должен содержать пробелы или управляющие символы");
            }
        }

        final URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Некорректный RTSP-адрес", ex);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !("rtsp".equals(scheme.toLowerCase(Locale.ROOT))
                || "rtsps".equals(scheme.toLowerCase(Locale.ROOT)))) {
            throw new IllegalArgumentException("Поддерживаются только RTSP и RTSPS");
        }
        if (uri.isOpaque() || uri.getHost() == null || uri.getHost().isEmpty()) {
            throw new IllegalArgumentException("В RTSP-адресе должен быть указан хост");
        }
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("Не встраивай логин и пароль в RTSP-адрес");
        }
        if (uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Фрагмент # в RTSP-адресе не поддерживается");
        }
        int port = uri.getPort();
        if (port == 0 || port > 65535) {
            throw new IllegalArgumentException("Порт RTSP должен быть в диапазоне 1–65535");
        }
        return new CraftRtspConfig(value);
    }

    public String getUrl() {
        return url;
    }
}
