package dev.kosherswitch

import android.app.Activity
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView

/** Test builds only: every icon in every style, to compare designs on a real screen. */
class IconPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val size = Theme.dp(this, 52)
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun row(bg: Int, items: List<android.graphics.drawable.Drawable>) = GridLayout(this).apply {
            columnCount = 5
            setBackgroundColor(bg)
            setPadding(8, 8, 8, 8)
            items.forEach { d -> addView(ImageView(context).apply { setImageDrawable(d) }, GridLayout.LayoutParams().apply { width = size; height = size; setMargins(10, 10, 10, 10) }) }
        }
        val kinds = Icons.Kind.values().toList()
        page.addView(row(0xFF2B4F8F.toInt(), listOf(SiddurActivity::class.java, TimesActivity::class.java, NotesActivity::class.java,
            WeatherActivity::class.java, AssistantActivity::class.java, KosherSettingsActivity::class.java)
            .map { packageManager.getActivityIcon(ComponentName(this, it)) }))
        page.addView(row(0xFF1F6FD0.toInt(), Sky.values().flatMap { listOf(WeatherGlyph(it, true), WeatherGlyph(it, false)) }))
        page.addView(row(0xFF6E8FC2.toInt(), kinds.map { Icons.sample(it, true, false) }))
        page.addView(row(0xFF10151F.toInt(), kinds.map { Icons.sample(it, true, true) }))
        // Export the launcher icons' symbol layers (108dp adaptive canvas, symbol in the 72dp visible area).
        if (intent.hasExtra("export")) {
            val out = getExternalFilesDir("icons")!!
            mapOf("siddur" to Icons.Kind.SIDDUR, "luach" to Icons.Kind.TIMES, "notes" to Icons.Kind.NOTES,
                "weather" to Icons.Kind.WEATHER, "assistant" to Icons.Kind.ASSISTANT).forEach { (name, kind) ->
                val bmp = android.graphics.Bitmap.createBitmap(432, 432, android.graphics.Bitmap.Config.ARGB_8888)
                Icons.glyphOnly(kind).apply { setBounds(72, 72, 360, 360) }.draw(android.graphics.Canvas(bmp))
                java.io.File(out, "fg_$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.BLACK); addView(page) })
    }
}
