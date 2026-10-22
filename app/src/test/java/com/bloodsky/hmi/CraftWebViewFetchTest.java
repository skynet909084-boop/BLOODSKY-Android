package com.bloodsky.hmi;

import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class CraftWebViewFetchTest {
    static class Peer extends HttpURLConnection {
        final int status;
        String location, contentType = "text/html; charset=windows-1251";
        byte[] body = "panel".getBytes(StandardCharsets.UTF_8);
        boolean closed;
        Peer(int status) throws Exception { super(new URL("https://panel.test/")); this.status = status; }
        public void disconnect() { closed = true; }
        public boolean usingProxy() { return false; }
        public void connect() {}
        public int getResponseCode() { return status; }
        public String getHeaderField(String key) { return "Location".equals(key) ? location : null; }
        public String getContentType() { return contentType; }
        public long getContentLengthLong() { return -1; }
        public InputStream getInputStream() { return new ByteArrayInputStream(body); }
    }

    @Test public void readsPageWithBoundedTimeoutsAndNoForwardedCredentials() throws Exception {
        Peer peer = new Peer(200);
        CraftWebViewFetch.Result result = CraftWebViewFetch.get("https://panel.test/", "https://panel.test/hmi",
                "GET", () -> true, url -> peer);
        assertEquals("panel", new String(result.body, StandardCharsets.UTF_8));
        assertEquals("text/html", result.mime);
        assertEquals("windows-1251", result.encoding);
        assertEquals(5000, peer.getConnectTimeout());
        assertEquals(5000, peer.getReadTimeout());
        assertFalse(peer.getInstanceFollowRedirects());
        assertNull(peer.getRequestProperty("Cookie"));
        assertNull(peer.getRequestProperty("Authorization"));
        assertTrue(peer.closed);
    }

    @Test public void crossOriginAndDowngradeRedirectsNeverOpenTheirDestination() throws Exception {
        for (String destination : new String[]{"https://other.test/", "http://panel.test/", "file:///private"}) {
            Peer peer = new Peer(302); peer.location = destination;
            List<String> opened = new ArrayList<>();
            assertThrows(IOException.class, () -> CraftWebViewFetch.get("https://panel.test/", "https://panel.test/hmi",
                    "GET", () -> true, url -> { opened.add(url); return peer; }));
            assertEquals(Collections.singletonList("https://panel.test/hmi"), opened);
            assertTrue(peer.closed);
        }
    }

    @Test public void sameOriginRedirectsAreCheckedAndLoopsBounded() throws Exception {
        Peer redirect = new Peer(302); redirect.location = "/next";
        Peer page = new Peer(200);
        List<String> opened = new ArrayList<>();
        CraftWebViewFetch.get("https://panel.test/", "https://panel.test/hmi", "GET", () -> true,
                url -> { opened.add(url); return opened.size() == 1 ? redirect : page; });
        assertEquals(Arrays.asList("https://panel.test/hmi", "https://panel.test/next"), opened);
        opened.clear();
        assertThrows(IOException.class, () -> CraftWebViewFetch.get("https://panel.test/", "https://panel.test/hmi",
                "GET", () -> true, url -> { opened.add(url); return redirect; }));
        assertEquals(6, opened.size());
    }

    @Test public void closedPageFormsHttpFailuresAndOversizedBodiesFailClosed() throws Exception {
        assertThrows(IOException.class, () -> CraftWebViewFetch.get("https://panel.test/", "https://panel.test/hmi",
                "GET", () -> false, url -> { fail("Closed page must not open a connection"); return null; }));
        assertThrows(IOException.class, () -> CraftWebViewFetch.get("https://panel.test/", "https://panel.test/hmi",
                "POST", () -> true, url -> { fail("Form submission must not connect"); return null; }));
        Peer huge = new Peer(200); huge.body = new byte[CraftWebViewFetch.MAX_BYTES + 1];
        assertThrows(IOException.class, () -> CraftWebViewFetch.get("https://panel.test/", "https://panel.test/hmi",
                "GET", () -> true, url -> huge));
        assertTrue(huge.closed);
        Peer failure = new Peer(500);
        assertThrows(IOException.class, () -> CraftWebViewFetch.get("https://panel.test/", "https://panel.test/hmi",
                "GET", () -> true, url -> failure));
        assertTrue(failure.closed);
        AtomicBoolean active = new AtomicBoolean(true);
        Peer canceled = new Peer(200) {
            @Override public InputStream getInputStream() { active.set(false); return super.getInputStream(); }
        };
        assertThrows(IOException.class, () -> CraftWebViewFetch.get("https://panel.test/", "https://panel.test/hmi",
                "GET", active::get, url -> canceled));
        assertTrue(canceled.closed);
    }
}
