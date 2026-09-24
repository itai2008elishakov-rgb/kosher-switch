package dev.kosherswitch

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.YomiCalculator
import java.time.LocalDate
import java.util.TimeZone

/** Hebrew-language wrappers around KosherJava's calendar. */
object JewishDates {
    val formatter = HebrewDateFormatter().apply { setHebrewFormat(true) }

    /** Holidays and the weekly parsha follow the Israel schedule only inside Israel. */
    val inIsrael: Boolean
        get() = TimeZone.getDefault().id in setOf("Asia/Jerusalem", "Asia/Tel_Aviv")

    fun calendar(date: LocalDate) = JewishCalendar(date).apply { setInIsrael(JewishDates.inIsrael) }

    fun hebrewDate(date: LocalDate): String = formatter.format(calendar(date))

    fun dayNumber(date: LocalDate): String = formatter.formatHebrewNumber(calendar(date).jewishDayOfMonth)

    /** Holiday, Rosh Chodesh, Chanukah etc. for this day, or null. */
    fun occasion(date: LocalDate): String? {
        val jc = calendar(date)
        return formatter.formatYomTov(jc).ifBlank { null }
            ?: formatter.formatRoshChodesh(jc).ifBlank { null }
    }

    /** The coming Shabbat: "פרשת האזינו", or "שבת סוכות" when a holiday replaces the parsha. */
    fun parsha(date: LocalDate): String {
        var d = date
        while (d.dayOfWeek != java.time.DayOfWeek.SATURDAY) d = d.plusDays(1)
        val jc = calendar(d)
        val name = formatter.formatParsha(jc)
        return if (name.isNotBlank()) "פרשת $name" else "שבת ${formatter.formatYomTov(jc)}"
    }

    fun omer(date: LocalDate): String? = formatter.formatOmer(calendar(date)).ifBlank { null }

    /** Which daily prayer fits now: "shacharit" until midday, "mincha" until sunset, then "arvit". */
    fun currentPrayer(ctx: android.content.Context): String {
        val now = java.util.Date()
        val zc = Place.zmanim(ctx, LocalDate.now())
        val midday = zc?.chatzos
        val sunset = zc?.sunset
        val hour = java.time.LocalTime.now().hour
        return when {
            midday != null && sunset != null -> when {
                now.before(midday) -> "shacharit"
                now.before(sunset) -> "mincha"
                else -> "arvit"
            }
            hour < 12 -> "shacharit"
            hour < 18 -> "mincha"
            else -> "arvit"
        }
    }

    fun dafYomi(date: LocalDate): String = formatter.formatDafYomiBavli(YomiCalculator.getDafYomiBavli(calendar(date)))
}
