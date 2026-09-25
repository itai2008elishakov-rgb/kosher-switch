package dev.kosherswitch

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** The kosher-phone look: deep blue, gold, white cards. */
object Theme {
    const val NAVY = 0xFF0B2A5B.toInt()
    const val BLUE = 0xFF1E4E9C.toInt()
    const val GOLD = 0xFFE0B64A.toInt()
    const val GOLD_DARK = 0xFF9C7418.toInt()

    // Surface and text colors switch with light/dark mode (see load()).
    var PAPER = 0xFFFBF8F1.toInt(); private set
    var PAGE = 0xFFF2F4F8.toInt(); private set
    var CARD = Color.WHITE; private set
    var CHIP = 0xFFE7EBF2.toInt(); private set
    var INK = 0xFF15223B.toInt(); private set
    var SUB = 0xFF6B7485.toInt(); private set
    var LINE = 0xFFE3E7EF.toInt(); private set
    /** Strong text on cards: navy in light mode, soft blue in dark mode. */
    var HEADLINE = NAVY; private set
    /** Links and selected text. */
    var LINK = BLUE; private set
    var dark = false; private set
    /** Glass strength 0..1 (0 = solid panels). */
    var glass = 0.6f; private set

    /** Reads light/dark mode and glass strength; called at the start of every screen. */
    fun load(ctx: Context) {
        dark = Looks.isDark(ctx)
        glass = Looks.glass(ctx) / 100f
        if (dark) {
            PAPER = 0xFF12161E.toInt(); PAGE = 0xFF0B0F17.toInt(); CARD = 0xFF182130.toInt(); CHIP = 0xFF222C3D.toInt()
            INK = 0xFFE8ECF4.toInt(); SUB = 0xFF95A0B3.toInt(); LINE = 0xFF283345.toInt()
            HEADLINE = 0xFFA9C3F5.toInt(); LINK = 0xFF7FA6F0.toInt()
        } else {
            PAPER = 0xFFFBF8F1.toInt(); PAGE = 0xFFF2F4F8.toInt(); CARD = Color.WHITE; CHIP = 0xFFE7EBF2.toInt()
            INK = 0xFF15223B.toInt(); SUB = 0xFF6B7485.toInt(); LINE = 0xFFE3E7EF.toInt()
            HEADLINE = NAVY; LINK = BLUE
        }
    }
    const val OK = 0xFF1F9D55.toInt()
    const val ERROR = 0xFFD64545.toInt()

    val REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val BLUE_TINT: ColorStateList get() = ColorStateList.valueOf(LINK)
    private val CHECKED = intArrayOf(android.R.attr.state_checked)
    val THUMB_TINT get() = ColorStateList(arrayOf(CHECKED, intArrayOf()), intArrayOf(LINK, if (dark) 0xFF8C96A8.toInt() else 0xFFFAFAFA.toInt()))
    val TRACK_TINT get() = ColorStateList(arrayOf(CHECKED, intArrayOf()), intArrayOf(0x661E4E9C, if (dark) 0xFF3A4558.toInt() else 0xFFC5CAD3.toInt()))

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun rounded(ctx: Context, color: Int, radiusDp: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(ctx, radiusDp).toFloat()
        if (stroke != null) setStroke(dp(ctx, 1), stroke)
    }

    /**
     * Frosted-glass panel: a translucent white sheen with a bright edge. It looks like glass
     * over the blurred backdrop drawn behind it.
     */
    fun glass(ctx: Context, radiusDp: Int, strength: Float = 1f): GradientDrawable {
        val g = glass * strength
        val colors = when {
            g <= 0.01f -> if (dark) intArrayOf(0xF00B0F17.toInt(), 0xF00B0F17.toInt()) else intArrayOf(0xF01E3A6B.toInt(), 0xF01E3A6B.toInt())
            dark -> intArrayOf(blackAlpha(0.22f + 0.28f * g), blackAlpha(0.32f + 0.30f * g))
            else -> intArrayOf(whiteAlpha(0.12f + 0.30f * g), whiteAlpha(0.05f + 0.12f * g))
        }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = dp(ctx, radiusDp).toFloat()
            setStroke(dp(ctx, 1), whiteAlpha(if (dark) 0.08f + 0.18f * g else 0.14f + 0.38f * g))
        }
    }

    /** A light tap on the phone's vibration motor, if haptics are on. */
    fun haptic(view: View, strong: Boolean = false) {
        if (!Looks.haptics(view.context)) return
        view.performHapticFeedback(
            if (strong) android.view.HapticFeedbackConstants.LONG_PRESS else android.view.HapticFeedbackConstants.CLOCK_TICK,
            android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }

    fun blackAlpha(a: Float) = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), 0, 0, 0)

    fun whiteAlpha(a: Float) = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), 255, 255, 255)

    fun skyGradient() = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(NAVY, BLUE)
    )

    /** Blue system bars for screens with the blue gradient. */
    fun blueBars(activity: Activity) {
        activity.window.statusBarColor = NAVY
        activity.window.navigationBarColor = BLUE
    }

    /** System bars for the page-colored screens (light or dark mode). */
    @Suppress("DEPRECATION")
    fun lightBars(activity: Activity) {
        activity.window.statusBarColor = NAVY
        activity.window.navigationBarColor = PAGE
        activity.window.decorView.systemUiVisibility = if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    fun text(
        ctx: Context, s: CharSequence, size: Float = 15f, color: Int = INK, face: Typeface = REGULAR,
    ) = TextView(ctx).apply {
        text = s
        textSize = size
        setTextColor(color)
        typeface = face
        setLineSpacing(0f, 1.15f)
    }

    fun card(ctx: Context, vararg children: View) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(ctx, CARD, 22)
        elevation = dp(ctx, 1).toFloat()
        val p = dp(ctx, 18)
        setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(ctx, 14) }
        children.forEach(::addView)
    }

    fun button(
        ctx: Context, s: String, fill: Int = BLUE, textColor: Int = Color.WHITE,
        outline: Boolean = false, onClick: () -> Unit,
    ) = Button(ctx).apply {
        text = s
        isAllCaps = false
        typeface = MEDIUM
        textSize = 16f
        stateListAnimator = null
        // Outline buttons use the brighter link color, so they stay readable in dark mode.
        val line = if (outline && fill == BLUE) LINK else fill
        setTextColor(if (outline) line else textColor)
        background = if (outline) rounded(ctx, Color.TRANSPARENT, 28, line) else rounded(ctx, fill, 28)
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
        pressable(this)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 52)
        ).apply { topMargin = dp(ctx, 12) }
    }

    /** Small shrink on touch and a springy release; clicks still go through. */
    fun pressable(view: View) {
        // Keypad phones: the navigation pad can select this view, shown with a gold ring.
        view.isFocusable = true
        // Only our gold ring (no grey system box), and no growing, so the ring is never cut off.
        if (android.os.Build.VERSION.SDK_INT >= 26) view.defaultFocusHighlightEnabled = false
        view.setOnFocusChangeListener { v, focused ->
            v.foreground = if (focused) GradientDrawable().apply {
                cornerRadius = dp(v.context, 18).toFloat()
                setColor(whiteAlpha(0.10f))
                setStroke(dp(v.context, 3), GOLD)
            } else null
            if (focused) haptic(v)
        }
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start()
                    haptic(v)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(OvershootInterpolator(3f)).start()
            }
            false
        }
    }
}
