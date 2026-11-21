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
    SettingsScaffold("Quick setup", onBack) {
        KeyboardPreview(theme, settings)

        SettingsGroup("Set up like my old keyboard", tone = GroupTone.PRIMARY) {
            fun preset(previous: PreviousKeyboard, name: String) {
                vm.applyWelcome(WelcomeChoices.suggestedFor(previous, settings))
                applied = name
            }
            SettingsItem("Ridmik Keyboard", "Classic style, English ⇄ অভ্র on the space bar, no autocorrect", UiR.drawable.ms_keyboard_keys, showChevron = false) { preset(PreviousKeyboard.RIDMIK, "Ridmik") }
            SettingsItem("Gboard", "Simple style laid out like Gboard, autocorrect on", UiR.drawable.ms_keyboard, showChevron = false) { preset(PreviousKeyboard.GBOARD, "Gboard") }
        }
        applied?.let {
            Text(
                "Set up like $it. Fine-tune it below; the preview shows the result.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        val style = KeyboardStyle.of(settings)
        SettingsGroup("Style") {
            for (s in KeyboardStyle.entries) {
                SettingsRadio(
                    "${s.banglaLabel} · ${s.label}" + (s.like?.let { " (like $it)" } ?: ""), s.summary,
                    selected = style == s,
                ) { vm.applyStyle(s) }
            }
        }
        if (style == null) SettingsNote("Your own mix (Toolbar & panels). Pick a style to go back to one of these.")

        SettingsGroup("Bangla layouts") {
            val on = settings.enabledLayouts
            // The last layout can't be turned off, so the switch for it is disabled.
            for ((layout, summary) in listOf(
                LayoutId.AVRO to "Phonetic: ami → আমি",
                LayoutId.NATIONAL to "জাতীয়, the same layout as Ridmik's National",
                LayoutId.PROBHAT to "প্রভাত, the fixed layout used on Linux",
            )) {
                SettingsSwitch(
                    layout.displayName, summary, layout in on, { vm.setLayoutEnabled(layout, it) },
                    UiR.drawable.ms_language, enabled = layout !in on || on.size > 1,
                )
            }
        }

        SettingsGroup("Comfort") {
            SettingsSwitch("Autocorrect English words", "Fixes typos when you press space", settings.autoCorrect, vm::setAutoCorrect, UiR.drawable.ms_spellcheck)
            SettingsSwitch("Number row", "A row of digits above the letters", settings.numberRow, vm::setNumberRow, UiR.drawable.ms_123)
            SettingsSwitch("Key sound", null, settings.keySound, vm::setKeySound, UiR.drawable.ms_volume_up)
            SettingsSwitch(
                "Vibrate on key press", null, settings.haptics != HapticLevel.OFF,
                { vm.setHaptics(if (it) HapticLevel.SYSTEM else HapticLevel.OFF) }, UiR.drawable.ms_vibration,
            )
        }

        SettingsGroup {
            SettingsItem("Show the welcome again", "The first-run pages, with tips for typing", UiR.drawable.ms_restart_alt) { vm.restartWelcome() }
        }
    }
}
