package com.yeamin.lipi.core.stats

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.LocalDate

/** What the keyboard reports as you type (counts only; never the words). */
interface TypingEvents {
    fun word(bangla: Boolean)
    fun autocorrected()
    fun suggestionPicked(lettersSaved: Int)
    fun emoji()
}

/** A summary for the Typing stats screen. [days] holds the last 7 days' word counts, oldest first. */
data class TypingStatsSnapshot(
    val words: Long,
    val banglaWords: Long,
    val englishWords: Long,
    val today: Int,
    val days: List<Int>,
    val autocorrections: Long,
    val suggestionsPicked: Long,
    val lettersSaved: Long,
    val emoji: Long,
    val sinceEpochDay: Long,
)

/**
 * Typing statistics, kept on the phone in a small preferences file. Only numbers are stored —
 * how many words, how many corrections — never what was typed, and never from password or
 * private fields (the keyboard doesn't report those). Per-day counts are kept for 30 days.
 */
class TypingStats(context: Context, private val today: () -> LocalDate = { LocalDate.now() }) : TypingEvents {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun word(bangla: Boolean) = bump(if (bangla) K_BANGLA else K_ENGLISH, 1, perDay = true)
    override fun autocorrected() = bump(K_AUTOCORRECT, 1)
    override fun suggestionPicked(lettersSaved: Int) {
        bump(K_SUGGESTIONS, 1)
        if (lettersSaved > 0) bump(K_SAVED, lettersSaved.toLong())
    }
    override fun emoji() = bump(K_EMOJI, 1)

    fun snapshot(): TypingStatsSnapshot {
        val day = today().toEpochDay()
        val bangla = prefs.getLong(K_BANGLA, 0)
        val english = prefs.getLong(K_ENGLISH, 0)
        return TypingStatsSnapshot(
            words = bangla + english,
            banglaWords = bangla,
            englishWords = english,
            today = prefs.getInt(dayKey(day), 0),
            days = (6 downTo 0).map { prefs.getInt(dayKey(day - it), 0) },
            autocorrections = prefs.getLong(K_AUTOCORRECT, 0),
            suggestionsPicked = prefs.getLong(K_SUGGESTIONS, 0),
            lettersSaved = prefs.getLong(K_SAVED, 0),
            emoji = prefs.getLong(K_EMOJI, 0),
            sinceEpochDay = prefs.getLong(K_SINCE, day),
        )
    }

    fun reset() = prefs.edit { clear() }

    /** Calls [onChange] whenever the numbers change (the stats screen updates live). */
    fun listen(onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        prefs.registerOnSharedPreferenceChangeListener(l)
        return l
    }

    fun stopListening(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(l)

    private fun bump(key: String, by: Long, perDay: Boolean = false) {
        val day = today().toEpochDay()
        prefs.edit {
            putLong(key, prefs.getLong(key, 0) + by)
            if (!prefs.contains(K_SINCE)) putLong(K_SINCE, day)
            if (perDay) {
                putInt(dayKey(day), prefs.getInt(dayKey(day), 0) + by.toInt())
                // Forget days older than a month.
                prefs.all.keys.filter { it.startsWith(DAY_PREFIX) && (it.removePrefix(DAY_PREFIX).toLongOrNull() ?: day) < day - KEEP_DAYS }
                    .forEach { remove(it) }
            }
        }
    }

    private fun dayKey(epochDay: Long) = DAY_PREFIX + epochDay

    private companion object {
        const val FILE = "typing_stats"
        const val K_BANGLA = "words_bn"
        const val K_ENGLISH = "words_en"
        const val K_AUTOCORRECT = "autocorrect"
        const val K_SUGGESTIONS = "suggestions"
        const val K_SAVED = "letters_saved"
        const val K_EMOJI = "emoji"
        const val K_SINCE = "since"
        const val DAY_PREFIX = "day_"
        const val KEEP_DAYS = 30
    }
}
