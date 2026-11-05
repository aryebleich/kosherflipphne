package com.kosherflip.guard;

import android.icu.util.HebrewCalendar;

import com.kosherflip.common.Zmanim;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

/** Shabbos and Yom Tov windows for Shabbos mode. */
final class JewishCalendar {
    private JewishCalendar() {}

    /** True if civil day {@code d} is Shabbos or a (biblical) Yom Tov. */
    static boolean isRestDay(LocalDate d, boolean inIsrael) {
        if (d.getDayOfWeek() == java.time.DayOfWeek.SATURDAY) return true;
        HebrewCalendar hc = new HebrewCalendar();
        hc.setTime(Date.from(d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()));
        int m = hc.get(HebrewCalendar.MONTH);
        int day = hc.get(HebrewCalendar.DAY_OF_MONTH);
        boolean diaspora = !inIsrael;
        if (m == HebrewCalendar.TISHRI) {
            return day == 1 || day == 2 || day == 10 || day == 15 || (diaspora && day == 16)
                    || day == 22 || (diaspora && day == 23);
        }
        if (m == HebrewCalendar.NISAN) {
            return day == 15 || (diaspora && day == 16) || day == 21 || (diaspora && day == 22);
        }
        if (m == HebrewCalendar.SIVAN) {
            return day == 6 || (diaspora && day == 7);
        }
        return false;
    }

    /** Is "now" inside Shabbos/Yom Tov (candle lighting the evening before until nightfall)? */
    static boolean isNow(Policy p, ZonedDateTime now) {
        if (p.latitude == 0 && p.longitude == 0) return false;
        ZoneId z = now.getZone();
        LocalDate today = now.toLocalDate();
        for (LocalDate d : new LocalDate[] {today, today.plusDays(1)}) {
            if (!isRestDay(d, p.inIsrael)) continue;
            // Walk back over consecutive rest days (e.g. Yom Tov followed by Shabbos).
            LocalDate first = d;
            while (isRestDay(first.minusDays(1), p.inIsrael)) first = first.minusDays(1);
            LocalDate last = d;
            while (isRestDay(last.plusDays(1), p.inIsrael)) last = last.plusDays(1);
            ZonedDateTime start = Zmanim.candleLighting(first.minusDays(1), p.latitude, p.longitude, z, p.candleMinutes);
            ZonedDateTime end = Zmanim.nightfall(last, p.latitude, p.longitude, z, p.endMinutes);
            if (start != null && end != null && !now.isBefore(start) && now.isBefore(end)) return true;
        }
        return false;
    }
}
