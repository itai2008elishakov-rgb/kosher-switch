package dev.kosherswitch

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Shared page layout: blue title bar on top. Hebrew content pages are always right-to-left. */
object KosherPage {
    private fun rtl(a: Activity) = a is ReaderActivity || a is TimesActivity || a is SiddurActivity ||
        a.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

    private fun direction(a: Activity) = if (rtl(a)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

    /** Blue title bar with a back button at the start (right side in Hebrew). */
    fun titleBar(activity: Activity, title: String, vararg actions: View) = LinearLayout(activity).apply {
        layoutDirection = direction(activity)
        gravity = Gravity.CENTER_VERTICAL
        background = Theme.skyGradient()
        val p = Theme.dp(context, 8)
        setPadding(p, Theme.dp(context, 10), p, Theme.dp(context, 10))
        addView(barButton(activity, if (rtl(activity)) "→" else "←") { activity.finish() })
        addView(Theme.text(context, title, 20f, Color.WHITE, Theme.MEDIUM).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(Theme.dp(context, 4), 0, Theme.dp(context, 4), 0)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actions.forEach(::addView)
    }

    fun barButton(activity: Activity, label: String, onClick: () -> Unit): TextView =
        Theme.text(activity, label, 22f, Color.WHITE, Theme.MEDIUM).apply {
            gravity = Gravity.CENTER
            val size = Theme.dp(context, 44)
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = Theme.rounded(context, 0x1FFFFFFF, 22)
            Theme.pressable(this)
            setOnClickListener { onClick() }
        }

    /** Whole-screen column: title bar on top, [body] filling the rest. */
    fun page(activity: Activity, bar: View, body: View) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = direction(activity)
        setBackgroundColor(Theme.PAGE)
        addView(bar)
        addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** A bottom sheet listing [items]; tapping one calls [onPick] and closes the sheet. */
    fun sheet(activity: Activity, title: String, items: List<String>, cancelable: Boolean = true, onPick: (Int) -> Unit) {
        val dialog = android.app.Dialog(activity)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        items.forEachIndexed { i, item ->
            list.addView(Theme.text(activity, item, 18f).apply {
                val p = Theme.dp(context, 14)
                setPadding(Theme.dp(context, 22), p, Theme.dp(context, 22), p)
                Theme.pressable(this)
                setOnClickListener { dialog.dismiss(); onPick(i) }
            })
        }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = direction(activity)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Theme.CARD)
                val r = Theme.dp(context, 28).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(0, Theme.dp(context, 10), 0, Theme.dp(context, 24))
            addView(View(context).apply { background = Theme.rounded(context, Theme.LINE, 3) },
                LinearLayout.LayoutParams(Theme.dp(context, 40), Theme.dp(context, 5)).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(Theme.text(context, title, 20f, Theme.INK, Theme.MEDIUM).apply {
                setPadding(Theme.dp(context, 22), Theme.dp(context, 14), Theme.dp(context, 22), Theme.dp(context, 6))
            })
            addView(android.widget.ScrollView(context).apply { addView(list) })
        }
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(body)
        dialog.setCancelable(cancelable)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setWindowAnimations(android.R.style.Animation_InputMethod)
            attributes = attributes.apply { dimAmount = 0.4f }
        }
        dialog.show()
    }
}
