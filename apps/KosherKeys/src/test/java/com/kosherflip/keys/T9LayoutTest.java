package com.kosherflip.keys;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class T9LayoutTest {
    @Test public void latin() {
        assertEquals("a", T9Layout.letter(T9Layout.Mode.ABC, 2, 1, false));
        assertEquals("c", T9Layout.letter(T9Layout.Mode.ABC, 2, 3, false));
        assertEquals("2", T9Layout.letter(T9Layout.Mode.ABC, 2, 4, false));
        assertEquals("a", T9Layout.letter(T9Layout.Mode.ABC, 2, 5, false)); // wraps
        assertEquals("S", T9Layout.letter(T9Layout.Mode.CAPS, 7, 4, false));
        assertEquals("G", T9Layout.letter(T9Layout.Mode.CAPS_FIRST, 4, 1, true));
        assertEquals(" ", T9Layout.letter(T9Layout.Mode.ABC, 0, 1, false));
    }

    @Test public void hebrewStandardKeypad() {
        assertEquals("א", T9Layout.letter(T9Layout.Mode.HEBREW, 3, 1, false)); // א
        assertEquals("ד", T9Layout.letter(T9Layout.Mode.HEBREW, 2, 1, false)); // ד
        assertEquals("ם", T9Layout.letter(T9Layout.Mode.HEBREW, 4, 2, false)); // ם
        assertEquals("ת", T9Layout.letter(T9Layout.Mode.HEBREW, 7, 3, false)); // ת
        assertEquals("ף", T9Layout.letter(T9Layout.Mode.HEBREW, 9, 4, true));  // ף, no caps
    }

    @Test public void yiddishLetters() {
        assertEquals("װ", T9Layout.letter(T9Layout.Mode.YIDDISH, 2, 4, false));      // װ
        assertEquals("אַ", T9Layout.letter(T9Layout.Mode.YIDDISH, 3, 2, false)); // אַ
        assertEquals("ײ", T9Layout.letter(T9Layout.Mode.YIDDISH, 5, 2, false));       // ײ
        assertEquals("פּ", T9Layout.letter(T9Layout.Mode.YIDDISH, 9, 3, false)); // פּ
    }

    @Test public void modesCycle() {
        T9Layout.Mode m = T9Layout.Mode.ABC;
        for (int i = 0; i < T9Layout.Mode.values().length; i++) m = m.next();
        assertEquals(T9Layout.Mode.ABC, m);
        assertEquals("7", T9Layout.letter(T9Layout.Mode.NUMBERS, 7, 3, false));
    }
}
