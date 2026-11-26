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
 * keys. Modern is Jolpai as its defaults set it up. Every line follows the app's language; the
 * first page has the English | বাংলা switch, so someone who reads Bangla can pick it before anything else.
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
                TextButton(onClick = onFinish) { Text(tr("Skip", "বাদ দিন")) }
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
                        WelcomeStep.HELLO -> tr("Let's start", "চলুন শুরু করি")
                        WelcomeStep.TURN_ON -> if (ready) tr("Next", "পরের ধাপ") else tr("I'll do this later", "পরে করব")
                        WelcomeStep.TRY -> tr("Done", "হয়ে গেছে")
                        else -> tr("Next", "পরের ধাপ")
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
    // The app's language comes first, so a Bangla reader can switch before reading anything else.
    LanguageSwitch(Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
    Spacer(Modifier.height(8.dp))
    // The app icon: the animated mark on its olive tile, as on the About page.
    Box(
        Modifier.size(120.dp).clip(RoundedCornerShape(34.dp)).background(LogoTile).align(Alignment.CenterHorizontally),
        contentAlignment = Alignment.Center,
    ) {
        JolpaiLogo(Modifier.size(84.dp))
    }
    Spacer(Modifier.height(8.dp))
    Text(
        tr("Welcome to Jolpai Keyboard", "জলপাই কীবোর্ডে স্বাগতম"),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        tr(
            "A calm, private Bangla keyboard. Set it up your way in a few taps.",
            "ঝামেলাহীন, নিরাপদ একটি বাংলা কীবোর্ড। কয়েকটা ট্যাপেই নিজের মতো সাজিয়ে নিন।",
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Promise(UiR.drawable.ms_lock, tr("What you type stays on your phone", "আপনার লেখা আপনার ফোনেই থাকে"))
    Promise(UiR.drawable.ms_block, tr("No ads, no account", "বিজ্ঞাপন নেই, অ্যাকাউন্ট লাগে না"))
    Promise(UiR.drawable.ms_keyboard, tr("Typing and suggestions work offline", "ইন্টারনেট ছাড়াই লেখা ও সাজেশন চলে"))
}

@Composable
private fun PreviousPage(selected: PreviousKeyboard?, onSelect: (PreviousKeyboard) -> Unit) {
    Heading(
        tr("Which keyboard did you use before?", "আগে কোন কীবোর্ড ব্যবহার করতেন?"),
        tr("Jolpai will feel familiar from the start.", "জলপাই শুরু থেকেই চেনা চেনা লাগবে।"),
    )
    Option(
        tr("Ridmik Keyboard", "রিদমিক কীবোর্ড"),
        tr("Same জাতীয় layout and bottom row, in the Classic style", "একই জাতীয় লেআউট ও নিচের সারি, ক্লাসিক ধরনে"),
        UiR.drawable.ms_keyboard_keys, selected == PreviousKeyboard.RIDMIK,
    ) { onSelect(PreviousKeyboard.RIDMIK) }
    Option(
        tr("Gboard", "জিবোর্ড"),
        tr("Autocorrect on, in the Simple style like Gboard", "অটোকারেক্ট চালু, জিবোর্ডের মতো সহজ ধরনে"),
        UiR.drawable.ms_keyboard, selected == PreviousKeyboard.GBOARD,
    ) { onSelect(PreviousKeyboard.GBOARD) }
    Option(
        tr("This is my first Bangla keyboard", "এটাই আমার প্রথম বাংলা কীবোর্ড"),
        tr("We'll keep it simple and show you around", "সহজ রাখব, সবকিছু দেখিয়ে দেব"),
        UiR.drawable.ms_auto_awesome, selected == PreviousKeyboard.NONE,
    ) { onSelect(PreviousKeyboard.NONE) }
}

@Composable
private fun BanglaPage(choices: WelcomeChoices, onChange: (WelcomeChoices) -> Unit) {
    Heading(
        tr("How do you type Bangla?", "বাংলা কীভাবে লেখেন?"),
        tr("Pick one or more. The language key switches between them and English.", "এক বা একাধিক বেছে নিন। ভাষার কী চেপে এগুলো আর ইংরেজির মধ্যে বদলাবেন।"),
    )
    fun toggle(layout: LayoutId) = onChange(
        choices.copy(bangla = if (layout in choices.bangla) choices.bangla - layout else choices.bangla + layout),
    )
    Check(
        tr("অভ্র · Phonetic", "অভ্র · ফোনেটিক"),
        tr("Type with English letters: ami → আমি, bhalo → ভালো", "ইংরেজি অক্ষরে লিখুন: ami → আমি, bhalo → ভালো"),
        LayoutId.AVRO in choices.bangla,
    ) { toggle(LayoutId.AVRO) }
    Check(
        tr("জাতীয় · National", "জাতীয়"),
        tr("Bangla letters on the keys, same as Ridmik's National", "কী-তে বাংলা অক্ষর, রিদমিকের জাতীয় লেআউটের মতোই"),
        LayoutId.NATIONAL in choices.bangla,
    ) { toggle(LayoutId.NATIONAL) }
    Check(
        tr("প্রভাত · Probhat", "প্রভাত"),
        tr("The Bangla layout used on Linux computers", "Linux কম্পিউটারে যে বাংলা লেআউট চলে"),
        LayoutId.PROBHAT in choices.bangla,
    ) { toggle(LayoutId.PROBHAT) }
    SettingsNote(
        if (choices.bangla.isEmpty()) {
            tr(
                "None picked: Jolpai will type English only. You can add Bangla later in settings.",
                "কিছুই বাছেননি: জলপাই শুধু ইংরেজি লিখবে। পরে সেটিংসে বাংলা যোগ করতে পারবেন।",
            )
        } else {
            tr("You can change this any time in settings.", "পরে যেকোনো সময় সেটিংসে বদলাতে পারবেন।")
        },
    )
}

@Composable
private fun StylePage(selected: KeyboardStyle, theme: KeyboardTheme, settings: KeyboardSettings, onSelect: (KeyboardStyle) -> Unit) {
    Heading(
        tr("Which style do you like?", "কোন ধরনটা পছন্দ?"),
        tr("Try the keyboard below. You can switch any time.", "নিচের কীবোর্ডটি চালিয়ে দেখুন। পরে যেকোনো সময় বদলানো যায়।"),
    )
    // The real keyboard, already in the picked style: try its buttons.
    KeyboardPreview(theme, settings)
    for (style in KeyboardStyle.entries) StyleCard(style, selected == style, style.uiSummary, onSelect)
}

@Composable
private fun ComfortPage(choices: WelcomeChoices, theme: KeyboardTheme, settings: KeyboardSettings, onChange: (WelcomeChoices) -> Unit) {
    Heading(
        tr("Make it yours", "নিজের মতো করে নিন"),
        tr("We picked these from your last keyboard. Change anything you like.", "আগের কীবোর্ড দেখে এগুলো বেছে রেখেছি। যা খুশি বদলে নিন।"),
    )
    KeyboardPreview(theme, settings)
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(vertical = 6.dp)) {
            SettingsSwitch(
                tr("Autocorrect English", "ইংরেজি অটোকারেক্ট"),
                tr("Fixes typos when you press space. Bangla is never changed on its own.", "স্পেস চাপলে ভুল বানান ঠিক করে। বাংলা নিজে থেকে কখনো বদলায় না।"),
                choices.autoCorrect, { onChange(choices.copy(autoCorrect = it)) }, UiR.drawable.ms_spellcheck,
            )
            SettingsSwitch(
                tr("Number row", "সংখ্যার সারি"),
                tr("Digits above the letters", "অক্ষরের ওপরে সংখ্যা"),
                choices.numberRow, { onChange(choices.copy(numberRow = it)) }, UiR.drawable.ms_123,
            )
            SettingsSwitch(tr("Key sound", "কী চাপার শব্দ"), null, choices.keySound, { onChange(choices.copy(keySound = it)) }, UiR.drawable.ms_volume_up)
            SettingsSwitch(tr("Vibrate on key press", "কী চাপলে ভাইব্রেশন"), null, choices.vibrate, { onChange(choices.copy(vibrate = it)) }, UiR.drawable.ms_vibration)
        }
    }
}

@Composable
private fun TurnOnPage(setup: SetupStatus, onOpenKeyboardSettings: () -> Unit, onPickKeyboard: () -> Unit) {
    val ready = setup.enabled && setup.selected
    Heading(
        if (ready) tr("Jolpai is on", "জলপাই চালু হয়েছে") else tr("Turn on Jolpai", "জলপাই চালু করুন"),
        if (ready) {
            tr("Jolpai is your keyboard. One more page: a quick try.", "জলপাই এখন আপনার কীবোর্ড। শেষ পাতায় একবার লিখে দেখুন।")
        } else {
            tr("Android asks for this once, for every keyboard.", "যেকোনো কীবোর্ডের জন্য Android এটা একবারই চায়।")
        },
    )
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SetupStep(
                1, tr("Turn on Jolpai Keyboard", "জলপাই কীবোর্ড চালু করুন"),
                tr(
                    "Switch on “Jolpai Keyboard” in the list. Android warns that keyboards can see what you type; Jolpai keeps it all on your phone and sends nothing.",
                    "তালিকায় “Jolpai Keyboard” চালু করুন। Android সতর্ক করবে যে কীবোর্ড আপনার লেখা দেখতে পায়; জলপাই সব আপনার ফোনেই রাখে, কোথাও কিছু পাঠায় না।",
                ),
                setup.enabled, tr("Open keyboard list", "কীবোর্ডের তালিকা খুলুন"), onOpenKeyboardSettings, enabled = true,
            )
            SetupStep(
                2, tr("Choose Jolpai", "জলপাই বেছে নিন"),
                tr("Pick “Jolpai Keyboard” in the keyboard chooser.", "কীবোর্ড বাছাইয়ের তালিকায় “Jolpai Keyboard” বেছে নিন।"),
                setup.selected, tr("Choose keyboard", "কীবোর্ড বেছে নিন"), onPickKeyboard, enabled = setup.enabled,
                help = tr("List empty? Set it in keyboard settings", "তালিকা খালি? কীবোর্ড সেটিংসে ঠিক করুন") to onOpenKeyboardSettings,
            )
        }
    }
    SettingsNote(
        tr(
            "You can switch back to your old keyboard any time from the app's home page. Nothing is lost.",
            "চাইলে যেকোনো সময় অ্যাপের মূল পাতা থেকে আগের কীবোর্ডে ফিরতে পারবেন। কিছুই হারাবে না।",
        ),
    )
}

@Composable
private fun TryPage(choices: WelcomeChoices, previous: PreviousKeyboard?) {
    Heading(tr("Give it a try", "একবার লিখে দেখুন"), tr("A few things worth knowing:", "কয়েকটা কাজের কথা:"))
    var text by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(if (LayoutId.AVRO in choices.bangla) tr("Try: ami bhalo achi", "লিখে দেখুন: ami bhalo achi") else tr("Type something here", "এখানে কিছু লিখুন")) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        shape = RoundedCornerShape(20.dp),
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    if (choices.bangla.isNotEmpty()) {
        val names = choices.bangla.joinToString(" / ") { it.shortLabel }
        Tip(
            UiR.drawable.ms_language,
            tr("Tap the language key next to space to switch between English and $names.", "স্পেসের পাশের ভাষার কী চেপে ইংরেজি আর $names-এর মধ্যে বদলান।"),
        )
    }
    if (LayoutId.AVRO in choices.bangla) {
        Tip(
            UiR.drawable.ms_quickreply,
            tr(
                "অভ্র: type in English letters (ami → আমি). Other spellings show in the bar above the keys; tap one.",
                "অভ্র: ইংরেজি অক্ষরে লিখুন (ami → আমি)। অন্য বানানগুলো কী-গুলোর ওপরের বারে দেখাবে; পছন্দেরটায় ট্যাপ করুন।",
            ),
        )
    }
    if (LayoutId.NATIONAL in choices.bangla) {
        Tip(
            UiR.drawable.ms_touch_long,
            if (previous == PreviousKeyboard.RIDMIK) {
                tr(
                    "জাতীয়: the keys are where Ridmik has them. Hold a key for the letter in its corner.",
                    "জাতীয়: কী-গুলো রিদমিকের মতো একই জায়গায়। কোণের অক্ষরটির জন্য কী চেপে ধরুন।",
                )
            } else {
                tr(
                    "জাতীয়: hold a key for the letter in its corner. ্ joins two letters (ক ্ ষ → ক্ষ).",
                    "জাতীয়: কোণের অক্ষরটির জন্য কী চেপে ধরুন। ্ দিয়ে দুটি অক্ষর জোড়া লাগে (ক ্ ষ → ক্ষ)।",
                )
            },
        )
    }
    Tip(
        UiR.drawable.ms_swipe,
        tr(
            "Slide your thumb along the space bar to move the cursor. Slide left from ⌫ to delete whole words.",
            "স্পেস বারে আঙুল টেনে কার্সর সরান। ⌫ থেকে বাঁয়ে টানলে পুরো শব্দ মুছে যায়।",
        ),
    )
    Tip(
        UiR.drawable.ms_settings,
        tr(
            "Change anything later from the app's home page: themes, size, sounds, layouts.",
            "থিম, সাইজ, শব্দ, লেআউট: সবকিছু পরে অ্যাপের মূল পাতা থেকে বদলাতে পারবেন।",
        ),
    )
}

// ---------------------------------------------------------------------------------- parts

/** A page heading with a short line under it. */
@Composable
private fun Heading(title: String, body: String) {
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Promise(@DrawableRes icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        IconBadge(icon, container = MaterialTheme.colorScheme.primaryContainer, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
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
                Text(style.uiLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                val like = style.like?.let { name ->
                    tr(
                        "like $name",
                        when (name) {
                            "Ridmik" -> "রিদমিকের মতো"
                            "Gboard" -> "জিবোর্ডের মতো"
                            else -> "$name-এর মতো"
                        },
                    )
                }
                if (like != null) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(like, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
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
