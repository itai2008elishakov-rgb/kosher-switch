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
        val size = Theme.dp(this, 64)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(EmblemView(context), LinearLayout.LayoutParams(size, size))
            addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Theme.dp(context, 18) })
            addView(dots, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Theme.dp(context, 36))
                .apply { topMargin = Theme.dp(context, 12) })
            addView(keypad())
            addView(unlock, LinearLayout.LayoutParams(keyWidth() * 3 + Theme.dp(context, 48), Theme.dp(context, 52))
                .apply { topMargin = Theme.dp(context, 16) })
        }
        val (_, blurred) = Backdrop.homeLayers(this)
        setContentView(FrameLayout(this).apply {
            addView(ImageView(context).apply { setImageBitmap(blurred); scaleType = ImageView.ScaleType.CENTER_CROP })
            addView(column, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            ))
        })
        column.alpha = 0f
        column.translationY = Theme.dp(this, 30).toFloat()
        column.animate().alpha(1f).translationY(0f).setDuration(320).start()
    }

    /** Key size that fits four rows plus the header on small or zoomed screens. */
    private fun keyWidth(): Int {
        val dm = resources.displayMetrics
        return minOf(Theme.dp(this, 78), (dm.heightPixels - Theme.dp(this, 360)) / 4 - Theme.dp(this, 16))
    }

    private fun keypad() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, Theme.dp(context, 8), 0, 0)
        listOf(
            listOf("1", "2", "3"), listOf("4", "5", "6"),
            listOf("7", "8", "9"), listOf("✕", "0", "⌫"),
        ).forEach { row ->
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                row.forEach { addView(key(it)) }
            })
        }
    }

    private fun key(label: String): TextView {
        val size = keyWidth()
        val digit = label.first().isDigit()
        return Theme.text(this, label, if (digit) 32f else 22f, Color.WHITE, Theme.LIGHT).apply {
            gravity = Gravity.CENTER
            if (digit) background = Theme.glass(context, 60, 1.2f)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                val m = Theme.dp(context, 8)
                setMargins(m, m, m, m)
            }
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
    }

    private fun showDots() {
        dots.removeAllViews()
        repeat(code.length) {
            dots.addView(View(this).apply { background = Theme.rounded(context, Color.WHITE, 7) },
                LinearLayout.LayoutParams(Theme.dp(this, 14), Theme.dp(this, 14)).apply {
                    val m = Theme.dp(this@UnlockActivity, 7)
                    setMargins(m, 0, m, 0)
                })
        }
        unlock.alpha = if (code.length >= PinStore.MIN_LENGTH) 1f else 0.4f
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
