package com.kosherflip.common;

/**
 * Built-in filter vocabulary. Admins add their own terms (any language) in
 * KosherGuard; these are deliberately conservative so normal shopping, news
 * and Torah sites keep working.
 */
final class DefaultTerms {
    private DefaultTerms() {}

    /** Any one of these blocks the screen. */
    static final String[] STRONG = {
            "porn", "porno", "pornography", "pornhub", "xvideos", "xhamster", "xnxx", "redtube", "youporn",
            "onlyfans", "chaturbate", "camgirl", "camgirls", "hentai", "milf", "nsfw", "xxx",
            "nude", "nudes", "naked", "topless", "nudity", "stripper", "striptease",
            "sex video", "sex videos", "sex tape", "sex chat", "sexting", "erotic", "erotica",
            "fetish", "bdsm", "orgasm", "masturbate", "masturbation", "blowjob", "handjob",
            "escort service", "escorts", "hookup", "hookups", "one night stand",
            "casino bonus", "online casino", "sports betting", "bet now",
            "fuck", "fucking", "motherfucker", "cunt", "pussy", "dick pic",
            "lingerie", "bikini", "swimsuit model", "playboy", "hustler",
            "sexy", "seductive", "hot singles", "dating app", "find a date",
    };

    /** Weak signals: only block in combination (3 points each, 10 blocks). */
    static final String[] MILD = {
            "sex", "sexual", "intimate", "kiss", "kissing", "romance", "romantic", "boyfriend", "girlfriend",
            "dating", "date night", "flirt", "flirting", "lust", "seduce", "underwear", "bra", "panties",
            "breasts", "cleavage", "thong", "steamy", "adult", "18+", "explicit", "uncensored",
            "gambling", "casino", "poker", "jackpot", "slots", "betting",
            "shit", "bitch", "bastard", "damn", "hell", "ass", "crap",
            "gore", "graphic violence", "beheading",
            "celebrity gossip", "red carpet", "netflix", "tiktok", "instagram", "youtube",
    };
}
