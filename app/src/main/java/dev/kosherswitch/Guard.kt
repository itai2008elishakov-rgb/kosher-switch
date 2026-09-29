package dev.kosherswitch

import java.text.Normalizer

/**
 * Keeps the assistant kosher. Every question is checked before it reaches the AI, and every answer
 * while it's being written; anything immodest or otherwise unsuitable is refused politely.
 * Checks English and Hebrew, and common tricks: spaced or dotted letters, digits for letters.
 */
object Guard {
    /** Whole words (so "sex" doesn't catch "Essex"). */
    private val WORDS = setOf(
        "sex", "sexy", "sexual", "sexually", "sexting", "porn", "porno", "pornography", "pornographic", "xxx", "nsfw",
        "nude", "nudes", "naked", "nudity", "erotic", "erotica", "hentai", "orgasm", "masturbate", "masturbation",
        "genitals", "genital", "penis", "vagina", "boobs", "tits", "nipple", "nipples", "horny", "kinky",
        "fetish", "bdsm", "orgy", "stripper", "striptease", "prostitute", "prostitution", "hooker", "escort", "escorts",
        "lingerie", "bikini", "seduce", "seduction", "intercourse", "blowjob", "incest", "rape", "molest", "hookup",
        "tinder", "grindr", "onlyfans", "playboy", "camgirl", "lust", "aroused", "arousal", "foreplay", "condom",
        "seks", "sekso", "pornografia",
        "סקס", "סקסי", "סקסית", "פורנו", "פורנוגרפיה", "עירום", "עירומה", "עירומים", "ארוטי", "ארוטית", "ארוטיקה",
        "זנות", "זונה", "זונות", "חשפנות", "חשפנית", "אוננות", "ציצים", "ביקיני", "טינדר", "אונס",
        "מתירנות",  "קונדום",
    )

    /** Phrases, matched anywhere. */
    private val PHRASES = listOf(
        "make love", "making love", "sleep with", "slept with", "one night stand", "friends with benefits", "dating app",
        "hook up", "adult video", "adult site", "adult content", "strip club", "sex video", "dirty talk", "turn me on",
        "cheat on", "cheating on", "have an affair", "an affair with", "without clothes", "take off her", "take off his",
        "יחסי מין", "איבר מין", "איברי מין", "הלבשה תחתונה", "סרט כחול", "סרטים כחולים", "לשכב עם", "שכב עם", "שכבה עם",
        "אתר היכרויות", "אתרי היכרויות", "לבגוד ב", "רומן עם", "בלי בגדים",
    )

    /** Long enough to be safe even with every space and dot removed ("p o r n", "p.o.r.n"). */
    private val COMPACT = listOf("porn", "pornograph", "onlyfans", "hentai", "nsfw", "masturbat", "sexvideo", "sexting",
        "stripclub", "פורנו", "סקס", "יחסימין")

    private val LEET = mapOf('0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '@' to 'a', '$' to 's', '!' to 'i')

    /** Lowercase, no accents or Hebrew vowel marks, digits turned back into letters. */
    private fun normalize(text: String): String {
        val noMarks = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("[\\p{Mn}\\u0591-\\u05C7]"), "")
        return noMarks.map { LEET[it] ?: it }.joinToString("")
    }

    /** True when [text] is something the assistant must not discuss. */
    fun blocked(text: String): Boolean {
        val t = normalize(text)
        val words = t.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        if (words.any { it in WORDS || stripPrefix(it) in WORDS }) return true
        val spaced = words.joinToString(" ")
        if (PHRASES.any { spaced.contains(it) }) return true
        val compact = t.filter { it.isLetter() }
        return COMPACT.any { compact.contains(it) }
    }

    /** Hebrew attaches small words to the front (ה, ו, ב, ל, מ, ש, כ): "והסקס" is still "סקס". */
    private fun stripPrefix(word: String): String {
        var w = word
        while (w.length > 3 && w.first() in "הובלמשכ") w = w.drop(1)
        return w
    }

    fun refusal(hebrew: Boolean) =
        if (hebrew) "על זה אני לא מדבר. אני כאן לדברים שמתאימים לבית כשר: פתקים, מתכונים, שאלות ביהדות, תכנון ושאלות יומיומיות. במה עוד אפשר לעזור?"
        else "That's not something I talk about. I'm here for things that suit a kosher home: notes, recipes, Jewish questions, plans and everyday help. What else can I do for you?"
}
