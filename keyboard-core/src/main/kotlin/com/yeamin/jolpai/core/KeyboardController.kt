package com.yeamin.jolpai.core

import com.yeamin.jolpai.bangla.phonetic.AvroGrammar
import com.yeamin.jolpai.bangla.phonetic.PhoneticEngine
import com.yeamin.jolpai.core.editor.TextEditor
import com.yeamin.jolpai.core.field.FieldInfo
import com.yeamin.jolpai.core.field.FieldKind
import com.yeamin.jolpai.core.input.AvroProcessor
import com.yeamin.jolpai.core.input.Composition
import com.yeamin.jolpai.core.input.EnglishProcessor
import com.yeamin.jolpai.core.input.InputProcessor
import com.yeamin.jolpai.core.input.NationalProcessor
import com.yeamin.jolpai.core.layout.LayoutFactory
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.model.HostCommand
import com.yeamin.jolpai.core.model.KeyboardLayout
import com.yeamin.jolpai.core.model.KeyboardPage
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.model.ShiftState
import com.yeamin.jolpai.core.settings.DEFAULT_LONG_PRESS_MS
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.suggest.NextChars
import com.yeamin.jolpai.core.gesture.BuiltIns
import com.yeamin.jolpai.core.gesture.EmojiTab
import com.yeamin.jolpai.core.gesture.GestureDispatcher
import com.yeamin.jolpai.suggest.TypingScript
import com.yeamin.jolpai.core.suggest.WordContext
import com.yeamin.jolpai.core.editor.EditorCommand
import com.yeamin.jolpai.core.editor.InMemoryEditor
import com.yeamin.jolpai.core.model.ToolbarItem
import com.yeamin.jolpai.core.settings.VoiceLanguage
import com.yeamin.jolpai.core.model.DateTimeKind
import com.yeamin.jolpai.core.model.EditCommand
import com.yeamin.jolpai.core.model.Panel
import com.yeamin.jolpai.core.tools.Calculator
import com.yeamin.jolpai.core.tools.TextUnits
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import com.yeamin.jolpai.suggest.Language
import com.yeamin.jolpai.suggest.SuggestOptions
import com.yeamin.jolpai.suggest.Suggester
import com.yeamin.jolpai.suggest.WordSuggestions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The translate box: what has been typed ([source]), the direction, and its translation once the
 * service has one for exactly this text (null while translating, or when it failed).
 */
/** The phone's clipboard, as the keyboard's own boxes need it (supplied by the keyboard service). */
interface BoxClipboard {
    fun copy(text: String)

    /** The clipboard's text, or null when it holds none. */
    fun paste(): String?
}

data class TranslateUi(
    val source: String,
    val toBangla: Boolean,
    val translation: String?,
    /**
     * Spelling and grammar suggestions for the finished words of [source] (as in the Check card),
     * shown as chips under the box; nothing is changed until the user taps one.
     */
    val issues: List<com.yeamin.jolpai.suggest.TextIssue> = emptyList(),
)

/**
 * The Check card: the [issues] found in the text field (in text order). [stale]: the text has
 * changed since it was checked (the card offers to check again). [fixed]: how many suggestions
 * were applied since the card opened. [empty]: there was no text to check.
 */
data class CheckUi(
    val issues: List<com.yeamin.jolpai.suggest.TextIssue>,
    val stale: Boolean,
    val fixed: Int,
    val empty: Boolean,
    /** The text that was checked (for the optional on-device AI proofreading). */
    val text: String = "",
)

/** One entry in the suggestion strip. */
data class StripItem(val label: String, val action: KeyAction, val emphasized: Boolean = false)

/** Everything the keyboard view needs to draw. */
data class KeyboardUiState(
    val layout: KeyboardLayout,
    val layoutId: LayoutId,
    val page: KeyboardPage,
    val shift: ShiftState,
    val field: FieldInfo,
    /** The Avro word being typed, if any. */
    val composition: Composition? = null,
    /** The suggestion strip; empty when there is nothing to suggest (or suggestions are off). */
    val strip: List<StripItem> = emptyList(),
    /** A panel shown in place of the keys. */
    val panel: Panel = Panel.NONE,
    /** Editing panel: cursor moves currently extend the selection. */
    val selecting: Boolean = false,
    /** Space-bar and backspace swipe gestures are on. */
    val cursorSwipe: Boolean = true,
    /** Holding space for trackpad mode, moving up or down moves between lines. */
    val trackpadVertical: Boolean = true,
    /** How long a key is held before its long-press characters appear, in ms. */
    val longPressMs: Long = DEFAULT_LONG_PRESS_MS.toLong(),
    /** Custom gesture shortcuts, or null when off (and in password fields). */
    val gestures: GestureDispatcher? = null,
    /** The emoji tab to open on (from a GIF/sticker shortcut), or null. */
    val emojiTab: EmojiTab? = null,
    /** Next-letter probabilities for predictive touch targets, or null (off, or not ready). */
    val nextChars: NextChars? = null,
    /** Learn this user's touch offsets now (setting on, not a private field). */
    val touchCalibration: Boolean = false,
    /** While non-null, the keys type into this panel's search box ([searchQuery]). */
    val search: Panel? = null,
    /** While non-null, the keys type into the translate box above the toolbar. */
    val translate: TranslateUi? = null,
    /** While non-null, the Check card (spelling, punctuation, grammar) is open above the toolbar. */
    val check: CheckUi? = null,
    val searchQuery: String = "",
    /** The last search for the panel being shown ("" for none). */
    val panelQuery: String = "",
    val toolbar: List<ToolbarItem> = ToolbarItem.DEFAULT,
    /** BCP-47 tag voice typing should listen for, e.g. "bn-BD". */
    val voiceLanguageTag: String = "en-US",
    val emojiSkinTone: Int = 0,
    /** Styled typing (𝐁𝐨𝐥𝐝, 𝐼𝑡𝑎𝑙𝑖𝑐 …) turned on from the editing panel, or null. */
    val textStyle: com.yeamin.jolpai.core.tools.TextStyle? = null,
)

/**
 * Runs suggestion work. The service runs it on a background thread and delivers the result
 * on the main thread; tests run it immediately.
 */
/** Runs work off the main thread (or inline in tests) and delivers the result on the main thread. */
interface BackgroundWork {
    fun <T> run(work: () -> T, deliver: (T) -> Unit)

    companion object {
        val IMMEDIATE = object : BackgroundWork {
            override fun <T> run(work: () -> T, deliver: (T) -> Unit) = deliver(work())
        }
    }
}

fun interface SuggestionRunner {
    fun run(work: () -> WordSuggestions, deliver: (WordSuggestions) -> Unit)

    companion object {
        val IMMEDIATE = SuggestionRunner { work, deliver -> deliver(work()) }
    }
}

/**
 * The keyboard's state machine: active layout, page, shift state, which processor receives
 * key presses, and the suggestion strip. Pure Kotlin on top of [TextEditor], so it is
 * unit-tested on the PC.
 *
 * All calls happen on the main thread; text is committed synchronously in [onKey].
 * Suggestions for English and National are computed through [runner] (in the background on a
 * phone); Avro candidates are computed inline because they decide the text shown while typing.
 *
 * @param suggester null disables suggestions, autocorrect and learning entirely.
 * @param avroEngine loaded lazily; call [preloadAvro] from a background thread to keep the
 *   first Avro key press fast.
 * @param snippetFor the snippet text for a shortcut the user defined, or null.
 * @param wallClock the time of day, for date/time insertion ([clock] is a monotonic clock).
 */
class KeyboardController(
    private val appEditor: TextEditor,
    private val clock: () -> Long,
    private val suggester: Suggester? = null,
    private val runner: SuggestionRunner = SuggestionRunner.IMMEDIATE,
    private val avroEngine: Lazy<PhoneticEngine> = lazy { PhoneticEngine(AvroGrammar.load()) },
    private val snippetFor: (String) -> String? = { null },
    private val wallClock: () -> Long = System::currentTimeMillis,
    /** Emoji to suggest for a typed word (already filtered to ones the device can draw). */
    private val emojiFor: (String) -> List<String> = { emptyList() },
    /** Emoji matching a search query, for searching from the emoji panel. */
    private val emojiSearch: (String) -> List<String> = { emptyList() },
    /** Emoji that fit a sentence's words (oldest first), by meaning; called in the background. */
    private val emojiPredict: (List<String>) -> List<String> = { emptyList() },
    /** Where next-letter predictions for predictive touch are computed. */
    private val touchRunner: BackgroundWork = BackgroundWork.IMMEDIATE,
    /** Where the check card's next-word-model pass runs (word choice, spelling by context). */
    private val checkRunner: BackgroundWork = BackgroundWork.IMMEDIATE,
) {
    /** Called when the user explicitly picks a layout, so it can be saved as the last-used one. */
    var onLayoutChosen: ((LayoutId) -> Unit)? = null

    /** Called when an emoji is inserted, so it can be added to the recents. */
    var onEmojiUsed: ((String) -> Unit)? = null

    /**
     * The phone's clipboard, for cut / copy / paste inside the keyboard's own boxes (translate,
     * search): those boxes aren't app text fields, so the app can't do it for them.
     */
    var boxClipboard: BoxClipboard? = null

    /** Typing statistics (counts only), or null. Never told about password, private or box typing. */
    var stats: com.yeamin.jolpai.core.stats.TypingEvents? = null

    private val countStats: Boolean get() = stats != null && !typingInBox && !editorField.isSensitive

    /** Typing speed: bursts of key presses, reported to [stats] when each ends. */
    private val speed = com.yeamin.jolpai.core.stats.SpeedMeter { typed, deleted, millis -> stats?.typingBurst(typed, deleted, millis) }

    // ------------------------------------------------------------------ search mode

    /** The search box of a panel; the keys type here while [searchPanel] is set. */
    private val searchBuffer = InMemoryEditor()
    private var searchPanel: Panel? = null
    private val panelQueries = HashMap<Panel, String>()

    // ------------------------------------------------------------------ translate mode

    /** The translate box; the keys type here while [translating]. */
    private val translateBuffer = InMemoryEditor()
    private var translating = false
    private var translateToBangla = true
    private var layoutBeforeTranslate: LayoutId? = null

    /** The latest translation and the text it belongs to (set by the service, which translates). */
    private var translatedSource: String? = null
    private var translatedText: String? = null

    // ------------------------------------------------------------------ check mode

    private var checking = false
    private var checkIssues: List<com.yeamin.jolpai.suggest.TextIssue> = emptyList()
    private var checkStale = false
    private var checkFixed = 0
    private var checkEmpty = false
    private val checkIgnored = HashSet<String>()

    /** Where the checked text starts in the field (absolute position), and the text itself. */
    private var checkBase = 0
    private var checkText = ""

    /** Typing goes into one of the keyboard's own boxes, not the app. */
    private val typingInBox: Boolean get() = searchPanel != null || translating

    /** Where key presses go: the app's text field, a panel's search box, or the translate box. */
    private val editor: TextEditor
        get() = when {
            searchPanel != null -> searchBuffer
            translating -> translateBuffer
            else -> appEditor
        }

    /**
     * The service reports a translation of [source] (null when it failed); it is shown, and
     * Enter inserts it, only while the box still holds that same text.
     */
    fun setTranslation(source: String, text: String?) {
        if (!translating) return
        translatedSource = source
        translatedText = text
        publish()
    }

    /** Voice typing is showing words that aren't final yet. */
    private var voiceComposing = false

    private var settings = KeyboardSettings()
    private var settingsLoaded = false
    private var editorField = FieldInfo.DEFAULT
    private var layouts = layoutFactory(settings)

    /** The layout the user chose. */
    private var userLayout = LayoutId.ENGLISH

    /** The layout shown now; differs from [userLayout] in email/URL/password fields. */
    private var activeLayout = LayoutId.ENGLISH
    private var page = KeyboardPage.LETTERS
    private var shift = ShiftState.OFF
    private var shiftIsAutomatic = false
    private var lastShiftTap = Long.MIN_VALUE

    // ------------------------------------------------------------------ suggestion state

    private var wordContext = WordContext.EMPTY
    private var wordSuggestions = WordSuggestions.EMPTY
    private var nextWords: List<String> = emptyList()
    /** Emoji that fit the sentence, shown after a space with the next words. */
    private var nextEmoji: List<String> = emptyList()

    /** The last space autocorrected [original] to [replacement]; Backspace right now undoes it. */
    private data class AutoCorrection(val original: String, val replacement: String)
    private var undoableCorrection: AutoCorrection? = null

    /** A word the user un-corrected; it is not autocorrected again while being typed. */
    private var rejectedWord: String? = null

    /** An unknown word the user just chose as typed: offer to add it to the dictionary. */
    private var offerToAdd: Pair<String, Language>? = null

    /** A calculation typed before the cursor ("12*3="), offered in the strip. */
    private var calculation: Calculator.Result? = null

    /** A snippet shortcut typed before the cursor, offered in the strip and expanded by space. */
    private var pendingSnippet: String? = null

    // ------------------------------------------------------------------ panels and editing

    private var panel = Panel.NONE
    private var selecting = false

    /** Styled typing from the editing panel: typed English letters come out in this style. */
    private var textStyle: com.yeamin.jolpai.core.tools.TextStyle? = null

    private val processors: Map<LayoutId, InputProcessor> = mapOf(
        LayoutId.ENGLISH to EnglishProcessor { settings.doubleSpacePeriod },
        LayoutId.AVRO to AvroProcessor(
            engine = { avroEngine.value },
            doubleSpaceEnabled = { settings.doubleSpacePeriod },
            candidatesFor = { term ->
                if (!suggestionsEnabled) null
                // With the Bangla model, the words before this one rank the candidates.
                else if (settings.smartPredictions) suggester?.avro(term, SuggestOptions(context = banglaContextBefore().sentence, useModel = true))
                else suggester?.avro(term)
            },
        ),
        LayoutId.NATIONAL to NationalProcessor({ settings.doubleSpacePeriod }),
        // Probhat types in the same logical order as the National layout, so it shares its composer.
        LayoutId.PROBHAT to NationalProcessor({ settings.doubleSpacePeriod }),
    )

    private val processor: InputProcessor get() = processors.getValue(activeLayout)

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<KeyboardUiState> = _state.asStateFlow()

    /** Loads the Avro rules. Safe to call from any thread. */
    fun preloadAvro() {
        avroEngine.value
    }

    fun updateSettings(newSettings: KeyboardSettings) {
        val first = !settingsLoaded
        val layoutsChanged = newSettings.enabledLayouts != settings.enabledLayouts || newSettings.numberRow != settings.numberRow
        settings = newSettings
        settingsLoaded = true
        if (layoutsChanged) layouts = layoutFactory(newSettings)
        if (first || userLayout !in newSettings.enabledLayouts) {
            userLayout = newSettings.startLayout
        }
        if (first || activeLayout !in newSettings.enabledLayouts) {
            processor.finishComposing(editor)
            activeLayout = layoutForField(editorField)
        }
        updateAutoShift()
        refreshSuggestions(force = true)
        publish()
    }

    fun onStartInput(newField: FieldInfo, restarting: Boolean) {
        editorField = newField
        if (!restarting) {
            speed.flush()
            textStyle = null // styled typing is for the field it was turned on in
            endTranslate()
            endCheck()
            processors.values.forEach { it.reset() }
            if (settingsLoaded && !settings.rememberLastLayout) userLayout = settings.startLayout
            activeLayout = layoutForField(newField)
            page = when (newField.kind) {
                FieldKind.NUMBER, FieldKind.DATETIME -> KeyboardPage.NUMBER
                FieldKind.PHONE -> KeyboardPage.PHONE
                else -> KeyboardPage.LETTERS
            }
            shift = ShiftState.OFF
            shiftIsAutomatic = false
            panel = Panel.NONE
            selecting = false
        }
        undoableCorrection = null
        rejectedWord = null
        offerToAdd = null
        updateAutoShift()
        refreshSuggestions(force = true)
        publish()
    }

    fun onFinishInput() {
        speed.flush()
        processor.finishComposing(editor)
        processors.values.forEach { it.reset() }
        wordContext = WordContext.EMPTY
        wordSuggestions = WordSuggestions.EMPTY
        nextWords = emptyList()
        nextEmoji = emptyList()
        publish()
    }

    /** Dictionaries finished loading: recompute suggestions for what is on screen. */
    fun onSuggestionDataLoaded() {
        refreshSuggestions(force = true)
        publish()
    }

    /**
     * The cursor or selection changed. [composingStart]/[composingEnd] are the app's composing
     * region (-1 if none).
     *
     * Updates arrive asynchronously, so one may describe the text as it was a few key presses
     * ago. If it doesn't match the word we're composing, we check the real text before the
     * cursor before deciding the user moved the cursor away.
     */
    fun onSelectionChanged(selStart: Int, selEnd: Int, composingStart: Int, composingEnd: Int) {
        val composition = processor.composition
        if (composition != null) {
            val consistent = selStart == selEnd && composingStart >= 0 && selEnd == composingEnd
            if (!consistent) {
                val cursorAfterWord = !editor.hasSelection() &&
                    editor.textBeforeCursor(composition.bangla.length).toString() == composition.bangla
                if (!cursorAfterWord) {
                    processor.onCursorMoved(editor)
                    undoableCorrection = null
                    updateAutoShift()
                    refreshSuggestions(force = true)
                    publish()
                    return
                }
            }
            return
        }
        val shiftChanged = updateAutoShift()
        val contextChanged = refreshSuggestions(force = false)
        if (shiftChanged || contextChanged) publish()
    }

    fun onKey(action: KeyAction) {
        val undo = undoableCorrection
        undoableCorrection = null
        if (action !is KeyAction.Shift && action !is KeyAction.SwitchPage) offerToAdd = null
        if (countStats) when (action) {
            is KeyAction.Text, KeyAction.Space, KeyAction.Enter -> speed.typed(clock())
            KeyAction.Delete -> speed.deleted(clock())
            else -> Unit
        }

        when (action) {
            is KeyAction.Text -> {
                val style = textStyle
                if (style == null || !styledTyping(style, action.text)) {
                    if (endsWord(action.text)) finishWord(autoCorrect = true)
                    processor.onText(action.text, editor)
                }
                if (shift == ShiftState.ON) {
                    shift = ShiftState.OFF
                    shiftIsAutomatic = false
                }
            }
            KeyAction.Space -> {
                val composition = processor.composition
                val shortcut = pendingSnippet
                if (shortcut != null && snippetFor(shortcut) != null) {
                    expandSnippet(shortcut)
                } else if (composition != null) {
                    val previous = banglaContextBefore()
                    processor.onSpace(editor, clock())
                    learnAvro(composition, composition.bangla, previous)
                } else {
                    val correction = finishWord(autoCorrect = true)
                    processor.onSpace(editor, clock())
                    undoableCorrection = correction
                }
            }
            KeyAction.Delete -> {
                if (undo != null && revert(undo)) {
                    // Handled: the autocorrection was undone.
                } else {
                    processor.onDelete(editor)
                }
            }
            KeyAction.Enter -> if (searchPanel != null) {
                endSearch(keepQuery = true)
            } else if (translating) {
                processor.finishComposing(translateBuffer)
                val source = translateBuffer.text
                val text = translatedText?.takeIf { translatedSource == source && it.isNotBlank() }
                if (text != null) commitTranslation(text)
            } else {
                val composition = processor.composition
                if (composition != null) {
                    val previous = banglaContextBefore()
                    processor.finishComposing(editor)
                    learnAvro(composition, composition.bangla, previous)
                } else {
                    finishWord(autoCorrect = true)
                }
                editor.performEnter(editorField)
            }
            is KeyAction.CommitCandidate -> commitCandidate(action)
            is KeyAction.AddToDictionary -> suggester?.history?.addPersonal(action.language, action.word)
            KeyAction.Shift -> onShift()
            is KeyAction.SwitchPage -> {
                page = action.page
                shift = ShiftState.OFF
                shiftIsAutomatic = false
            }
            KeyAction.NextLayout -> {
                val enabled = settings.enabledLayouts
                val next = enabled[(enabled.indexOf(activeLayout) + 1).mod(enabled.size)]
                selectLayout(next)
            }
            KeyAction.PreviousLayout -> {
                val enabled = settings.enabledLayouts
                selectLayout(enabled[(enabled.indexOf(activeLayout) - 1).mod(enabled.size)])
            }
            is KeyAction.SelectLayout -> selectLayout(action.id)
            is KeyAction.PasteText -> {
                endComposing()
                editor.commit(action.text)
            }
            is KeyAction.InsertSnippet -> {
                endComposing()
                val start = editor.selectionStart()
                val filled = fillSnippet(action.text)
                editor.commit(filled.text)
                filled.cursor?.let { at -> if (start >= 0) editor.setSelection(start + at, start + at) }
            }
            is KeyAction.SetTextStyle -> setTextStyle(action.style)
            is KeyAction.Shortcut -> runShortcut(action)
            is KeyAction.OpenEmojiTab -> {
                if (searchPanel != null) endSearch(keepQuery = false)
                endComposing()
                emojiTab = action.tab
                panel = Panel.EMOJI
            }
            is KeyAction.ReplaceBefore -> {
                endComposing()
                editor.replaceBefore(action.count, action.text)
            }
            is KeyAction.ExpandSnippet -> expandSnippet(action.shortcut)
            is KeyAction.Edit -> {
                endComposing()
                edit(action.command)
            }
            is KeyAction.SelectBack -> selectBack(action.words)
            is KeyAction.EndSelectBack -> endSelectBack(action.delete)
            is KeyAction.MoveCursor -> {
                endComposing()
                val command = if (action.steps < 0) EditorCommand.LEFT else EditorCommand.RIGHT
                repeat(kotlin.math.abs(action.steps)) { editor.run(command, extendSelection = selecting) }
            }
            is KeyAction.InsertDateTime -> {
                endComposing()
                editor.commit(formatDateTime(action.kind))
            }
            is KeyAction.ShowPanel -> {
                emojiTab = null
                if (searchPanel != null) endSearch(keepQuery = false)
                if (checking && action.panel != Panel.NONE) endCheck()
                if (translating && action.panel != Panel.NONE) endTranslate()
                if (action.panel != Panel.NONE) endComposing()
                panel = action.panel
                if (panel != Panel.EDITING) selecting = false
            }
            is KeyAction.InsertEmoji -> {
                val searching = searchPanel != null
                if (searching) endSearch(keepQuery = true)
                endComposing()
                val before = appEditor.textBeforeCursor(1)
                val needsSpace = action.afterWord && before.isNotEmpty() && !before[0].isWhitespace()
                appEditor.commit(if (needsSpace) " ${action.emoji}" else action.emoji)
                onEmojiUsed?.invoke(action.emoji)
                if (countStats) stats?.emoji()
            }
            is KeyAction.StartSearch -> {
                if (translating) endTranslate()
                if (checking) endCheck()
                endComposing()
                searchPanel = action.panel
                searchBuffer.clear()
                panel = Panel.NONE
                page = KeyboardPage.LETTERS
                shift = ShiftState.OFF
            }
            is KeyAction.EndSearch -> when {
                searchPanel != null -> endSearch(action.keepQuery)
                !action.keepQuery -> panelQueries.remove(panel) // the panel's "✕ clear search"
            }
            is KeyAction.VoicePartial -> {
                if (!voiceComposing) {
                    endComposing()
                    voiceComposing = true
                }
                appEditor.setComposing(action.text)
            }
            is KeyAction.VoiceFinal -> {
                if (!voiceComposing) endComposing()
                voiceComposing = false
                val text = action.text.trim()
                if (text.isEmpty()) appEditor.finishComposing() else appEditor.commit("$text ")
            }
            KeyAction.StartCheck -> {
                if (searchPanel != null) endSearch(keepQuery = false)
                if (translating) endTranslate()
                endComposing()
                panel = Panel.NONE
                checking = true
                checkFixed = 0
                checkIgnored.clear()
                runCheck()
            }
            KeyAction.EndCheck -> endCheck()
            KeyAction.Recheck -> if (checking) {
                endComposing()
                runCheck()
            }
            is KeyAction.FixIssue -> if (checking) {
                endComposing()
                if (checkStale) runCheck()
                // The card sends the issue with the word the user picked as its replacement: the
                // suggested fix or one of the alternatives ("takl" → talk / tail / tall).
                val chosen = action.issue.replacement
                val current = checkIssues.firstOrNull { it == action.issue }
                    ?: checkIssues.firstOrNull { it.start == action.issue.start && it.original == action.issue.original && chosen in it.choices }
                    ?: checkIssues.firstOrNull { it.original == action.issue.original && chosen in it.choices }
                if (current != null && chosen in current.choices) {
                    if (current.kind == com.yeamin.jolpai.suggest.IssueKind.SPELLING) learnCheckChoice(current, chosen)
                    applyFixes(listOf(current.copy(replacement = chosen)))
                    checkFixed++
                }
                runCheck()
            }
            KeyAction.FixAllIssues -> if (checking) {
                endComposing()
                if (checkStale) runCheck()
                // A fix can uncover the next one ("its better then"): check again, up to 3 rounds.
                var rounds = 0
                while (checkIssues.isNotEmpty() && rounds < 3) {
                    checkFixed += checkIssues.size
                    applyFixes(checkIssues)
                    runCheck()
                    rounds++
                }
            }
            is KeyAction.ApplyRewrite -> if (checking) {
                endComposing()
                if (checkStale) runCheck()
                val original = action.original
                // Rewrite the part that was proofread (the checked text ends where it ends).
                val at = checkText.lastIndexOf(original)
                if (original.isNotEmpty() && at >= 0 && action.replacement != original) {
                    applyFixes(listOf(com.yeamin.jolpai.suggest.TextIssue(at, at + original.length, original, action.replacement, com.yeamin.jolpai.suggest.IssueKind.GRAMMAR, "AI suggestion")))
                    checkFixed++
                }
                runCheck()
            }
            is KeyAction.IgnoreIssue -> if (checking) {
                checkIgnored += action.issue.original
                checkIssues = checkIssues.filter { it.original != action.issue.original }
                // A "misspelled" word the user keeps (a name, a rare word) joins their dictionary,
                // so it is never flagged again, in the checker or in suggestions.
                if (action.issue.kind == com.yeamin.jolpai.suggest.IssueKind.SPELLING) {
                    val word = action.issue.original.trim()
                    val lang = if (word.any { it in 'ঀ'..'৿' }) Language.BANGLA else Language.ENGLISH
                    if (word.isNotEmpty()) suggester?.history?.addPersonal(lang, word)
                }
            }
            is KeyAction.StartTranslate -> startTranslate(action.toBangla)
            KeyAction.EndTranslate -> endTranslate()
            KeyAction.SwapTranslate -> swapTranslate()
            KeyAction.ApplyTranslateSpelling -> if (translating) fixTranslate(translateIssues(translateBuffer.text))
            is KeyAction.FixTranslateIssue -> if (translating) {
                val current = translateIssues(translateBuffer.text)
                fixTranslate(listOfNotNull(current.firstOrNull { it == action.issue } ?: current.firstOrNull { it.original == action.issue.original && it.replacement == action.issue.replacement }))
            }
            is KeyAction.IgnoreTranslateIssue -> if (translating) translateIgnored += action.issue.original
            KeyAction.ClearTranslate -> if (translating) {
                processor.finishComposing(translateBuffer)
                processor.reset()
                translateBuffer.clear()
            }
            is KeyAction.CommitTranslation -> if (translating && action.text.isNotBlank()) {
                processor.finishComposing(translateBuffer)
                commitTranslation(action.text)
            }
            is KeyAction.Host -> Unit // handled by the service before it reaches here
        }
        if (checking && !action.isCheckAction()) checkStale = true
        updateAutoShift()
        refreshSuggestions(force = false)
        publish()
    }

    private fun KeyAction.isCheckAction() = this is KeyAction.StartCheck || this is KeyAction.Recheck || this is KeyAction.ApplyRewrite ||
        this is KeyAction.FixIssue || this is KeyAction.FixAllIssues || this is KeyAction.IgnoreIssue ||
        this is KeyAction.EndCheck || this is KeyAction.ShowPanel

    /** Leaves search mode and shows the panel again, with the typed query if [keepQuery]. */
    private fun endSearch(keepQuery: Boolean) {
        val target = searchPanel ?: return
        processor.finishComposing(searchBuffer)
        processors.values.forEach { it.reset() }
        panelQueries[target] = if (keepQuery) searchBuffer.text.trim() else ""
        searchPanel = null
        searchBuffer.clear()
        panel = target
    }

    /**
     * Reads the text around the cursor (up to [CHECK_WINDOW] characters each way, starting at a
     * sentence boundary) and checks it. Positions are kept as absolute offsets in the field, so
     * fixes can be applied anywhere in it.
     */
    private fun runCheck() {
        val before = appEditor.textBeforeCursor(CHECK_WINDOW).toString()
        val after = appEditor.textAfterCursor(CHECK_WINDOW).toString()
        var cut = 0
        if (before.length >= CHECK_WINDOW) {
            // Start at a sentence boundary so the first words aren't judged out of context.
            val boundary = before.indexOfFirst { it in ".!?।\n" }
            cut = if (boundary in 0 until before.length - 1) boundary + 1 else 0
        }
        val text = before.substring(cut) + after
        val cursor = appEditor.selectionStart().takeIf { it >= 0 } ?: before.length
        checkBase = cursor - before.length + cut
        checkText = text
        checkEmpty = text.isBlank()
        val checker = suggester?.checker
        val endsAtFieldEnd = after.length < CHECK_WINDOW
        // The rules first, at once; then the next-word model (word choice, spelling by context) in
        // the background, replacing the list if the text is still the same.
        checkIssues = checker?.check(text, checkIgnored, endsAtFieldEnd).orEmpty()
        checkStale = false
        val generation = ++checkGeneration
        if (checker != null && settings.smartPredictions && checker.canUseModel) {
            val ignored = checkIgnored.toSet()
            checkRunner.run(work = { checker.check(text, ignored, endsAtFieldEnd, useModel = true) }) { issues ->
                if (generation == checkGeneration && checking && !checkStale && checkText == text) {
                    checkIssues = issues.filter { it.original !in checkIgnored }
                    publish()
                }
            }
        }
    }

    /** Bumped by every check, so a slower model pass never overwrites a newer result. */
    private var checkGeneration = 0

    /** Applies [issues] (positions in [checkText]) from last to first, then puts the cursor back. */
    private fun applyFixes(issues: List<com.yeamin.jolpai.suggest.TextIssue>) {
        var cursor = appEditor.selectionStart().takeIf { it >= 0 } ?: (checkBase + checkText.length)
        issues.sortedByDescending { it.start }.forEach { issue ->
            val start = checkBase + issue.start
            val end = checkBase + issue.end
            appEditor.setSelection(start, end)
            appEditor.commit(issue.replacement)
            val delta = issue.replacement.length - (issue.end - issue.start)
            cursor = when {
                cursor >= end -> cursor + delta
                cursor > start -> start + issue.replacement.length
                else -> cursor
            }
        }
        appEditor.setSelection(cursor, cursor)
    }

    private fun endCheck() {
        if (!checking) return
        checking = false
        checkIssues = emptyList()
        checkIgnored.clear()
        checkStale = false
        checkFixed = 0
    }

    /**
     * Opens the translate box. The keys switch to the language being typed (English keys for
     * English to Bangla, the user's Bangla layout for Bangla to English) without changing the
     * saved layout; closing the box brings the previous layout back.
     */
    private fun startTranslate(toBangla: Boolean) {
        if (searchPanel != null) endSearch(keepQuery = false)
        if (checking) endCheck()
        endComposing()
        panel = Panel.NONE
        if (!translating) layoutBeforeTranslate = activeLayout
        translating = true
        translateToBangla = toBangla
        translateBuffer.clear()
        translatedSource = null
        translatedText = null
        useTranslateLayout()
    }

    private fun endTranslate() {
        if (!translating) return
        translateIgnored.clear()
        processor.finishComposing(translateBuffer)
        processors.values.forEach { it.reset() }
        translating = false
        translateBuffer.clear()
        translatedSource = null
        translatedText = null
        layoutBeforeTranslate?.let { if (it in settings.enabledLayouts) activeLayout = it }
        layoutBeforeTranslate = null
        page = KeyboardPage.LETTERS
        shift = ShiftState.OFF
        shiftIsAutomatic = false
    }

    /** Turns the direction round; what was translated becomes the text to translate back. */
    private fun swapTranslate() {
        if (!translating) return
        processor.finishComposing(translateBuffer)
        val previous = translatedText?.takeIf { translatedSource == translateBuffer.text }
        translateToBangla = !translateToBangla
        translateBuffer.clear()
        if (!previous.isNullOrBlank()) translateBuffer.commit(previous)
        translatedSource = null
        translatedText = null
        useTranslateLayout()
    }

    private fun useTranslateLayout() {
        val bangla = layoutBeforeTranslate?.takeIf { it != LayoutId.ENGLISH }
            ?: settings.enabledLayouts.firstOrNull { it != LayoutId.ENGLISH }
        val wanted = if (translateToBangla) LayoutId.ENGLISH else bangla
        processors.values.forEach { it.reset() }
        if (wanted != null && wanted in settings.enabledLayouts) activeLayout = wanted
        page = KeyboardPage.LETTERS
        shift = ShiftState.OFF
        shiftIsAutomatic = false
    }

    /** Suggestions the user dismissed in the translate box (cleared when it closes). */
    private val translateIgnored = HashSet<String>()

    /**
     * The checker's suggestions for the finished words in the translate box (the word still
     * being typed is left alone). Positions are in [text].
     */
    private fun translateIssues(text: String): List<com.yeamin.jolpai.suggest.TextIssue> {
        if (text.isBlank()) return emptyList()
        val checker = suggester?.checker ?: return emptyList()
        val done = if (text.last().isWhitespace() || text.last() in ".,!?।") text.length else text.trimEnd().lastIndexOf(' ').let { if (it < 0) 0 else it }
        if (done == 0) return emptyList()
        return checker.check(text.substring(0, done), translateIgnored, endsAtFieldEnd = false)
    }

    /** Applies [issues] to the translate box (last first, so earlier positions stay valid). */
    private fun fixTranslate(issues: List<com.yeamin.jolpai.suggest.TextIssue>) {
        if (issues.isEmpty()) return
        processor.finishComposing(translateBuffer)
        val sb = StringBuilder(translateBuffer.text)
        issues.sortedByDescending { it.start }.forEach { if (it.end <= sb.length) sb.replace(it.start, it.end, it.replacement) }
        processors.values.forEach { it.reset() }
        translateBuffer.clear()
        translateBuffer.commit(sb.toString())
        wordSuggestions = WordSuggestions.EMPTY
    }

    /**
     * Cut, copy and paste in the translate or search box (it has no selection, so cut and copy
     * take the whole text). Pasted text goes in at the end, on one line.
     */
    private fun boxEdit(command: EditCommand) {
        val clip = boxClipboard ?: return
        val box = if (searchPanel != null) searchBuffer else translateBuffer
        processor.finishComposing(box)
        when (command) {
            EditCommand.COPY -> box.text.takeIf { it.isNotBlank() }?.let { clip.copy(it) }
            EditCommand.CUT -> box.text.takeIf { it.isNotBlank() }?.let {
                clip.copy(it)
                processors.values.forEach { p -> p.reset() }
                box.clear()
            }
            EditCommand.PASTE -> clip.paste()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotBlank() }?.let { box.commit(it) }
            else -> Unit
        }
        selecting = false
    }

    /** Puts the translation into the app (with a space either side as needed) and empties the box. */
    private fun commitTranslation(text: String) {
        val before = appEditor.textBeforeCursor(1)
        val space = before.isNotEmpty() && !before[0].isWhitespace()
        appEditor.commit((if (space) " " else "") + text.trim() + " ")
        processors.values.forEach { it.reset() }
        translateBuffer.clear()
        translatedSource = null
        translatedText = null
    }

    // ------------------------------------------------------------------ editing, snippets, tools

    /** Commits an Avro word in progress (and learns it), so an edit acts on settled text. */
    private fun endComposing() {
        val composition = processor.composition
        if (composition != null) {
            val previous = banglaContextBefore()
            processor.finishComposing(editor)
            learnAvro(composition, composition.bangla, previous)
        }
        processors.values.forEach { it.reset() }
    }

    // Backspace swipe: the cursor where it started and the text before it, so the range can grow and shrink.
    private var backAnchor = -1
    private var backText = ""

    private fun selectBack(words: Int) {
        if (backAnchor < 0) {
            endComposing()
            if (editor.selectionStart() < 0) return
            backAnchor = editor.selectionStart()
            backText = editor.textBeforeCursor(CONTEXT_CHARS).toString()
        }
        var length = 0
        repeat(words) {
            val n = TextUnits.wordLengthBefore(backText.substring(0, backText.length - length))
            if (n == 0) return@repeat
            length += n
        }
        editor.setSelection(backAnchor - length, backAnchor)
    }

    private fun endSelectBack(delete: Boolean) {
        if (backAnchor < 0) return
        if (delete && editor.hasSelection()) editor.deleteBackward() else editor.setSelection(backAnchor, backAnchor)
        backAnchor = -1
        backText = ""
    }

    /** Called for gesture actions the keyboard service carries out (open settings, mode toggles). */
    var onHostCommand: ((HostCommand) -> Unit)? = null

    /** The emoji tab a shortcut asked for, until the panel closes. */
    private var emojiTab: EmojiTab? = null

    /** Runs a gesture shortcut through the existing actions; unavailable ones do nothing. */
    private fun runShortcut(shortcut: KeyAction.Shortcut) {
        if (!shortcut.action.available) return
        shortcut.action.toKeyActions(shortcut.text, activeLayout).forEach { a ->
            if (a is KeyAction.Host) onHostCommand?.invoke(a.command) else onKey(a)
        }
    }

    private fun edit(command: EditCommand) {
        if (typingInBox && (command == EditCommand.CUT || command == EditCommand.COPY || command == EditCommand.PASTE)) {
            boxEdit(command)
            return
        }
        when (command) {
            EditCommand.TOGGLE_SELECT -> selecting = !selecting
            EditCommand.SELECT_ALL -> editor.run(EditorCommand.SELECT_ALL)
            EditCommand.CUT -> { editor.run(EditorCommand.CUT); selecting = false }
            EditCommand.COPY -> { editor.run(EditorCommand.COPY); selecting = false }
            EditCommand.PASTE -> editor.run(EditorCommand.PASTE)
            EditCommand.UNDO -> editor.run(EditorCommand.UNDO)
            EditCommand.REDO -> editor.run(EditorCommand.REDO)
            EditCommand.LEFT -> editor.run(EditorCommand.LEFT, selecting)
            EditCommand.RIGHT -> editor.run(EditorCommand.RIGHT, selecting)
            EditCommand.UP -> editor.run(EditorCommand.UP, selecting)
            EditCommand.DOWN -> editor.run(EditorCommand.DOWN, selecting)
            EditCommand.LINE_START -> editor.run(EditorCommand.LINE_START, selecting)
            EditCommand.LINE_END -> editor.run(EditorCommand.LINE_END, selecting)
            EditCommand.DELETE_WORD -> {
                if (editor.hasSelection()) {
                    editor.deleteBackward()
                } else {
                    val n = TextUnits.wordLengthBefore(editor.textBeforeCursor(CONTEXT_CHARS))
                    if (n > 0) editor.replaceBefore(n, "") else editor.deleteBackward()
                }
            }
            EditCommand.SELECT_WORD_LEFT -> {
                val start = editor.selectionStart()
                val end = editor.selectionEnd()
                if (start < 0) return
                val n = TextUnits.wordLengthBefore(editor.textBeforeCursor(CONTEXT_CHARS))
                if (n > 0) editor.setSelection(start - n, end)
            }
            EditCommand.SELECT_WORD_RIGHT -> {
                val start = editor.selectionStart()
                val end = editor.selectionEnd()
                if (start < 0) return
                val n = TextUnits.wordLengthAfter(editor.textAfterCursor(CONTEXT_CHARS))
                if (n > 0) editor.setSelection(start, end + n)
            }
        }
    }

    /**
     * Replaces the typed snippet shortcut with the snippet text (its variables filled in),
     * followed by a space, or with the cursor where the snippet's {cursor} is.
     */
    private fun expandSnippet(shortcut: String) {
        val raw = snippetFor(shortcut) ?: return
        val filled = fillSnippet(raw)
        val composition = processor.composition
        if (composition != null && composition.roman == shortcut) {
            if (filled.cursor == null) {
                processor.commitCandidate(filled.text, editor, clock())
                return
            }
            processor.reset()
            editor.commit(filled.text) // replaces the composing shortcut
        } else {
            val before = editor.textBeforeCursor(shortcut.length)
            if (before.toString() != shortcut) return
            editor.replaceBefore(shortcut.length, filled.text)
            if (filled.cursor == null) {
                processor.onSpace(editor, clock())
                return
            }
        }
        // {cursor}: put the cursor there instead of adding a space.
        val end = editor.selectionStart()
        if (end >= 0) {
            val at = end - filled.text.length + filled.cursor
            editor.setSelection(at, at)
        }
    }

    /** A snippet's text with {date}, {time}, {bangla_date}, {clipboard} … filled in. */
    private fun fillSnippet(text: String): com.yeamin.jolpai.core.tools.SnippetVariables.Expanded =
        com.yeamin.jolpai.core.tools.SnippetVariables.expand(text, wallClock(), clipboard = { boxClipboard?.paste() })

    /** Today's date or the time, in Bangla (digits and month names) on the Bangla layouts. */
    private fun formatDateTime(kind: DateTimeKind): String {
        if (kind == DateTimeKind.BANGLA_DATE) {
            val today = java.time.Instant.ofEpochMilli(wallClock()).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            return com.yeamin.jolpai.core.tools.BanglaCalendar.format(today)
        }
        val locale = if (language == Language.BANGLA) Locale.forLanguageTag("bn-BD") else Locale.getDefault()
        val format = when (kind) {
            DateTimeKind.TIME -> DateFormat.getTimeInstance(DateFormat.SHORT, locale)
            else -> DateFormat.getDateInstance(DateFormat.LONG, locale)
        }
        return format.format(Date(wallClock()))
    }

    // ------------------------------------------------------------------ text styles

    /**
     * From the editing panel: with text selected, restyle it (and keep it selected so another
     * style can be tried); with nothing selected, turn styled typing on or off.
     */
    private fun setTextStyle(style: com.yeamin.jolpai.core.tools.TextStyle?) {
        val selected = if (editor.hasSelection()) editor.selectedText().toString() else ""
        if (selected.isNotEmpty()) {
            val start = editor.selectionStart()
            val styled = if (style == null) {
                com.yeamin.jolpai.core.tools.TextStyle.plain(selected)
            } else {
                com.yeamin.jolpai.core.tools.TextStyle.apply(style, selected)
            }
            editor.commit(styled)
            if (start >= 0) editor.setSelection(start, start + styled.length)
            return
        }
        endComposing()
        textStyle = if (style == textStyle) null else style
    }

    /**
     * Styled typing: commits [text] in [style] when it is something the style changes (English
     * letters and digits; any letter for strike and underline). Returns false to type it normally.
     */
    private fun styledTyping(style: com.yeamin.jolpai.core.tools.TextStyle, text: String): Boolean {
        if (text.isEmpty() || typingInBox || editorField.isSensitive) return false
        val cp = text.codePointAt(0)
        val markStyle = style == com.yeamin.jolpai.core.tools.TextStyle.STRIKE || style == com.yeamin.jolpai.core.tools.TextStyle.UNDERLINE
        // English letters and digits (Bangla can be struck or underlined by selecting it instead).
        val styleable = if (markStyle) Character.isLetterOrDigit(cp) && activeLayout == LayoutId.ENGLISH else
            text.length == 1 && com.yeamin.jolpai.core.tools.TextStyle.styles(style, cp)
        if (!styleable) return false
        endComposing()
        editor.commit(com.yeamin.jolpai.core.tools.TextStyle.apply(style, text))
        return true
    }

    // ------------------------------------------------------------------ words, autocorrect, learning

    private val language: Language
        get() = if (activeLayout == LayoutId.ENGLISH) Language.ENGLISH else Language.BANGLA

    /** Suggestions are shown in normal text and address fields, never in password or no-suggestion fields. */
    private val suggestionsEnabled: Boolean
        get() = if (translating) {
            // The translate box is plain text: suggestions help spell what is being translated.
            searchPanel == null && suggester != null && !editorField.isPassword
        } else {
            !typingInBox && suggester != null && settings.showSuggestions &&
                !editorField.isPassword && !editorField.noSuggestions &&
                (editorField.kind == FieldKind.TEXT || editorField.kind == FieldKind.URI)
        }

    /** Learning never happens in password, private (incognito) or no-suggestion fields. */
    private val learningEnabled: Boolean
        get() = !typingInBox && suggester != null && settings.personalizedLearning && !editorField.isSensitive &&
            !editorField.noSuggestions && editorField.kind == FieldKind.TEXT

    /**
     * Autocorrect as set for normal typing. Never in the translate box: there misspellings are
     * offered as suggestion chips instead, so nothing changes without the user's say.
     */
    private val autoCorrectEnabled: Boolean
        get() = suggestionsEnabled && activeLayout == LayoutId.ENGLISH && !translating &&
            settings.autoCorrect && editorField.kind == FieldKind.TEXT

    private fun suggestOptions(typed: String, ctx: WordContext = wordContext) = SuggestOptions(
        autoCorrect = autoCorrectEnabled && typed != rejectedWord,
        blockOffensive = settings.blockOffensiveWords,
        context = ctx.sentence,
        // Each language uses its own model (English, or Bangla for Avro, জাতীয় and প্রভাত).
        useModel = settings.smartPredictions,
    )

    /** The next-word model is used for English typing (it knows English only). */
    private val modelEnabled: Boolean
        get() = settings.smartPredictions && activeLayout == LayoutId.ENGLISH

    /** Emoji by meaning for a sentence, when emoji suggestions and the model are both on. */
    private fun predictEmoji(words: List<String>): List<String> =
        if (settings.emojiSuggestions && modelEnabled && words.isNotEmpty()) emojiPredict(words) else emptyList()

    private fun readContext(): WordContext {
        val before = editor.textBeforeCursor(CONTEXT_CHARS)
        return if (activeLayout == LayoutId.ENGLISH) WordContext.english(before) else WordContext.bangla(before)
    }

    private fun banglaContextBefore(): WordContext = WordContext.bangla(editor.textBeforeCursor(CONTEXT_CHARS))

    /** Characters typed on the letter pages that end a word (Avro handles its own punctuation). */
    private fun endsWord(text: String): Boolean =
        activeLayout != LayoutId.AVRO && text.length == 1 && text[0] in ".,!?;:।"

    /**
     * Called when a word is finished by space, Enter or punctuation (English and National).
     * Applies an English autocorrection if there is a confident one, and learns the word.
     * Returns the correction made, so a following Backspace can undo it.
     */
    private fun finishWord(autoCorrect: Boolean): AutoCorrection? {
        if (suggester == null || activeLayout == LayoutId.AVRO || editor.hasSelection()) return null
        val ctx = readContext()
        if (ctx.current.isEmpty()) return null
        var word = ctx.current
        var correction: AutoCorrection? = null
        if (autoCorrect && autoCorrectEnabled && word != rejectedWord) {
            val s = if (wordSuggestions.typed == word) wordSuggestions else suggester.english(word, suggestOptions(word, ctx))
            val fix = s.autoCorrect
            if (fix != null && fix != word) {
                editor.replaceBefore(word.length, fix)
                correction = AutoCorrection(word, fix)
                word = fix
                if (countStats) stats?.autocorrected()
            }
        }
        learn(language, word, ctx.previous, ctx.secondPrevious())
        if (word != rejectedWord) rejectedWord = null
        return correction
    }

    /** Backspace right after an autocorrection: put back what the user typed. */
    private fun revert(c: AutoCorrection): Boolean {
        val expected = c.replacement + " "
        if (editor.textBeforeCursor(expected.length).toString() != expected) return false
        editor.replaceBefore(expected.length, c.original)
        rejectedWord = c.original
        suggester?.history?.let { if (learningEnabled) it.recordWord(Language.ENGLISH, c.original, null) }
        return true
    }

    private fun commitCandidate(action: KeyAction.CommitCandidate) {
        val composition = processor.composition
        if (composition != null) {
            val previous = banglaContextBefore()
            if (countStats) speed.typed(clock())
            processor.commitCandidate(action.text, editor, clock())
            learnAvro(composition, action.text, previous)
            return
        }
        val ctx = readContext()
        if (countStats) {
            val saved = action.text.length - ctx.current.length
            stats?.suggestionPicked(saved)
            speed.typed(clock(), saved.coerceAtLeast(0) + 1) // the rest of the word, and the space
        }
        if (ctx.current.isNotEmpty()) editor.replaceBefore(ctx.current.length, action.text) else editor.commit(action.text)
        processor.onSpace(editor, clock())
        learn(language, action.text, ctx.previous, ctx.secondPrevious())
        if (action.verbatim) {
            rejectedWord = action.text
            val known = wordSuggestions.typed == action.text && wordSuggestions.typedIsKnown
            if (!known && learningEnabled) offerToAdd = action.text to language
        }
    }

    /**
     * The user picked [chosen] for a misspelling in the Check card: remember it with the word
     * before it, so the same choice ranks first next time ("want to takl" → talk).
     */
    private fun learnCheckChoice(issue: com.yeamin.jolpai.suggest.TextIssue, chosen: String) {
        if (!learningEnabled) return
        val history = suggester?.history ?: return
        val before = checkText.substring(0, issue.start.coerceIn(0, checkText.length)).trimEnd(' ')
        val previous = Regex("[\\p{L}\\p{M}']+$").find(before)?.value
        val language = if (chosen.any { it in 'ঀ'..'৿' }) Language.BANGLA else Language.ENGLISH
        history.recordWord(language, chosen, previous)
    }

    private fun learn(language: Language, word: String, previous: String?, previous2: String? = null) {
        if (countStats && word.isNotBlank()) stats?.word(language == Language.BANGLA)
        if (!learningEnabled || word.isBlank()) return
        suggester?.history?.recordWord(language, word, previous, previous2)
    }

    /**
     * The word before [WordContext.previous] in the same sentence, for learning three-word
     * sequences; null at a sentence's start or when the previous word isn't the sentence's last.
     */
    private fun WordContext.secondPrevious(): String? {
        val p = previous ?: return null
        if (sentence.size < 2 || !sentence.last().equals(p, ignoreCase = true)) return null
        return sentence[sentence.size - 2]
    }

    private fun learnAvro(composition: Composition, committed: String, before: WordContext) {
        val previous = before.previous
        if (countStats && committed.isNotBlank()) stats?.word(committed.any { it in 'ঀ'..'৿' })
        if (!learningEnabled) return
        val s = suggester ?: return
        s.recordAvroSelection(composition.roman, committed)
        val isBangla = committed.any { it in 'ঀ'..'৿' }
        s.history.recordWord(if (isBangla) Language.BANGLA else Language.ENGLISH, committed, previous, before.secondPrevious())
    }

    /**
     * Re-reads the word around the cursor and requests suggestions if it changed (or if
     * [force]). Returns true when the context changed.
     */
    private fun refreshSuggestions(force: Boolean): Boolean {
        val textPage = page == KeyboardPage.LETTERS || page == KeyboardPage.SYMBOLS
        val composition = processor.composition
        // The translate box also gets word suggestions (but not the calculator or snippets).
        val readText = toolsAllowed || translating && searchPanel == null
        val before: CharSequence = if (readText && textPage && composition == null) editor.textBeforeCursor(CONTEXT_CHARS) else ""
        val oldCalculation = calculation
        val oldSnippet = pendingSnippet
        calculation = if (toolsAllowed && before.isNotEmpty()) Calculator.resultFor(before) else null
        pendingSnippet = when {
            !toolsAllowed || !textPage -> null
            composition != null -> composition.roman.takeIf { snippetFor(it) != null }
            else -> TextUnits.lastToken(before).takeIf { it.length >= 2 && snippetFor(it) != null }
        }
        val toolsChanged = calculation != oldCalculation || pendingSnippet != oldSnippet

        val s = suggester
        if (s == null || !suggestionsEnabled || !textPage) {
            val changed = wordContext != WordContext.EMPTY || nextWords.isNotEmpty() || toolsChanged
            wordContext = WordContext.EMPTY
            wordSuggestions = WordSuggestions.EMPTY
            nextWords = emptyList()
            nextEmoji = emptyList()
            return changed
        }
        if (composition != null) return toolsChanged // Avro candidates come with the composition
        val ctx = if (activeLayout == LayoutId.ENGLISH) WordContext.english(before) else WordContext.bangla(before)
        val changed = ctx != wordContext || toolsChanged
        if (!changed && !force) return false
        wordContext = ctx
        if (ctx.current.isEmpty()) {
            wordSuggestions = WordSuggestions.EMPTY
            nextWords = emptyList()
            nextEmoji = emptyList()
            if (settings.nextWordPrediction) {
                // In the background: with the model this reads the whole sentence.
                val lang = language
                val options = suggestOptions("", ctx)
                val emojiWords = if (lang == Language.ENGLISH) ctx.sentence else emptyList()
                runner.run(
                    work = {
                        WordSuggestions("", s.nextWords(lang, ctx.previous, options = options), null, false, emoji = predictEmoji(emojiWords))
                    },
                    deliver = { result ->
                        if (wordContext == ctx) {
                            nextWords = result.candidates
                            nextEmoji = result.emoji
                            publish()
                        }
                    },
                )
            }
        } else {
            nextWords = emptyList()
            nextEmoji = emptyList()
            val typed = ctx.current
            val lang = language
            val options = suggestOptions(typed, ctx)
            runner.run(
                work = {
                    if (lang == Language.ENGLISH) {
                        val r = s.english(typed, options)
                        // Emoji by meaning, for the sentence with the word as it will be.
                        val emoji = if (r.typedIsKnown || r.autoCorrect != null) predictEmoji(ctx.sentence + (r.autoCorrect ?: typed)) else emptyList()
                        r.copy(emoji = emoji)
                    } else {
                        s.bangla(typed, options = options)
                    }
                },
                deliver = { result ->
                    if (wordContext.current == result.typed) {
                        wordSuggestions = result
                        publish()
                    }
                },
            )
        }
        return changed
    }

    /** Calculator and snippets work wherever suggestions could, even with suggestions turned off. */
    private val toolsAllowed: Boolean
        get() = !typingInBox && !editorField.isPassword && !editorField.noSuggestions &&
            (editorField.kind == FieldKind.TEXT || editorField.kind == FieldKind.URI)

    /** The strip; while styled typing is on, it starts with the style's name and ✕ to turn it off. */
    private fun buildStrip(): List<StripItem> {
        val items = stripItems()
        val style = textStyle ?: return items
        if (panel != Panel.NONE || searchPanel != null || translating || checking) return items
        return listOf(StripItem("${style.sample} ✕", KeyAction.SetTextStyle(null), emphasized = true)) + items.take(2)
    }

    private fun stripItems(): List<StripItem> {
        searchPanel?.let { target ->
            // Search mode: cancel, the query (tap = search), and live emoji results.
            val query = searchBuffer.text
            val results = if (target == Panel.EMOJI && query.isNotBlank()) {
                emojiSearch(query.trim()).take(SEARCH_RESULTS).map { StripItem(it, KeyAction.InsertEmoji(it)) }
            } else {
                emptyList()
            }
            return listOf(
                StripItem("✕", KeyAction.EndSearch(keepQuery = false)),
                StripItem("🔍 ${query.ifEmpty { "Search…" }}", KeyAction.EndSearch(keepQuery = true), emphasized = true),
            ) + results
        }
        if (panel != Panel.NONE) return emptyList()
        calculation?.let { c ->
            val insert = if (c.replace > 0) KeyAction.ReplaceBefore(c.replace, c.value) else KeyAction.PasteText(c.value)
            return listOf(StripItem("= ${c.value}", insert, emphasized = true))
        }
        val snippetItem = pendingSnippet?.let { shortcut ->
            snippetFor(shortcut)?.let { text ->
                val preview = text.replace('\n', ' ').let { if (it.length > 28) it.take(27) + "…" else it }
                StripItem("✎ $preview", KeyAction.ExpandSnippet(shortcut), emphasized = true)
            }
        }
        if (!suggestionsEnabled) return listOfNotNull(snippetItem)
        offerToAdd?.let { (word, lang) ->
            return listOf(StripItem("+ Add “$word” to dictionary", KeyAction.AddToDictionary(word, lang), emphasized = true))
        }
        processor.composition?.let { c ->
            val emoji = emojiSuggestions(c.bangla).map { StripItem(it, KeyAction.InsertEmoji(it, afterWord = true)) }
            return listOfNotNull(snippetItem) + c.candidates.mapIndexed { i, text ->
                StripItem(text, KeyAction.CommitCandidate(text, verbatim = text == c.roman), emphasized = i == c.selected && snippetItem == null)
            } + emoji
        }
        if (snippetItem != null) return listOf(snippetItem)
        val typed = wordContext.current
        if (typed.isEmpty()) {
            // At a sentence start (shift is on) the words are offered with a capital, as they'd be typed.
            val capital = activeLayout == LayoutId.ENGLISH && shift == ShiftState.ON
            val words = nextWords.map { if (capital) it.replaceFirstChar { c -> c.uppercaseChar() } else it }
            val emoji = nextEmoji.firstOrNull()
            return words.take(if (emoji != null) 2 else 3).map { StripItem(it, KeyAction.CommitCandidate(it)) } +
                listOfNotNull(emoji?.let { StripItem(it, KeyAction.InsertEmoji(it, afterWord = false)) })
        }

        val s = wordSuggestions.takeIf { it.typed == typed }
        val auto = s?.autoCorrect
        val typedLabel = if (s != null && !s.typedIsKnown && auto != null) "“$typed”" else typed
        val items = ArrayList<StripItem>(3)
        items += StripItem(typedLabel, KeyAction.CommitCandidate(typed, verbatim = true), emphasized = auto == null)
        val others = buildList {
            auto?.let { add(it) }
            (s ?: wordSuggestions).candidates.forEach { if (it != auto && it != typed) add(it) }
        }.distinct()
        // Three slots: typed word, best suggestion, then a matching emoji if there is one.
        val emoji = emojiSuggestions(auto ?: typed).firstOrNull() ?: s?.emoji?.firstOrNull()?.takeIf { settings.emojiSuggestions }
        others.take(if (emoji != null) 1 else 2).forEachIndexed { i, word ->
            items += StripItem(word, KeyAction.CommitCandidate(word), emphasized = auto != null && i == 0)
        }
        emoji?.let { items += StripItem(it, KeyAction.InsertEmoji(it, afterWord = true)) }
        return items
    }

    private fun emojiSuggestions(word: String): List<String> =
        if (settings.emojiSuggestions && word.length >= 2) emojiFor(word).take(2) else emptyList()

    // ------------------------------------------------------------------ layout, shift

    private fun onShift() {
        val now = clock()
        val doubleTap = lastShiftTap != Long.MIN_VALUE && now - lastShiftTap <= DOUBLE_TAP_MS
        shift = when {
            shift == ShiftState.LOCKED -> ShiftState.OFF
            shift == ShiftState.ON && !shiftIsAutomatic && doubleTap -> ShiftState.LOCKED
            shift == ShiftState.ON -> ShiftState.OFF
            else -> ShiftState.ON
        }
        shiftIsAutomatic = false
        lastShiftTap = now
    }

    private fun selectLayout(id: LayoutId) {
        val composition = processor.composition
        if (composition != null) {
            val previous = banglaContextBefore()
            processor.finishComposing(editor)
            learnAvro(composition, composition.bangla, previous)
        }
        processor.reset()
        userLayout = id
        activeLayout = id
        page = KeyboardPage.LETTERS
        shift = ShiftState.OFF
        shiftIsAutomatic = false
        rejectedWord = null
        onLayoutChosen?.invoke(id)
        refreshSuggestions(force = true)
    }

    /** Email, URL and password fields expect Latin text, so they open in English. */
    private fun layoutForField(f: FieldInfo): LayoutId =
        if (f.isPassword || f.kind == FieldKind.EMAIL || f.kind == FieldKind.URI) LayoutId.ENGLISH else userLayout

    /**
     * Turns shift on at the start of a sentence (English letters only), and off again when
     * it no longer applies. Returns true if the shift state changed.
     *
     * Never for Avro: upper and lower case are different Bangla letters there (t ত, T ট).
     */
    private fun updateAutoShift(): Boolean {
        if (shift == ShiftState.LOCKED) return false
        if (shift == ShiftState.ON && !shiftIsAutomatic) return false
        val wanted = settings.autoCapitalize &&
            activeLayout == LayoutId.ENGLISH &&
            page == KeyboardPage.LETTERS &&
            !editorField.isPassword &&
            !editor.hasSelection() &&
            editor.isCapsModeAtCursor()
        val before = shift
        if (wanted) {
            shift = ShiftState.ON
            shiftIsAutomatic = true
        } else if (shiftIsAutomatic) {
            shift = ShiftState.OFF
            shiftIsAutomatic = false
        }
        return shift != before
    }

    private fun publish() {
        refreshNextChars()
        _state.value = buildState()
    }

    // Predictive touch: next-letter probabilities for the word being typed, computed in the
    // background and cached per word prefix. Off in password fields and on symbol pages.
    private var nextChars: NextChars? = null
    private var nextCharsKey: String? = null

    private fun refreshNextChars() {
        val predictor = suggester?.nextChars
        val kind = editorField.kind
        val allowed = predictor != null && settings.predictiveTouch && page == KeyboardPage.LETTERS &&
            panel == Panel.NONE && !typingInBox && !editorField.isPassword &&
            (kind == FieldKind.TEXT || kind == FieldKind.URI || kind == FieldKind.EMAIL)
        if (!allowed) {
            nextChars = null
            nextCharsKey = null
            return
        }
        val script: TypingScript
        val prefix: String
        var sentence: List<String> = emptyList()
        when (activeLayout) {
            LayoutId.ENGLISH -> {
                script = TypingScript.ENGLISH
                val ctx = WordContext.english(editor.textBeforeCursor(CONTEXT_CHARS))
                prefix = ctx.current
                if (modelEnabled) sentence = ctx.sentence
            }
            LayoutId.AVRO -> {
                script = TypingScript.AVRO
                prefix = processor.composition?.roman.orEmpty()
            }
            LayoutId.NATIONAL, LayoutId.PROBHAT -> {
                script = TypingScript.NATIONAL
                prefix = WordContext.bangla(editor.textBeforeCursor(CONTEXT_CHARS)).current
            }
        }
        val useModel = script == TypingScript.ENGLISH && modelEnabled
        val key = "${script.name}:$prefix" + if (useModel) "|" + sentence.joinToString(" ") else ""
        if (key == nextCharsKey) return
        nextCharsKey = key
        nextChars = null // never use a prediction for a different prefix
        touchRunner.run(work = { predictor!!.predict(script, prefix, sentence, useModel) }) { result ->
            if (nextCharsKey == key) {
                nextChars = result
                _state.value = buildState()
            }
        }
    }

    private fun layoutFactory(s: KeyboardSettings) = LayoutFactory(s.enabledLayouts, s.numberRow)

    private fun buildState() = KeyboardUiState(
        layout = layouts.build(activeLayout, page, shift != ShiftState.OFF, editorField),
        layoutId = activeLayout,
        page = page,
        shift = shift,
        field = editorField,
        composition = processor.composition,
        strip = buildStrip(),
        panel = panel,
        selecting = selecting,
        cursorSwipe = settings.cursorSwipe,
        trackpadVertical = settings.trackpadVertical,
        longPressMs = settings.longPressMs.toLong(),
        gestures = GestureDispatcher(settings.gestures, BuiltIns(settings.cursorSwipe, settings.doubleSpacePeriod)).takeIf { settings.gestures.enabled && !editorField.isPassword },
        emojiTab = emojiTab.takeIf { panel == Panel.EMOJI },
        nextChars = nextChars,
        touchCalibration = settings.touchCalibration && !editorField.isSensitive,
        search = searchPanel,
        searchQuery = if (searchPanel != null) searchBuffer.text else "",
        check = if (checking) CheckUi(checkIssues, checkStale, checkFixed, checkEmpty, checkText) else null,
        translate = if (translating) {
            val source = translateBuffer.text
            TranslateUi(source, translateToBangla, translatedText.takeIf { translatedSource == source }, translateIssues(source))
        } else {
            null
        },
        panelQuery = panelQueries[panel].orEmpty(),
        toolbar = settings.toolbar,
        voiceLanguageTag = when (settings.voiceLanguage) {
            VoiceLanguage.AUTO -> if (activeLayout == LayoutId.ENGLISH) "en-US" else "bn-BD"
            else -> settings.voiceLanguage.storageKey
        },
        emojiSkinTone = settings.emojiSkinTone,
        textStyle = textStyle,
    )

    private companion object {
        const val DOUBLE_TAP_MS = 350L
        const val CONTEXT_CHARS = 128
        const val SEARCH_RESULTS = 16

        /** Characters read on each side of the cursor for the Check card. */
        const val CHECK_WINDOW = 1500
    }
}
