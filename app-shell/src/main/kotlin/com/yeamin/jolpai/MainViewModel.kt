package com.yeamin.jolpai

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import com.yeamin.jolpai.clipboard.ClipboardRepository
import com.yeamin.jolpai.core.model.ToolbarItem
import com.yeamin.jolpai.core.settings.VoiceLanguage
import com.yeamin.jolpai.core.settings.HapticLevel
import com.yeamin.jolpai.core.settings.OneHanded
import com.yeamin.jolpai.core.theme.ThemeRepository
import com.yeamin.jolpai.theme.KeyboardTheme
import com.yeamin.jolpai.media.Sticker
import com.yeamin.jolpai.media.StickerStore
import com.yeamin.jolpai.voice.VoiceAvailability
import com.yeamin.jolpai.voice.VoiceTyping
import com.yeamin.jolpai.data.ClipEntity
import com.yeamin.jolpai.data.ClipRetention
import com.yeamin.jolpai.data.SnippetEntity
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.core.settings.KeyboardSettingsRepository
import com.yeamin.jolpai.core.suggest.SuggestionResources
import com.yeamin.jolpai.data.HistoryDao
import com.yeamin.jolpai.data.PersonalWordEntity
import com.yeamin.jolpai.suggest.Language
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Whether Jolpai is turned on in system settings, and whether it is the keyboard in use. */
data class SetupStatus(val enabled: Boolean = false, val selected: Boolean = false)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val app: Application,
    private val repository: KeyboardSettingsRepository,
    private val suggestionResources: SuggestionResources,
    historyDao: HistoryDao,
    val clipboard: ClipboardRepository,
    private val themes: ThemeRepository,
    private val backup: BackupManager,
) : AndroidViewModel(app) {

    val settings: StateFlow<KeyboardSettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KeyboardSettings())

    val clips: StateFlow<List<ClipEntity>> =
        clipboard.clips.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val clipFolders: StateFlow<List<String>> =
        clipboard.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val snippets: StateFlow<List<SnippetEntity>> =
        clipboard.snippets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // The ready-made snippet packs (Islamic greetings, everyday messages), added once.
        viewModelScope.launch { clipboard.addPacksOnce() }
    }

    fun setClipboardHistory(v: Boolean) {
        viewModelScope.launch { repository.setClipboardHistory(v) }
    }

    fun setClipboardImages(v: Boolean) {
        viewModelScope.launch { repository.setClipboardImages(v) }
    }

    fun setClipboardRetention(v: ClipRetention) {
        viewModelScope.launch {
            repository.setClipboardRetention(v)
            clipboard.prune(v)
        }
    }

    fun setCursorSwipe(v: Boolean) {
        viewModelScope.launch { repository.setCursorSwipe(v) }
    }

    fun setLongPressMs(v: Int) {
        viewModelScope.launch { repository.setLongPressMs(v) }
    }

    fun setTrackpadVertical(v: Boolean) {
        viewModelScope.launch { repository.setTrackpadVertical(v) }
    }

    fun setPredictiveTouch(v: Boolean) {
        viewModelScope.launch { repository.setPredictiveTouch(v) }
    }

    fun setTouchCalibration(v: Boolean) {
        viewModelScope.launch { repository.setTouchCalibration(v) }
    }

    fun resetTouchCalibration() {
        viewModelScope.launch { repository.resetTouchCalibration() }
    }

    fun setGestures(config: com.yeamin.jolpai.core.gesture.GestureConfig) {
        viewModelScope.launch { repository.setGestures(config) }
    }

    // ------------------------------------------------------------------ toolbar, emoji, GIFs, stickers, voice

    fun setToolbar(items: List<ToolbarItem>) {
        viewModelScope.launch { repository.setToolbar(items) }
    }

    fun setEmojiSkinTone(tone: Int) {
        viewModelScope.launch { repository.setEmojiSkinTone(tone) }
    }

    fun setEmojiSuggestions(v: Boolean) {
        viewModelScope.launch { repository.setEmojiSuggestions(v) }
    }

    fun setSmartPredictions(v: Boolean) {
        viewModelScope.launch { repository.setSmartPredictions(v) }
    }

    fun setGiphyApiKey(key: String) {
        viewModelScope.launch { repository.setGiphyApiKey(key) }
    }

    fun setVoiceLanguage(v: VoiceLanguage) {
        viewModelScope.launch { repository.setVoiceLanguage(v) }
    }

    val stickerStore = StickerStore(app)
    val stickers: StateFlow<List<Sticker>> = stickerStore.stickers

    fun refreshStickers() {
        viewModelScope.launch { stickerStore.refresh() }
    }

    /** Imports picked images as stickers; returns how many could not be used, via [onDone]. */
    fun importStickers(uris: List<Uri>, onDone: (failed: Int) -> Unit) {
        viewModelScope.launch {
            val failed = uris.count { !stickerStore.import(it) }
            onDone(failed)
        }
    }

    fun deleteSticker(s: Sticker) {
        viewModelScope.launch { stickerStore.delete(s) }
    }

    /** Only used to ask the speech service about offline support; never listens. */
    private val voiceProbe = VoiceTyping(app, onPartial = {}, onFinal = {})

    private val _voiceAvailability = MutableStateFlow<Map<String, VoiceAvailability>>(emptyMap())
    val voiceAvailability: StateFlow<Map<String, VoiceAvailability>> = _voiceAvailability.asStateFlow()

    fun checkVoice() {
        listOf("bn-BD", "en-US").forEach { tag ->
            voiceProbe.checkAvailability(tag) { a -> _voiceAvailability.value = _voiceAvailability.value + (tag to a) }
        }
    }

    fun downloadOfflineVoice(tag: String) {
        voiceProbe.downloadOffline(tag)
        _voiceAvailability.value = _voiceAvailability.value + (tag to VoiceAvailability.DOWNLOADING)
    }

    fun hasMicPermission(): Boolean = voiceProbe.hasPermission()

    // ------------------------------------------------------------------ appearance, size, feedback

    val customThemes: StateFlow<List<KeyboardTheme>> =
        themes.customThemes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectTheme(id: String) {
        viewModelScope.launch { repository.setThemeId(id) }
    }

    /** Saves [theme] and makes it the active theme. */
    fun saveTheme(theme: KeyboardTheme) {
        viewModelScope.launch { repository.setThemeId(themes.save(theme)) }
    }

    /** Copies a theme (built-in or custom) into a new custom theme and returns it for editing. */
    fun duplicateTheme(theme: KeyboardTheme, onReady: (KeyboardTheme) -> Unit) {
        viewModelScope.launch {
            val id = themes.duplicate(theme)
            onReady(theme.copy(id = id, name = (theme.name + " copy").take(40), dynamic = false))
        }
    }

    fun deleteTheme(theme: KeyboardTheme, activeId: String) {
        viewModelScope.launch {
            themes.delete(theme)
            if (activeId == theme.id) repository.setThemeId(KeyboardTheme.DEFAULT_ID)
        }
    }

    fun exportTheme(theme: KeyboardTheme) {
        val cm = app.getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText("Jolpai theme", themes.export(theme)))
    }

    /** Imports a theme from JSON; [onResult] gets an error message, or null on success. */
    fun importTheme(json: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = runCatching { repository.setThemeId(themes.import(json)) }.exceptionOrNull()
            onResult(error?.message ?: if (error != null) "Couldn't import the theme." else null)
        }
    }

    fun pickThemeBackground(uri: Uri, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(themes.importBackground(uri)) }
    }

    fun setHeight(portrait: Boolean, percent: Int) {
        viewModelScope.launch { repository.setHeight(portrait, percent) }
    }

    fun setNumberRow(v: Boolean) {
        viewModelScope.launch { repository.setNumberRow(v) }
    }

    fun setKeyFont(v: com.yeamin.jolpai.core.settings.KeyFont) {
        viewModelScope.launch { repository.setKeyFont(v) }
    }

    fun setSoundPack(v: com.yeamin.jolpai.core.settings.SoundPack) {
        viewModelScope.launch { repository.setSoundPack(v) }
    }

    fun setSplitKeys(v: com.yeamin.jolpai.core.settings.SplitKeys) {
        viewModelScope.launch { repository.setSplitKeys(v) }
    }

    fun setOneHanded(v: OneHanded) {
        viewModelScope.launch { repository.setOneHanded(v) }
    }

    fun setFloating(v: Boolean) {
        viewModelScope.launch { repository.setFloating(v) }
    }

    fun resetFloatPosition() {
        viewModelScope.launch { repository.setFloatPosition(0.5f, 0f) }
    }

    fun setHaptics(v: HapticLevel) {
        viewModelScope.launch { repository.setHaptics(v) }
    }

    fun setKeySound(v: Boolean) {
        viewModelScope.launch { repository.setKeySound(v) }
    }

    fun setSoundVolume(v: Float) {
        viewModelScope.launch { repository.setSoundVolume(v) }
    }

    // ------------------------------------------------------------------ backup

    fun exportBackup(uri: Uri, includeLearned: Boolean, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val json = backup.export(includeLearned)
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                        ?: error("Couldn't write the file.")
                }
            }
            onResult(result.fold({ "Backup saved." }, { it.message ?: "Couldn't save the backup." }))
        }
    }

    fun restoreBackup(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val json = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Couldn't read the file.")
                }
                backup.restore(json)
            }
            onResult(
                result.fold(
                    { s -> "Restored ${s.settings} settings, ${s.personalWords} dictionary words, ${s.snippets} snippets, ${s.themes} themes" +
                        (if (s.learnedWords > 0) " and ${s.learnedWords} learned words." else ".") },
                    { it.message ?: "Couldn't restore the backup." },
                ),
            )
        }
    }

    fun deleteClip(clip: ClipEntity) {
        viewModelScope.launch { clipboard.delete(clip) }
    }

    fun setClipPinned(clip: ClipEntity, pinned: Boolean) {
        viewModelScope.launch { clipboard.setPinned(clip, pinned) }
    }

    fun setClipFolder(clip: ClipEntity, folder: String?) {
        viewModelScope.launch { clipboard.setFolder(clip, folder) }
    }

    fun clearUnpinnedClips() {
        viewModelScope.launch { clipboard.clearUnpinned() }
    }

    /** Puts a history entry back on the system clipboard (allowed: this app is in the foreground). */
    fun copyClip(clip: ClipEntity) {
        val cm = app.getSystemService(ClipboardManager::class.java) ?: return
        val data = clip.text?.let { ClipData.newPlainText("Jolpai", it) }
            ?: clipboard.contentUri(clip)?.let { ClipData.newUri(app.contentResolver, "Jolpai", it) }
            ?: return
        cm.setPrimaryClip(data)
    }

    fun saveSnippet(title: String, shortcut: String, text: String, id: Long, category: com.yeamin.jolpai.data.SnippetCategory) {
        viewModelScope.launch { clipboard.saveSnippet(title, shortcut, text, id, category) }
    }

    fun deleteSnippet(snippet: SnippetEntity) {
        viewModelScope.launch { clipboard.deleteSnippet(snippet) }
    }

    /** Adds a ready-made pack's snippets that aren't saved yet; [done] gets how many were added. */
    fun addSnippetPack(pack: com.yeamin.jolpai.clipboard.SnippetPack, done: (Int) -> Unit) {
        viewModelScope.launch { done(clipboard.addSnippets(pack.items)) }
    }

    val personalWords: StateFlow<List<PersonalWordEntity>> =
        historyDao.observePersonal().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val learnedWordCount: StateFlow<Int> =
        historyDao.observeLearnedCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _setup = MutableStateFlow(SetupStatus())
    val setup: StateFlow<SetupStatus> = _setup.asStateFlow()

    /** Re-reads the system state; called when the screen resumes or regains focus. */
    fun refreshSetup() {
        val imm = app.getSystemService(InputMethodManager::class.java)
        val enabled = imm.enabledInputMethodList.any { it.packageName == app.packageName }
        val current = Settings.Secure.getString(app.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        _setup.value = SetupStatus(enabled = enabled, selected = current.startsWith(app.packageName + "/"))
    }

    fun setAutoCapitalize(v: Boolean) {
        viewModelScope.launch { repository.setAutoCapitalize(v) }
    }
    fun setDoubleSpacePeriod(v: Boolean) {
        viewModelScope.launch { repository.setDoubleSpacePeriod(v) }
    }
    fun setFestivalThemes(v: Boolean) {
        viewModelScope.launch { repository.setFestivalThemes(v) }
    }
    fun setRememberLastLayout(v: Boolean) {
        viewModelScope.launch { repository.setRememberLastLayout(v) }
    }
    fun setDefaultLayout(v: LayoutId) {
        viewModelScope.launch { repository.setDefaultLayout(v) }
    }
    fun setLayoutEnabled(layout: LayoutId, enabled: Boolean) {
        viewModelScope.launch { repository.setLayoutEnabled(layout, enabled) }
    }
    fun setShowSuggestions(v: Boolean) {
        viewModelScope.launch { repository.setShowSuggestions(v) }
    }
    fun setAutoCorrect(v: Boolean) {
        viewModelScope.launch { repository.setAutoCorrect(v) }
    }
    fun setNextWordPrediction(v: Boolean) {
        viewModelScope.launch { repository.setNextWordPrediction(v) }
    }
    fun setPersonalizedLearning(v: Boolean) {
        viewModelScope.launch { repository.setPersonalizedLearning(v) }
    }
    fun setBlockOffensiveWords(v: Boolean) {
        viewModelScope.launch { repository.setBlockOffensiveWords(v) }
    }

    /** Adds a word to the personal dictionary. Bangla script is detected automatically. */
    fun addPersonalWord(word: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        val language = if (w.any { it in 'ঀ'..'৿' }) Language.BANGLA else Language.ENGLISH
        suggestionResources.history.addPersonal(language, w)
    }

    fun removePersonalWord(entity: PersonalWordEntity) {
        val language = Language.fromStorageKey(entity.language) ?: return
        suggestionResources.history.removePersonal(language, entity.word)
    }

    /** Deletes learned words, word pairs and Avro picks. The personal dictionary is kept. */
    fun clearLearnedData() = suggestionResources.history.clearLearned()
}
