package com.yeamin.lipi

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.yeamin.lipi.ui.R as UiR

/** Save settings and personal data to a file, or restore from one. */
@Composable
fun BackupScreen(vm: MainViewModel, onBack: () -> Unit) {
    var includeLearned by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri, includeLearned) { message = it }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restoreBackup(uri) { message = it }
    }
    SettingsScaffold("Backup & restore", onBack) {
        SettingsNote(
            "A backup file holds your settings, personal dictionary, snippets and custom themes. It never " +
                "includes clipboard history, pictures or your GIPHY key. You choose where it's saved; Jolpai " +
                "doesn't upload it anywhere.",
        )
        SettingsGroup {
            SettingsItem("Save backup", "Choose where to save the file", UiR.drawable.ms_save, showChevron = false) {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                exporter.launch("lipi-backup-$date.json")
            }
            SettingsSwitch(
                "Include learned words",
                "Words, word pairs and Avro choices learned from your typing. Keep the file private if you include them.",
                includeLearned,
                { includeLearned = it },
                UiR.drawable.ms_auto_awesome,
            )
        }
        SettingsGroup {
            SettingsItem(
                "Restore from a file",
                "Settings are replaced; words, snippets and themes are added",
                UiR.drawable.ms_settings_backup_restore,
                showChevron = false,
            ) { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        }
        message?.let { SettingsNote(it) }
    }
}

private data class Notice(val title: String, val notice: String, val licence: String)

private val NOTICES = listOf(
    Notice("Avro Phonetic rules (OpenBangla rupantor) — MPL-2.0", "/avro/NOTICE.txt", "/avro/LICENSE-MPL-2.0.txt"),
    Notice("English word list (AOSP LatinIME) — Apache-2.0", "/en/NOTICE.txt", "/en/LICENSE-APACHE-2.0.txt"),
    Notice("Bangla word list and Avro search (OpenBangla upodesh) — MIT; suffixes and autocorrect (OpenBangla riti) — MPL-2.0", "/bn/NOTICE.txt", "/bn/LICENSE-MIT-upodesh.txt"),
    Notice("Emoji data (Unicode Emoji 17.0, CLDR 49) — Unicode License V3", "/emoji/NOTICE.txt", "/emoji/LICENSE-UNICODE-V3.txt"),
    Notice("Icons (Material Symbols by Google) — Apache-2.0", "/icons/NOTICE.txt", "/en/LICENSE-APACHE-2.0.txt"),
    Notice("Bangla word frequencies (FrequencyWords, OpenSubtitles) — CC BY-SA 4.0; code MIT", "/bn/NOTICE.txt", "/bn/LICENSE-MIT-FrequencyWords.txt"),
    Notice("Key fonts: Hind Siliguri, Tiro Bangla, Galada, Atma (Google Fonts) — SIL Open Font License 1.1", "/fonts/NOTICE.txt", "/fonts/OFL.txt"),
)

// Read through one of the app's own classes: NOTICES::class.java is a system List class whose
// class loader can't see the app's files, so every notice showed "(not found …)" on the phone.
private fun resourceText(path: String): String =
    Notice::class.java.getResourceAsStream(path)?.use { it.readBytes().decodeToString() } ?: "(not found: $path)"

private const val DEVELOPER_GITHUB = "https://github.com/Yeamin-Talukder"
private const val SOURCE_CODE = "https://github.com/Yeamin-Talukder/jolpai-keyboard"

/** Opens [url] in the browser; does nothing if the phone has no app for links. */
private fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Version, developer, privacy summary, permissions, and the licences of everything Jolpai includes. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    var open by remember { mutableStateOf<String?>(null) }
    SettingsScaffold("About Jolpai", onBack) {
        SettingsGroup {
            SettingsItem("Jolpai Keyboard", "Version $version · English, বাংলা (অভ্র) and বাংলা (জাতীয়)", UiR.drawable.ms_keyboard, showChevron = false) {}
        }

        SettingsGroup("Developer") {
            SettingsItem("Md Yeamin Talukder", "Made in Bangladesh 🇧🇩", UiR.drawable.ms_code, showChevron = false) {}
            SettingsItem("GitHub", DEVELOPER_GITHUB.removePrefix("https://"), UiR.drawable.ms_open_in_new) {
                openLink(context, DEVELOPER_GITHUB)
            }
            SettingsItem("Source code", "Open source under Apache-2.0 · report a problem", UiR.drawable.ms_link) {
                openLink(context, SOURCE_CODE)
            }
        }

        SettingsGroup("Privacy") {
            Bullet(UiR.drawable.ms_lock, "Everything you type stays on this phone. No account, no analytics, no ads, and no logs of what you type.")
            Bullet(UiR.drawable.ms_auto_awesome, "Learning never happens in password, private (incognito) or no-suggestion fields. You can turn it off or clear it.")
            Bullet(UiR.drawable.ms_content_paste, "Clipboard history is saved only while Jolpai is your keyboard, never from password fields or sensitive copies, and is deleted on your schedule.")
            Bullet(UiR.drawable.ms_backup, "Typing data, clipboard pictures and stickers are left out of Android's cloud backup and device transfer.")
            Bullet(UiR.drawable.ms_gif_box, "The internet is used only for GIF search with your own GIPHY key and to download the offline translation pack when you ask. Translation itself runs on the phone. Voice typing uses your phone's speech service, which may work online.")
        }

        SettingsGroup("Permissions") {
            Bullet(UiR.drawable.ms_mic, "Microphone — only for voice typing, asked for on the Voice typing screen.")
            Bullet(UiR.drawable.ms_language, "Internet — only for GIF search with your own GIPHY key and for downloading the offline translation pack.")
            Bullet(UiR.drawable.ms_vibration, "Vibration — for the key-press strengths you choose.")
        }

        SettingsGroup("Open-source data and licences") {
            NOTICES.forEach { n ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { open = if (open == n.title) null else n.title }
                        .animateContentSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MsIcon(UiR.drawable.ms_description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(n.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        MsIcon(UiR.drawable.ms_expand_more, if (open == n.title) "Hide" else "Show", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (open == n.title) {
                        Text(resourceText(n.notice), fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 10.dp))
                        Text(resourceText(n.licence), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        SettingsNote("Built with Kotlin and Jetpack (Compose, Room, DataStore, Hilt) — Apache-2.0.")
    }
}

@Composable
private fun Bullet(icon: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        MsIcon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
