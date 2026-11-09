package com.yeamin.jolpai

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yeamin.jolpai.ui.JolpaiImeService
import com.yeamin.jolpai.core.theme.ThemeRepository
import com.yeamin.jolpai.theme.KeyboardTheme
import com.yeamin.jolpai.theme.BuiltInThemes
import androidx.compose.runtime.remember
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Screen requested by the keyboard (e.g. "Manage" in the clipboard panel). */
    private val requestedScreen = mutableStateOf<Destination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark (Jolpai olive), so the system bars always get light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleScreenExtra(intent)
        setContent {
            JolpaiTheme {
                val setup by viewModel.setup.collectAsStateWithLifecycle()
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val personalWords by viewModel.personalWords.collectAsStateWithLifecycle()
                val learnedCount by viewModel.learnedWordCount.collectAsStateWithLifecycle()
                val clips by viewModel.clips.collectAsStateWithLifecycle()
                val folders by viewModel.clipFolders.collectAsStateWithLifecycle()
                val snippets by viewModel.snippets.collectAsStateWithLifecycle()
                val stickers by viewModel.stickers.collectAsStateWithLifecycle()
                val voiceAvailability by viewModel.voiceAvailability.collectAsStateWithLifecycle()
                val customThemes by viewModel.customThemes.collectAsStateWithLifecycle()
                val systemDark = isSystemInDarkTheme()
                var editingTheme by remember { mutableStateOf<KeyboardTheme?>(null) }

                // A simple back stack of screen names (saveable across rotation).
                var stack by rememberSaveable { mutableStateOf(listOf(Destination.HOME.name)) }
                val screen = Destination.valueOf(stack.last())
                // Which way pages move: forward when opening, backward when going back.
                var popping by remember { mutableStateOf(false) }
                val open: (Destination) -> Unit = {
                    popping = false
                    stack = stack + it.name
                }
                // Each page on the stack keeps its saved UI state (scroll position, collapsed top
                // bar), so going back returns to exactly where you were; a page you leave by going
                // back forgets it, so opening it again starts at the top.
                val pageStates = rememberSaveableStateHolder()
                val back: () -> Unit = {
                    if (stack.size > 1) {
                        popping = true
                        val left = stack.last()
                        stack = stack.dropLast(1)
                        if (left !in stack) pageStates.removeState(left)
                    }
                }
                requestedScreen.value?.let {
                    stack = listOf(Destination.HOME.name, it.name)
                    requestedScreen.value = null
                }
                // Predictive back (Android 13+): the page shrinks and slides with the finger, and
                // springs back if the gesture is cancelled.
                var backProgress by remember { mutableFloatStateOf(0f) }
                var backFromLeft by remember { mutableStateOf(true) }
                PredictiveBackHandler(enabled = stack.size > 1) { events ->
                    try {
                        events.collect { e ->
                            backProgress = e.progress
                            backFromLeft = e.swipeEdge == BackEventCompat.EDGE_LEFT
                        }
                        back()
                    } finally {
                        backProgress = 0f
                    }
                }
                val peek by animateFloatAsState(backProgress, spring(stiffness = Spring.StiffnessMediumLow), label = "peek")
                val theme = ThemeRepository.resolve(settings.themeId, customThemes, systemDark)

                AnimatedContent(
                    targetState = screen,
                    // Black behind the pages: a page that fades while covered reads as dimmed.
                    modifier = Modifier.background(Color.Black).graphicsLayer {
                        val scale = 1f - 0.08f * peek
                        scaleX = scale
                        scaleY = scale
                        translationX = (if (backFromLeft) 1f else -1f) * peek * 24.dp.toPx()
                        shape = RoundedCornerShape((28 * peek).dp)
                        clip = peek > 0f
                    },
                    transitionSpec = {
                        // A page push: the new page glides in over the old one from the right edge,
                        // while the old one drifts a quarter to the left and dims. Back is the exact
                        // reverse, with the leaving page on top sliding off to the right.
                        // The motion starts a few frames late on purpose: the first frame composes and
                        // measures the whole new page (20–55 ms on a Pixel 7 Pro), and starting the
                        // clock during that frame made the page jump halfway in at once.
                        val slide = tween<IntOffset>(PAGE_MS, PAGE_DELAY_MS, PageEasing)
                        if (!popping) {
                            (slideInHorizontally(slide) { w -> w } togetherWith
                                (slideOutHorizontally(slide) { w -> -w / 4 } +
                                    fadeOut(tween(PAGE_MS, PAGE_DELAY_MS, LinearOutSlowInEasing), targetAlpha = 0.35f)))
                                .apply { targetContentZIndex = 1f }
                        } else {
                            ((slideInHorizontally(slide) { w -> -w / 4 } +
                                fadeIn(tween(PAGE_MS, PAGE_DELAY_MS, FastOutLinearInEasing), initialAlpha = 0.35f)) togetherWith
                                slideOutHorizontally(slide) { w -> w })
                                .apply { targetContentZIndex = -1f }
                        }.using(SizeTransform(clip = false))
                    },
                    label = "screen",
                ) { s ->
                    pageStates.SaveableStateProvider(s.name) {
                    when (s) {
                        Destination.HOME -> HomeScreen(
                            setup = setup,
                            settings = settings,
                            themeName = theme.name,
                            learnedWordCount = learnedCount,
                            personalWordCount = personalWords.size,
                            onOpenKeyboardSettings = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                            onPickKeyboard = { getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
                            onOpen = open,
                            themes = listOf(BuiltInThemes.forSystem(systemDark)) + BuiltInThemes.all.filter { it.id != BuiltInThemes.MATERIAL_YOU_LIGHT.id && it.id != BuiltInThemes.MATERIAL_YOU_DARK.id } + customThemes,
                            activeThemeId = theme.id,
                            onSelectTheme = viewModel::selectTheme,
                        )
                        Destination.LANGUAGES -> LanguagesScreen(viewModel, settings, back)
                        Destination.TYPING -> TypingScreen(viewModel, settings, back)
                        Destination.GESTURES -> GestureScreen(viewModel, settings, back)
                        Destination.SUGGESTIONS -> SuggestionsScreen(viewModel, settings, personalWords.size, { open(Destination.DICTIONARY) }, back)
                        Destination.DICTIONARY -> DictionaryScreen(
                            words = personalWords,
                            onAdd = viewModel::addPersonalWord,
                            onRemove = viewModel::removePersonalWord,
                            onBack = back,
                        )
                        Destination.CLIPBOARD_SETTINGS -> ClipboardSettingsScreen(
                            viewModel, settings,
                            onOpenClipboard = { open(Destination.CLIPBOARD) },
                            onOpenSnippets = { open(Destination.SNIPPETS) },
                            onBack = back,
                        )
                        Destination.CLIPBOARD -> ClipboardScreen(
                            clips = clips,
                            folders = folders,
                            historyEnabled = settings.clipboardHistory,
                            onCopy = viewModel::copyClip,
                            onPin = viewModel::setClipPinned,
                            onFolder = viewModel::setClipFolder,
                            onDelete = viewModel::deleteClip,
                            onClearUnpinned = viewModel::clearUnpinnedClips,
                            onBack = back,
                        )
                        Destination.SNIPPETS -> SnippetsScreen(
                            snippets = snippets,
                            onSave = viewModel::saveSnippet,
                            onDelete = viewModel::deleteSnippet,
                            onAddPack = viewModel::addSnippetPack,
                            onBack = back,
                        )
                        Destination.TOOLBAR -> ToolbarScreen(settings.toolbar, viewModel::setToolbar, back)
                        Destination.VOICE -> VoiceScreen(viewModel, settings, voiceAvailability, back)
                        Destination.MEDIA -> MediaScreen(viewModel, settings, stickers, back)
                        Destination.THEMES -> ThemesScreen(viewModel, settings, customThemes, systemDark, onEdit = { editingTheme = it; open(Destination.THEME_EDITOR) }, onBack = back)
                        Destination.THEME_EDITOR -> editingTheme?.let { ThemeEditorScreen(viewModel, settings, it, back) } ?: back()
                        Destination.SIZE -> SizeScreen(viewModel, settings, theme, back)
                        Destination.SOUND -> SoundScreen(viewModel, settings, back)
                        Destination.PRIVACY -> PrivacyScreen(viewModel, settings, learnedCount, back)
                        Destination.BACKUP -> BackupScreen(viewModel, back)
                        Destination.ABOUT -> AboutScreen(back)
                        Destination.TEST_FIELDS -> TestFieldsScreen(back)
                        Destination.STATS -> TypingStatsScreen(back)
                    }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleScreenExtra(intent)
    }

    private fun handleScreenExtra(intent: Intent?) {
        when (intent?.getStringExtra(JolpaiImeService.EXTRA_SCREEN)) {
            "clipboard" -> requestedScreen.value = Destination.CLIPBOARD
            "voice" -> requestedScreen.value = Destination.VOICE
            "gifs", "stickers" -> requestedScreen.value = Destination.MEDIA
            "themes" -> requestedScreen.value = Destination.THEMES
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSetup()
    }

    /** The keyboard picker is a dialog over this screen, so focus returns here when it closes. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) viewModel.refreshSetup()
    }
}

/** Page push timing: long enough to see, short enough not to wait for. */
private const val PAGE_MS = 380

/** Lets the new page's heavy first frame pass before anything moves. */
private const val PAGE_DELAY_MS = 50

/** A quick start that settles gently (iOS/Material page-push feel), no overshoot. */
private val PageEasing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)

/** The app's screens. */

enum class Destination {
    HOME, LANGUAGES, TYPING, GESTURES, SUGGESTIONS, DICTIONARY, THEMES, THEME_EDITOR, SIZE, SOUND,
    CLIPBOARD_SETTINGS, CLIPBOARD, SNIPPETS, TOOLBAR, VOICE, MEDIA, PRIVACY, BACKUP, ABOUT, TEST_FIELDS, STATS,
}
