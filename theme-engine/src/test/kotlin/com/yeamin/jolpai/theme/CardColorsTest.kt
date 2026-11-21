package com.yeamin.jolpai.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardColorsTest {
    /** Every built-in theme, Chameleon ones in a few app colours, festival themes, and an awkward custom one. */
    private val themes: List<KeyboardTheme> = BuiltInThemes.all.flatMap { t ->
        if (t.live is LiveStyle.AppColour) listOf(0xFF25D366, 0xFFFFC107, 0xFF1877F2, 0xFFE1306C).map { AppTint.apply(t, it.toInt()) }
        else listOf(LiveThemes.at(t, java.time.LocalTime.NOON), LiveThemes.at(t, java.time.LocalTime.MIDNIGHT))
    } + BuiltInThemes.festivals + listOf(
        BuiltInThemes.LIGHT.copy(text = 0xFFFFFFFF.toInt(), key = 0x10FFFFFF, background = 0xFFF0F0F0.toInt(), hint = 0xFFEEEEEE.toInt()),
    )

    private fun c(a: Int, b: Int) = ThemeColors.contrast(a, b)

    @Test fun `card boxes are opaque and their text is readable on every theme`() {
        themes.forEach { t ->
            val cc = CardColors.of(t)
            assertEquals("${t.name}: surface opaque", 0xFF, cc.surface ushr 24)
            assertTrue("${t.name}: text on card %.2f".format(c(cc.text, cc.surface)), c(cc.text, cc.surface) >= CardColors.MIN_TEXT)
            assertTrue("${t.name}: hint on card", c(cc.hint, cc.surface) >= CardColors.MIN_HINT)
            assertTrue("${t.name}: icon button", c(cc.text, cc.raised) >= CardColors.MIN_ICON)
        }
    }

    @Test fun `issue colours, hint and accent are readable on the card and the keyboard behind it`() {
        // Built-in themes only: the awkward custom theme has a light keyboard under white text,
        // so nothing can be readable on both its card and its background (the card wins).
        themes.dropLast(1).forEach { t ->
            val cc = CardColors.of(t)
            val issues = listOf("spelling" to cc.spelling, "punctuation" to cc.punctuation, "grammar" to cc.grammar, "ai" to cc.ai)
            // On the cards: text sizes.
            issues.forEach { (name, col) -> assertTrue("${t.name}: $name on card %.2f".format(c(col, cc.surface)), c(col, cc.surface) >= CardColors.MIN_TEXT) }
            assertTrue("${t.name}: accent on card", c(cc.accentText, cc.surface) >= CardColors.MIN_ICON)
            // Around the cards (gradient ends, brightest glow): labels and text read, dots and outlines show.
            LiveThemes.brightestBackgrounds(t).forEach { bg ->
                issues.forEach { (name, col) -> assertTrue("${t.name}: $name around %.2f".format(c(col, bg)), c(col, bg) >= CardColors.MIN_ICON) }
                assertTrue("${t.name}: hint %.2f".format(c(cc.hint, bg)), c(cc.hint, bg) >= CardColors.MIN_HINT)
                assertTrue("${t.name}: accent %.2f".format(c(cc.accentText, bg)), c(cc.accentText, bg) >= CardColors.MIN_ICON)
                assertTrue("${t.name}: text %.2f".format(c(cc.text, bg)), c(cc.text, bg) >= CardColors.MIN_TEXT)
            }
        }
    }

    @Test fun `see-through white keys no longer make a white card under white text`() {
        // The reported kind of failure: Glass and Nebula have translucent white keys and white text.
        listOf(BuiltInThemes.GLASS, BuiltInThemes.NEBULA, BuiltInThemes.SUNSET, BuiltInThemes.CHAMELEON).forEach { t ->
            val old = t.key or (0xFF shl 24) // what the cards used to draw
            assertTrue("${t.name} was unreadable before", c(t.text, old) < 2.0)
            val cc = CardColors.of(t)
            assertTrue("${t.name} now", c(cc.text, cc.surface) >= CardColors.MIN_TEXT)
        }
    }

    @Test fun `colours that are already readable are kept as they are`() {
        val cc = CardColors.of(BuiltInThemes.DARK)
        assertEquals(BuiltInThemes.DARK.text, cc.text)
        assertEquals(BuiltInThemes.DARK.accent, cc.accentText)
    }
}
