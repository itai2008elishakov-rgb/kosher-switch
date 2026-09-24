package dev.kosherswitch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Section(val heading: String, val paragraphs: List<String>)

/** One whole prayer (e.g. weekday Mincha), read as a single text split into sections. */
class Prayer(val title: String, val group: String, val sections: List<Section>)

/** The offline siddurim and Tehillim in assets/texts. Each book stays in memory after first use. */
object Texts {
    const val KEY_NUSACH = "siddur_nusach"

    val NUSACHOT = linkedMapOf("ashkenaz" to "אשכנז", "sefard" to "ספרד", "edot" to "עדות המזרח")

    /** Tabs of the siddur, in order. Tehillim is its own tab. */
    val GROUPS = linkedMapOf(
        "weekday" to "חול", "shabbat" to "שבת", "holidays" to "מועדים", "brachot" to "ברכות", "more" to "עוד",
    )

    private val books = HashMap<String, List<Prayer>>()
    private var tehillim: List<Section>? = null

    fun nusach(ctx: Context): String? = prefs(ctx).getString(KEY_NUSACH, null)

    fun setNusach(ctx: Context, nusach: String) = prefs(ctx).edit().putString(KEY_NUSACH, nusach).apply()

    fun book(ctx: Context, nusach: String): List<Prayer> = synchronized(books) {
        books.getOrPut(nusach) {
            val prayers = readJson(ctx, "texts/book_$nusach.json").getJSONArray("prayers")
            List(prayers.length()) { i ->
                val p = prayers.getJSONObject(i)
                val sections = p.getJSONArray("s")
                Prayer(p.getString("t"), p.getString("g"), List(sections.length()) { j ->
                    val s = sections.getJSONObject(j)
                    Section(s.getString("h"), strings(s.getJSONArray("p")))
                })
            }
        }
    }

    /** The 150 chapters, each a section titled "פרק …". */
    fun tehillim(ctx: Context): List<Section> = synchronized(this) {
        tehillim ?: run {
            val chapters = readJson(ctx, "texts/tehillim.json").getJSONArray("c")
            List(chapters.length()) { i ->
                val c = chapters.getJSONObject(i)
                Section(c.getString("t"), strings(c.getJSONArray("p")))
            }.also { tehillim = it }
        }
    }

    /** Text without HTML, vowels or cantillation, for searching. */
    fun plain(s: String) = s.replace(Regex("<[^>]*>"), "")
        .replace('־', ' ')
        .replace(Regex("[֑-ׇ]"), "")

    private fun readJson(ctx: Context, asset: String) =
        JSONObject(ctx.assets.open(asset).bufferedReader().use { it.readText() })

    private fun strings(a: JSONArray) = List(a.length()) { a.getString(it) }
}

/** A stretch of Tehillim: chapters [from]..[to], optionally only some verses of a single chapter. */
data class TehillimRange(val from: Int, val to: Int, val verseFrom: Int = 0, val verseTo: Int = 0) {
    fun label(): String {
        val f = JewishDates.formatter.formatHebrewNumber(from)
        val t = JewishDates.formatter.formatHebrewNumber(to)
        return when {
            verseFrom > 0 -> "פרק $f (${JewishDates.formatter.formatHebrewNumber(verseFrom)}–${JewishDates.formatter.formatHebrewNumber(verseTo)})"
            from == to -> "פרק $f"
            else -> "פרקים $f–$t"
        }
    }

    companion object {
        /** The monthly cycle: which chapters to say on each day of the Hebrew month. */
        private val MONTHLY = listOf(
            TehillimRange(1, 9), TehillimRange(10, 17), TehillimRange(18, 22), TehillimRange(23, 28),
            TehillimRange(29, 34), TehillimRange(35, 38), TehillimRange(39, 43), TehillimRange(44, 48),
            TehillimRange(49, 54), TehillimRange(55, 59), TehillimRange(60, 65), TehillimRange(66, 68),
            TehillimRange(69, 71), TehillimRange(72, 76), TehillimRange(77, 78), TehillimRange(79, 82),
            TehillimRange(83, 87), TehillimRange(88, 89), TehillimRange(90, 96), TehillimRange(97, 103),
            TehillimRange(104, 105), TehillimRange(106, 107), TehillimRange(108, 112), TehillimRange(113, 118),
            TehillimRange(119, 119, 1, 96), TehillimRange(119, 119, 97, 176), TehillimRange(120, 134),
            TehillimRange(135, 139), TehillimRange(140, 144), TehillimRange(145, 150),
        )

        /** The weekly cycle, Sunday first. */
        val WEEKLY = listOf(
            TehillimRange(1, 29), TehillimRange(30, 50), TehillimRange(51, 72), TehillimRange(73, 89),
            TehillimRange(90, 106), TehillimRange(107, 119), TehillimRange(120, 150),
        )

        /** In a 29-day month, the 29th also covers the 30th day's chapters. */
        fun forHebrewDay(day: Int, daysInMonth: Int): TehillimRange =
            if (day == 29 && daysInMonth == 29) TehillimRange(140, 150) else MONTHLY[day - 1]
    }
}
