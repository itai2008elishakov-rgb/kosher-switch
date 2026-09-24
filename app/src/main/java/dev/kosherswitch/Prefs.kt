package dev.kosherswitch

import android.content.Context
import android.content.SharedPreferences

internal fun prefs(ctx: Context): SharedPreferences =
    ctx.getSharedPreferences("kosher_switch", Context.MODE_PRIVATE)
