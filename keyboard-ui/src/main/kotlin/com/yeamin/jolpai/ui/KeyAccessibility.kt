package com.yeamin.jolpai.ui

import com.yeamin.jolpai.core.model.Key
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.model.KeyStyle
import com.yeamin.jolpai.core.model.KeyboardPage
import com.yeamin.jolpai.core.model.ShiftState

/** What TalkBack says for a key. Symbols that a screen reader can't pronounce get names. */
object KeyAccessibility {

    fun description(key: Key, shift: ShiftState): String = when (val a = key.action) {
        KeyAction.Shift -> when (shift) {
            ShiftState.OFF -> "Shift"
            ShiftState.ON -> "Shift, on"
            ShiftState.LOCKED -> "Caps lock, on"
        }
        KeyAction.Delete -> "Delete"
        KeyAction.Space -> "Space, ${key.label}. Hold to move the cursor"
        is KeyAction.ShowPanel -> "Emoji"
        KeyAction.Enter -> when (key.label) {
            "➜" -> "Go"
            "🔍" -> "Search"
            "➤" -> "Send"
            "⇥" -> "Next"
            "✓" -> "Done"
            "⇤" -> "Previous"
            else -> "Enter"
        }
        KeyAction.NextLayout -> "Switch language. Double-tap and hold for the list"
        is KeyAction.SwitchPage -> when (a.page) {
            KeyboardPage.LETTERS -> "Letters"
            KeyboardPage.SYMBOLS -> "Symbols"
            KeyboardPage.SYMBOLS_MORE -> "More symbols"
            else -> key.label
        }
        is KeyAction.Text -> spoken(a.text)
        else -> key.label
    }.let { base ->
        when {
            key.longPress == KeyAction.Edit(com.yeamin.jolpai.core.model.EditCommand.CUT) -> "$base. Hold to cut"
            key.longPress == KeyAction.Edit(com.yeamin.jolpai.core.model.EditCommand.COPY) -> "$base. Hold to copy"
            key.longPress == KeyAction.Edit(com.yeamin.jolpai.core.model.EditCommand.PASTE) -> "$base. Hold to paste"
            key.popup.isNotEmpty() && key.style == KeyStyle.CHARACTER -> "$base. More options on long press"
            else -> base
        }
    }

    /** Names for characters screen readers often skip or misread. */
    private fun spoken(text: String): String = NAMES[text] ?: text

    private val NAMES = mapOf(
        "." to "Period", "," to "Comma", "?" to "Question mark", "!" to "Exclamation mark",
        "'" to "Apostrophe", "\"" to "Quote", ":" to "Colon", ";" to "Semicolon", "-" to "Dash",
        "_" to "Underscore", "/" to "Slash", "\\" to "Backslash", "@" to "At", "#" to "Hash",
        "&" to "Ampersand", "*" to "Star", "(" to "Open bracket", ")" to "Close bracket",
        "+" to "Plus", "=" to "Equals", "%" to "Percent", "$" to "Dollar", "৳" to "Taka",
        "।" to "দাঁড়ি", "্" to "হসন্ত", "্র" to "র-ফলা", "্য" to "য-ফলা", "র্" to "রেফ",
        "ং" to "অনুস্বার", "ঃ" to "বিসর্গ", "ঁ" to "চন্দ্রবিন্দু", "ৎ" to "খণ্ড ত",
        "া" to "আ-কার", "ি" to "ই-কার", "ী" to "ঈ-কার", "ু" to "উ-কার", "ূ" to "ঊ-কার",
        "ৃ" to "ঋ-কার", "ে" to "এ-কার", "ৈ" to "ঐ-কার", "ৗ" to "ঔ-কারের চিহ্ন",
    )
}
