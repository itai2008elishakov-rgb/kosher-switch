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
                view.textSize = size * 0.72f
                view.text = item.text
            } else {
                view.textSize = size
                view.text = Html.fromHtml(item.text, Html.FROM_HTML_MODE_LEGACY)
            }
            return view
        }
    }

    private fun headingView() = TextView(this).apply {
        setTextColor(Theme.GOLD_DARK)
        typeface = Theme.MEDIUM
        textDirection = View.TEXT_DIRECTION_RTL
        setPadding(0, Theme.dp(context, 22), 0, Theme.dp(context, 4))
    }

    private fun paragraphView() = TextView(this).apply {
        setTextColor(Theme.INK)
        typeface = android.graphics.Typeface.SERIF
        setLineSpacing(0f, 1.45f)
        textDirection = View.TEXT_DIRECTION_RTL
        setPadding(0, Theme.dp(context, 6), 0, Theme.dp(context, 6))
    }
}
