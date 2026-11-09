package com.yeamin.jolpai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.yeamin.jolpai.ui.R as UiR

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
                exporter.launch("jolpai-backup-$date.json")
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
    Notice("English next-word model (Destin Moss, youcoded.ai; FineWeb and Tatoeba) — ODC-By 1.0 and CC BY 2.0 FR", "/lm/NOTICE.txt", "/lm/ODC-By-1.0.txt"),
    Notice("Key fonts: Hind Siliguri, Tiro Bangla, Galada, Atma (Google Fonts) — SIL Open Font License 1.1", "/fonts/NOTICE.txt", "/fonts/OFL.txt"),
)

// Read through one of the app's own classes: NOTICES::class.java is a system List class whose
// class loader can't see the app's files, so every notice showed "(not found …)" on the phone.
private fun resourceText(path: String): String =
    Notice::class.java.getResourceAsStream(path)?.use { it.readBytes().decodeToString() } ?: "(not found: $path)"

/** Where an update check stands. */
/** Version, updates, developer, privacy summary, permissions, and the licences of everything Jolpai includes. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val info = remember { deviceInfo(context) }
    val version = info.appVersion
    val update by AppUpdater.state.collectAsState()
    var showChanges by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    // Back from Android's "Install unknown apps" page: carry on with the update if allowed.
    LifecycleResumeEffect(Unit) {
        AppUpdater.onResume(context)
        onPauseOrDispose { }
    }

    SettingsScaffold("About Jolpai", onBack) {
        AboutHeader(version)

        SettingsGroup("Updates & feedback", tone = GroupTone.PRIMARY) {
            UpdateSection(update, version)
            SettingsItem("What's new", "Changes in each version", UiR.drawable.ms_new_releases) { showChanges = true }
            SettingsItem("Report a problem", "Opens a GitHub issue with your phone and app version filled in", UiR.drawable.ms_bug_report) {
                openLink(context, AppUpdates.issueUrl(info) { Uri.encode(it) })
            }
            SettingsItem("Share Jolpai", "Send the download link to a friend", UiR.drawable.ms_share) { shareJolpai(context) }
        }

        SettingsGroup("Developer") {
            SettingsItem(JolpaiLinks.DEVELOPER_NAME, "Made in Bangladesh 🇧🇩", UiR.drawable.ms_code, showChevron = false) {}
            SettingsItem("GitHub", JolpaiLinks.DEVELOPER_GITHUB.removePrefix("https://"), UiR.drawable.ms_open_in_new) {
                openLink(context, JolpaiLinks.DEVELOPER_GITHUB)
            }
            SettingsItem("Source code", "Open source under Apache-2.0 · ⭐ star it on GitHub", UiR.drawable.ms_link) {
                openLink(context, JolpaiLinks.REPO)
            }
        }

        SettingsGroup("Privacy") {
            Bullet(UiR.drawable.ms_lock, "Everything you type stays on this phone. No account, no analytics, no ads, and no logs of what you type.")
            Bullet(UiR.drawable.ms_auto_awesome, "Learning never happens in password, private (incognito) or no-suggestion fields. You can turn it off or clear it.")
            Bullet(UiR.drawable.ms_content_paste, "Clipboard history is saved only while Jolpai is your keyboard, never from password fields or sensitive copies, and is deleted on your schedule.")
            Bullet(UiR.drawable.ms_backup, "Typing data, clipboard pictures and stickers are left out of Android's cloud backup and device transfer.")
            Bullet(UiR.drawable.ms_gif_box, "The internet is used only for GIF search with your own GIPHY key, to download the offline translation pack when you ask, and for updates when you tap them (Check for updates asks GitHub for the latest version; Update downloads it from GitHub; nothing about you or your typing is sent). Translation itself runs on the phone. Voice typing uses your phone's speech service, which may work online.")
        }

        SettingsGroup("Permissions") {
            Bullet(UiR.drawable.ms_mic, "Microphone — only for voice typing, asked for on the Voice typing screen.")
            Bullet(UiR.drawable.ms_language, "Internet — only for GIF search with your own GIPHY key, downloading the offline translation pack, and checking for and downloading updates when you tap them.")
            Bullet(UiR.drawable.ms_vibration, "Vibration — for the key-press strengths you choose.")
            Bullet(UiR.drawable.ms_system_update, "Install apps — only to install Jolpai's own update when you tap Update; Android asks you to confirm every time.")
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

    if (showChanges) WhatsNewDialog(installed = version, onClose = { showChanges = false }) {
        openLink(context, JolpaiLinks.RELEASES)
    }
}

/** The app icon, name in English and Bangla, version and developer, on the home card's gradient. */
@Composable
private fun AboutHeader(version: String) {
    val gradient = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer),
    )
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.background(gradient).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The launcher icon's foreground on its cream background: the 108dp canvas, of which
            // a launcher shows the middle 72dp, clipped to a rounded tile.
            Box(
                Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFFF3EFD9)),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(114.dp))
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text("Jolpai Keyboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("জলপাই কিবোর্ড", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text("Version $version", style = MaterialTheme.typography.bodyMedium)
                Text("by ${JolpaiLinks.DEVELOPER_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Every version's changes, newest first; the installed version is marked. */
@Composable
private fun WhatsNewDialog(installed: String, onClose: () -> Unit, onAllReleases: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        icon = { MsIcon(UiR.drawable.ms_new_releases, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("What's new") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Changelog.RELEASES.forEach { r ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val heading = if (r.released) "Version ${r.version} · ${r.date}" else "${r.version} · in development"
                        val mark = if (r.released && AppUpdates.compareVersions(r.version, installed) == 0) "  (installed)" else ""
                        Text(heading + mark, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        r.changes.forEach { c ->
                            Row {
                                Text("•  ", style = MaterialTheme.typography.bodyMedium)
                                Text(c, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        dismissButton = { TextButton(onClick = onAllReleases) { Text("All releases") } },
    )
}

@Composable
private fun Bullet(icon: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        MsIcon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
