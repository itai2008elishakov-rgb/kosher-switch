package dev.kosherswitch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Reads one whole prayer (or a stretch of Tehillim) as a single scrolling text.
 * Section titles appear inside the text, and "חלקים" jumps straight to any section.
 */
class ReaderActivity : BaseActivity() {
    companion object {
        private const val KEY_FONT = "reader_font_size"
        private const val EXTRA_NUSACH = "nusach"
        private const val EXTRA_PRAYER = "prayer"
        private const val EXTRA_TEHILLIM = "tehillim"
        private const val EXTRA_SECTION = "section"
        private const val EXTRA_PARAGRAPH = "paragraph"

        fun prayer(ctx: Context, nusach: String, index: Int, section: Int = 0, paragraph: Int = 0) =
            Intent(ctx, ReaderActivity::class.java).putExtra(EXTRA_NUSACH, nusach).putExtra(EXTRA_PRAYER, index)
                .putExtra(EXTRA_SECTION, section).putExtra(EXTRA_PARAGRAPH, paragraph)

        fun tehillim(ctx: Context, range: TehillimRange, paragraph: Int = 0) =
            Intent(ctx, ReaderActivity::class.java)
                .putExtra(EXTRA_TEHILLIM, intArrayOf(range.from, range.to, range.verseFrom, range.verseTo))
                .putExtra(EXTRA_PARAGRAPH, paragraph)
    }

    /** One row of the reader: a section title or a paragraph. */
    private class Item(val text: String, val heading: Boolean, val section: Int)

    private var size = 22f
    private lateinit var list: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.lightBars(this)
        size = prefs(this).getFloat(KEY_FONT, 22f)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Theme.PAPER)
            val size = Theme.dp(context, 72)
            addView(EmblemView(context).apply {
                animate().scaleX(1.08f).scaleY(1.08f).setDuration(600).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(600).start()
                }.start()
            }, LinearLayout.LayoutParams(size, size))
            addView(Theme.text(context, getString(R.string.loading), 15f, Theme.SUB).apply {
                gravity = Gravity.CENTER
                setPadding(0, Theme.dp(context, 12), 0, 0)
            })
        })
        Thread {
            val (title, sections) = load()
            runOnUiThread { if (!isFinishing) show(title, sections) }
        }.start()
    }

    private fun load(): Pair<String, List<Section>> {
        val t = intent.getIntArrayExtra(EXTRA_TEHILLIM)
        if (t != null) {
            val range = TehillimRange(t[0], t[1], t[2], t[3])
            val chapters = Texts.tehillim(this).subList(range.from - 1, range.to)
            val sections = if (range.verseFrom > 0) {
                listOf(Section(chapters[0].heading, chapters[0].paragraphs.subList(range.verseFrom - 1, range.verseTo)))
            } else chapters
            return "תהלים · ${range.label()}" to sections
        }
        val prayer = Texts.book(this, intent.getStringExtra(EXTRA_NUSACH)!!)[intent.getIntExtra(EXTRA_PRAYER, 0)]
        return prayer.title to prayer.sections
    }

    private fun show(title: String, sections: List<Section>) {
        val items = mutableListOf<Item>()
        val sectionStart = IntArray(sections.size)
        sections.forEachIndexed { i, s ->
            sectionStart[i] = items.size
            // A lone section named like the prayer itself doesn't need a title.
            if (sections.size > 1 || s.heading != title) items += Item(s.heading, true, i)
            s.paragraphs.forEach { items += Item(it, false, i) }
        }

        list = ListView(this).apply {
            divider = null
            setBackgroundColor(Theme.PAPER)
            val p = Theme.dp(context, 22)
            setPadding(p, Theme.dp(context, 4), p, Theme.dp(context, 48))
            clipToPadding = false
            isFastScrollEnabled = items.size > 60
            adapter = ItemAdapter(items)
        }
        val actions = mutableListOf<View>(
            KosherPage.barButton(this, "א+") { setSize(size + 2) },
            KosherPage.barButton(this, "א−") { setSize(size - 2) },
        )
        if (sections.size > 1) {
            actions.add(0, KosherPage.barButton(this, "☰") {
                KosherPage.sheet(this, "חלקים", sections.map { it.heading }) { i -> list.setSelection(sectionStart[i]) }
            })
        }
        setContentView(KosherPage.page(this, KosherPage.titleBar(this, title, *actions.toTypedArray()), list))

        // Jump to the requested place (from search or the parts list).
        val section = intent.getIntExtra(EXTRA_SECTION, 0).coerceIn(0, sections.size - 1)
        val paragraph = intent.getIntExtra(EXTRA_PARAGRAPH, 0)
        val target = sectionStart[section] + paragraph + (if (items[sectionStart[section]].heading) 1 else 0)
        if (section > 0 || paragraph > 0) list.setSelection((target - 1).coerceIn(0, items.size - 1))
    }

    private fun setSize(s: Float) {
        size = s.coerceIn(14f, 40f)
        prefs(this).edit().putFloat(KEY_FONT, size).apply()
        (list.adapter as BaseAdapter).notifyDataSetChanged()
    }

    private inner class ItemAdapter(val items: List<Item>) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(i: Int) = items[i]
        override fun getItemId(i: Int) = i.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(i: Int) = if (items[i].heading) 0 else 1

        override fun getView(i: Int, recycled: View?, parent: ViewGroup): View {
            val item = items[i]
            val view = recycled as? TextView ?: if (item.heading) headingView() else paragraphView()
            if (item.heading) {
                // The part's name in gold, centred; its group (e.g. "עמידה") small above it.
                val parts = item.text.split(" · ")
                val sb = android.text.SpannableStringBuilder()
                if (parts.size > 1) {
                    sb.append(parts.dropLast(1).joinToString(" · "))
                    sb.setSpan(android.text.style.RelativeSizeSpan(0.62f), 0, sb.length, 0)
                    sb.setSpan(android.text.style.ForegroundColorSpan(Theme.SUB), 0, sb.length, 0)
                    sb.append("\n")
                }
                val start = sb.length
                sb.append(parts.last())
                sb.setSpan(android.text.style.ForegroundColorSpan(if (Theme.dark) Theme.GOLD else Theme.GOLD_DARK), start, sb.length, 0)
                view.textSize = size * 0.85f
                view.text = sb
            } else {
                val instruction = Texts.isInstruction(item.text)
                view.textSize = if (instruction) size * 0.66f else size
                view.setLineSpacing(0f, if (instruction) 1.2f else 1.5f)
                view.setPadding(0, Theme.dp(view.context, if (instruction) 12 else 7), 0, Theme.dp(view.context, 7))
                view.typeface = if (instruction) Theme.REGULAR else android.graphics.Typeface.SERIF
                view.text = styled(item.text, instruction)
            }
            return view
        }
    }

    /** Prayer text in the reading colour; <small> instructions smaller and gold-toned, like a printed siddur. */
    private fun styled(html: String, instruction: Boolean): CharSequence {
        val rubric = if (Theme.dark) 0xFFE9CF8A.toInt() else Theme.GOLD_DARK
        val sp = android.text.SpannableStringBuilder(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY))
        if (instruction) {
            sp.setSpan(android.text.style.ForegroundColorSpan(rubric), 0, sp.length, 0)
        } else {
            sp.getSpans(0, sp.length, android.text.style.RelativeSizeSpan::class.java).forEach { r ->
                val a = sp.getSpanStart(r); val b = sp.getSpanEnd(r)
                sp.removeSpan(r)
                sp.setSpan(android.text.style.RelativeSizeSpan(0.68f), a, b, 0)
                sp.setSpan(android.text.style.ForegroundColorSpan(rubric), a, b, 0)
            }
        }
        return sp
    }

    private fun headingView() = TextView(this).apply {
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER
        textDirection = View.TEXT_DIRECTION_RTL
        setPadding(0, Theme.dp(context, 30), 0, Theme.dp(context, 10))
    }

    private fun paragraphView() = TextView(this).apply {
        setTextColor(Theme.INK)
        typeface = android.graphics.Typeface.SERIF
        setLineSpacing(0f, 1.45f)
        textDirection = View.TEXT_DIRECTION_RTL
        setPadding(0, Theme.dp(context, 6), 0, Theme.dp(context, 6))
    }
}
