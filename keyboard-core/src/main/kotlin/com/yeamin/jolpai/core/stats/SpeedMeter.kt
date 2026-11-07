package com.yeamin.jolpai.core.stats

/**
 * Turns key presses into typing-speed samples.
 *
 * Typing is split into bursts at pauses longer than [pauseMs]: time spent reading, thinking or
 * in another app isn't typing time. Each finished burst long enough to mean something is passed
 * to [report] as key presses that typed something, deletions, and the time it took.
 *
 * Times are from a monotonic clock in milliseconds.
 */
class SpeedMeter(
    private val pauseMs: Long = PAUSE_MS,
    private val report: (typed: Int, deleted: Int, millis: Long) -> Unit,
) {
    private var start = -1L
    private var last = -1L
    private var typed = 0
    private var deleted = 0

    /** [count] characters typed with one key press (a suggestion tap types several). */
    fun typed(now: Long, count: Int = 1) {
        tick(now)
        typed += count
    }

    fun deleted(now: Long) {
        tick(now)
        deleted++
    }

    /** Ends the current burst (the keyboard closed, or the field changed). */
    fun flush() {
        val presses = typed + deleted
        if (start >= 0 && typed >= MIN_TYPED && presses > 1) {
            // n presses span n-1 gaps; add one average gap for the first press.
            val span = last - start
            val millis = span + span / (presses - 1)
            if (millis >= MIN_MS) report(typed, deleted, millis)
        }
        start = -1
        last = -1
        typed = 0
        deleted = 0
    }

    private fun tick(now: Long) {
        if (last >= 0 && (now - last > pauseMs || now < last)) flush()
        if (start < 0) start = now
        last = now
    }

    companion object {
        const val PAUSE_MS = 3_000L
        /** Shorter bursts ("ok", "lol") say little about speed. */
        const val MIN_TYPED = 8
        const val MIN_MS = 1_500L

        /** Words per minute, counting five characters as a word and deletions against you. */
        fun wpm(typed: Long, deleted: Long, millis: Long): Double =
            if (millis <= 0) 0.0 else (typed - deleted).coerceAtLeast(0) / 5.0 / (millis / 60_000.0)
    }
}
