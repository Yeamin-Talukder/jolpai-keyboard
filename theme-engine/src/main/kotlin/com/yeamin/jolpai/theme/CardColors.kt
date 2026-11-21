package com.yeamin.jolpai.theme

/**
 * Readable colours for the cards that open above the keys (Translate, Check), worked out from
 * any theme. Before, the cards made the theme's key colour opaque for their boxes, which turned
 * see-through white keys (Glass, Nebula, the glow and Chameleon themes…) into solid white under
 * white text, and used fixed red / blue / amber / violet labels that were faint on light cards.
 *
 * Every colour here is checked against what it is drawn on: the card boxes ([surface]) and the
 * keyboard background around them (both ends of a gradient, and where a glow is brightest).
 */
data class CardColors(
    /** Opaque background of boxes and cards (the input box, issue cards). Text is ≥ 4.5:1 on it. */
    val surface: Int,
    /** Round icon buttons (clear, swap…): a step away from [surface]; icons in [text] stay ≥ 3:1. */
    val raised: Int,
    /** Main text, on [surface] and on the keyboard background. */
    val text: Int,
    /** Secondary text (reasons, struck-out mistakes), ≥ 3.5:1 everywhere. */
    val hint: Int,
    /** The accent used as text or an icon (the "to" language, card icons), ≥ 3:1 everywhere. */
    val accentText: Int,
    /** Issue kinds, as text and outlines, ≥ 4.5:1 on cards and background. */
    val spelling: Int,
    val punctuation: Int,
    val grammar: Int,
    /** On-device AI suggestions. */
    val ai: Int,
) {
    companion object {
        const val MIN_TEXT = 4.5
        const val MIN_HINT = 3.5
        const val MIN_ICON = 3.0

        private const val RED = 0xFFE5484D.toInt()
        private const val BLUE = 0xFF3E8EF0.toInt()
        private const val AMBER = 0xFFE09A1F.toInt()
        private const val VIOLET = 0xFF9B7BF7.toInt()

        /** The card colours for [t] (with its real colours already filled in, e.g. Material You). */
        fun of(t: KeyboardTheme): CardColors {
            val surface = ThemeColors.popup(t)
            val backgrounds = LiveThemes.brightestBackgrounds(t)
            // On the card it must be readable; on the keyboard around the card, as far as possible.
            fun pick(color: Int, onCard: Double, around: Double) =
                readable(color or OPAQUE, must = listOf(surface to onCard), nice = backgrounds.map { it to around })
            val text = pick(t.text, MIN_TEXT, MIN_TEXT)
            // Icon buttons: nudged towards the text colour so they show up against the card.
            var raised = ThemeColors.mix(surface, text, 0.10f)
            var step = 0
            while (ThemeColors.contrast(text, raised) < MIN_ICON && step < 10) {
                raised = ThemeColors.mix(raised, if (ThemeColors.luminance(text) > 0.5) BLACK else WHITE, 0.15f)
                step++
            }
            return CardColors(
                surface = surface,
                raised = raised,
                text = text,
                hint = pick(t.hint, MIN_HINT, MIN_HINT),
                accentText = pick(t.accent, MIN_ICON, MIN_ICON),
                // Kind labels are text on the cards; around them they are only dots and outlines.
                spelling = pick(RED, MIN_TEXT, MIN_ICON),
                punctuation = pick(BLUE, MIN_TEXT, MIN_ICON),
                grammar = pick(AMBER, MIN_TEXT, MIN_ICON),
                ai = pick(VIOLET, MIN_TEXT, MIN_ICON),
            )
        }

        /**
         * [color], changed as little as possible (moved towards white or black) so that it meets
         * every contrast in [must] and, if that is possible too, every one in [nice] (pairs of
         * background and minimum ratio). When [must] can't be met, the most readable it gets.
         */
        fun readable(color: Int, must: List<Pair<Int, Double>>, nice: List<Pair<Int, Double>> = emptyList()): Int {
            fun meets(c: Int, req: List<Pair<Int, Double>>) = req.all { (bg, min) -> ThemeColors.contrast(c, bg) >= min }
            fun score(c: Int, req: List<Pair<Int, Double>>) = req.minOfOrNull { (bg, min) -> ThemeColors.contrast(c, bg) / min } ?: 1.0
            // Candidates from least to most changed: the colour itself, then steps towards white and black.
            val candidates = ArrayList<Int>(1 + 2 * STEPS).apply { add(color) }
            var light = color
            var dark = color
            repeat(STEPS) {
                light = ThemeColors.mix(light, WHITE, 0.12f)
                dark = ThemeColors.mix(dark, BLACK, 0.12f)
                candidates.add(light)
                candidates.add(dark)
            }
            // Last resort on very colourful cards: plain white or black.
            candidates.add(WHITE)
            candidates.add(BLACK)
            candidates.firstOrNull { meets(it, must) && meets(it, nice) }?.let { return it }
            val fine = candidates.filter { meets(it, must) }
            if (fine.isNotEmpty()) return fine.maxBy { score(it, nice) }
            return candidates.maxBy { score(it, must) }
        }

        private const val STEPS = 24

        private const val OPAQUE = 0xFF shl 24
        private const val WHITE = -1
        private const val BLACK = OPAQUE
    }
}
