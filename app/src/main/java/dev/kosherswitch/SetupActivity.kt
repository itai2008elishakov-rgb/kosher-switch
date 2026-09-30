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
    private var animated = false

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
        if (!GuideActivity.seen(this)) openGuide()
    }

    private fun openGuide() {
        startActivity(GuideActivity.intent(this))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
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
            addView(adviceCard())
            addView(assistantCard())
            addView(weatherCard())
            addView(webFilterCard())
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Theme.PAGE)
            addView(content)
        })
        // Only the first time the screen opens; after a change the page stays put.
        if (!animated) {
            animated = true
            Motion.stagger((0 until content.childCount).map(content::getChildAt))
        }
    }

    /**
     * Our advice, said plainly: a kosher phone shouldn't have a browser, not even a filtered one.
     * That's why the Kosher Browser is off by default. It's advice; the choice stays with the family.
     */
    private fun adviceCard() = Theme.card(this,
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Theme.text(context, "🌐", 22f), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = Theme.dp(context, 10) })
            addView(Theme.text(context, getString(R.string.advice_title), 18f, Theme.INK, Theme.MEDIUM),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        },
        Theme.text(this, getString(R.string.advice_text), 14f, Theme.SUB).apply {
            setPadding(0, Theme.dp(context, 8), 0, 0)
            setLineSpacing(0f, 1.25f)
        },
        Theme.text(this, getString(R.string.advice_choice), 13f, Theme.GOLD, Theme.MEDIUM).apply {
            setPadding(0, Theme.dp(context, 10), 0, 0)
        },
    ).apply {
        background = Theme.rounded(context, Theme.CARD, 22, Theme.GOLD)
    }

    /** Asks once more before turning on something we advise against. [onAnswer] gets true for "turn on anyway". */
    private fun confirmAgainstAdvice(title: String, text: String, onAnswer: (Boolean) -> Unit) {
        android.app.AlertDialog.Builder(this, if (Theme.dark) android.R.style.Theme_DeviceDefault_Dialog_Alert
            else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle(title)
            .setMessage(text)
            .setPositiveButton(R.string.advice_keep_off) { _, _ -> onAnswer(false) }
            .setNegativeButton(R.string.advice_turn_on) { _, _ -> onAnswer(true) }
            .setOnCancelListener { onAnswer(false) }
            .show()
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
            setOnClickListener { openGuide() }
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
            var button: android.widget.Button? = null
            button = Theme.button(this, getString(R.string.change_code), outline = true) {
                val b = button ?: return@button
                if (form.visibility == View.GONE) {
                    b.text = getString(R.string.cancel)
                    Motion.expand(form) { current?.requestFocus() }
                } else {
                    b.text = getString(R.string.change_code)
                    error.text = ""
                    listOfNotNull(current, new1, new2).forEach { it.text = null }
                    Motion.collapse(form)
                }
            }
            card.addView(button)
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
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        var built = false
        var toggle: android.widget.Button? = null
        toggle = Theme.button(this, getString(R.string.show_apps), outline = true) {
            val b = toggle ?: return@button
            if (panel.visibility == View.GONE) {
                if (!built) { buildAppsPanel(panel, allowed, network, ::updateSummary); built = true }
                appsOpen = true
                b.text = getString(R.string.hide_apps)
                b.setTextColor(android.graphics.Color.WHITE)
                b.background = Theme.rounded(this, Theme.BLUE, 28)
                Motion.expand(panel)
            } else {
                appsOpen = false
                b.text = getString(R.string.show_apps)
                b.setTextColor(Theme.LINK)
                b.background = Theme.rounded(this, android.graphics.Color.TRANSPARENT, 28, Theme.LINK)
                // Bring the card's top back into view as the list folds away.
                (card.parent?.parent as? ScrollView)?.let { sv -> if (sv.scrollY > card.top) sv.smoothScrollTo(0, card.top) }
                Motion.collapse(panel) { findViewById<android.widget.EditText>(R.id.apps_search)?.setText("") }
            }
        }
        card.addView(toggle)
        card.addView(panel)
        // Build the list quietly right after the screen appears, so "Choose apps" opens at once.
        if (!appsOpen) card.postDelayed({
            if (!built && !isFinishing) { buildAppsPanel(panel, allowed, network, ::updateSummary); built = true }
        }, 900)
        if (appsOpen) {
            buildAppsPanel(panel, allowed, network, ::updateSummary); built = true
            panel.visibility = View.VISIBLE
            toggle.text = getString(R.string.hide_apps)
            toggle.setTextColor(android.graphics.Color.WHITE)
            toggle.background = Theme.rounded(this, Theme.BLUE, 28)
        }
        return card
    }

    /** Browsers get a "not recommended" note, and allowing one online asks once more. */
    private fun browserPackages(): Set<String> {
        val web = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
        return packageManager.queryIntentActivities(web, android.content.pm.PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }.toSet() + setOf("com.google.android.googlequicksearchbox")
    }

    private fun buildAppsPanel(panel: LinearLayout, allowed: MutableSet<String>, network: MutableSet<String>, updateSummary: () -> Unit) {
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(Theme.text(this, getString(R.string.apps_explain), 13f, Theme.SUB).apply {
            setPadding(0, Theme.dp(context, 12), 0, 0)
        })
        panel.addView(Theme.text(this, getString(R.string.settings_locked_note), 13f, Theme.SUB).apply {
            setPadding(0, Theme.dp(context, 6), 0, 0)
        })
        val search = EditText(this).apply {
            id = R.id.apps_search
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
        panel.addView(search, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = Theme.dp(this@SetupActivity, 12) })
        panel.addView(columnTitles())
        panel.addView(list)

        val browsers = browserPackages()
        val rows = launchableApps(this)
            .filterNot { it.pkg in ModeManager.ALWAYS_BLOCKED }
            .sortedWith(compareByDescending<AppEntry> { it.pkg in allowed }.thenBy { it.label.lowercase() })
            .map { app ->
                val isBrowser = app.pkg in browsers
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
                var asking = false
                net.setOnCheckedChangeListener { v, on ->
                    if (asking) return@setOnCheckedChangeListener
                    Theme.haptic(v)
                    fun apply(value: Boolean) {
                        if (value) network += app.pkg else network -= app.pkg
                        Allowlist.setNetworkApps(this, network)
                        updateSummary()
                    }
                    if (on && isBrowser) {
                        confirmAgainstAdvice(getString(R.string.advice_browser_app_title, app.label), getString(R.string.advice_browser_app_text)) { yes ->
                            if (yes) apply(true) else { asking = true; net.isChecked = false; asking = false }
                        }
                    } else apply(on)
                }
                app to appRow(app, show, net, isBrowser).also(list::addView)
            }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val q = s.toString().trim().lowercase()
                rows.forEach { (app, row) -> row.visibility = if (q.isEmpty() || app.label.lowercase().contains(q)) View.VISIBLE else View.GONE }
            }
            override fun beforeTextChanged(s: CharSequence, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence, a: Int, b: Int, c: Int) = Unit
        })
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
                    var asking = false
                    setOnCheckedChangeListener { v, on ->
                        if (asking) return@setOnCheckedChangeListener
                        Theme.haptic(v)
                        if (!on) { Looks.setAssistantAllowed(context, false); return@setOnCheckedChangeListener }
                        confirmAgainstAdvice(getString(R.string.advice_assistant_title), getString(R.string.advice_assistant)) { yes ->
                            if (yes) Looks.setAssistantAllowed(context, true) else { asking = true; isChecked = false; asking = false }
                        }
                    }
                })
            },
            Theme.text(this, getString(R.string.assistant_allow_note), 13f, Theme.SUB),
            Theme.text(this, getString(R.string.advice_assistant), 13f, Theme.GOLD).apply {
                setPadding(0, Theme.dp(context, 6), 0, 0)
            },
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

    /** The filter for apps allowed online. Always on; a parent can only choose to allow pictures. */
    private fun webFilterCard() = Theme.card(this,
        stepTitle(R.string.web_filter, true),
        Theme.text(this, getString(R.string.web_filter_note), 13f, Theme.SUB),
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Theme.dp(context, 8), 0, Theme.dp(context, 4))
            addView(Theme.text(context, getString(R.string.web_pictures), 16f).apply {
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(toggle(Looks.webPictures(context)).apply {
                setOnCheckedChangeListener { v, on -> Theme.haptic(v); Looks.setWebPictures(context, on) }
            })
        },
        Theme.text(this, getString(R.string.web_pictures_note), 13f, Theme.SUB),
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Theme.dp(context, 14), 0, Theme.dp(context, 4))
            addView(Theme.text(context, getString(R.string.browser_allow), 16f).apply {
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(toggle(Looks.browserAllowed(context)).apply {
                var asking = false
                setOnCheckedChangeListener { v, on ->
                    if (asking) return@setOnCheckedChangeListener
                    Theme.haptic(v)
                    if (!on) { Looks.setBrowserAllowed(context, false); return@setOnCheckedChangeListener }
                    confirmAgainstAdvice(getString(R.string.advice_browser_title), getString(R.string.advice_browser_text)) { yes ->
                        if (yes) Looks.setBrowserAllowed(context, true) else { asking = true; isChecked = false; asking = false }
                    }
                }
            })
        },
        Theme.text(this, getString(R.string.browser_allow_note), 13f, Theme.SUB),
        Theme.text(this, getString(R.string.advice_browser_short), 13f, Theme.GOLD).apply {
            setPadding(0, Theme.dp(context, 6), 0, 0)
        },
    )

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

    private fun appRow(app: AppEntry, show: Switch, net: Switch, browser: Boolean) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, Theme.dp(context, 8), 0, Theme.dp(context, 8))
        val size = Theme.dp(context, 36)
        addView(ImageView(context).apply {
            setImageDrawable(packageManager.getApplicationIcon(app.pkg))
        }, LinearLayout.LayoutParams(size, size))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Theme.dp(context, 12), 0, Theme.dp(context, 8), 0)
            addView(Theme.text(context, app.label, 15f).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            })
            if (browser) addView(Theme.text(context, getString(R.string.advice_browser_label), 12f, Theme.GOLD).apply {
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            })
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
        textSize = 16f
        background = Theme.rounded(context, Theme.CHIP, 16)
        val p = Theme.dp(context, 14)
        setPadding(p, Theme.dp(context, 12), p, Theme.dp(context, 12))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = Theme.dp(context, 10) }
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    }
}
