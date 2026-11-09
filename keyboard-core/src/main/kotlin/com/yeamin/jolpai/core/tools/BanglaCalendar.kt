package com.yeamin.jolpai.core.tools

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The Bangla calendar (বঙ্গাব্দ) as Bangladesh uses it since the 2019 revision:
 * the year starts on 14 April (১ বৈশাখ); বৈশাখ to আশ্বিন have 31 days; কার্তিক to মাঘ and চৈত্র
 * have 30; ফাল্গুন has 29, or 30 in a Gregorian leap year. So national days always fall on the
 * same Bangla date: 21 February is ৮ ফাল্গুন, 26 March ১২ চৈত্র, 16 December ১ পৌষ.
 */
object BanglaCalendar {
    data class BanglaDate(val year: Int, val month: Int, val day: Int) {
        val monthName: String get() = MONTHS[month - 1]
        /** The season (ঋতু): two months each, starting with গ্রীষ্ম. */
        val season: String get() = SEASONS[(month - 1) / 2]
    }

    val MONTHS = listOf("বৈশাখ", "জ্যৈষ্ঠ", "আষাঢ়", "শ্রাবণ", "ভাদ্র", "আশ্বিন", "কার্তিক", "অগ্রহায়ণ", "পৌষ", "মাঘ", "ফাল্গুন", "চৈত্র")
    val SEASONS = listOf("গ্রীষ্ম", "বর্ষা", "শরৎ", "হেমন্ত", "শীত", "বসন্ত")

    /** Bangla names of the days of the week, Monday first like [DayOfWeek]. */
    private val WEEKDAYS = listOf("সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার", "রবিবার")

    fun of(date: LocalDate): BanglaDate {
        val newYear = LocalDate.of(date.year, 4, 14)
        val start = if (date.isBefore(newYear)) newYear.minusYears(1) else newYear
        var offset = ChronoUnit.DAYS.between(start, date).toInt()
        // ফাল্গুন falls in February of the next Gregorian year; it gets the leap day.
        val falgun = if (start.plusYears(1).isLeapYear) 30 else 29
        val lengths = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, falgun, 30)
        var month = 0
        while (offset >= lengths[month]) {
            offset -= lengths[month]
            month++
        }
        return BanglaDate(year = start.year - 593, month = month + 1, day = offset + 1)
    }

    /** "২৩ কার্তিক ১৪৩৩", or with [weekday] "শনিবার, ২৩ কার্তিক ১৪৩৩ বঙ্গাব্দ". */
    fun format(date: LocalDate, weekday: Boolean = false): String {
        val b = of(date)
        val core = "${banglaDigits(b.day)} ${b.monthName} ${banglaDigits(b.year)}"
        return if (weekday) "${weekdayName(date.dayOfWeek)}, $core বঙ্গাব্দ" else core
    }

    fun weekdayName(day: DayOfWeek): String = WEEKDAYS[day.value - 1]

    /** 1433 → "১৪৩৩". */
    fun banglaDigits(n: Int): String = n.toString().map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")
}
