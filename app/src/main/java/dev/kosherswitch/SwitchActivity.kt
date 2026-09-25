package dev.kosherswitch

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Full-screen "switching" screen: shows up instantly while the switch runs in the background. */
class SwitchActivity : BaseActivity() {
    override val glides = false
    companion object {
        private const val EXTRA_TO_CLOSED = "to_closed"

        fun intent(ctx: Context, toClosed: Boolean) = Intent(ctx, SwitchActivity::class.java)
            .putExtra(EXTRA_TO_CLOSED, toClosed)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.setDecorFitsSystemWindows(false)
        val toClosed = intent.getBooleanExtra(EXTRA_TO_CLOSED, true)

        val emblem = EmblemView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val size = Theme.dp(context, 120)
            addView(emblem, LinearLayout.LayoutParams(size, size))
            addView(Theme.text(
                context, getString(if (toClosed) R.string.kosher_device else R.string.opening_phone),
                22f, 0xFFFFFFFF.toInt(), Theme.MEDIUM,
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, Theme.dp(context, 28), 0, Theme.dp(context, 6))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(Theme.text(context, getString(R.string.one_moment), 15f, 0xB3FFFFFF.toInt()).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val (_, blurred) = Backdrop.homeLayers(this)
        setContentView(FrameLayout(this).apply {
            clipChildren = false // let the ripple spread past the emblem
            addView(android.widget.ImageView(context).apply {
                setImageBitmap(blurred)
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            })
            addView(column, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            ))
        })
        Theme.haptic(column, strong = true)

        // A soft gold ripple spreading out behind the emblem, again and again.
        val ripple = android.view.View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setStroke(Theme.dp(context, 2), Theme.GOLD)
            }
            alpha = 0f
        }
        (emblem.parent as LinearLayout).let { col ->
            col.removeView(emblem)
            val size = Theme.dp(this, 120)
            col.addView(FrameLayout(this).apply {
                clipChildren = false
                addView(ripple, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
                addView(emblem, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
            }, 0, LinearLayout.LayoutParams(size, size))
            col.clipChildren = false
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            startDelay = 500
            addUpdateListener {
                val t = it.animatedValue as Float
                ripple.scaleX = 1f + t * 0.9f
                ripple.scaleY = 1f + t * 0.9f
                ripple.alpha = (1f - t) * 0.7f
            }
            start()
        }

        // The emblem turns and settles in; the text follows.
        val spring = android.view.animation.OvershootInterpolator(1.6f)
        emblem.scaleX = 0.4f; emblem.scaleY = 0.4f; emblem.rotation = -30f; emblem.alpha = 0f
        emblem.animate().scaleX(1f).scaleY(1f).rotation(0f).alpha(1f).setDuration(650).setInterpolator(spring).start()
        for (i in 1 until column.childCount) {
            val v = column.getChildAt(i)
            v.alpha = 0f
            v.translationY = Theme.dp(this, 16).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(250L + i * 90L).setDuration(400).start()
        }

        val started = System.currentTimeMillis()
        ModeManager.inBackground({
            if (toClosed) ModeManager.enterClosed(this) else ModeManager.exitClosed(this)
        }) {
            // Keep the screen up briefly so the switch feels deliberate, not glitchy,
            // then zoom through the emblem into the new home screen.
            val wait = (1200 - (System.currentTimeMillis() - started)).coerceAtLeast(0)
            window.decorView.postDelayed({
                Theme.haptic(column, strong = true)
                emblem.animate().scaleX(1.5f).scaleY(1.5f).alpha(0f).setDuration(280).start()
                column.animate().alpha(0f).setStartDelay(60).setDuration(260).withEndAction { goHome() }.start()
            }, wait)
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    @Deprecated("Switching can't be cancelled")
    override fun onBackPressed() = Unit
}
