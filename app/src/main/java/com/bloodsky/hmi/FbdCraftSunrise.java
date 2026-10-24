package com.bloodsky.hmi;

import java.util.Calendar;
import java.util.TimeZone;

/** Local Craft-compatible sunrise/sunset HHmm calculation for the current date. */
public final class FbdCraftSunrise {
    private FbdCraftSunrise() {}

    public static int currentLocalHhmm(double latitude, double longitude, boolean sunset,
                                       long nowMillis, TimeZone zone) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180
                || (latitude == 0.0 && longitude == 0.0) || zone == null) return 0;
        Calendar cal = Calendar.getInstance(zone);
        cal.setTimeInMillis(nowMillis);
        int n = cal.get(Calendar.DAY_OF_YEAR);
        double lngHour = longitude / 15.0;
        double t = n + ((sunset ? 18.0 : 6.0) - lngHour) / 24.0;
        double meanAnomaly = (0.9856 * t) - 3.289;
        double trueLongitude = normalize(meanAnomaly + 1.916 * sin(meanAnomaly)
                + 0.020 * sin(2 * meanAnomaly) + 282.634);
        double rightAscension = normalize(Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(trueLongitude)))));
        rightAscension += Math.floor(trueLongitude / 90.0) * 90.0 - Math.floor(rightAscension / 90.0) * 90.0;
        rightAscension /= 15.0;
        double sinDeclination = 0.39782 * sin(trueLongitude);
        double cosDeclination = Math.cos(Math.asin(sinDeclination));
        double cosHourAngle = (Math.cos(Math.toRadians(90.833))
                - sinDeclination * Math.sin(Math.toRadians(latitude)))
                / (cosDeclination * Math.cos(Math.toRadians(latitude)));
        if (cosHourAngle > 1 || cosHourAngle < -1) return 0;
        double hourAngle = sunset ? Math.acos(cosHourAngle) : 2 * Math.PI - Math.acos(cosHourAngle);
        double localMeanTime = Math.toDegrees(hourAngle) / 15.0 + rightAscension - 0.06571 * t - 6.622;
        double utcHours = (localMeanTime - lngHour) % 24.0;
        if (utcHours < 0) utcHours += 24.0;
        int localMinutes = (int)Math.round((utcHours * 60.0 + zone.getOffset(nowMillis) / 60000.0) % 1440.0);
        localMinutes = (localMinutes + 1440) % 1440;
        return (localMinutes / 60) * 100 + localMinutes % 60;
    }

    private static double sin(double degrees) { return Math.sin(Math.toRadians(degrees)); }
    private static double normalize(double value) { return ((value % 360.0) + 360.0) % 360.0; }
}
