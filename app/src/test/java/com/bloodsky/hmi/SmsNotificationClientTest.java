package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class SmsNotificationClientTest {
    @Test public void validatesInternationalPhoneNumbers() {
        assertTrue(SmsNotificationClient.isValidPhone("+1 (415) 555-0123"));
        assertTrue(SmsNotificationClient.isValidPhone("74951234567"));
        assertFalse(SmsNotificationClient.isValidPhone("123"));
        assertFalse(SmsNotificationClient.isValidPhone("+1 800 CALL NOW"));
    }

    @Test public void substitutesModbusValueIntoBoundedMessage() {
        assertEquals("Alarm: 21.5", SmsNotificationClient.message("Alarm: {value}", "21.5"));
        assertEquals("Событие BLOODSKY", SmsNotificationClient.message(" ", "0"));
        assertEquals(1200, SmsNotificationClient.message(repeat('x',1300), "").length());
    }

    private static String repeat(char value,int count){StringBuilder result=new StringBuilder();for(int i=0;i<count;i++)result.append(value);return result.toString();}
}
