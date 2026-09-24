package dev.kosherswitch

import android.content.Context
import android.text.Html
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A saved note. [html] holds the formatted text (bold, headings, lists, checklists);
 * [sketches] are drawings stored as PNG files next to the notes.
 */
data class Note(
    val id: Long,
    val title: String,
    val html: String,
    val updated: Long,
    val pinned: Boolean = false,
    val sketches: List<String> = emptyList(),
) {
    /** The note's text without formatting, for previews and search. */
    val plain: String get() = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim()

    val checked get() = plain.count { it == '☑' }
    val checkboxes get() = checked + plain.count { it == '☐' }
}

/** Notes kept in a small JSON file inside the app; drawings in a folder beside it. */
object Notes {
    private fun file(ctx: Context) = File(ctx.filesDir, "notes.json")

    fun sketchDir(ctx: Context) = File(ctx.filesDir, "sketches").apply { mkdirs() }

    @Synchronized
    fun all(ctx: Context): List<Note> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        val a = runCatching { JSONArray(f.readText()) }.getOrElse { JSONArray() }
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            // Notes from before formatting existed stored plain text in "body".
            val html = if (o.has("html")) o.getString("html") else fromPlain(o.optString("body"))
            val sketches = o.optJSONArray("sketches")?.let { s -> List(s.length()) { s.getString(it) } } ?: emptyList()
            Note(o.getLong("id"), o.optString("title"), html, o.optLong("updated"), o.optBoolean("pinned"), sketches)
        }.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updated })
    }

    fun get(ctx: Context, id: Long) = all(ctx).firstOrNull { it.id == id }

    /** Saves (creates or replaces) a note and returns it. */
    @Synchronized
    fun save(ctx: Context, note: Note): Note {
        val saved = note.copy(title = note.title.trim(), updated = System.currentTimeMillis())
        write(ctx, all(ctx).filterNot { it.id == saved.id } + saved)
        return saved
    }

    /** Saves plain text (e.g. from the assistant) as a new note. */
    fun savePlain(ctx: Context, title: String, text: String) =
        save(ctx, Note(System.currentTimeMillis(), title, fromPlain(text), 0))

    @Synchronized
    fun setPinned(ctx: Context, id: Long, pinned: Boolean) {
        write(ctx, all(ctx).map { if (it.id == id) it.copy(pinned = pinned) else it })
    }

    @Synchronized
    fun delete(ctx: Context, id: Long) {
        get(ctx, id)?.sketches?.forEach { File(sketchDir(ctx), it).delete() }
        write(ctx, all(ctx).filterNot { it.id == id })
    }

    fun fromPlain(text: String) = TextUtils.htmlEncode(text.trim()).replace("\n", "<br>")

    private fun write(ctx: Context, notes: List<Note>) {
        val a = JSONArray()
        notes.forEach { n ->
            a.put(JSONObject().put("id", n.id).put("title", n.title).put("html", n.html).put("updated", n.updated)
                .put("pinned", n.pinned).put("sketches", JSONArray(n.sketches)))
        }
        val tmp = File(ctx.filesDir, "notes.json.tmp")
        tmp.writeText(a.toString())
        tmp.renameTo(file(ctx))
    }
}
