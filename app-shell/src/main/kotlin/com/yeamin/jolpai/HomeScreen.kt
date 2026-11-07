package com.yeamin.jolpai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.yeamin.jolpai.theme.KeyboardTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.settings.HapticLevel
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.core.settings.OneHanded
import com.yeamin.jolpai.ui.R as UiR

/** The settings home: setup status, a field to try the keyboard, and every settings area. */
@Composable
fun HomeScreen(
    setup: SetupStatus,
    settings: KeyboardSettings,
    themeName: String,
    learnedWordCount: Int,
    personalWordCount: Int,
    onOpenKeyboardSettings: () -> Unit,
    onPickKeyboard: () -> Unit,
    onOpen: (Destination) -> Unit,
    themes: List<KeyboardTheme> = emptyList(),
    activeThemeId: String = "",
    onSelectTheme: (String) -> Unit = {},
) {
    SettingsScaffold("Jolpai Keyboard", onBack = null) {
        SetupCard(setup, onOpenKeyboardSettings, onPickKeyboard)
        TryField(onMore = { onOpen(Destination.TEST_FIELDS) })

        SettingsGroup("Typing", tone = GroupTone.PRIMARY) {
            SettingsItem(
                "Languages & layouts",
                settings.enabledLayouts.joinToString(" · ") { it.displayName },
                UiR.drawable.ms_language,
            ) { onOpen(Destination.LANGUAGES) }
            SettingsItem(
                "Typing & gestures",
                listOfNotNull(
                    "Auto-capitalise".takeIf { settings.autoCapitalize },
                    "double-space full stop".takeIf { settings.doubleSpacePeriod },
                    "swipe to delete".takeIf { settings.cursorSwipe },
                ).joinToString(", ").ifEmpty { "Plain typing" }.replaceFirstChar { it.uppercase() },
                UiR.drawable.ms_keyboard,
            ) { onOpen(Destination.TYPING) }
            SettingsItem(
                "Custom gestures",
                if (!settings.gestures.enabled) "Off" else "${settings.gestures.activeCount(com.yeamin.jolpai.core.gesture.BuiltIns(settings.cursorSwipe, settings.doubleSpacePeriod))} active shortcuts",
                UiR.drawable.ms_gesture,
            ) { onOpen(Destination.GESTURES) }
            SettingsItem(
                "Suggestions & autocorrect",
                when {
                    !settings.showSuggestions -> "Off"
                    settings.autoCorrect -> "Suggestions and autocorrect on"
                    else -> "Suggestions on, autocorrect off"
                },
                UiR.drawable.ms_spellcheck,
            ) { onOpen(Destination.SUGGESTIONS) }
            SettingsItem(
                "Personal dictionary",
                "$personalWordCount word${if (personalWordCount == 1) "" else "s"}",
                UiR.drawable.ms_menu_book,
            ) { onOpen(Destination.DICTIONARY) }
            SettingsItem(
                "Typing stats",
                "Words typed, by day and language",
                UiR.drawable.ms_history,
            ) { onOpen(Destination.STATS) }
        }

        if (themes.isNotEmpty()) QuickThemes(themes, activeThemeId, onSelectTheme)

        SettingsGroup("Look & feel", tone = GroupTone.TERTIARY) {
            SettingsItem("Themes", themeName, UiR.drawable.ms_palette) { onOpen(Destination.THEMES) }
            SettingsItem(
                "Size & layout",
                listOfNotNull(
                    "Height ${settings.heightPortrait}%",
                    "number row".takeIf { settings.numberRow },
                    "one-handed".takeIf { settings.oneHanded != OneHanded.OFF },
                    "floating".takeIf { settings.floating },
                ).joinToString(" · "),
                UiR.drawable.ms_height,
            ) { onOpen(Destination.SIZE) }
            SettingsItem(
                "Sound & vibration",
                (if (settings.haptics == HapticLevel.OFF) "No vibration" else "Vibration: ${settings.haptics.label.lowercase()}") +
                    " · sound ${if (settings.keySound) "on" else "off"}",
                UiR.drawable.ms_vibration,
            ) { onOpen(Destination.SOUND) }
        }

        SettingsGroup("Tools") {
            SettingsItem(
                "Clipboard & snippets",
                if (settings.clipboardHistory) "History on · ${settings.clipboardRetention.label.lowercase()}" else "History off",
                UiR.drawable.ms_content_paste,
            ) { onOpen(Destination.CLIPBOARD_SETTINGS) }
            SettingsItem(
                "Toolbar",
                settings.toolbar.joinToString(" · ") { toolbarName(it) }.ifEmpty { "Empty" },
                UiR.drawable.ms_toolbar,
            ) { onOpen(Destination.TOOLBAR) }
            SettingsItem("Voice typing", settings.voiceLanguage.label, UiR.drawable.ms_mic) { onOpen(Destination.VOICE) }
            SettingsItem(
                "Emoji, GIFs & stickers",
                if (settings.giphyApiKey.isBlank()) "GIF search off" else "GIF search on",
                UiR.drawable.ms_add_reaction,
            ) { onOpen(Destination.MEDIA) }
        }

        SettingsGroup("Privacy & data", tone = GroupTone.PRIMARY) {
            SettingsItem(
                "Privacy",
                if (settings.personalizedLearning) "Learning on · $learnedWordCount learned words" else "Learning off",
                UiR.drawable.ms_shield,
            ) { onOpen(Destination.PRIVACY) }
            SettingsItem("Backup & restore", "Save settings and your words to a file", UiR.drawable.ms_backup) { onOpen(Destination.BACKUP) }
            SettingsItem("About & licences", "Version, open-source credits", UiR.drawable.ms_info) { onOpen(Destination.ABOUT) }
        }
    }
}

/** Setup steps while Jolpai isn't the keyboard yet; afterwards a small "ready" card. */
@Composable
private fun SetupCard(setup: SetupStatus, onOpenKeyboardSettings: () -> Unit, onPickKeyboard: () -> Unit) {
    val ready = setup.enabled && setup.selected
    // A soft gradient from the theme's primary to tertiary container colours.
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer),
    )
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        // A transparent surface would otherwise keep the page's text colour (white in dark mode),
        // which vanished on the light card.
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.background(gradient).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    if (ready) UiR.drawable.ms_check_circle_fill else UiR.drawable.ms_keyboard,
                    container = MaterialTheme.colorScheme.primary,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    size = 48,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (ready) "Jolpai is your keyboard" else "Finish setting up",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        if (ready) "English, অভ্র and জাতীয় — private, on-device typing" else "Two quick steps and you can type",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            if (ready) {
                FilledTonalButton(onClick = onPickKeyboard) { Text("Switch keyboard") }
            } else {
                SetupStep(1, "Turn on Jolpai Keyboard", "Switch on “Jolpai Keyboard” in the list. Android warns that keyboards can see what you type — Jolpai keeps everything on your phone.", setup.enabled, "Open keyboard list", onOpenKeyboardSettings, enabled = true)
                SetupStep(2, "Choose Jolpai", "Pick “Jolpai Keyboard” in the keyboard chooser.", setup.selected, "Choose keyboard", onPickKeyboard, enabled = setup.enabled)
            }
        }
    }
}

@Composable
private fun SetupStep(number: Int, title: String, detail: String, done: Boolean, action: String, onAction: () -> Unit, enabled: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = RoundedCornerShape(50),
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(28.dp),
        ) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (done) {
                    MsIcon(UiR.drawable.ms_check, "Done", Modifier.size(18.dp), MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("$number", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!done) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onAction, enabled = enabled) { Text(action) }
            }
        }
    }
}

/** One field to try the keyboard straight away; the full set of field types is a tap away. */
@Composable
private fun TryField(onMore: () -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            placeholder = { Text("Type here to try Jolpai") },
            leadingIcon = { MsIcon(UiR.drawable.ms_edit_note, null) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onMore, modifier = Modifier.align(Alignment.End)) { Text("Try other field types") }
    }
}

/** A row of theme sketches: tap to use one straight away. */
@Composable
private fun QuickThemes(themes: List<KeyboardTheme>, activeId: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Quick themes",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            items(themes, key = { it.id }) { t ->
                val selected = t.id == activeId
                Column(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelect(t.id) }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ThemeSketch(t, selected)
                    Text(
                        t.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

fun layoutBadge(id: LayoutId): String = when (id) {
    LayoutId.ENGLISH -> "En"
    LayoutId.AVRO -> "অ"
    LayoutId.NATIONAL -> "জা"
}
