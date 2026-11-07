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

    /** A burst of typing (see [SpeedMeter]): key presses that typed, deletions, and how long it took. */
    fun typingBurst(typed: Int, deleted: Int, millis: Long) {}
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
    /** Average typing speed in words per minute, or null before there's enough typing to tell. */
    val wpm: Int? = null,
    val todayWpm: Int? = null,
    /** The fastest burst of a fair length. */
    val bestWpm: Int? = null,
    /** Share of key presses that weren't deleted again, 0–100. */
    val accuracy: Int? = null,
    /** Minutes spent actually typing (pauses left out). */
    val typingMinutes: Long = 0,
    /** The last 7 days' speed, oldest first; null for a day without enough typing. */
    val speedDays: List<Int?> = List(7) { null },
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

    override fun typingBurst(typed: Int, deleted: Int, millis: Long) {
        val day = today().toEpochDay()
        val net = (typed - deleted).coerceAtLeast(0)
        prefs.edit {
            putLong(K_TYPED, prefs.getLong(K_TYPED, 0) + typed)
            putLong(K_DELETED, prefs.getLong(K_DELETED, 0) + deleted)
            putLong(K_MILLIS, prefs.getLong(K_MILLIS, 0) + millis)
            putLong(SPEED_CHARS + day, prefs.getLong(SPEED_CHARS + day, 0) + net)
            putLong(SPEED_MILLIS + day, prefs.getLong(SPEED_MILLIS + day, 0) + millis)
            if (!prefs.contains(K_SINCE)) putLong(K_SINCE, day)
            if (net >= BEST_MIN_CHARS && millis >= BEST_MIN_MS) {
                val wpm = SpeedMeter.wpm(typed.toLong(), deleted.toLong(), millis).toFloat()
                if (wpm > prefs.getFloat(K_BEST, 0f) && wpm < MAX_PLAUSIBLE_WPM) putFloat(K_BEST, wpm)
            }
            forgetOldDays(day)
        }
    }

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
            wpm = speed(prefs.getLong(K_TYPED, 0), prefs.getLong(K_DELETED, 0), prefs.getLong(K_MILLIS, 0)),
            todayWpm = daySpeed(day),
            bestWpm = prefs.getFloat(K_BEST, 0f).takeIf { it > 0f }?.let { Math.round(it) },
            accuracy = prefs.getLong(K_TYPED, 0).takeIf { it >= MIN_KEYS }?.let { typed ->
                Math.round(100.0 * (typed - prefs.getLong(K_DELETED, 0)).coerceAtLeast(0) / typed).toInt()
            },
            typingMinutes = prefs.getLong(K_MILLIS, 0) / 60_000,
            speedDays = (6 downTo 0).map { daySpeed(day - it) },
        )
    }

    fun reset() = prefs.edit { clear() }

    private fun speed(typed: Long, deleted: Long, millis: Long): Int? =
        if (typed < MIN_KEYS || millis < MIN_MILLIS) null else Math.round(SpeedMeter.wpm(typed, deleted, millis)).toInt()

    /** A day's speed from its net characters (deletions already taken off) and typing time. */
    private fun daySpeed(day: Long): Int? {
        val chars = prefs.getLong(SPEED_CHARS + day, 0)
        val millis = prefs.getLong(SPEED_MILLIS + day, 0)
        return if (chars < MIN_KEYS || millis < MIN_MILLIS) null else Math.round(SpeedMeter.wpm(chars, 0, millis)).toInt()
    }

    /** Forgets per-day numbers older than a month. */
    private fun SharedPreferences.Editor.forgetOldDays(day: Long) {
        prefs.all.keys.forEach { key ->
            val prefix = DAY_PREFIXES.firstOrNull { key.startsWith(it) } ?: return@forEach
            val d = key.removePrefix(prefix).toLongOrNull() ?: return@forEach
            if (d < day - KEEP_DAYS) remove(key)
        }
    }

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
                forgetOldDays(day)
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
        const val K_TYPED = "speed_typed"
        const val K_DELETED = "speed_deleted"
        const val K_MILLIS = "speed_millis"
        const val K_BEST = "speed_best"
        const val SPEED_CHARS = "spc_"
        const val SPEED_MILLIS = "spt_"
        val DAY_PREFIXES = listOf(DAY_PREFIX, SPEED_CHARS, SPEED_MILLIS)
        const val KEEP_DAYS = 30

        /** Enough typing to show a speed (about a sentence or two). */
        const val MIN_KEYS = 40L
        const val MIN_MILLIS = 10_000L

        /** A best burst must be a fair length, and fast bursts beyond this are glitches. */
        const val BEST_MIN_CHARS = 25
        const val BEST_MIN_MS = 6_000L
        const val MAX_PLAUSIBLE_WPM = 250f
    }
}
