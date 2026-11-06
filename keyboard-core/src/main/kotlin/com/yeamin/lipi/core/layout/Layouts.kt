package com.yeamin.lipi.core.layout

import com.yeamin.lipi.core.field.EnterAction
import com.yeamin.lipi.core.field.FieldInfo
import com.yeamin.lipi.core.field.FieldKind
import com.yeamin.lipi.core.model.Key
import com.yeamin.lipi.core.model.EditCommand
import com.yeamin.lipi.core.gesture.GestureZone
import com.yeamin.lipi.core.model.KeyAction
import com.yeamin.lipi.core.model.KeyIcon
import com.yeamin.lipi.core.model.KeyRow
import com.yeamin.lipi.core.model.KeyStyle
import com.yeamin.lipi.core.model.KeyboardLayout
import com.yeamin.lipi.core.model.KeyboardPage
import com.yeamin.lipi.core.model.LayoutId
import com.yeamin.lipi.core.model.Panel
import com.yeamin.lipi.core.model.PopupItem

/**
 * Builds the key grids for every layout and page.
 *
 * Built layouts are cached: they depend only on the inputs of [build], and rebuilding on
 * every key press would allocate in the typing path.
 */
class LayoutFactory(
    private val available: List<LayoutId> = LayoutId.entries,
    /** Show a row of digits above the letters (then digits leave the top row's long-press). */
    private val numberRow: Boolean = false,
) {

    private data class CacheKey(val id: LayoutId, val page: KeyboardPage, val shifted: Boolean, val bottom: BottomVariant)

    /** The parts of the field that change the bottom row. */
    private data class BottomVariant(val kind: FieldKind, val enter: EnterAction, val password: Boolean)

    private val cache = HashMap<CacheKey, KeyboardLayout>()

    fun build(id: LayoutId, page: KeyboardPage, shifted: Boolean, field: FieldInfo): KeyboardLayout {
        val key = CacheKey(id, page, shifted && page == KeyboardPage.LETTERS, BottomVariant(field.kind, field.enterAction, field.isPassword))
        return cache.getOrPut(key) { create(key.id, key.page, key.shifted, field) }
    }

    private fun create(id: LayoutId, page: KeyboardPage, shifted: Boolean, field: FieldInfo): KeyboardLayout {
        val enter = enterKey(field.enterAction)
        return when (page) {
            KeyboardPage.NUMBER -> numberPad(lettersKey(id), enter)
            KeyboardPage.PHONE -> phonePad(lettersKey(id), enter)
            KeyboardPage.LETTERS -> when (id) {
                LayoutId.ENGLISH -> qwerty(LayoutId.ENGLISH, shifted, field, enter)
                LayoutId.AVRO -> qwerty(LayoutId.AVRO, shifted, field, enter)
                LayoutId.NATIONAL -> national(shifted, field, enter)
            }
            KeyboardPage.SYMBOLS -> symbols(id, field, enter)
            KeyboardPage.SYMBOLS_MORE -> moreSymbols(id, field, enter)
        }
    }

    // ---------------------------------------------------------------- English

    /**
     * QWERTY letters, used by English and by Avro (which types Roman keys and converts them).
     * Avro gets no accented letters on long-press: they mean nothing to its rules.
     */
    private fun qwerty(id: LayoutId, shifted: Boolean, field: FieldInfo, enter: Key): KeyboardLayout {
        val accents = id == LayoutId.ENGLISH
        fun letter(c: Char, digit: Char? = null): Key {
            val label = if (shifted) c.uppercase() else c.toString()
            val extras = buildList {
                if (digit != null) add(digit.toString())
                if (accents) ACCENTS[c]?.forEach { add(if (shifted) it.uppercase() else it) }
            }
            // Editing shortcuts on long-press, like Ctrl+X / C / V: x cuts, c copies, v pastes.
            val shortcut = when (c) {
                'x' -> KeyAction.Edit(EditCommand.CUT)
                'c' -> KeyAction.Edit(EditCommand.COPY)
                'v' -> KeyAction.Edit(EditCommand.PASTE)
                else -> null
            }
            return Key(
                label, KeyAction.Text(label), hint = digit?.toString(),
                popup = if (shortcut != null) emptyList() else extras.map { PopupItem(it, KeyAction.Text(it)) },
                longPress = shortcut,
            )
        }
        val row1 = "qwertyuiop".mapIndexed { i, c -> letter(c, if (numberRow) null else DIGITS[i]) }
        val row2 = "asdfghjkl".map { letter(it) }
        val row3 = listOf(shiftKey()) + "zxcvbnm".map { letter(it) } + deleteKey()
        val digits = if (numberRow) listOf(KeyRow(DIGITS.map { textKey(it.toString(), SYMBOL_EXTRAS[it.toString()].orEmpty()) })) else emptyList()
        return KeyboardLayout(digits + listOf(KeyRow(row1), KeyRow(row2), KeyRow(row3), bottomRow(id, field, enter, "?123", KeyboardPage.SYMBOLS)))
    }

    // ---------------------------------------------------------------- National (জাতীয়)

    private fun national(shifted: Boolean, field: FieldInfo, enter: Key): KeyboardLayout {
        val primary = if (shifted) NationalKeyMap.SHIFT else NationalKeyMap.NORMAL
        val other = if (shifted) NationalKeyMap.NORMAL else NationalKeyMap.SHIFT
        val hints = if (shifted) NationalKeyMap.SHIFT_HINTS else NationalKeyMap.HINTS
        val rows = primary.indices.map { r ->
            val keys = primary[r].indices.map { k ->
                val label = primary[r][k]
                val hint = hints[r][k]
                val digit = if (r == 0 && !numberRow) BANGLA_DIGITS[k].toString() else null
                // Long-press: the corner hint first (so press-and-release types it), then the
                // key's other shift state, the digit, and any extras.
                val choices = (listOfNotNull(hint, other[r][k], digit) + NationalKeyMap.EXTRAS[label].orEmpty())
                    .distinct()
                    .filter { it != label }
                Key(label, KeyAction.Text(label), hint = hint, popup = choices.map { PopupItem(it, KeyAction.Text(it)) })
            }
            if (r == 2) listOf(shiftKey()) + keys + deleteKey() else keys
        }
        val digits = if (numberRow) listOf(KeyRow(BANGLA_DIGITS.mapIndexed { i, d -> textKey(d.toString(), listOf(DIGITS[i].toString())) })) else emptyList()
        return KeyboardLayout(digits + rows.map { KeyRow(it) } + bottomRow(LayoutId.NATIONAL, field, enter, "?১২৩", KeyboardPage.SYMBOLS))
    }

    // ---------------------------------------------------------------- Symbols

    private fun symbols(id: LayoutId, field: FieldInfo, enter: Key): KeyboardLayout {
        val bangla = id == LayoutId.NATIONAL
        val back = if (bangla) "কখগ" else "ABC"
        val rows = if (bangla) {
            // Ridmik's ?১২৩ page: Bangla digits (English digit on long-press), then symbols.
            listOf(
                BANGLA_DIGITS.mapIndexed { i, d ->
                    val latin = DIGITS[i].toString()
                    Key(d.toString(), KeyAction.Text(d.toString()), hint = latin, popup = (listOf(latin) + SYMBOL_EXTRAS[latin].orEmpty()).map { PopupItem(it, KeyAction.Text(it)) })
                },
                listOf("@", "#", "৳", "%", "&", "*", "-", "+", "(", ")").map { hintedSymbol(it) },
                listOf(pageKey("ALT", KeyboardPage.SYMBOLS_MORE)) +
                    listOf("!", "\"", "'", ":", ";", "/", "?").map { hintedSymbol(it) } + deleteKey(),
            )
        } else {
            listOf(
                DIGITS.map { textKey(it.toString(), SYMBOL_EXTRAS[it.toString()].orEmpty()) },
                listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/").map { textKey(it, SYMBOL_EXTRAS[it].orEmpty()) },
                listOf(pageKey("=\\<", KeyboardPage.SYMBOLS_MORE)) +
                    listOf("*", "\"", "'", ":", ";", "!", "?").map { textKey(it, SYMBOL_EXTRAS[it].orEmpty()) } + deleteKey(),
            )
        }
        return KeyboardLayout(rows.map { KeyRow(it) } + bottomRow(id, field, enter, back, KeyboardPage.LETTERS))
    }

    private fun moreSymbols(id: LayoutId, field: FieldInfo, enter: Key): KeyboardLayout {
        val bangla = id == LayoutId.NATIONAL
        val back = if (bangla) "কখগ" else "ABC"
        if (bangla) {
            // Ridmik's ALT page, including Tab and র‍্য (র + ZWJ + ্য, as in র‍্যাব).
            val rows = listOf(
                listOf("~", "`", "|", "♪", "♥", "π", "÷", "♠", "{", "}").map { hintedSymbol(it) },
                listOf(Key("⇥", KeyAction.Text("\t"))) +
                    listOf("£", "¢", "♦", "♣", "^", "_", "=", "[", "]").map { hintedSymbol(it) },
                listOf(pageKey("?১২৩", KeyboardPage.SYMBOLS)) +
                    listOf("র‍্য", "®", "©", "™", "\\", "<", ">").map { hintedSymbol(it) } + deleteKey(),
            )
            val bottom = KeyRow(
                listOf(
                    pageKey(back, KeyboardPage.LETTERS),
                    textKey("„", listOf("“", "”", "«", "»"), KeyStyle.FUNCTION),
                    spaceKey(id, 5f),
                    textKey("…", listOf("·", "•", "‰"), KeyStyle.FUNCTION),
                    enter,
                ),
            )
            return KeyboardLayout(rows.map { KeyRow(it) } + bottom)
        }
        val rows = listOf(
            listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆").map { textKey(it) },
            (if (bangla) listOf("$", "%", "*", "/", "_", "=", "{", "}", "\\", "^") else listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\"))
                .map { textKey(it, SYMBOL_EXTRAS[it].orEmpty()) },
            listOf(pageKey(if (bangla) "?১২৩" else "?123", KeyboardPage.SYMBOLS)) +
                (if (bangla) listOf("[", "]", "<", ">", ";", "©", "®") else listOf("%", "©", "®", "™", "✓", "[", "]"))
                    .map { textKey(it, SYMBOL_EXTRAS[it].orEmpty()) } + deleteKey(),
        )
        return KeyboardLayout(rows.map { KeyRow(it) } + bottomRow(id, field, enter, back, KeyboardPage.LETTERS))
    }

    // ---------------------------------------------------------------- Number and phone pads

    /**
     * Number fields: a calculator-style pad. Operators down the left, large digits in the
     * middle (hold a digit for its Bangla form), and ⌫ / space / / / Enter down the right.
     *
     * ```
     *  +   1   2   3   ⌫
     *  -   4   5   6   ␣
     *  *   7   8   9   /
     * ABC  ,   0   .   ↵
     * ```
     * ABC switches to the letters (their ?123 key comes back here). Hold / for ÷ and %.
     */
    private fun numberPad(letters: Key, enter: Key): KeyboardLayout = KeyboardLayout(
        listOf(
            KeyRow(listOf(sideKey("+", "±")) + digitKeys("123") + deleteKey(1f)),
            KeyRow(listOf(sideKey("-", "−", "_")) + digitKeys("456") + spaceKey("␣", 1f)),
            KeyRow(listOf(sideKey("*", "×")) + digitKeys("789") + sideKey("/", "÷", "%", "‰")),
            KeyRow(
                listOf(letters, textKey(",", style = KeyStyle.FUNCTION).copy(width = DIGIT_WIDTH)) +
                    digitKeys("0") + textKey(".", style = KeyStyle.FUNCTION).copy(width = DIGIT_WIDTH) + enter.copy(width = 1f),
            ),
        ),
    )

    /**
     * Phone fields: a dial pad with letters under the digits, and the dialling symbols.
     *
     * ```
     *  +   1   2   3   ⌫
     *  *   4   5   6   ␣
     *  #   7   8   9   -
     * ABC  (   0   )   ↵
     * ```
     * Hold * for , (a pause) and # for ; (a wait).
     */
    private fun phonePad(letters: Key, enter: Key): KeyboardLayout = KeyboardLayout(
        listOf(
            KeyRow(listOf(sideKey("+")) + digitKeys("123", listOf(null, "ABC", "DEF")) + deleteKey(1f)),
            KeyRow(listOf(sideKey("*", ",")) + digitKeys("456", listOf("GHI", "JKL", "MNO")) + spaceKey("␣", 1f)),
            KeyRow(listOf(sideKey("#", ";")) + digitKeys("789", listOf("PQRS", "TUV", "WXYZ")) + sideKey("-")),
            KeyRow(
                listOf(letters, textKey("(", style = KeyStyle.FUNCTION).copy(width = DIGIT_WIDTH)) +
                    digitKeys("0", listOf("+")) + textKey(")", style = KeyStyle.FUNCTION).copy(width = DIGIT_WIDTH) + enter.copy(width = 1f),
            ),
        ),
    )

    /** Wide digit keys; [hints] are shown small in the corner (dial-pad letters). Hold for the Bangla digit. */
    private fun digitKeys(digits: String, hints: List<String?> = emptyList()): List<Key> = digits.mapIndexed { i, d ->
        val bangla = BANGLA_DIGITS[(d - '0' + 9) % 10].toString()
        // On the dial pad, holding 0 gives + (international prefix) first.
        val extras = (if (hints.getOrNull(i) == "+") listOf("+") else emptyList()) + bangla
        Key(d.toString(), KeyAction.Text(d.toString()), width = DIGIT_WIDTH, hint = hints.getOrNull(i), popup = extras.map { PopupItem(it, KeyAction.Text(it)) })
    }

    /** On the number and phone pads: go to the letters. */
    private fun lettersKey(id: LayoutId) = Key(if (id == LayoutId.NATIONAL) "কখগ" else "ABC", KeyAction.SwitchPage(KeyboardPage.LETTERS), style = KeyStyle.FUNCTION)

    /** A narrow symbol key at the side of a pad. */
    private fun sideKey(text: String, vararg extras: String) = textKey(text, extras.toList(), KeyStyle.FUNCTION)

    // ---------------------------------------------------------------- Shared keys

    /**
     * `?123 😊 , [space] . ↵` (the language button is in the top bar). The National letters page has `,` and `।` like Ridmik's; the
     * comma key changes to @ or / in email and URL fields. Password fields have no emoji key.
     */
    private fun bottomRow(id: LayoutId, field: FieldInfo, enter: Key, label: String, target: KeyboardPage): KeyRow {
        // In number and phone fields, the letters' ?123 key goes back to the number / dial pad.
        val padPage = when (field.kind) {
            FieldKind.NUMBER, FieldKind.DATETIME -> KeyboardPage.NUMBER
            FieldKind.PHONE -> KeyboardPage.PHONE
            else -> null
        }
        val pageTarget = if (target == KeyboardPage.SYMBOLS && padPage != null) padPage else target
        val pageLabel = if (pageTarget == padPage) (if (id == LayoutId.NATIONAL) "১২৩" else "123") else label
        val banglaLetters = id == LayoutId.NATIONAL && pageTarget != KeyboardPage.LETTERS
        val comma = when {
            field.kind == FieldKind.EMAIL -> textKey("@", style = KeyStyle.FUNCTION)
            field.kind == FieldKind.URI -> textKey("/", style = KeyStyle.FUNCTION)
            banglaLetters -> textKey(",", listOf("/", ";", ":", "-", "?", "!"), KeyStyle.FUNCTION)
            else -> textKey(",", listOf(";", ":", "'", "\""), KeyStyle.FUNCTION)
        }
        val period = if (banglaLetters) {
            textKey("।", listOf(",", ".", "?", "!", "-", "'", "\"", ":", ";", "৳"), KeyStyle.FUNCTION)
        } else {
            textKey(".", listOf(",", "?", "!", "'", "\"", "-", ":", ";", "@", "#", "/"), KeyStyle.FUNCTION)
        }
        return KeyRow(
            listOf(
                pageKey(pageLabel, pageTarget),
                *(if (field.isPassword) arrayOf(comma.copy(zone = GestureZone.COMMA), spaceKey(id, 5f)) else arrayOf(emojiKey(), comma.copy(zone = GestureZone.COMMA), spaceKey(id, 4f))),
                period.copy(zone = GestureZone.PERIOD),
                enter,
            ),
        )
    }

    private fun emojiKey(): Key =
        Key("😊", KeyAction.ShowPanel(Panel.EMOJI), style = KeyStyle.FUNCTION, icon = KeyIcon.EMOJI)

    /** A symbol with Ridmik's corner hint (if it has one) as its first long-press choice. */
    private fun hintedSymbol(text: String): Key {
        val hint = NationalKeyMap.SYMBOL_HINTS[text]
        val choices = (listOfNotNull(hint) + SYMBOL_EXTRAS[text].orEmpty()).distinct()
        return Key(text, KeyAction.Text(text), hint = hint, popup = choices.map { PopupItem(it, KeyAction.Text(it)) })
    }

    private fun textKey(text: String, extras: List<String> = emptyList(), style: KeyStyle = KeyStyle.CHARACTER) =
        Key(text, KeyAction.Text(text), style = style, popup = extras.map { PopupItem(it, KeyAction.Text(it)) })

    private fun pageKey(label: String, page: KeyboardPage) =
        Key(label, KeyAction.SwitchPage(page), width = 1.5f, style = KeyStyle.FUNCTION)

    private fun shiftKey() = Key("⇧", KeyAction.Shift, width = 1.5f, style = KeyStyle.SHIFT, icon = KeyIcon.SHIFT, zone = GestureZone.SHIFT)

    private fun deleteKey(width: Float = 1.5f) = Key("⌫", KeyAction.Delete, width = width, style = KeyStyle.DELETE, icon = KeyIcon.DELETE, zone = GestureZone.BACKSPACE)

    /** Space shows the language. Swipe it to move the cursor; hold it for trackpad mode. */
    private fun spaceKey(id: LayoutId, width: Float): Key = Key(id.spaceLabel, KeyAction.Space, width = width, style = KeyStyle.SPACE, zone = GestureZone.SPACE)

    private fun spaceKey(label: String, width: Float) = Key(label, KeyAction.Space, width = width, style = KeyStyle.SPACE, zone = GestureZone.SPACE)

    private fun enterKey(action: EnterAction): Key {
        val (label, icon) = when (action) {
            EnterAction.NEWLINE -> "↵" to KeyIcon.ENTER
            EnterAction.GO -> "➜" to KeyIcon.GO
            EnterAction.SEARCH -> "🔍" to KeyIcon.SEARCH
            EnterAction.SEND -> "➤" to KeyIcon.SEND
            EnterAction.NEXT -> "⇥" to KeyIcon.NEXT
            EnterAction.DONE -> "✓" to KeyIcon.DONE
            EnterAction.PREVIOUS -> "⇤" to KeyIcon.PREVIOUS
        }
        val style = if (action == EnterAction.NEWLINE) KeyStyle.ENTER else KeyStyle.ENTER_ACTION
        return Key(label, KeyAction.Enter, width = 1.5f, style = style, icon = icon)
    }

    private companion object {
        val DIGITS = "1234567890".toList()
        const val DIGIT_WIDTH = 1.5f
        val BANGLA_DIGITS = "১২৩৪৫৬৭৮৯০".toList()

        val ACCENTS: Map<Char, List<String>> = mapOf(
            'a' to listOf("à", "á", "â", "ä", "æ", "ã", "å", "ā"),
            'c' to listOf("ç", "ć", "č"),
            'e' to listOf("è", "é", "ê", "ë", "ē", "ę"),
            'i' to listOf("ì", "í", "î", "ï", "ī"),
            'n' to listOf("ñ", "ń"),
            'o' to listOf("ò", "ó", "ô", "ö", "õ", "ø", "œ", "ō"),
            's' to listOf("ß", "ś", "š"),
            'u' to listOf("ù", "ú", "û", "ü", "ū"),
            'y' to listOf("ý", "ÿ"),
            'z' to listOf("ž", "ź", "ż"),
        )

        val SYMBOL_EXTRAS: Map<String, List<String>> = mapOf(
            "1" to listOf("¹", "½", "⅓", "¼", "⅛"),
            "2" to listOf("²", "⅔"),
            "3" to listOf("³", "¾", "⅜"),
            "4" to listOf("⁴"),
            "0" to listOf("ⁿ", "∅"),
            "$" to listOf("৳", "€", "£", "¥", "₹", "¢"),
            "৳" to listOf("$", "€", "£", "¥", "₹"),
            "-" to listOf("_", "–", "—", "·"),
            "+" to listOf("±"),
            "(" to listOf("<", "[", "{"),
            ")" to listOf(">", "]", "}"),
            "!" to listOf("¡"),
            "?" to listOf("¿"),
            "'" to listOf("‘", "’", "‚"),
            "\"" to listOf("“", "”", "„", "«", "»"),
            "%" to listOf("‰"),
            "=" to listOf("≠", "≈", "∞"),
            "*" to listOf("†", "‡", "★"),
            "#" to listOf("№"),
            "&" to listOf("§"),
        )
    }
}

/**
 * The National (জাতীয়) layout as Ridmik Keyboard arranges it, copied from the user's
 * screenshots on 2026-11-06 (they asked for "the national keyboard layout like this").
 * Rows follow QWERTY positions: row 1 = Q…P, row 2 = A…L, row 3 = Z…M.
 *
 * Compared with the BDS 1738 table: Z is chandrabindu (shift: bisarga), X is o-kar
 * (shift: au-kar), shift-W is ya-phala, shift-A is ঋ, shift-G is reph and shift-V is ra-phala
 * (ল is র's corner letter: hold র). Shifted page checked against the user's second screenshot.
 */
object NationalKeyMap {
    val NORMAL: List<List<String>> = listOf(
        listOf("ঙ", "য", "ড", "প", "ট", "চ", "জ", "হ", "গ", "ড়"),
        listOf("ৃ", "ু", "ি", "ব", "্", "া", "ক", "ত", "দ"),
        listOf("ঁ", "ো", "ে", "র", "ন", "স", "ম"),
    )
    val SHIFT: List<List<String>> = listOf(
        listOf("ং", "্য", "ঢ", "ফ", "ঠ", "ছ", "ঝ", "ঞ", "ঘ", "ঢ়"),
        listOf("ঋ", "ূ", "ী", "ভ", "র্", "অ", "খ", "থ", "ধ"),
        listOf("ঃ", "ৌ", "ৈ", "্র", "ণ", "ষ", "শ"),
    )

    /**
     * The small character in each key's corner, as Ridmik shows it. Long-press types it
     * (it is the first choice in the popup). Vowel signs show their full vowel: ু → উ.
     */
    val HINTS: List<List<String?>> = listOf(
        listOf("ং", "য়", "ঢ", "ফ", "ঠ", "ছ", "ঝ", "ঞ", "ঘ", "ঢ়"),
        listOf("ঋ", "উ", "ই", "ভ", null, "আ", "খ", "ৎ", "ধ"),
        listOf("ঃ", "ও", "এ", "ল", "ণ", "ষ", "শ"),
    )
    val SHIFT_HINTS: List<List<String?>> = listOf(
        List(10) { null },
        listOf(null, "ঊ", "ঈ", null, null, null, null, null, null),
        listOf(null, "ঔ", "ঐ", null, null, null, null),
    )

    /** More long-press choices for some keys (after the hint and the other shift state). */
    val EXTRAS: Map<String, List<String>> = mapOf(
        "র" to listOf("্র", "র্", "র‍্য"),
        "য" to listOf("্য"),
        "ত" to listOf("ৎ"),
        "ঁ" to listOf("ং"),
        "ং" to listOf("ঁ", "ঃ"),
        "্" to listOf("্র", "্য"),
    )

    /** Corner hints on the National symbol pages (also their first long-press choice). */
    val SYMBOL_HINTS: Map<String, String> = mapOf(
        "৳" to "$", "*" to "★", "-" to "_", "(" to "<", ")" to ">", "'" to "’", ":" to "ঃ",
        "π" to "Π", "^" to "→", "=" to "≠",
    )
}
