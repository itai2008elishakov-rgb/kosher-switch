package dev.kosherswitch

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** The kosher assistant: chat on glass, with real phone actions and an offline AI. */
class AssistantActivity : BaseActivity() {
    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var send: TextView
    private val history = mutableListOf<Pair<String, String>>()
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ModeManager.isClosed(this) && !Looks.assistantAllowed(this)) {
            finish()
            return
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setDecorFitsSystemWindows(false)

        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Theme.dp(context, 16)
            setPadding(p, p, p, p)
        }
        scroll = ScrollView(this).apply { addView(chat); isVerticalScrollBarEnabled = false }
        input = EditText(this).apply {
            hint = getString(R.string.ask_hint)
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(0x99FFFFFF.toInt())
            background = null
            maxLines = 4
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            imeOptions = EditorInfo.IME_ACTION_SEND
            setRawInputType(android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
            setOnEditorActionListener { _, id, e ->
                if (id == EditorInfo.IME_ACTION_SEND || e?.keyCode == KeyEvent.KEYCODE_ENTER) { submit(); true } else false
            }
        }
        send = Theme.text(this, "↑", 22f, Theme.NAVY, Theme.MEDIUM).apply {
            gravity = Gravity.CENTER
            background = Theme.rounded(context, Theme.GOLD, 22)
            Theme.pressable(this)
            setOnClickListener { submit() }
        }
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = Theme.glass(context, 28, 1.3f)
            val p = Theme.dp(context, 8)
            setPadding(Theme.dp(context, 18), p, p, p)
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(send, LinearLayout.LayoutParams(Theme.dp(context, 44), Theme.dp(context, 44)))
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            val p = Theme.dp(context, 16)
            setPadding(p, 0, p, 0)
            addView(Theme.text(context, "✦  " + getString(R.string.assistant), 22f, Color.WHITE, Theme.MEDIUM),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Theme.text(context, "✕", 20f, Color.WHITE).apply {
                gravity = Gravity.CENTER
                background = Theme.glass(context, 20)
                Theme.pressable(this)
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(Theme.dp(context, 40), Theme.dp(context, 40)))
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                val m = Theme.dp(context, 14)
                setMargins(m, 0, m, m)
            })
        }
        val root = FrameLayout(this).apply {
            addView(ImageView(context).apply {
                setImageBitmap(Backdrop.homeLayers(context).second)
                scaleType = ImageView.ScaleType.CENTER_CROP
            })
            addView(column)
        }
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            column.setPadding(0, bars.top + Theme.dp(this, 8), 0, bars.bottom)
            insets
        }
        setContentView(root)
        welcome()
        Ai.cancelRelease()
        if (Ai.installed(this)) Ai.load(this) {} // warm up while the user types
    }

    override fun onDestroy() {
        super.onDestroy()
        Ai.releaseLater()
    }

    // ---- Conversation ----

    private fun welcome() {
        chat.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, Theme.dp(context, 40), 0, Theme.dp(context, 20))
            addView(Theme.text(context, "✦", 44f, Theme.GOLD).apply { gravity = Gravity.CENTER })
            addView(Theme.text(context, getString(R.string.assistant_hello), 22f, Color.WHITE, Theme.MEDIUM).apply { gravity = Gravity.CENTER })
            addView(Theme.text(context, getString(R.string.assistant_private), 13f, 0xB3FFFFFF.toInt()).apply {
                gravity = Gravity.CENTER
                setPadding(0, Theme.dp(context, 6), 0, Theme.dp(context, 18))
            })
            resources.getStringArray(R.array.assistant_examples).forEach { example ->
                addView(Theme.text(context, example, 15f, Color.WHITE).apply {
                    gravity = Gravity.CENTER
                    background = Theme.glass(context, 20)
                    val h = Theme.dp(context, 18)
                    setPadding(h, Theme.dp(context, 11), h, Theme.dp(context, 11))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .apply { topMargin = Theme.dp(context, 8) }
                    Theme.pressable(this)
                    setOnClickListener { input.setText(example); input.setSelection(example.length) }
                })
            }
        })
    }

    private fun submit() {
        val text = input.text.toString().trim()
        if (text.isEmpty() || busy) return
        input.setText("")
        if (chat.childCount == 1 && history.isEmpty()) chat.removeAllViews()
        bubble(text, mine = true)
        Theme.haptic(send)
        handle(text)
    }

    private fun handle(text: String) {
        when (val a = Agent.understand(this, text)) {
            is Agent.Action.Call -> reply(getString(R.string.agent_call, a.contact.name), a.contact.number,
                getString(R.string.agent_call_button)) { Agent.call(this, a.contact) }
            is Agent.Action.Message -> reply(getString(R.string.agent_message, a.contact.name), a.text.ifBlank { "…" },
                getString(R.string.agent_message_button)) { Agent.message(this, a.contact, a.text) }
            is Agent.Action.Alarm -> {
                val ok = Agent.alarm(this, a)
                reply(if (ok) getString(R.string.agent_alarm, "%02d:%02d".format(a.hour, a.minute)) else getString(R.string.agent_failed))
            }
            is Agent.Action.Timer -> {
                val ok = Agent.timer(this, a)
                val label = if (a.seconds % 60 == 0) getString(R.string.minutes_short, a.seconds / 60) else getString(R.string.seconds_short, a.seconds)
                reply(if (ok) getString(R.string.agent_timer, label) else getString(R.string.agent_failed))
            }
            is Agent.Action.OpenApp -> {
                reply(getString(R.string.agent_opening, a.label))
                chat.postDelayed({ startActivity(a.intent) }, 350)
            }
            is Agent.Action.Look -> {
                reply(a.done)
                Theme.load(this)
            }
            is Agent.Action.NotFound -> reply(getString(R.string.agent_not_found, a.what))
            is Agent.Action.Note -> if (!a.generate) {
                val note = Notes.savePlain(this, a.content.lineSequence().first().take(40), a.content)
                reply(getString(R.string.agent_note_saved), note.title, getString(R.string.agent_open_note)) {
                    startActivity(NoteEditActivity.intent(this, note.id))
                }
            } else think(getString(R.string.agent_note_prompt, a.content)) { answer ->
                val note = Notes.savePlain(this, a.content.take(40).replaceFirstChar { it.uppercase() }, Ai.plain(answer))
                action(getString(R.string.agent_open_note)) { startActivity(NoteEditActivity.intent(this, note.id)) }
            }
            is Agent.Action.Ask -> think(a.question) { answer ->
                action(getString(R.string.agent_save_note)) {
                    val note = Notes.savePlain(this, a.question.take(40), Ai.plain(answer))
                    startActivity(NoteEditActivity.intent(this, note.id))
                }
            }
        }
    }

    /** Streams an AI answer into a new bubble; [then] runs with the finished text. */
    private fun think(question: String, then: (String) -> Unit) {
        if (!Ai.installed(this)) {
            reply(getString(R.string.assistant_no_brain))
            return
        }
        busy = true
        val view = bubble("", mine = false)
        val dots = typing(view, if (Ai.loaded) getString(R.string.assistant_thinking) else getString(R.string.assistant_waking))
        Ai.load(this) { ok ->
            if (!ok) {
                dots.cancel()
                view.text = getString(R.string.assistant_failed)
                busy = false
                return@load
            }
            var started = false
            val hebrew = Kashrut.isHebrew(question)
            val prompt = if (Kashrut.isFood(question)) question + Kashrut.reminder(hebrew) else question
            Ai.ask(history, prompt, onText = { partial ->
                if (!started && partial.isNotEmpty()) { started = true; dots.cancel() }
                if (started) { view.text = Ai.styled(partial); scrollDown() }
            }, onDone = { answer ->
                dots.cancel()
                busy = false
                if (answer.isBlank()) { view.text = getString(R.string.assistant_failed); return@ask }
                val checked = Kashrut.warning(answer, hebrew)?.let { "$answer\n\n$it" } ?: answer
                view.text = Ai.styled(checked)
                history += question to answer
                Theme.haptic(view)
                then(checked)
            })
        }
    }

    /** Animated "Thinking…" dots in [view] until cancelled. */
    private fun typing(view: TextView, label: String): ValueAnimator =
        ValueAnimator.ofInt(0, 3).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { view.text = label + ".".repeat(it.animatedValue as Int + 1) }
            start()
        }

    private fun reply(text: String, detail: String? = null, button: String? = null, run: (() -> Unit)? = null) {
        val v = bubble(text + (detail?.let { "\n$it" } ?: ""), mine = false)
        Theme.haptic(v)
        if (button != null && run != null) action(button, run)
    }

    private fun action(label: String, run: () -> Unit) {
        chat.addView(Theme.text(this, label, 15f, Theme.NAVY, Theme.MEDIUM).apply {
            gravity = Gravity.CENTER
            background = Theme.rounded(context, Theme.GOLD, 20)
            val h = Theme.dp(context, 20)
            setPadding(h, Theme.dp(context, 10), h, Theme.dp(context, 10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Theme.dp(context, 6); gravity = Gravity.START }
            Theme.pressable(this)
            setOnClickListener { run() }
            appear(this)
        })
        scrollDown()
    }

    private fun bubble(text: String, mine: Boolean): TextView {
        val v = Theme.text(this, text, 16f, if (mine) Theme.NAVY else Color.WHITE).apply {
            background = if (mine) Theme.rounded(context, 0xF2FFFFFF.toInt(), 20) else Theme.glass(context, 20, 1.2f)
            val h = Theme.dp(context, 16)
            setPadding(h, Theme.dp(context, 11), h, Theme.dp(context, 11))
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            setTextIsSelectable(!mine)
            movementMethod = LinkMovementMethod.getInstance()
            maxWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Theme.dp(context, 10)
                gravity = if (mine) Gravity.END else Gravity.START
            }
        }
        chat.addView(v)
        appear(v)
        scrollDown()
        return v
    }

    private fun appear(v: View) {
        v.alpha = 0f
        v.translationY = Theme.dp(this, 14).toFloat()
        v.animate().alpha(1f).translationY(0f).setDuration(240).start()
    }

    private fun scrollDown() = scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
}
