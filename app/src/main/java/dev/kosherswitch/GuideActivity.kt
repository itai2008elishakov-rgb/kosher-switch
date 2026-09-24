package dev.kosherswitch

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/** Step-by-step guide shown the first time Kosher Switch opens (and from "How it works"). */
class GuideActivity : BaseActivity() {
    companion object {
        private const val KEY_SEEN = "guide_seen"

        fun seen(ctx: Context) = prefs(ctx).getBoolean(KEY_SEEN, false)
        fun intent(ctx: Context) = Intent(ctx, GuideActivity::class.java)
    }

    private class Page(val icon: String?, val title: Int, val text: Int)

    private val pages = listOf(
        Page(null, R.string.g1_title, R.string.g1_text),
        Page("🔀", R.string.g2_title, R.string.g2_text),
        Page("💻", R.string.g3_title, R.string.g3_text),
        Page("🔒", R.string.g4_title, R.string.g4_text),
        Page("📱", R.string.g5_title, R.string.g5_text),
        Page("⬇️", R.string.g6_title, R.string.g6_text),
        Page("📦", R.string.g7_title, R.string.g7_text),
        Page("✅", R.string.g8_title, R.string.g8_text),
    )
    private var index = 0
    private lateinit var card: FrameLayout
    private lateinit var dots: LinearLayout
    private lateinit var back: TextView
    private lateinit var next: TextView
    private val rtl get() = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setDecorFitsSystemWindows(false)

        card = FrameLayout(this)
        dots = LinearLayout(this).apply { gravity = Gravity.CENTER }
        back = navButton(getString(R.string.g_back), filled = false) { go(index - 1) }
        next = navButton(getString(R.string.g_next), filled = true) { if (index == pages.lastIndex) finishGuide() else go(index + 1) }
        val skip = Theme.text(this, getString(R.string.g_skip), 15f, 0xCCFFFFFF.toInt(), Theme.MEDIUM).apply {
            setPadding(Theme.dp(context, 16), Theme.dp(context, 10), Theme.dp(context, 16), Theme.dp(context, 10))
            Theme.pressable(this)
            setOnClickListener { finishGuide() }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(skip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { gravity = Gravity.END })
            addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(dots, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Theme.dp(context, 40)))
            addView(LinearLayout(context).apply {
                addView(back, LinearLayout.LayoutParams(0, Theme.dp(context, 54), 1f).apply { marginEnd = Theme.dp(context, 6) })
                addView(next, LinearLayout.LayoutParams(0, Theme.dp(context, 54), 1f).apply { marginStart = Theme.dp(context, 6) })
            })
        }
        val root = FrameLayout(this).apply {
            addView(ImageView(context).apply {
                setImageBitmap(Backdrop.homeLayers(context).second)
                scaleType = ImageView.ScaleType.CENTER_CROP
            })
            addView(column)
        }
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            val p = Theme.dp(this, 20)
            column.setPadding(p, bars.top + Theme.dp(this, 8), p, bars.bottom + p)
            insets
        }
        // Swipe between pages.
        val swipe = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vx) < 600 || abs(vx) < abs(vy)) return false
                val forward = (vx < 0) != rtl
                go(if (forward) index + 1 else index - 1)
                return true
            }
        })
        card.setOnTouchListener { _, e -> swipe.onTouchEvent(e); true }
        setContentView(root)
        show(0, 0)
    }

    private fun go(to: Int) {
        if (to !in pages.indices || to == index) return
        val dir = if (to > index) 1 else -1
        index = to
        show(to, dir)
    }

    /** Slides the new page in from the side it's coming from. */
    private fun show(i: Int, dir: Int) {
        val page = pages[i]
        val view = pageView(page, i)
        val shift = Theme.dp(this, 60).toFloat() * dir * (if (rtl) -1 else 1)
        val old = if (card.childCount > 0) card.getChildAt(0) else null
        old?.animate()?.alpha(0f)?.translationX(-shift)?.setDuration(220)?.withEndAction { card.removeView(old) }?.start()
        card.addView(view)
        view.alpha = 0f
        view.translationX = shift
        view.animate().alpha(1f).translationX(0f).setDuration(380).setInterpolator(PathInterpolator(0.2f, 0.9f, 0.1f, 1f)).start()

        back.visibility = if (i == 0) View.INVISIBLE else View.VISIBLE
        next.text = getString(if (i == pages.lastIndex) R.string.g_start else R.string.g_next)
        dots.removeAllViews()
        pages.indices.forEach { d ->
            dots.addView(View(this).apply { background = Theme.rounded(context, if (d == i) Theme.GOLD else Theme.whiteAlpha(0.35f), 4) },
                LinearLayout.LayoutParams(Theme.dp(this, if (d == i) 22 else 8), Theme.dp(this, 8)).apply {
                    val m = Theme.dp(this@GuideActivity, 3)
                    setMargins(m, 0, m, 0)
                })
        }
        if (dir != 0) Theme.haptic(card)
    }

    private fun pageView(page: Page, i: Int): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.glass(context, 30, 1.2f)
            val p = Theme.dp(context, 24)
            setPadding(p, p, p, p)
            if (page.icon == null) {
                addView(EmblemView(context), LinearLayout.LayoutParams(Theme.dp(context, 96), Theme.dp(context, 96)))
            } else {
                addView(Theme.text(context, page.icon, 52f).apply { gravity = Gravity.CENTER })
            }
            addView(Theme.text(context, getString(page.title), 26f, Color.WHITE, Theme.MEDIUM).apply {
                gravity = Gravity.CENTER
                setPadding(0, Theme.dp(context, 14), 0, Theme.dp(context, 12))
            })
            if (page.title == R.string.g3_title && ModeManager.isDeviceOwner(context)) {
                addView(Theme.text(context, getString(R.string.g3_done), 16f, Theme.GOLD, Theme.MEDIUM).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, Theme.dp(context, 12))
                })
            }
            addView(Theme.text(context, getString(page.text), 16f, 0xF2FFFFFF.toInt()).apply {
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                setLineSpacing(0f, 1.3f)
                setTextIsSelectable(page.title == R.string.g3_title) // so the command can be copied
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        return ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(FrameLayout(context).apply {
                addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            })
        }
    }

    private fun navButton(label: String, filled: Boolean, onClick: () -> Unit) =
        Theme.text(this, label, 17f, if (filled) Theme.NAVY else Color.WHITE, Theme.MEDIUM).apply {
            gravity = Gravity.CENTER
            background = if (filled) Theme.rounded(context, Theme.GOLD, 27) else Theme.glass(context, 27)
            Theme.pressable(this)
            setOnClickListener { onClick() }
        }

    private fun finishGuide() {
        prefs(this).edit().putBoolean(KEY_SEEN, true).apply()
        finish()
    }

    @Deprecated("Back goes to the previous page")
    override fun onBackPressed() {
        if (index > 0) go(index - 1) else finishGuide()
    }
}
