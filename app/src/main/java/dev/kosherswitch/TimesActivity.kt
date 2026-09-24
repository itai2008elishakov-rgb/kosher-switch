package dev.kosherswitch

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/** לוח וזמנים: today's Jewish date and prayer times, the month calendar, and upcoming holidays. */
class TimesActivity : BaseActivity() {
    private companion object {
        val HE = Locale("he")
        val WEEK = listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳")
    }

    private val today = LocalDate.now()
    private var date = today
    private var month = YearMonth.now()
    private var tab = 0
    private lateinit var tabs: LinearLayout
    private lateinit var content: LinearLayout
    private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.lightBars(this)
        tabs = LinearLayout(this).apply {
            background = Theme.rounded(context, Theme.CHIP, 22)
            val p = Theme.dp(context, 4)
            setPadding(p, p, p, p)
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Theme.dp(context, 14)
            setPadding(p, 0, p, Theme.dp(context, 32))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Theme.PAGE)
            addView(KosherPage.titleBar(this@TimesActivity, "לוח וזמנים"))
            addView(tabs, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { val m = Theme.dp(context, 14); setMargins(m, m, m, 0) })
            addView(ScrollView(context).apply { addView(content) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        render()
        Place.refresh(this) { render() }
    }

    private fun render() {
        tabs.removeAllViews()
        listOf("היום", "לוח חודשי", "מועדים").forEachIndexed { i, name ->
            tabs.addView(Theme.text(this, name, 15f, if (i == tab) Theme.HEADLINE else Theme.SUB, Theme.MEDIUM).apply {
                gravity = Gravity.CENTER
                if (i == tab) { background = Theme.rounded(context, Theme.CARD, 18); elevation = Theme.dp(context, 2).toFloat() }
                setPadding(0, Theme.dp(context, 9), 0, Theme.dp(context, 9))
                setOnClickListener { tab = i; render() }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.removeAllViews()
        when (tab) {
            0 -> renderDay()
            1 -> renderMonth()
            else -> renderHolidays()
        }
    }

    // ---- Today ----

    private fun renderDay() {
        content.addView(dayHero(date))
        val zc = Place.zmanim(this, date)
        if (zc == null) {
            content.addView(Theme.card(this,
                Theme.text(this, "מחפש מיקום…", 17f, Theme.INK, Theme.MEDIUM),
                Theme.text(this, "צריך את המיקום פעם אחת כדי לחשב את הזמנים. כדאי לעמוד ליד חלון.", 14f, Theme.SUB)))
            return
        }
        val jc = JewishDates.calendar(date)
        val shabbat = mutableListOf<Pair<String, Date?>>()
        if (jc.hasCandleLighting()) shabbat += "הדלקת נרות" to zc.candleLighting
        if (jc.isAssurBemelacha && !jc.hasCandleLighting()) shabbat += "צאת השבת / החג" to zc.tzaisGeonim8Point5Degrees
        if (shabbat.isNotEmpty()) content.addView(timeline("שבת וחג", shabbat, gold = true))

        val times = listOf(
            "עלות השחר" to zc.alosHashachar,
            "זמן טלית ותפילין" to zc.misheyakir10Point2Degrees,
            "הנץ החמה" to zc.sunrise,
            "סוף זמן ק״ש (מג״א)" to zc.sofZmanShmaMGA,
            "סוף זמן ק״ש (גר״א)" to zc.sofZmanShmaGRA,
            "סוף זמן תפילה" to zc.sofZmanTfilaGRA,
            "חצות היום" to zc.chatzos,
            "מנחה גדולה" to zc.minchaGedola,
            "מנחה קטנה" to zc.minchaKetana,
            "פלג המנחה" to zc.plagHamincha,
            "שקיעה" to zc.sunset,
            "צאת הכוכבים" to zc.tzais,
        )
        if (date == today) nextZman(times)?.let(content::addView)
        content.addView(timeline("זמני היום", times))
    }

    private fun dayHero(d: LocalDate) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Theme.skyGradient().apply { cornerRadius = Theme.dp(context, 24).toFloat() }
        val p = Theme.dp(context, 18)
        setPadding(p, Theme.dp(context, 8), p, p)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = Theme.dp(context, 14) }
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(arrow("→") { date = date.minusDays(1); render() })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(Theme.text(context, JewishDates.hebrewDate(d), 24f, Color.WHITE, Theme.MEDIUM).apply { gravity = Gravity.CENTER })
                addView(Theme.text(context, d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", HE)), 14f, 0xCCFFFFFF.toInt())
                    .apply { gravity = Gravity.CENTER })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(arrow("←") { date = date.plusDays(1); render() })
        })
        val chips = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            setPadding(0, Theme.dp(context, 12), 0, 0)
        }
        JewishDates.occasion(d)?.let { chips.addView(chip(it, gold = true)) }
        chips.addView(chip(JewishDates.parsha(d)))
        JewishDates.omer(d)?.let { chips.addView(chip(it)) }
        addView(chips)
        if (d != today) addView(Theme.text(context, "חזרה להיום", 14f, Theme.GOLD, Theme.MEDIUM).apply {
            gravity = Gravity.CENTER
            setPadding(0, Theme.dp(context, 10), 0, 0)
            setOnClickListener { date = today; render() }
        })
    }

    /** "Next: Sunset at 19:12 — in 1:23". */
    private fun nextZman(times: List<Pair<String, Date?>>): View? {
        val now = Date()
        val (name, time) = times.firstOrNull { it.second?.after(now) == true } ?: return null
        val mins = (time!!.time - now.time) / 60000
        val left = if (mins >= 60) "%d:%02d שעות".format(mins / 60, mins % 60) else "$mins דקות"
        return Theme.card(this,
            Theme.text(this, "הזמן הבא", 13f, Theme.SUB),
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(Theme.text(context, name, 20f, Theme.INK, Theme.MEDIUM),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(Theme.text(context, clock.format(time), 24f, Theme.LINK, Theme.MEDIUM))
            },
            Theme.text(this, "בעוד $left", 14f, Theme.GOLD_DARK, Theme.MEDIUM),
        )
    }

    /** A vertical timeline: passed times are faded, the next one is highlighted. */
    private fun timeline(title: String, times: List<Pair<String, Date?>>, gold: Boolean = false): LinearLayout {
        val card = Theme.card(this, Theme.text(this, title, 17f, if (gold) Theme.GOLD_DARK else Theme.INK, Theme.MEDIUM))
        val now = Date()
        val next = if (date == today) times.firstOrNull { it.second?.after(now) == true } else null
        times.forEach { (name, time) ->
            val past = date == today && time != null && time.before(now)
            val isNext = next?.first == name
            card.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                val v = Theme.dp(context, 9)
                setPadding(Theme.dp(context, 8), v, Theme.dp(context, 8), v)
                if (isNext) background = Theme.rounded(context, 0x141E4E9C, 14)
                addView(View(context).apply {
                    background = Theme.rounded(context, if (isNext) Theme.BLUE else if (past) Theme.LINE else Theme.GOLD, 5)
                }, LinearLayout.LayoutParams(Theme.dp(context, 10), Theme.dp(context, 10)).apply { marginEnd = Theme.dp(context, 12) })
                addView(Theme.text(context, name, 16f, if (past) Theme.SUB else Theme.INK, if (isNext) Theme.MEDIUM else Theme.REGULAR),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(Theme.text(context, time?.let(clock::format) ?: "—", 17f,
                    if (past) Theme.SUB else Theme.HEADLINE, Theme.MEDIUM))
            })
        }
        return card
    }

    // ---- Month ----

    private fun renderMonth() {
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(arrow("→", Theme.BLUE) { month = month.minusMonths(1); render() })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val first = JewishDates.calendar(month.atDay(1))
                val last = JewishDates.calendar(month.atEndOfMonth())
                val hebrew = listOf(first, last).map { JewishDates.formatter.formatMonth(it) }.distinct().joinToString(" – ")
                addView(Theme.text(context, hebrew + " " + JewishDates.formatter.formatHebrewNumber(last.jewishYear), 18f, Theme.INK, Theme.MEDIUM)
                    .apply { gravity = Gravity.CENTER })
                addView(Theme.text(context, month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", HE)), 13f, Theme.SUB)
                    .apply { gravity = Gravity.CENTER })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(arrow("←", Theme.BLUE) { month = month.plusMonths(1); render() })
        }
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Theme.dp(context, 8), 0, 0)
        }
        grid.addView(row(WEEK.mapIndexed { i, d ->
            Theme.text(this, d, 13f, if (i == 6) Theme.BLUE else Theme.SUB, Theme.MEDIUM).apply { gravity = Gravity.CENTER }
        }, Theme.dp(this, 28)))
        val lead = month.atDay(1).dayOfWeek.value % 7 // Sunday first
        val cells = List<LocalDate?>(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week -> grid.addView(row(week.map { cell(it) } + List(7 - week.size) { View(this) }, Theme.dp(this, 62))) }
        content.addView(Theme.card(this, header, grid))

        // Details of the chosen day.
        val lines = mutableListOf<View>(
            Theme.text(this, JewishDates.hebrewDate(date), 20f, Theme.HEADLINE, Theme.MEDIUM),
            Theme.text(this, date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", HE)), 14f, Theme.SUB),
        )
        JewishDates.occasion(date)?.let { lines += Theme.text(this, it, 16f, Theme.GOLD_DARK, Theme.MEDIUM) }
        lines += Theme.text(this, JewishDates.parsha(date), 15f)
        lines += Theme.text(this, "דף יומי: ${JewishDates.dafYomi(date)}", 14f, Theme.SUB)
        lines += Theme.button(this, "זמני היום לתאריך הזה", outline = true) { tab = 0; render() }
        content.addView(Theme.card(this, *lines.toTypedArray()))
    }

    private fun row(views: List<View>, height: Int) = LinearLayout(this).apply {
        views.forEach { addView(it, LinearLayout.LayoutParams(0, height, 1f)) }
    }

    private fun cell(d: LocalDate?): View {
        if (d == null) return View(this)
        val occasion = JewishDates.occasion(d)
        val selected = d == date
        val shabbat = d.dayOfWeek == DayOfWeek.SATURDAY
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val m = Theme.dp(context, 2)
            setPadding(m, m, m, m)
            background = when {
                selected -> Theme.rounded(context, Theme.BLUE, 14)
                d == today -> Theme.rounded(context, Color.TRANSPARENT, 14, Theme.BLUE)
                shabbat -> Theme.rounded(context, 0x0F1E4E9C, 14)
                else -> null
            }
            addView(Theme.text(context, JewishDates.dayNumber(d), 18f,
                if (selected) Color.WHITE else if (shabbat) Theme.LINK else Theme.INK, Theme.MEDIUM).apply { gravity = Gravity.CENTER })
            addView(Theme.text(context, d.dayOfMonth.toString(), 11f,
                if (selected) 0xCCFFFFFF.toInt() else Theme.SUB).apply { gravity = Gravity.CENTER })
            addView(View(context).apply {
                background = Theme.rounded(context, if (occasion != null) Theme.GOLD else Color.TRANSPARENT, 3)
            }, LinearLayout.LayoutParams(Theme.dp(context, 6), Theme.dp(context, 6)).apply { topMargin = Theme.dp(context, 2) })
            Theme.pressable(this)
            setOnClickListener { date = d; render() }
        }
    }

    // ---- Holidays ----

    private fun renderHolidays() {
        var d = today
        var last: String? = null
        var shown = 0
        while (shown < 30 && d.isBefore(today.plusDays(400))) {
            val o = JewishDates.occasion(d)
            if (o != null && o != last) {
                val days = ChronoUnit.DAYS.between(today, d)
                val day = d
                content.addView(LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = Theme.rounded(context, Theme.CARD, 18)
                    val p = Theme.dp(context, 14)
                    setPadding(p, p, p, p)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .apply { topMargin = Theme.dp(context, 10) }
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(Theme.text(context, o, 17f, Theme.INK, Theme.MEDIUM))
                        addView(Theme.text(context, "${JewishDates.hebrewDate(day)} · ${day.format(DateTimeFormatter.ofPattern("d MMM", HE))}", 13f, Theme.SUB))
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(Theme.text(context, when (days) { 0L -> "היום"; 1L -> "מחר"; else -> "בעוד $days ימים" }, 13f, Theme.GOLD_DARK, Theme.MEDIUM))
                    Theme.pressable(this)
                    setOnClickListener { date = day; month = YearMonth.from(day); tab = 0; render() }
                })
                shown++
            }
            last = o
            d = d.plusDays(1)
        }
    }

    // ---- Small pieces ----

    private fun chip(text: String, gold: Boolean = false) = Theme.text(this, text, 13f, if (gold) Theme.NAVY else Color.WHITE, Theme.MEDIUM).apply {
        background = if (gold) Theme.rounded(context, Theme.GOLD, 14) else Theme.glass(context, 14)
        val h = Theme.dp(context, 12)
        setPadding(h, Theme.dp(context, 5), h, Theme.dp(context, 5))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { val m = Theme.dp(context, 3); setMargins(m, m, m, m) }
    }

    private fun arrow(label: String, color: Int = Color.WHITE, onClick: () -> Unit): TextView =
        Theme.text(this, label, 22f, color, Theme.MEDIUM).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(Theme.dp(context, 44), Theme.dp(context, 44))
            Theme.pressable(this)
            setOnClickListener { onClick() }
        }
}
