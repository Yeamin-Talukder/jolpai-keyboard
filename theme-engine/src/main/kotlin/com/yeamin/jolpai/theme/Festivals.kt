package com.yeamin.jolpai.theme

import java.time.LocalDate
import java.time.MonthDay
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * Festival days in Bangladesh and the theme for each. With "Festival themes" on, the keyboard
 * uses that theme for the day, then returns to the user's own.
 *
 * National days are fixed (the Bangla calendar puts them on the same Gregorian dates). Eid and
 * Ramadan follow the Hijri calendar; Java's Umm al-Qura calendar can be a day off from the moon
 * sighting in Bangladesh, so Eid gets a few days of leeway.
 */
object Festivals {
    data class Festival(val name: String, val theme: KeyboardTheme)

    private val FIXED: Map<MonthDay, Festival> = mapOf(
        MonthDay.of(4, 14) to Festival("পহেলা বৈশাখ", BuiltInThemes.BOISHAKH),
        MonthDay.of(2, 21) to Festival("একুশে ফেব্রুয়ারি", BuiltInThemes.EKUSHEY),
        MonthDay.of(3, 26) to Festival("স্বাধীনতা দিবস", BuiltInThemes.BIJOY),
        MonthDay.of(12, 16) to Festival("বিজয় দিবস", BuiltInThemes.BIJOY),
    )

    /** The festival on [date], or null. A national day wins over Ramadan. */
    fun on(date: LocalDate): Festival? {
        FIXED[MonthDay.from(date)]?.let { return it }
        val hijri = runCatching { HijrahDate.from(date) }.getOrNull() ?: return null
        val month = hijri.get(ChronoField.MONTH_OF_YEAR)
        val day = hijri.get(ChronoField.DAY_OF_MONTH)
        return when {
            month == 10 && day in 1..4 -> Festival("ঈদুল ফিতর", BuiltInThemes.EID)
            month == 12 && day in 10..13 -> Festival("ঈদুল আযহা", BuiltInThemes.EID)
            month == 9 -> Festival("রমজান", BuiltInThemes.RAMADAN)
            else -> null
        }
    }
}
