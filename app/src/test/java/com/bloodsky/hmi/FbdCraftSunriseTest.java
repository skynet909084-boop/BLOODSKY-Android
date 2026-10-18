package com.bloodsky.hmi;

import org.junit.Test;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class FbdCraftSunriseTest {
    private static long noonUtc(int year, int month, int day) {
        return LocalDate.of(year, month, day).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() + 12 * 3600000L;
    }

    @Test public void returnsLocalHhmmForLondonEquinox() {
        TimeZone london = TimeZone.getTimeZone("Europe/London");
        int sunrise = FbdCraftSunrise.currentLocalHhmm(51.5074, -0.1278, false, noonUtc(2024, 3, 20), london);
        int sunset = FbdCraftSunrise.currentLocalHhmm(51.5074, -0.1278, true, noonUtc(2024, 3, 20), london);
        assertTrue("sunrise=" + sunrise, sunrise >= 555 && sunrise <= 615);
        assertTrue("sunset=" + sunset, sunset >= 1750 && sunset <= 1830);
    }

    @Test public void rejectsInvalidAndPolarNoSunriseCoordinates() {
        long date = noonUtc(2024, 6, 21);
        TimeZone utc = TimeZone.getTimeZone("UTC");
        assertEquals(0, FbdCraftSunrise.currentLocalHhmm(0, 0, false, date, utc));
        assertEquals(0, FbdCraftSunrise.currentLocalHhmm(91, 10, false, date, utc));
        assertEquals(0, FbdCraftSunrise.currentLocalHhmm(89, 0, false, date, utc));
    }
}
