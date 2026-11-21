package com.yeamin.jolpai

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import com.yeamin.jolpai.theme.KeyboardTheme
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.core.settings.KeyboardStyle
import com.yeamin.jolpai.core.settings.PreviousKeyboard
import com.yeamin.jolpai.core.settings.WelcomeChoices
import com.yeamin.jolpai.ui.R as UiR

/** The welcome flow's pages, in order. */
private enum class WelcomeStep { HELLO, PREVIOUS, STYLE, BANGLA, COMFORT, TURN_ON, TRY }

/**
 * First-run welcome: a few friendly questions (which keyboard you used before, Classic or
 * Modern style, how you type Bangla, a few comfort choices), then turning Jolpai on and a
 * first try with tips.
 *
 * Someone coming from Ridmik who only taps Next gets the Classic style, which behaves like
 * Ridmik: English ⇄ one Bangla layout, no autocorrect, a short toolbar, emoji in place of the
 * keys. Modern is Jolpai as its defaults set it up. Headings are in Bangla with English underneath, since the rest of the app is English.
 */
@Composable
fun WelcomeFlow(
    settings: KeyboardSettings,
    theme: KeyboardTheme,
    setup: SetupStatus,
    onApply: (WelcomeChoices) -> Unit,
    onFinish: () -> Unit,
    onOpenKeyboardSettings: () -> Unit,
    onPickKeyboard: () -> Unit,
) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var forward by rememberSaveable { mutableStateOf(true) }
    var previous by rememberSaveable { mutableStateOf<PreviousKeyboard?>(null) }
    var choices by rememberSaveable(stateSaver = ChoicesSaver) {
        mutableStateOf(WelcomeChoices.suggestedFor(PreviousKeyboard.NONE, settings))
    }
    val step = WelcomeStep.entries[stepIndex]
    val goTo: (Int) -> Unit = { forward = it > stepIndex; stepIndex = it }
    val next: () -> Unit = {
        if (stepIndex == WelcomeStep.entries.lastIndex) onFinish() else goTo(stepIndex + 1)
    }
    // Every answer applies at once, so the keyboard (and the preview) already works that way,
    // whichever page the user stops or skips on.
    val update: (WelcomeChoices) -> Unit = { choices = it; onApply(it) }
    // Back goes to the previous page; on the first page it leaves the welcome (never the app).
    BackHandler { if (stepIndex > 0) goTo(stepIndex - 1) else onFinish() }

    // A Surface, not a background: it also sets the text colour (light on the dark page).
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding(),
    ) {
        // Progress and Skip. Skip keeps Jolpai's defaults; everything stays changeable in settings.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step != WelcomeStep.HELLO) ProgressDots(stepIndex - 1, WelcomeStep.entries.size - 1)
            Spacer(Modifier.weight(1f))
            if (step != WelcomeStep.TRY) {
                TextButton(onClick = onFinish) { Text("Skip") }
            }
        }

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            // The same page push as the rest of the app.
            transitionSpec = {
                val slide = tween<IntOffset>(PAGE_MS, PAGE_DELAY_MS, PageEasing)
                if (forward) {
                    (slideInHorizontally(slide) { w -> w } togetherWith
                        (slideOutHorizontally(slide) { w -> -w / 4 } +
                            fadeOut(tween(PAGE_MS, PAGE_DELAY_MS, LinearOutSlowInEasing), targetAlpha = 0.35f)))
                        .apply { targetContentZIndex = 1f }
                } else {
                    ((slideInHorizontally(slide) { w -> -w / 4 } +
                        fadeIn(tween(PAGE_MS, PAGE_DELAY_MS, FastOutLinearInEasing), initialAlpha = 0.35f)) togetherWith
                        slideOutHorizontally(slide) { w -> w })
                        .apply { targetContentZIndex = -1f }
                }
            },
            label = "welcome",
        ) { s ->
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (s) {
                    WelcomeStep.HELLO -> HelloPage()
                    WelcomeStep.PREVIOUS -> PreviousPage(previous) {
                        previous = it
                        update(WelcomeChoices.suggestedFor(it, settings))
                    }
                    WelcomeStep.STYLE -> StylePage(choices.style, theme, settings) { update(choices.withStyle(it)) }
                    WelcomeStep.BANGLA -> BanglaPage(choices) { update(it) }
                    WelcomeStep.COMFORT -> ComfortPage(choices, theme, settings) { update(it) }
                    WelcomeStep.TURN_ON -> TurnOnPage(setup, onOpenKeyboardSettings, onPickKeyboard)
                    WelcomeStep.TRY -> TryPage(choices, previous)
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // The main button, always in the same place under the thumb.
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            val ready = setup.enabled && setup.selected
            Button(
                onClick = next,
                enabled = step != WelcomeStep.PREVIOUS || previous != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    when (step) {
                        WelcomeStep.HELLO -> "শুরু করি · Let's start"
                        WelcomeStep.TURN_ON -> if (ready) "Next" else "I'll do this later"
                        WelcomeStep.TRY -> "শেষ · Done"
                        else -> "Next"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
    }
}

// ---------------------------------------------------------------------------------- pages

@Composable
private fun ColumnScope.HelloPage() {
    Spacer(Modifier.height(24.dp))
    // The app icon: the animated mark on its olive tile, as on the About page.
    Box(
        Modifier.size(120.dp).clip(RoundedCornerShape(34.dp)).background(LogoTile).align(Alignment.CenterHorizontally),
        contentAlignment = Alignment.Center,
    ) {
        JolpaiLogo(Modifier.size(84.dp))
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "জলপাই কীবোর্ডে স্বাগতম",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "Welcome to Jolpai Keyboard. A calm, private Bangla keyboard. A few taps and it's set up the way you like.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Promise(UiR.drawable.ms_lock, "আপনার লেখা আপনার ফোনেই থাকে", "What you type stays on your phone")
    Promise(UiR.drawable.ms_block, "বিজ্ঞাপন নেই, অ্যাকাউন্ট লাগে না", "No ads and no account")
    Promise(UiR.drawable.ms_keyboard, "ইন্টারনেট ছাড়াই লেখা যায়", "Typing and suggestions work offline")
}

@Composable
private fun PreviousPage(selected: PreviousKeyboard?, onSelect: (PreviousKeyboard) -> Unit) {
    Heading("আগে কোন কীবোর্ড ব্যবহার করতেন?", "Which keyboard did you use before? Jolpai will start out feeling familiar.")
    Option("Ridmik Keyboard", "Same জাতীয় layout and bottom row; starts on the Classic style", UiR.drawable.ms_keyboard_keys, selected == PreviousKeyboard.RIDMIK) { onSelect(PreviousKeyboard.RIDMIK) }
    Option("Gboard", "Autocorrect on; starts on the Simple style, laid out like Gboard", UiR.drawable.ms_keyboard, selected == PreviousKeyboard.GBOARD) { onSelect(PreviousKeyboard.GBOARD) }
    Option("This is my first Bangla keyboard", "We'll keep it simple and show you around", UiR.drawable.ms_auto_awesome, selected == PreviousKeyboard.NONE) { onSelect(PreviousKeyboard.NONE) }
}

@Composable
private fun BanglaPage(choices: WelcomeChoices, onChange: (WelcomeChoices) -> Unit) {
    Heading("বাংলা কীভাবে লেখেন?", "How do you type Bangla? Pick one or more. The language key switches between them and English.")
    fun toggle(layout: LayoutId) = onChange(
        choices.copy(bangla = if (layout in choices.bangla) choices.bangla - layout else choices.bangla + layout),
    )
    Check("অভ্র · Phonetic", "Type with English letters: ami → আমি, bhalo → ভালো", LayoutId.AVRO in choices.bangla) { toggle(LayoutId.AVRO) }
    Check("জাতীয় · National", "Bangla letters on the keys, the same layout as Ridmik's National", LayoutId.NATIONAL in choices.bangla) { toggle(LayoutId.NATIONAL) }
    Check("প্রভাত · Probhat", "The fixed Bangla layout used on Linux computers", LayoutId.PROBHAT in choices.bangla) { toggle(LayoutId.PROBHAT) }
    SettingsNote(
        if (choices.bangla.isEmpty()) "Nothing picked: Jolpai will type English only. You can add Bangla any time in Languages & layouts."
        else "You can change this any time in Languages & layouts.",
    )
}

@Composable
private fun StylePage(selected: KeyboardStyle, theme: KeyboardTheme, settings: KeyboardSettings, onSelect: (KeyboardStyle) -> Unit) {
    Heading("কোন ধরনটা পছন্দ?", "Which style do you like? You can switch any time in Quick setup.")
    // The real keyboard, already in the picked style: try its buttons.
    KeyboardPreview(theme, settings)
    for (style in KeyboardStyle.entries) StyleCard(style, selected == style, style.summary, onSelect)
}

@Composable
private fun ComfortPage(choices: WelcomeChoices, theme: KeyboardTheme, settings: KeyboardSettings, onChange: (WelcomeChoices) -> Unit) {
    Heading("নিজের মতো করে নিন", "Make it feel like yours. We picked these from your last keyboard; change anything you like.")
    KeyboardPreview(theme, settings)
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(vertical = 6.dp)) {
            SettingsSwitch("Autocorrect English words", "Fixes typos when you press space. Bangla is never changed by itself.", choices.autoCorrect, { onChange(choices.copy(autoCorrect = it)) }, UiR.drawable.ms_spellcheck)
            SettingsSwitch("Number row", "A row of digits above the letters", choices.numberRow, { onChange(choices.copy(numberRow = it)) }, UiR.drawable.ms_123)
            SettingsSwitch("Key sound", null, choices.keySound, { onChange(choices.copy(keySound = it)) }, UiR.drawable.ms_volume_up)
            SettingsSwitch("Vibrate on key press", null, choices.vibrate, { onChange(choices.copy(vibrate = it)) }, UiR.drawable.ms_vibration)
        }
    }
}

@Composable
private fun TurnOnPage(setup: SetupStatus, onOpenKeyboardSettings: () -> Unit, onPickKeyboard: () -> Unit) {
    val ready = setup.enabled && setup.selected
    Heading(
        if (ready) "জলপাই চালু হয়েছে" else "জলপাই চালু করুন",
        if (ready) "Jolpai is your keyboard. One more page: a quick try." else "Turn Jolpai on. Android asks for this once, for every keyboard.",
    )
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SetupStep(
                1, "Turn on Jolpai Keyboard",
                "Switch on “Jolpai Keyboard” in the list. Android warns that keyboards can see what you type. Jolpai keeps everything on your phone and sends nothing.",
                setup.enabled, "Open keyboard list", onOpenKeyboardSettings, enabled = true,
            )
            SetupStep(
                2, "Choose Jolpai", "Pick “Jolpai Keyboard” in the keyboard chooser.",
                setup.selected, "Choose keyboard", onPickKeyboard, enabled = setup.enabled,
                help = "List empty? Set it in keyboard settings" to onOpenKeyboardSettings,
            )
        }
    }
    SettingsNote("Want to go back to your old keyboard later? Use “Switch keyboard” on the home screen. Nothing is lost.")
}

@Composable
private fun TryPage(choices: WelcomeChoices, previous: PreviousKeyboard?) {
    Heading("একবার লিখে দেখুন", "Give it a try. A few things worth knowing:")
    var text by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(if (LayoutId.AVRO in choices.bangla) "Try: ami bhalo achi" else "Type something here") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        shape = RoundedCornerShape(20.dp),
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    if (choices.bangla.isNotEmpty()) {
        Tip(UiR.drawable.ms_language, "Tap the language key next to space to switch between English and ${choices.bangla.joinToString(" / ") { it.shortLabel }}.")
    }
    if (LayoutId.AVRO in choices.bangla) {
        Tip(UiR.drawable.ms_quickreply, "অভ্র: write in English letters (ami → আমি). The bar above the keys shows other spellings; tap one to use it.")
    }
    if (LayoutId.NATIONAL in choices.bangla) {
        Tip(
            UiR.drawable.ms_touch_long,
            if (previous == PreviousKeyboard.RIDMIK) "জাতীয়: the keys are where Ridmik has them. Hold a key for the letter in its corner."
            else "জাতীয়: hold a key for the letter in its corner. ্ joins two letters (ক ্ ষ → ক্ষ).",
        )
    }
    Tip(UiR.drawable.ms_swipe, "Slide your thumb along the space bar to move the cursor. Slide left from ⌫ to delete whole words.")
    Tip(UiR.drawable.ms_settings, "Everything here can be changed later on the home screen: themes, size, sounds, layouts.")
}

// ---------------------------------------------------------------------------------- parts

/** A Bangla heading with its English line under it. */
@Composable
private fun Heading(bangla: String, english: String) {
    Spacer(Modifier.height(8.dp))
    Text(bangla, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(english, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Promise(@DrawableRes icon: Int, bangla: String, english: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        IconBadge(icon, container = MaterialTheme.colorScheme.primaryContainer, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(bangla, style = MaterialTheme.typography.titleMedium)
            Text(english, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Tip(@DrawableRes icon: Int, text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        IconBadge(icon, container = MaterialTheme.colorScheme.tertiaryContainer, tint = MaterialTheme.colorScheme.onTertiaryContainer, size = 36)
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
}

/** A selectable card: one choice of several. */
@Composable
private fun Option(title: String, summary: String, @DrawableRes icon: Int, selected: Boolean, onClick: () -> Unit) {
    ChoiceCard(selected, Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)) {
        IconBadge(icon, container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(16.dp))
        CardText(title, summary)
        RadioButton(selected = selected, onClick = null)
    }
}

/** A card that turns on or off: one of several that can all be on. */
@Composable
private fun Check(title: String, summary: String, checked: Boolean, onClick: () -> Unit) {
    ChoiceCard(checked, Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() })) {
        CardText(title, summary)
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ChoiceCard(selected: Boolean, interaction: Modifier, content: @Composable RowScope.() -> Unit) {
    val border by animateDpAsState(if (selected) 2.dp else 1.dp, label = "border")
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        // Set explicitly: a colour outside the scheme would otherwise get black text.
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(border, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).then(interaction),
    ) {
        Row(Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            content()
        }
    }
}

@Composable
private fun RowScope.CardText(title: String, summary: String) {
    Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.8f))
    }
}

/** A style to pick, with a small sketch of its toolbar and keys. */
@Composable
private fun StyleCard(style: KeyboardStyle, selected: Boolean, summary: String, onSelect: (KeyboardStyle) -> Unit) {
    ChoiceCard(selected, Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(style) })) {
        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${style.banglaLabel} · ${style.label}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                if (style.like != null) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text("like ${style.like}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.8f))
        }
        RadioButton(selected = selected, onClick = null)
    }
}

/** Where you are in the questions: the current dot is a longer pill. */
@Composable
private fun ProgressDots(current: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 22.dp else 8.dp, label = "dot")
            Box(
                Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

/** Keeps the answers across rotation. */
private val ChoicesSaver = Saver<WelcomeChoices, List<Any>>(
    save = { c ->
        listOf(c.style.storageKey, c.bangla.joinToString(",") { it.storageKey }, c.autoCorrect, c.numberRow, c.keySound, c.vibrate)
    },
    restore = { v ->
        WelcomeChoices(
            style = KeyboardStyle.entries.firstOrNull { it.storageKey == v[0] } ?: KeyboardStyle.MODERN,
            bangla = (v[1] as String).split(',').mapNotNull { LayoutId.fromStorageKey(it) },
            autoCorrect = v[2] as Boolean,
            numberRow = v[3] as Boolean,
            keySound = v[4] as Boolean,
            vibrate = v[5] as Boolean,
        )
    },
)
