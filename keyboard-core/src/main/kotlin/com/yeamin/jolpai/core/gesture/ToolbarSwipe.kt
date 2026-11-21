package com.yeamin.jolpai.core.gesture

import kotlin.math.abs

/**
 * The top bar's swipe, made easy to use: you see it coming and can change your mind.
 *
 * 1. A touch that hardly moves is a tap for the button under it ([slop]).
 * 2. Once the finger has clearly gone sideways (or down), past [slop] and straight enough
 *    ([dominance]), and that direction has an action, the swipe is *claimed*: the buttons
 *    let go and the keyboard shows the action's pill filling up ([Phase.Tracking.progress]).
 * 3. At [armDistance] the pill is full: [Phase.Armed] (the keyboard ticks).
 * 4. Letting go while armed runs the action ([up] returns its direction). Letting go before
 *    that, or sliding back below [DISARM] of the way, does nothing: an easy way out.
 *
 * Sliding right past where it started switches to the other side's action, if it has one
 * (start left for Undo, change your mind and slide right for Redo). Plain Kotlin, unit-tested.
 */
class ToolbarSwipe(
    private val slop: Float,
    private val armDistance: Float,
    private val dominance: Float,
    /** Whether a swipe in this direction does anything here. */
    private val hasAction: (SwipeDirection) -> Boolean,
) {
    sealed interface Phase {
        /** Not a swipe (yet): a tap, or a move too small or too slanted. */
        data object Idle : Phase

        /** Claimed, heading towards [direction]: [progress] 0…1 of the way to arming. */
        data class Tracking(val direction: SwipeDirection, val progress: Float) : Phase

        /** Far enough: letting go now runs [direction]'s action. */
        data class Armed(val direction: SwipeDirection) : Phase
    }

    var phase: Phase = Phase.Idle
        private set

    /** True once the swipe has taken over the touch (buttons under it must cancel their press). */
    val claimed: Boolean get() = axis != null

    private var downX = 0f
    private var downY = 0f
    private var axis: Axis? = null
    private var armed = false

    private enum class Axis { HORIZONTAL, VERTICAL }

    fun down(x: Float, y: Float) {
        downX = x
        downY = y
        axis = null
        armed = false
        phase = Phase.Idle
    }

    /** The finger moved to ([x], [y]); returns the new phase. */
    fun move(x: Float, y: Float): Phase {
        val dx = x - downX
        val dy = y - downY
        if (axis == null) {
            val ax = abs(dx)
            val ay = abs(dy)
            axis = when {
                ax >= slop && ax >= ay * dominance && hasAction(if (dx < 0) SwipeDirection.LEFT else SwipeDirection.RIGHT) -> Axis.HORIZONTAL
                // The top bar sits at the keyboard's top edge: only downward swipes.
                dy >= slop && ay >= ax * dominance && hasAction(SwipeDirection.DOWN) -> Axis.VERTICAL
                else -> null
            }
            if (axis == null) return phase
        }
        val (direction, distance) = when (axis!!) {
            Axis.HORIZONTAL -> (if (dx < 0) SwipeDirection.LEFT else SwipeDirection.RIGHT) to abs(dx)
            Axis.VERTICAL -> SwipeDirection.DOWN to dy.coerceAtLeast(0f)
        }
        if (!hasAction(direction)) {
            // Slid over to a side with nothing on it: nothing will happen.
            armed = false
            phase = Phase.Tracking(direction, 0f)
            return phase
        }
        val progress = (distance / armDistance).coerceIn(0f, 1f)
        armed = if (armed) progress >= DISARM else progress >= 1f
        phase = if (armed) Phase.Armed(direction) else Phase.Tracking(direction, progress)
        return phase
    }

    /** The finger lifted: the direction whose action to run, or null to do nothing. */
    fun up(): SwipeDirection? {
        val fire = (phase as? Phase.Armed)?.direction
        reset()
        return fire
    }

    /** The touch was taken away (another gesture, the keyboard closing): do nothing. */
    fun cancel() = reset()

    private fun reset() {
        axis = null
        armed = false
        phase = Phase.Idle
    }

    companion object {
        /** Once armed, the pill stays armed until the finger slides back below this share of the way. */
        const val DISARM = 0.8f
    }
}
