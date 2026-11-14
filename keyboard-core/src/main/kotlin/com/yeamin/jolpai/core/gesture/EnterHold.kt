package com.yeamin.jolpai.core.gesture

import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.model.Panel
import java.text.BreakIterator
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The two optional long-press gestures on the Enter key (Typing & gestures settings). A tap on
 * Enter always does what it did before: new line, send, search, go …
 *
 * - [emoji]: hold Enter to open the emoji panel.
 * - [select]: hold Enter, then slide to select text from the cursor: sideways by character
 *   (like the space-bar slide), up and down by line. Letting go keeps the selection.
 *
 * With both on they can't both own the hold, so the rule is: **hold still and let go opens
 * emoji; hold and slide selects**. The emoji panel then opens on release instead of at the hold
 * time, because opening it straight away would leave no time to slide.
 *
 * The keyboard passes null instead of an [EnterHold] in password fields (like the custom gesture
 * shortcuts) and while typing into a panel's search box or the translate box.
 */
data class EnterHold(val emoji: Boolean, val select: Boolean) {
    val enabled: Boolean get() = emoji || select

    companion object {
        fun of(emoji: Boolean, select: Boolean): EnterHold? = EnterHold(emoji, select).takeIf { it.enabled }
    }
}

/**
 * One touch on Enter, after its long-press time has passed. The key view feeds it the finger's
 * positions (in pixels) and emits the actions it returns. Pure Kotlin, so the rules are
 * unit-tested on the PC.
 *
 * [charStep] and [lineStep] are how far the finger moves for one character or one line;
 * [slop] is how far it must move after the hold before selecting starts (so a shaky "hold
 * still" still counts as still).
 */
class EnterHoldGesture(
    private val options: EnterHold,
    private val charStep: Float,
    private val lineStep: Float,
    private val slop: Float,
) {
    enum class Phase {
        /** The hold time hasn't passed: a release is a normal Enter tap. */
        WAITING,

        /** Held: waiting to see whether the finger slides (select) or lifts (emoji). */
        ARMED,
        SELECTING,

        /** Finished: the touch does nothing more (emoji opened, or released). */
        DONE,
    }

    var phase = Phase.WAITING
        private set

    private var startX = 0f
    private var startY = 0f
    private var chars = 0
    private var lines = 0

    /** True once the hold time has passed: the release must not type Enter. */
    val heldPastTap: Boolean get() = phase != Phase.WAITING

    /** The hold time passed with the finger at ([x], [y]). */
    fun onHold(x: Float, y: Float): List<KeyAction> {
        if (phase != Phase.WAITING) return emptyList()
        startX = x
        startY = y
        return when {
            options.select -> {
                phase = Phase.ARMED
                emptyList()
            }
            options.emoji -> {
                phase = Phase.DONE
                listOf(KeyAction.ShowPanel(Panel.EMOJI))
            }
            else -> emptyList()
        }
    }

    /** The finger moved to ([x], [y]). Before the hold time this does nothing (Enter still types on release). */
    fun onMove(x: Float, y: Float): List<KeyAction> {
        if (phase == Phase.ARMED) {
            if (hypot(x - startX, y - startY) < slop) return emptyList()
            phase = Phase.SELECTING
            // Steps count from where selecting began, so the first one isn't a jump.
            startX = x
            startY = y
        }
        if (phase != Phase.SELECTING) return emptyList()
        val c = ((x - startX) / charStep).toInt()
        val l = ((y - startY) / lineStep).toInt()
        if (c == chars && l == lines) return emptyList()
        chars = c
        lines = l
        return listOf(KeyAction.SelectSlide(chars = c, lines = l))
    }

    /** The finger lifted. Returns what to emit; a normal tap ([heldPastTap] false) is left to the view. */
    fun onRelease(): List<KeyAction> {
        val result = when (phase) {
            Phase.ARMED -> if (options.emoji) listOf(KeyAction.ShowPanel(Panel.EMOJI)) else emptyList()
            Phase.SELECTING -> listOf(KeyAction.EndSelectSlide)
            else -> emptyList()
        }
        phase = Phase.DONE
        return result
    }

    /** The touch was cancelled (keyboard hidden): end a selection in progress, open nothing. */
    fun onCancel(): List<KeyAction> {
        val result = if (phase == Phase.SELECTING) listOf(KeyAction.EndSelectSlide) else emptyList()
        phase = Phase.DONE
        return result
    }
}

/**
 * Where the selection's moving end lands when the Enter slide has gone [lines] lines and [chars]
 * characters from where it started. [text] is the known text around the cursor; positions are
 * indexes into it. Lines are the text's own lines (split at new-line characters), keeping the
 * column where it fits; a line up from the first line goes to its start, a line down from the
 * last goes to its end. Characters are whole graphemes, so an emoji or a Bangla letter with its
 * kar moves as one.
 */
object SlideSelection {
    fun focus(text: String, start: Int, chars: Int, lines: Int): Int {
        var at = start.coerceIn(0, text.length)
        at = moveLines(text, at, lines)
        return moveChars(text, at, chars)
    }

    fun moveChars(text: String, from: Int, chars: Int): Int {
        if (chars == 0 || text.isEmpty()) return from
        val graphemes = BreakIterator.getCharacterInstance()
        graphemes.setText(text)
        var at = from.coerceIn(0, text.length)
        // Start from a grapheme boundary at or before [from].
        if (!graphemes.isBoundary(at)) at = graphemes.preceding(at).let { b -> if (b == BreakIterator.DONE) 0 else b }
        repeat(abs(chars)) {
            val next = if (chars > 0) graphemes.following(at) else graphemes.preceding(at)
            if (next == BreakIterator.DONE) return at
            at = next
        }
        return at
    }

    fun moveLines(text: String, from: Int, lines: Int): Int {
        var at = from.coerceIn(0, text.length)
        if (lines == 0) return at
        val column = at - lineStart(text, at)
        repeat(abs(lines)) {
            if (lines < 0) {
                val start = lineStart(text, at)
                if (start == 0) return 0
                val prevStart = lineStart(text, start - 1)
                at = minOf(prevStart + column, start - 1)
            } else {
                val end = lineEnd(text, at)
                if (end == text.length) return text.length
                val nextStart = end + 1
                at = minOf(nextStart + column, lineEnd(text, nextStart))
            }
        }
        return at
    }

    private fun lineStart(text: String, at: Int): Int = text.lastIndexOf('\n', at - 1).let { if (it < 0) 0 else it + 1 }

    private fun lineEnd(text: String, at: Int): Int = text.indexOf('\n', at).let { if (it < 0) text.length else it }
}
