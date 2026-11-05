package com.kosherflip.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;

public class CommonTest {
    private final WordFilter f = WordFilter.standard(Arrays.asList("badword", "meh:4"));

    @Test public void cleanTextPasses() {
        assertFalse(f.check("Buy a stainless steel pot, free shipping. Rated 4.5 stars.").blocked);
        assertFalse(f.check("Parshas Noach: the generation of the flood").blocked);
        assertFalse(f.check("Hello, how are you? Sussex county news.").blocked);
    }

    @Test public void strongTermBlocks() {
        assertTrue(f.check("Watch free PORN now").blocked);
        assertTrue(f.check("n.u.d.e photos").blocked);
        assertTrue(f.check("p 0 r n").blocked);
        assertTrue(f.check("This has a badword in it").blocked);
    }

    @Test public void leetAndPluralBlock() {
        assertTrue(f.check("nud3s").blocked);
        assertTrue(f.check("$exy pics").blocked);
    }

    @Test public void mildTermsNeedCombination() {
        assertFalse(f.check("A romantic dinner").blocked);
        assertTrue(f.check("romantic kissing with my girlfriend in lingerie").blocked);
        assertTrue(f.check("dating flirting kissing romance").blocked);
        assertFalse(f.check("meh").blocked);
        assertTrue(f.check("meh meh meh").blocked);
    }

    @Test public void sunriseNewYorkIsPlausible() {
        ZoneId ny = ZoneId.of("America/New_York");
        LocalDate d = LocalDate.of(2026, 1, 1);
        ZonedDateTime rise = Zmanim.sunrise(d, 40.7128, -74.0060, ny);
        ZonedDateTime set = Zmanim.sunset(d, 40.7128, -74.0060, ny);
        // Published: sunrise 7:20, sunset 16:39 (EST)
        assertNear(LocalTime.of(7, 20), rise.toLocalTime(), 3);
        assertNear(LocalTime.of(16, 39), set.toLocalTime(), 3);
        assertEquals(d, set.toLocalDate());
    }

    @Test public void sunsetJerusalemSummer() {
        ZoneId j = ZoneId.of("Asia/Jerusalem");
        ZonedDateTime set = Zmanim.sunset(LocalDate.of(2026, 6, 21), 31.7683, 35.2137, j);
        assertNear(LocalTime.of(19, 48), set.toLocalTime(), 4);
    }

    private static void assertNear(LocalTime expected, LocalTime actual, int minutes) {
        long diff = Math.abs(Duration.between(expected, actual).toMinutes());
        assertTrue("expected ~" + expected + " got " + actual, diff <= minutes);
    }
}
