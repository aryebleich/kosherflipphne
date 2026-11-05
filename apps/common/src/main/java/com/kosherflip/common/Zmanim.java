package com.kosherflip.common;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Sunrise / sunset (NOAA solar algorithm) and the times derived from them.
 * Accuracy is about a minute, which is why the UI rounds and shows candle
 * lighting with the customary 18 minute margin. Always tell users to follow
 * their community's luach for practical halacha.
 */
public final class Zmanim {
    private Zmanim() {}

    /** Official sunrise/sunset: sun's centre 0.833 degrees below the horizon. */
    private static final double ZENITH = 90.833;

    public static ZonedDateTime sunrise(LocalDate date, double lat, double lon, ZoneId zone) {
        return calc(date, lat, lon, zone, true);
    }

    public static ZonedDateTime sunset(LocalDate date, double lat, double lon, ZoneId zone) {
        return calc(date, lat, lon, zone, false);
    }

    public static ZonedDateTime candleLighting(LocalDate date, double lat, double lon, ZoneId zone, int minutesBefore) {
        ZonedDateTime s = sunset(date, lat, lon, zone);
        return s == null ? null : s.minusMinutes(minutesBefore);
    }

    /** Nightfall used for the end of Shabbos/Yom Tov: sunset + the given minutes (e.g. 42, 50, 72). */
    public static ZonedDateTime nightfall(LocalDate date, double lat, double lon, ZoneId zone, int minutesAfter) {
        ZonedDateTime s = sunset(date, lat, lon, zone);
        return s == null ? null : s.plusMinutes(minutesAfter);
    }

    private static ZonedDateTime calc(LocalDate date, double lat, double lon, ZoneId zone, boolean rising) {
        int n = date.getDayOfYear();
        double lngHour = lon / 15.0;
        double t = n + ((rising ? 6 : 18) - lngHour) / 24.0;
        double m = 0.9856 * t - 3.289;
        double l = norm360(m + 1.916 * Math.sin(Math.toRadians(m)) + 0.020 * Math.sin(Math.toRadians(2 * m)) + 282.634);
        double ra = norm360(Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(l)))));
        ra += Math.floor(l / 90) * 90 - Math.floor(ra / 90) * 90;
        ra /= 15.0;
        double sinDec = 0.39782 * Math.sin(Math.toRadians(l));
        double cosDec = Math.cos(Math.asin(sinDec));
        double cosH = (Math.cos(Math.toRadians(ZENITH)) - sinDec * Math.sin(Math.toRadians(lat)))
                / (cosDec * Math.cos(Math.toRadians(lat)));
        if (cosH > 1 || cosH < -1) return null; // polar day/night
        double h = rising ? 360 - Math.toDegrees(Math.acos(cosH)) : Math.toDegrees(Math.acos(cosH));
        h /= 15.0;
        double localMean = h + ra - 0.06571 * t - 6.622;
        double ut = ((localMean - lngHour) % 24 + 24) % 24;
        long seconds = Math.round(ut * 3600);
        ZonedDateTime utc = date.atStartOfDay(ZoneId.of("UTC")).plusSeconds(seconds);
        ZonedDateTime local = utc.withZoneSameInstant(zone);
        // Keep the event on the requested civil date.
        if (local.toLocalDate().isAfter(date)) local = local.minusDays(1);
        else if (local.toLocalDate().isBefore(date)) local = local.plusDays(1);
        return local;
    }

    private static double norm360(double d) {
        return ((d % 360) + 360) % 360;
    }
}
