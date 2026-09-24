package dev.kosherswitch

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/** Drawing on a page: pens in several colors and sizes, eraser, undo. Saves a PNG into the note. */
class SketchActivity : BaseActivity() {
    companion object {
        const val EXTRA_FILE = "file"

        /** Opens a drawing; [file] is an existing sketch to keep editing, or null for a new one. */
        fun intent(ctx: Context, file: String?) = Intent(ctx, SketchActivity::class.java).apply { if (file != null) putExtra(EXTRA_FILE, file) }

        private val COLORS = intArrayOf(
            0xFF1C1F26.toInt(), 0xFF1E4E9C.toInt(), 0xFFD64545.toInt(), 0xFF1F9D55.toInt(),
            0xFFE0B64A.toInt(), 0xFF8E44AD.toInt(), 0xFFFF8A00.toInt(),
        )
        private val SIZES = floatArrayOf(4f, 9f, 18f)
    }

    private lateinit var pad: SketchView
    private var color = COLORS[0]
    private var size = SIZES[1]
    private var eraser = false
    private lateinit var tools: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.lightBars(this)
        val existing = intent.getStringExtra(EXTRA_FILE)?.let { File(Notes.sketchDir(this), it) }
        pad = SketchView(this, existing?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) })
        tools = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            val p = Theme.dp(context, 10)
            setPadding(p, p, p, p)
        }
        paintTools()
        val done = KosherPage.barButton(this, "✓") { save(existing) }
        val undo = KosherPage.barButton(this, "↶") { pad.undo() }
        val clear = KosherPage.barButton(this, "🗑") { pad.clear() }
        setContentView(KosherPage.page(this, KosherPage.titleBar(this, getString(R.string.draw), undo, clear, done),
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Theme.PAGE)
                addView(pad, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    val m = Theme.dp(context, 12)
                    setMargins(m, m, m, 0)
                })
                addView(HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(tools)
                })
            }))
    }

    /** Color dots, pen sizes and the eraser, with the current choice highlighted. */
    private fun paintTools() {
        tools.removeAllViews()
        COLORS.forEach { c ->
            tools.addView(View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(c)
                    if (!eraser && c == this@SketchActivity.color) setStroke(Theme.dp(context, 3), Theme.GOLD)
                }
                Theme.pressable(this)
                setOnClickListener { color = c; eraser = false; paintTools() }
            }, LinearLayout.LayoutParams(Theme.dp(this, 34), Theme.dp(this, 34)).apply { marginEnd = Theme.dp(this@SketchActivity, 10) })
        }
        SIZES.forEachIndexed { i, s ->
            tools.addView(tool(listOf("•", "●", "⬤")[i], !eraser && s == size) { size = s; eraser = false; paintTools() })
        }
        tools.addView(tool("⌫", eraser) { eraser = true; paintTools() })
    }

    private fun tool(label: String, selected: Boolean, onClick: () -> Unit): TextView =
        Theme.text(this, label, 18f, if (selected) Color.WHITE else Theme.INK, Theme.MEDIUM).apply {
            gravity = Gravity.CENTER
            background = Theme.rounded(context, if (selected) Theme.BLUE else Theme.CHIP, 20)
            layoutParams = LinearLayout.LayoutParams(Theme.dp(context, 44), Theme.dp(context, 40)).apply { marginEnd = Theme.dp(context, 8) }
            Theme.pressable(this)
            setOnClickListener { onClick() }
        }

    private fun save(existing: File?) {
        if (pad.isEmpty()) {
            finish()
            return
        }
        val file = existing ?: File(Notes.sketchDir(this), "sketch_${System.currentTimeMillis()}.png")
        file.outputStream().use { pad.export().compress(Bitmap.CompressFormat.PNG, 100, it) }
        setResult(RESULT_OK, Intent().putExtra(EXTRA_FILE, file.name))
        finish()
    }

    /** The drawing surface. Strokes are kept so they can be undone. */
    private inner class SketchView(ctx: Context, private val base: Bitmap?) : View(ctx) {
        private inner class Stroke(val path: Path, val paint: Paint)

        private val strokes = mutableListOf<Stroke>()
        private var current: Path? = null
        private var lastX = 0f
        private var lastY = 0f
        private val paper = Color.WHITE

        init {
            background = Theme.rounded(ctx, paper, 18)
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }

        fun isEmpty() = strokes.isEmpty() && base == null

        fun undo() {
            if (strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex); invalidate(); Theme.haptic(this) }
        }

        fun clear() {
            strokes.clear(); invalidate(); Theme.haptic(this, strong = true)
        }

        fun export(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { draw(Canvas(it).apply { drawColor(paper) }) }

        override fun onDraw(c: Canvas) {
            base?.let { c.drawBitmap(it, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null) }
            strokes.forEach { c.drawPath(it.path, it.paint) }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val x = e.x
            val y = e.y
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent.requestDisallowInterceptTouchEvent(true)
                    val path = Path().apply { moveTo(x, y) }
                    current = path
                    strokes += Stroke(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        this.color = if (eraser) paper else this@SketchActivity.color
                        strokeWidth = Theme.dp(context, 1) * (if (eraser) size * 2.5f else size) / 2f
                    })
                    lastX = x; lastY = y
                }
                MotionEvent.ACTION_MOVE -> {
                    // Smooth curves through the midpoints of the finger's path.
                    current?.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
                    lastX = x; lastY = y
                }
                MotionEvent.ACTION_UP -> {
                    current?.lineTo(x, y)
                    current = null
                }
            }
            invalidate()
            return true
        }
    }
}
