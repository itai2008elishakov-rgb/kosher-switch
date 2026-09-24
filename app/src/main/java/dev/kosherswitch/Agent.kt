package dev.kosherswitch

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract

/**
 * Understands phone commands (Hebrew or English) and turns them into actions.
 * Anything that isn't a command becomes a question for the AI.
 */
object Agent {
    class Contact(val name: String, val number: String)

    sealed class Action {
        class Call(val contact: Contact) : Action()
        class Message(val contact: Contact, val text: String) : Action()
        class Alarm(val hour: Int, val minute: Int) : Action()
        class Timer(val seconds: Int) : Action()
        /** A note to save as-is ([generate] = false) or to write with the AI first. */
        class Note(val content: String, val generate: Boolean) : Action()
        class OpenApp(val label: String, val intent: Intent) : Action()
        class Look(val done: String) : Action()
        class NotFound(val what: String) : Action()
        class Ask(val question: String) : Action()
    }

    private val GENERATE_WORDS = listOf("recipe", "how to", "how do", "list", "plan", "ideas", "explain", "steps", "guide", "write",
        "מתכון", "איך", "רשימה", "תכנית", "תוכנית", "רעיונות", "הסבר", "שלבים", "מדריך")

    fun understand(ctx: Context, input: String): Action {
        val text = input.trim().trimEnd('.', '!', '?')
        val lower = text.lowercase()

        // Alarm: "wake me up at 6:30", "תעיר אותי ב-7"
        Regex("""(?:wake me(?: up)?|set (?:an )?alarm|alarm|תעיר(?:י)? אותי|העירו אותי|שעון מעורר|כוון שעון|תכוון שעון)\D*(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm|בבוקר|בערב|בלילה)?""")
            .find(lower)?.let { m ->
                var h = m.groupValues[1].toInt()
                val min = m.groupValues[2].toIntOrNull() ?: 0
                val half = m.groupValues[3]
                if ((half == "pm" || half == "בערב" || half == "בלילה") && h < 12) h += 12
                if (half == "am" && h == 12) h = 0
                if (h in 0..23 && min in 0..59) return Action.Alarm(h, min)
            }
        // Timer: "timer for 10 minutes", "טיימר ל-10 דקות"
        Regex("""(?:timer|טיימר)\D*(\d+)\s*(sec|second|seconds|min|minute|minutes|hour|hours|שניות|שנייה|דקות|דקה|שעות|שעה)""")
            .find(lower)?.let { m ->
                val n = m.groupValues[1].toInt()
                val unit = m.groupValues[2]
                val secs = when {
                    unit.startsWith("sec") || unit.startsWith("שני") -> n
                    unit.startsWith("hour") || unit.startsWith("שע") -> n * 3600
                    else -> n * 60
                }
                return Action.Timer(secs)
            }
        // Call: "call Mom", "תתקשר לאמא"
        Regex("""^(?:please\s+)?(?:call|phone|dial|ring)\s+(.+)$""").find(lower)?.let { m ->
            return contactAction(ctx, m.groupValues[1]) { c, _ -> Action.Call(c) }
        }
        Regex("""^(?:תתקשר|התקשר|תתקשרי|התקשרי|חייג|תחייג|תחייגי)\s+(?:אל\s+)?(.+)$""").find(text)?.let { m ->
            return contactAction(ctx, m.groupValues[1]) { c, _ -> Action.Call(c) }
        }
        // Message: "text Dad I'm on my way", "send a message to Dad saying...", "שלח הודעה לאבא שאני בדרך"
        Regex("""^(?:send (?:a )?(?:message|text|sms) to|message|text)\s+(.+)$""").find(lower)?.let { m ->
            return contactAction(ctx, text.substring(m.groups[1]!!.range.first)) { c, rest ->
                Action.Message(c, rest.replace(Regex("""^(?:that|saying|:|,)\s*""", RegexOption.IGNORE_CASE), ""))
            }
        }
        Regex("""^(?:שלח|תשלח|שלחי|תשלחי)\s+(?:הודעה|מסרון|sms)?\s*(?:אל\s+)?(.+)$""").find(text)?.let { m ->
            return contactAction(ctx, m.groupValues[1]) { c, rest ->
                Action.Message(c, rest.replace(Regex("""^(?:ש|:|,|תגיד ש|להגיד ש)\s*"""), ""))
            }
        }
        // Note: "make me a note of a recipe for shepherd's pie", "תכתוב לי פתק: לקנות חלב"
        Regex("""(?:write|make|create|take|save|add)(?: me)? (?:a )?note(?:\s+(?:about|on|of|for|with|that|saying))?[:,]?\s*(.*)""")
            .find(lower)?.let { m -> return noteAction(text.substring(m.groups[1]!!.range.first)) }
        Regex("""(?:תכתוב|כתוב|תרשום|רשום|תעשה|צור|תכין|הכן)\s+(?:לי\s+)?פתק(?:\s+(?:על|עם|של))?[:,]?\s*(.*)""")
            .find(text)?.let { m -> return noteAction(m.groupValues[1]) }
        // Look and layout
        look(ctx, lower, text)?.let { return it }
        // Open an app: "open camera", "פתח את המצלמה"
        Regex("""^(?:open|launch|start|go to)\s+(?:the\s+)?(.+)$""").find(lower)?.let { m -> return openApp(ctx, m.groupValues[1]) }
        Regex("""^(?:פתח|תפתח|פתחי|תפתחי)\s+(?:את\s+)?(?:ה)?(.+)$""").find(text)?.let { m -> return openApp(ctx, m.groupValues[1]) }
        return Action.Ask(text)
    }

    private fun noteAction(content: String): Action {
        val c = content.trim()
        val generate = c.isNotEmpty() && GENERATE_WORDS.any { c.lowercase().contains(it) }
        return Action.Note(c, generate)
    }

    // ---- Contacts ----

    /** Finds the contact named at the start of [rest]; [make] gets it plus the words after the name. */
    private fun contactAction(ctx: Context, rest: String, make: (Contact, String) -> Action): Action {
        val contacts = contacts(ctx)
        val candidates = listOf(rest.trim(), rest.trim().removePrefix("ל").removePrefix("-"))
        for (s in candidates) {
            val low = s.lowercase()
            val hit = contacts.filter { low.startsWith(it.name.lowercase()) }.maxByOrNull { it.name.length }
                ?: contacts.filter { c -> c.name.lowercase().split(' ').any { it.length > 1 && low.startsWith(it) } }
                    .maxByOrNull { c -> c.name.lowercase().split(' ').filter { low.startsWith(it) }.maxOf { it.length } }
            if (hit != null) {
                val first = hit.name.lowercase().split(' ').filter { low.startsWith(it) }.maxByOrNull { it.length }
                    ?: hit.name.lowercase()
                val after = s.substring(minOf(s.length, if (low.startsWith(hit.name.lowercase())) hit.name.length else first.length)).trim()
                return make(hit, after)
            }
        }
        return Action.NotFound(rest.trim().split(' ').take(2).joinToString(" "))
    }

    fun contacts(ctx: Context): List<Contact> {
        grant(ctx, Manifest.permission.READ_CONTACTS)
        val out = mutableListOf<Contact>()
        runCatching {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    if (out.none { it.name == name }) out += Contact(name, number)
                }
            }
        }
        return out
    }

    // ---- Look and layout ----

    private fun look(ctx: Context, lower: String, text: String): Action? {
        fun done(s: String) = Action.Look(s)
        when {
            Regex("""dark (?:mode|theme)|מצב כהה|ערכת נושא כהה""").containsMatchIn(lower) -> {
                Looks.setMode(ctx, Looks.Mode.DARK); return done(ctx.getString(R.string.agent_done_dark))
            }
            Regex("""light (?:mode|theme)|מצב בהיר|ערכת נושא בהירה""").containsMatchIn(lower) -> {
                Looks.setMode(ctx, Looks.Mode.LIGHT); return done(ctx.getString(R.string.agent_done_light))
            }
            Regex("""(glass|kosher|original|זכוכית|כשר|מקורי)\S*\s+icons|(?:סמלים|אייקונים)\s+(?:של\s+)?(זכוכית|כשרים|מקוריים)""").find(lower) != null -> {
                val m = Regex("""glass|זכוכית|original|מקורי""").find(lower)?.value
                val style = when (m) { "glass", "זכוכית" -> Looks.Icons.GLASS; "original", "מקורי" -> Looks.Icons.ORIGINAL; else -> Looks.Icons.KOSHER }
                Looks.setIcons(ctx, style); return done(ctx.getString(R.string.agent_done_icons))
            }
        }
        val scenes = mapOf("blue" to "blue", "כחול" to "blue", "night" to "night", "לילה" to "night", "jerusalem" to "jerusalem",
            "gold" to "jerusalem", "ירושלים" to "jerusalem", "זהב" to "jerusalem", "sea" to "sea", "ocean" to "sea", "ים" to "sea",
            "forest" to "forest", "green" to "forest", "יער" to "forest", "ירוק" to "forest")
        if (Regex("""background|wallpaper|רקע""").containsMatchIn(lower)) {
            scenes.entries.firstOrNull { lower.contains(it.key) }?.let {
                Looks.setScene(ctx, it.value)
                if (ModeManager.isClosed(ctx)) ModeManager.inBackground({ Wallpapers.applyKosher(ctx) })
                return done(ctx.getString(R.string.agent_done_background))
            }
        }
        // "put Siddur in the dock", "שים את הסידור בשורה התחתונה"
        (Regex("""(?:put|add|move)\s+(.+?)\s+(?:in|to|into|on)\s+(?:the\s+)?dock""").find(lower)
            ?: Regex("""(?:שים|תשים|הוסף|תוסיף|הכנס|תכניס)\s+(?:את\s+)?(?:ה)?(.+?)\s+ב(?:מעגן|דוק|שורה התחתונה|למטה)""").find(text))
            ?.let { m ->
                val app = findApp(ctx, m.groupValues[1]) ?: return Action.NotFound(m.groupValues[1])
                val dock = (Looks.dock(ctx) ?: listOf("dialer", "messag", "siddur")).toMutableList()
                if (app.first !in dock) { dock.add(0, app.first); Looks.setDock(ctx, dock.take(3)) }
                return done(ctx.getString(R.string.agent_done_dock, app.second))
            }
        // "move Siddur first", "תזיז את הסידור להתחלה"
        (Regex("""(?:put|move)\s+(.+?)\s+(?:first|to the (?:top|start|beginning))""").find(lower)
            ?: Regex("""(?:שים|תשים|הזז|תזיז)\s+(?:את\s+)?(?:ה)?(.+?)\s+(?:ראשון|ראשונה|להתחלה|בהתחלה|למעלה)""").find(text))
            ?.let { m ->
                val app = findApp(ctx, m.groupValues[1]) ?: return Action.NotFound(m.groupValues[1])
                Looks.setOrder(ctx, listOf(app.first) + Looks.order(ctx).filterNot { it == app.first })
                return done(ctx.getString(R.string.agent_done_first, app.second))
            }
        return null
    }

    // ---- Apps ----

    /** The apps the assistant may open: allowed apps plus the built-in ones, as (key, label, intent). */
    private fun apps(ctx: Context): List<Triple<String, String, Intent>> {
        val allowed = if (ModeManager.isClosed(ctx)) ModeManager.allowedInClosed(ctx) else null
        val installed = launchableApps(ctx).filter { allowed == null || it.pkg in allowed }.mapNotNull { a ->
            ctx.packageManager.getLaunchIntentForPackage(a.pkg)?.let { Triple(a.pkg, a.label, it) }
        }
        fun own(key: String, label: String, cls: Class<*>) = Triple(key, label, Intent(ctx, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return installed + listOf(
            own("siddur", "סידור siddur prayer tehillim תהלים", SiddurActivity::class.java),
            own("times", "לוח וזמנים calendar zmanim times luach", TimesActivity::class.java),
            own("notes", "פתקים notes", NotesActivity::class.java),
            own("kosher_settings", "הגדרות settings", KosherSettingsActivity::class.java),
        )
    }

    /** "the Siddur" → "siddur", "את הסידור" → "סידור". */
    private fun appName(name: String) = name.trim().lowercase()
        .replace(Regex("""^(?:the|my|a|an|app|את)\s+"""), "")
        .replace(Regex("""\s+(?:app|application|אפליקציה|אפליקציית)$"""), "")
        .removePrefix("ה").trim()

    private fun findApp(ctx: Context, name: String): Pair<String, String>? {
        val q = appName(name)
        if (q.isEmpty()) return null
        fun hit(word: String) = word == q || word.startsWith(q) || (q.startsWith(word) && word.length > 2)
        for ((key, label, _) in apps(ctx)) {
            // Answer with the name the user used (e.g. "Siddur" rather than "סידור").
            val word = label.split(' ').firstOrNull { hit(it.lowercase()) } ?: continue
            return key to word.replaceFirstChar { it.uppercase() }
        }
        return null
    }

    private fun openApp(ctx: Context, name: String): Action {
        val q = appName(name)
        val hebrewAliases = mapOf("מצלמה" to "camera", "גלריה" to "gallery", "שעון" to "clock", "מחשבון" to "calculator",
            "אנשי קשר" to "contacts", "הודעות" to "messag", "טלפון" to "dialer", "חייגן" to "dialer", "מפות" to "maps", "ווייז" to "waze")
        val alias = hebrewAliases.entries.firstOrNull { q.contains(it.key) }?.value
        val found = apps(ctx).firstOrNull { (key, label, _) ->
            (alias != null && key.contains(alias)) || label.lowercase().split(' ').any { it.length > 1 && (it == q || q.contains(it) || it.startsWith(q)) }
        } ?: return Action.NotFound(name)
        val shown = found.second.split(' ').firstOrNull { it.lowercase() == q } ?: found.second.split(' ').first()
        return Action.OpenApp(shown.replaceFirstChar { it.uppercase() }, found.third)
    }

    // ---- Running actions ----

    fun call(ctx: Context, c: Contact) {
        grant(ctx, Manifest.permission.CALL_PHONE)
        val action = if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
            Intent.ACTION_CALL else Intent.ACTION_DIAL
        ctx.startActivity(Intent(action, Uri.parse("tel:" + Uri.encode(c.number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Opens Messages with the text ready; the user presses send. */
    fun message(ctx: Context, c: Contact, text: String) {
        ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(c.number)))
            .putExtra("sms_body", text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun alarm(ctx: Context, a: Action.Alarm) = runCatching {
        ctx.startActivity(Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, a.hour).putExtra(AlarmClock.EXTRA_MINUTES, a.minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    fun timer(ctx: Context, t: Action.Timer) = runCatching {
        ctx.startActivity(Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, t.seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    /** Contacts and calling need runtime permissions; as device owner we grant them once, silently. */
    private fun grant(ctx: Context, permission: String) {
        if (ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        if (!dpm.isDeviceOwnerApp(ctx.packageName)) return
        runCatching {
            dpm.setPermissionGrantState(KosherAdmin.component(ctx), ctx.packageName, permission,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
        }
    }
}
