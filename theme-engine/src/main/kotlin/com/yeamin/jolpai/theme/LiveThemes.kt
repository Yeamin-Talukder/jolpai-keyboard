package com.yeamin.jolpai.theme

import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A soft round light over the keyboard background, fading to nothing at its edge: the "ambient"
 * look. [color] is the colour at the centre; its alpha is how strong the light is there.
 * [x] and [y] place the centre as a share of the keyboard's width and height (it may sit a
 * little outside, so only the edge of the light shows); [radius] is a share of the width.
 */
data class Glow(val color: Int, val x: Float, val y: Float, val radius: Float) {
    fun sanitized(): Glow = copy(x = x.coerceIn(MIN_POS, MAX_POS), y = y.coerceIn(MIN_POS, MAX_POS), radius = radius.coerceIn(0.05f, 1.5f))

    companion object {
        /** At most this many glows per theme (each is one extra circle per frame). */
        const val MAX = 3
        const val MIN_POS = -0.5f
        const val MAX_POS = 1.5f
    }
}

/** How a theme changes by itself. */
sealed interface LiveStyle {
    /**
     * Colours follow the clock: the theme blends from one [TimeStop] to the next through the day.
     * A light stop and a dark stop are never blended (half-way colours make labels hard to
     * read); the theme switches between them half-way instead.
     */
    data class TimeOfDay(val stops: List<TimeStop>) : LiveStyle {
        init { require(stops.isNotEmpty()) { "A time-of-day theme needs at least one stop." } }
    }

    /**
     * The theme's [KeyboardTheme.glows] drift slowly round the keyboard, one loop every
     * [periodSeconds]. The keyboard keeps them still when the phone's animations are off or
     * battery saver is on.
     */
    data class Drift(val periodSeconds: Int = 48) : LiveStyle

    /**
     * Chameleon: the colours come from the app being typed in, in this [style] (see [AppTint]).
     * With [driftSeconds] > 0 the app-coloured glows also drift (one loop in that many seconds).
     */
    data class AppColour(val style: AppTintStyle, val driftSeconds: Int = 0) : LiveStyle
}

/** [this] within the limits the keyboard handles sensibly (for saved and imported themes). */
fun LiveStyle.sanitized(): LiveStyle = when (this) {
    is LiveStyle.AppColour -> copy(driftSeconds = if (driftSeconds <= 0) 0 else driftSeconds.coerceIn(10, 600))
    is LiveStyle.Drift -> copy(periodSeconds = periodSeconds.coerceIn(10, 600))
    is LiveStyle.TimeOfDay -> LiveStyle.TimeOfDay(
        stops.take(24).map { TimeStop(it.minute.coerceIn(0, 24 * 60 - 1), it.look.copy(live = null, dynamic = false).sanitized()) },
    )
}

/** The colours ([look]) a time-of-day theme has at [minute] past midnight. */
data class TimeStop(val minute: Int, val look: KeyboardTheme) {
    companion object {
        fun at(hour: Int, minute: Int, look: KeyboardTheme) = TimeStop(hour * 60 + minute, look)
    }
}

/** The maths of dynamic themes (plain Kotlin, unit-tested on the PC). */
object LiveThemes {
    /** Time-of-day themes change in steps of this many minutes, so the keyboard isn't rebuilt more often. */
    const val STEP_MINUTES = 10
    private const val DAY = 24 * 60

    /** How far drifting glows move from their place, as a share of the keyboard's width and height. */
    const val DRIFT_X = 0.22f
    const val DRIFT_Y = 0.14f

    /** How much a drifting glow dims at the quiet point of its loop (it never gets brighter than at rest). */
    const val BREATH = 0.25f

    /**
     * [theme] as it looks at [time]: a time-of-day theme gets the colours for that moment (in
     * [STEP_MINUTES] steps); every other theme comes back unchanged.
     */
    fun at(theme: KeyboardTheme, time: LocalTime): KeyboardTheme {
        val live = theme.live as? LiveStyle.TimeOfDay ?: return theme
        val minute = (time.hour * 60 + time.minute) / STEP_MINUTES * STEP_MINUTES
        return theme.withColoursOf(lookAt(live, minute))
    }

    /**
     * A still copy of [theme] as it looks at [time], for making a custom theme from a dynamic one
     * (custom themes don't change by themselves).
     */
    fun frozen(theme: KeyboardTheme, time: LocalTime): KeyboardTheme = at(theme, time).copy(live = null)

    /** The blended colours of [live] at [minute] past midnight (0 until 1440). */
    fun lookAt(live: LiveStyle.TimeOfDay, minute: Int): KeyboardTheme {
        val stops = live.stops.sortedBy { it.minute }
        val m = ((minute % DAY) + DAY) % DAY
        val prev = stops.lastOrNull { it.minute <= m } ?: stops.last()
        val next = stops.firstOrNull { it.minute > m } ?: stops.first()
        if (prev === next) return prev.look
        val span = ((next.minute - prev.minute) % DAY + DAY) % DAY
        val into = ((m - prev.minute) % DAY + DAY) % DAY
        val t = if (span == 0) 0f else into.toFloat() / span
        return blend(prev.look, next.look, smooth(t))
    }

    /**
     * Colours between [a] and [b] (t = 0 is a, 1 is b). Themes of different darkness switch at
     * the half-way point instead of blending.
     */
    fun blend(a: KeyboardTheme, b: KeyboardTheme, t: Float): KeyboardTheme {
        if (t <= 0f) return a
        if (t >= 1f) return b
        if (a.dark != b.dark) return if (t < 0.5f) a else b
        fun l(x: Int, y: Int) = ThemeColors.lerp(x, y, t)
        fun lOpt(x: Int?, y: Int?, fallbackX: Int, fallbackY: Int): Int? =
            if (x == null && y == null) null else l(x ?: fallbackX, y ?: fallbackY)
        return a.copy(
            background = l(a.background, b.background),
            backgroundEnd = lOpt(a.backgroundEnd, b.backgroundEnd, a.background, b.background),
            key = l(a.key, b.key),
            keyPressed = l(a.keyPressed, b.keyPressed),
            functionKey = l(a.functionKey, b.functionKey),
            text = l(a.text, b.text),
            hint = l(a.hint, b.hint),
            accent = l(a.accent, b.accent),
            onAccent = l(a.onAccent, b.onAccent),
            // A missing outline blends from the other's colour with no opacity.
            border = lOpt(a.border, b.border, (b.border ?: 0) and RGB, (a.border ?: 0) and RGB),
            glows = blendGlows(a.glows, b.glows, t),
        )
    }

    private fun blendGlows(a: List<Glow>, b: List<Glow>, t: Float): List<Glow> {
        if (a.size != b.size) return if (t < 0.5f) a else b
        return a.zip(b) { x, y ->
            Glow(
                color = ThemeColors.lerp(x.color, y.color, t),
                x = x.x + (y.x - x.x) * t,
                y = x.y + (y.y - x.y) * t,
                radius = x.radius + (y.radius - x.radius) * t,
            )
        }
    }

    /**
     * Where drifting [glows] are at [phase] (0 until 1, one full loop). Each glow moves on its
     * own figure-of-eight around its resting place, a little out of step with the others, and
     * dims slightly at the far point. Phase 0 and phase 1 are the same frame, so the loop is
     * seamless. A glow is never brighter than at rest, so a theme that is readable with its
     * resting glows stays readable while they drift.
     */
    fun drift(glows: List<Glow>, phase: Float): List<Glow> {
        val loop = (phase % 1f + 1f) % 1f // phase 1 is exactly phase 0
        return glows.mapIndexed { i, g -> drifted(g, 2 * PI * ((loop + i * 0.37) % 1.0)) }
    }

    private fun drifted(g: Glow, p: Double): Glow {
        val strength = 1f - BREATH * (1f - cos(p).toFloat()) / 2f
        val alpha = (((g.color ushr 24) * strength).toInt()).coerceIn(0, 255)
        return g.copy(
            color = (alpha shl 24) or (g.color and RGB),
            x = (g.x + DRIFT_X * sin(p).toFloat()).coerceIn(Glow.MIN_POS, Glow.MAX_POS),
            y = (g.y + DRIFT_Y * sin(2 * p).toFloat()).coerceIn(Glow.MIN_POS, Glow.MAX_POS),
        )
    }

    /** The background where its brightest glow reaches it: the worst case for reading a see-through key. */
    fun brightestBackgrounds(t: KeyboardTheme): List<Int> {
        val bases = listOfNotNull(t.background, t.backgroundEnd).map { it or OPAQUE }
        return bases + bases.flatMap { bg -> t.glows.map { ThemeColors.over(it.color, bg) } }
    }

    /** Ease in and out, so colours change slowly near each stop. */
    private fun smooth(t: Float): Float = t * t * (3 - 2 * t)

    private const val RGB = 0x00FFFFFF
    private const val OPAQUE = 0xFF shl 24
}

private fun KeyboardTheme.withColoursOf(look: KeyboardTheme): KeyboardTheme = copy(
    dark = look.dark,
    background = look.background,
    backgroundEnd = look.backgroundEnd,
    key = look.key,
    keyPressed = look.keyPressed,
    functionKey = look.functionKey,
    text = look.text,
    hint = look.hint,
    accent = look.accent,
    onAccent = look.onAccent,
    border = look.border,
    glows = look.glows,
)
