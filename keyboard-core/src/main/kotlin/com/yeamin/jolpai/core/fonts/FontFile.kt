package com.yeamin.jolpai.core.fonts

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads just enough of a TrueType / OpenType font file (.ttf, .otf, .ttc) to answer "which glyph
 * does this character use?" from its `cmap` table. Used to check a font the user picks before
 * the keyboard uses it, and to find a font's dotted-circle glyph (◌, U+25CC) so it can be left
 * out when a vowel sign is drawn on its own.
 *
 * Every read is bounds-checked: a damaged file gives `null` from [parse] or glyph 0, never an
 * exception. Works on a heap or a memory-mapped buffer; the buffer itself is not changed.
 */
class FontFile private constructor(private val buf: ByteBuffer, private val cmap: Cmap?) {

    /** The glyph for [codePoint], or 0 (the "missing" glyph) when the font doesn't have it. */
    fun glyphId(codePoint: Int): Int = runCatching { cmap?.glyphId(buf, codePoint) ?: 0 }.getOrDefault(0)

    fun has(codePoint: Int): Boolean = glyphId(codePoint) != 0

    /** Has the English letters A–Z and a–z. */
    val coversLatin: Boolean get() = ('A'..'Z').all { has(it.code) } && ('a'..'z').all { has(it.code) }

    /** Has the Bangla letters, vowel signs and digits used on the keys (অ–হ, া–ৌ, ্, ০–৯). */
    val coversBangla: Boolean get() = BANGLA_SAMPLE.all { has(it.code) }

    private class Cmap(val format: Int, val offset: Int) {
        fun glyphId(b: ByteBuffer, cp: Int): Int = when (format) {
            4 -> format4(b, cp)
            12 -> format12(b, cp)
            else -> 0
        }

        private fun format4(b: ByteBuffer, cp: Int): Int {
            if (cp > 0xFFFF) return 0
            val t = offset
            val segX2 = u16(b, t + 6)
            val segs = segX2 / 2
            val ends = t + 14
            val starts = ends + segX2 + 2
            val deltas = starts + segX2
            val ranges = deltas + segX2
            // The end codes are sorted: binary search for the first segment ending at or after cp.
            var lo = 0
            var hi = segs - 1
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (u16(b, ends + 2 * mid) < cp) lo = mid + 1 else hi = mid
            }
            if (segs == 0 || u16(b, ends + 2 * lo) < cp) return 0
            val start = u16(b, starts + 2 * lo)
            if (cp < start) return 0
            val delta = u16(b, deltas + 2 * lo)
            val rangePos = ranges + 2 * lo
            val range = u16(b, rangePos)
            if (range == 0) return (cp + delta) and 0xFFFF
            val g = u16(b, rangePos + range + 2 * (cp - start))
            return if (g == 0) 0 else (g + delta) and 0xFFFF
        }

        private fun format12(b: ByteBuffer, cp: Int): Int {
            val groups = u32(b, offset + 12)
            var lo = 0L
            var hi = groups - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val g = offset + 16 + (mid * 12).toInt()
                val start = u32(b, g)
                val end = u32(b, g + 4)
                when {
                    cp < start -> hi = mid - 1
                    cp > end -> lo = mid + 1
                    else -> return (u32(b, g + 8) + (cp - start)).toInt()
                }
            }
            return 0
        }
    }

    companion object {
        /** Bigger than any real key font; a guard against picking a huge file by mistake. */
        const val MAX_BYTES = 32 * 1024 * 1024

        private const val TAG_TTCF = 0x74746366 // 'ttcf'
        private val SFNT_VERSIONS = setOf(0x00010000, 0x4F54544F /* OTTO */, 0x74727565 /* true */)
        private const val TAG_CMAP = 0x636D6170 // 'cmap'

        // অ আ ই ক খ গ ঘ চ জ ট ড ত দ ন প ব ম য র ল শ স হ, া ি ী ু ূ ে ৈ ো ৌ ্, ০ ১ ৯.
        private const val BANGLA_SAMPLE =
            "অআইকখগঘচজটডতদনপবমযরলশসহ" +
                "ািীুূেৈোৌ্০১৯"

        fun parse(bytes: ByteArray, ttcIndex: Int = 0): FontFile? = parse(ByteBuffer.wrap(bytes), ttcIndex)

        /** Reads [buffer] (a whole font file, or one font of a collection by [ttcIndex]); null if it isn't a font. */
        fun parse(buffer: ByteBuffer, ttcIndex: Int = 0): FontFile? = runCatching {
            val b = buffer.duplicate().order(ByteOrder.BIG_ENDIAN)
            b.position(0)
            var dir = 0
            if (b.limit() < 12) return null
            if (b.getInt(0) == TAG_TTCF) {
                val count = u32(b, 8)
                if (ttcIndex < 0 || ttcIndex >= count) return null
                dir = u32(b, 12 + 4 * ttcIndex).toInt()
            }
            if (b.getInt(dir) !in SFNT_VERSIONS) return null
            val tables = u16(b, dir + 4)
            if (tables == 0 || tables > 512 || dir + 12 + 16 * tables > b.limit()) return null
            var cmapAt = -1
            for (i in 0 until tables) {
                val rec = dir + 12 + 16 * i
                val off = u32(b, rec + 8)
                val len = u32(b, rec + 12)
                if (off + len > b.limit()) return null
                if (b.getInt(rec) == TAG_CMAP) cmapAt = off.toInt()
            }
            FontFile(b, if (cmapAt < 0) null else bestCmap(b, cmapAt))
        }.getOrNull()

        /** The Unicode subtable to use: full-range format 12 first, else the BMP format 4 one. */
        private fun bestCmap(b: ByteBuffer, at: Int): Cmap? {
            val n = u16(b, at + 2)
            var best: Cmap? = null
            var bestRank = Int.MAX_VALUE
            for (i in 0 until n) {
                val rec = at + 4 + 8 * i
                val platform = u16(b, rec)
                val encoding = u16(b, rec + 2)
                val sub = at + u32(b, rec + 4).toInt()
                if (sub + 4 > b.limit()) continue
                val format = u16(b, sub)
                if (format != 4 && format != 12) continue
                // The order HarfBuzz (Android's shaper) uses: Windows full Unicode, Unicode full
                // repertoire, Windows BMP, then the other Unicode tables. Some fonts (Kalaa) have a
                // smaller Unicode BMP table next to a complete Windows one.
                val rank = when {
                    platform == 3 && encoding == 10 -> 0
                    platform == 0 && encoding == 6 -> 1
                    platform == 0 && encoding == 4 -> 2
                    platform == 3 && encoding == 1 -> 3
                    platform == 0 && encoding == 3 -> 4
                    platform == 0 -> 5
                    else -> continue
                }
                if (rank < bestRank) {
                    bestRank = rank
                    best = Cmap(format, sub)
                }
            }
            return best
        }

        private fun u16(b: ByteBuffer, at: Int): Int = b.getShort(at).toInt() and 0xFFFF
        private fun u32(b: ByteBuffer, at: Int): Long = b.getInt(at).toLong() and 0xFFFFFFFFL
    }
}

/** What [checkKeyFont] made of a file the user picked for the keys. */
sealed interface KeyFontCheck {
    /** Usable; [latin] / [bangla] say which letters it has (the phone's font fills in the rest). */
    data class Ok(val latin: Boolean, val bangla: Boolean) : KeyFontCheck

    /** Empty, too big, a web font (.woff), or not a font at all. */
    data object NotAFont : KeyFontCheck

    data object TooBig : KeyFontCheck

    /** A real font, but with neither English nor Bangla letters (symbols, icons, another script). */
    data object NoLetters : KeyFontCheck
}

/** Checks a picked font file before it is used on the keys. */
fun checkKeyFont(bytes: ByteArray): KeyFontCheck {
    if (bytes.size > FontFile.MAX_BYTES) return KeyFontCheck.TooBig
    val font = FontFile.parse(bytes) ?: return KeyFontCheck.NotAFont
    val latin = font.coversLatin
    val bangla = font.coversBangla
    return if (latin || bangla) KeyFontCheck.Ok(latin, bangla) else KeyFontCheck.NoLetters
}

/** A short message for the settings screen; null when the font is fine as it is. */
fun KeyFontCheck.message(): String? = when (this) {
    is KeyFontCheck.Ok -> when {
        latin && bangla -> null
        latin -> "This font has no Bangla letters, so Bangla keys use the phone's font."
        else -> "This font has no English letters, so English keys use the phone's font."
    }
    KeyFontCheck.NotAFont -> "That file isn't a font Jolpai can use. Pick a .ttf or .otf file."
    KeyFontCheck.TooBig -> "That font file is too big (over 32 MB)."
    KeyFontCheck.NoLetters -> "That font has no English or Bangla letters."
}

/** A picked font file's name as shown in settings: no folder, no font extension, at most 40 characters. */
fun fontDisplayName(fileName: String): String {
    val base = fileName.substringAfterLast('/').substringAfterLast(':')
    val stem = Regex("""\.(ttf|otf|ttc|otc)$""", RegexOption.IGNORE_CASE).replace(base, "")
    return stem.trim().ifEmpty { "Font" }.take(40)
}
