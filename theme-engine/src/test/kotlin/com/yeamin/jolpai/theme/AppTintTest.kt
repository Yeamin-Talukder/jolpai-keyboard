package com.yeamin.jolpai.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTintTest {
    private val chameleons = BuiltInThemes.chameleon

    /** Whether some part of [t] (background, keys or outline) clearly carries hue [h]. */
    private fun carriesHue(t: KeyboardTheme, h: Float) = listOfNotNull(t.background, t.backgroundEnd, t.key, t.border).any { c ->
        c ushr 24 == 0xFF && ThemeColors.toHsl(c).let { it[1] >= 0.08f && hueDistance(it[0], h) < 15f }
    }

    private fun hue(c: Int) = ThemeColors.toHsl(c)[0]

    private fun hueDistance(a: Float, b: Float): Float = kotlin.math.abs(((a - b + 540) % 360) - 180)

    @Test fun `hsl round trip`() {
        listOf(0xFF25D366, 0xFF0084FF, 0xFFFF0000, 0xFF808080, 0xFFFFFFFF, 0xFF000000, 0xFF7B2D8E).forEach { v ->
            val c = v.toInt()
            val (h, s, l) = ThemeColors.toHsl(c).toList()
            val back = ThemeColors.fromHsl(h, s, l)
            for (shift in listOf(0, 8, 16)) {
                assertTrue("%08X".format(c), kotlin.math.abs(((back shr shift) and 0xFF) - ((c shr shift) and 0xFF)) <= 1)
            }
        }
        assertEquals(120f, ThemeColors.toHsl(0xFF00FF00.toInt())[0], 0.5f)
    }

    @Test fun `an icon's main colour is found and white, transparent and grey pixels are ignored`() {
        val green = 0xFF25D366.toInt() // WhatsApp-like
        // A green circle on a white background with a transparent corner and a dark outline.
        val icon = IntArray(48 * 48) { i ->
            val x = i % 48 - 24
            val y = i / 48 - 24
            when {
                x * x + y * y < 14 * 14 -> green
                x * x + y * y < 15 * 15 -> 0xFF202020.toInt()
                x > 18 && y > 18 -> 0x00000000
                else -> -1
            }
        }
        val found = AppTint.dominantColour(icon)!!
        assertTrue(hueDistance(hue(found), hue(green)) < 8f)
    }

    @Test fun `the bigger colour wins`() {
        val blue = 0xFF0084FF.toInt()
        val red = 0xFFE53935.toInt()
        val icon = IntArray(100) { if (it < 70) blue else red }
        assertTrue(hueDistance(hue(AppTint.dominantColour(icon)!!), hue(blue)) < 8f)
    }

    @Test fun `grey, black-and-white and empty icons have no colour`() {
        assertNull(AppTint.dominantColour(IntArray(100) { if (it % 2 == 0) -1 else 0xFF111111.toInt() }))
        assertNull(AppTint.dominantColour(IntArray(100) { 0xFF8A8A8A.toInt() }))
        assertNull(AppTint.dominantColour(IntArray(100)))
        assertNull(AppTint.dominantColour(IntArray(0)))
        // Two coloured pixels in a black-and-white icon don't count.
        assertNull(AppTint.dominantColour(IntArray(100) { if (it < 2) 0xFFFF0000.toInt() else -1 }))
    }

    @Test fun `chameleon themes take the app's hue and keep their identity`() {
        val green = 0xFF25D366.toInt()
        chameleons.forEach { base ->
            val t = AppTint.apply(base, green)
            assertEquals(base.id, t.id)
            assertEquals(base.name, t.name)
            assertTrue("applied theme no longer needs the app", t.live == null || t.live is LiveStyle.Drift)
            assertTrue("${base.name} accent follows the app", hueDistance(hue(t.accent), hue(green)) < 12f)
            assertTrue("${base.name} carries the app's colour", carriesHue(t, hue(green)))
            assertEquals("${base.name} keeps its press effect", base.pressEffect, t.pressEffect)
            if (t.pressEffect != null) assertEquals("${base.name}: effect in the app's colour", t.accent, t.effectColor)
        }
        assertEquals(LiveStyle.Drift(50), AppTint.apply(BuiltInThemes.CHAMELEON_DRIFT, green).live)
        // Each style looks different for the same app.
        val looks = chameleons.filter { it.pressEffect == null && (it.live as LiveStyle.AppColour).driftSeconds == 0 }
            .map { AppTint.apply(it, green).let { t -> listOf(t.background, t.key, t.accent, t.border) } }
        assertEquals(looks.size, looks.toSet().size)
        assertTrue(AppTint.apply(BuiltInThemes.CHAMELEON_BLACK, green).background == 0xFF000000.toInt())
        assertTrue(!AppTint.apply(BuiltInThemes.CHAMELEON_PASTEL, green).dark)
        // A grey app gives a grey keyboard.
        val grey = AppTint.apply(BuiltInThemes.CHAMELEON, null)
        assertTrue(ThemeColors.toHsl(grey.accent)[1] < 0.05f)
    }

    @Test fun `chameleon themes are readable for every app colour`() {
        val colours = buildList {
            for (h in 0 until 360 step 10) for (s in listOf(0.3f, 0.7f, 1f)) for (l in listOf(0.3f, 0.5f, 0.7f)) add(ThemeColors.fromHsl(h.toFloat(), s, l))
            add(null)
            add(0xFFFFFFFF.toInt())
            add(0xFF000000.toInt())
        }
        chameleons.forEach { base ->
            colours.forEach { app ->
                val t = AppTint.apply(base, app)
                val label = "${base.name} for ${app?.let { "%08X".format(it) }}"
                LiveThemes.brightestBackgrounds(t).forEach { bg ->
                    val ratio = ThemeColors.contrast(t.text, ThemeColors.over(t.key, bg))
                    assertTrue("$label: label %.2f".format(ratio), ratio >= 4.5)
                }
                val accent = ThemeColors.contrast(t.onAccent, t.accent)
                assertTrue("$label: accent %.2f".format(accent), accent >= 4.5)
                assertTrue("$label: popup", ThemeColors.contrast(t.text, ThemeColors.popup(t)) >= ThemeColors.MIN_POPUP_CONTRAST)
            }
        }
    }

    @Test fun `other themes are left alone`() {
        assertTrue(AppTint.apply(BuiltInThemes.NEBULA, 0xFFFF0000.toInt()) === BuiltInThemes.NEBULA)
    }
}
