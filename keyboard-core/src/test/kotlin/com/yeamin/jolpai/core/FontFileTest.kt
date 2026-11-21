package com.yeamin.jolpai.core

import com.yeamin.jolpai.core.fonts.FontFile
import com.yeamin.jolpai.core.fonts.KeyFontCheck
import com.yeamin.jolpai.core.fonts.checkKeyFont
import com.yeamin.jolpai.core.fonts.fontDisplayName
import com.yeamin.jolpai.core.fonts.message
import com.yeamin.jolpai.core.settings.KeyFont
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.core.settings.banglaLettersFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The font reader behind custom key fonts and the dotted-circle fix, on made-up and bundled fonts. */
class FontFileTest {

    // ---- made-up fonts ----

    /** A font with only a cmap table: format 4 segments (start, end, delta), or format 12 groups (start, end, firstGlyph). */
    private fun font(format4: List<Triple<Int, Int, Int>>? = null, format12: List<Triple<Int, Int, Int>>? = null): ByteArray {
        val sub = ByteArrayOutputStream()
        DataOutputStream(sub).apply {
            if (format4 != null) {
                val segs = format4 + Triple(0xFFFF, 0xFFFF, 1)
                writeShort(4); writeShort(16 + 8 * segs.size); writeShort(0)
                writeShort(2 * segs.size); writeShort(0); writeShort(0); writeShort(0)
                segs.forEach { writeShort(it.second) }
                writeShort(0)
                segs.forEach { writeShort(it.first) }
                segs.forEach { writeShort(it.third) }
                segs.forEach { _ -> writeShort(0) }
            } else {
                val groups = format12!!
                writeShort(12); writeShort(0); writeInt(16 + 12 * groups.size); writeInt(0); writeInt(groups.size)
                groups.forEach { writeInt(it.first); writeInt(it.second); writeInt(it.third) }
            }
        }
        val cmap = ByteArrayOutputStream()
        DataOutputStream(cmap).apply {
            writeShort(0); writeShort(1)
            writeShort(3); writeShort(if (format4 != null) 1 else 10); writeInt(12)
            write(sub.toByteArray())
        }
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeInt(0x00010000); writeShort(1); writeShort(16); writeShort(0); writeShort(0)
            writeInt(0x636D6170); writeInt(0); writeInt(28); writeInt(cmap.size())
            write(cmap.toByteArray())
        }
        return out.toByteArray()
    }

    private val latin = listOf(Triple('A'.code, 'Z'.code, 1 - 'A'.code), Triple('a'.code, 'z'.code, 27 - 'a'.code))

    @Test
    fun format4LooksUpGlyphs() {
        val f = FontFile.parse(font(format4 = latin + Triple(0x25CC, 0x25CC, 60 - 0x25CC)))!!
        assertEquals(1, f.glyphId('A'.code))
        assertEquals(26, f.glyphId('Z'.code))
        assertEquals(27, f.glyphId('a'.code))
        assertEquals(60, f.glyphId(0x25CC))
        assertEquals(0, f.glyphId('0'.code))
        assertEquals(0, f.glyphId(0x00A0))
        assertEquals(0, f.glyphId(0x1F600))
        assertTrue(f.coversLatin)
        assertFalse(f.coversBangla)
    }

    @Test
    fun format12LooksUpGlyphsBeyondTheBmp() {
        val f = FontFile.parse(font(format12 = listOf(Triple(0x0980, 0x09FF, 100), Triple(0x1F600, 0x1F64F, 500))))!!
        assertEquals(100 + 0x09BF - 0x0980, f.glyphId(0x09BF))
        assertEquals(500, f.glyphId(0x1F600))
        assertEquals(0, f.glyphId('A'.code))
        assertTrue(f.coversBangla)
        assertFalse(f.coversLatin)
    }

    @Test
    fun readsALittleEndianOrOffsetBufferTheSame() {
        val bytes = font(format4 = latin)
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(bytes)
        buffer.position(5) // a caller's position must not matter
        assertEquals(1, FontFile.parse(buffer)!!.glyphId('A'.code))
    }

    @Test
    fun junkIsNotAFont() {
        assertNull(FontFile.parse(ByteArray(0)))
        assertNull(FontFile.parse("hello, this is not a font file at all".toByteArray()))
        assertNull(FontFile.parse(font(format4 = latin).copyOf(20))) // cut off inside the table list
        val woff = font(format4 = latin).also { ByteBuffer.wrap(it).putInt(0, 0x774F4646) } // 'wOFF'
        assertNull(FontFile.parse(woff))
        // A table pointing past the end of the file.
        val broken = font(format4 = latin).also { ByteBuffer.wrap(it).putInt(12 + 12, 99999) }
        assertNull(FontFile.parse(broken))
    }

    @Test
    fun aFontWithoutACmapHasNoGlyphs() {
        val bytes = font(format4 = latin).also { ByteBuffer.wrap(it).putInt(12, 0x676C7966) } // rename 'cmap' to 'glyf'
        val f = FontFile.parse(bytes)!!
        assertEquals(0, f.glyphId('A'.code))
    }

    @Test
    fun checkKeyFontSaysWhatIsMissing() {
        assertEquals(KeyFontCheck.Ok(latin = true, bangla = false), checkKeyFont(font(format4 = latin)))
        assertEquals(KeyFontCheck.Ok(latin = false, bangla = true), checkKeyFont(font(format12 = listOf(Triple(0x0980, 0x09FF, 1)))))
        assertEquals(KeyFontCheck.NoLetters, checkKeyFont(font(format4 = listOf(Triple(0x2600, 0x26FF, 1)))))
        assertEquals(KeyFontCheck.NotAFont, checkKeyFont("PK\u0003\u0004 a zip file".toByteArray()))
        assertEquals(KeyFontCheck.TooBig, checkKeyFont(ByteArray(FontFile.MAX_BYTES + 1)))
        assertNotNull(KeyFontCheck.Ok(latin = true, bangla = false).message())
        assertNull(KeyFontCheck.Ok(latin = true, bangla = true).message())
        assertNotNull(KeyFontCheck.NotAFont.message())
    }

    @Test
    fun displayNamesLoseTheFolderAndExtension() {
        assertEquals("Kalpurush", fontDisplayName("Kalpurush.ttf"))
        assertEquals("SolaimanLipi", fontDisplayName("primary:Download/fonts/SolaimanLipi.TTF"))
        assertEquals("My Font", fontDisplayName("/storage/emulated/0/My Font.otf"))
        assertEquals("notes.txt", fontDisplayName("notes.txt"))
        assertEquals("Font", fontDisplayName(".ttf"))
        assertEquals(40, fontDisplayName("x".repeat(80) + ".ttf").length)
    }

    // ---- the bundled key fonts ----

    private fun bundled(name: String): FontFile {
        val file = listOf(File("../keyboard-ui/src/main/res/font/$name"), File("keyboard-ui/src/main/res/font/$name")).first { it.isFile }
        return FontFile.parse(file.readBytes())!!
    }

    /**
     * The cause of the dotted circles on sign keys: Hind Siliguri and Galada have no no-break space
     * (the invisible base put before a lone sign), but do have their own dotted circle.
     */
    @Test
    fun hindSiliguriAndGaladaHaveNoNoBreakSpace() {
        for (name in listOf("hind_siliguri_regular.ttf", "hind_siliguri_medium.ttf", "galada_regular.ttf")) {
            val f = bundled(name)
            assertFalse(name, f.has(0x00A0))
            assertNotEquals(name, 0, f.glyphId(0x25CC))
            assertTrue(name, f.has(0x09BF))
        }
        for (name in listOf("atma_regular.ttf", "tiro_bangla_regular.ttf", "baloo_da_2.ttf", "mina_regular.ttf")) {
            assertTrue(name, bundled(name).has(0x00A0))
        }
    }

    /** Every bundled font's file, as KeyFonts in keyboard-ui loads it. */
    private val files = mapOf(
        KeyFont.HIND_SILIGURI to "hind_siliguri_regular.ttf",
        KeyFont.TIRO_BANGLA to "tiro_bangla_regular.ttf",
        KeyFont.KALPURUSH to "kalpurush.ttf",
        KeyFont.BALOO_DA to "baloo_da_2.ttf",
        KeyFont.MINA to "mina_regular.ttf",
        KeyFont.KALAA to "kalaa.ttf",
        KeyFont.NOTO_SERIF_BENGALI to "noto_serif_bengali_regular.ttf",
        KeyFont.GALADA to "galada_regular.ttf",
        KeyFont.ATMA to "atma_regular.ttf",
        KeyFont.ALKATRA to "alkatra.ttf",
        KeyFont.TIRO_BANGLA_ITALIC to "tiro_bangla_italic.ttf",
        KeyFont.BALOO_DA_HEAVY to "baloo_da_2.ttf",
        KeyFont.POPPINS to "poppins_regular.ttf",
        KeyFont.QUICKSAND to "quicksand.ttf",
        KeyFont.SPACE_MONO to "space_mono_regular.ttf",
        KeyFont.PACIFICO to "pacifico_regular.ttf",
        KeyFont.DANCING_SCRIPT to "dancing_script.ttf",
        KeyFont.LOBSTER to "lobster_regular.ttf",
        KeyFont.SATISFY to "satisfy_regular.ttf",
        KeyFont.COURGETTE to "courgette_regular.ttf",
        KeyFont.KAUSHAN_SCRIPT to "kaushan_script_regular.ttf",
        KeyFont.PERMANENT_MARKER to "permanent_marker_regular.ttf",
        KeyFont.INDIE_FLOWER to "indie_flower_regular.ttf",
        KeyFont.SHADOWS_INTO_LIGHT to "shadows_into_light.ttf",
        KeyFont.FREDOKA to "fredoka.ttf",
        KeyFont.BANGERS to "bangers_regular.ttf",
        KeyFont.ABRIL_FATFACE to "abril_fatface_regular.ttf",
        KeyFont.RIGHTEOUS to "righteous_regular.ttf",
        KeyFont.ORBITRON to "orbitron.ttf",
        KeyFont.CREEPSTER to "creepster_regular.ttf",
        KeyFont.PRESS_START to "press_start_2p_regular.ttf",
    )

    private fun fontBytes(name: String): ByteArray =
        listOf(File("../keyboard-ui/src/main/res/font/$name"), File("keyboard-ui/src/main/res/font/$name")).first { it.isFile }.readBytes()

    @Test
    fun bundledFontsHaveTheLettersTheirEntrySays() {
        // Every font except the phone's and the picked one is listed, in the order of its group.
        assertEquals(
            KeyFont.entries.filter { it != KeyFont.SYSTEM && it != KeyFont.CUSTOM }.sortedBy { it.group.ordinal },
            files.keys.sortedBy { it.group.ordinal },
        )
        for ((font, name) in files) {
            val f = bundled(name)
            assertEquals("$name English", font.english, f.coversLatin)
            assertEquals("$name Bangla", font.bangla, f.coversBangla)
            assertEquals(name, KeyFontCheck.Ok(latin = font.english, bangla = font.bangla), checkKeyFont(fontBytes(name)))
        }
    }

    /**
     * Fonts without a no-break space put a dotted circle on lone vowel signs unless the circle
     * glyph is dropped (DisplayLabel): Hind Siliguri, Galada, Kalpurush and Kalaa among the
     * bundled Bangla ones, so the fix is needed for them; every Bangla font has its own ◌.
     */
    @Test
    fun banglaFontsWithoutANoBreakSpaceAreTheExpectedOnes() {
        val missing = files.filter { (font, name) -> font.bangla && !bundled(name).has(0x00A0) }.keys
        assertEquals(setOf(KeyFont.HIND_SILIGURI, KeyFont.GALADA, KeyFont.KALPURUSH, KeyFont.KALAA), missing)
        files.filter { it.key.bangla }.forEach { (_, name) -> assertNotEquals(name, 0, bundled(name).glyphId(0x25CC)) }
    }

    @Test
    fun keyFontStorageKeysAreUniqueAndUnknownOnesFallBack() {
        assertEquals(KeyFont.entries.size, KeyFont.entries.map { it.storageKey }.toSet().size)
        KeyFont.entries.forEach { assertEquals(it, KeyFont.fromStorageKey(it.storageKey)) }
        assertEquals(KeyFont.SYSTEM, KeyFont.fromStorageKey("comic-sans"))
        assertEquals(KeyFont.SYSTEM, KeyFont.fromStorageKey(null))
        KeyFont.Group.entries.forEach { g -> assertFalse(KeyFont.SYSTEM in KeyFont.bundled(g) || KeyFont.CUSTOM in KeyFont.bundled(g)) }
    }

    @Test
    fun banglaLettersFollowTheMainFontUnlessMixed() {
        // One font: Bangla letters use it when it has them, else the phone's font.
        assertEquals(KeyFont.GALADA, banglaLettersFont(KeyFont.GALADA, null))
        assertEquals(KeyFont.SYSTEM, banglaLettersFont(KeyFont.LOBSTER, null))
        assertEquals(KeyFont.CUSTOM, banglaLettersFont(KeyFont.CUSTOM, null))
        // Mixed: the Bangla choice wins, whatever the main font.
        assertEquals(KeyFont.ALKATRA, banglaLettersFont(KeyFont.LOBSTER, KeyFont.ALKATRA))
        assertEquals(KeyFont.KALPURUSH, banglaLettersFont(KeyFont.HIND_SILIGURI, KeyFont.KALPURUSH))
        assertEquals(KeyFont.ALKATRA, KeyboardSettings(keyFont = KeyFont.PACIFICO, banglaKeyFont = KeyFont.ALKATRA).banglaLettersFont)
        // The lists offered when mixing: Bangla letters only from fonts that have them.
        KeyFont.BANGLA_GROUPS.flatMap(KeyFont::bundled).forEach { assertTrue(it.name, it.bangla) }
        assertTrue(KeyFont.bundled(KeyFont.Group.STYLISH).none { it.bangla })
        assertTrue(KeyFont.bundled(KeyFont.Group.BANGLA_STYLISH).size >= 5)
    }
}
