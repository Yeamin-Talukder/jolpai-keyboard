package com.yeamin.jolpai.core.tools

import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/**
 * Placeholders filled in when a snippet is inserted:
 *
 * | Variable | Bangla name | Becomes |
 * |---|---|---|
 * | `{date}` | `{তারিখ}` | today's date ("8 November 2026" / "৮ নভেম্বর, ২০২৬") |
 * | `{time}` | `{সময়}` | the time |
 * | `{day}` | `{বার}` | the day of the week |
 * | `{bangla_date}` | `{বাংলা তারিখ}` | the Bangla calendar date ("২৩ কার্তিক ১৪৩৩") |
 * | `{clipboard}` | `{ক্লিপবোর্ড}` | what's on the clipboard |
 * | `{cursor}` | `{কার্সর}` | where the cursor goes afterwards |
 *
 * A snippet written in Bangla gets the date, time and day in Bangla. Names ignore case;
 * anything else in braces is left as it is.
 */
object SnippetVariables {
    data class Expanded(val text: String, /** Where to put the cursor in [text], or null for the end. */ val cursor: Int?)

    private val PATTERN = Regex("""\{([^{}\n]{1,24})\}""")

    fun expand(text: String, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault(), clipboard: () -> String? = { null }): Expanded {
        if ('{' !in text) return Expanded(text, null)
        val bangla = text.any { it in 'ঀ'..'৿' }
        val locale = if (bangla) Locale.forLanguageTag("bn-BD") else Locale.getDefault()
        val date = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val cursorMark = '' // a private-use character, replaced by the cursor position below
        val out = PATTERN.replace(text) { m ->
            val value = when (m.groupValues[1].trim().lowercase()) {
                "date", "তারিখ" -> DateFormat.getDateInstance(DateFormat.LONG, locale).apply { timeZone = java.util.TimeZone.getTimeZone(zone) }.format(Date(nowMillis))
                "time", "সময়", "সময়" -> DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply { timeZone = java.util.TimeZone.getTimeZone(zone) }.format(Date(nowMillis))
                "day", "বার" -> if (bangla) BanglaCalendar.weekdayName(date.dayOfWeek) else date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                "bangla_date", "bangla date", "বাংলা তারিখ", "বঙ্গাব্দ" -> BanglaCalendar.format(date)
                "clipboard", "ক্লিপবোর্ড" -> clipboard().orEmpty()
                "cursor", "কার্সর" -> cursorMark.toString()
                else -> null
            }
            Regex.escapeReplacement(value ?: m.value)
        }
        val at = out.indexOf(cursorMark)
        return if (at < 0) Expanded(out, null) else Expanded(out.replace(cursorMark.toString(), ""), at)
    }

    /** True when [text] uses at least one known variable (so its insertion needs [expand]). */
    fun hasVariables(text: String): Boolean = '{' in text && PATTERN.containsMatchIn(text)
}
