package com.yeamin.lipi.core.touch

import com.yeamin.lipi.suggest.NextChars
import kotlin.math.abs
import kotlin.math.ln

/** A key's visible rectangle. [char] is the lower-case character a letter key types, else null. */
class TouchTarget(val left: Float, val top: Float, val right: Float, val bottom: Float, val char: Char?) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2
}

/**
 * Predictive touch targets (like iOS): decides which letter key a touch meant by combining
 * where the finger landed with how likely each letter is next ([NextChars]).
 *
 * For each letter key near the touch:
 *   score = log P(touch | key) + log P(key)
 * where P(touch | key) is a Gaussian around the key's centre (shifted by the user's
 * [TouchCalibration] if any) and P(key) is the predicted probability, smoothed so no letter
 * is ever impossible. The best score wins. The visible keys never change.
 *
 * Safeguards, so typing stays predictable:
 * - A touch in the inner [CORE] of a key always types that key (a clear direct hit).
 * - A key can only win touches within [MAX_EXPAND] of a key size beyond its own edge.
 * - Only letter keys compete; a touch on Space, Shift, Backspace etc. is never re-assigned,
 *   and a touch on a letter never becomes one of those.
 * - Each touch maps to exactly one key, so regions never overlap.
 *
 * Allocation-free per touch; pure Kotlin so it is unit-tested on the PC.
 */
object TouchResolver {
    /** Half of the key's width/height around its centre that is always a direct hit. */
    const val CORE = 0.4f

    /** How far beyond its edge a likely key may reach, in key sizes. */
    const val MAX_EXPAND = 0.45f

    /** Spread of touches around the intended key's centre, in key sizes. */
    private const val SIGMA = 0.32f

    /** Added to every probability: an unpredicted letter still wins clear touches. */
    private const val FLOOR = 0.02f

    /**
     * The index of the key meant by a touch at ([x], [y]). [direct] is the key whose area
     * contains the touch (or -1); it is returned unchanged when there is no prediction.
     */
    fun resolve(
        targets: List<TouchTarget>,
        x: Float,
        y: Float,
        direct: Int,
        next: NextChars?,
        calibration: TouchCalibration? = null,
    ): Int {
        if (direct < 0 || next == null || next.isEmpty) return direct
        val d = targets[direct]
        val dc = d.char ?: return direct
        val (dox, doy) = calibration?.offsetX(dc) to calibration?.offsetY(dc)
        val dcx = d.centerX + (dox ?: 0f) * d.width
        val dcy = d.centerY + (doy ?: 0f) * d.height
        if (abs(x - dcx) <= d.width * CORE / 2 && abs(y - dcy) <= d.height * CORE / 2) return direct

        var best = direct
        var bestScore = Float.NEGATIVE_INFINITY
        val reach = 0.5f + MAX_EXPAND
        for (i in targets.indices) {
            val t = targets[i]
            val c = t.char ?: continue
            val cx = t.centerX + (calibration?.offsetX(c) ?: 0f) * t.width
            val cy = t.centerY + (calibration?.offsetY(c) ?: 0f) * t.height
            val dx = (x - cx) / t.width
            val dy = (y - cy) / t.height
            if (i != direct && (abs(dx) > reach || abs(dy) > reach)) continue
            val score = -(dx * dx + dy * dy) / (2 * SIGMA * SIGMA) + ln(next.probability(c) + FLOOR)
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        return best
    }
}
