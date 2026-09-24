package dev.kosherswitch

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** The app's own language (Hebrew / English / phone default). Prayer texts stay in Hebrew. */
object Lang {
    private const val KEY = "language"
    val CHOICES = listOf("", "iw", "en") // "" = phone default

    fun get(ctx: Context): String = prefs(ctx).getString(KEY, "") ?: ""

    fun set(ctx: Context, lang: String) = prefs(ctx).edit().putString(KEY, lang).commit()

    fun wrap(base: Context): Context {
        val lang = get(base)
        if (lang.isEmpty()) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale(lang))
        return base.createConfigurationContext(config)
    }
}

/** All screens: apply the chosen language and the shared transition style. */
open class BaseActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        Theme.load(this)
        super.onCreate(savedInstanceState)
    }
}
