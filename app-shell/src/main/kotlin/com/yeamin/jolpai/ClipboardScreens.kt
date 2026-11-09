package com.yeamin.jolpai

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yeamin.jolpai.data.ClipEntity
import com.yeamin.jolpai.clipboard.ClipImages
import com.yeamin.jolpai.clipboard.SnippetPack
import com.yeamin.jolpai.data.SnippetEntity
import com.yeamin.jolpai.data.ClipCategory
import com.yeamin.jolpai.data.SnippetCategory
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yeamin.jolpai.ui.R as UiR

/** Clipboard history: search, filter by pinned or folder, copy, pin, file, delete. */
@Composable
fun ClipboardScreen(
    clips: List<ClipEntity>,
    folders: List<String>,
    historyEnabled: Boolean,
    onCopy: (ClipEntity) -> Unit,
    onPin: (ClipEntity, Boolean) -> Unit,
    onFolder: (ClipEntity, String?) -> Unit,
    onDelete: (ClipEntity) -> Unit,
    onClearUnpinned: () -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var pinnedOnly by rememberSaveable { mutableStateOf(false) }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var folderDialogFor by remember { mutableStateOf<ClipEntity?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf<Long?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val kinds = remember(clips) { clips.associate { it.id to ClipCategory.of(it) } }

    SettingsScaffold(
        "Clipboard",
        onBack,
        scrollable = false,
        actions = {
            IconButton(onClick = { confirmClear = true }, enabled = clips.any { !it.pinned }) {
                MsIcon(UiR.drawable.ms_delete, "Clear unpinned")
            }
        },
    ) {
        if (!historyEnabled) {
            SettingsNote("Clipboard history is off. Turn it on in Clipboard & snippets to save new copies.")
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search clipboard") },
            leadingIcon = { MsIcon(UiR.drawable.ms_search, null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !pinnedOnly && folder == null && category == null, onClick = { pinnedOnly = false; folder = null; category = null }, label = { Text("All") })
            FilterChip(
                selected = pinnedOnly,
                onClick = { pinnedOnly = !pinnedOnly; folder = null },
                label = { Text("Pinned") },
                leadingIcon = { MsIcon(UiR.drawable.ms_push_pin, null, Modifier.size(18.dp)) },
            )
            ClipCategory.entries.filter { it != ClipCategory.TEXT && it in kinds.values }.forEach { k ->
                FilterChip(
                    selected = category == k.name,
                    onClick = { category = if (category == k.name) null else k.name; pinnedOnly = false; folder = null },
                    label = { Text(k.label) },
                    leadingIcon = { MsIcon(clipIcon(k), null, Modifier.size(18.dp)) },
                )
            }
            folders.forEach { f ->
                FilterChip(
                    selected = folder == f,
                    onClick = { folder = if (folder == f) null else f; pinnedOnly = false },
                    label = { Text(f) },
                    leadingIcon = { MsIcon(UiR.drawable.ms_folder, null, Modifier.size(18.dp)) },
                )
            }
        }
        val shown = clips.filter { c ->
            (!pinnedOnly || c.pinned) && (folder == null || c.folder == folder) &&
                (category == null || kinds[c.id]?.name == category) &&
                (query.isBlank() || c.text?.contains(query, ignoreCase = true) == true || kinds[c.id]?.label?.contains(query, ignoreCase = true) == true)
        }
        if (shown.isEmpty()) {
            SettingsNote(if (clips.isEmpty()) "Nothing copied yet. Copy text while Jolpai is your keyboard and it appears here." else "No matches.")
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { clip ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)) {
                        if (clip.isImage) {
                            val bitmap by thumbnail(clip.imagePath.orEmpty())
                            bitmap?.let {
                                Image(
                                    it, "Copied image", contentScale = ContentScale.Crop,
                                    modifier = Modifier.padding(end = 12.dp).fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)),
                                )
                            } ?: Text("Image", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(clip.text.orEmpty(), maxLines = 6, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 12.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            kinds[clip.id]?.let { k -> MsIcon(clipIcon(k), k.label, Modifier.size(16.dp).padding(end = 0.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp)) }
                            val meta = listOfNotNull(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(clip.lastUsedAt)),
                                clip.folder,
                            ).joinToString(" · ")
                            Text(meta, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick = { onCopy(clip); copied = clip.id }) {
                                MsIcon(if (copied == clip.id) UiR.drawable.ms_check else UiR.drawable.ms_content_copy, if (copied == clip.id) "Copied" else "Copy")
                            }
                            IconButton(onClick = { onPin(clip, !clip.pinned) }) {
                                MsIcon(
                                    if (clip.pinned) UiR.drawable.ms_push_pin_fill else UiR.drawable.ms_push_pin,
                                    if (clip.pinned) "Unpin" else "Pin",
                                    tint = if (clip.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { folderDialogFor = clip }) { MsIcon(UiR.drawable.ms_folder, "Folder") }
                            IconButton(onClick = { onDelete(clip) }) { MsIcon(UiR.drawable.ms_delete, "Delete") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    folderDialogFor?.let { clip ->
        var name by remember(clip.id) { mutableStateOf(clip.folder.orEmpty()) }
        AlertDialog(
            onDismissRequest = { folderDialogFor = null },
            icon = { MsIcon(UiR.drawable.ms_folder, null) },
            title = { Text("Put in folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Folder name") }, singleLine = true)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        folders.forEach { f -> FilterChip(selected = name == f, onClick = { name = f }, label = { Text(f) }) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onFolder(clip, name); folderDialogFor = null }) { Text("Save") } },
            dismissButton = {
                TextButton(onClick = { onFolder(clip, null); folderDialogFor = null }) { Text("No folder") }
            },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { MsIcon(UiR.drawable.ms_delete, null) },
            title = { Text("Clear clipboard history?") },
            text = { Text("Everything that isn't pinned will be deleted.") },
            confirmButton = { TextButton(onClick = { onClearUnpinned(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

/** A ready-made starting point for a new snippet. */
private class SnippetTemplate(val label: String, val category: SnippetCategory, val title: String, val text: String, val shortcut: String)

private val TEMPLATES = listOf(
    SnippetTemplate("Email signature", SnippetCategory.SIGNATURE, "Signature", "Best regards,\nYour Name\nyour.email@example.com\n+880 1XXX-XXXXXX", "@sig"),
    SnippetTemplate("Address", SnippetCategory.ADDRESS, "Home address", "House 00, Road 00, Area, Dhaka 1200, Bangladesh", "@addr"),
    SnippetTemplate("Phone number", SnippetCategory.PHONE, "My number", "+880 1XXX-XXXXXX", "@ph"),
    SnippetTemplate("Quick reply", SnippetCategory.REPLY, "Busy", "I'm busy right now, I'll call you back soon.", "@busy"),
    SnippetTemplate("Message", SnippetCategory.MESSAGE, "Message", "কেমন আছেন? অনেক দিন কথা হয় না।", ""),
    SnippetTemplate("Code", SnippetCategory.CODE, "Code snippet", "fun main() {\n    println(\"Hello\")\n}", ""),
)

/** Snippets: saved text with an optional shortcut that expands when followed by space. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnippetsScreen(
    snippets: List<SnippetEntity>,
    onSave: (title: String, shortcut: String, text: String, id: Long, category: SnippetCategory) -> Unit,
    onDelete: (SnippetEntity) -> Unit,
    onAddPack: (SnippetPack, (Int) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<SnippetEntity?>(null) }
    var creating by remember { mutableStateOf<SnippetTemplate?>(null) }
    var packShown by remember { mutableStateOf<SnippetPack?>(null) }
    var packNote by remember { mutableStateOf<String?>(null) }
    var blank by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    SettingsScaffold("Snippets", onBack, scrollable = false) {
        SettingsNote(
            "Messages, signatures, addresses, phone numbers, code and quick replies. Give one a shortcut " +
                "(like @addr): type it, then space, to insert it. They're also in the keyboard's clipboard panel.",
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { blank = true }) {
                MsIcon(UiR.drawable.ms_add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New")
            }
            TEMPLATES.forEach { t ->
                AssistChip(
                    onClick = { creating = t },
                    label = { Text(t.label) },
                    leadingIcon = { MsIcon(snippetIcon(t.category), null, Modifier.size(18.dp)) },
                )
            }
        }
        // Ready-made packs: everyday messages and Islamic greetings, in Bangla and English.
        Text("Ready-made packs", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SnippetPack.entries.forEach { p ->
                val icon = if (p == SnippetPack.ISLAMIC) snippetIcon(SnippetCategory.ISLAMIC) else snippetIcon(SnippetCategory.MESSAGE)
                AssistChip(
                    onClick = { packShown = p },
                    label = { Text("${p.label} · ${p.items.size}") },
                    leadingIcon = { MsIcon(icon, null, Modifier.size(18.dp)) },
                )
            }
        }
        packNote?.let { SettingsNote(it) }
        if (snippets.size > 4) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search snippets") },
                leadingIcon = { MsIcon(UiR.drawable.ms_search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val present = SnippetCategory.entries.filter { k -> snippets.any { it.kind == k } }
        if (present.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                present.forEach { k ->
                    FilterChip(
                        selected = filter == k.storageKey,
                        onClick = { filter = if (filter == k.storageKey) null else k.storageKey },
                        label = { Text(k.label) },
                        leadingIcon = { MsIcon(snippetIcon(k), null, Modifier.size(18.dp)) },
                    )
                }
            }
        }
        val shown = snippets.filter { s ->
            (filter == null || s.category == filter) && (
                query.isBlank() || s.title.contains(query, true) || s.text.contains(query, true) ||
                    s.shortcut?.contains(query, true) == true
                )
        }
        if (snippets.isEmpty()) SettingsNote("No snippets yet. Start from a template above.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { s ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
                        IconBadge(snippetIcon(s.kind))
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(s.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(
                                listOfNotNull(s.kind.label, s.shortcut).joinToString(" · "),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(s.text, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                        IconButton(onClick = { editing = s }) { MsIcon(UiR.drawable.ms_edit, "Edit ${s.title}") }
                        IconButton(onClick = { onDelete(s) }) { MsIcon(UiR.drawable.ms_delete, "Delete ${s.title}") }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    packShown?.let { pack ->
        val saved = snippets.map { it.text.trim() }.toSet()
        val missing = pack.items.count { it.text.trim() !in saved }
        AlertDialog(
            onDismissRequest = { packShown = null },
            icon = { MsIcon(if (pack == SnippetPack.ISLAMIC) snippetIcon(SnippetCategory.ISLAMIC) else snippetIcon(SnippetCategory.MESSAGE), null) },
            title = { Text(pack.label) },
            text = {
                Column {
                    Text(
                        if (missing == 0) "All of these are already in your snippets." else "Type a shortcut, then space, to insert its text.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(pack.items, key = { it.text }) { p ->
                            val have = p.text.trim() in saved
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(p.shortcut, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                if (have) MsIcon(UiR.drawable.ms_check, "Already added", Modifier.size(18.dp).padding(start = 4.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = missing > 0,
                    onClick = {
                        onAddPack(pack) { n -> packNote = if (n == 0) "${pack.label}: nothing new to add." else "Added $n from ${pack.label}." }
                        packShown = null
                    },
                ) { Text(if (missing == 0) "Added" else "Add $missing") }
            },
            dismissButton = { TextButton(onClick = { packShown = null }) { Text("Close") } },
        )
    }

    if (blank || creating != null || editing != null) {
        val original = editing
        val template = creating
        val key = original?.id ?: template?.label ?: "blank"
        var title by remember(key) { mutableStateOf(original?.title ?: template?.title.orEmpty()) }
        var shortcut by remember(key) { mutableStateOf(original?.shortcut ?: template?.shortcut.orEmpty()) }
        var text by remember(key) { mutableStateOf(original?.text ?: template?.text.orEmpty()) }
        var picked by remember(key) { mutableStateOf(original?.kind ?: template?.category) }
        val category = picked ?: SnippetCategory.suggest(text)
        val shortcutTaken = shortcut.isNotBlank() && snippets.any { it.shortcut == shortcut.trim() && it.id != original?.id }
        val shortcutInvalid = shortcut.any { it.isWhitespace() }
        fun close() {
            blank = false
            creating = null
            editing = null
        }
        AlertDialog(
            onDismissRequest = ::close,
            icon = { MsIcon(snippetIcon(category), null) },
            title = { Text(if (original == null) "New snippet" else "Edit snippet") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Text") },
                        modifier = Modifier.heightIn(min = 120.dp),
                    )
                    OutlinedTextField(
                        value = shortcut,
                        onValueChange = { shortcut = it },
                        label = { Text("Shortcut (optional)") },
                        singleLine = true,
                        isError = shortcutTaken || shortcutInvalid,
                        supportingText = {
                            when {
                                shortcutInvalid -> Text("No spaces in a shortcut")
                                shortcutTaken -> Text("Another snippet uses this shortcut")
                                else -> Text("Type it then space to insert the snippet")
                            }
                        },
                    )
                    Text("Category" + if (picked == null) " (suggested)" else "", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SnippetCategory.entries.forEach { k ->
                            FilterChip(selected = category == k, onClick = { picked = k }, label = { Text(k.label) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank() && !shortcutTaken && !shortcutInvalid,
                    onClick = {
                        onSave(title, shortcut, text, original?.id ?: 0L, category)
                        close()
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = ::close) { Text("Cancel") } },
        )
    }
}

@Composable
private fun thumbnail(path: String) = produceState<ImageBitmap?>(null, path) {
    value = withContext(Dispatchers.IO) {
        runCatching { ClipImages.decode(path, 480)?.asImageBitmap() }.getOrNull() // stored encrypted
    }
}

/** The Material Symbol for an automatic clipboard category. */
fun clipIcon(k: ClipCategory): Int = when (k) {
    ClipCategory.IMAGE -> UiR.drawable.ms_image
    ClipCategory.LINK -> UiR.drawable.ms_link
    ClipCategory.EMAIL -> UiR.drawable.ms_mail
    ClipCategory.OTP -> UiR.drawable.ms_password
    ClipCategory.PHONE -> UiR.drawable.ms_call
    ClipCategory.ADDRESS -> UiR.drawable.ms_home
    ClipCategory.CODE -> UiR.drawable.ms_code
    ClipCategory.TEXT -> UiR.drawable.ms_notes
}

/** The Material Symbol for a snippet category. */
fun snippetIcon(k: SnippetCategory): Int = when (k) {
    SnippetCategory.MESSAGE -> UiR.drawable.ms_chat
    SnippetCategory.ISLAMIC -> UiR.drawable.ms_dark_mode
    SnippetCategory.REPLY -> UiR.drawable.ms_quickreply
    SnippetCategory.SIGNATURE -> UiR.drawable.ms_signature
    SnippetCategory.ADDRESS -> UiR.drawable.ms_home
    SnippetCategory.PHONE -> UiR.drawable.ms_call
    SnippetCategory.EMAIL -> UiR.drawable.ms_mail
    SnippetCategory.CODE -> UiR.drawable.ms_code
    SnippetCategory.OTHER -> UiR.drawable.ms_short_text
}
