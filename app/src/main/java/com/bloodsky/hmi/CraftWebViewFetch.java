package com.bloodsky.hmi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

/** Fetch each page/resource ourselves: WebView interception alone does not check resource redirects. */
final class CraftWebViewFetch {
    static final int MAX_BYTES = 4 * 1024 * 1024;
    interface Active { boolean getAsBoolean(); }
    interface Connections { HttpURLConnection open(String url) throws IOException; }
    static final class Result {
        final byte[] body;
        final String mime, encoding;
        Result(byte[] body, String mime, String encoding) {
            this.body = body; this.mime = mime; this.encoding = encoding;
        }
    }

    static Result get(String approved, String target, String method, Active active,
                       Connections connections) throws IOException {
        if (!"GET".equals(method) && !"HEAD".equals(method)) throw new IOException("WebView request method blocked");
        String current = target;
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (!active.getAsBoolean() || !CraftWebViewPolicy.allows(approved, current))
                throw new IOException("WebView origin blocked or page closed");
            HttpURLConnection connection = connections.open(CraftWebViewPolicy.checkedUrl(current));
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setUseCaches(false);
                connection.setRequestMethod(method);
                // Never forward WebView cookies, Authorization, Referer or imported secrets.
                connection.setRequestProperty("Accept-Encoding", "identity");
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("WebView redirect has no destination");
                    try { current = URI.create(current).resolve(location).toString(); }
                    catch (IllegalArgumentException invalid) { throw new IOException("Invalid WebView redirect", invalid); }
                    continue;
                }
                if (status < 200 || status >= 300) throw new IOException("WebView HTTP " + status);
                if (connection.getContentLength() > MAX_BYTES) throw new IOException("WebView resource too large");
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!"HEAD".equals(method)) try (InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (!active.getAsBoolean()) throw new IOException("WebView page closed");
                        if (count > MAX_BYTES - output.size()) throw new IOException("WebView resource too large");
                        output.write(buffer, 0, count);
                    }
                }
                String contentType = connection.getContentType();
                String mime = "application/octet-stream", encoding = "UTF-8";
                if (contentType != null) {
                    String[] parts = contentType.split(";");
                    mime = parts[0].trim();
                    for (int i = 1; i < parts.length; i++) {
                        String part = parts[i].trim();
                        if (part.toLowerCase(java.util.Locale.ROOT).startsWith("charset="))
                            encoding = part.substring(8).replace("\"", "").trim();
                    }
                }
                return new Result(output.toByteArray(), mime, encoding);
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many WebView redirects");
    }

    private CraftWebViewFetch() {}
}
