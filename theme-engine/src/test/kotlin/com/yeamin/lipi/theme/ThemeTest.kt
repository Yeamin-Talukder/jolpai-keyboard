package com.yeamin.lipi.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test fun `built-in themes have unique ids and readable labels`() {
        val ids = BuiltInThemes.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        BuiltInThemes.all.forEach { t ->
            // Key labels must stay readable on the key as actually seen (see-through keys over
            // either end of a gradient): WCAG contrast of at least 3:1, the large-text level.
            listOfNotNull(t.background, t.backgroundEnd).forEach { bg ->
                val keySeen = over(t.key, bg)
                val ratio = contrast(t.text, keySeen)
                assertTrue("${t.name}: contrast %.2f".format(ratio), ratio >= 3.0)
            }
        }
        assertEquals(BuiltInThemes.DARK, BuiltInThemes.byId("builtin:dark"))
        assertNull(BuiltInThemes.byId("nope"))
    }

    @Test fun `the default System theme is Material You in the phone's light or dark mode`() {
        assertEquals(BuiltInThemes.MATERIAL_YOU_LIGHT, BuiltInThemes.forSystem(dark = false))
        assertEquals(BuiltInThemes.MATERIAL_YOU_DARK, BuiltInThemes.forSystem(dark = true))
        assertTrue(BuiltInThemes.forSystem(false).dynamic && !BuiltInThemes.forSystem(false).dark)
        assertTrue(BuiltInThemes.forSystem(true).dynamic && BuiltInThemes.forSystem(true).dark)
    }

    @Test fun `enter and caps lock icons are readable on the accent colour`() {
        BuiltInThemes.all.forEach { t ->
            val ratio = contrast(t.onAccent, t.accent)
            assertTrue("${t.name}: accent contrast %.2f".format(ratio), ratio >= 3.0)
        }
    }

    @Test fun `key preview and long-press menu are readable`() {
        // Twilight to Glass once drew a near-white bubble under white text.
        BuiltInThemes.all.forEach { t ->
            val ratio = contrast(t.text, ThemeColors.popup(t))
            assertTrue("${t.name}: popup contrast %.2f".format(ratio), ratio >= ThemeColors.MIN_POPUP_CONTRAST)
        }
        // Even a careless custom theme gets a readable bubble.
        val odd = BuiltInThemes.LIGHT.copy(text = 0xFFFFFFFF.toInt(), key = 0x10FFFFFF, background = 0xFFF0F0F0.toInt())
        assertTrue(contrast(odd.text, ThemeColors.popup(odd)) >= ThemeColors.MIN_POPUP_CONTRAST)
    }

    private fun over(top: Int, bottom: Int) = ThemeColors.over(top, bottom)

    private fun contrast(a: Int, b: Int) = ThemeColors.contrast(a, b)

    @Test fun `json round trip`() {
        val t = BuiltInThemes.SUNSET.copy(id = "custom:7", name = "Mine", labelScale = 1.2f, boldLabels = true)
        val back = ThemeCodec.decode(ThemeCodec.encode(t), "custom:9")
        assertEquals(t.copy(id = "custom:9"), back)
    }

    @Test fun `import clamps silly values`() {
        val json = ThemeCodec.encode(BuiltInThemes.LIGHT.copy(keyRadiusDp = 99f, keyOpacity = 0f, labelScale = 5f))
        val t = ThemeCodec.decode(json, "custom:1")
        assertEquals(24f, t.keyRadiusDp)
        assertEquals(0.2f, t.keyOpacity)
        assertEquals(1.4f, t.labelScale)
    }

    @Test fun `rejects things that are not themes`() {
        assertThrows(IllegalArgumentException::class.java) { ThemeCodec.decode("hello", "custom:1") }
        assertThrows(IllegalArgumentException::class.java) { ThemeCodec.decode("{\"format\":\"other\"}", "custom:1") }
    }

    @Test fun `hex colours`() {
        assertEquals(0xFF1A73E8.toInt(), ThemeCodec.parseHex("#1A73E8"))
        assertEquals(0x801A73E8.toInt(), ThemeCodec.parseHex("801a73e8"))
        assertEquals("#FF1A73E8", ThemeCodec.hex(0xFF1A73E8.toInt()))
    }
}
