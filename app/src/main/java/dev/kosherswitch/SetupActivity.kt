package dev.kosherswitch

import android.app.NotificationManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast

/**
 * The Kosher Switch app in open mode: a three-step setup (code, apps, turn on),
 * usable by an adult for themselves or by a parent preparing a child's phone.
 */
class SetupActivity : BaseActivity() {
    private var appsOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.lightBars(this)
    }

    override fun onResume() {
        super.onResume()
        if (ModeManager.isClosed(this)) {
            finish()
            return
        }
        ModeManager.repairIfOpen(this)
        render()
        if (!GuideActivity.seen(this)) startActivity(GuideActivity.intent(this))
    }

    private fun render() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Theme.dp(context, 16)
            setPadding(p, p, p, Theme.dp(context, 32))
            addView(header())
            addView(parentsCard())
            addView(codeCard())
            addView(appsCard())
            addView(turnOnCard())
            addView(assistantCard())
            addView(weatherCard())
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Theme.PAGE)
            addView(content)
        })
    }

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Theme.skyGradient().apply { cornerRadius = Theme.dp(context, 28).toFloat() }
        val p = Theme.dp(context, 22)
        setPadding(p, p, p, p)
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            val size = Theme.dp(context, 56)
            addView(EmblemView(context), LinearLayout.LayoutParams(size, size))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Theme.dp(context, 14), 0, 0, 0)
                addView(Theme.text(context, getString(R.string.app_name), 26f, Color.WHITE, Theme.MEDIUM))
                addView(Theme.text(context, getString(R.string.setup_tagline), 14f, 0xCCFFFFFF.toInt()))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
        addView(Theme.text(context, "?  " + getString(R.string.how_it_works), 14f, Color.WHITE, Theme.MEDIUM).apply {
            background = Theme.glass(context, 18)
            val h = Theme.dp(context, 14)
            setPadding(h, Theme.dp(context, 7), h, Theme.dp(context, 7))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Theme.dp(context, 14) }
            Theme.pressable(this)
            setOnClickListener { startActivity(GuideActivity.intent(context)) }
        })
        val (status, detail) = when {
            !ModeManager.isDeviceOwner(context) -> getString(R.string.setup_not_finished) to getString(R.string.setup_connect)
            !PinStore.isSet(context) -> getString(R.string.almost_ready) to getString(R.string.choose_code_below)
            else -> getString(R.string.ready) to getString(R.string.ready_detail)
        }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = Theme.glass(context, 18)
            val q = Theme.dp(context, 14)
            setPadding(q, q, q, q)
            addView(Theme.text(context, status, 17f, Theme.GOLD, Theme.MEDIUM))
            addView(Theme.text(context, detail, 14f, 0xE6FFFFFF.toInt()))
            val nm = context.getSystemService(NotificationManager::class.java)
            if (!nm.isNotificationListenerAccessGranted(ComponentName(context, NotificationFilterService::class.java))) {
                // Without a computer: the user switches the filter on in Android's own screen.
                addView(Theme.text(context, getString(R.string.notif_setup), 13f, Theme.GOLD).apply {
                    setPadding(0, Theme.dp(context, 6), 0, 0)
                })
                addView(Theme.button(context, getString(R.string.notif_turn_on), fill = Theme.GOLD, textColor = Theme.NAVY) {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                })
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = Theme.dp(context, 18) })
    }

    private fun parentsCard() = Theme.card(this,
        Theme.text(this, getString(R.string.parents_title), 17f, Theme.INK, Theme.MEDIUM),
        Theme.text(this, getString(R.string.parents_text), 14f, Theme.SUB),
    )

    /** Step title with a ✓ once that step is done. */
    private fun stepTitle(res: Int, done: Boolean) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(Theme.text(context, getString(res), 18f, Theme.INK, Theme.MEDIUM),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (done) addView(Theme.text(context, "✓", 18f, Theme.OK, Theme.MEDIUM))
    }

    private fun codeCard(): LinearLayout {
        val hasCode = PinStore.isSet(this)
        val card = Theme.card(this,
            stepTitle(R.string.step_code, hasCode),
            Theme.text(this, getString(if (hasCode) R.string.code_needed else R.string.code_new_hint), 14f, Theme.SUB),
        )
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val current = if (hasCode) pinField(getString(R.string.current_code)).also(form::addView) else null
        val new1 = pinField(getString(if (hasCode) R.string.new_code else R.string.code)).also(form::addView)
        val new2 = pinField(getString(if (hasCode) R.string.new_code_again else R.string.code_again)).also(form::addView)
        val error = Theme.text(this, "", 14f, Theme.ERROR)
        form.addView(Theme.button(this, getString(R.string.save_code)) {
            val code = new1.text.toString()
            error.text = when {
                current != null && !PinStore.verify(this, current.text.toString()) -> getString(R.string.err_current_wrong)
                code.length < PinStore.MIN_LENGTH -> getString(R.string.err_min_digits, PinStore.MIN_LENGTH)
                code != new2.text.toString() -> getString(R.string.err_mismatch)
                else -> {
                    PinStore.set(this, code)
                    Toast.makeText(this, getString(R.string.code_saved), Toast.LENGTH_SHORT).show()
                    render()
                    return@button
                }
            }
            Theme.haptic(error, strong = true)
        })
        form.addView(error)
        if (hasCode) {
            form.visibility = View.GONE
            card.addView(Theme.button(this, getString(R.string.change_code), outline = true) {
                form.visibility = if (form.visibility == View.GONE) View.VISIBLE else View.GONE
            })
        }
        card.addView(form)
        return card
    }

    private fun appsCard(): LinearLayout {
        val allowed = Allowlist.apps(this).toMutableSet()
        val network = Allowlist.networkApps(this).toMutableSet()
        val summary = Theme.text(this, "", 14f, Theme.SUB)
        fun updateSummary() {
            summary.text = getString(R.string.apps_count, allowed.size, network.size)
        }
        updateSummary()
        val card = Theme.card(this, stepTitle(R.string.step_apps, allowed.isNotEmpty()), summary)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toggle = Theme.button(this, getString(if (appsOpen) R.string.hide_apps else R.string.show_apps), outline = !appsOpen) {
            appsOpen = !appsOpen
            render()
        }
        card.addView(toggle)
        if (!appsOpen) return card

        card.addView(Theme.text(this, getString(R.string.apps_explain), 13f, Theme.SUB).apply {
            setPadding(0, Theme.dp(context, 12), 0, 0)
        })
        card.addView(Theme.text(this, getString(R.string.settings_locked_note), 13f, Theme.SUB).apply {
            setPadding(0, Theme.dp(context, 6), 0, 0)
        })
        val search = EditText(this).apply {
            hint = getString(R.string.search_apps)
            setSingleLine()
            textSize = 15f
            setTextColor(Theme.INK)
            setHintTextColor(Theme.SUB)
            background = Theme.rounded(context, Theme.CHIP, 16)
            val p = Theme.dp(context, 14)
            setPadding(p, Theme.dp(context, 10), p, Theme.dp(context, 10))
            setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0)
        }
        card.addView(search, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = Theme.dp(this@SetupActivity, 12) })
        card.addView(columnTitles())
        card.addView(list)

        val rows = launchableApps(this)
            .filterNot { it.pkg in ModeManager.ALWAYS_BLOCKED }
            .sortedWith(compareByDescending<AppEntry> { it.pkg in allowed }.thenBy { it.label.lowercase() })
            .map { app ->
                val show = toggle(app.pkg in allowed)
                val net = toggle(app.pkg in network).apply { isEnabled = show.isChecked }
                show.setOnCheckedChangeListener { v, on ->
                    Theme.haptic(v)
                    if (on) allowed += app.pkg else {
                        allowed -= app.pkg
                        network -= app.pkg
                        net.isChecked = false
                    }
                    net.isEnabled = on
                    Allowlist.setApps(this, allowed)
                    Allowlist.setNetworkApps(this, network)
                    updateSummary()
                }
                net.setOnCheckedChangeListener { v, on ->
                    Theme.haptic(v)
                    if (on) network += app.pkg else network -= app.pkg
                    Allowlist.setNetworkApps(this, network)
                    updateSummary()
                }
                app to appRow(app, show, net).also(list::addView)
            }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val q = s.toString().trim().lowercase()
                rows.forEach { (app, row) -> row.visibility = if (q.isEmpty() || app.label.lowercase().contains(q)) View.VISIBLE else View.GONE }
            }
            override fun beforeTextChanged(s: CharSequence, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence, a: Int, b: Int, c: Int) = Unit
        })
        return card
    }

    private fun turnOnCard(): LinearLayout {
        val ready = ModeManager.isReady(this)
        return Theme.card(this,
            stepTitle(R.string.step_on, false),
            Theme.text(this, getString(R.string.turn_on_detail), 14f, Theme.SUB),
            Theme.button(this, getString(R.string.turn_on), fill = Theme.GOLD, textColor = Theme.NAVY) {
                if (!ready) return@button
                startActivity(SwitchActivity.intent(this, toClosed = true))
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }.apply { alpha = if (ready) 1f else 0.4f },
        )
    }

    /** The offline AI is downloaded once, over Wi-Fi, while the phone is still open. */
    private fun assistantCard(): LinearLayout {
        val installed = Ai.installed(this)
        val card = Theme.card(this,
            stepTitle(R.string.assistant_section, installed && Looks.assistantAllowed(this)),
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, Theme.dp(context, 8), 0, Theme.dp(context, 4))
                addView(Theme.text(context, getString(R.string.assistant_allow), 16f).apply {
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(toggle(Looks.assistantAllowed(context)).apply {
                    setOnCheckedChangeListener { v, on -> Theme.haptic(v); Looks.setAssistantAllowed(context, on) }
                })
            },
            Theme.text(this, getString(R.string.assistant_allow_note), 13f, Theme.SUB),
            Theme.text(this, getString(if (installed) R.string.assistant_installed else R.string.assistant_not_installed), 14f, Theme.SUB).apply {
                setPadding(0, Theme.dp(context, 8), 0, 0)
            },
        )
        if (!installed) card.addView(Theme.button(this, getString(R.string.assistant_download), outline = true) {
            Ai.download(this)
            Toast.makeText(this, getString(R.string.assistant_download_started), Toast.LENGTH_LONG).show()
        })
        return card
    }

    /** Weather is on by default; a parent can hide it (and its internet) in kosher mode. */
    private fun weatherCard() = Theme.card(this,
        stepTitle(R.string.weather, Looks.weatherAllowed(this)),
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Theme.dp(context, 8), 0, Theme.dp(context, 4))
            addView(Theme.text(context, getString(R.string.weather_allow), 16f).apply {
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(toggle(Looks.weatherAllowed(context)).apply {
                setOnCheckedChangeListener { v, on -> Theme.haptic(v); Looks.setWeatherAllowed(context, on) }
            })
        },
        Theme.text(this, getString(R.string.weather_allow_note), 13f, Theme.SUB),
    )

    private fun columnTitles() = LinearLayout(this).apply {
        setPadding(0, Theme.dp(context, 14), 0, Theme.dp(context, 4))
        addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        listOf(getString(R.string.col_show), getString(R.string.col_internet)).forEach {
            addView(Theme.text(context, it, 12f, Theme.SUB, Theme.MEDIUM).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(Theme.dp(context, 64), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun appRow(app: AppEntry, show: Switch, net: Switch) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, Theme.dp(context, 8), 0, Theme.dp(context, 8))
        val size = Theme.dp(context, 36)
        addView(ImageView(context).apply {
            setImageDrawable(packageManager.getApplicationIcon(app.pkg))
        }, LinearLayout.LayoutParams(size, size))
        addView(Theme.text(context, app.label, 15f).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setPadding(Theme.dp(context, 12), 0, Theme.dp(context, 8), 0)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        listOf(show, net).forEach {
            addView(it, LinearLayout.LayoutParams(Theme.dp(context, 64), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun toggle(on: Boolean) = Switch(this).apply {
        isChecked = on
        gravity = Gravity.CENTER
        thumbTintList = Theme.THUMB_TINT
        trackTintList = Theme.TRACK_TINT
    }

    private fun pinField(hintText: String) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        hint = hintText
        typeface = Theme.REGULAR
        setTextColor(Theme.INK)
        setHintTextColor(Theme.SUB)
        backgroundTintList = Theme.BLUE_TINT
    }
}
