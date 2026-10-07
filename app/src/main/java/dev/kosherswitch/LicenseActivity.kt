package dev.kosherswitch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/** "Your plan": enter the licence key bought on the website. Opened from Setup, or when turning kosher mode on without a plan. */
class LicenseActivity : BaseActivity() {
    companion object {
        private const val EXTRA_THEN_TURN_ON = "then_turn_on"
        fun intent(ctx: Context, thenTurnOn: Boolean = false) = Intent(ctx, LicenseActivity::class.java)
            .putExtra(EXTRA_THEN_TURN_ON, thenTurnOn)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.lightBars(this)
        val he = Lang.isHebrew(this)
        val status = Theme.text(this, "", 15f, Theme.SUB)
        val field = EditText(this).apply {
            hint = if (he) "מפתח רישיון" else "Licence key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(License.key(context) ?: "")
            setSingleLine()
            textSize = 17f
            setTextColor(Theme.INK); setHintTextColor(Theme.SUB)
        }
        val message = Theme.text(this, "", 14f, Theme.GOLD_DARK)
        fun showStatus() {
            status.text = (if (he) "מצב: " else "Status: ") + License.status(this)
        }
        showStatus()
        lateinit var activate: android.widget.Button
        activate = Theme.button(this, if (he) "הפעלת המפתח" else "Activate key", fill = Theme.GOLD, textColor = Theme.NAVY) {
            val key = field.text.toString().trim()
            if (key.isEmpty()) return@button
            activate.isEnabled = false
            message.text = if (he) "בודק…" else "Checking…"
            License.activate(this, key) { r ->
                runOnUiThread {
                    activate.isEnabled = true
                    when (r) {
                        is License.Result.Ok -> {
                            message.text = if (he) "המסלול פעיל בטלפון הזה. תודה!" else "Your plan is active on this phone. Thank you!"
                            showStatus()
                            if (intent.getBooleanExtra(EXTRA_THEN_TURN_ON, false)) {
                                startActivity(SwitchActivity.intent(this, toClosed = true)); finish()
                            }
                        }
                        is License.Result.Error -> message.text = r.message
                    }
                }
            }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Theme.dp(context, 18)
            setPadding(p, Theme.dp(context, 4), p, Theme.dp(context, 40))
            addView(Theme.card(context,
                Theme.text(context, if (he) "מצב כשר צריך מסלול" else "Kosher mode needs a plan", 20f, Theme.HEADLINE, Theme.MEDIUM),
                Theme.text(context, if (he) "אישי: טלפון אחד, שבועיים חינם ואז €8.99 לחודש. יציאה ממצב כשר תמיד בחינם."
                    else "Personal: one phone, 14 days free, then €8.99 a month. Leaving kosher mode is always free.", 14f, Theme.SUB).apply {
                    setPadding(0, Theme.dp(context, 6), 0, Theme.dp(context, 10))
                },
                status,
            ))
            addView(Theme.card(context,
                Theme.text(context, if (he) "יש לכם מפתח?" else "Have a key?", 17f, Theme.INK, Theme.MEDIUM),
                Theme.text(context, if (he) "המפתח נשלח אליכם במייל אחרי הרכישה." else "Your key was emailed to you after buying.", 14f, Theme.SUB),
                field, activate, message,
            ))
            addView(Theme.card(context,
                Theme.text(context, if (he) "אין לכם עדיין מסלול?" else "No plan yet?", 17f, Theme.INK, Theme.MEDIUM),
                Theme.button(context, if (he) "שבועיים חינם" else "Start 14 days free", outline = true) {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(License.BUY_URL))) }
                },
            ))
        }
        setContentView(KosherPage.page(this, KosherPage.titleBar(this, if (he) "המסלול שלכם" else "Your plan"),
            ScrollView(this).apply { addView(body) }))
    }
}
