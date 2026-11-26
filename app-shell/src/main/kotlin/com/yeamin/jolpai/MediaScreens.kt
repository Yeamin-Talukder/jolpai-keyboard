package com.yeamin.jolpai

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.core.settings.VoiceLanguage
import com.yeamin.jolpai.media.Sticker
import com.yeamin.jolpai.voice.VoiceAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yeamin.jolpai.ui.R as UiR

/** Voice typing: microphone permission, language, and what the phone supports offline. */
@Composable
fun VoiceScreen(vm: MainViewModel, settings: KeyboardSettings, availability: Map<String, VoiceAvailability>, onBack: () -> Unit) {
    var granted by rememberSaveable { mutableStateOf(vm.hasMicPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { vm.checkVoice() }

    SettingsScaffold(tr("Voice typing", "ভয়েস টাইপিং"), onBack) {
        SettingsGroup {
            SettingsItem(
                if (granted) tr("Microphone allowed", "মাইক্রোফোনের অনুমতি আছে") else tr("Allow microphone", "মাইক্রোফোনের অনুমতি দিন"),
                if (granted) tr("Voice typing is ready", "এখন ভয়েস টাইপিং করা যাবে") else tr("Needed to hear you", "আপনার কথা শুনতে এটি লাগবে"),
                if (granted) UiR.drawable.ms_check_circle_fill else UiR.drawable.ms_mic,
                showChevron = !granted,
            ) { if (!granted) launcher.launch(Manifest.permission.RECORD_AUDIO) }
            SettingsChoice(
                tr("Language", "ভাষা"),
                tr("Which language voice typing listens for", "ভয়েস টাইপিং কোন ভাষা শুনবে"),
                VoiceLanguage.entries.map { it to it.uiLabel },
                settings.voiceLanguage,
                vm::setVoiceLanguage,
                UiR.drawable.ms_language,
            )
        }
        SettingsNote(
            tr(
                "Jolpai listens only while the voice panel is open and stores no audio. " +
                    "Your phone's speech service (usually Google's) may process it online.",
                "ভয়েস প্যানেল খোলা থাকলেই শুধু জলপাই শোনে, কোনো অডিও রাখে না। " +
                    "ফোনের স্পিচ সার্ভিস (সাধারণত Google-এর) তা অনলাইনে প্রসেস করতে পারে।",
            ),
        )

        SettingsGroup(tr("Offline on this phone", "এই ফোনে অফলাইন")) {
            listOf("bn-BD" to "বাংলা", "en-US" to tr("English", "ইংরেজি")).forEach { (tag, name) ->
                val a = availability[tag]
                val canDownload = a == VoiceAvailability.DOWNLOADABLE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                SettingsItem(
                    name,
                    a?.statusLabel ?: tr("Checking…", "দেখা হচ্ছে…"),
                    UiR.drawable.ms_record_voice_over,
                    enabled = true,
                    showChevron = false,
                    trailing = if (canDownload) {
                        { FilledTonalButton(onClick = { vm.downloadOfflineVoice(tag) }) { Text(tr("Download", "ডাউনলোড")) } }
                    } else {
                        null
                    },
                ) { if (canDownload) vm.downloadOfflineVoice(tag) }
            }
        }
        SettingsNote(tr("Without offline support, voice typing needs the internet.", "অফলাইন সুবিধা না থাকলে ভয়েস টাইপিংয়ে ইন্টারনেট লাগে।"))

        SettingsGroup(tr("Spoken punctuation", "মুখে বলা যতিচিহ্ন")) {
            Column(Modifier.padding(GroupContentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // The words themselves are what you say, so they stay in their own language.
                Text(
                    tr("English", "ইংরেজি") + ": comma, full stop, period, question mark, exclamation mark, colon, new line, new paragraph.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("বাংলা: কমা, দাঁড়ি, পূর্ণচ্ছেদ, প্রশ্নবোধক চিহ্ন, বিস্ময়সূচক চিহ্ন, কোলন, নতুন লাইন, নতুন অনুচ্ছেদ।", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** What the speech service says about a language, in the app's language (the module keeps English). */
private val VoiceAvailability.statusLabel: String
    get() = when (this) {
        VoiceAvailability.ON_DEVICE -> tr("Works offline on this phone", "এই ফোনে অফলাইনে চলে")
        VoiceAvailability.DOWNLOADABLE -> tr("Offline pack available; needs internet until downloaded", "অফলাইন প্যাক ডাউনলোড করা যায়; তার আগে ইন্টারনেট লাগে")
        VoiceAvailability.DOWNLOADING -> tr("Downloading offline pack; needs internet until done", "অফলাইন প্যাক ডাউনলোড হচ্ছে; শেষ না হওয়া পর্যন্ত ইন্টারনেট লাগে")
        VoiceAvailability.ONLINE -> tr("Needs an internet connection", "ইন্টারনেট সংযোগ লাগে")
        VoiceAvailability.UNSUPPORTED -> tr("Not supported on this phone", "এই ফোনে চলে না")
        VoiceAvailability.UNKNOWN -> tr("Can't check offline support here; uses the internet", "অফলাইন সুবিধা যাচাই করা যায় না; ইন্টারনেট ব্যবহার হয়")
    }

/** Emoji skin tone and suggestions, GIF key, and the user's stickers. */
@Composable
fun MediaScreen(vm: MainViewModel, settings: KeyboardSettings, stickers: List<Sticker>, onBack: () -> Unit) {
    var key by rememberSaveable(settings.giphyApiKey) { mutableStateOf(settings.giphyApiKey) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    // The last sticker import: how many pictures were picked (null: none yet) and how many failed.
    // Kept as numbers and worded when shown, so the note follows the app's language.
    var importPicked by rememberSaveable { mutableStateOf<Int?>(null) }
    var importFailed by rememberSaveable { mutableStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) {
            vm.importStickers(uris) { failed ->
                importFailed = failed
                importPicked = uris.size
            }
        }
    }
    LaunchedEffect(Unit) { vm.refreshStickers() }
    val importNote = importPicked?.let { picked ->
        val failed = importFailed
        if (failed == 0) {
            tr("Added $picked sticker${if (picked == 1) "" else "s"}.", "${num(picked)}টি স্টিকার যোগ হয়েছে।")
        } else {
            tr(
                "$failed picture${if (failed == 1) "" else "s"} couldn't be added (PNG, WebP, JPEG or GIF up to 2 MB).",
                "${num(failed)}টি ছবি যোগ করা যায়নি (PNG, WebP, JPEG বা GIF, ${num(2)} MB পর্যন্ত)।",
            )
        }
    }

    SettingsScaffold(tr("Emoji, GIFs & stickers", "ইমোজি, GIF ও স্টিকার"), onBack) {
        SettingsGroup(tr("Emoji skin tone", "ইমোজির গায়ের রং")) {
            Row(
                Modifier.fillMaxWidth().padding(GroupContentPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("👋", "👋🏻", "👋🏼", "👋🏽", "👋🏾", "👋🏿").forEachIndexed { tone, e ->
                    val selected = settings.emojiSkinTone == tone
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                            .selectable(selected, role = Role.RadioButton) { vm.setEmojiSkinTone(tone) },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 24.sp) }
                }
            }
            SettingsSwitch(
                tr("Emoji suggestions", "ইমোজি সাজেশন"),
                "pizza → 🍕, হাসি → 😄",
                settings.emojiSuggestions,
                vm::setEmojiSuggestions,
                UiR.drawable.ms_mood,
            )
        }

        val builtIn = com.yeamin.jolpai.ui.GifSources.builtInAvailable
        SettingsGroup(tr("GIFs", "GIF")) {
            SettingsSwitch(
                tr("GIF search", "GIF খোঁজা"),
                when {
                    !builtIn && settings.giphyApiKey.isBlank() ->
                        tr("Needs your own GIPHY key below in this build", "এই বিল্ডে নিচে নিজের GIPHY কী লাগবে")
                    settings.giphyApiKey.isNotBlank() -> tr("Trending and search, from GIPHY with your key", "ট্রেন্ডিং ও খোঁজা, আপনার কী দিয়ে GIPHY থেকে")
                    else -> tr("Trending and search, built in. Powered by KLIPY", "ট্রেন্ডিং ও খোঁজা, সেটআপ ছাড়াই। KLIPY-এর সৌজন্যে")
                },
                settings.gifSearch,
                vm::setGifSearch,
                UiR.drawable.ms_gif_box,
            )
        }
        SettingsNote(
            tr(
                "GIFs need the internet. Only your GIF search words go to the GIF service, never what you type in apps.",
                "GIF-এর জন্য ইন্টারনেট লাগে। শুধু GIF খোঁজার শব্দ GIF সার্ভিসে যায়, অ্যাপে যা লেখেন তা কখনো নয়।",
            ),
        )

        SettingsGroup(tr("Your own GIPHY key (optional)", "নিজের GIPHY কী (ঐচ্ছিক)")) {
            Column(Modifier.padding(GroupContentPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (builtIn) {
                        tr("Not needed. Add one only if you prefer GIPHY's GIFs.", "দরকার নেই। GIPHY-এর GIF চাইলে তবেই যোগ করুন।")
                    } else {
                        tr("Get a free key at developers.giphy.com.", "developers.giphy.com থেকে ফ্রি কী নিন।")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    label = { Text(tr("GIPHY API key", "GIPHY API কী")) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            MsIcon(
                                if (showKey) UiR.drawable.ms_visibility_off else UiR.drawable.ms_visibility,
                                if (showKey) tr("Hide key", "কী লুকান") else tr("Show key", "কী দেখান"),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.setGiphyApiKey(key) }, enabled = key != settings.giphyApiKey) { Text(tr("Save", "সেভ করুন")) }
                    if (settings.giphyApiKey.isNotBlank()) OutlinedButton(onClick = { key = ""; vm.setGiphyApiKey("") }) { Text(tr("Remove", "সরান")) }
                }
            }
        }

        SettingsGroup(tr("Stickers", "স্টিকার")) {
            SettingsItem(
                tr("Add pictures", "ছবি যোগ করুন"),
                importNote ?: tr("Your own stickers, in the emoji panel", "আপনার নিজের স্টিকার, ইমোজি প্যানেলে"),
                UiR.drawable.ms_add,
                showChevron = false,
            ) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            if (stickers.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(88.dp),
                    // Inside a scrolling screen the grid needs a fixed height: up to four rows, then it scrolls.
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .height(((stickers.size / 3 + 1) * 104).coerceAtMost(420).dp),
                ) {
                    items(stickers, key = { it.file.name }) { s ->
                        Box(Modifier.padding(6.dp), contentAlignment = Alignment.TopEnd) {
                            val bitmap by produceState<Bitmap?>(null, s.file) {
                                value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(s.file.absolutePath) }
                            }
                            Box(
                                Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                bitmap?.let { Image(it.asImageBitmap(), tr("Sticker", "স্টিকার"), contentScale = ContentScale.Fit, modifier = Modifier.size(68.dp)) }
                            }
                            IconButton(onClick = { vm.deleteSticker(s) }, modifier = Modifier.size(32.dp)) {
                                MsIcon(UiR.drawable.ms_close, tr("Remove sticker", "স্টিকার সরান"), Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
