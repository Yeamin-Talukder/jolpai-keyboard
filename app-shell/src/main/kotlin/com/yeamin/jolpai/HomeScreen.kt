package com.yeamin.jolpai

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yeamin.jolpai.core.gesture.BuiltIns
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.settings.HapticLevel
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.core.settings.KeyboardStyle
import com.yeamin.jolpai.theme.KeyboardTheme
import com.yeamin.jolpai.ui.R as UiR

/**
 * The settings home, kept simple: the app language at the top, setup status, four big tiles
 * for what people change most, then three short groups. Trying the keyboard is the floating
 * button (MainActivity), so there's no text field here.
 */
@Composable
fun HomeScreen(
    setup: SetupStatus,
    settings: KeyboardSettings,
    themeName: String,
    learnedWordCount: Int,
    onOpenKeyboardSettings: () -> Unit,
    onPickKeyboard: () -> Unit,
    onOpen: (Destination) -> Unit,
    themes: List<KeyboardTheme> = emptyList(),
    activeThemeId: String = "",
    onSelectTheme: (String) -> Unit = {},
) {
    SettingsScaffold(
        tr("Jolpai Keyboard", "জলপাই কীবোর্ড"),
        onBack = null,
        actions = { LanguageSwitch(Modifier.padding(end = 12.dp)) },
    ) {
        SetupCard(setup, settings.enabledLayouts, onOpenKeyboardSettings, onPickKeyboard)

        // The four things people change most, as big tiles.
        Spacer(Modifier.height(4.dp))
        TileRow {
            HomeTile(
                tr("Languages", "ভাষা"),
                settings.enabledLayouts.joinToString(" · ") { shortName(it) },
                UiR.drawable.ms_language, GroupTone.PRIMARY,
            ) { onOpen(Destination.LANGUAGES) }
            HomeTile(tr("Themes", "থিম"), themeName, UiR.drawable.ms_palette, GroupTone.TERTIARY) { onOpen(Destination.THEMES) }
        }
        TileRow {
            HomeTile(
                tr("Sound & vibration", "শব্দ ও ভাইব্রেশন"),
                listOf(
                    if (settings.keySound) tr("Sound on", "শব্দ চালু") else tr("Sound off", "শব্দ বন্ধ"),
                    if (settings.haptics == HapticLevel.OFF) tr("no vibration", "ভাইব্রেশন বন্ধ") else tr("vibration on", "ভাইব্রেশন চালু"),
                ).joinToString(" · "),
                UiR.drawable.ms_vibration, GroupTone.SECONDARY,
            ) { onOpen(Destination.SOUND) }
            HomeTile(
                tr("Size & layout", "আকার ও লেআউট"),
                listOfNotNull(
                    tr("Height ${settings.heightPortrait}%", "উচ্চতা ${num(settings.heightPortrait)}%"),
                    tr("number row", "সংখ্যার সারি").takeIf { settings.numberRow },
                ).joinToString(" · "),
                UiR.drawable.ms_height, GroupTone.SECONDARY,
            ) { onOpen(Destination.SIZE) }
        }

        if (themes.isNotEmpty()) QuickThemes(themes, activeThemeId, onSelectTheme)

        SettingsGroup(tr("Typing", "টাইপিং"), tone = GroupTone.PRIMARY) {
            SettingsItem(
                tr("Easy setup", "সহজ সেটআপ"),
                KeyboardStyle.of(settings)?.let { tr("${it.uiLabel} style · like Ridmik or Gboard in one tap", "${it.uiLabel} স্টাইল · এক ট্যাপে রিদমিক বা জিবোর্ডের মতো") }
                    ?: tr("Set it up like Ridmik or Gboard in one tap", "এক ট্যাপে রিদমিক বা জিবোর্ডের মতো সাজান"),
                UiR.drawable.ms_tune,
            ) { onOpen(Destination.QUICK_SETUP) }
            SettingsItem(
                tr("Typing & gestures", "টাইপিং ও জেসচার"),
                tr("Capitals, full stop, swipe and hold", "বড় হাতের অক্ষর, দাঁড়ি, সোয়াইপ ও চেপে ধরা"),
                UiR.drawable.ms_keyboard,
            ) { onOpen(Destination.TYPING) }
            SettingsItem(
                tr("Suggestions & spelling", "সাজেশন ও বানান"),
                when {
                    !settings.showSuggestions -> tr("Off", "বন্ধ")
                    settings.autoCorrect -> tr("Suggestions and autocorrect on", "সাজেশন ও অটোকারেক্ট চালু")
                    else -> tr("Suggestions on, autocorrect off", "সাজেশন চালু, অটোকারেক্ট বন্ধ")
                },
                UiR.drawable.ms_spellcheck,
            ) { onOpen(Destination.SUGGESTIONS) }
        }

        SettingsGroup(tr("More features", "আরও সুবিধা"), tone = GroupTone.TERTIARY) {
            SettingsItem(
                tr("Clipboard & snippets", "ক্লিপবোর্ড ও স্নিপেট"),
                if (settings.clipboardHistory) tr("Copy history on · ${settings.clipboardRetention.uiLabel}", "কপির ইতিহাস চালু · ${settings.clipboardRetention.uiLabel}")
                else tr("Copy history off", "কপির ইতিহাস বন্ধ"),
                UiR.drawable.ms_content_paste,
            ) { onOpen(Destination.CLIPBOARD_SETTINGS) }
            SettingsItem(tr("Voice typing", "ভয়েস টাইপিং"), settings.voiceLanguage.uiLabel, UiR.drawable.ms_mic) { onOpen(Destination.VOICE) }
            SettingsItem(
                tr("Emoji, GIFs & stickers", "ইমোজি, GIF ও স্টিকার"),
                if (com.yeamin.jolpai.ui.GifSources.name(settings) == null) tr("GIF search off", "GIF খোঁজা বন্ধ") else tr("GIF search on", "GIF খোঁজা চালু"),
                UiR.drawable.ms_add_reaction,
            ) { onOpen(Destination.MEDIA) }
            SettingsItem(
                tr("Top bar & panels", "ওপরের বার ও প্যানেল"),
                settings.toolbar.joinToString(" · ") { toolbarName(it) }.ifEmpty { tr("None", "কিছু নেই") },
                UiR.drawable.ms_toolbar,
            ) { onOpen(Destination.TOOLBAR) }
            SettingsItem(
                tr("Key font", "কী-এর ফন্ট"),
                settings.banglaKeyFont?.let { bn -> "${settings.keyFont.uiLabel} · ${bn.uiLabel}" } ?: settings.keyFont.uiLabel,
                UiR.drawable.ms_text_fields,
            ) { onOpen(Destination.KEY_FONT) }
            SettingsItem(
                tr("Custom gestures", "নিজের মতো জেসচার"),
                if (!settings.gestures.enabled) {
                    tr("Off", "বন্ধ")
                } else {
                    val n = settings.gestures.activeCount(BuiltIns(settings.cursorSwipe, settings.doubleSpacePeriod))
                    tr("$n shortcut${if (n == 1) "" else "s"} on", "${num(n)}টি শর্টকাট চালু")
                },
                UiR.drawable.ms_gesture,
            ) { onOpen(Destination.GESTURES) }
            SettingsItem(
                tr("Typing stats", "টাইপিংয়ের হিসাব"),
                tr("How much you type, by day and language", "দিন ও ভাষা অনুযায়ী কত লিখেছেন"),
                UiR.drawable.ms_history,
            ) { onOpen(Destination.STATS) }
        }

        SettingsGroup(tr("Privacy & help", "গোপনীয়তা ও সাহায্য"), tone = GroupTone.PRIMARY) {
            SettingsItem(
                tr("Privacy", "গোপনীয়তা"),
                if (settings.personalizedLearning) {
                    tr(
                        "Learning on · $learnedWordCount learned word${if (learnedWordCount == 1) "" else "s"}",
                        "শেখা চালু · ${num(learnedWordCount)}টি শেখা শব্দ",
                    )
                } else {
                    tr("Learning off", "শেখা বন্ধ")
                },
                UiR.drawable.ms_shield,
            ) { onOpen(Destination.PRIVACY) }
            SettingsItem(
                tr("Backup & restore", "ব্যাকআপ ও রিস্টোর"),
                tr("Save settings and your words to a file", "সেটিংস ও আপনার শব্দ ফাইলে রাখুন"),
                UiR.drawable.ms_backup,
            ) { onOpen(Destination.BACKUP) }
            SettingsItem(
                tr("Report a problem", "সমস্যা জানান"),
                tr("Tell us what went wrong", "কী সমস্যা হলো আমাদের জানান"),
                UiR.drawable.ms_bug_report,
            ) { onOpen(Destination.REPORT_BUG) }
            SettingsItem(
                tr("About & updates", "অ্যাপ সম্পর্কে ও আপডেট"),
                tr("Version, updates, open-source credits", "ভার্সন, আপডেট, ওপেন-সোর্স কৃতজ্ঞতা"),
                UiR.drawable.ms_info,
            ) { onOpen(Destination.ABOUT) }
        }
    }
}

/** Two tiles side by side, both as tall as the taller one. */
@Composable
private fun TileRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** A big square-ish button: icon badge on top, then a name and what it's set to now. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.HomeTile(
    title: String,
    summary: String,
    @DrawableRes icon: Int,
    tone: GroupTone,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "tilePress",
    )
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CompositionLocalProvider(LocalGroupTone provides tone) { IconBadge(icon, size = 44) }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "English" / "ইংরেজি" and the Bangla layouts' short names (অভ্র, জাতীয়, প্রভাত). */
private fun shortName(id: LayoutId): String = if (id == LayoutId.ENGLISH) tr("English", "ইংরেজি") else id.shortLabel

/** Setup steps while Jolpai isn't the keyboard yet; afterwards a small "ready" card. */
@Composable
private fun SetupCard(setup: SetupStatus, layouts: List<LayoutId>, onOpenKeyboardSettings: () -> Unit, onPickKeyboard: () -> Unit) {
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
                        if (ready) tr("Jolpai is your keyboard", "জলপাই এখন আপনার কীবোর্ড") else tr("Finish setting up", "সেটআপ শেষ করুন"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        if (ready) tr("${layoutList(layouts)} · private, on your phone", "${layoutList(layouts)} · সব কিছু আপনার ফোনেই")
                        else tr("Two quick steps and you can type", "দুটি সহজ ধাপ, তারপরই টাইপ করুন"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            if (ready) {
                FilledTonalButton(onClick = onPickKeyboard) { Text(tr("Switch keyboard", "কীবোর্ড বদলান")) }
            } else {
                SetupStep(
                    1,
                    tr("Turn on Jolpai Keyboard", "জলপাই কীবোর্ড চালু করুন"),
                    tr(
                        "Switch on “Jolpai Keyboard” in the list. Android shows a warning; Jolpai keeps everything on your phone.",
                        "তালিকায় “Jolpai Keyboard” চালু করুন। অ্যান্ড্রয়েড একটা সতর্কবার্তা দেখাবে; জলপাই সব কিছু আপনার ফোনেই রাখে।",
                    ),
                    setup.enabled,
                    tr("Open keyboard list", "কীবোর্ডের তালিকা খুলুন"),
                    onOpenKeyboardSettings,
                    enabled = true,
                )
                SetupStep(
                    2,
                    tr("Choose Jolpai", "জলপাই বেছে নিন"),
                    tr("Pick “Jolpai Keyboard” in the keyboard chooser.", "কীবোর্ডের তালিকা থেকে “Jolpai Keyboard” বেছে নিন।"),
                    setup.selected,
                    tr("Choose keyboard", "কীবোর্ড বেছে নিন"),
                    onPickKeyboard,
                    enabled = setup.enabled,
                    help = tr("List empty? Set it in keyboard settings", "তালিকা খালি? কীবোর্ড সেটিংস থেকে বেছে নিন") to onOpenKeyboardSettings,
                )
            }
        }
    }
}

@Composable
internal fun SetupStep(
    number: Int, title: String, detail: String, done: Boolean, action: String, onAction: () -> Unit, enabled: Boolean,
    help: Pair<String, () -> Unit>? = null,
) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = RoundedCornerShape(50),
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(28.dp),
        ) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (done) {
                    MsIcon(UiR.drawable.ms_check, tr("Done", "হয়ে গেছে"), Modifier.size(18.dp), MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(num(number), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!done) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onAction, enabled = enabled) { Text(action) }
                if (help != null) TextButton(onClick = help.second, enabled = enabled) { Text(help.first) }
            }
        }
    }
}

/** A row of theme sketches: tap to use one straight away. */
@Composable
private fun QuickThemes(themes: List<KeyboardTheme>, activeId: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            tr("Quick themes", "এক ট্যাপে থিম"),
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

/** The layouts that are on, as the space bar names them: "English, অভ্র and জাতীয়". */
private fun layoutList(layouts: List<LayoutId>): String {
    val names = layouts.map { shortName(it) }
    return if (names.size < 2) names.joinToString() else names.dropLast(1).joinToString(", ") + tr(" and ", " ও ") + names.last()
}

fun layoutBadge(id: LayoutId): String = when (id) {
    LayoutId.ENGLISH -> "En"
    LayoutId.AVRO -> "অ"
    LayoutId.NATIONAL -> "জা"
    LayoutId.PROBHAT -> "প্র"
}
