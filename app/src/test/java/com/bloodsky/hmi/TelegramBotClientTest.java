package com.bloodsky.hmi;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class TelegramBotClientTest {
    @Test public void validatesBotTokenAndPrivateOrChannelChatIds() {
        assertTrue(TelegramBotClient.isValidToken("123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghi"));
        assertFalse(TelegramBotClient.isValidToken("not-a-token"));
        assertFalse(TelegramBotClient.isValidToken("123456789:bad token"));
        assertTrue(TelegramBotClient.isValidChatId("-1001234567890"));
        assertTrue(TelegramBotClient.isValidChatId("@bloodsky_updates"));
        assertFalse(TelegramBotClient.isValidChatId("https://evil.example"));
    }

    @Test public void createsJsonTelegramPayloadWithoutFormEncodingLoss() throws Exception {
        JSONObject payload = new JSONObject(TelegramBotClient.requestJson("-10042", "Насос: {value} & норма"));
        assertEquals("-10042", payload.getString("chat_id"));
        assertEquals("Насос: {value} & норма", payload.getString("text"));
        assertTrue(payload.getBoolean("disable_web_page_preview"));
    }

    @Test public void rejectsEmptyAndOversizedMessages() {
        assertBadMessage("");
        assertBadMessage(" \n ");
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 4097; i++) tooLong.append('x');
        assertBadMessage(tooLong.toString());
    }

    private static void assertBadMessage(String message) {
        try {
            TelegramBotClient.requestJson("12345", message);
            fail("Expected validation error");
        } catch (IllegalArgumentException expected) { /* expected */ }
    }
}
