package com.yeamin.jolpai.core.tools

/**
 * Styled text that works anywhere (Facebook, WhatsApp, bios): Unicode's mathematical letters
 * (𝐁𝐨𝐥𝐝, 𝐼𝑡𝑎𝑙𝑖𝑐, 𝓢𝓬𝓻𝓲𝓹𝓽 …), circled and full-width letters, small capitals, and strikethrough
 * or underline marks. Letters and digits change; other characters stay. Strike and underline
 * also work on Bangla.
 *
 * Note: screen readers may read styled letters oddly, and some old phones lack the glyphs.
 */
enum class TextStyle(val label: String, val sample: String) {
    BOLD("Bold", "𝐁𝐨𝐥𝐝"),
    ITALIC("Italic", "𝐼𝑡𝑎𝑙𝑖𝑐"),
    BOLD_ITALIC("Bold italic", "𝑩𝒐𝒍𝒅 𝒊𝒕."),
    SCRIPT("Script", "𝒮𝒸𝓇𝒾𝓅𝓉"),
    BOLD_SCRIPT("Bold script", "𝓢𝓬𝓻𝓲𝓹𝓽"),
    FRAKTUR("Gothic", "𝔊𝔬𝔱𝔥𝔦𝔠"),
    DOUBLE("Outline", "𝕆𝕦𝕥𝕝𝕚𝕟𝕖"),
    SANS_BOLD("Sans bold", "𝗦𝗮𝗻𝘀"),
    SANS_ITALIC("Sans italic", "𝘚𝘢𝘯𝘴"),
    MONO("Mono", "𝙼𝚘𝚗𝚘"),
    CIRCLED("Circled", "Ⓒⓘⓡⓒⓛⓔ"),
    WIDE("Wide", "Ｗｉｄｅ"),
    SMALL_CAPS("Small caps", "Sᴍᴀʟʟ"),
    STRIKE("Strike", "S̶t̶r̶i̶k̶e̶"),
    UNDERLINE("Underline", "U̲n̲d̲e̲r̲"),
    ;

    companion object {
        /** [text] in [style], from its plain letters (so re-styling never stacks styles). */
        fun apply(style: TextStyle, text: String): String {
            val plainText = plain(text)
            return when (style) {
                STRIKE -> withMark(plainText, '̶')
                UNDERLINE -> withMark(plainText, '̲')
                else -> buildString { plainText.codePoints().forEach { appendCodePoint(map(style, it)) } }
            }
        }

        /** One typed character in [style] (for typing with a style on). */
        fun applyChar(style: TextStyle, c: String): String = apply(style, c)

        /** Turns styled letters back into plain ones and removes strike/underline marks. */
        fun plain(text: String): String = buildString {
            text.codePoints().forEach { cp ->
                if (cp == 0x0336 || cp == 0x0332) return@forEach
                appendCodePoint(REVERSE[cp] ?: cp)
            }
        }

        /** True when [style] changes this character (letters and, for some styles, digits). */
        fun styles(style: TextStyle, c: Int): Boolean = map(style, c) != c || style == STRIKE || style == UNDERLINE

        // ---------------------------------------------------------------- mapping

        private class Alphabet(val upper: Int, val lower: Int, val digit: Int? = null, val exceptions: Map<Char, Int> = emptyMap())

        private val ALPHABETS: Map<TextStyle, Alphabet> = mapOf(
            BOLD to Alphabet(0x1D400, 0x1D41A, 0x1D7CE),
            ITALIC to Alphabet(0x1D434, 0x1D44E, null, mapOf('h' to 0x210E)),
            BOLD_ITALIC to Alphabet(0x1D468, 0x1D482),
            SCRIPT to Alphabet(
                0x1D49C, 0x1D4B6, null,
                mapOf(
                    'B' to 0x212C, 'E' to 0x2130, 'F' to 0x2131, 'H' to 0x210B, 'I' to 0x2110, 'L' to 0x2112,
                    'M' to 0x2133, 'R' to 0x211B, 'e' to 0x212F, 'g' to 0x210A, 'o' to 0x2134,
                ),
            ),
            BOLD_SCRIPT to Alphabet(0x1D4D0, 0x1D4EA),
            FRAKTUR to Alphabet(0x1D504, 0x1D51E, null, mapOf('C' to 0x212D, 'H' to 0x210C, 'I' to 0x2111, 'R' to 0x211C, 'Z' to 0x2128)),
            DOUBLE to Alphabet(
                0x1D538, 0x1D552, 0x1D7D8,
                mapOf('C' to 0x2102, 'H' to 0x210D, 'N' to 0x2115, 'P' to 0x2119, 'Q' to 0x211A, 'R' to 0x211D, 'Z' to 0x2124),
            ),
            SANS_BOLD to Alphabet(0x1D5D4, 0x1D5EE, 0x1D7EC),
            SANS_ITALIC to Alphabet(0x1D608, 0x1D622),
            MONO to Alphabet(0x1D670, 0x1D68A, 0x1D7F6),
            WIDE to Alphabet(0xFF21, 0xFF41, 0xFF10),
        )

        private const val SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘꞯʀꜱᴛᴜᴠᴡxʏᴢ"

        private fun map(style: TextStyle, cp: Int): Int {
            if (cp > 0xFFFF) return cp
            val c = cp.toChar()
            when (style) {
                CIRCLED -> return when (c) {
                    in 'A'..'Z' -> 0x24B6 + (c - 'A')
                    in 'a'..'z' -> 0x24D0 + (c - 'a')
                    '0' -> 0x24EA
                    in '1'..'9' -> 0x2460 + (c - '1')
                    else -> cp
                }
                SMALL_CAPS -> return when (c) {
                    in 'a'..'z' -> SMALL[c - 'a'].code
                    else -> cp // capitals stay capital, like real small caps
                }
                STRIKE, UNDERLINE -> return cp
                else -> Unit
            }
            val a = ALPHABETS[style] ?: return cp
            a.exceptions[c]?.let { return it }
            return when (c) {
                in 'A'..'Z' -> a.upper + (c - 'A')
                in 'a'..'z' -> a.lower + (c - 'a')
                in '0'..'9' -> a.digit?.let { it + (c - '0') } ?: cp
                else -> cp
            }
        }

        /** Every styled character back to its plain letter. */
        private val REVERSE: Map<Int, Int> = buildMap {
            val plainChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
            TextStyle.entries.forEach { style ->
                if (style == STRIKE || style == UNDERLINE) return@forEach
                plainChars.forEach { c -> val m = map(style, c.code); if (m != c.code) put(m, c.code) }
            }
        }

        /** Puts [mark] after every character, but not inside a Bangla letter cluster. */
        private fun withMark(text: String, mark: Char): String = buildString {
            val cps = text.codePoints().toArray()
            for (i in cps.indices) {
                val cp = cps[i]
                appendCodePoint(cp)
                if (cp == '\n'.code) continue
                val next = cps.getOrNull(i + 1)
                val joins = cp == 0x09CD || cp == 0x200D || cp == 0x200C // hasanta, ZWJ, ZWNJ: the cluster goes on
                val nextIsMark = next != null && (isMark(next) || next == 0x200D || next == 0x200C)
                if (!joins && !nextIsMark) append(mark)
            }
        }

        private fun isMark(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
            Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
            else -> false
        }
    }
}
