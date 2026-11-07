package com.yeamin.jolpai.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Paint
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.graphics.PaintCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.yeamin.jolpai.clipboard.ClipboardPanel
import com.yeamin.jolpai.clipboard.ClipboardPanelActions
import com.yeamin.jolpai.clipboard.ClipboardRepository
import com.yeamin.jolpai.clipboard.ClipboardWatcher
import com.yeamin.jolpai.clipboard.PanelColors
import com.yeamin.jolpai.core.BackgroundWork
import com.yeamin.jolpai.core.KeyboardController
import com.yeamin.jolpai.core.touch.TouchCalibration
import androidx.core.content.edit
import com.yeamin.jolpai.core.KeyboardUiState
import com.yeamin.jolpai.core.TranslateUi
import com.yeamin.jolpai.core.SuggestionRunner
import com.yeamin.jolpai.core.editor.EditorSession
import com.yeamin.jolpai.core.field.FieldClassifier
import com.yeamin.jolpai.core.model.EditCommand
import com.yeamin.jolpai.core.model.HostCommand
import com.yeamin.jolpai.core.model.KeyAction
import com.yeamin.jolpai.core.model.Panel
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.core.settings.OneHanded
import com.yeamin.jolpai.core.settings.KeyboardSettingsRepository
import com.yeamin.jolpai.core.settings.VoiceLanguage
import com.yeamin.jolpai.core.suggest.SuggestionResources
import com.yeamin.jolpai.core.theme.ThemeRepository
import com.yeamin.jolpai.theme.KeyboardTheme
import com.yeamin.jolpai.data.ClipEntity
import com.yeamin.jolpai.data.EmojiDao
import com.yeamin.jolpai.data.EmojiUsageEntity
import com.yeamin.jolpai.emoji.EmojiData
import com.yeamin.jolpai.media.Gif
import com.yeamin.jolpai.media.GifCache
import com.yeamin.jolpai.media.GiphyProvider
import com.yeamin.jolpai.media.Sticker
import com.yeamin.jolpai.media.StickerStore
import com.yeamin.jolpai.voice.VoiceAvailability
import com.yeamin.jolpai.voice.VoiceState
import com.yeamin.jolpai.voice.VoiceTyping
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The system keyboard. Android binds to this service when Jolpai is the selected input method.
 *
 * It owns the glue between Android and the pure-Kotlin [KeyboardController]:
 * field info in, key actions in, UI state out. Typing never waits on storage or dictionaries:
 * settings and dictionaries load in the background, English/National suggestions are
 * computed off the main thread, and learning is written to disk asynchronously.
 *
 * It is also the lifecycle owner for the Compose panels shown inside the keyboard window
 * (an input method service is not an Activity, so it provides these itself), and it
 * runs the parts that need Android: clipboard capture, voice, image sending, emoji fonts.
 */
@AndroidEntryPoint
class JolpaiImeService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    @Inject lateinit var settingsRepository: KeyboardSettingsRepository
    @Inject lateinit var suggestionResources: SuggestionResources
    @Inject lateinit var clipboardRepository: ClipboardRepository
    @Inject lateinit var emojiDao: EmojiDao
    @Inject lateinit var themeRepository: ThemeRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val editor = EditorSession { currentInputConnection }
    private lateinit var controller: KeyboardController
    private lateinit var clipboardWatcher: ClipboardWatcher
    private lateinit var voice: VoiceTyping

    private var container: KeyboardContainer? = null
    private var renderJob: Job? = null
    private var suggestionJob: Job? = null
    private var lastPanel = Panel.NONE

    @Volatile private var settings = KeyboardSettings()
    @Volatile private var snippets: Map<String, String> = emptyMap()
    @Volatile private var emojiData: EmojiData? = null
    @Volatile private var customThemes: List<KeyboardTheme> = emptyList()
    private var builtAppearance: Any? = null
    private val feedback by lazy { KeyFeedback(this) { settings } }

    private val voiceAvailability = MutableStateFlow<VoiceAvailability?>(null)
    private val stickerStore by lazy { StickerStore(this) }
    private val gifCache by lazy { GifCache(this) }
    private lateinit var recentEmoji: StateFlow<List<String>>
    private lateinit var favoriteEmoji: StateFlow<List<String>>

    // ------------------------------------------------------------------ lifecycle for Compose

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    /** Computes suggestions on a background thread; only the newest request is delivered. */
    private val backgroundRunner = SuggestionRunner { work, deliver ->
        suggestionJob?.cancel()
        suggestionJob = scope.launch {
            val result = withContext(Dispatchers.Default) { work() }
            deliver(result)
        }
    }

    // Predictive touch: next-letter predictions run here, apart from suggestions, newest only.
    private var touchJob: Job? = null
    private val touchRunner = object : BackgroundWork {
        override fun <T> run(work: () -> T, deliver: (T) -> Unit) {
            touchJob?.cancel()
            touchJob = scope.launch {
                val result = withContext(Dispatchers.Default) { work() }
                deliver(result)
            }
        }
    }

    /** Where this user touches each key; kept in a private preferences file, never backed up. */
    private val touchCalibration = TouchCalibration()
    private val touchPrefs by lazy { getSharedPreferences(TOUCH_PREFS, MODE_PRIVATE) }

    private fun saveTouchCalibration() {
        touchPrefs.edit { putString(KEY_CALIBRATION, touchCalibration.encode()) }
    }

    // ------------------------------------------------------------------ emoji fonts

    private val glyphPaint = Paint()
    private val glyphCache = ConcurrentHashMap<String, Boolean>()

    /** Whether this phone's font can draw [emoji] (older phones lack newer emoji). */
    private fun canDraw(emoji: String): Boolean = glyphCache.getOrPut(emoji) { PaintCompat.hasGlyph(glyphPaint, emoji) }

    override fun onCreate() {
        super.onCreate() // Hilt injects here
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        controller = KeyboardController(
            appEditor = editor,
            clock = SystemClock::uptimeMillis,
            suggester = suggestionResources.suggester,
            runner = backgroundRunner,
            touchRunner = touchRunner,
            avroEngine = suggestionResources.phonetic,
            snippetFor = { shortcut -> snippets[shortcut] },
            emojiFor = { word ->
                emojiData?.suggestionsFor(word).orEmpty().filter { canDraw(it.value) }.map { it.withTone(settings.emojiSkinTone) }
            },
            emojiSearch = { query ->
                emojiData?.search(query).orEmpty().filter { canDraw(it.value) }.map { it.withTone(settings.emojiSkinTone) }
            },
        )
        touchCalibration.decode(touchPrefs.getString(KEY_CALIBRATION, "").orEmpty())
        controller.onLayoutChosen = { id -> scope.launch { settingsRepository.setLastLayout(id) } }
        controller.onEmojiUsed = ::recordEmoji
        controller.stats = com.yeamin.jolpai.core.stats.TypingStats(this) // counts only, shown in Typing stats
        // Cut / copy / paste inside the translate and search boxes use the phone's clipboard directly.
        controller.boxClipboard = object : com.yeamin.jolpai.core.BoxClipboard {
            private val clipboard get() = getSystemService(android.content.ClipboardManager::class.java)
            override fun copy(text: String) {
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Jolpai", text))
            }
            override fun paste(): String? = runCatching {
                clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this@JolpaiImeService)?.toString()
            }.getOrNull()
        }
        controller.onHostCommand = ::handleHost
        scope.launch {
            settingsRepository.settings.collect {
                settings = it
                if (it.touchCalibrationReset != touchPrefs.getLong(KEY_RESET_SEEN, 0L)) {
                    touchCalibration.clear()
                    touchPrefs.edit { putLong(KEY_RESET_SEEN, it.touchCalibrationReset).putString(KEY_CALIBRATION, "") }
                }
                controller.updateSettings(it)
                rebuildIfAppearanceChanged()
            }
        }
        scope.launch {
            themeRepository.customThemes.collect {
                customThemes = it
                rebuildIfAppearanceChanged()
            }
        }
        scope.launch {
            clipboardRepository.snippets.collect { list ->
                snippets = list.mapNotNull { s -> s.shortcut?.let { it to s.text } }.toMap()
            }
        }
        recentEmoji = emojiDao.observeRecent(RECENT_EMOJI).map { l -> l.map { it.emoji } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())
        favoriteEmoji = emojiDao.observeFavorites().map { l -> l.map { it.emoji } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

        // Avro rules first (needed for the first Bangla key press), then history, dictionaries, emoji.
        scope.launch(Dispatchers.Default) {
            controller.preloadAvro()
            suggestionResources.loadAll { scope.launch { controller.onSuggestionDataLoaded() } }
            emojiData = EmojiData.load()
            scope.launch { controller.onSuggestionDataLoaded() }
        }

        clipboardWatcher = ClipboardWatcher(this, clipboardRepository, scope) {
            ClipboardWatcher.Policy(
                enabled = settings.clipboardHistory,
                saveImages = settings.clipboardImages,
                retention = settings.clipboardRetention,
                inSensitiveField = controller.state.value.field.isSensitive,
            )
        }
        clipboardWatcher.start()
        scope.launch { clipboardRepository.prune(settings.clipboardRetention) }

        voice = VoiceTyping(
            context = this,
            onPartial = { controller.onKey(KeyAction.VoicePartial(it)) },
            onFinal = { controller.onKey(KeyAction.VoiceFinal(it)) },
        )
        // Keep the voice bar in step with the recogniser (listening, words, level, errors).
        scope.launch { voice.state.collect { if (inlineVoice) renderVoiceBar() } }
    }

    override fun onCreateInputView(): View {
        window.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
        }
        val view = KeyboardContainer(
            context = this,
            onAction = ::handleAction,
            servicePanel = { panel, colors, state -> ServicePanel(panel, colors, state) },
            palette = KeyboardPalette.from(this, currentTheme()),
            options = layoutOptions(),
            feedback = feedback,
            onModeChange = ::onModeChange,
            touchCalibration = touchCalibration,
            onVoice = ::onVoiceCommand,
            onTranslateDownload = { translator().download() },
            onAiCheck = ::runAiCheck,
        )
        container = view
        translateWatch?.let { view.setTranslateStatus(translator().status.value) }
        if (inlineVoice) renderVoiceBar()
        builtAppearance = appearanceKey()
        renderJob?.cancel()
        renderJob = scope.launch {
            controller.state.collect { s ->
                view.render(s)
                onPanelShown(s)
                onTranslateState(s.translate)
                onCheckState(s.check)
            }
        }
        return view
    }

    // ------------------------------------------------------------------ on-device AI proofreading

    private var aiProofreader: AiProofreader? = null
    private var aiStatus: AiStatus? = null
    private var checkOpen = false

    private fun ai(): AiProofreader = aiProofreader ?: AiProofreader(this).also { aiProofreader = it }

    /** When the Check card opens, offer ✨ AI if this phone has on-device AI and the text is English. */
    private fun onCheckState(check: com.yeamin.jolpai.core.CheckUi?) {
        val open = check != null
        if (open == checkOpen) return
        checkOpen = open
        if (!open) {
            container?.setAi(CheckCardView.Ai.Hidden)
            return
        }
        if (!mostlyEnglish(check?.text.orEmpty())) return
        ai().status { st ->
            aiStatus = st
            if (!checkOpen) return@status
            container?.setAi(
                when (st) {
                    AiStatus.AVAILABLE -> CheckCardView.Ai.Ready(needsDownload = false)
                    AiStatus.DOWNLOADABLE, AiStatus.DOWNLOADING -> CheckCardView.Ai.Ready(needsDownload = true)
                    AiStatus.UNAVAILABLE -> CheckCardView.Ai.Hidden
                },
            )
        }
    }

    /** Proofreads the end of [text] (the model takes ~256 tokens); downloads the model first if needed. */
    private fun runAiCheck(text: String) {
        val part = text.takeLast(AiProofreader.MAX_CHARS).let { t ->
            if (t.length < text.length) t.substring((t.indexOfFirst { it in ".!?\n" } + 1).coerceAtMost(t.length)) else t
        }.trim()
        if (part.isEmpty()) return
        container?.setAi(CheckCardView.Ai.Working)
        val run = {
            ai().proofread(part) { result ->
                if (!checkOpen) return@proofread
                container?.setAi(
                    when {
                        result == null -> CheckCardView.Ai.Failed
                        result.trim() == part -> CheckCardView.Ai.NothingToChange
                        else -> CheckCardView.Ai.Suggestion(part, result.trim())
                    },
                )
            }
        }
        if (aiStatus == AiStatus.AVAILABLE) run() else ai().download { ok -> if (ok) { aiStatus = AiStatus.AVAILABLE; run() } else container?.setAi(CheckCardView.Ai.Failed) }
    }

    private fun mostlyEnglish(text: String): Boolean {
        val latin = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        val bangla = text.count { it in '\u0980'..'\u09FF' }
        return latin >= 3 && latin > bangla
    }

    // ------------------------------------------------------------------ translate

    private var offlineTranslator: OfflineTranslator? = null
    private var translateWatch: kotlinx.coroutines.Job? = null
    private val translateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var translateKey: String? = null
    private var translateSeq = 0

    private fun translator(): OfflineTranslator = offlineTranslator ?: OfflineTranslator().also { offlineTranslator = it }

    /** Keeps the translate card's status current and translates the typed text as it changes. */
    private fun onTranslateState(t: TranslateUi?) {
        if (t == null) {
            translateHandler.removeCallbacksAndMessages(null)
            translateKey = null
            container?.setTranslateBusy(false)
            return
        }
        if (translateWatch == null) {
            val tr = translator()
            tr.refresh()
            translateWatch = scope.launch {
                tr.status.collect { st ->
                    container?.setTranslateStatus(st)
                    if (st == TranslateStatus.READY) requestTranslation(force = true)
                }
            }
        }
        requestTranslation(force = false)
    }

    /**
     * Translates the box's text a moment after typing pauses (220 ms), so fast typing isn't
     * translated letter by letter; an older answer that arrives late is ignored.
     */
    private fun requestTranslation(force: Boolean) {
        val t = controller.state.value.translate ?: return
        val key = "${t.toBangla}|${t.source}"
        if (!force && key == translateKey) return
        translateKey = key
        translateHandler.removeCallbacksAndMessages(null)
        val seq = ++translateSeq
        if (t.source.isBlank()) {
            container?.setTranslateBusy(false)
            return
        }
        if (t.translation != null && !force) return
        container?.setTranslateBusy(true)
        translateHandler.postDelayed({
            translator().translate(t.source, t.toBangla) { result ->
                if (seq != translateSeq) return@translate
                container?.setTranslateBusy(false)
                controller.setTranslation(t.source, result)
            }
        }, TRANSLATE_PAUSE_MS)
    }

    // ------------------------------------------------------------------ appearance

    private fun currentTheme(): KeyboardTheme =
        ThemeRepository.resolve(settings.themeId, customThemes, KeyboardPalette.isNight(this))

    private fun layoutOptions(): KeyboardContainer.LayoutOptions {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val percent = if (landscape) settings.heightLandscape else settings.heightPortrait
        return KeyboardContainer.LayoutOptions(
            heightScale = percent / 100f,
            oneHanded = settings.oneHanded,
            floating = settings.floating,
            floatX = settings.floatX,
            floatY = settings.floatY,
            keyFont = settings.keyFont,
            split = when (settings.splitKeys) {
                com.yeamin.jolpai.core.settings.SplitKeys.OFF -> false
                com.yeamin.jolpai.core.settings.SplitKeys.LANDSCAPE -> landscape
                com.yeamin.jolpai.core.settings.SplitKeys.ALWAYS -> true
            },
        )
    }

    /** What the input view was built with; the position of a floating keyboard is left out. */
    private fun appearanceKey(): Any = listOf(currentTheme(), layoutOptions().copy(floatX = 0f, floatY = 0f))

    /** Rebuilds the keyboard right away when the theme, size or mode changes. */
    private fun rebuildIfAppearanceChanged() {
        feedback.prepareSounds() // a newly chosen sound pack is ready for the next key press
        if (container == null || builtAppearance == appearanceKey()) return
        setInputView(onCreateInputView())
    }

    private fun onModeChange(change: KeyboardContainer.ModeChange) {
        scope.launch {
            when (change) {
                is KeyboardContainer.ModeChange.SetOneHanded -> settingsRepository.setOneHanded(change.mode)
                is KeyboardContainer.ModeChange.SetFloating -> settingsRepository.setFloating(change.floating)
                is KeyboardContainer.ModeChange.MoveFloating -> settingsRepository.setFloatPosition(change.x, change.y)
            }
        }
    }

    /**
     * Floating keyboard: the app keeps its full size, and only touches on the floating card
     * go to the keyboard; everywhere else they reach the app.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val c = container ?: return
        // Floating card, or the voice pill shrunk to one side: the app keeps its full size and only
        // touches on the card / pill go to the keyboard.
        val card = (if (c.isFloating) c.cardRectInWindow() else c.voicePillRectInWindow()) ?: return
        val decorHeight = window.window?.decorView?.height ?: return
        outInsets.contentTopInsets = decorHeight
        outInsets.visibleTopInsets = decorHeight
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(card)
    }

    @Composable
    private fun ServicePanel(panel: Panel, colors: PanelColors, state: KeyboardUiState) {
        when (panel) {
            Panel.CLIPBOARD -> ClipboardPanel(
                repository = clipboardRepository,
                captureEnabled = settings.clipboardHistory,
                colors = colors,
                actions = clipboardActions,
                query = state.panelQuery,
            )
            Panel.EMOJI -> {
                val recents by recentEmoji.collectAsState()
                val favorites by favoriteEmoji.collectAsState()
                val stickers by stickerStore.stickers.collectAsState()
                EmojiPanel(
                    data = EmojiPanelData(
                        emoji = emojiData,
                        canDraw = ::canDraw,
                        recents = recents,
                        favorites = favorites,
                        stickers = stickers,
                        skinTone = state.emojiSkinTone,
                        gifsEnabled = settings.giphyApiKey.isNotBlank(),
                        startTab = state.emojiTab,
                    ),
                    query = state.panelQuery,
                    colors = colors,
                    actions = emojiActions,
                )
            }
            Panel.VOICE -> {
                val voiceState by voice.state.collectAsState()
                val availability by voiceAvailability.collectAsState()
                VoicePanel(voiceState, state.voiceLanguageTag, availability, MicPermissionActivity.isBlocked(this@JolpaiImeService), colors, voiceActions)
            }
            else -> Unit
        }
    }

    /** Starts and stops voice and refreshes stickers when panels open and close. */
    private fun onPanelShown(s: KeyboardUiState) {
        if (s.panel == lastPanel) return
        val previous = lastPanel
        lastPanel = s.panel
        if (previous == Panel.VOICE) voice.stop()
        when (s.panel) {
            Panel.VOICE -> {
                startVoice(s.voiceLanguageTag)
                // First use: ask straight away (like Google voice typing) instead of sending the user elsewhere.
                if (!voice.hasPermission() && !MicPermissionActivity.isBlocked(this)) {
                    voiceInBar = false
                    askForMicrophone()
                }
            }
            Panel.EMOJI -> scope.launch { stickerStore.refresh() }
            else -> Unit
        }
    }

    /** Android's "Allow microphone?" dialog over the current app; listening starts once allowed. */
    private fun askForMicrophone() {
        runCatching { startActivity(MicPermissionActivity.intent(this)) }
    }

    /** The language just picked with the language button, until the saved setting catches up. */
    private var pickedVoiceTag: String? = null

    /** The voice language to use now: the one just picked, else the setting (or the layout's). */
    private fun voiceTag(): String {
        val setting = controller.state.value.voiceLanguageTag
        val picked = pickedVoiceTag ?: return setting
        if (picked == setting) pickedVoiceTag = null
        return picked
    }

    private fun startVoice(languageTag: String) {
        voice.start(languageTag)
        voiceAvailability.value = null
        voice.checkAvailability(languageTag) { voiceAvailability.value = it }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        editor.onStartInput(attribute)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        controller.onStartInput(FieldClassifier.classify(info.inputType, info.imeOptions), restarting)
        // Back from the microphone dialog with permission: carry on where voice typing was asked for.
        if (MicPermissionActivity.consumeStartAfterGrant(this) && voice.hasPermission()) {
            val s = controller.state.value
            when {
                voiceInBar -> startInlineVoice()
                s.panel == Panel.VOICE -> startVoice(s.voiceLanguageTag)
                else -> controller.onKey(KeyAction.ShowPanel(Panel.VOICE))
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        container?.dismissPopups()
        saveTouchCalibration()
        stopInlineVoice()
        if (lastPanel == Panel.VOICE) voice.stop()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        suggestionJob?.cancel()
        controller.onFinishInput()
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        editor.onSelectionChanged(newSelStart, newSelEnd)
        controller.onSelectionChanged(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    /** A full-screen extract view would hide the app; modern keyboards keep the app visible. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun handleAction(action: KeyAction) {
        // Typing while dictating in the top bar ends the dictation first (what was heard is kept),
        // so typed and spoken text never interleave. Shift, pages, layouts and cursor moves don't.
        if (inlineVoice && stopsDictation(action)) stopInlineVoice()
        if (action is KeyAction.Host) {
            handleHost(action.command)
        } else {
            controller.onKey(action)
        }
    }

    // ------------------------------------------------------------------ voice typing in the top bar

    /** True while dictating in the top bar (the voice bar is showing; the keys stay usable). */
    private var inlineVoice = false

    /** Set while the microphone dialog is up: start in the bar (true) or the voice panel. */
    private var voiceInBar = false

    private fun onVoiceCommand(command: KeyboardContainer.VoiceCommand) {
        when (command) {
            KeyboardContainer.VoiceCommand.START -> startInlineVoice()
            KeyboardContainer.VoiceCommand.OPEN_PANEL -> {
                stopInlineVoice()
                controller.onKey(KeyAction.ShowPanel(Panel.VOICE))
            }
            KeyboardContainer.VoiceCommand.MIC -> {
                val s = voice.state.value
                when {
                    s is VoiceState.Listening || s is VoiceState.Starting -> voice.stop()
                    s == VoiceState.NoPermission -> askForMicrophoneOrSettings(inBar = true)
                    else -> startVoice(voiceTag())
                }
            }
            KeyboardContainer.VoiceCommand.LANGUAGE -> voiceActions.switchLanguage()
            KeyboardContainer.VoiceCommand.CLOSE -> stopInlineVoice()
            KeyboardContainer.VoiceCommand.BACKSPACE -> {
                // Fix a mistake without leaving voice typing: keep what was heard, delete, and carry on
                // listening shortly after the last delete (holding backspace repeats).
                val listening = voice.state.value.let { it is VoiceState.Listening || it is VoiceState.Starting }
                if (listening) {
                    voice.stop()
                    resumeAfterDelete = true
                }
                controller.onKey(KeyAction.Delete)
                voiceHandler.removeCallbacks(resumeListening)
                if (resumeAfterDelete) voiceHandler.postDelayed(resumeListening, 700)
            }
        }
    }

    private val voiceHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var resumeAfterDelete = false
    private val resumeListening = Runnable {
        resumeAfterDelete = false
        if (inlineVoice) startVoice(voiceTag())
    }

    private fun startInlineVoice() {
        if (controller.state.value.panel != Panel.NONE) controller.onKey(KeyAction.ShowPanel(Panel.NONE))
        inlineVoice = true
        if (voice.hasPermission()) startVoice(voiceTag()) else askForMicrophoneOrSettings(inBar = true)
        renderVoiceBar()
    }

    private fun stopInlineVoice() {
        if (!inlineVoice) return
        voiceHandler.removeCallbacks(resumeListening)
        resumeAfterDelete = false
        inlineVoice = false
        voice.stop()
        container?.renderVoice(null)
    }

    private fun askForMicrophoneOrSettings(inBar: Boolean) {
        voiceInBar = inBar
        if (MicPermissionActivity.isBlocked(this)) voiceActions.openPermissionSettings() else askForMicrophone()
    }

    private fun stopsDictation(action: KeyAction): Boolean = when (action) {
        is KeyAction.Text, KeyAction.Delete, KeyAction.Space, KeyAction.Enter, is KeyAction.CommitCandidate,
        is KeyAction.PasteText, is KeyAction.ReplaceBefore, is KeyAction.ExpandSnippet, is KeyAction.InsertEmoji,
        is KeyAction.ShowPanel, is KeyAction.OpenEmojiTab, is KeyAction.Edit, is KeyAction.Shortcut -> true
        else -> false
    }

    /** What the voice bar shows for the recogniser's state. */
    private fun renderVoiceBar() {
        val c = container ?: return
        if (!inlineVoice) {
            c.renderVoice(null)
            return
        }
        val bangla = voiceTag().startsWith("bn")
        val s = voice.state.value
        val model = when (s) {
            VoiceState.Starting -> VoiceBarModel(true, if (bangla) "শুনছি…" else "Listening…", false, 0f, bangla)
            is VoiceState.Listening -> VoiceBarModel(true, s.partial.ifEmpty { if (bangla) "বলুন…" else "Speak now" }, s.partial.isNotEmpty(), s.level, bangla)
            VoiceState.Idle -> VoiceBarModel(false, "Paused · tap the mic to go on", false, 0f, bangla)
            VoiceState.NoPermission -> VoiceBarModel(
                false,
                if (MicPermissionActivity.isBlocked(this)) "Microphone blocked · tap the mic for settings" else "Tap the mic to allow the microphone",
                false, 0f, bangla,
            )
            VoiceState.Unavailable -> VoiceBarModel(false, "No speech service on this phone", false, 0f, bangla)
            is VoiceState.Error -> VoiceBarModel(false, s.message, false, 0f, bangla)
        }
        c.renderVoice(model)
    }

    /** Commands only the service can carry out (also used by gesture shortcuts). */
    private fun handleHost(command: HostCommand) {
        when (command) {
            HostCommand.SYSTEM_KEYBOARD_PICKER ->
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            HostCommand.OPEN_SETTINGS -> openApp(null)
            HostCommand.HIDE_KEYBOARD -> requestHideSelf(0)
            HostCommand.TOGGLE_NUMBER_ROW -> scope.launch { settingsRepository.toggleNumberRow() }
            HostCommand.TOGGLE_ONE_HANDED -> scope.launch {
                settingsRepository.setOneHanded(if (settings.oneHanded == OneHanded.OFF) OneHanded.RIGHT else OneHanded.OFF)
            }
            HostCommand.TOGGLE_FLOATING -> scope.launch { settingsRepository.setFloating(!settings.floating) }
            HostCommand.START_VOICE -> startInlineVoice()
        }
    }

    // ------------------------------------------------------------------ images (clipboard, stickers, GIFs)

    /**
     * Sends an image to the app with `commitContent` and a one-time read grant.
     * Returns an error message, or null when it was sent.
     */
    private fun commitImage(uri: Uri, mimeType: String): String? {
        val info = currentInputEditorInfo ?: return "No text field is active."
        val ic = currentInputConnection ?: return "No text field is active."
        val accepted = EditorInfoCompat.getContentMimeTypes(info).any { ClipDescription.compareMimeTypes(mimeType, it) }
        if (!accepted) return "This app doesn't accept pictures from the keyboard."
        val content = InputContentInfoCompat(uri, ClipDescription("Image", arrayOf(mimeType)), null)
        val sent = InputConnectionCompat.commitContent(ic, info, content, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
        return if (sent) null else "The app didn't accept the picture."
    }

    private val authority: String get() = ClipboardRepository.authority(this)

    // ------------------------------------------------------------------ panel actions

    private val clipboardActions = object : ClipboardPanelActions {
        override fun pasteText(text: String) = controller.onKey(KeyAction.PasteText(text))

        override fun pasteImage(clip: ClipEntity): Boolean {
            val uri = clipboardRepository.contentUri(clip) ?: return false
            return commitImage(uri, clip.mimeType) == null
        }

        override fun copyToClipboard(clip: ClipEntity) {
            val cm = getSystemService(ClipboardManager::class.java) ?: return
            val data = when {
                clip.text != null -> ClipData.newPlainText("Jolpai", clip.text)
                else -> clipboardRepository.contentUri(clip)?.let { ClipData.newUri(contentResolver, "Jolpai", it) }
            } ?: return
            cm.setPrimaryClip(data)
        }

        override fun openManager() = openApp("clipboard")
        override fun close() = controller.onKey(KeyAction.ShowPanel(Panel.NONE))
        override fun startSearch() = controller.onKey(KeyAction.StartSearch(Panel.CLIPBOARD))
        override fun clearSearch() = controller.onKey(KeyAction.EndSearch(keepQuery = false))
    }

    private val emojiActions = object : EmojiPanelActions {
        override fun insert(text: String) = controller.onKey(KeyAction.InsertEmoji(text))

        override fun toggleFavorite(emoji: String) {
            scope.launch(Dispatchers.IO) {
                val current = emojiDao.get(emoji)
                val now = System.currentTimeMillis()
                emojiDao.upsert((current ?: EmojiUsageEntity(emoji, 0, now)).copy(favorite = current?.favorite != true))
            }
        }

        override fun setSkinTone(tone: Int) {
            scope.launch { settingsRepository.setEmojiSkinTone(tone) }
        }

        override fun startSearch() = controller.onKey(KeyAction.StartSearch(Panel.EMOJI))
        override fun clearSearch() = controller.onKey(KeyAction.EndSearch(keepQuery = false))
        override fun backspace() = controller.onKey(KeyAction.Delete)
        override fun close() = controller.onKey(KeyAction.ShowPanel(Panel.NONE))

        override fun sendSticker(sticker: Sticker): String? =
            commitImage(stickerStore.contentUri(sticker, authority), sticker.mimeType)

        override suspend fun loadGifs(query: String): Result<List<Gif>> = runCatching {
            val provider = GiphyProvider(settings.giphyApiKey)
            if (query.isBlank()) provider.trending() else provider.search(query)
        }

        override suspend fun gifPreview(gif: Gif): Bitmap? = gifCache.preview(gif)

        override suspend fun sendGif(gif: Gif): String? = runCatching {
            commitImage(gifCache.file(gif, authority), "image/gif")
        }.getOrElse { it.message ?: "Couldn't send the GIF." }

        override fun openSettings(screen: String) = openApp(screen)
    }

    private val voiceActions = object : VoicePanelActions {
        override fun toggleListening() {
            val s = voice.state.value
            if (s is VoiceState.Listening || s is VoiceState.Starting) voice.stop() else startVoice(voiceTag())
        }

        override fun switchLanguage() {
            val next = if (voiceTag().startsWith("bn")) VoiceLanguage.ENGLISH else VoiceLanguage.BANGLA
            // Remember the pick at once: the saved setting reaches the keyboard a moment later, and
            // quick repeated taps used to read the old value and pick the same language again.
            pickedVoiceTag = next.storageKey
            voice.switchLanguage(next.storageKey)
            voiceAvailability.value = null
            voice.checkAvailability(next.storageKey) { voiceAvailability.value = it }
            scope.launch { settingsRepository.setVoiceLanguage(next) }
            renderVoiceBar()
        }

        override fun deleteLastWord() = controller.onKey(KeyAction.Edit(EditCommand.DELETE_WORD))
        override fun close() = controller.onKey(KeyAction.ShowPanel(Panel.NONE))
        override fun requestPermission() = askForMicrophone()

        override fun openPermissionSettings() {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            MicPermissionActivity.clearBlocked(this@JolpaiImeService)
            requestHideSelf(0)
            startActivity(intent)
        }
    }

    private fun recordEmoji(emoji: String) {
        scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val current = emojiDao.get(emoji)
            emojiDao.upsert(EmojiUsageEntity(emoji, (current?.count ?: 0) + 1, now, current?.favorite ?: false))
        }
    }

    /** Opens the Jolpai app, optionally at a [screen] ("clipboard", "voice", "gifs", "stickers"). */
    private fun openApp(screen: String?) {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (screen != null) intent.putExtra(EXTRA_SCREEN, screen)
        requestHideSelf(0)
        startActivity(intent)
    }

    override fun onDestroy() {
        translateHandler.removeCallbacksAndMessages(null)
        offlineTranslator?.close()
        aiProofreader?.close()
        clipboardWatcher.stop()
        voice.destroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Intent extra naming the app screen to open. */
        const val EXTRA_SCREEN = "com.yeamin.jolpai.SCREEN"
        private const val RECENT_EMOJI = 32

        /** Pause after typing before the translate box is translated. */
        private const val TRANSLATE_PAUSE_MS = 220L
        private const val TOUCH_PREFS = "touch"
        private const val KEY_CALIBRATION = "calibration"
        private const val KEY_RESET_SEEN = "reset_seen"
    }
}
