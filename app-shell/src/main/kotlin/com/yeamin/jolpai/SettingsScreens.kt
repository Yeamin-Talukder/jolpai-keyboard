package com.yeamin.jolpai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import com.yeamin.jolpai.core.settings.DEFAULT_LONG_PRESS_MS
import com.yeamin.jolpai.core.settings.LONG_PRESS_RANGE
import kotlin.math.roundToInt
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.model.ToolbarItem
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.data.ClipRetention
import com.yeamin.jolpai.ui.R as UiR

/** A round badge with a short label, for layouts ("En", "অ", "জা"). */
@Composable
fun TextBadge(text: String) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Medium)
    }
}

private fun layoutHint(id: LayoutId): String = when (id) {
    LayoutId.ENGLISH -> "QWERTY, accents on long-press"
    LayoutId.AVRO -> "Type how it sounds: ami → আমি · reph: rr"
    LayoutId.NATIONAL -> "Ridmik-style জাতীয় · hold a key for its corner letter"
    LayoutId.PROBHAT -> "Probhat (প্রভাত) as on a computer · corners show the shifted letter"
}

/** Which layouts are on, which one to start with, and how to switch. */
@Composable
fun LanguagesScreen(vm: MainViewModel, settings: KeyboardSettings, onBack: () -> Unit) {
    SettingsScaffold("Languages & layouts", onBack) {
        SettingsGroup("Layouts") {
            LayoutId.entries.forEach { id ->
                val on = id in settings.enabledLayouts
                val isLast = on && settings.enabledLayouts.size == 1
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .toggleable(value = on, enabled = !isLast, role = Role.Switch) { vm.setLayoutEnabled(id, it) }
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextBadge(layoutBadge(id))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(id.displayName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal))
                        Text(layoutHint(id), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(checked = on, onCheckedChange = null, enabled = !isLast)
                }
            }
        }
        SettingsNote("At least one layout stays on.")

        SettingsNote("Tap 🌐 to switch layouts; hold it for the list and Android's keyboard chooser.")
        SettingsGroup {
            SettingsSwitch(
                "Remember last layout",
                "Start with the layout you used last",
                settings.rememberLastLayout,
                vm::setRememberLastLayout,
                UiR.drawable.ms_history,
            )
        }

        SettingsGroup(if (settings.rememberLastLayout) "Default layout" else "Start every time with") {
            settings.enabledLayouts.forEach { id ->
                SettingsRadio(id.displayName, selected = settings.defaultLayout == id) { vm.setDefaultLayout(id) }
            }
        }

        SettingsGroup("Offline translation", tone = GroupTone.TERTIARY) {
            OfflineTranslationSetting()
        }
        SettingsNote("Tap 🌐 Translate in the keyboard's top bar: a box opens above the keys, you type in English (or Bangla) and insert the translation. Translation runs on your phone; only the language pack is downloaded, from Google, when you ask.")
    }
}

/** The offline Bangla pack: whether it is on the phone, with download / delete. */
@Composable
private fun OfflineTranslationSetting() {
    val translator = remember { com.yeamin.jolpai.ui.OfflineTranslator() }
    DisposableEffect(translator) {
        translator.refresh()
        onDispose { translator.close() }
    }
    val status by translator.status.collectAsState()
    val size = com.yeamin.jolpai.ui.OfflineTranslator.PACK_SIZE_MB
    val summary = when (status) {
        com.yeamin.jolpai.ui.TranslateStatus.CHECKING -> "Checking…"
        com.yeamin.jolpai.ui.TranslateStatus.NOT_DOWNLOADED -> "English ⇄ বাংলা · download once (about $size MB), then it works offline"
        com.yeamin.jolpai.ui.TranslateStatus.DOWNLOADING -> "Downloading the Bangla pack…"
        com.yeamin.jolpai.ui.TranslateStatus.READY -> "Ready offline · English ⇄ বাংলা"
        com.yeamin.jolpai.ui.TranslateStatus.FAILED -> "Download failed. Check the internet and try again."
    }
    SettingsItem(
        "English ⇄ Bangla pack",
        summary,
        UiR.drawable.ms_translate,
        showChevron = false,
        enabled = status != com.yeamin.jolpai.ui.TranslateStatus.CHECKING && status != com.yeamin.jolpai.ui.TranslateStatus.DOWNLOADING,
        trailing = {
            when (status) {
                com.yeamin.jolpai.ui.TranslateStatus.READY -> TextButton(onClick = { translator.delete() }) { Text("Delete") }
                com.yeamin.jolpai.ui.TranslateStatus.DOWNLOADING -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                com.yeamin.jolpai.ui.TranslateStatus.CHECKING -> Unit
                else -> androidx.compose.material3.FilledTonalButton(onClick = { translator.download() }) { Text("Download") }
            }
        },
        onClick = {
            if (status == com.yeamin.jolpai.ui.TranslateStatus.NOT_DOWNLOADED || status == com.yeamin.jolpai.ui.TranslateStatus.FAILED) translator.download()
        },
    )
}

/** Capitals, full stops and gestures. */
@Composable
fun TypingScreen(vm: MainViewModel, settings: KeyboardSettings, onBack: () -> Unit) {
    SettingsScaffold("Typing & gestures", onBack) {
        SettingsGroup("Typing") {
            SettingsSwitch("Auto-capitalisation", "Capital letter at the start of each English sentence", settings.autoCapitalize, vm::setAutoCapitalize, UiR.drawable.ms_text_fields)
            SettingsSwitch("Double-space full stop", "Two spaces type “. ” in English and “। ” in Bangla", settings.doubleSpacePeriod, vm::setDoubleSpacePeriod, UiR.drawable.ms_space_bar)
        }
        SettingsGroup("Hold for more characters") {
            LongPressSetting(settings.longPressMs, vm::setLongPressMs)
        }
        SettingsGroup("Gestures") {
            SettingsSwitch(
                "Swipe gestures",
                "Slide along the space bar to move the cursor. Slide left from backspace to select words, back right to select fewer; let go to delete them.",
                settings.cursorSwipe,
                vm::setCursorSwipe,
                UiR.drawable.ms_swipe,
            )
            SettingsSwitch(
                "Trackpad: move between lines",
                "Hold the space bar and the keyboard becomes a trackpad for the cursor. With this on, moving up and down changes line too.",
                settings.trackpadVertical,
                vm::setTrackpadVertical,
                UiR.drawable.ms_drag_pan,
            )
            SettingsSwitch(
                "Hold Enter for emoji",
                if (settings.enterHoldSelect) {
                    "Hold Enter and let go without sliding to open the emoji panel (sliding selects text). A tap still sends or starts a new line."
                } else {
                    "Hold Enter to open the emoji panel. A tap still sends, searches or starts a new line."
                },
                settings.enterHoldEmoji,
                vm::setEnterHoldEmoji,
                UiR.drawable.ms_mood,
            )
            SettingsSwitch(
                "Hold Enter and slide to select",
                "Hold Enter, then slide left or right to select text from the cursor a letter at a time, up or down for whole lines. Let go and the text stays selected, ready to copy or cut. Not in password fields.",
                settings.enterHoldSelect,
                vm::setEnterHoldSelect,
                UiR.drawable.ms_keyboard_return,
            )
        }
        SettingsGroup("Typing accuracy") {
            SettingsSwitch(
                "Predictive touch",
                "Jolpai guesses the next letter (lik → e) and quietly gives likely keys a little more room near their edges. The keys look the same; a tap in the middle of a key always types it.",
                settings.predictiveTouch,
                vm::setPredictiveTouch,
                UiR.drawable.ms_auto_awesome,
            )
            SettingsSwitch(
                "Learn my touch",
                "Learns where you tap each key, faster from corrections (a letter, backspace, then its neighbour). Never in password or private fields.",
                settings.touchCalibration,
                vm::setTouchCalibration,
                UiR.drawable.ms_tune,
                enabled = settings.predictiveTouch,
            )
            SettingsItem(
                "Reset learned touch",
                "Forget the learned tap positions",
                UiR.drawable.ms_restart_alt,
                enabled = settings.predictiveTouch,
                showChevron = false,
                onClick = vm::resetTouchCalibration,
            )
        }
    }
}

/**
 * How long to hold a key before its hidden characters appear (the corner hint, or the
 * long-press menu). A short time helps জাতীয়, where many letters live behind a hold.
 * The slider saves when the finger lifts, so dragging doesn't write the setting on every step.
 */
@Composable
private fun LongPressSetting(current: Int, onChange: (Int) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging?.let { snap(it) } ?: current
    // Slider, labels and presets line up with the title, past the icon badge (40 dp + 16 dp gap).
    val indent = Modifier.padding(start = 56.dp)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(UiR.drawable.ms_touch_long)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Hold time", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal))
                Text(
                    "Hold a key this long for its corner letter (like ঋ or ৎ) or more characters",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    "$shown ms",
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // A smooth slider (no tick marks); the value snaps to 25 ms steps and saves on release.
        Slider(
            value = dragging ?: current.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { onChange(snap(it)) }; dragging = null },
            valueRange = LONG_PRESS_RANGE.first.toFloat()..LONG_PRESS_RANGE.last.toFloat(),
            modifier = indent,
        )
        Row(indent.fillMaxWidth()) {
            Text("Faster", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("Slower", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        val presets = listOf("Quick" to DEFAULT_LONG_PRESS_MS, "Normal" to 350, "Relaxed" to 500)
        SingleChoiceSegmentedButtonRow(indent.fillMaxWidth()) {
            presets.forEachIndexed { i, (label, ms) ->
                SegmentedButton(
                    selected = shown == ms,
                    onClick = { onChange(ms) },
                    shape = SegmentedButtonDefaults.itemShape(i, presets.size),
                    icon = {},
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Text("$ms ms", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun snap(v: Float): Int = ((v / 25f).roundToInt() * 25).coerceIn(LONG_PRESS_RANGE)

/** Word suggestions, autocorrect and the personal dictionary. */
@Composable
fun SuggestionsScreen(vm: MainViewModel, settings: KeyboardSettings, personalWordCount: Int, onOpenDictionary: () -> Unit, onBack: () -> Unit) {
    SettingsScaffold("Suggestions", onBack) {
        SettingsGroup {
            SettingsSwitch("Show suggestions", "Words above the keys, and Avro dictionary candidates", settings.showSuggestions, vm::setShowSuggestions, UiR.drawable.ms_lightbulb)
            SettingsSwitch("Autocorrect", "Fix obvious English typos when you press space. Backspace undoes it.", settings.autoCorrect, vm::setAutoCorrect, UiR.drawable.ms_spellcheck, enabled = settings.showSuggestions)
            SettingsSwitch("Next-word suggestions", "Suggest the word you usually type next, and with on-device AI the word that fits the sentence", settings.nextWordPrediction, vm::setNextWordPrediction, UiR.drawable.ms_auto_awesome, enabled = settings.showSuggestions)
            SettingsSwitch("Emoji suggestions", "pizza → 🍕, হাসি → 😄", settings.emojiSuggestions, vm::setEmojiSuggestions, UiR.drawable.ms_mood, enabled = settings.showSuggestions)
            SettingsSwitch(
                "On-device AI predictions",
                "English: a small neural model reads your sentence to rank words, predict the next one, pick emoji by meaning (so tired → 😴) and catch their/there in Check. Runs only on this phone.",
                settings.smartPredictions, vm::setSmartPredictions, UiR.drawable.ms_auto_awesome, enabled = settings.showSuggestions,
            )
            SettingsSwitch("Block offensive words", "Never suggested (you can still type them)", settings.blockOffensiveWords, vm::setBlockOffensiveWords, UiR.drawable.ms_block, enabled = settings.showSuggestions)
        }
        SettingsGroup {
            SettingsItem(
                "Personal dictionary",
                "$personalWordCount word${if (personalWordCount == 1) "" else "s"} — always suggested, never autocorrected",
                UiR.drawable.ms_menu_book,
                onClick = onOpenDictionary,
            )
        }
    }
}

/** Clipboard history settings, with the history and snippets one tap away. */
@Composable
fun ClipboardSettingsScreen(vm: MainViewModel, settings: KeyboardSettings, onOpenClipboard: () -> Unit, onOpenSnippets: () -> Unit, onBack: () -> Unit) {
    SettingsScaffold("Clipboard & snippets", onBack) {
        SettingsGroup {
            SettingsItem("Clipboard history", "Search, pin and organise what you copied", UiR.drawable.ms_content_paste, onClick = onOpenClipboard)
            SettingsItem("Snippets", "Saved text you can insert with a shortcut", UiR.drawable.ms_short_text, onClick = onOpenSnippets)
        }
        SettingsGroup("History") {
            SettingsSwitch("Save clipboard history", "Keep copied text on this phone", settings.clipboardHistory, vm::setClipboardHistory, UiR.drawable.ms_history)
            SettingsSwitch("Save copied images", "A private copy of copied pictures (up to 10 MB each)", settings.clipboardImages, vm::setClipboardImages, UiR.drawable.ms_image, enabled = settings.clipboardHistory)
        }
        SettingsGroup("Keep items that aren't pinned for") {
            ClipRetention.entries.forEach { r ->
                SettingsRadio(r.label, selected = settings.clipboardRetention == r, enabled = settings.clipboardHistory) { vm.setClipboardRetention(r) }
            }
        }
        SettingsNote(
            "Android only lets the active keyboard see copies, so things copied while another keyboard " +
                "was in use aren't saved. Copies from password fields and from apps that mark them " +
                "sensitive (password managers, banking apps) are never saved.",
        )
    }
}

fun toolbarName(item: ToolbarItem): String = when (item) {
    ToolbarItem.EMOJI -> "Emoji"
    ToolbarItem.CLIPBOARD -> "Clipboard"
    ToolbarItem.VOICE -> "Voice"
    ToolbarItem.EDIT -> "Editing"
    ToolbarItem.TRANSLATE -> "Translate"
    ToolbarItem.CHECK -> "Check"
}

private fun toolbarIcon(item: ToolbarItem): Int = when (item) {
    ToolbarItem.EMOJI -> UiR.drawable.ms_mood
    ToolbarItem.CLIPBOARD -> UiR.drawable.ms_content_paste
    ToolbarItem.VOICE -> UiR.drawable.ms_mic
    ToolbarItem.EDIT -> UiR.drawable.ms_edit_note
    ToolbarItem.TRANSLATE -> UiR.drawable.ms_translate
    ToolbarItem.CHECK -> UiR.drawable.ms_spellcheck
}

/** Which tools appear above the keys, and in what order. */
@Composable
fun ToolbarScreen(
    items: List<ToolbarItem>,
    onChange: (List<ToolbarItem>) -> Unit,
    onBack: () -> Unit,
    placement: com.yeamin.jolpai.core.settings.PanelPlacement,
    onPlacement: (com.yeamin.jolpai.core.settings.PanelPlacement) -> Unit,
) {
    SettingsScaffold("Toolbar & panels", onBack) {
        SettingsGroup("Emoji and clipboard open") {
            com.yeamin.jolpai.core.settings.PanelPlacement.entries.forEach { p ->
                SettingsRadio(p.label, p.summary, selected = placement == p) { onPlacement(p) }
            }
        }
        SettingsNote("Tools shown above the keys when you're not typing a word. While suggestions show, the grid button brings them back.")
        SettingsGroup {
            val ordered = items + ToolbarItem.entries.filter { it !in items }
            ordered.forEach { item ->
                val on = item in items
                val index = items.indexOf(item)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(toolbarIcon(item))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f).alpha(if (on) 1f else 0.6f)) {
                        Text(toolbarName(item), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal))
                        Text(item.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onChange(items.toMutableList().apply { add(index - 1, removeAt(index)) }) }, enabled = on && index > 0) {
                        MsIcon(UiR.drawable.ms_arrow_upward, "Move ${toolbarName(item)} up")
                    }
                    IconButton(onClick = { onChange(items.toMutableList().apply { add(index + 1, removeAt(index)) }) }, enabled = on && index < items.lastIndex) {
                        MsIcon(UiR.drawable.ms_arrow_downward, "Move ${toolbarName(item)} down")
                    }
                    Checkbox(checked = on, onCheckedChange = { checked -> onChange(if (checked) items + item else items - item) })
                }
            }
        }
    }
}

/** What Jolpai learns, and a way to forget it. */
@Composable
fun PrivacyScreen(vm: MainViewModel, settings: KeyboardSettings, learnedWordCount: Int, onBack: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    SettingsScaffold("Privacy", onBack) {
        SettingsNote(
            "Jolpai works offline and never sends what you type anywhere. To improve suggestions it " +
                "remembers words you type, on this phone only. In password and private (incognito) " +
                "fields it learns nothing.",
        )
        SettingsGroup {
            SettingsSwitch("Learn from my typing", "Remember words and word pairs to improve suggestions", settings.personalizedLearning, vm::setPersonalizedLearning, UiR.drawable.ms_auto_awesome)
            SettingsItem(
                "Clear learned data",
                "$learnedWordCount learned word${if (learnedWordCount == 1) "" else "s"}",
                UiR.drawable.ms_delete,
                enabled = learnedWordCount > 0,
                showChevron = false,
            ) { confirm = true }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { MsIcon(UiR.drawable.ms_delete, null) },
            title = { Text("Clear learned data?") },
            text = { Text("Jolpai will forget the words, word pairs and Avro choices it learned from your typing. Your personal dictionary is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearLearnedData()
                    confirm = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/** Real fields of each input type, so every keyboard mode can be tried. */
@Composable
fun TestFieldsScreen(onBack: () -> Unit) {
    SettingsScaffold("Try the keyboard", onBack) {
        SettingsNote("Each field asks for a different keyboard: Enter changes to Send, Search, Next or Done, and number fields get a number pad.")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
            TestField("Message", KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send))
            TestField("Search", KeyboardOptions(imeAction = ImeAction.Search))
            TestField("Email", KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
            TestField("Website", KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go))
            TestField("Password", KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), password = true)
            TestField("Number", KeyboardOptions(keyboardType = KeyboardType.Number))
            TestField("Phone", KeyboardOptions(keyboardType = KeyboardType.Phone))
            TestField("Note (several lines)", KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), singleLine = false)
        }
    }
}

@Composable
private fun TestField(label: String, options: KeyboardOptions, password: Boolean = false, singleLine: Boolean = true) {
    var value by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(label) },
        keyboardOptions = options,
        singleLine = singleLine,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (singleLine) Modifier else Modifier.heightIn(min = 120.dp)),
    )
}
