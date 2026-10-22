package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class CraftWebViewPolicyTest {
    @Test public void sourceFirstSetBitWinsForEveryWord() {
        for (int word = 1; word <= 65535; word++) {
            int expected = 0;
            while ((word & (1 << expected)) == 0) expected++;
            assertEquals(expected, CraftWebViewPolicy.selectedIndex(Integer.toString(word)));
        }
        assertEquals(15, CraftWebViewPolicy.selectedIndex("-32768"));
        assertEquals(0, CraftWebViewPolicy.selectedIndex("-1"));
        assertEquals(3, CraftWebViewPolicy.selectedIndex("8.0"));
    }

    @Test public void absentZeroFractionalOrUnavailableRegisterNeverNavigates() {
        for (String state : new String[]{null, "", "CFG", "ERR: timeout", "0", "65536", "-32769", "1.5", "NaN", "Infinity"})
            assertEquals(-1, CraftWebViewPolicy.selectedIndex(state));
    }

    @Test public void urlPolicyRejectsUnsafeOrAmbiguousTargets() {
        for (String url : new String[]{null, "", "example.test", "http://example.test", "javascript:alert(1)",
                "file:///data/local/x", "content://project", "https://u:p@example.test", "https://example.test:0/",
                "https://example.test:65536/", "https://example.test/a%0d%0aInjected", "https://example.test/\\evil", "https://example.test/a\nb"})
            assertThrows(IllegalArgumentException.class, () -> CraftWebViewPolicy.checkedUrl(url));
        assertEquals("https://example.test/path?q=1#part", CraftWebViewPolicy.checkedUrl(" https://example.test/path?q=1#part "));
    }

    @Test public void redirectsAndSubresourcesAreConfinedToApprovedOrigin() {
        String approved = "https://example.test/hmi";
        assertTrue(CraftWebViewPolicy.allows(approved, "https://EXAMPLE.test:443/images/panel.png"));
        assertTrue(CraftWebViewPolicy.allows(approved, "https://example.test/next#part"));
        for (String target : new String[]{"http://example.test/", "https://example.test:444/", "https://evil.test/",
                "https://example.test.evil.test/", "https://example.test@evil.test/", "data:text/html,hello", "intent://app"})
            assertFalse(CraftWebViewPolicy.allows(approved, target));
    }
}
