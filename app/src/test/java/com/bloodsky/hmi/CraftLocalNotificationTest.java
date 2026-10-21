package com.bloodsky.hmi;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class CraftLocalNotificationTest {
    @Test public void titleIsTrimmedDefaultedAndBounded() {
        assertEquals("BLOODSKY", CraftLocalNotification.title("  "));
        assertEquals("Alarm", CraftLocalNotification.title("  Alarm  "));
        assertEquals(120, CraftLocalNotification.title("x".repeat(150)).length());
    }

    @Test public void bodyIsBounded() {
        assertEquals(2000, CraftLocalNotification.body("x".repeat(2100)).length());
    }

    @Test public void notificationIdsAreStableAndDistinctByWidget() {
        int first = CraftLocalNotification.notificationId(1, 4);
        assertEquals(first, CraftLocalNotification.notificationId(1, 4));
        assertNotEquals(first, CraftLocalNotification.notificationId(1, 5));
    }
}
