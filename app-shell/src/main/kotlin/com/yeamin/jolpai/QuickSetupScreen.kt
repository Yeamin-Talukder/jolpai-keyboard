package com.yeamin.jolpai

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.settings.HapticLevel
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.core.settings.KeyboardStyle
import com.yeamin.jolpai.core.settings.PreviousKeyboard
import com.yeamin.jolpai.core.settings.WelcomeChoices
import com.yeamin.jolpai.theme.KeyboardTheme
import com.yeamin.jolpai.ui.R as UiR

/**
 * The welcome flow's choices in one settings page, for later: set up like an old keyboard,
 * Classic or Modern, Bangla layouts, and the comfort switches. Every change applies at once
 * and shows in the real keyboard preview at the top.
 */
@Composable
fun QuickSetupScreen(
    vm: MainViewModel,
    settings: KeyboardSettings,
    theme: KeyboardTheme,
    onBack: () -> Unit,
) {
    var applied by rememberSaveable { mutableStateOf<String?>(null) }
    SettingsScaffold(tr("Easy setup", "সহজ সেটআপ"), onBack) {
        KeyboardPreview(theme, settings)

        SettingsGroup(tr("Set up like my old keyboard", "আগের কীবোর্ডের মতো সাজান"), tone = GroupTone.PRIMARY) {
            // [applied] keeps the English name as a key; it is put into words where it is shown.
            fun preset(previous: PreviousKeyboard, name: String) {
                vm.applyWelcome(WelcomeChoices.suggestedFor(previous, settings))
                applied = name
            }
            SettingsItem(tr("Ridmik Keyboard", "রিদমিক কীবোর্ড"), tr("Classic style, English ⇄ অভ্র on the space bar, no autocorrect", "ক্লাসিক স্টাইল, স্পেস বারে ইংরেজি ⇄ অভ্র, অটোকারেক্ট বন্ধ"), UiR.drawable.ms_keyboard_keys, showChevron = false) { preset(PreviousKeyboard.RIDMIK, "Ridmik") }
            SettingsItem(tr("Gboard", "জিবোর্ড"), tr("Simple style like Gboard, autocorrect on", "জিবোর্ডের মতো সহজ স্টাইল, অটোকারেক্ট চালু"), UiR.drawable.ms_keyboard, showChevron = false) { preset(PreviousKeyboard.GBOARD, "Gboard") }
        }
        applied?.let {
            val banglaName = if (it == "Ridmik") "রিদমিক" else "জিবোর্ড"
            Text(
                tr("Set up like $it. Fine-tune it below.", "$banglaName-এর মতো সাজানো হলো। নিচে আরও বদলাতে পারেন।"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        val style = KeyboardStyle.of(settings)
        SettingsGroup(tr("Style", "স্টাইল")) {
            for (s in KeyboardStyle.entries) {
                SettingsRadio(
                    s.uiLabel, s.uiSummary,
                    selected = style == s,
                ) { vm.applyStyle(s) }
            }
        }
        if (style == null) SettingsNote(tr("You have your own mix. Pick a style to use one of these.", "আপনি নিজের মতো সাজিয়েছেন। এগুলোর একটিতে ফিরতে স্টাইল বেছে নিন।"))

        SettingsGroup(tr("Bangla layouts", "বাংলা লেআউট")) {
            val on = settings.enabledLayouts
            // The last layout can't be turned off, so the switch for it is disabled.
            for ((layout, summary) in listOf(
                LayoutId.AVRO to tr("Phonetic: ami → আমি", "উচ্চারণ মতো: ami → আমি"),
                LayoutId.NATIONAL to tr("The same as Ridmik's National", "রিদমিকের জাতীয় লেআউটের মতোই"),
                LayoutId.PROBHAT to tr("The fixed layout used on Linux", "Linux-এ প্রচলিত ফিক্সড লেআউট"),
            )) {
                SettingsSwitch(
                    layout.uiName, summary, layout in on, { vm.setLayoutEnabled(layout, it) },
                    UiR.drawable.ms_language, enabled = layout !in on || on.size > 1,
                )
            }
        }

        SettingsGroup(tr("Comfort", "সুবিধা")) {
            SettingsSwitch(tr("Autocorrect English words", "ইংরেজি শব্দ অটোকারেক্ট"), tr("Fixes typos when you press space", "স্পেস চাপলে ভুল বানান ঠিক করে"), settings.autoCorrect, vm::setAutoCorrect, UiR.drawable.ms_spellcheck)
            SettingsSwitch(tr("Number row", "সংখ্যার সারি"), tr("A row of digits above the letters", "অক্ষরের ওপরে এক সারি সংখ্যা"), settings.numberRow, vm::setNumberRow, UiR.drawable.ms_123)
            SettingsSwitch(tr("Key sound", "কী-এর শব্দ"), null, settings.keySound, vm::setKeySound, UiR.drawable.ms_volume_up)
            SettingsSwitch(
                tr("Vibrate on key press", "কী চাপলে ভাইব্রেশন"), null, settings.haptics != HapticLevel.OFF,
                { vm.setHaptics(if (it) HapticLevel.SYSTEM else HapticLevel.OFF) }, UiR.drawable.ms_vibration,
            )
        }

        SettingsGroup {
            SettingsItem(tr("Show the welcome again", "স্বাগতম পাতা আবার দেখুন"), tr("The first pages, with typing tips", "শুরুর পাতাগুলো, টাইপিংয়ের টিপসসহ"), UiR.drawable.ms_restart_alt) { vm.restartWelcome() }
        }
    }
}
