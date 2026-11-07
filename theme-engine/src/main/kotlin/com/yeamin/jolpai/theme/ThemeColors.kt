package com.yeamin.jolpai.theme

/** Colour maths for themes (plain Kotlin, so it is unit-tested on the PC). */
object ThemeColors {

    /** [top] (possibly translucent) drawn over the opaque [bottom]; the result is opaque. */
    fun over(top: Int, bottom: Int): Int {
        val a = (top ushr 24) / 255.0
        fun ch(shift: Int) = (((top shr shift) and 0xFF) * a + ((bottom shr shift) and 0xFF) * (1 - a)).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Mixes opaque [a] towards [b] by [t] (0 = a, 1 = b). */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** WCAG relative luminance of an opaque colour. */
    fun luminance(c: Int): Double {
        fun lin(v: Int): Double = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        return 0.2126 * lin((c shr 16) and 0xFF) + 0.7152 * lin((c shr 8) and 0xFF) + 0.0722 * lin(c and 0xFF)
    }

    /** WCAG contrast ratio of two opaque colours (1 … 21). */
    fun contrast(a: Int, b: Int): Double {
        val l1 = luminance(a)
        val l2 = luminance(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    /**
     * The opaque colour of the key preview bubble and long-press menu. It looks like a slightly
     * raised key, and is always readable under the theme's [KeyboardTheme.text]: see-through
     * keys are first composited over the background (treating a translucent white key as opaque
     * made the bubble white under white text), then darkened or lightened until the text has at
     * least 4.5:1 contrast.
     */
    fun popup(t: KeyboardTheme): Int {
        val keySeen = over(t.key, t.background or OPAQUE)
        var popup = if (t.dark) mix(keySeen, WHITE, 0.10f) else mix(keySeen, WHITE, 0.6f)
        val textLight = luminance(t.text or OPAQUE) > 0.5
        var step = 0
        while (contrast(t.text or OPAQUE, popup) < MIN_POPUP_CONTRAST && step < 20) {
            popup = mix(popup, if (textLight) BLACK else WHITE, 0.15f)
            step++
        }
        return popup
    }

    const val MIN_POPUP_CONTRAST = 4.5
    private const val OPAQUE = 0xFF shl 24
    private const val WHITE = -1
    private const val BLACK = OPAQUE
}
