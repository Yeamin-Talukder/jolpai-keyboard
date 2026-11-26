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
    LayoutId.ENGLISH -> tr("QWERTY · hold a letter for é, ñ", "QWERTY · অক্ষর চেপে ধরলে é, ñ")
    LayoutId.AVRO -> tr("Type how it sounds: ami → আমি · reph: rr", "উচ্চারণ মতো লিখুন: ami → আমি · রেফ: rr")
    LayoutId.NATIONAL -> tr("Same as Ridmik · hold a key for its corner letter", "রিদমিকের মতো · কোণের অক্ষর পেতে কী চেপে ধরুন")
    LayoutId.PROBHAT -> tr("As on a computer · corners show the Shift letter", "কম্পিউটারের মতো · কোণে শিফটের অক্ষর")
}

/** Which layouts are on, which one to start with, and how to switch. */
@Composable
fun LanguagesScreen(vm: MainViewModel, settings: KeyboardSettings, onBack: () -> Unit) {
    SettingsScaffold(tr("Languages & layouts", "ভাষা ও লেআউট"), onBack) {
        SettingsGroup(tr("Layouts", "লেআউট")) {
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
                        Text(id.uiName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal))
                        Text(layoutHint(id), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(checked = on, onCheckedChange = null, enabled = !isLast)
                }
            }
        }
        SettingsNote(
            tr(
                "At least one layout stays on. Tap 🌐 to switch layout; hold it for the list and other keyboards.",
                "অন্তত একটি লেআউট চালু থাকবে। লেআউট বদলাতে 🌐 চাপুন; তালিকা ও অন্য কীবোর্ড পেতে চেপে ধরুন।",
            ),
        )

        SettingsGroup(tr("Starting layout", "শুরুর লেআউট")) {
            SettingsSwitch(
                tr("Remember last layout", "শেষ লেআউট মনে রাখুন"),
                tr("Start with the layout you used last", "শেষবার যে লেআউটে ছিলেন, সেটিতেই শুরু"),
                settings.rememberLastLayout,
                vm::setRememberLastLayout,
                UiR.drawable.ms_history,
            )
            SettingsChoice(
                if (settings.rememberLastLayout) tr("Default layout", "ডিফল্ট লেআউট") else tr("Always start with", "সবসময় এই লেআউটে শুরু"),
                null,
                settings.enabledLayouts.map { it to it.uiName },
                settings.defaultLayout,
                vm::setDefaultLayout,
                UiR.drawable.ms_keyboard,
            )
        }

        SettingsGroup(tr("Offline translation", "অফলাইন অনুবাদ"), tone = GroupTone.TERTIARY) {
            OfflineTranslationSetting()
        }
        SettingsNote(
            tr(
                "Tap Translate in the top bar, type, and insert the translation. It runs on your phone; only the pack is downloaded from Google.",
                "ওপরের বারে অনুবাদ চাপুন, লিখুন, তারপর অনুবাদটি বসান। অনুবাদ ফোনেই হয়; শুধু প্যাকটি Google থেকে ডাউনলোড হয়।",
            ),
        )
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
        com.yeamin.jolpai.ui.TranslateStatus.CHECKING -> tr("Checking…", "দেখা হচ্ছে…")
        com.yeamin.jolpai.ui.TranslateStatus.NOT_DOWNLOADED -> tr("Download once (about $size MB), then it works offline", "একবার ডাউনলোড করুন (প্রায় ${num(size)} MB), তারপর ইন্টারনেট ছাড়াই চলবে")
        com.yeamin.jolpai.ui.TranslateStatus.DOWNLOADING -> tr("Downloading…", "ডাউনলোড হচ্ছে…")
        com.yeamin.jolpai.ui.TranslateStatus.READY -> tr("Ready · works offline", "তৈরি · ইন্টারনেট ছাড়াই চলে")
        com.yeamin.jolpai.ui.TranslateStatus.FAILED -> tr("Download failed. Check the internet and try again.", "ডাউনলোড হয়নি। ইন্টারনেট দেখে আবার চেষ্টা করুন।")
    }
    SettingsItem(
        tr("English ⇄ Bangla pack", "ইংরেজি ⇄ বাংলা প্যাক"),
        summary,
        UiR.drawable.ms_translate,
        showChevron = false,
        enabled = status != com.yeamin.jolpai.ui.TranslateStatus.CHECKING && status != com.yeamin.jolpai.ui.TranslateStatus.DOWNLOADING,
        trailing = {
            when (status) {
                com.yeamin.jolpai.ui.TranslateStatus.READY -> TextButton(onClick = { translator.delete() }) { Text(tr("Delete", "মুছুন")) }
                com.yeamin.jolpai.ui.TranslateStatus.DOWNLOADING -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                com.yeamin.jolpai.ui.TranslateStatus.CHECKING -> Unit
                else -> androidx.compose.material3.FilledTonalButton(onClick = { translator.download() }) { Text(tr("Download", "ডাউনলোড")) }
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
    SettingsScaffold(tr("Typing & gestures", "টাইপিং ও জেসচার"), onBack) {
        SettingsGroup(tr("Typing", "টাইপিং")) {
            SettingsSwitch(tr("Automatic capitals", "নিজে থেকে বড় হাতের অক্ষর"), tr("Capital letter at the start of each English sentence", "ইংরেজি বাক্যের শুরুতে বড় হাতের অক্ষর"), settings.autoCapitalize, vm::setAutoCapitalize, UiR.drawable.ms_text_fields)
            SettingsSwitch(tr("Double-space full stop", "দুই স্পেসে দাঁড়ি"), tr("Two spaces type “. ” in English and “। ” in Bangla", "দুবার স্পেস দিলে বাংলায় “। ”, ইংরেজিতে “. ”"), settings.doubleSpacePeriod, vm::setDoubleSpacePeriod, UiR.drawable.ms_space_bar)
            SettingsSwitch(
                tr("Fix vowel signs", "কার নিজে থেকে ঠিক হবে"),
                tr(
                    "জাতীয় and প্রভাত: ি alone becomes ই, ে + া becomes ো. Off: exactly as typed, like Ridmik.",
                    "জাতীয় ও প্রভাত: একা ি হয় ই, ে + া হয় ো। বন্ধ থাকলে রিদমিকের মতো যা চাপবেন তা-ই বসবে।",
                ),
                settings.banglaAutoVowels, vm::setBanglaAutoVowels, UiR.drawable.ms_auto_awesome,
            )
        }
        SettingsGroup(tr("Hold for more characters", "চেপে ধরলে আরও অক্ষর")) {
            LongPressSetting(settings.longPressMs, vm::setLongPressMs)
        }
        SettingsGroup(tr("Gestures", "জেসচার")) {
            SettingsSwitch(
                tr("Swipe gestures", "সোয়াইপ জেসচার"),
                tr(
                    "Slide on the space bar to move the cursor; slide left from backspace to delete words.",
                    "স্পেস বারে সোয়াইপ করে কার্সর সরান; ব্যাকস্পেস থেকে বাঁয়ে সোয়াইপ করে শব্দ মুছুন।",
                ),
                settings.cursorSwipe,
                vm::setCursorSwipe,
                UiR.drawable.ms_swipe,
            )
            SettingsSwitch(
                tr("Trackpad: change lines", "ট্র্যাকপ্যাডে লাইন বদল"),
                tr(
                    "Hold the space bar to move the cursor; up and down change lines too.",
                    "স্পেস বার চেপে ধরে কার্সর সরান; ওপর-নিচে সরালে লাইনও বদলাবে।",
                ),
                settings.trackpadVertical,
                vm::setTrackpadVertical,
                UiR.drawable.ms_drag_pan,
            )
            SettingsSwitch(
                tr("Hold Enter for emoji", "এন্টার চেপে ধরলে ইমোজি"),
                if (settings.enterHoldSelect) {
                    tr("Hold Enter and let go without sliding to open emoji.", "এন্টার চেপে ধরে না সরিয়ে ছেড়ে দিলে ইমোজি খুলবে।")
                } else {
                    tr("Hold Enter to open emoji. A tap works as usual.", "ইমোজি খুলতে এন্টার চেপে ধরুন। সাধারণ চাপ আগের মতোই কাজ করবে।")
                },
                settings.enterHoldEmoji,
                vm::setEnterHoldEmoji,
                UiR.drawable.ms_mood,
            )
            SettingsSwitch(
                tr("Hold Enter to select", "এন্টার চেপে ধরে সিলেক্ট"),
                tr(
                    "Hold Enter and slide to select text. Not in password fields.",
                    "এন্টার চেপে ধরে সরিয়ে লেখা সিলেক্ট করুন। পাসওয়ার্ডের ঘরে নয়।",
                ),
                settings.enterHoldSelect,
                vm::setEnterHoldSelect,
                UiR.drawable.ms_keyboard_return,
            )
        }
        SettingsGroup(tr("Typing accuracy", "নির্ভুল টাইপিং")) {
            SettingsSwitch(
                tr("Predictive touch", "স্মার্ট টাচ"),
                tr(
                    "Guesses the next letter (lik → e) and makes it easier to hit.",
                    "পরের অক্ষর আন্দাজ করে (lik → e) সেটি চাপা সহজ করে।",
                ),
                settings.predictiveTouch,
                vm::setPredictiveTouch,
                UiR.drawable.ms_auto_awesome,
            )
            SettingsSwitch(
                tr("Learn my touch", "আমার টাচ শেখা"),
                tr(
                    "Learns where you tap each key. Never in password or private fields.",
                    "আপনি কী-এর কোথায় চাপেন তা শেখে। পাসওয়ার্ড বা প্রাইভেট ঘরে কখনো নয়।",
                ),
                settings.touchCalibration,
                vm::setTouchCalibration,
                UiR.drawable.ms_tune,
                enabled = settings.predictiveTouch,
            )
            SettingsItem(
                tr("Reset learned touch", "শেখা টাচ রিসেট"),
                tr("Forget where you tap", "শেখা চাপার জায়গা ভুলে যাবে"),
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
                Text(tr("Hold time", "চেপে ধরার সময়"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal))
                Text(
                    tr("How long to hold a key for its corner letter, like ঋ or ৎ", "কোণের অক্ষর (যেমন ঋ বা ৎ) পেতে কতক্ষণ চেপে ধরবেন"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    tr("$shown ms", "${num(shown)} মি.সে."),
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
            Text(tr("Faster", "দ্রুত"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(tr("Slower", "ধীরে"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        val presets = listOf(tr("Quick", "দ্রুত") to DEFAULT_LONG_PRESS_MS, tr("Normal", "স্বাভাবিক") to 350, tr("Relaxed", "আরামে") to 500)
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
                        Text(tr("$ms ms", "${num(ms)} মি.সে."), style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
    SettingsScaffold(tr("Suggestions & spelling", "সাজেশন ও বানান"), onBack) {
        SettingsGroup(tr("Basics", "মূল")) {
            SettingsSwitch(tr("Show suggestions", "সাজেশন দেখান"), tr("Word choices above the keys", "কী-গুলোর ওপরে শব্দের সাজেশন"), settings.showSuggestions, vm::setShowSuggestions, UiR.drawable.ms_lightbulb)
            SettingsSwitch(tr("Autocorrect", "অটোকারেক্ট"), tr("Fixes English typos when you press space. Backspace undoes it.", "স্পেস চাপলে ইংরেজি ভুল বানান ঠিক করে। ব্যাকস্পেস চাপলে আগের মতো হয়।"), settings.autoCorrect, vm::setAutoCorrect, UiR.drawable.ms_spellcheck, enabled = settings.showSuggestions)
            SettingsSwitch(tr("Next-word suggestions", "পরের শব্দের সাজেশন"), tr("Suggests the word you'll likely type next", "পরে যে শব্দ লিখতে পারেন, তার সাজেশন"), settings.nextWordPrediction, vm::setNextWordPrediction, UiR.drawable.ms_auto_awesome, enabled = settings.showSuggestions)
        }
        SettingsGroup(tr("Extras", "আরও")) {
            SettingsSwitch(tr("Emoji suggestions", "ইমোজি সাজেশন"), "pizza → 🍕, হাসি → 😄", settings.emojiSuggestions, vm::setEmojiSuggestions, UiR.drawable.ms_mood, enabled = settings.showSuggestions)
            SettingsSwitch(
                tr("On-device AI predictions", "ফোনেই এআই সাজেশন"),
                tr(
                    "Smarter suggestions in English and Bangla. Runs only on this phone.",
                    "ইংরেজি ও বাংলায় আরও ভালো সাজেশন। শুধু এই ফোনেই চলে।",
                ),
                settings.smartPredictions, vm::setSmartPredictions, UiR.drawable.ms_auto_awesome, enabled = settings.showSuggestions,
            )
            SettingsSwitch(
                tr("Banglish suggestions", "বাংলিশ সাজেশন"),
                tr(
                    "Bangla in English letters: valob → valobashi. Words like kmn aren't autocorrected.",
                    "ইংরেজি অক্ষরে বাংলা: valob → valobashi। kmn-এর মতো শব্দ অটোকারেক্ট হয় না।",
                ),
                settings.banglishSuggestions, vm::setBanglishSuggestions, UiR.drawable.ms_spellcheck, enabled = settings.showSuggestions,
            )
            SettingsSwitch(tr("Block offensive words", "খারাপ শব্দ বাদ দিন"), tr("Never suggested, but you can still type them", "সাজেশনে আসবে না, তবে নিজে লিখতে পারবেন"), settings.blockOffensiveWords, vm::setBlockOffensiveWords, UiR.drawable.ms_block, enabled = settings.showSuggestions)
        }
        SettingsGroup {
            SettingsItem(
                tr("Personal dictionary", "নিজের অভিধান"),
                tr(
                    "$personalWordCount word${if (personalWordCount == 1) "" else "s"} · always suggested, never autocorrected",
                    "${num(personalWordCount)}টি শব্দ · সবসময় সাজেশনে আসে, অটোকারেক্ট হয় না",
                ),
                UiR.drawable.ms_menu_book,
                onClick = onOpenDictionary,
            )
        }
    }
}

/** Clipboard history settings, with the history and snippets one tap away. */
@Composable
fun ClipboardSettingsScreen(vm: MainViewModel, settings: KeyboardSettings, onOpenClipboard: () -> Unit, onOpenSnippets: () -> Unit, onBack: () -> Unit) {
    SettingsScaffold(tr("Clipboard & snippets", "ক্লিপবোর্ড ও স্নিপেট"), onBack) {
        SettingsGroup {
            SettingsItem(tr("Clipboard history", "ক্লিপবোর্ডের ইতিহাস"), tr("Search and pin what you copied", "কপি করা লেখা খুঁজুন ও পিন করুন"), UiR.drawable.ms_content_paste, onClick = onOpenClipboard)
            SettingsItem(tr("Snippets", "স্নিপেট"), tr("Saved text you insert with a shortcut", "শর্টকাট দিয়ে বসানোর জন্য সেভ করা লেখা"), UiR.drawable.ms_short_text, onClick = onOpenSnippets)
        }
        SettingsGroup(tr("History", "ইতিহাস")) {
            SettingsSwitch(tr("Save clipboard history", "ক্লিপবোর্ডের ইতিহাস রাখুন"), tr("Keep copied text on this phone", "কপি করা লেখা এই ফোনে রাখুন"), settings.clipboardHistory, vm::setClipboardHistory, UiR.drawable.ms_history)
            SettingsSwitch(tr("Save copied images", "কপি করা ছবি রাখুন"), tr("A private copy, up to 10 MB each", "নিজস্ব কপি, প্রতিটি ১০ MB পর্যন্ত"), settings.clipboardImages, vm::setClipboardImages, UiR.drawable.ms_image, enabled = settings.clipboardHistory)
            SettingsChoice(
                tr("Keep unpinned copies for", "পিন না করা কপি কতদিন থাকবে"),
                tr("Pinned copies stay until you delete them", "পিন করা কপি আপনি না মোছা পর্যন্ত থাকে"),
                ClipRetention.entries.map { it to it.uiLabel },
                settings.clipboardRetention,
                vm::setClipboardRetention,
                UiR.drawable.ms_schedule,
                enabled = settings.clipboardHistory,
            )
        }
        SettingsNote(
            tr(
                "Copies made while another keyboard was on aren't saved. Copies from password fields, banking or password apps are never saved.",
                "অন্য কীবোর্ড চালু থাকার সময়ের কপি সেভ হয় না। পাসওয়ার্ডের ঘর, ব্যাংকিং বা পাসওয়ার্ড অ্যাপের কপি কখনো সেভ হয় না।",
            ),
        )
    }
}

fun toolbarName(item: ToolbarItem): String = when (item) {
    ToolbarItem.EMOJI -> tr("Emoji", "ইমোজি")
    ToolbarItem.CLIPBOARD -> tr("Clipboard", "ক্লিপবোর্ড")
    ToolbarItem.VOICE -> tr("Voice", "ভয়েস")
    ToolbarItem.EDIT -> tr("Editing", "এডিট")
    ToolbarItem.TRANSLATE -> tr("Translate", "অনুবাদ")
    ToolbarItem.CHECK -> tr("Check", "যাচাই")
    ToolbarItem.NUMBER_PAD -> tr("Number pad", "নম্বর প্যাড")
}

private fun toolbarIcon(item: ToolbarItem): Int = when (item) {
    ToolbarItem.EMOJI -> UiR.drawable.ms_mood
    ToolbarItem.CLIPBOARD -> UiR.drawable.ms_content_paste
    ToolbarItem.VOICE -> UiR.drawable.ms_mic
    ToolbarItem.EDIT -> UiR.drawable.ms_edit_note
    ToolbarItem.TRANSLATE -> UiR.drawable.ms_translate
    ToolbarItem.CHECK -> UiR.drawable.ms_spellcheck
    ToolbarItem.NUMBER_PAD -> UiR.drawable.ms_123
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
    SettingsScaffold(tr("Top bar & panels", "ওপরের বার ও প্যানেল"), onBack) {
        SettingsNote(tr("Easy setup on Home sets all of this in one tap.", "হোম পাতার “সহজ সেটআপ” থেকে এক ট্যাপে সব ঠিক করা যায়।"))
        SettingsGroup {
            SettingsChoice(
                tr("Emoji and clipboard open", "ইমোজি ও ক্লিপবোর্ড খুলবে"),
                placement.uiSummary,
                com.yeamin.jolpai.core.settings.PanelPlacement.entries.map { it to it.uiLabel },
                placement,
                onPlacement,
                UiR.drawable.ms_dock_to_bottom,
            )
        }
        SettingsNote(tr("Tools above the keys. While suggestions show, tap the grid button to see them.", "কী-গুলোর ওপরের টুল। সাজেশন দেখালে গ্রিড বোতাম চেপে টুলগুলো দেখুন।"))
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
                        Text(item.uiTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onChange(items.toMutableList().apply { add(index - 1, removeAt(index)) }) }, enabled = on && index > 0) {
                        MsIcon(UiR.drawable.ms_arrow_upward, tr("Move ${toolbarName(item)} up", "${toolbarName(item)} ওপরে সরান"))
                    }
                    IconButton(onClick = { onChange(items.toMutableList().apply { add(index + 1, removeAt(index)) }) }, enabled = on && index < items.lastIndex) {
                        MsIcon(UiR.drawable.ms_arrow_downward, tr("Move ${toolbarName(item)} down", "${toolbarName(item)} নিচে সরান"))
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
    SettingsScaffold(tr("Privacy", "গোপনীয়তা"), onBack) {
        SettingsNote(
            tr(
                "Jolpai works offline and never sends what you type anywhere. It learns your words on this phone only, " +
                    "and nothing in password or private (incognito) fields.",
                "জলপাই অফলাইনে চলে, আপনার লেখা কোথাও পাঠায় না। আপনার শব্দ শুধু এই ফোনেই শেখে; " +
                    "পাসওয়ার্ড বা প্রাইভেট (ইনকগনিটো) ঘরে কিছুই শেখে না।",
            ),
        )
        SettingsGroup {
            SettingsSwitch(tr("Learn from my typing", "আমার টাইপিং থেকে শেখা"), tr("Remembers your words for better suggestions", "ভালো সাজেশনের জন্য আপনার শব্দ মনে রাখে"), settings.personalizedLearning, vm::setPersonalizedLearning, UiR.drawable.ms_auto_awesome)
            SettingsItem(
                tr("Clear learned data", "শেখা তথ্য মুছে ফেলুন"),
                tr("$learnedWordCount learned word${if (learnedWordCount == 1) "" else "s"}", "${num(learnedWordCount)}টি শেখা শব্দ"),
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
            title = { Text(tr("Clear learned data?", "শেখা তথ্য মুছে ফেলবেন?")) },
            text = {
                Text(
                    tr(
                        "Jolpai will forget the words and Avro choices it learned from your typing. Your personal dictionary stays.",
                        "আপনার টাইপিং থেকে শেখা শব্দ ও অভ্রের পছন্দগুলো জলপাই ভুলে যাবে। নিজের অভিধান থেকে যাবে।",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearLearnedData()
                    confirm = false
                }) { Text(tr("Clear", "মুছে ফেলুন")) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("Cancel", "বাতিল")) } },
        )
    }
}

/** Real fields of each input type, so every keyboard mode can be tried. */
@Composable
fun TestFieldsScreen(onBack: () -> Unit) {
    SettingsScaffold(tr("Try the keyboard", "কীবোর্ড চালিয়ে দেখুন"), onBack) {
        SettingsNote(tr("Each box gets its own keyboard: Enter changes, and number boxes get a number pad.", "প্রতিটি ঘরে আলাদা কীবোর্ড আসে: এন্টার বদলায়, আর নম্বরের ঘরে নম্বর প্যাড আসে।"))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
            TestField(tr("Message", "মেসেজ"), KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send))
            TestField(tr("Search", "খুঁজুন"), KeyboardOptions(imeAction = ImeAction.Search))
            TestField(tr("Email", "ইমেইল"), KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
            TestField(tr("Website", "ওয়েবসাইট"), KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go))
            TestField(tr("Password", "পাসওয়ার্ড"), KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), password = true)
            TestField(tr("Number", "নম্বর"), KeyboardOptions(keyboardType = KeyboardType.Number))
            TestField(tr("Phone", "ফোন"), KeyboardOptions(keyboardType = KeyboardType.Phone))
            TestField(tr("Note (several lines)", "নোট (কয়েক লাইন)"), KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), singleLine = false)
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
