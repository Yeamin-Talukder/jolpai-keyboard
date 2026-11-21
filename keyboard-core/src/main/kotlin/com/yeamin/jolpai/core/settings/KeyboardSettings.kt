package com.yeamin.jolpai.core.settings

import com.yeamin.jolpai.theme.KeyboardTheme
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.yeamin.jolpai.core.model.ToolbarItem
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.data.ClipRetention
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class KeyboardSettings(
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    /**
     * জাতীয় and প্রভাত: fix vowel signs automatically (`্` + `া` → `আ`, a kar with nothing before
     * it → its vowel, `ে` + `া` → `ো`). Off by default: every key goes in as typed, like Ridmik.
     */
    val banglaAutoVowels: Boolean = false,
    /** Start with the layout used last; otherwise always start with [defaultLayout]. */
    val rememberLastLayout: Boolean = true,
    val defaultLayout: LayoutId = LayoutId.ENGLISH,
    val lastLayout: LayoutId = LayoutId.ENGLISH,
    /** Layouts the 🌐 key cycles through, in [LayoutId] order. Never empty. */
    val enabledLayouts: List<LayoutId> = LayoutId.DEFAULT_ENABLED,
    val showSuggestions: Boolean = true,
    /** Fix obvious English typos when space is pressed. */
    val autoCorrect: Boolean = false,
    val nextWordPrediction: Boolean = true,
    /** Learn words and word pairs from typing (never in password or private fields). */
    val personalizedLearning: Boolean = true,
    val blockOffensiveWords: Boolean = true,
    /** Save copied text (and images) while Jolpai is the active keyboard. */
    val clipboardHistory: Boolean = true,
    val clipboardImages: Boolean = true,
    val clipboardRetention: ClipRetention = ClipRetention.DEFAULT,
    /** Swipe along the space bar to move the cursor; swipe left from backspace to delete words. */
    val cursorSwipe: Boolean = true,
    /** In space-bar trackpad mode (hold space), moving up or down moves the cursor between lines. */
    val trackpadVertical: Boolean = true,
    /** Hold Enter to open the emoji panel (a tap still sends, searches or starts a new line). Off by default. */
    val enterHoldEmoji: Boolean = false,
    /** Hold Enter, then slide, to select text from the cursor (by character sideways, by line up and down). Off by default. */
    val enterHoldSelect: Boolean = false,
    /** How long a key is held (ms) before its hidden characters (corner hint, long-press menu) appear. */
    val longPressMs: Int = DEFAULT_LONG_PRESS_MS,
    /** Predictive touch targets: likely next letters get slightly larger invisible touch areas. */
    val predictiveTouch: Boolean = true,
    /** Learn where this user touches each key (from taps and corrections) to adjust touch areas. */
    val touchCalibration: Boolean = true,
    /** Changes when the user resets the learned touch calibration (the keyboard then clears it). */
    val touchCalibrationReset: Long = 0,
    /** Custom gesture shortcuts (bindings, sensitivity, distances). */
    val gestures: com.yeamin.jolpai.core.gesture.GestureConfig = com.yeamin.jolpai.core.gesture.GestureConfig(),
    /** Toolbar buttons, in order. Items not listed are hidden. */
    val toolbar: List<ToolbarItem> = ToolbarItem.DEFAULT,
    /** Where emoji and clipboard open: above the keyboard (the keys stay) or in place of the keys. */
    val panelPlacement: PanelPlacement = PanelPlacement.ABOVE,
    /** Which keyboard the bottom row, space bar and key hints follow (Jolpai, Ridmik or Gboard). */
    val keyLook: KeyLook = KeyLook.JOLPAI,
    /** Default emoji skin tone: 0 = yellow, 1–5 = light to dark. */
    val emojiSkinTone: Int = 0,
    /** Suggest emoji for the word being typed (English and Bangla keywords). */
    val emojiSuggestions: Boolean = true,
    /** Use the on-device next-word model (English): next words, ranking by context, word choice, emoji by meaning. */
    val smartPredictions: Boolean = true,
    val voiceLanguage: VoiceLanguage = VoiceLanguage.AUTO,
    /** The user's own GIPHY API key; GIF search is off until one is set. Stored only on the device. */
    val giphyApiKey: String = "",
    /** "system" (Material You, light/dark with the phone), a built-in id ("builtin:ocean") or "custom:<n>". Default: Olive night. */
    val themeId: String = KeyboardTheme.DEFAULT_ID,
    /** On festival days (Pohela Boishakh, 21 February, Eid, Ramadan, 26 March, 16 December) use that day's theme. */
    val festivalThemes: Boolean = true,
    /** Keyboard height, percent of normal (70–140), separately for portrait and landscape. */
    val heightPortrait: Int = 100,
    val heightLandscape: Int = 100,
    /** A row of digits above the letters. */
    val numberRow: Boolean = false,
    val oneHanded: OneHanded = OneHanded.OFF,
    val keyFont: KeyFont = KeyFont.SYSTEM,
    /**
     * A separate font for Bangla letters ("Mix fonts"), or null to use [keyFont] for both
     * (see [banglaLettersFont]). Always a font with Bangla letters.
     */
    val banglaKeyFont: KeyFont? = null,
    /**
     * The font file the user picked for [KeyFont.CUSTOM] (its name as shown in settings), or null.
     * The file itself is kept in the app's storage, not in backups; [customKeyFontVersion]
     * changes each time a file is picked or removed, so the keyboard reloads it.
     */
    val customKeyFontName: String? = null,
    val customKeyFontVersion: Long = 0,
    val soundPack: SoundPack = SoundPack.SYSTEM,
    val splitKeys: SplitKeys = SplitKeys.OFF,
    /** Floating keyboard (a movable, smaller keyboard over the app), with its position. */
    val floating: Boolean = false,
    val floatX: Float = 0.5f,
    val floatY: Float = 0.0f,
    val haptics: HapticLevel = HapticLevel.SYSTEM,
    val keySound: Boolean = true,
    /** 0–1. */
    val soundVolume: Float = 0.4f,
) {
    /** The layout to open with, always one of [enabledLayouts]. */
    val startLayout: LayoutId
        get() {
            val wanted = if (rememberLastLayout) lastLayout else defaultLayout
            return if (wanted in enabledLayouts) wanted else enabledLayouts.first()
        }
}

enum class OneHanded(val storageKey: String) { OFF("off"), LEFT("left"), RIGHT("right") }

/**
 * Where the emoji and clipboard panels open. [ABOVE] (default since 2026-11-07): a section above the
 * top bar, over the app, with the keys still in place. [KEYS]: the panel takes the keys' place,
 * as it did before; the keyboard doesn't grow over the app.
 */
enum class PanelPlacement(val storageKey: String, val label: String, val summary: String) {
    ABOVE("above", "Above the keyboard", "Opens over the app; the keys stay, and any key closes it and types"),
    KEYS("keys", "In place of the keys", "Takes the keys' space; tap the toolbar button again for the keys"),
    ;

    companion object {
        fun fromStorageKey(key: String?): PanelPlacement = entries.firstOrNull { it.storageKey == key } ?: ABOVE
    }
}

/**
 * How the bottom row, the space bar and the letter hints look, copied from the keyboards
 * people switch from (checked against Ridmik and Gboard on a Pixel 7 Pro, 2026-11-21).
 *
 * - [JOLPAI]: `?123 😊 , space . ↵`; the language button is in the top bar.
 * - [RIDMIK]: the same keys, but the space bar shows `◂ English ▸` and sliding along it
 *   changes the language (holding it still moves the cursor); English letters show Ridmik's
 *   symbol hints (a @, s #, … m /).
 * - [GBOARD]: `?123 , 😊 space . ↵` (comma before emoji).
 */
enum class KeyLook(val storageKey: String) {
    JOLPAI("jolpai"),
    RIDMIK("ridmik"),
    GBOARD("gboard"),
    ;

    companion object {
        fun fromStorageKey(key: String?): KeyLook = entries.firstOrNull { it.storageKey == key } ?: JOLPAI
    }
}


/**
 * The typeface on the keys: the phone's, one of the bundled fonts (Google Fonts and other open
 * fonts, SIL Open Font License or Apache 2.0), or a font file the user picked ([CUSTOM]).
 * [bangla] / [english] say which letters a font has; keys whose letters a font lacks use the
 * phone's font. With [KeyboardSettings.banglaKeyFont] set, Bangla letters use a second font.
 */
enum class KeyFont(val storageKey: String, val label: String, val group: Group, val english: Boolean = true) {
    SYSTEM("system", "Phone default", Group.OTHER),

    // Bangla and English letters, for everyday typing.
    HIND_SILIGURI("hind", "Hind Siliguri", Group.BANGLA),
    TIRO_BANGLA("tiro", "Tiro Bangla", Group.BANGLA),
    KALPURUSH("kalpurush", "Kalpurush", Group.BANGLA),
    BALOO_DA("balooda", "Baloo Da 2", Group.BANGLA),
    MINA("mina", "Mina", Group.BANGLA),
    KALAA("kalaa", "Kalaa", Group.BANGLA),
    NOTO_SERIF_BENGALI("notoserifbn", "Noto Serif Bengali", Group.BANGLA, english = false),

    // Stylish Bangla (with English letters too).
    GALADA("galada", "Galada", Group.BANGLA_STYLISH),
    ATMA("atma", "Atma", Group.BANGLA_STYLISH),
    ALKATRA("alkatra", "Alkatra", Group.BANGLA_STYLISH),
    TIRO_BANGLA_ITALIC("tiroitalic", "Tiro Bangla Italic", Group.BANGLA_STYLISH),
    BALOO_DA_HEAVY("baloodaheavy", "Baloo Da 2 Heavy", Group.BANGLA_STYLISH),

    // English letters only.
    POPPINS("poppins", "Poppins", Group.ENGLISH),
    QUICKSAND("quicksand", "Quicksand", Group.ENGLISH),
    SPACE_MONO("spacemono", "Space Mono", Group.ENGLISH),

    // Stylish English.
    PACIFICO("pacifico", "Pacifico", Group.STYLISH),
    DANCING_SCRIPT("dancing", "Dancing Script", Group.STYLISH),
    LOBSTER("lobster", "Lobster", Group.STYLISH),
    SATISFY("satisfy", "Satisfy", Group.STYLISH),
    COURGETTE("courgette", "Courgette", Group.STYLISH),
    KAUSHAN_SCRIPT("kaushan", "Kaushan Script", Group.STYLISH),
    PERMANENT_MARKER("marker", "Permanent Marker", Group.STYLISH),
    INDIE_FLOWER("indieflower", "Indie Flower", Group.STYLISH),
    SHADOWS_INTO_LIGHT("shadows", "Shadows Into Light", Group.STYLISH),
    FREDOKA("fredoka", "Fredoka", Group.STYLISH),
    BANGERS("bangers", "Bangers", Group.STYLISH),
    ABRIL_FATFACE("abril", "Abril Fatface", Group.STYLISH),
    RIGHTEOUS("righteous", "Righteous", Group.STYLISH),
    ORBITRON("orbitron", "Orbitron", Group.STYLISH),
    CREEPSTER("creepster", "Creepster", Group.STYLISH),
    PRESS_START("pressstart", "Press Start 2P", Group.STYLISH),

    CUSTOM("custom", "Your own font", Group.OTHER),
    ;

    enum class Group(val title: String) {
        BANGLA("Bangla and English"),
        BANGLA_STYLISH("Stylish Bangla"),
        ENGLISH("English"),
        STYLISH("Stylish English"),
        OTHER(""),
    }

    /** Has Bangla letters (the phone's font, a custom font, or a Bangla font). */
    val bangla: Boolean get() = group == Group.BANGLA || group == Group.BANGLA_STYLISH || group == Group.OTHER

    companion object {
        fun fromStorageKey(key: String?): KeyFont = entries.firstOrNull { it.storageKey == key } ?: SYSTEM

        /** The bundled fonts of [group] (never SYSTEM or CUSTOM). */
        fun bundled(group: Group): List<KeyFont> = if (group == Group.OTHER) emptyList() else entries.filter { it.group == group }

        /** Groups listed for English letters / for Bangla letters when the two use different fonts. */
        val ENGLISH_GROUPS = listOf(Group.BANGLA, Group.BANGLA_STYLISH, Group.ENGLISH, Group.STYLISH)
        val BANGLA_GROUPS = listOf(Group.BANGLA, Group.BANGLA_STYLISH)
    }
}

/**
 * The font for Bangla letters: the separate Bangla font if one is set, else the main font when
 * it has Bangla letters, else the phone's font.
 */
val KeyboardSettings.banglaLettersFont: KeyFont get() = banglaLettersFont(keyFont, banglaKeyFont)

fun banglaLettersFont(main: KeyFont, bangla: KeyFont?): KeyFont = bangla ?: if (main.bangla) main else KeyFont.SYSTEM

/** How key presses sound: the phone's click, or one of Jolpai's own sounds (made in code, no files). */
enum class SoundPack(val storageKey: String, val label: String) {
    SYSTEM("system", "Phone's click"),
    SOFT("soft", "Soft tap"),
    TYPEWRITER("typewriter", "Typewriter"),
    BUBBLE("bubble", "Bubble"),
    WOOD("wood", "Wooden"),
}

/** Splitting the keys into two halves (for thumbs on a wide screen). */
enum class SplitKeys(val storageKey: String, val label: String) {
    OFF("off", "Never"),
    LANDSCAPE("landscape", "In landscape"),
    ALWAYS("always", "Always"),
}

/** Default hold time (ms) before a key shows its hidden characters. */
const val DEFAULT_LONG_PRESS_MS = 200

/** Hold times the settings allow (ms): quick for Bangla hint letters, slower for careful typists. */
val LONG_PRESS_RANGE = 150..800

/** Key-press vibration. SYSTEM uses the phone's own keyboard vibration and its setting. */
enum class HapticLevel(val storageKey: String, val label: String, val millis: Long, val amplitude: Int) {
    OFF("off", "Off", 0, 0),
    SYSTEM("system", "Phone default", 0, 0),
    LIGHT("light", "Light", 8, 40),
    MEDIUM("medium", "Medium", 12, 110),
    STRONG("strong", "Strong", 18, 220),
}

/** Which language voice typing listens for. AUTO follows the active layout. */
enum class VoiceLanguage(val storageKey: String, val label: String) {
    AUTO("auto", "Same as the keyboard layout"),
    BANGLA("bn-BD", "বাংলা (Bangladesh)"),
    ENGLISH("en-US", "English");

    companion object {
        fun fromStorageKey(key: String?): VoiceLanguage = entries.firstOrNull { it.storageKey == key } ?: AUTO
    }
}

/** Typing settings, stored on the device with DataStore. Shared by the keyboard service and the settings app. */
@Singleton
class KeyboardSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<KeyboardSettings> = dataStore.data
        .map { p ->
            KeyboardSettings(
                autoCapitalize = p[AUTO_CAPITALIZE] ?: true,
                doubleSpacePeriod = p[DOUBLE_SPACE_PERIOD] ?: true,
                banglaAutoVowels = p[BANGLA_AUTO_VOWELS] ?: false,
                rememberLastLayout = p[REMEMBER_LAST_LAYOUT] ?: true,
                defaultLayout = LayoutId.fromStorageKey(p[DEFAULT_LAYOUT]) ?: LayoutId.ENGLISH,
                lastLayout = LayoutId.fromStorageKey(p[LAST_LAYOUT]) ?: LayoutId.ENGLISH,
                enabledLayouts = p[ENABLED_LAYOUTS]
                    ?.let { keys -> LayoutId.entries.filter { it.storageKey in keys } }
                    ?.ifEmpty { null }
                    ?: LayoutId.DEFAULT_ENABLED,
                showSuggestions = p[SHOW_SUGGESTIONS] ?: true,
                autoCorrect = p[AUTO_CORRECT] ?: false,
                nextWordPrediction = p[NEXT_WORD] ?: true,
                personalizedLearning = p[LEARNING] ?: true,
                blockOffensiveWords = p[BLOCK_OFFENSIVE] ?: true,
                clipboardHistory = p[CLIPBOARD_HISTORY] ?: true,
                clipboardImages = p[CLIPBOARD_IMAGES] ?: true,
                clipboardRetention = ClipRetention.fromStorageKey(p[CLIPBOARD_RETENTION]),
                cursorSwipe = p[CURSOR_SWIPE] ?: true,
                trackpadVertical = p[TRACKPAD_VERTICAL] ?: true,
                enterHoldEmoji = p[ENTER_HOLD_EMOJI] ?: false,
                enterHoldSelect = p[ENTER_HOLD_SELECT] ?: false,
                longPressMs = (p[LONG_PRESS] ?: DEFAULT_LONG_PRESS_MS).coerceIn(LONG_PRESS_RANGE),
                predictiveTouch = p[PREDICTIVE_TOUCH] ?: true,
                touchCalibration = p[TOUCH_CALIBRATION] ?: true,
                touchCalibrationReset = p[TOUCH_CALIBRATION_RESET] ?: 0L,
                gestures = com.yeamin.jolpai.core.gesture.GestureConfig.decode(p[GESTURES]),
                // A saved order keeps working when new tools are added: tools the user has never
                // seen (not in TOOLBAR_SEEN) are appended, so e.g. Translate appears once.
                toolbar = p[TOOLBAR]?.let { s ->
                    val saved = s.split(',').mapNotNull { ToolbarItem.fromStorageKey(it) }.distinct()
                    val seen = p[TOOLBAR_SEEN] ?: LEGACY_TOOLBAR_KEYS
                    saved + ToolbarItem.DEFAULT.filter { it.storageKey !in seen && it !in saved }
                }
                    ?: ToolbarItem.DEFAULT,
                panelPlacement = PanelPlacement.fromStorageKey(p[PANEL_PLACEMENT]),
                keyLook = KeyLook.fromStorageKey(p[KEY_LOOK]),
                emojiSkinTone = (p[EMOJI_TONE] ?: 0).coerceIn(0, 5),
                emojiSuggestions = p[EMOJI_SUGGESTIONS] ?: true,
                smartPredictions = p[SMART_PREDICTIONS] ?: true,
                voiceLanguage = VoiceLanguage.fromStorageKey(p[VOICE_LANGUAGE]),
                giphyApiKey = p[GIPHY_KEY].orEmpty(),
                themeId = p[THEME_ID] ?: KeyboardTheme.DEFAULT_ID,
                festivalThemes = p[FESTIVAL_THEMES] ?: true,
                heightPortrait = (p[HEIGHT_PORTRAIT] ?: 100).coerceIn(70, 140),
                heightLandscape = (p[HEIGHT_LANDSCAPE] ?: 100).coerceIn(70, 140),
                numberRow = p[NUMBER_ROW] ?: false,
                oneHanded = OneHanded.entries.firstOrNull { it.storageKey == p[ONE_HANDED] } ?: OneHanded.OFF,
                keyFont = KeyFont.fromStorageKey(p[KEY_FONT]),
                banglaKeyFont = p[BANGLA_KEY_FONT]?.let(KeyFont::fromStorageKey)?.takeIf { it.bangla },
                soundPack = SoundPack.entries.firstOrNull { it.storageKey == p[SOUND_PACK] } ?: SoundPack.SYSTEM,
                splitKeys = SplitKeys.entries.firstOrNull { it.storageKey == p[SPLIT_KEYS] } ?: SplitKeys.OFF,
                floating = p[FLOATING] ?: false,
                floatX = (p[FLOAT_X] ?: 0.5f).coerceIn(0f, 1f),
                floatY = (p[FLOAT_Y] ?: 0f).coerceIn(0f, 1f),
                haptics = HapticLevel.entries.firstOrNull { it.storageKey == p[HAPTICS] } ?: HapticLevel.SYSTEM,
                keySound = p[KEY_SOUND] ?: true,
                soundVolume = (p[SOUND_VOLUME] ?: 0.4f).coerceIn(0f, 1f),
                customKeyFontName = p[CUSTOM_FONT_NAME],
                customKeyFontVersion = p[CUSTOM_FONT_VERSION] ?: 0L,
            )
        }
        .distinctUntilChanged()

    /** Turns a layout on or off. The last enabled layout cannot be turned off. */
    suspend fun setLayoutEnabled(layout: LayoutId, enabled: Boolean) {
        dataStore.edit { p ->
            val current = p[ENABLED_LAYOUTS] ?: LayoutId.DEFAULT_ENABLED.map { it.storageKey }.toSet()
            val next = if (enabled) current + layout.storageKey else current - layout.storageKey
            if (next.isNotEmpty()) p[ENABLED_LAYOUTS] = next
        }
    }

    suspend fun setAutoCapitalize(value: Boolean) = dataStore.edit { it[AUTO_CAPITALIZE] = value }
    suspend fun setDoubleSpacePeriod(value: Boolean) = dataStore.edit { it[DOUBLE_SPACE_PERIOD] = value }
    suspend fun setBanglaAutoVowels(value: Boolean) = dataStore.edit { it[BANGLA_AUTO_VOWELS] = value }
    suspend fun setRememberLastLayout(value: Boolean) = dataStore.edit { it[REMEMBER_LAST_LAYOUT] = value }
    suspend fun setDefaultLayout(value: LayoutId) = dataStore.edit { it[DEFAULT_LAYOUT] = value.storageKey }
    suspend fun setLastLayout(value: LayoutId) = dataStore.edit { it[LAST_LAYOUT] = value.storageKey }
    suspend fun setShowSuggestions(value: Boolean) = dataStore.edit { it[SHOW_SUGGESTIONS] = value }
    suspend fun setAutoCorrect(value: Boolean) = dataStore.edit { it[AUTO_CORRECT] = value }
    suspend fun setNextWordPrediction(value: Boolean) = dataStore.edit { it[NEXT_WORD] = value }
    suspend fun setPersonalizedLearning(value: Boolean) = dataStore.edit { it[LEARNING] = value }
    suspend fun setBlockOffensiveWords(value: Boolean) = dataStore.edit { it[BLOCK_OFFENSIVE] = value }
    suspend fun setClipboardHistory(value: Boolean) = dataStore.edit { it[CLIPBOARD_HISTORY] = value }
    suspend fun setClipboardImages(value: Boolean) = dataStore.edit { it[CLIPBOARD_IMAGES] = value }
    suspend fun setClipboardRetention(value: ClipRetention) = dataStore.edit { it[CLIPBOARD_RETENTION] = value.storageKey }
    suspend fun setCursorSwipe(value: Boolean) = dataStore.edit { it[CURSOR_SWIPE] = value }
    suspend fun setTrackpadVertical(value: Boolean) = dataStore.edit { it[TRACKPAD_VERTICAL] = value }
    suspend fun setEnterHoldEmoji(value: Boolean) = dataStore.edit { it[ENTER_HOLD_EMOJI] = value }
    suspend fun setEnterHoldSelect(value: Boolean) = dataStore.edit { it[ENTER_HOLD_SELECT] = value }
    suspend fun setLongPressMs(value: Int) = dataStore.edit { it[LONG_PRESS] = value.coerceIn(LONG_PRESS_RANGE) }
    suspend fun setPredictiveTouch(value: Boolean) = dataStore.edit { it[PREDICTIVE_TOUCH] = value }
    suspend fun setTouchCalibration(value: Boolean) = dataStore.edit { it[TOUCH_CALIBRATION] = value }
    suspend fun resetTouchCalibration() = dataStore.edit { it[TOUCH_CALIBRATION_RESET] = System.currentTimeMillis() }
    suspend fun setGestures(config: com.yeamin.jolpai.core.gesture.GestureConfig) = dataStore.edit { it[GESTURES] = config.encode() }
    suspend fun toggleNumberRow() = dataStore.edit { it[NUMBER_ROW] = !(it[NUMBER_ROW] ?: false) }
    suspend fun setToolbar(items: List<ToolbarItem>) = dataStore.edit {
        it[TOOLBAR] = items.joinToString(",") { i -> i.storageKey }
        it[TOOLBAR_SEEN] = ToolbarItem.entries.map { i -> i.storageKey }.toSet()
    }
    suspend fun setPanelPlacement(value: PanelPlacement) = dataStore.edit { it[PANEL_PLACEMENT] = value.storageKey }
    suspend fun setEmojiSkinTone(value: Int) = dataStore.edit { it[EMOJI_TONE] = value.coerceIn(0, 5) }
    suspend fun setEmojiSuggestions(value: Boolean) = dataStore.edit { it[EMOJI_SUGGESTIONS] = value }
    suspend fun setSmartPredictions(value: Boolean) = dataStore.edit { it[SMART_PREDICTIONS] = value }
    suspend fun setVoiceLanguage(value: VoiceLanguage) = dataStore.edit { it[VOICE_LANGUAGE] = value.storageKey }
    suspend fun setGiphyApiKey(value: String) = dataStore.edit { it[GIPHY_KEY] = value.trim() }
    suspend fun setThemeId(value: String) = dataStore.edit { it[THEME_ID] = value }
    suspend fun setFestivalThemes(value: Boolean) = dataStore.edit { it[FESTIVAL_THEMES] = value }
    suspend fun setHeight(portrait: Boolean, percent: Int) = dataStore.edit {
        it[if (portrait) HEIGHT_PORTRAIT else HEIGHT_LANDSCAPE] = percent.coerceIn(70, 140)
    }
    suspend fun setNumberRow(value: Boolean) = dataStore.edit { it[NUMBER_ROW] = value }
    suspend fun setOneHanded(value: OneHanded) = dataStore.edit { it[ONE_HANDED] = value.storageKey }
    suspend fun setKeyFont(value: KeyFont) = dataStore.edit { it[KEY_FONT] = value.storageKey }
    suspend fun setSoundPack(value: SoundPack) = dataStore.edit { it[SOUND_PACK] = value.storageKey }
    suspend fun setSplitKeys(value: SplitKeys) = dataStore.edit { it[SPLIT_KEYS] = value.storageKey }
    suspend fun setFloating(value: Boolean) = dataStore.edit { it[FLOATING] = value }
    suspend fun setFloatPosition(x: Float, y: Float) = dataStore.edit {
        it[FLOAT_X] = x.coerceIn(0f, 1f)
        it[FLOAT_Y] = y.coerceIn(0f, 1f)
    }
    suspend fun setHaptics(value: HapticLevel) = dataStore.edit { it[HAPTICS] = value.storageKey }
    suspend fun setKeySound(value: Boolean) = dataStore.edit { it[KEY_SOUND] = value }
    suspend fun setSoundVolume(value: Float) = dataStore.edit { it[SOUND_VOLUME] = value.coerceIn(0f, 1f) }

    /** A separate font for Bangla letters, or null to use the main key font for both. */
    suspend fun setBanglaKeyFont(value: KeyFont?) = dataStore.edit {
        if (value == null) it.remove(BANGLA_KEY_FONT) else it[BANGLA_KEY_FONT] = value.storageKey
    }

    /**
     * A newly picked custom key font: remembers its name, selects it (for Bangla letters when
     * [forBangla], else as the main font), and makes the keyboard reload it.
     */
    suspend fun setCustomKeyFont(name: String, forBangla: Boolean = false) = dataStore.edit {
        it[CUSTOM_FONT_NAME] = name
        it[CUSTOM_FONT_VERSION] = (it[CUSTOM_FONT_VERSION] ?: 0L) + 1
        if (forBangla) it[BANGLA_KEY_FONT] = KeyFont.CUSTOM.storageKey else it[KEY_FONT] = KeyFont.CUSTOM.storageKey
    }

    /** The custom key font was removed: back to the phone's font wherever it was in use. */
    suspend fun clearCustomKeyFont() = dataStore.edit {
        it.remove(CUSTOM_FONT_NAME)
        it[CUSTOM_FONT_VERSION] = (it[CUSTOM_FONT_VERSION] ?: 0L) + 1
        if (it[KEY_FONT] == KeyFont.CUSTOM.storageKey) it[KEY_FONT] = KeyFont.SYSTEM.storageKey
        if (it[BANGLA_KEY_FONT] == KeyFont.CUSTOM.storageKey) it[BANGLA_KEY_FONT] = KeyFont.SYSTEM.storageKey
    }

    /**
     * Whether the welcome flow has been finished or skipped: null until a choice was saved
     * (a fresh install, or an install from before the welcome flow existed).
     */
    val welcomeDone: Flow<Boolean?> = dataStore.data.map { it[WELCOME_DONE] }.distinctUntilChanged()

    suspend fun setWelcomeDone(value: Boolean) = dataStore.edit { it[WELCOME_DONE] = value }

    /** Saves the welcome flow's answers in one step, so the keyboard sees them all at once. */
    suspend fun applyWelcome(choices: WelcomeChoices) = dataStore.edit { p ->
        p[ENABLED_LAYOUTS] = choices.layouts.map { it.storageKey }.toSet()
        p[LAST_LAYOUT] = choices.startLayout.storageKey
        p[DEFAULT_LAYOUT] = choices.startLayout.storageKey
        p[AUTO_CORRECT] = choices.autoCorrect
        p[NUMBER_ROW] = choices.numberRow
        p[KEY_SOUND] = choices.keySound
        p[HAPTICS] = (if (choices.vibrate) HapticLevel.SYSTEM else HapticLevel.OFF).storageKey
        p[TOOLBAR] = choices.style.toolbar.joinToString(",") { it.storageKey }
        p[TOOLBAR_SEEN] = ToolbarItem.entries.map { it.storageKey }.toSet()
        p[PANEL_PLACEMENT] = choices.style.panelPlacement.storageKey
        p[KEY_LOOK] = choices.style.look.storageKey
    }

    /** Switches style: toolbar, where panels open, and the bottom row's look. */
    suspend fun applyStyle(style: KeyboardStyle) = dataStore.edit { p ->
        p[TOOLBAR] = style.toolbar.joinToString(",") { it.storageKey }
        p[TOOLBAR_SEEN] = ToolbarItem.entries.map { it.storageKey }.toSet()
        p[PANEL_PLACEMENT] = style.panelPlacement.storageKey
        p[KEY_LOOK] = style.look.storageKey
    }

    /**
     * Every setting as a plain value, for a backup file. The GIPHY API key is a secret and
     * is left out.
     */
    suspend fun exportAll(): Map<String, Any> =
        dataStore.data.first().asMap()
            .filterKeys { it.name in BACKUP_KEYS }
            .mapKeys { it.key.name }

    /** Restores settings from a backup. Unknown names and wrong types are ignored. */
    suspend fun importAll(values: Map<String, Any>) {
        dataStore.edit { p ->
            for ((name, value) in values) {
                if (name !in BACKUP_KEYS) continue
                when (value) {
                    is Boolean -> p[booleanPreferencesKey(name)] = value
                    is Int -> p[intPreferencesKey(name)] = value
                    is Float -> p[floatPreferencesKey(name)] = value
                    is String -> p[stringPreferencesKey(name)] = value
                    is Set<*> -> p[stringSetPreferencesKey(name)] = value.filterIsInstance<String>().toSet()
                }
            }
        }
    }

    private companion object {
        val AUTO_CAPITALIZE = booleanPreferencesKey("auto_capitalize")
        val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
        val BANGLA_AUTO_VOWELS = booleanPreferencesKey("bangla_auto_vowels")
        val FESTIVAL_THEMES = booleanPreferencesKey("festival_themes")
        val REMEMBER_LAST_LAYOUT = booleanPreferencesKey("remember_last_layout")
        val DEFAULT_LAYOUT = stringPreferencesKey("default_layout")
        val LAST_LAYOUT = stringPreferencesKey("last_layout")
        val ENABLED_LAYOUTS = stringSetPreferencesKey("enabled_layouts")
        val SHOW_SUGGESTIONS = booleanPreferencesKey("show_suggestions")
        val AUTO_CORRECT = booleanPreferencesKey("auto_correct")
        val NEXT_WORD = booleanPreferencesKey("next_word_prediction")
        val LEARNING = booleanPreferencesKey("personalized_learning")
        val BLOCK_OFFENSIVE = booleanPreferencesKey("block_offensive_words")
        val CLIPBOARD_HISTORY = booleanPreferencesKey("clipboard_history")
        val CLIPBOARD_IMAGES = booleanPreferencesKey("clipboard_images")
        val CLIPBOARD_RETENTION = stringPreferencesKey("clipboard_retention")
        val CURSOR_SWIPE = booleanPreferencesKey("cursor_swipe")
        val TRACKPAD_VERTICAL = booleanPreferencesKey("trackpad_vertical")
        val ENTER_HOLD_EMOJI = booleanPreferencesKey("enter_hold_emoji")
        val ENTER_HOLD_SELECT = booleanPreferencesKey("enter_hold_select")
        val LONG_PRESS = intPreferencesKey("long_press_ms")
        val PREDICTIVE_TOUCH = booleanPreferencesKey("predictive_touch")
        val TOUCH_CALIBRATION = booleanPreferencesKey("touch_calibration")
        val TOUCH_CALIBRATION_RESET = longPreferencesKey("touch_calibration_reset")
        val GESTURES = stringPreferencesKey("gesture_config")
        val TOOLBAR = stringPreferencesKey("toolbar")
        val PANEL_PLACEMENT = stringPreferencesKey("panel_placement")
        val KEY_LOOK = stringPreferencesKey("key_look")

        /** Every tool the user's saved toolbar order already knew about (newer ones get appended). */
        val TOOLBAR_SEEN = stringSetPreferencesKey("toolbar_seen")

        /** The tools that existed before TOOLBAR_SEEN was introduced. */
        val LEGACY_TOOLBAR_KEYS = setOf("emoji", "clipboard", "voice", "edit")
        val EMOJI_TONE = intPreferencesKey("emoji_skin_tone")
        val EMOJI_SUGGESTIONS = booleanPreferencesKey("emoji_suggestions")
        val SMART_PREDICTIONS = booleanPreferencesKey("smart_predictions")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
        val GIPHY_KEY = stringPreferencesKey("giphy_api_key")
        val THEME_ID = stringPreferencesKey("theme_id")
        val HEIGHT_PORTRAIT = intPreferencesKey("height_portrait")
        val HEIGHT_LANDSCAPE = intPreferencesKey("height_landscape")
        val NUMBER_ROW = booleanPreferencesKey("number_row")
        val ONE_HANDED = stringPreferencesKey("one_handed")
        val KEY_FONT = stringPreferencesKey("key_font")
        val BANGLA_KEY_FONT = stringPreferencesKey("bangla_key_font")
        val SOUND_PACK = stringPreferencesKey("sound_pack")
        val SPLIT_KEYS = stringPreferencesKey("split_keys")
        val FLOATING = booleanPreferencesKey("floating")
        val FLOAT_X = floatPreferencesKey("float_x")
        val FLOAT_Y = floatPreferencesKey("float_y")
        val HAPTICS = stringPreferencesKey("haptics")
        val KEY_SOUND = booleanPreferencesKey("key_sound")
        val SOUND_VOLUME = floatPreferencesKey("sound_volume")

        /** Not in backups: the font file isn't backed up, so a restored "custom" falls back to the phone's font. */
        val CUSTOM_FONT_NAME = stringPreferencesKey("custom_key_font_name")
        val CUSTOM_FONT_VERSION = longPreferencesKey("custom_key_font_version")

        /** Not in backups: a restored phone has already been through the welcome flow or skips it. */
        val WELCOME_DONE = booleanPreferencesKey("welcome_done")

        /** Settings that go into a backup file (everything except the GIPHY key). */
        val BACKUP_KEYS: Set<String> = listOf(
            AUTO_CAPITALIZE, DOUBLE_SPACE_PERIOD, BANGLA_AUTO_VOWELS, REMEMBER_LAST_LAYOUT, DEFAULT_LAYOUT, LAST_LAYOUT, ENABLED_LAYOUTS,
            SHOW_SUGGESTIONS, AUTO_CORRECT, NEXT_WORD, LEARNING, BLOCK_OFFENSIVE, CLIPBOARD_HISTORY, CLIPBOARD_IMAGES,
            CLIPBOARD_RETENTION, CURSOR_SWIPE, TRACKPAD_VERTICAL, ENTER_HOLD_EMOJI, ENTER_HOLD_SELECT, LONG_PRESS, PREDICTIVE_TOUCH, TOUCH_CALIBRATION, GESTURES, TOOLBAR, TOOLBAR_SEEN, PANEL_PLACEMENT, KEY_LOOK, EMOJI_TONE, EMOJI_SUGGESTIONS, SMART_PREDICTIONS, VOICE_LANGUAGE, THEME_ID,
            HEIGHT_PORTRAIT, HEIGHT_LANDSCAPE, NUMBER_ROW, ONE_HANDED, FLOATING, FLOAT_X, FLOAT_Y, HAPTICS, KEY_SOUND, SOUND_VOLUME,
            KEY_FONT, BANGLA_KEY_FONT, SOUND_PACK, SPLIT_KEYS, FESTIVAL_THEMES,
        ).map { it.name }.toSet()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object KeyboardSettingsModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("keyboard_settings") })
}
