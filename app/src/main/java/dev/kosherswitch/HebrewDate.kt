package dev.kosherswitch

import android.icu.text.DateFormat
import android.icu.util.Calendar
import android.icu.util.ULocale

/** Today's Hebrew date in Hebrew, e.g. "כ״ב באלול תשפ״ו". */
fun hebrewDate(): String {
    val locale = ULocale("he_IL@calendar=hebrew")
    return DateFormat.getDateInstance(DateFormat.LONG, locale).format(Calendar.getInstance(locale).time)
}
