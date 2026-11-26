package com.yeamin.jolpai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
    // The outcome, not its words: the note is worded while drawing, so it follows the app's language.
    var result by remember { mutableStateOf<BackupResult?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri, includeLearned) { result = it }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restoreBackup(uri) { result = it }
    }
    SettingsScaffold(tr("Backup & restore", "ব্যাকআপ ও রিস্টোর"), onBack) {
        SettingsNote(
            tr(
                "A file with your settings, dictionary, snippets and themes. It never holds clipboard history, " +
                    "pictures or your GIPHY key, and nothing is uploaded.",
                "আপনার সেটিংস, অভিধান, স্নিপেট ও থিম একটি ফাইলে থাকে। ক্লিপবোর্ডের ইতিহাস, ছবি বা আপনার GIPHY কী " +
                    "এতে কখনো থাকে না, আর কিছুই আপলোড হয় না।",
            ),
        )
        SettingsGroup {
            SettingsItem(
                tr("Save a backup", "ব্যাকআপ নিন"),
                tr("Choose where to save the file", "ফাইলটি কোথায় রাখবেন বেছে নিন"),
                UiR.drawable.ms_save,
                showChevron = false,
            ) {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                exporter.launch("jolpai-backup-$date.json")
            }
            SettingsSwitch(
                tr("Include learned words", "শেখা শব্দও রাখুন"),
                tr(
                    "Words learned from your typing. Keep the file private if you include them.",
                    "আপনার টাইপ থেকে শেখা শব্দ। এগুলো রাখলে ফাইলটি কাউকে দেবেন না।",
                ),
                includeLearned,
                { includeLearned = it },
                UiR.drawable.ms_auto_awesome,
            )
        }
        SettingsGroup {
            SettingsItem(
                tr("Restore from a file", "ফাইল থেকে রিস্টোর করুন"),
                tr("Replaces your settings; adds words, snippets and themes", "সেটিংস বদলে যাবে; শব্দ, স্নিপেট ও থিম যোগ হবে"),
                UiR.drawable.ms_settings_backup_restore,
                showChevron = false,
            ) { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        }
        result?.let { SettingsNote(backupMessage(it)) }
    }
}

/** A backup or restore outcome in the app's language. */
private fun backupMessage(result: BackupResult): String = when (result) {
    BackupResult.Saved -> tr("Backup saved.", "ব্যাকআপ সেভ হয়েছে।")
    is BackupResult.Restored -> with(result.summary) {
        if (learnedWords > 0) {
            tr(
                "Restored $settings settings, $personalWords dictionary words, $snippets snippets, $themes themes " +
                    "and $learnedWords learned words.",
                "রিস্টোর হয়েছে: ${num(settings)}টি সেটিং, অভিধানের ${num(personalWords)}টি শব্দ, ${num(snippets)}টি স্নিপেট, " +
                    "${num(themes)}টি থিম ও ${num(learnedWords)}টি শেখা শব্দ।",
            )
        } else {
            tr(
                "Restored $settings settings, $personalWords dictionary words, $snippets snippets and $themes themes.",
                "রিস্টোর হয়েছে: ${num(settings)}টি সেটিং, অভিধানের ${num(personalWords)}টি শব্দ, ${num(snippets)}টি স্নিপেট ও " +
                    "${num(themes)}টি থিম।",
            )
        }
    }
    BackupResult.SaveFailed -> tr("Couldn't save the backup.", "ব্যাকআপ সেভ করা গেল না।")
    BackupResult.NotABackup -> tr("That file isn't a Jolpai backup.", "এই ফাইলটি জলপাইয়ের ব্যাকআপ নয়।")
    BackupResult.RestoreFailed -> tr("Couldn't restore the backup.", "ব্যাকআপ রিস্টোর করা গেল না।")
}

private data class Notice(val title: String, val notice: String, val licence: String)

private val NOTICES = listOf(
    Notice("Avro Phonetic rules (OpenBangla rupantor) — MPL-2.0", "/avro/NOTICE.txt", "/avro/LICENSE-MPL-2.0.txt"),
    Notice("English word list (AOSP LatinIME) — Apache-2.0", "/en/NOTICE.txt", "/en/LICENSE-APACHE-2.0.txt"),
    Notice("Bangla word list and Avro search (OpenBangla upodesh) — MIT; suffixes and autocorrect (OpenBangla riti) — MPL-2.0", "/bn/NOTICE.txt", "/bn/LICENSE-MIT-upodesh.txt"),
    Notice("Emoji data (Unicode Emoji 17.0, CLDR 49) — Unicode License V3", "/emoji/NOTICE.txt", "/emoji/LICENSE-UNICODE-V3.txt"),
    Notice("Icons (Material Symbols by Google) — Apache-2.0", "/icons/NOTICE.txt", "/en/LICENSE-APACHE-2.0.txt"),
    Notice("Bangla word frequencies (FrequencyWords, OpenSubtitles) — CC BY-SA 4.0; code MIT", "/bn/NOTICE.txt", "/bn/LICENSE-MIT-FrequencyWords.txt"),
    Notice("Banglish spellings (BanglaTLit, Fahim et al. 2024) — MIT", "/bn/NOTICE.txt", "/bn/LICENSE-MIT-BanglaTLit.txt"),
    Notice("Bangla next-word model (trained for Jolpai on HPLT, YouTube and online comments, Common Voice, OpenSubtitles, Tatoeba, Wikipedia) — CC BY-SA 4.0", "/lm/NOTICE.txt", "/lm/CC-BY-SA-4.0.txt"),
    Notice("Banglish next-word model (trained for Jolpai on BanglaTLit, YouTube comments, BANTEN and other Mendeley comment sets) — CC BY 4.0; BanglaTLit MIT", "/lm/NOTICE.txt", "/bn/LICENSE-MIT-BanglaTLit.txt"),
    Notice("English next-word model (Destin Moss, youcoded.ai; FineWeb and Tatoeba) — ODC-By 1.0 and CC BY 2.0 FR", "/lm/NOTICE.txt", "/lm/ODC-By-1.0.txt"),
    Notice("Key fonts: Hind Siliguri, Tiro Bangla, Galada, Atma, Baloo Da 2, Mina, Poppins, Quicksand, Space Mono, Pacifico, Dancing Script, Lobster, Kaushan Script, Indie Flower, Righteous, Orbitron, Press Start 2P (Google Fonts) — SIL Open Font License 1.1", "/fonts/NOTICE.txt", "/fonts/OFL.txt"),
)

// Read through one of the app's own classes: NOTICES::class.java is a system List class whose
// class loader can't see the app's files, so every notice showed "(not found …)" on the phone.
private fun resourceText(path: String): String =
    Notice::class.java.getResourceAsStream(path)?.use { it.readBytes().decodeToString() } ?: "(not found: $path)"

/** Where an update check stands. */
/** Version, updates, developer, privacy summary, permissions, and the licences of everything Jolpai includes. */
@Composable
fun AboutScreen(onBack: () -> Unit, onReport: (() -> Unit)? = null) {
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

    SettingsScaffold(tr("About Jolpai", "জলপাই সম্পর্কে"), onBack) {
        AboutHeader(version)

        SettingsGroup(tr("Updates & feedback", "আপডেট ও মতামত"), tone = GroupTone.PRIMARY) {
            UpdateSection(update, version)
            SettingsItem(
                tr("What's new", "নতুন কী আছে"),
                tr("Changes in each version", "প্রতিটি ভার্সনে কী বদলেছে"),
                UiR.drawable.ms_new_releases,
            ) { showChanges = true }
            SettingsItem(
                tr("Report a problem", "সমস্যা জানান"),
                tr("Describe it and send it on GitHub", "কী হয়েছে লিখে GitHub-এ পাঠান"),
                UiR.drawable.ms_bug_report,
            ) {
                if (onReport != null) onReport() else openLink(context, AppUpdates.issueUrl(info) { Uri.encode(it) })
            }
            SettingsItem(
                tr("Share Jolpai", "জলপাই শেয়ার করুন"),
                tr("Send the download link to a friend", "বন্ধুকে ডাউনলোডের লিংক পাঠান"),
                UiR.drawable.ms_share,
            ) { shareJolpai(context) }
        }

        SettingsGroup(tr("Developer", "ডেভেলপার")) {
            SettingsItem(
                JolpaiLinks.DEVELOPER_NAME,
                tr("Made in Bangladesh 🇧🇩", "বাংলাদেশে তৈরি 🇧🇩"),
                UiR.drawable.ms_code,
                showChevron = false,
            ) {}
            SettingsItem("GitHub", JolpaiLinks.DEVELOPER_GITHUB.removePrefix("https://"), UiR.drawable.ms_open_in_new) {
                openLink(context, JolpaiLinks.DEVELOPER_GITHUB)
            }
            SettingsItem(
                tr("Source code", "সোর্স কোড"),
                tr("Open source (Apache-2.0) · ⭐ star it on GitHub", "ওপেন সোর্স (Apache-2.0) · GitHub-এ ⭐ দিন"),
                UiR.drawable.ms_link,
            ) {
                openLink(context, JolpaiLinks.REPO)
            }
        }

        SettingsGroup(tr("Privacy", "গোপনীয়তা")) {
            Bullet(
                UiR.drawable.ms_lock,
                tr(
                    "Everything you type stays on this phone. No account, no tracking, no ads.",
                    "আপনি যা টাইপ করেন, সব এই ফোনেই থাকে। কোনো অ্যাকাউন্ট নেই, ট্র্যাকিং নেই, বিজ্ঞাপন নেই।",
                ),
            )
            Bullet(
                UiR.drawable.ms_auto_awesome,
                tr(
                    "Nothing is learned in password or private (incognito) fields. You can turn learning off or clear it.",
                    "পাসওয়ার্ড বা প্রাইভেট (ইনকগনিটো) ঘরে কিছুই শেখে না। চাইলে শেখা বন্ধ করতে বা মুছে ফেলতে পারেন।",
                ),
            )
            Bullet(
                UiR.drawable.ms_content_paste,
                tr(
                    "Clipboard history never keeps passwords or sensitive copies, and is deleted on your schedule.",
                    "পাসওয়ার্ড বা গোপন কিছু কপি করলে ক্লিপবোর্ডের ইতিহাসে থাকে না; বাকিটা আপনার ঠিক করা সময়ে মুছে যায়।",
                ),
            )
            Bullet(
                UiR.drawable.ms_backup,
                tr(
                    "Typing data, copied pictures and stickers stay out of Android's cloud backup and phone transfer.",
                    "টাইপিংয়ের তথ্য, কপি করা ছবি ও স্টিকার Android-এর ক্লাউড ব্যাকআপ বা নতুন ফোনে ট্রান্সফারে যায় না।",
                ),
            )
            Bullet(
                UiR.drawable.ms_gif_box,
                tr(
                    "The internet is used only when you ask: GIF search, the translation pack and updates. Nothing about " +
                        "you or your typing is sent. Voice typing uses your phone's speech service, which may go online.",
                    "ইন্টারনেট লাগে শুধু আপনি চাইলে: GIF খোঁজা, অনুবাদের প্যাক আর আপডেটের জন্য। আপনার বা আপনার লেখার কোনো " +
                        "তথ্য পাঠানো হয় না। ভয়েস টাইপিং ফোনের নিজস্ব স্পিচ সার্ভিস দিয়ে হয়, যা অনলাইনে যেতে পারে।",
                ),
            )
        }

        SettingsGroup(tr("Permissions", "অনুমতি")) {
            Bullet(UiR.drawable.ms_mic, tr("Microphone — only for voice typing.", "মাইক্রোফোন — শুধু ভয়েস টাইপিংয়ের জন্য।"))
            Bullet(
                UiR.drawable.ms_language,
                tr("Internet — only for GIFs, the translation pack and updates.", "ইন্টারনেট — শুধু GIF, অনুবাদের প্যাক আর আপডেটের জন্য।"),
            )
            Bullet(UiR.drawable.ms_vibration, tr("Vibration — for key-press feedback.", "ভাইব্রেশন — কী চাপলে হালকা কাঁপার জন্য।"))
            Bullet(
                UiR.drawable.ms_system_update,
                tr(
                    "Install apps — only for Jolpai's own updates; Android asks you every time.",
                    "অ্যাপ ইনস্টল — শুধু জলপাইয়ের নিজের আপডেটের জন্য; প্রতিবার Android আপনার অনুমতি চায়।",
                ),
            )
        }

        SettingsGroup(tr("Open-source credits", "ওপেন-সোর্স কৃতজ্ঞতা")) {
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
                        MsIcon(
                            UiR.drawable.ms_expand_more,
                            if (open == n.title) tr("Hide", "লুকান") else tr("Show", "দেখুন"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (open == n.title) {
                        Text(resourceText(n.notice), fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 10.dp))
                        Text(resourceText(n.licence), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        SettingsNote(
            tr(
                "Built with Kotlin and Jetpack (Compose, Room, DataStore, Hilt) — Apache-2.0.",
                "Kotlin ও Jetpack (Compose, Room, DataStore, Hilt) দিয়ে তৈরি — Apache-2.0।",
            ),
        )
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
            // The app icon, alive: the animated mark on its olive tile.
            Box(
                Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)).background(LogoTile),
                contentAlignment = Alignment.Center,
            ) {
                JolpaiLogo(Modifier.size(52.dp))
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text("Jolpai Keyboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("জলপাই কীবোর্ড", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(tr("Version $version", "ভার্সন ${version.digits()}"), style = MaterialTheme.typography.bodyMedium)
                Text(
                    tr("by ${JolpaiLinks.DEVELOPER_NAME}", "তৈরি করেছেন ${JolpaiLinks.DEVELOPER_NAME}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        title = { Text(tr("What's new", "নতুন কী আছে")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Changelog.RELEASES.forEach { r ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // The changes themselves stay English, so the version and date keep Latin digits.
                        val heading = if (r.released) {
                            tr("Version ${r.version} · ${r.date}", "ভার্সন ${r.version} · ${r.date}")
                        } else {
                            tr("${r.version} · in development", "${r.version} · তৈরি হচ্ছে")
                        }
                        val mark = if (r.released && AppUpdates.compareVersions(r.version, installed) == 0) {
                            tr("  (installed)", "  (ইনস্টল করা আছে)")
                        } else {
                            ""
                        }
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
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Close", "বন্ধ করুন")) } },
        dismissButton = { TextButton(onClick = onAllReleases) { Text(tr("All releases", "সব রিলিজ")) } },
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
