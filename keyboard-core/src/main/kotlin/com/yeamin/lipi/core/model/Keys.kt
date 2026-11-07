package com.yeamin.lipi.core.model

/** What a key does when it is released (or chosen from a long-press popup). */
sealed interface KeyAction {
    data class Text(val text: String) : KeyAction
    data object Delete : KeyAction
    data object Enter : KeyAction
    data object Space : KeyAction
    data object Shift : KeyAction
    data class SwitchPage(val page: KeyboardPage) : KeyAction
    data object NextLayout : KeyAction
    data object PreviousLayout : KeyAction

    /** A custom gesture shortcut (see `gesture/GestureShortcuts.kt`); [text] for custom text. */
    data class Shortcut(val action: com.yeamin.lipi.core.gesture.GestureAction, val text: String = "") : KeyAction

    /** Open the emoji panel on a particular tab (GIFs, stickers). */
    data class OpenEmojiTab(val tab: com.yeamin.lipi.core.gesture.EmojiTab) : KeyAction

    /**
     * Backspace swipe in progress: select the [words] words before the point where the swipe
     * started (0 = none). Moving the finger back shrinks the range again.
     */
    data class SelectBack(val words: Int) : KeyAction

    /** Backspace swipe ended: delete the selected range, or restore the cursor if [delete] is false. */
    data class EndSelectBack(val delete: Boolean) : KeyAction
    data class SelectLayout(val id: LayoutId) : KeyAction

    /**
     * Commit [text] in place of the word being typed (a tapped suggestion), then a space.
     * [verbatim] means the user chose exactly what they typed (so it must not be autocorrected,
     * and an unknown word can be offered for the personal dictionary).
     */
    data class CommitCandidate(val text: String, val verbatim: Boolean = false) : KeyAction

    /** Add [word] to the personal dictionary. */
    data class AddToDictionary(val word: String, val language: com.yeamin.lipi.suggest.Language) : KeyAction

    /** Insert [text] exactly as given (clipboard paste, calculator result): no conversion, no learning. */
    data class PasteText(val text: String) : KeyAction

    /** Replace the [count] characters before the cursor with [text] (e.g. a typed sum with its answer). */
    data class ReplaceBefore(val count: Int, val text: String) : KeyAction

    /** Replace the snippet shortcut [shortcut] before the cursor with its text. */
    data class ExpandSnippet(val shortcut: String) : KeyAction

    /** A text-editing command from the editing panel or a gesture. */
    data class Edit(val command: EditCommand) : KeyAction

    /** Move the cursor by [steps] characters (negative = left), from the space-bar swipe. */
    data class MoveCursor(val steps: Int) : KeyAction

    data class InsertDateTime(val kind: DateTimeKind) : KeyAction

    /** Show a panel in place of the keys, or the keys again with [Panel.NONE]. */
    data class ShowPanel(val panel: Panel) : KeyAction

    /**
     * Insert an emoji (or kaomoji). [afterWord]: it was suggested for the word just typed, so
     * it goes after that word with a space.
     */
    data class InsertEmoji(val emoji: String, val afterWord: Boolean = false) : KeyAction

    /** Type into [panel]'s search box with the keys (instead of into the app). */
    data class StartSearch(val panel: Panel) : KeyAction

    /** Leave search mode and go back to the panel, keeping the query if [keepQuery]. */
    data class EndSearch(val keepQuery: Boolean) : KeyAction

    /** Voice typing: words heard so far (shown underlined, not final yet). */
    data class VoicePartial(val text: String) : KeyAction

    /** Voice typing: final words for one utterance. */
    data class VoiceFinal(val text: String) : KeyAction

    /**
     * Translate mode: the keys type into the translate box above the toolbar instead of the app,
     * and the translation can be inserted. [toBangla]: English → Bangla (else Bangla → English).
     */
    data class StartTranslate(val toBangla: Boolean = true) : KeyAction

    /** Leave translate mode; the keys type into the app again. */
    data object EndTranslate : KeyAction

    /** Turn the translation round (English → Bangla ⇄ Bangla → English), keeping the typed text. */
    data object SwapTranslate : KeyAction

    /** Replace the translate box's text with its spelling corrected ("Did you mean …"). */
    data object ApplyTranslateSpelling : KeyAction

    /** Empty the translate box. */
    data object ClearTranslate : KeyAction

    /** Put [text] (the translation) into the app and empty the translate box. */
    data class CommitTranslation(val text: String) : KeyAction

    /** Open the Check card: spelling, punctuation and grammar in the text field, checked offline. */
    data object StartCheck : KeyAction

    /** Close the Check card. */
    data object EndCheck : KeyAction

    /** Check the text again (after typing more). */
    data object Recheck : KeyAction

    /** Apply one suggestion from the Check card. */
    data class FixIssue(val issue: com.yeamin.lipi.suggest.TextIssue) : KeyAction

    /** Apply every suggestion. */
    data object FixAllIssues : KeyAction

    /**
     * Replace the checked text with an AI proofreading suggestion — only if the field still
     * holds exactly [original] where it was checked.
     */
    data class ApplyRewrite(val original: String, val replacement: String) : KeyAction

    /** Hide this suggestion (and the same word elsewhere) until the card is closed. */
    data class IgnoreIssue(val issue: com.yeamin.lipi.suggest.TextIssue) : KeyAction

    /** Actions the keyboard service handles itself (they need Android framework calls). */
    data class Host(val command: HostCommand) : KeyAction
}

enum class Panel { NONE, CLIPBOARD, EDITING, EMOJI, VOICE }

/** Buttons that can appear in the toolbar above the keys (order and visibility are settings). */
enum class ToolbarItem(val storageKey: String, val icon: String, val title: String, val panel: Panel) {
    EMOJI("emoji", "😊", "Emoji, kaomoji, stickers and GIFs", Panel.EMOJI),
    CLIPBOARD("clipboard", "📋", "Clipboard and snippets", Panel.CLIPBOARD),
    VOICE("voice", "🎤", "Voice typing", Panel.VOICE),
    EDIT("edit", "✎", "Text editing tools", Panel.EDITING),

    /** Translate English ⇄ Bangla offline in a box above the keys (not a panel: the keys stay). */
    TRANSLATE("translate", "🌐", "Translate English ⇄ Bangla", Panel.NONE),

    /** Check spelling, punctuation and grammar offline in a card above the keys. */
    CHECK("check", "✓", "Check spelling and grammar", Panel.NONE);

    companion object {
        val DEFAULT = listOf(EMOJI, CLIPBOARD, VOICE, EDIT, TRANSLATE, CHECK)
        fun fromStorageKey(key: String): ToolbarItem? = entries.firstOrNull { it.storageKey == key }
    }
}

enum class DateTimeKind { DATE, TIME }

enum class EditCommand {
    SELECT_ALL, CUT, COPY, PASTE, UNDO, REDO,
    LEFT, RIGHT, UP, DOWN, LINE_START, LINE_END,

    /** Start or stop selecting: while on, cursor moves extend the selection. */
    TOGGLE_SELECT,
    DELETE_WORD,
    SELECT_WORD_LEFT,
    SELECT_WORD_RIGHT,
}

enum class HostCommand { SYSTEM_KEYBOARD_PICKER, OPEN_SETTINGS, HIDE_KEYBOARD, TOGGLE_NUMBER_ROW, TOGGLE_ONE_HANDED, TOGGLE_FLOATING, START_VOICE }

/** Visual role of a key; the view picks colours and text size from it. */
enum class KeyStyle { CHARACTER, FUNCTION, SPACE, ENTER, ENTER_ACTION, SHIFT, DELETE }

/** A drawn symbol shown in place of the text label (the label still names the key for TalkBack). */
enum class KeyIcon { SHIFT, DELETE, ENTER, GO, SEARCH, SEND, NEXT, PREVIOUS, DONE, LANGUAGE, EMOJI }

data class PopupItem(val label: String, val action: KeyAction)

data class Key(
    val label: String,
    val action: KeyAction,
    /** Width in key units. A standard letter key is 1. */
    val width: Float = 1f,
    val style: KeyStyle = KeyStyle.CHARACTER,
    /** Small label in the corner, e.g. the shifted character or digit. */
    val hint: String? = null,
    /** Choices shown on long-press. */
    val popup: List<PopupItem> = emptyList(),
    val icon: KeyIcon? = null,
    /** Done straight away on long-press instead of showing [popup] (e.g. x → Cut). */
    val longPress: KeyAction? = null,
    /** Where custom gesture shortcuts on this key are looked up (space, comma, full stop …). */
    val zone: com.yeamin.lipi.core.gesture.GestureZone? = null,
)

data class KeyRow(val keys: List<Key>) {
    val units: Float = keys.fold(0f) { acc, k -> acc + k.width }
}

data class KeyboardLayout(val rows: List<KeyRow>) {
    /** Width of the widest row, in key units. Narrower rows are centred. */
    val units: Float = rows.maxOfOrNull { it.units } ?: 0f
}

enum class KeyboardPage { LETTERS, SYMBOLS, SYMBOLS_MORE, NUMBER, PHONE }

enum class ShiftState { OFF, ON, LOCKED }

/** The keyboard layouts (languages) the user can switch between. */
enum class LayoutId(
    val storageKey: String,
    val displayName: String,
    val shortLabel: String,
) {
    ENGLISH("en", "English", "EN"),
    AVRO("bn-avro", "বাংলা (অভ্র)", "অভ্র"),
    NATIONAL("bn-national", "বাংলা (জাতীয়)", "জাতীয়");

    /** Shown faintly on the space bar, like Ridmik: "English", "অভ্র", "জাতীয়". */
    val spaceLabel: String get() = if (this == ENGLISH) displayName else shortLabel

    companion object {
        fun fromStorageKey(key: String?): LayoutId? = entries.firstOrNull { it.storageKey == key }
    }
}
