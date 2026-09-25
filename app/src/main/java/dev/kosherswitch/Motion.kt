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

    fun exit(content: View, done: () -> Unit) {
        content.animate().alpha(0f).translationY(content.resources.displayMetrics.density * 20)
            .setDuration(170).setInterpolator(EASE).withEndAction(done).start()
    }
}
