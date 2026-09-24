package dev.kosherswitch

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.format.DateUtils
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/** פתקים: pinned notes first, then newest; search; hold a note to pin or delete it. */
class NotesActivity : BaseActivity() {
    private lateinit var list: LinearLayout
    private lateinit var search: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.lightBars(this)
        search = EditText(this).apply {
            hint = getString(R.string.search_notes)
            setSingleLine()
            textSize = 15f
            setTextColor(Theme.INK)
            setHintTextColor(Theme.SUB)
            background = Theme.rounded(context, Theme.CHIP, 16)
            val p = Theme.dp(context, 14)
            setPadding(p, Theme.dp(context, 10), p, Theme.dp(context, 10))
            setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) = refresh(animate = false)
                override fun beforeTextChanged(s: CharSequence, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence, a: Int, b: Int, c: Int) = Unit
            })
        }
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Theme.dp(context, 16)
            setPadding(p, Theme.dp(context, 10), p, Theme.dp(context, 110))
            addView(search)
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list.addView(content)
        val add = Theme.text(this, "+", 30f, Theme.NAVY, Theme.MEDIUM).apply {
            gravity = Gravity.CENTER
            background = Theme.rounded(context, Theme.GOLD, 30)
            elevation = Theme.dp(context, 6).toFloat()
            Theme.pressable(this)
            setOnClickListener { startActivity(NoteEditActivity.intent(context, null)) }
        }
        setContentView(FrameLayout(this).apply {
            addView(KosherPage.page(this@NotesActivity, KosherPage.titleBar(this@NotesActivity, getString(R.string.notes)),
                ScrollView(context).apply { addView(list) }))
            addView(add, FrameLayout.LayoutParams(Theme.dp(context, 60), Theme.dp(context, 60), Gravity.BOTTOM or Gravity.END).apply {
                val m = Theme.dp(context, 24)
                setMargins(m, m, m, m + Theme.dp(context, 8))
            })
        })
    }

    override fun onResume() {
        super.onResume()
        refresh(animate = true)
    }

    private fun refresh(animate: Boolean) {
        val content = list.getChildAt(1) as LinearLayout
        content.removeAllViews()
        val q = search.text.toString().trim().lowercase()
        val notes = Notes.all(this).filter { q.isEmpty() || it.title.lowercase().contains(q) || it.plain.lowercase().contains(q) }
        if (notes.isEmpty()) {
            content.addView(Theme.text(this, getString(if (q.isEmpty()) R.string.no_notes else R.string.no_results), 16f, Theme.SUB).apply {
                gravity = Gravity.CENTER
                setPadding(0, Theme.dp(context, 80), 0, 0)
            })
            return
        }
        var lastPinned: Boolean? = null
        notes.forEachIndexed { i, n ->
            if (n.pinned != lastPinned && notes.any { it.pinned }) {
                content.addView(Theme.text(this, getString(if (n.pinned) R.string.pinned else R.string.all_notes), 13f, Theme.SUB, Theme.MEDIUM).apply {
                    setPadding(Theme.dp(context, 4), Theme.dp(context, 16), 0, 0)
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                })
                lastPinned = n.pinned
            }
            content.addView(card(n).apply {
                if (animate) {
                    alpha = 0f
                    translationY = Theme.dp(context, 12).toFloat()
                    animate().alpha(1f).translationY(0f).setStartDelay(i * 30L).setDuration(220).start()
                }
            })
        }
    }

    private fun card(n: Note) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = Theme.rounded(context, Theme.CARD, 18)
        val p = Theme.dp(context, 16)
        setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = Theme.dp(context, 10) }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val title = (if (n.pinned) "📌 " else "") + n.title.ifBlank { n.plain.lineSequence().firstOrNull().orEmpty() }
            addView(Theme.text(context, title, 17f, Theme.INK, Theme.MEDIUM).apply { oneLine() })
            addView(Theme.text(context, n.plain.replace('\n', ' '), 14f, Theme.SUB).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            })
            val extras = listOfNotNull(
                DateUtils.getRelativeTimeSpanString(n.updated).toString(),
                if (n.checkboxes > 0) "☑ ${n.checked}/${n.checkboxes}" else null,
                if (n.sketches.isNotEmpty()) "✎ ${n.sketches.size}" else null,
            )
            addView(Theme.text(context, extras.joinToString("  ·  "), 12f, Theme.SUB).apply {
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                setPadding(0, Theme.dp(context, 6), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        // Thumbnail of the first drawing.
        n.sketches.firstOrNull()?.let { name ->
            BitmapFactory.decodeFile(File(Notes.sketchDir(context), name).absolutePath)?.let { bmp ->
                addView(ImageView(context).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = Theme.rounded(context, Theme.LINE, 10)
                    clipToOutline = true
                }, LinearLayout.LayoutParams(Theme.dp(context, 56), Theme.dp(context, 56)).apply { marginStart = Theme.dp(context, 12) })
            }
        }
        Theme.pressable(this)
        setOnClickListener { startActivity(NoteEditActivity.intent(context, n.id)) }
        setOnLongClickListener {
            Theme.haptic(it, strong = true)
            val pin = getString(if (n.pinned) R.string.unpin else R.string.pin)
            KosherPage.sheet(this@NotesActivity, n.title.ifBlank { getString(R.string.notes) }, listOf(pin, getString(R.string.delete))) { i ->
                if (i == 0) Notes.setPinned(context, n.id, !n.pinned) else Notes.delete(context, n.id)
                refresh(animate = false)
            }
            true
        }
    }

    private fun TextView.oneLine() {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    }
}

/**
 * Writing a note: bold, italic, underline, headings, bullet lists, tap-to-tick checklists
 * and drawings. Saves by itself when you leave.
 */
class NoteEditActivity : BaseActivity() {
    companion object {
        private const val EXTRA_ID = "id"
        private const val DRAW = 1
        private const val BULLET = "• "
        private const val BOX = "☐ "
        private const val TICKED = "☑ "

        fun intent(ctx: Context, id: Long?) = Intent(ctx, NoteEditActivity::class.java).apply { if (id != null) putExtra(EXTRA_ID, id) }
    }

    private var note = Note(System.currentTimeMillis(), "", "", 0)
    private var isNew = true
    private var deleted = false
    private var editingSketch: String? = null
    private var listGuard = false
    private lateinit var title: EditText
    private lateinit var body: EditText
    private lateinit var sketchRow: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.lightBars(this)
        intent.getLongExtra(EXTRA_ID, -1).takeIf { it >= 0 }?.let { id ->
            Notes.get(this, id)?.let { note = it; isNew = false }
        }
        title = field(R.string.note_title, 24f, true).apply { setText(note.title) }
        body = field(R.string.note_body, 17f, false).apply {
            setText(Html.fromHtml(note.html, Html.FROM_HTML_MODE_COMPACT).trimEnd())
            gravity = Gravity.TOP or Gravity.START
            minLines = 10
            setLineSpacing(0f, 1.35f)
            addTextChangedListener(ListContinuation())
            setOnTouchListener { v, e -> tickBox(e) || v.onTouchEvent(e) }
        }
        sketchRow = LinearLayout(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Theme.dp(context, 20)
            setPadding(p, Theme.dp(context, 8), p, p)
            addView(title)
            addView(Theme.text(context, DateUtils.getRelativeTimeSpanString(note.updated.takeIf { it > 0 } ?: System.currentTimeMillis()).toString(),
                12f, Theme.SUB).apply { textAlignment = View.TEXT_ALIGNMENT_VIEW_START })
            addView(body)
            addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(sketchRow) })
        }
        val pin = KosherPage.barButton(this, "📌") {
            note = note.copy(pinned = !note.pinned)
            Theme.haptic(body)
            android.widget.Toast.makeText(this, getString(if (note.pinned) R.string.pinned else R.string.unpinned), android.widget.Toast.LENGTH_SHORT).show()
        }
        val delete = KosherPage.barButton(this, "🗑") {
            Notes.delete(this, note.id)
            deleted = true
            finish()
        }
        setContentView(KosherPage.page(this, KosherPage.titleBar(this, getString(R.string.notes), pin, delete),
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Theme.PAGE)
                addView(ScrollView(context).apply { addView(column) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(toolbar())
            }))
        showSketches()
        if (isNew) body.requestFocus()
    }

    private fun field(hintRes: Int, size: Float, bold: Boolean) = EditText(this).apply {
        hint = getString(hintRes)
        textSize = size
        typeface = if (bold) Theme.MEDIUM else Theme.REGULAR
        setTextColor(Theme.INK)
        setHintTextColor(Theme.SUB)
        background = null
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    }

    // ---- Toolbar ----

    private fun toolbar() = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        setBackgroundColor(Theme.CARD)
        elevation = Theme.dp(context, 8).toFloat()
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            val p = Theme.dp(context, 8)
            setPadding(p, p, p, p)
            addView(tool("B", Typeface.BOLD) { toggleSpan { StyleSpan(Typeface.BOLD) } })
            addView(tool("I", Typeface.ITALIC) { toggleSpan { StyleSpan(Typeface.ITALIC) } })
            addView(tool("U") { toggleSpan { UnderlineSpan() } }.apply { paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG })
            addView(tool("H") { heading() })
            addView(tool("•") { toggleLinePrefix(BULLET) })
            addView(tool("☑") { toggleLinePrefix(BOX) })
            addView(tool("✎  " + getString(R.string.draw)) {
                editingSketch = null
                @Suppress("DEPRECATION")
                startActivityForResult(SketchActivity.intent(this@NoteEditActivity, null), DRAW)
            })
        })
    }

    private fun tool(label: String, style: Int = Typeface.NORMAL, onClick: () -> Unit) =
        Theme.text(this, label, 17f, Theme.INK, Theme.MEDIUM).apply {
            typeface = Typeface.create(Theme.MEDIUM, style)
            gravity = Gravity.CENTER
            minWidth = Theme.dp(context, 46)
            val h = Theme.dp(context, 12)
            setPadding(h, Theme.dp(context, 8), h, Theme.dp(context, 8))
            background = Theme.rounded(context, Theme.CHIP, 14)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = Theme.dp(context, 8) }
            Theme.pressable(this)
            setOnClickListener { onClick() }
        }

    /** Adds the style to the selected text, or removes it if the whole selection already has it. */
    private inline fun <reified T : Any> toggleSpan(crossinline make: () -> T) {
        val t = body.text
        val start = body.selectionStart
        val end = body.selectionEnd
        if (start == end) return
        val existing = t.getSpans(start, end, T::class.java).filter {
            (it !is StyleSpan || (make() as? StyleSpan)?.style == it.style) && t.getSpanStart(it) <= start && t.getSpanEnd(it) >= end
        }
        if (existing.isNotEmpty()) existing.forEach { t.removeSpan(it) }
        else t.setSpan(make(), start, end, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
        Theme.haptic(body)
    }

    /** Makes the current line(s) a heading (bigger and bold), or back to normal. */
    private fun heading() {
        val t = body.text
        val (start, end) = lineRange()
        if (start == end) return
        val sizes = t.getSpans(start, end, RelativeSizeSpan::class.java)
        if (sizes.isNotEmpty()) {
            sizes.forEach { t.removeSpan(it) }
            t.getSpans(start, end, StyleSpan::class.java).filter { it.style == Typeface.BOLD }.forEach { t.removeSpan(it) }
        } else {
            t.setSpan(RelativeSizeSpan(1.35f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            t.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        Theme.haptic(body)
    }

    /** Adds or removes "• " / "☐ " at the start of each selected line. */
    private fun toggleLinePrefix(prefix: String) {
        val t = body.text
        val (start, end) = lineRange()
        val lines = t.substring(start, end).split('\n')
        val allHave = lines.all { it.startsWith(prefix) || (prefix == BOX && it.startsWith(TICKED)) }
        var pos = start
        listGuard = true
        for (line in lines) {
            val has = line.startsWith(prefix) || (prefix == BOX && line.startsWith(TICKED))
            val other = listOf(BULLET, BOX, TICKED).firstOrNull { line.startsWith(it) }
            when {
                allHave && has -> { t.delete(pos, pos + 2); pos += line.length - 2 }
                !allHave && !has -> {
                    if (other != null) t.delete(pos, pos + 2)
                    t.insert(pos, prefix)
                    pos += line.length - (if (other != null) 2 else 0) + prefix.length
                }
                else -> pos += line.length
            }
            pos += 1 // the newline
        }
        listGuard = false
        Theme.haptic(body)
    }

    private fun lineRange(): Pair<Int, Int> {
        val t = body.text
        val s = t.lastIndexOf('\n', (body.selectionStart - 1).coerceAtLeast(0)).let { if (it < 0 || body.selectionStart == 0) 0 else it + 1 }
        val e = t.indexOf('\n', body.selectionEnd).let { if (it < 0) t.length else it }
        return s to e
    }

    /** Tapping a ☐ ticks it (☑), and tapping ☑ unticks it. */
    private fun tickBox(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_UP) return false
        val layout = body.layout ?: return false
        val y = e.y.toInt() - body.totalPaddingTop + body.scrollY
        val line = layout.getLineForVertical(y)
        val offset = layout.getOffsetForHorizontal(line, e.x - body.totalPaddingLeft + body.scrollX)
        val t = body.text
        val lineStart = layout.getLineStart(line)
        // Only when the tap lands on the box itself (the first characters of the line).
        if (offset - lineStart > 1 || lineStart >= t.length) return false
        val c = t[lineStart]
        if (c != '☐' && c != '☑') return false
        listGuard = true
        t.replace(lineStart, lineStart + 1, if (c == '☐') "☑" else "☐")
        listGuard = false
        Theme.haptic(body)
        return true
    }

    /** Pressing Enter on a list line starts the next item; Enter on an empty item ends the list. */
    private inner class ListContinuation : TextWatcher {
        private var newlineAt = -1

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            newlineAt = if (!listGuard && count == 1 && before == 0 && s[start] == '\n') start else -1
        }

        override fun afterTextChanged(t: Editable) {
            val at = newlineAt
            if (at < 0) return
            newlineAt = -1
            val prevStart = t.lastIndexOf('\n', at - 1) + 1
            val prev = t.substring(prevStart, at)
            val prefix = listOf(BULLET, BOX, TICKED).firstOrNull { prev.startsWith(it) } ?: return
            listGuard = true
            if (prev.length == prefix.length) {
                t.delete(prevStart, at + 1) // empty item: end the list
            } else {
                t.insert(at + 1, if (prefix == TICKED) BOX else prefix)
            }
            listGuard = false
        }
    }

    // ---- Drawings ----

    private fun showSketches() {
        sketchRow.removeAllViews()
        note.sketches.forEach { name ->
            val bmp = BitmapFactory.decodeFile(File(Notes.sketchDir(this), name).absolutePath) ?: return@forEach
            sketchRow.addView(ImageView(this).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = Theme.rounded(context, Theme.LINE, 16)
                clipToOutline = true
                Theme.pressable(this)
                setOnClickListener {
                    editingSketch = name
                    @Suppress("DEPRECATION")
                    startActivityForResult(SketchActivity.intent(context, name), DRAW)
                }
                setOnLongClickListener {
                    Theme.haptic(it, strong = true)
                    KosherPage.sheet(this@NoteEditActivity, getString(R.string.draw), listOf(getString(R.string.delete))) {
                        File(Notes.sketchDir(context), name).delete()
                        note = note.copy(sketches = note.sketches - name)
                        showSketches()
                    }
                    true
                }
            }, LinearLayout.LayoutParams(Theme.dp(this, 150), Theme.dp(this, 150)).apply {
                marginEnd = Theme.dp(this@NoteEditActivity, 10)
                topMargin = Theme.dp(this@NoteEditActivity, 12)
            })
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val name = data?.getStringExtra(SketchActivity.EXTRA_FILE) ?: return
        if (requestCode == DRAW && resultCode == RESULT_OK && name !in note.sketches) {
            note = note.copy(sketches = note.sketches + name)
        }
        showSketches()
    }

    override fun onPause() {
        super.onPause()
        if (deleted) return
        val t = title.text.toString()
        if (t.isBlank() && body.text.isBlank() && note.sketches.isEmpty()) return
        note = Notes.save(this, note.copy(title = t, html = Html.toHtml(body.text, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)))
    }
}
