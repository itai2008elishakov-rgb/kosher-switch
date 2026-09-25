package dev.kosherswitch

import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Glass keypad over the home background; asks for the code before switching back to open mode. */
class UnlockActivity : BaseActivity() {
    override val glides = false
    private companion object {
        const val MAX_TRIES = 5
        const val LOCKOUT_MS = 60_000L
        const val MAX_DIGITS = 12
        const val KEY_FAILS = "unlock_fails"
        const val KEY_LOCKED_UNTIL = "unlock_locked_until"
    }

    private val code = StringBuilder()
    private lateinit var dots: LinearLayout
    private lateinit var status: TextView
    private lateinit var unlock: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ModeManager.isClosed(this)) {
            finish()
            return
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setDecorFitsSystemWindows(false)

        status = Theme.text(this, getString(R.string.enter_code), 17f, Color.WHITE, Theme.MEDIUM).apply { gravity = Gravity.CENTER }
        dots = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            minimumHeight = Theme.dp(context, 36)
        }
        unlock = Theme.text(this, getString(R.string.unlock), 17f, Color.WHITE, Theme.MEDIUM).apply {
            gravity = Gravity.CENTER
            background = Theme.glass(context, 26, 1.3f)
            alpha = 0.4f
            Theme.pressable(this)
            setOnClickListener { attempt() }
        }
        val small = resources.displayMetrics.let { it.heightPixels / it.density < 700 }
        val emblem = Theme.dp(this, if (small) 46 else 64)
        // The keypad takes all the room between the header and the unlock button.
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val side = Theme.dp(context, 20)
            setPadding(side, 0, side, 0)
            addView(EmblemView(context), LinearLayout.LayoutParams(emblem, emblem))
            addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Theme.dp(context, if (small) 8 else 16) })
            addView(dots, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Theme.dp(context, if (small) 28 else 36))
                .apply { topMargin = Theme.dp(context, if (small) 4 else 10) })
            addView(keypad(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                width = minOf(resources.displayMetrics.widthPixels, Theme.dp(context, 380))
            })
            addView(unlock, LinearLayout.LayoutParams(minOf(resources.displayMetrics.widthPixels - 2 * side, Theme.dp(context, 340)),
                Theme.dp(context, if (small) 46 else 54)).apply { topMargin = Theme.dp(context, 8) })
        }
        column.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top + Theme.dp(this, if (small) 10 else 28), v.paddingRight, bars.bottom + Theme.dp(this, if (small) 10 else 24))
            insets
        }
        val (_, blurred) = Backdrop.homeLayers(this)
        setContentView(FrameLayout(this).apply {
            addView(ImageView(context).apply { setImageBitmap(blurred); scaleType = ImageView.ScaleType.CENTER_CROP })
            addView(View(context).apply { setBackgroundColor(0x33000000) }) // a little darker, so the keys stand out
            addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        })
        // Header drops in, then the keys ripple in row by row.
        column.alpha = 0f
        column.animate().alpha(1f).setDuration(260).start()
        for (i in 0 until column.childCount) {
            val v = column.getChildAt(i)
            v.translationY = Theme.dp(this, 24).toFloat()
            v.animate().translationY(0f).setStartDelay(i * 45L).setDuration(420).setInterpolator(Motion.EASE).start()
        }
    }

    /** Set while the user moves around the on-screen keypad with the arrows. */
    private var navigating = false

    /**
     * Keypad phones: number keys type the code, back (or delete) erases a digit, OK or Enter unlocks.
     * Handled before the buttons see the key: typing selects the "1" button by itself, and OK must
     * not type an extra 1. OK presses a button only after the arrows were used to pick it.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val k = event.keyCode
        val handled = when {
            k in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> true
            k == android.view.KeyEvent.KEYCODE_DEL -> true
            k == android.view.KeyEvent.KEYCODE_BACK -> code.isNotEmpty()
            k == android.view.KeyEvent.KEYCODE_ENTER || k == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> true
            k == android.view.KeyEvent.KEYCODE_DPAD_CENTER -> !navigating || currentFocus == null || currentFocus == unlock
            else -> false
        }
        if (k in listOf(android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)) navigating = true
        if (!handled) return super.dispatchKeyEvent(event)
        if (event.action != android.view.KeyEvent.ACTION_DOWN) return true
        when (k) {
            in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> {
                navigating = false
                if (code.length < MAX_DIGITS) code.append(k - android.view.KeyEvent.KEYCODE_0)
                Theme.haptic(dots)
            }
            android.view.KeyEvent.KEYCODE_DEL, android.view.KeyEvent.KEYCODE_BACK ->
                if (code.isNotEmpty()) code.deleteCharAt(code.length - 1)
            else -> { attempt(); return true }
        }
        showDots()
        return true
    }

    private fun keypad() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        listOf(
            listOf("1", "2", "3"), listOf("4", "5", "6"),
            listOf("7", "8", "9"), listOf("✕", "0", "⌫"),
        ).forEach { row ->
            addView(LinearLayout(context).apply {
                row.forEach { label ->
                    addView(FrameLayout(context).apply {
                        addView(KeyView(label), FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private val LETTERS = mapOf("2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO",
        "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "0" to "+")

    /** A round key as big as its cell allows (up to a limit), with the letters under the digit. */
    private inner class KeyView(val label: String) : TextView(this@UnlockActivity) {
        private val digit = label.first().isDigit()

        init {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Theme.LIGHT
            includeFontPadding = false
            if (digit) background = Theme.glass(context, 200, 1.2f)
            val letters = LETTERS[label]
            text = if (digit && letters != null) android.text.SpannableString("$label\n$letters").apply {
                setSpan(android.text.style.RelativeSizeSpan(0.3f), label.length + 1, length, 0)
                setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), label.length + 1, length, 0)
            } else label
            setLineSpacing(0f, 0.9f)
            contentDescription = label
            Theme.pressable(this)
            setOnClickListener {
                when (label) {
                    "✕" -> finish()
                    "⌫" -> if (code.isNotEmpty()) code.deleteCharAt(code.length - 1)
                    else -> if (code.length < MAX_DIGITS) code.append(label)
                }
                showDots()
            }
        }

        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            val gap = Theme.dp(context, 6)
            val side = minOf(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec), Theme.dp(context, 92)) - 2 * gap
            val exact = MeasureSpec.makeMeasureSpec(side.coerceAtLeast(0), MeasureSpec.EXACTLY)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, side * if (digit) 0.4f else 0.3f)
            super.onMeasure(exact, exact)
        }
    }

    private var shownDots = 0

    private fun showDots() {
        dots.removeAllViews()
        repeat(code.length) { i ->
            dots.addView(View(this).apply {
                background = Theme.rounded(context, Color.WHITE, 7)
                if (i == code.length - 1 && i >= shownDots) { // the newest dot pops in
                    scaleX = 0.2f; scaleY = 0.2f
                    animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
                }
            },
                LinearLayout.LayoutParams(Theme.dp(this, 14), Theme.dp(this, 14)).apply {
                    val m = Theme.dp(this@UnlockActivity, 7)
                    setMargins(m, 0, m, 0)
                })
        }
        shownDots = code.length
        unlock.animate().alpha(if (code.length >= PinStore.MIN_LENGTH) 1f else 0.4f).setDuration(200).start()
    }

    private fun attempt() {
        val p = prefs(this)
        // elapsedRealtime can't be moved back by changing the clock; reset on reboot is acceptable.
        val now = SystemClock.elapsedRealtime()
        val lockedUntil = p.getLong(KEY_LOCKED_UNTIL, 0)
        if (now < lockedUntil) {
            showError(getString(R.string.too_many_tries, ((lockedUntil - now) / 1000 + 1).toInt()))
            return
        }
        if (PinStore.verify(this, code.toString())) {
            p.edit().remove(KEY_FAILS).remove(KEY_LOCKED_UNTIL).apply()
            Theme.haptic(dots, strong = true)
            startActivity(SwitchActivity.intent(this, toClosed = false))
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
            return
        }
        val fails = p.getInt(KEY_FAILS, 0) + 1
        if (fails >= MAX_TRIES) {
            p.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCKED_UNTIL, now + LOCKOUT_MS).apply()
            showError(getString(R.string.too_many_tries, (LOCKOUT_MS / 1000).toInt()))
        } else {
            p.edit().putInt(KEY_FAILS, fails).apply()
            showError(getString(R.string.wrong_code))
        }
    }

    private fun showError(msg: String) {
        status.text = msg
        status.setTextColor(Theme.GOLD)
        code.clear()
        showDots()
        Theme.haptic(status, strong = true)
        val d = Theme.dp(this, 14).toFloat()
        ObjectAnimator.ofFloat(dots, "translationX", 0f, -d, d, -d, d, 0f).setDuration(380).start()
        ObjectAnimator.ofFloat(status, "translationX", 0f, -d, d, -d, d, 0f).setDuration(380).start()
    }
}
