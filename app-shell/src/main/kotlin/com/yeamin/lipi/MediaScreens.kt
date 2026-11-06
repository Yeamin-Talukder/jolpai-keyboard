package com.yeamin.lipi

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
import com.yeamin.lipi.core.settings.KeyboardSettings
import com.yeamin.lipi.core.settings.VoiceLanguage
import com.yeamin.lipi.media.Sticker
import com.yeamin.lipi.voice.VoiceAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yeamin.lipi.ui.R as UiR

/** Voice typing: microphone permission, language, and what the phone supports offline. */
@Composable
fun VoiceScreen(vm: MainViewModel, settings: KeyboardSettings, availability: Map<String, VoiceAvailability>, onBack: () -> Unit) {
    var granted by rememberSaveable { mutableStateOf(vm.hasMicPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { vm.checkVoice() }

    SettingsScaffold("Voice typing", onBack) {
        SettingsGroup {
            SettingsItem(
                if (granted) "Microphone allowed" else "Allow microphone",
                if (granted) "Voice typing is ready" else "Needed to hear you",
                if (granted) UiR.drawable.ms_check_circle_fill else UiR.drawable.ms_mic,
                showChevron = !granted,
            ) { if (!granted) launcher.launch(Manifest.permission.RECORD_AUDIO) }
        }
        SettingsNote(
            "Jolpai records only while the voice panel is open and listening, and passes the sound to this " +
                "phone's speech service (usually Google's), which may process it online. Jolpai stores no audio.",
        )

        SettingsGroup("Language") {
            VoiceLanguage.entries.forEach { lang ->
                SettingsRadio(lang.label, selected = settings.voiceLanguage == lang) { vm.setVoiceLanguage(lang) }
            }
        }

        SettingsGroup("Offline on this phone") {
            listOf("bn-BD" to "বাংলা", "en-US" to "English").forEach { (tag, name) ->
                val a = availability[tag]
                val canDownload = a == VoiceAvailability.DOWNLOADABLE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                SettingsItem(
                    name,
                    a?.label ?: "Checking…",
                    UiR.drawable.ms_record_voice_over,
                    enabled = true,
                    showChevron = false,
                    trailing = if (canDownload) {
                        { FilledTonalButton(onClick = { vm.downloadOfflineVoice(tag) }) { Text("Download") } }
                    } else {
                        null
                    },
                ) { if (canDownload) vm.downloadOfflineVoice(tag) }
            }
        }
        SettingsNote("This is what the phone's speech service reports. Without offline support, voice typing needs the internet.")

        SettingsGroup("Spoken punctuation") {
            Column(Modifier.padding(GroupContentPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("English: comma, full stop, period, question mark, exclamation mark, colon, new line, new paragraph.", style = MaterialTheme.typography.bodyMedium)
                Text("বাংলা: কমা, দাঁড়ি, পূর্ণচ্ছেদ, প্রশ্নবোধক চিহ্ন, বিস্ময়সূচক চিহ্ন, কোলন, নতুন লাইন, নতুন অনুচ্ছেদ।", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Emoji skin tone and suggestions, GIF key, and the user's stickers. */
@Composable
fun MediaScreen(vm: MainViewModel, settings: KeyboardSettings, stickers: List<Sticker>, onBack: () -> Unit) {
    var key by rememberSaveable(settings.giphyApiKey) { mutableStateOf(settings.giphyApiKey) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var importNote by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) {
            vm.importStickers(uris) { failed ->
                importNote = if (failed == 0) "Added ${uris.size} sticker${if (uris.size == 1) "" else "s"}." else
                    "$failed picture${if (failed == 1) "" else "s"} couldn't be added (PNG, WebP, JPEG or GIF up to 2 MB)."
            }
        }
    }
    LaunchedEffect(Unit) { vm.refreshStickers() }

    SettingsScaffold("Emoji, GIFs & stickers", onBack) {
        SettingsGroup("Emoji skin tone") {
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
                "Emoji suggestions",
                "pizza → 🍕, হাসি → 😄",
                settings.emojiSuggestions,
                vm::setEmojiSuggestions,
                UiR.drawable.ms_mood,
            )
        }

        SettingsGroup("GIFs") {
            Column(Modifier.padding(GroupContentPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(UiR.drawable.ms_gif_box)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (settings.giphyApiKey.isBlank()) "GIF search is off" else "GIF search is on", style = MaterialTheme.typography.titleMedium)
                        Text("Uses your own free GIPHY key", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    label = { Text("GIPHY API key") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            MsIcon(if (showKey) UiR.drawable.ms_visibility_off else UiR.drawable.ms_visibility, if (showKey) "Hide key" else "Show key")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.setGiphyApiKey(key) }, enabled = key != settings.giphyApiKey) { Text("Save") }
                    if (settings.giphyApiKey.isNotBlank()) OutlinedButton(onClick = { key = ""; vm.setGiphyApiKey("") }) { Text("Remove") }
                }
            }
        }
        SettingsNote(
            "Google closed Tenor's public GIF API on 30 June 2026, so GIF search uses GIPHY. Create a free key at " +
                "developers.giphy.com. The key stays on this phone; only your GIF search words go to GIPHY.",
        )

        SettingsGroup("Stickers") {
            SettingsItem(
                "Add pictures",
                importNote ?: "Your own stickers, in the emoji panel",
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
                                bitmap?.let { Image(it.asImageBitmap(), "Sticker", contentScale = ContentScale.Fit, modifier = Modifier.size(68.dp)) }
                            }
                            IconButton(onClick = { vm.deleteSticker(s) }, modifier = Modifier.size(32.dp)) {
                                MsIcon(UiR.drawable.ms_close, "Remove sticker", Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
