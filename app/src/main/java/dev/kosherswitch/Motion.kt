package dev.kosherswitch

import android.animation.ValueAnimator
import android.app.Activity
import android.provider.Settings
import android.view.View
import android.view.animation.PathInterpolator

/**
 * Many keypad phones (like the Qin F21 Pro) ship with all system animations switched off, which makes
 * every screen jump. Kosher Switch keeps its own animations running at normal speed on any phone,
 * without touching the phone's settings, and slides its screens in and out itself when the phone
 * doesn't.
 */
object Motion {
    val EASE = PathInterpolator(0.2f, 0.9f, 0.1f, 1f)

    /** Runs this app's animations at normal speed even when the phone's animation setting is off. */
    fun enable() {
        if (ValueAnimator.areAnimatorsEnabled()) return
        runCatching {
            ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, 1f)
        }
    }

    /** True when the phone itself draws no screen transitions. */
    fun systemTransitionsOff(a: Activity) =
        Settings.Global.getFloat(a.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f

    fun enter(content: View) {
        content.alpha = 0f
        content.translationY = content.resources.displayMetrics.density * 28
        content.animate().alpha(1f).translationY(0f).setDuration(280).setInterpolator(EASE).start()
    }

    /** Cards rise in one after another the first time a screen opens. */
    fun stagger(views: List<View>) {
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = v.resources.displayMetrics.density * 24
            v.animate().alpha(1f).translationY(0f).setStartDelay(40L + i * 45L).setDuration(360).setInterpolator(EASE).start()
        }
    }

    /** Opens a section by growing its height from 0 while it fades in. */
    fun expand(v: View, done: () -> Unit = {}) {
        val parent = v.parent as? View
        val width = (parent?.width ?: 0) - (parent?.paddingLeft ?: 0) - (parent?.paddingRight ?: 0)
        v.measure(
            View.MeasureSpec.makeMeasureSpec(width.coerceAtLeast(1), if (width > 0) View.MeasureSpec.EXACTLY else View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val target = v.measuredHeight
        v.layoutParams.height = 1
        v.alpha = 0f
        v.visibility = View.VISIBLE
        v.requestLayout()
        ValueAnimator.ofInt(1, target).apply {
            duration = (220 + target / v.resources.displayMetrics.density * 0.35f).toLong().coerceAtMost(520)
            interpolator = EASE
            addUpdateListener {
                v.layoutParams.height = it.animatedValue as Int
                v.alpha = it.animatedFraction.coerceAtMost(1f)
                v.requestLayout()
            }
            doOnEnd {
                v.layoutParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                v.requestLayout()
                done()
            }
            start()
        }
    }

    /** Closes a section by shrinking its height to 0 while it fades out. */
    fun collapse(v: View, done: () -> Unit = {}) {
        val start = v.height
        ValueAnimator.ofInt(start, 0).apply {
            duration = (180 + start / v.resources.displayMetrics.density * 0.25f).toLong().coerceAtMost(420)
            interpolator = EASE
            addUpdateListener {
                v.layoutParams.height = it.animatedValue as Int
                v.alpha = 1f - it.animatedFraction
                v.requestLayout()
            }
            doOnEnd {
                v.visibility = View.GONE
                v.layoutParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                v.alpha = 1f
                done()
            }
            start()
        }
    }

    private fun ValueAnimator.doOnEnd(block: () -> Unit) = addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) = block()
    })

    fun exit(content: View, done: () -> Unit) {
        content.animate().alpha(0f).translationY(content.resources.displayMetrics.density * 20)
            .setDuration(170).setInterpolator(EASE).withEndAction(done).start()
    }
}
