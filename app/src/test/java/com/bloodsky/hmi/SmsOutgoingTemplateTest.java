package com.bloodsky.hmi;

import org.junit.Test;
import static org.junit.Assert.*;

public class SmsOutgoingTemplateTest {
    @Test public void substitutesExplicitRegisterValuesInPlace() {
        assertEquals("A=10; C=30", SmsOutgoingTemplate.render("A=%1; C=%3",
                new String[]{"10", "20", "30"}, "10"));
    }

    @Test public void appendsValuesWhenNoValuePlaceholderIsPresent() {
        assertEquals("Values 10 20", SmsOutgoingTemplate.render("Values",
                new String[]{"10", "20"}, "10"));
    }

    @Test public void preservesLegacyValueTemplateWithoutDuplicatingValue() {
        assertEquals("SMS: 10", SmsOutgoingTemplate.render("SMS: {value}", new String[]{"10"}, "10"));
    }

    @Test public void rejectsPlaceholdersBeyondSelectedCountAndTruncatesMessage() {
        try {
            SmsOutgoingTemplate.render("%2", new String[]{"10"}, "10");
            fail("missing register must fail closed");
        } catch (IllegalArgumentException expected) {}
        assertEquals(1200, SmsOutgoingTemplate.render(new String(new char[1300]).replace('\0', 'x'),
                new String[]{"1"}, "1").length());
    }
}
