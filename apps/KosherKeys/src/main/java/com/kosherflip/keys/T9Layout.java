package com.kosherflip.keys;

/**
 * Multi-tap layouts for phone keypads. Press a key repeatedly to cycle its
 * letters; # switches mode; 0 = space (press again for 0); 1 = punctuation.
 *
 * Hebrew uses the standard Israeli keypad (2 = ד ה ו, 3 = א ב ג ...), with
 * final letters next to their regular form. Yiddish adds the Yiddish letters
 * and ligatures (וו װ, וי ױ, יי ײ, ײַ, אַ אָ, פּ פֿ, בֿ, כּ, שׂ, תּ) on the same keys.
 */
final class T9Layout {
    enum Mode {
        ABC("abc"), CAPS_FIRST("Abc"), CAPS("ABC"), HEBREW("אבג"),
        YIDDISH("ייִד"), NUMBERS("123");

        final String label;
        Mode(String label) { this.label = label; }

        Mode next() {
            Mode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        boolean isRtl() {
            return this == HEBREW || this == YIDDISH;
        }
    }

    private static final String[] LATIN = {
            " 0", ".,?!'\"-@:;/()1", "abc2", "def3", "ghi4", "jkl5", "mno6", "pqrs7", "tuv8", "wxyz9"};

    // Keys 0-9. Each string is a list of letters to cycle through (one code point
    // or one letter+point cluster each), separated by '|'.
    private static final String[] HEBREW = {
            " |0", ".|,|?|!|'|\"|-|־|:|1",
            "ד|ה|ו|2",                         // ד ה ו
            "א|ב|ג|3",                         // א ב ג
            "מ|ם|נ|ן|4",                  // מ ם נ ן
            "י|כ|ך|ל|5",                  // י כ ך ל
            "ז|ח|ט|6",                         // ז ח ט
            "ר|ש|ת|7",                         // ר ש ת
            "צ|ץ|ק|8",                         // צ ץ ק
            "ס|ע|פ|ף|9"};                 // ס ע פ ף

    private static final String[] YIDDISH = {
            " |0", ".|,|?|!|'|\"|-|־|:|1",
            "ד|ה|ו|װ|ױ|וּ|2",                  // ד ה ו װ ױ וּ
            "א|אַ|אָ|ב|בֿ|ג|3",      // א אַ אָ ב בֿ ג
            "מ|ם|נ|ן|4",                                      // מ ם נ ן
            "י|ײ|ײַ|יִ|כ|כּ|ך|ל|5", // י ײ ײַ יִ כ כּ ך ל
            "ז|ח|ט|6",                                             // ז ח ט
            "ר|ש|שׂ|ת|תּ|7",                   // ר ש שׂ ת תּ
            "צ|ץ|ק|8",                                             // צ ץ ק
            "ס|ע|פּ|פֿ|ף|9"};                  // ס ע פּ פֿ ף

    private T9Layout() {}

    /** The letters key {@code digit} cycles through in {@code mode}. */
    static String[] letters(Mode mode, int digit) {
        switch (mode) {
            case HEBREW: return HEBREW[digit].split("\\|");
            case YIDDISH: return YIDDISH[digit].split("\\|");
            case NUMBERS: return new String[] {String.valueOf(digit)};
            default:
                String s = LATIN[digit];
                String[] out = new String[s.length()];
                for (int i = 0; i < s.length(); i++) out[i] = String.valueOf(s.charAt(i));
                return out;
        }
    }

    /** The letter after {@code presses} presses (1-based), with capitals applied. */
    static String letter(Mode mode, int digit, int presses, boolean capitalize) {
        String[] l = letters(mode, digit);
        String c = l[(presses - 1) % l.length];
        if (mode == Mode.CAPS || (mode == Mode.CAPS_FIRST && capitalize)) c = c.toUpperCase(java.util.Locale.ROOT);
        return c;
    }
}
