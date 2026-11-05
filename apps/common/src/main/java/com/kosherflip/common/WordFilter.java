package com.kosherflip.common;

import java.text.Normalizer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Scores text for inappropriate content. Used by the screen filter (every app),
 * the browser (every page) and the AI assistant (every answer).
 *
 * Terms carry weights: a {@link #BLOCK}-weight term blocks on its own, lighter
 * terms only block when enough of them appear together, so a single ambiguous
 * word in a news headline or a product name does not block a whole page.
 * Text is normalised first (case, accents, common character substitutions like
 * "s3x" / "p0rn", separators like "p.o.r.n").
 */
public final class WordFilter {
    public static final int BLOCK = 10;

    private final Map<String, Integer> words = new HashMap<>();
    private final Map<String, Integer> phrases = new HashMap<>();
    private final int threshold;

    public WordFilter(int threshold) {
        this.threshold = threshold;
    }

    /** The built-in list plus optional admin terms ("word" = block, "word:3" = weight 3). */
    public static WordFilter standard(Collection<String> customTerms) {
        WordFilter f = new WordFilter(BLOCK);
        for (String t : DefaultTerms.STRONG) f.add(t, BLOCK);
        for (String t : DefaultTerms.MILD) f.add(t, 3);
        if (customTerms != null) {
            for (String raw : customTerms) {
                String t = raw.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                int w = BLOCK;
                int colon = t.lastIndexOf(':');
                if (colon > 0) {
                    try {
                        w = Integer.parseInt(t.substring(colon + 1).trim());
                        t = t.substring(0, colon);
                    } catch (NumberFormatException ignored) {
                    }
                }
                f.add(t, w);
            }
        }
        return f;
    }

    public void add(String term, int weight) {
        String n = normalize(term).trim();
        if (n.isEmpty()) return;
        if (n.indexOf(' ') >= 0) phrases.put(n, weight);
        else words.put(n, weight);
    }

    public Result check(CharSequence text) {
        if (text == null || text.length() == 0) return Result.CLEAN;
        String n = normalize(text.toString());
        int score = 0;
        String worst = null;
        int worstWeight = 0;
        // Single words
        int i = 0, len = n.length();
        while (i < len) {
            while (i < len && n.charAt(i) == ' ') i++;
            int s = i;
            while (i < len && n.charAt(i) != ' ') i++;
            if (i > s) {
                String w = n.substring(s, i);
                Integer weight = words.get(w);
                if (weight == null && w.endsWith("s")) weight = words.get(w.substring(0, w.length() - 1));
                if (weight != null) {
                    score += weight;
                    if (weight > worstWeight) { worstWeight = weight; worst = w; }
                    if (score >= threshold) return new Result(true, score, worst);
                }
            }
        }
        // Phrases
        String padded = " " + n + " ";
        for (Map.Entry<String, Integer> e : phrases.entrySet()) {
            if (padded.contains(" " + e.getKey() + " ")) {
                score += e.getValue();
                if (e.getValue() > worstWeight) { worstWeight = e.getValue(); worst = e.getKey(); }
                if (score >= threshold) return new Result(true, score, worst);
            }
        }
        return score == 0 ? Result.CLEAN : new Result(false, score, worst);
    }

    /**
     * Lower case, strip accents/niqqud, undo look-alike characters, and join
     * letters split apart to dodge filters ("p.o.r.n", "p o r n" -> "porn").
     */
    static String normalize(String in) {
        String s = Normalizer.normalize(in, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(s.length());
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
            switch (c) {
                case '0': c = 'o'; break;
                case '1': case '!': case '|': c = 'i'; break;
                case '3': c = 'e'; break;
                case '4': case '@': c = 'a'; break;
                case '5': case '$': c = 's'; break;
                case '7': c = 't'; break;
                default: break;
            }
            if (Character.isLetterOrDigit(c) || c == '+') sb.append(c);
            else sb.append(' ');
        }
        // Join runs of 3+ single-letter tokens.
        String[] tokens = sb.toString().trim().split(" +");
        StringBuilder out = new StringBuilder(sb.length());
        int t = 0;
        while (t < tokens.length) {
            int run = t;
            while (run < tokens.length && tokens[run].length() == 1) run++;
            if (run - t >= 3) {
                if (out.length() > 0) out.append(' ');
                for (int k = t; k < run; k++) out.append(tokens[k]);
                t = run;
            } else {
                if (out.length() > 0) out.append(' ');
                out.append(tokens[t]);
                t++;
            }
        }
        return out.toString();
    }

    public static final class Result {
        public static final Result CLEAN = new Result(false, 0, null);
        public final boolean blocked;
        public final int score;
        public final String reason;

        Result(boolean blocked, int score, String reason) {
            this.blocked = blocked;
            this.score = score;
            this.reason = reason;
        }
    }
}
