package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class CraftWebViewLinksTest {
    @Test public void readsAndWritesAllSixteenSourceKeysAndKeepsUnknownFields() throws Exception {
        JSONObject original = new JSONObject().put("link0", "https://example.test").put("link15", "http://last")
                .put("futureField", "keep");
        String[] links = CraftWebViewLinks.read(original);
        assertEquals("https://example.test", links[0]);
        assertEquals("", links[1]);
        assertEquals("http://last", links[15]);

        links[1] = "https://second.test";
        JSONObject saved = CraftWebViewLinks.write(original, links);
        assertEquals("keep", saved.getString("futureField"));
        assertEquals("https://second.test", saved.getString("link1"));
        assertEquals("http://last", saved.getString("link15"));
    }

    @Test public void importedValuesAreBoundedAndOversizedEditsAreRejected() throws Exception {
        JSONObject original = new JSONObject().put("link0", "x".repeat(CraftWebViewLinks.MAX_LINK_LENGTH + 10));
        assertEquals(CraftWebViewLinks.MAX_LINK_LENGTH, CraftWebViewLinks.read(original)[0].length());

        String[] links = new String[CraftWebViewLinks.LINK_COUNT];
        links[0] = "x".repeat(CraftWebViewLinks.MAX_LINK_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> CraftWebViewLinks.write(original, links));
    }
}
