package dev.kosherswitch

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate

/** סידור: the siddur in the chosen nusach and Tehillim together, with tabs and search. */
class SiddurActivity : BaseActivity() {
    private companion object {
        const val TEHILLIM = "tehillim"
        val DAY_NAMES = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")
    }

    /** One searchable paragraph: prayer [prayer] (or Tehillim when -1), [section], [paragraph]. */
    private class Entry(val prayer: Int, val section: Int, val paragraph: Int, val plain: String, val place: String)

    private var nusach = "sefard"
    private var prayers: List<Prayer> = emptyList()
    private var tab = "weekday"
    private var index: List<Entry>? = null
    private val main = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null

    private lateinit var nusachChip: TextView
    private lateinit var search: EditText
    private lateinit var tabs: LinearLayout
    private lateinit var tabsScroll: HorizontalScrollView
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.lightBars(this)
        buildShell()
        val saved = Texts.nusach(this)
        if (saved == null) chooseNusach(firstTime = true) else loadNusach(saved)
    }

    private fun buildShell() {
        nusachChip = Theme.text(this, "", 14f, Color.WHITE, Theme.MEDIUM).apply {
            background = Theme.glass(context, 18)
            val h = Theme.dp(context, 14)
            setPadding(h, Theme.dp(context, 7), h, Theme.dp(context, 7))
            Theme.pressable(this)
            setOnClickListener { chooseNusach(firstTime = false) }
        }
        search = EditText(this).apply {
            hint = "חיפוש תפילה, ברכה או פסוק"
            textSize = 16f
            typeface = Theme.REGULAR
            setTextColor(Theme.INK)
            setHintTextColor(Theme.SUB)
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            textDirection = View.TEXT_DIRECTION_RTL
            background = Theme.rounded(context, Theme.CARD, 24)
            val p = Theme.dp(context, 16)
            setPadding(p, Theme.dp(context, 12), p, Theme.dp(context, 12))
            setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0)
            compoundDrawablePadding = Theme.dp(context, 8)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) = scheduleSearch()
                override fun beforeTextChanged(s: CharSequence, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence, a: Int, b: Int, c: Int) = Unit
            })
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Theme.skyGradient().apply {
                cornerRadii = FloatArray(8) { i -> if (i >= 4) Theme.dp(context, 28).toFloat() else 0f }
            }
            val p = Theme.dp(context, 18)
            setPadding(p, Theme.dp(context, 14), p, p)
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(Theme.text(context, "סידור", 30f, Color.WHITE, Theme.MEDIUM),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(nusachChip)
            })
            addView(search, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Theme.dp(context, 14) })
        }
        tabs = LinearLayout(this).apply {
            val p = Theme.dp(context, 14)
            setPadding(p, Theme.dp(context, 12), p, Theme.dp(context, 4))
        }
        tabsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
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
            addView(header)
            addView(tabsScroll)
            addView(ScrollView(context).apply { addView(content) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    private fun chooseNusach(firstTime: Boolean) {
        val keys = Texts.NUSACHOT.keys.toList()
        KosherPage.sheet(this, "באיזה נוסח אתה מתפלל?", Texts.NUSACHOT.values.toList(), cancelable = !firstTime) { i ->
            Texts.setNusach(this, keys[i])
            loadNusach(keys[i])
        }
    }

    private fun loadNusach(key: String) {
        nusach = key
        index = null
        nusachChip.text = "נוסח ${Texts.NUSACHOT[key]}  ▾"
        Thread {
            val book = Texts.book(this, key)
            runOnUiThread {
                prayers = book
                renderTabs()
                render()
                tabsScroll.post { tabsScroll.fullScroll(View.FOCUS_RIGHT) }
            }
        }.start()
    }

    private fun renderTabs() {
        tabs.removeAllViews()
        val groups = Texts.GROUPS.filterKeys { g -> prayers.any { it.group == g } } + (TEHILLIM to "תהלים")
        groups.forEach { (key, name) ->
            val selected = key == tab
            tabs.addView(Theme.text(this, name, 15f, if (selected) Color.WHITE else Theme.INK, Theme.MEDIUM).apply {
                background = if (selected) Theme.rounded(context, Theme.NAVY, 20) else Theme.rounded(context, Theme.CARD, 20, Theme.LINE)
                val h = Theme.dp(context, 18)
                setPadding(h, Theme.dp(context, 9), h, Theme.dp(context, 9))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { marginEnd = Theme.dp(context, 8) }
                Theme.pressable(this)
                setOnClickListener {
                    tab = key
                    renderTabs()
                    render()
                }
            })
        }
    }

    private fun render() {
        content.removeAllViews()
        if (tab == TEHILLIM) renderTehillim() else renderPrayers()
        content.alpha = 0f
        content.translationY = Theme.dp(this, 12).toFloat()
        content.animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    // ---- Prayers ----

    private fun renderPrayers() {
        if (tab == "weekday" || tab == "shabbat") nowCard()?.let(content::addView)
        prayers.forEachIndexed { i, p ->
            if (p.group == tab) content.addView(prayerCard(p, i))
        }
    }

    /** "Now: Mincha" — the prayer that fits the time of day (Shabbat prayers on Shabbat). */
    private fun nowCard(): View? {
        val today = LocalDate.now()
        val which = JewishDates.currentPrayer(this)
        val shabbat = today.dayOfWeek == java.time.DayOfWeek.SATURDAY ||
            (today.dayOfWeek == java.time.DayOfWeek.FRIDAY && which == "arvit")
        val group = if (shabbat) "shabbat" else "weekday"
        if (group != tab) return null
        val words = when {
            shabbat && today.dayOfWeek == java.time.DayOfWeek.FRIDAY -> listOf("קבלת שבת")
            which == "shacharit" -> listOf("שחרית")
            which == "mincha" -> listOf("מנחה")
            else -> listOf("ערבית", "מעריב")
        }
        val i = prayers.indexOfFirst { p -> p.group == group && words.any { p.title.contains(it) } }
        if (i < 0) return null
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Theme.skyGradient().apply { cornerRadius = Theme.dp(context, 22).toFloat() }
            val p = Theme.dp(context, 18)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Theme.dp(context, 12) }
            addView(Theme.text(context, "עכשיו", 13f, Theme.GOLD, Theme.MEDIUM))
            addView(Theme.text(context, prayers[i].title, 22f, Color.WHITE, Theme.MEDIUM))
            addView(Theme.button(context, "פתיחת התפילה  ←", fill = Theme.GOLD, textColor = Theme.NAVY) { openPrayer(i) })
        }
    }

    private fun prayerCard(p: Prayer, i: Int): View {
        val preview = p.sections.map { it.heading.substringAfterLast(" · ") }.distinct().take(5).joinToString(" · ")
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = Theme.rounded(context, Theme.CARD, 20)
            elevation = Theme.dp(context, 1).toFloat()
            val pad = Theme.dp(context, 16)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Theme.dp(context, 10) }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(Theme.text(context, p.title, 19f, Theme.INK, Theme.MEDIUM))
                if (p.sections.size > 1) addView(Theme.text(context, preview, 13f, Theme.SUB).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Theme.text(context, "←", 20f, Theme.SUB).apply { setPadding(Theme.dp(context, 8), 0, 0, 0) })
            Theme.pressable(this)
            setOnClickListener { openPrayer(i) }
        }
    }

    private fun openPrayer(i: Int, section: Int = 0, paragraph: Int = 0) =
        startActivity(ReaderActivity.prayer(this, nusach, i, section, paragraph))

    // ---- Tehillim ----

    private fun renderTehillim() {
        val today = LocalDate.now()
        val jc = JewishDates.calendar(today)
        val daily = TehillimRange.forHebrewDay(jc.jewishDayOfMonth, jc.daysInJewishMonth)
        val weekday = today.dayOfWeek.value % 7
        val weekly = TehillimRange.WEEKLY[weekday]
        content.addView(LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Theme.dp(context, 12) }
            addView(cycleCard("תהלים יומי", "${JewishDates.dayNumber(today)} בחודש", daily),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = Theme.dp(context, 6) })
            addView(cycleCard("לפי השבוע", "יום ${DAY_NAMES[weekday]}", weekly),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = Theme.dp(context, 6) })
        })
        content.addView(Theme.text(this, "ספר תהלים", 17f, Theme.INK, Theme.MEDIUM).apply {
            setPadding(Theme.dp(context, 4), Theme.dp(context, 20), 0, Theme.dp(context, 4))
        })
        (1..150).chunked(5).forEach { row ->
            content.addView(LinearLayout(this).apply {
                row.forEach { n ->
                    addView(Theme.text(context, JewishDates.formatter.formatHebrewNumber(n), 18f, Theme.HEADLINE, Theme.MEDIUM).apply {
                        gravity = Gravity.CENTER
                        background = Theme.rounded(context, Theme.CARD, 14)
                        Theme.pressable(this)
                        setOnClickListener { startActivity(ReaderActivity.tehillim(context, TehillimRange(n, n))) }
                    }, LinearLayout.LayoutParams(0, Theme.dp(context, 52), 1f).apply {
                        val m = Theme.dp(context, 4)
                        setMargins(m, m, m, m)
                    })
                }
            })
        }
    }

    private fun cycleCard(title: String, subtitle: String, range: TehillimRange) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Theme.skyGradient().apply { cornerRadius = Theme.dp(context, 20).toFloat() }
        val p = Theme.dp(context, 16)
        setPadding(p, p, p, p)
        addView(Theme.text(context, title, 17f, Color.WHITE, Theme.MEDIUM))
        addView(Theme.text(context, subtitle, 13f, 0xCCFFFFFF.toInt()))
        addView(Theme.text(context, range.label(), 15f, Theme.GOLD, Theme.MEDIUM).apply {
            setPadding(0, Theme.dp(context, 8), 0, 0)
        })
        Theme.pressable(this)
        setOnClickListener { startActivity(ReaderActivity.tehillim(context, range)) }
    }

    // ---- Search ----

    private fun scheduleSearch() {
        pendingSearch?.let(main::removeCallbacks)
        val q = Texts.plain(search.text.toString()).trim()
        if (q.length < 2) {
            tabsScroll.visibility = View.VISIBLE
            render()
            return
        }
        tabsScroll.visibility = View.GONE
        pendingSearch = Runnable { runSearch(q) }.also { main.postDelayed(it, 250) }
    }

    private fun runSearch(q: String) {
        Thread {
            val entries = index ?: buildIndex().also { index = it }
            val hits = entries.filter { it.plain.contains(q) }.take(80)
            runOnUiThread { if (Texts.plain(search.text.toString()).trim() == q) showResults(q, hits) }
        }.start()
    }

    private fun buildIndex(): List<Entry> {
        val out = mutableListOf<Entry>()
        prayers.forEachIndexed { pi, p ->
            p.sections.forEachIndexed { si, s ->
                val place = if (p.sections.size > 1) "${p.title} · ${s.heading}" else p.title
                out += Entry(pi, si, -1, Texts.plain("${p.title} ${s.heading}"), place)
                s.paragraphs.forEachIndexed { ti, t -> out += Entry(pi, si, ti, Texts.plain(t), place) }
            }
        }
        Texts.tehillim(this).forEachIndexed { ci, c ->
            c.paragraphs.forEachIndexed { vi, v -> out += Entry(-1, ci, vi, Texts.plain(v), "תהלים · ${c.heading}") }
        }
        return out
    }

    private fun showResults(q: String, hits: List<Entry>) {
        content.removeAllViews()
        content.addView(Theme.text(this, if (hits.isEmpty()) "לא נמצאו תוצאות" else "תוצאות", 15f, Theme.SUB).apply {
            setPadding(Theme.dp(context, 4), Theme.dp(context, 14), 0, 0)
        })
        hits.forEach { e ->
            val at = e.plain.indexOf(q)
            val snippet = if (e.paragraph < 0) "" else
                (if (at > 40) "…" else "") + e.plain.substring((at - 40).coerceAtLeast(0), (at + q.length + 60).coerceAtMost(e.plain.length))
            content.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = Theme.rounded(context, Theme.CARD, 18)
                val p = Theme.dp(context, 14)
                setPadding(p, p, p, p)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = Theme.dp(context, 8) }
                addView(Theme.text(context, e.place, 15f, Theme.LINK, Theme.MEDIUM))
                if (snippet.isNotEmpty()) addView(Theme.text(context, snippet, 15f).apply {
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
                Theme.pressable(this)
                setOnClickListener {
                    if (e.prayer < 0) {
                        startActivity(ReaderActivity.tehillim(context, TehillimRange(e.section + 1, e.section + 1), e.paragraph.coerceAtLeast(0)))
                    } else openPrayer(e.prayer, e.section, e.paragraph.coerceAtLeast(0))
                }
            })
        }
    }
}
