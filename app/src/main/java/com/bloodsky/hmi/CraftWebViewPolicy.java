package com.bloodsky.hmi;

import java.net.URI;

/** Source: Craft CustomComponents/b0.e(int), first set bit among bits 0..15.
 * Navigation is deliberately narrower than Craft: explicit HTTPS, no credentials,
 * and only the selected origin. Imported strings remain untouched in persistence.
 */
public final class CraftWebViewPolicy {
    private CraftWebViewPolicy() {}

    public static int selectedIndex(String registerValue) {
        if (registerValue == null) return -1;
        try {
            double value = Double.parseDouble(registerValue.trim());
            if ((Double.isNaN(value) || Double.isInfinite(value)) || value != Math.rint(value)
                    || value < -32768 || value > 65535) return -1;
            int word = ((int) value) & 0xffff;
            return word == 0 ? -1 : Integer.numberOfTrailingZeros(word);
        } catch (NumberFormatException invalid) { return -1; }
    }

    public static String checkedUrl(String value) {
        if (value == null || value.length() > CraftWebViewLinks.MAX_LINK_LENGTH)
            throw new IllegalArgumentException("Некорректная ссылка WebView");
        String url = value.trim();
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getPort() == 0
                    || uri.getPort() > 65535 || uri.getPort() < -1 || url.indexOf('\\') >= 0)
                throw new IllegalArgumentException("WebView: нужен HTTPS-адрес без логина и пароля");
            // Reject encoded controls as well as literal controls; neither belongs in an HMI URL.
            String lower = url.toLowerCase(java.util.Locale.ROOT);
            for (int i = 0; i < url.length(); i++)
                if (Character.isISOControl(url.charAt(i))) throw new IllegalArgumentException("Недопустимый символ URL");
            if (lower.matches(".*%(0[0-9a-f]|1[0-9a-f]|7f).*"))
                throw new IllegalArgumentException("Недопустимый символ URL");
            return uri.toASCIIString();
        } catch (java.net.URISyntaxException invalid) {
            throw new IllegalArgumentException("Некорректный HTTPS-адрес", invalid);
        }
    }

    public static boolean allows(String approvedUrl, String candidate) {
        try {
            URI approved = URI.create(checkedUrl(approvedUrl));
            URI target = URI.create(checkedUrl(candidate));
            return approved.getHost().equalsIgnoreCase(target.getHost())
                    && port(approved) == port(target);
        } catch (IllegalArgumentException invalid) { return false; }
    }

    private static int port(URI uri) { return uri.getPort() == -1 ? 443 : uri.getPort(); }
}
