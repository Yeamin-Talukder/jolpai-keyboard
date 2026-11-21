package com.yeamin.jolpai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.settings.KeyFont
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.core.settings.banglaLettersFont
import com.yeamin.jolpai.theme.KeyboardTheme
import com.yeamin.jolpai.ui.CustomKeyFont
import com.yeamin.jolpai.ui.KeyFonts
import com.yeamin.jolpai.ui.R as UiR

/**
 * Home → Key font: the font on the keys, with a live keyboard. One font for both scripts, or
 * with "Mix fonts" one for English letters and another for Bangla letters. Bundled fonts in four
 * groups (Bangla and English, Stylish Bangla, English, Stylish English) and "Your own font".
 * Bangla vowel-sign keys show just the sign in every font (see DisplayLabel in keyboard-ui).
 */
@Composable
fun KeyFontScreen(vm: MainViewModel, settings: KeyboardSettings, theme: KeyboardTheme, onBack: () -> Unit) {
    val context = LocalContext.current
    val mixed = settings.banglaKeyFont != null
    // When mixing, which letters the list below sets.
    var editingBangla by rememberSaveable { mutableStateOf(false) }
    val forBangla = mixed && editingBangla

    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            note = null
            vm.pickCustomKeyFont(uri, forBangla) { message ->
                busy = false
                note = message
            }
        }
    }
    val pick = {
        // Font files come with many types (or none) depending on the app that has them.
        picker.launch(arrayOf("font/ttf", "font/otf", "font/sfnt", "font/collection", "application/x-font-ttf", "application/x-font-otf", "application/font-sfnt", "application/vnd.ms-opentype", "application/octet-stream"))
    }
    val hasCustom = settings.customKeyFontName != null && remember(settings.customKeyFontVersion) { CustomKeyFont.file(context).isFile }

    val selected = if (forBangla) settings.banglaLettersFont else settings.keyFont
    val choose: (KeyFont) -> Unit = { if (forBangla) vm.setBanglaKeyFont(it) else vm.setKeyFont(it) }

    // The live keyboard shows the letters being chosen: Bangla for a Bangla font or the Bangla
    // side of a mix (so the clean vowel-sign keys show too), English otherwise. 🌐 in it switches.
    val banglaLayout = listOf(LayoutId.NATIONAL, LayoutId.PROBHAT, LayoutId.AVRO).firstOrNull { it in settings.enabledLayouts }
    val showBangla = if (mixed) editingBangla else settings.keyFont.bangla && settings.keyFont != KeyFont.SYSTEM
    val previewLayout = if (showBangla && banglaLayout != null) banglaLayout else LayoutId.ENGLISH

    SettingsScaffold("Key font", onBack) {
        key(previewLayout) {
            KeyboardPreview(theme, settings.copy(rememberLastLayout = true, lastLayout = previewLayout))
        }
        Text(
            if (mixed) "English: ${fontName(settings.keyFont, settings)} · Bangla: ${fontName(settings.banglaLettersFont, settings)}"
            else "In use: ${fontName(settings.keyFont, settings)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )

        SettingsGroup {
            SettingsSwitch(
                "Mix fonts",
                "One font for English letters and another for Bangla letters",
                mixed,
                { on ->
                    vm.setBanglaKeyFont(if (on) settings.banglaLettersFont else null)
                    editingBangla = on
                },
                UiR.drawable.ms_text_fields,
            )
        }
        if (mixed) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !editingBangla,
                    onClick = { editingBangla = false },
                    label = { Text("English letters · ${fontName(settings.keyFont, settings)}") },
                )
                FilterChip(
                    selected = editingBangla,
                    onClick = { editingBangla = true },
                    label = { Text("বাংলা letters · ${fontName(settings.banglaLettersFont, settings)}") },
                )
            }
        }

        SettingsGroup {
            KeyFontRow(KeyFont.SYSTEM, selected == KeyFont.SYSTEM, onSelect = { choose(KeyFont.SYSTEM) })
        }
        val groups = when {
            forBangla -> KeyFont.BANGLA_GROUPS
            else -> KeyFont.ENGLISH_GROUPS
        }
        groups.forEach { group ->
            // English letters in a mix: only fonts that have them.
            val fonts = KeyFont.bundled(group).filter { !mixed || forBangla || it.english }
            if (fonts.isNotEmpty()) {
                SettingsGroup(group.title) {
                    fonts.forEach { font -> KeyFontRow(font, selected == font, onSelect = { choose(font) }) }
                }
            }
        }
        if (!mixed) {
            SettingsNote("English and Stylish English fonts change the English letters; with them, Bangla keys use the phone's font. Turn on Mix fonts to pick a Bangla font as well.")
        }

        SettingsGroup("Your own font") {
            if (hasCustom) {
                KeyFontRow(KeyFont.CUSTOM, selected == KeyFont.CUSTOM, name = settings.customKeyFontName, version = settings.customKeyFontVersion) {
                    choose(KeyFont.CUSTOM)
                }
                Row(Modifier.padding(start = 52.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = pick, enabled = !busy) { Text("Change file") }
                    TextButton(onClick = {
                        note = null
                        vm.removeCustomKeyFont()
                    }) { Text("Remove") }
                }
            } else {
                SettingsItem(
                    if (busy) "Checking the font…" else "Choose a font file",
                    "A .ttf or .otf font saved on this phone",
                    UiR.drawable.ms_upload,
                    showChevron = false,
                    onClick = { if (!busy) pick() },
                )
            }
        }
        note?.let { SettingsNote(it) }
        SettingsNote(
            "Fonts change the letters on the keys, not the text you type. Vowel-sign keys (ি া ে ো ু ্) show just the sign in every font. " +
                "Bundled fonts are open fonts (SIL Open Font License or Apache 2.0; see About). Your own font is copied into Jolpai and stays on this phone; " +
                "it isn't in backups. If it stops loading, the keys use the phone's font.",
        )
    }
}

/** A font's name as shown: the picked file's name for "Your own font". */
private fun fontName(font: KeyFont, settings: KeyboardSettings): String =
    if (font == KeyFont.CUSTOM) settings.customKeyFontName ?: font.label else font.label

/** A key font choice, previewed in that font. */
@Composable
private fun KeyFontRow(font: KeyFont, selected: Boolean, name: String? = null, version: Long = 0, onSelect: () -> Unit) {
    val context = LocalContext.current
    val family = remember(font, version) { KeyFonts.load(context, font)?.first?.let { FontFamily(it) } }
    val sample = when {
        font == KeyFont.SYSTEM || font == KeyFont.CUSTOM -> "অ আ ক খ গ  কি কো  Aa Bb"
        !font.english -> "অ আ ক খ গ  ধন্যবাদ  কি কো"
        font.group == KeyFont.Group.BANGLA_STYLISH -> "অ আ ক খ গ  ভালোবাসা  Aa"
        font.bangla -> "অ আ ক খ গ  ধন্যবাদ  Aa"
        else -> "Hello Aa Bb 123"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .selectable(selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name ?: font.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                sample,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = family,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
