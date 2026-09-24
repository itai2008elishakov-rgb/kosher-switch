package dev.kosherswitch

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.util.concurrent.Executors

/**
 * The assistant's language model, running fully offline on the phone (Qwen 2.5, 1.5B).
 * Nothing leaves the phone and it needs no internet, which keeps it kosher.
 */
object Ai {
    private const val TAG = "KosherAi"
    private const val MODEL = "assistant.task"
    const val MODEL_URL = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/" +
        "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    private const val MAX_TOKENS = 1280

    private val SYSTEM = """
        You are "Ozer", the built-in assistant of a kosher phone. You help with everyday things: notes,
        recipes, explanations, plans, lists, Jewish topics and practical questions.
        Keep every answer clean and suitable for a religious Jewish family. Politely decline anything
        immodest, violent, or about the internet, social media or websites.
        All food and recipes must be kosher: never mix meat and dairy in one dish or meal (for meat dishes
        use pareve substitutes such as oil, margarine or non-dairy milk instead of butter, milk or cheese),
        no pork, shellfish or other non-kosher foods, and say whether a recipe is meat, dairy or pareve.
        Reply in the same language the user writes in (Hebrew or English). Be warm and concise.
        Use short paragraphs or numbered steps. Never invent phone numbers or links.
    """.trimIndent()

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var llm: LlmInference? = null

    fun modelFile(ctx: Context) = File(ctx.getExternalFilesDir(null), MODEL)

    fun installed(ctx: Context) = modelFile(ctx).let { it.exists() && it.length() > 100_000_000 }

    val loaded get() = llm != null

    /** Loads the model (a few seconds, once); [done] gets true on success. */
    fun load(ctx: Context, done: (Boolean) -> Unit) {
        worker.execute {
            val ok = llm != null || runCatching {
                llm = LlmInference.createFromOptions(ctx.applicationContext,
                    LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile(ctx).absolutePath)
                        .setMaxTokens(MAX_TOKENS)
                        .build())
            }.onFailure { Log.e(TAG, "Model failed to load", it) }.isSuccess
            main.post { done(ok) }
        }
    }

    /**
     * Answers [prompt] (with a little earlier conversation for context). [onText] gets the whole
     * answer so far each time more arrives; [onDone] runs once at the end.
     */
    fun ask(history: List<Pair<String, String>>, prompt: String, onText: (String) -> Unit, onDone: (String) -> Unit) {
        worker.execute {
            val engine = llm ?: run { main.post { onDone("") }; return@execute }
            val text = buildString {
                append("<|im_start|>system\n$SYSTEM<|im_end|>\n")
                history.takeLast(2).forEach { (q, a) ->
                    append("<|im_start|>user\n$q<|im_end|>\n<|im_start|>assistant\n${a.take(600)}<|im_end|>\n")
                }
                append("<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n")
            }
            val answer = StringBuilder()
            val finished = java.util.concurrent.CountDownLatch(1)
            try {
                engine.generateResponseAsync(text) { partial, done ->
                    answer.append(partial)
                    val clean = tidy(answer.toString())
                    main.post { onText(clean) }
                    if (done) finished.countDown()
                }
                finished.await()
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed", e)
            }
            val final = tidy(answer.toString())
            main.post { onDone(final) }
        }
    }

    /** Model output → plain text: real line breaks, • bullets, no leftover chat markers. */
    fun tidy(raw: String) = raw.substringBefore("<|im_end|>").substringBefore("<|im_start|>")
        .replace("\\n", "\n")
        .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")
        .replace(Regex("(?m)^#+\\s*"), "")
        .trim()

    /** **bold** → bold text, for showing answers. */
    fun styled(text: String): CharSequence {
        val out = android.text.SpannableStringBuilder()
        var rest = text
        val bold = Regex("\\*\\*(.+?)\\*\\*")
        while (true) {
            val m = bold.find(rest) ?: break
            out.append(rest.substring(0, m.range.first))
            val start = out.length
            out.append(m.groupValues[1])
            out.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, out.length, 0)
            rest = rest.substring(m.range.last + 1)
        }
        return out.append(rest)
    }

    /** Plain text for notes (no ** markers). */
    fun plain(text: String) = text.replace("**", "")

    private var releaseTask: Runnable? = null

    /** Keeps the model loaded for a few minutes after the assistant closes, then frees its memory. */
    fun releaseLater() {
        releaseTask?.let(main::removeCallbacks)
        releaseTask = Runnable { worker.execute { llm?.close(); llm = null } }.also { main.postDelayed(it, 3 * 60_000L) }
    }

    fun cancelRelease() {
        releaseTask?.let(main::removeCallbacks)
        releaseTask = null
    }

    /** Downloads the model over Wi-Fi (open mode only; kosher mode has no internet for this app). */
    fun download(ctx: Context): Long {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        modelFile(ctx).delete()
        return dm.enqueue(DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle(ctx.getString(R.string.assistant))
            .setDescription(ctx.getString(R.string.assistant_downloading))
            .setAllowedOverMetered(false)
            .setDestinationInExternalFilesDir(ctx, null, MODEL)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED))
    }
}
