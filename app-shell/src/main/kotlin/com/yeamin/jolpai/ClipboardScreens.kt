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
import androidx.compose.foundation.layout.PaddingValues
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
        tr("Clipboard", "ক্লিপবোর্ড"),
        onBack,
        scrollable = false,
        actions = {
            IconButton(onClick = { confirmClear = true }, enabled = clips.any { !it.pinned }) {
                MsIcon(UiR.drawable.ms_delete, tr("Clear unpinned", "পিন না করা সব মুছুন"))
            }
        },
    ) {
        if (!historyEnabled) {
            SettingsNote(
                tr(
                    "History is off, so new copies aren't saved. Turn it on in Clipboard & snippets.",
                    "কপির ইতিহাস বন্ধ, তাই নতুন কপি সেভ হচ্ছে না। “ক্লিপবোর্ড ও স্নিপেট” পাতায় চালু করুন।",
                ),
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(tr("Search clipboard", "ক্লিপবোর্ডে খুঁজুন")) },
            leadingIcon = { MsIcon(UiR.drawable.ms_search, null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !pinnedOnly && folder == null && category == null, onClick = { pinnedOnly = false; folder = null; category = null }, label = { Text(tr("All", "সব")) })
            FilterChip(
                selected = pinnedOnly,
                onClick = { pinnedOnly = !pinnedOnly; folder = null },
                label = { Text(tr("Pinned", "পিন করা")) },
                leadingIcon = { MsIcon(UiR.drawable.ms_push_pin, null, Modifier.size(18.dp)) },
            )
            ClipCategory.entries.filter { it != ClipCategory.TEXT && it in kinds.values }.forEach { k ->
                FilterChip(
                    selected = category == k.name,
                    onClick = { category = if (category == k.name) null else k.name; pinnedOnly = false; folder = null },
                    label = { Text(k.shownLabel) },
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
            SettingsNote(
                if (clips.isEmpty()) {
                    tr(
                        "Nothing here yet. Text you copy while using Jolpai shows up here.",
                        "এখনো কিছু নেই। জলপাই দিয়ে লেখার সময় যা কপি করবেন, এখানে দেখাবে।",
                    )
                } else {
                    tr("No matches.", "কিছু পাওয়া যায়নি।")
                },
            )
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = FabClearance),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.id }) { clip ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)) {
                        if (clip.isImage) {
                            val bitmap by thumbnail(clip.imagePath.orEmpty())
                            bitmap?.let {
                                Image(
                                    it, tr("Copied image", "কপি করা ছবি"), contentScale = ContentScale.Crop,
                                    modifier = Modifier.padding(end = 12.dp).fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)),
                                )
                            } ?: Text(tr("Image", "ছবি"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(clip.text.orEmpty(), maxLines = 6, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 12.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            kinds[clip.id]?.let { k -> MsIcon(clipIcon(k), k.shownLabel, Modifier.size(16.dp).padding(end = 0.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp)) }
                            val meta = listOfNotNull(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(clip.lastUsedAt)).digits(),
                                clip.folder,
                            ).joinToString(" · ")
                            Text(meta, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick = { onCopy(clip); copied = clip.id }) {
                                MsIcon(if (copied == clip.id) UiR.drawable.ms_check else UiR.drawable.ms_content_copy, if (copied == clip.id) tr("Copied", "কপি হয়েছে") else tr("Copy", "কপি"))
                            }
                            IconButton(onClick = { onPin(clip, !clip.pinned) }) {
                                MsIcon(
                                    if (clip.pinned) UiR.drawable.ms_push_pin_fill else UiR.drawable.ms_push_pin,
                                    if (clip.pinned) tr("Unpin", "পিন সরান") else tr("Pin", "পিন"),
                                    tint = if (clip.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { folderDialogFor = clip }) { MsIcon(UiR.drawable.ms_folder, tr("Folder", "ফোল্ডার")) }
                            IconButton(onClick = { onDelete(clip) }) { MsIcon(UiR.drawable.ms_delete, tr("Delete", "মুছুন")) }
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
            title = { Text(tr("Put in a folder", "ফোল্ডারে রাখুন")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr("Folder name", "ফোল্ডারের নাম")) }, singleLine = true)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        folders.forEach { f -> FilterChip(selected = name == f, onClick = { name = f }, label = { Text(f) }) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onFolder(clip, name); folderDialogFor = null }) { Text(tr("Save", "সেভ করুন")) } },
            dismissButton = {
                TextButton(onClick = { onFolder(clip, null); folderDialogFor = null }) { Text(tr("No folder", "ফোল্ডার ছাড়া রাখুন")) }
            },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { MsIcon(UiR.drawable.ms_delete, null) },
            title = { Text(tr("Clear clipboard history?", "কপির ইতিহাস মুছে ফেলবেন?")) },
            text = { Text(tr("Everything except pinned items will be deleted.", "পিন করা ছাড়া বাকি সব মুছে যাবে।")) },
            confirmButton = { TextButton(onClick = { onClearUnpinned(); confirmClear = false }) { Text(tr("Clear", "মুছে ফেলুন")) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr("Cancel", "বাতিল")) } },
        )
    }
}

/**
 * A ready-made starting point for a new snippet. The chip label and the starting name come in
 * both languages; the text is sample content the user replaces, so it is not translated.
 */
private class SnippetTemplate(
    val label: String,
    val labelBn: String,
    val category: SnippetCategory,
    val title: String,
    val titleBn: String,
    val text: String,
    val shortcut: String,
)

private val TEMPLATES = listOf(
    SnippetTemplate("Email signature", "ইমেইল সিগনেচার", SnippetCategory.SIGNATURE, "Signature", "সিগনেচার", "Best regards,\nYour Name\nyour.email@example.com\n+880 1XXX-XXXXXX", "@sig"),
    SnippetTemplate("Address", "ঠিকানা", SnippetCategory.ADDRESS, "Home address", "বাসার ঠিকানা", "House 00, Road 00, Area, Dhaka 1200, Bangladesh", "@addr"),
    SnippetTemplate("Phone number", "ফোন নম্বর", SnippetCategory.PHONE, "My number", "আমার নম্বর", "+880 1XXX-XXXXXX", "@ph"),
    SnippetTemplate("Quick reply", "দ্রুত উত্তর", SnippetCategory.REPLY, "Busy", "ব্যস্ত", "I'm busy right now, I'll call you back soon.", "@busy"),
    SnippetTemplate("Message", "মেসেজ", SnippetCategory.MESSAGE, "Message", "মেসেজ", "কেমন আছেন? অনেক দিন কথা হয় না।", ""),
    SnippetTemplate("Code", "কোড", SnippetCategory.CODE, "Code snippet", "কোড", "fun main() {\n    println(\"Hello\")\n}", ""),
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
    // The pack just added and how many snippets it added; the note is worded when shown.
    var packAdded by remember { mutableStateOf<Pair<SnippetPack, Int>?>(null) }
    var blank by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    SettingsScaffold(tr("Snippets", "স্নিপেট"), onBack, scrollable = false) {
        SettingsNote(
            tr(
                "Save text you type often. Type its shortcut (like @addr) and space, or pick it in the keyboard's clipboard.",
                "বারবার লেখা জিনিস সেভ করে রাখুন। শর্টকাট (যেমন @addr) লিখে স্পেস চাপুন, অথবা কীবোর্ডের ক্লিপবোর্ড থেকে বেছে নিন।",
            ),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { blank = true }) {
                MsIcon(UiR.drawable.ms_add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("New", "নতুন"))
            }
            TEMPLATES.forEach { t ->
                AssistChip(
                    onClick = { creating = t },
                    label = { Text(tr(t.label, t.labelBn)) },
                    leadingIcon = { MsIcon(snippetIcon(t.category), null, Modifier.size(18.dp)) },
                )
            }
        }
        // Ready-made packs: everyday messages and Islamic greetings, in Bangla and English.
        Text(tr("Ready-made packs", "রেডিমেড প্যাক"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SnippetPack.entries.forEach { p ->
                val icon = if (p == SnippetPack.ISLAMIC) snippetIcon(SnippetCategory.ISLAMIC) else snippetIcon(SnippetCategory.MESSAGE)
                AssistChip(
                    onClick = { packShown = p },
                    label = { Text("${p.shownLabel} · ${num(p.items.size)}") },
                    leadingIcon = { MsIcon(icon, null, Modifier.size(18.dp)) },
                )
            }
        }
        packAdded?.let { (p, n) ->
            SettingsNote(
                if (n == 0) {
                    tr("${p.shownLabel}: nothing new to add.", "${p.shownLabel}: নতুন কিছু যোগ করার নেই।")
                } else {
                    tr("Added $n from ${p.shownLabel}.", "${p.shownLabel} থেকে ${num(n)}টি যোগ হয়েছে।")
                },
            )
        }
        if (snippets.size > 4) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(tr("Search snippets", "স্নিপেট খুঁজুন")) },
                leadingIcon = { MsIcon(UiR.drawable.ms_search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val present = SnippetCategory.entries.filter { k -> snippets.any { it.kind == k } }
        if (present.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(tr("All", "সব")) })
                present.forEach { k ->
                    FilterChip(
                        selected = filter == k.storageKey,
                        onClick = { filter = if (filter == k.storageKey) null else k.storageKey },
                        label = { Text(k.shownLabel) },
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
        if (snippets.isEmpty()) {
            SettingsNote(tr("No snippets yet. Start from one of the buttons above.", "এখনো কোনো স্নিপেট নেই। ওপরের যেকোনো একটা দিয়ে শুরু করুন।"))
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = FabClearance),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.id }) { s ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
                        IconBadge(snippetIcon(s.kind))
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(s.title.ifBlank { tr("Untitled", "নামহীন") }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(
                                listOfNotNull(s.kind.shownLabel, s.shortcut).joinToString(" · "),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(s.text, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                        IconButton(onClick = { editing = s }) { MsIcon(UiR.drawable.ms_edit, tr("Edit ${s.title}", "${s.title} এডিট করুন")) }
                        IconButton(onClick = { onDelete(s) }) { MsIcon(UiR.drawable.ms_delete, tr("Delete ${s.title}", "${s.title} মুছুন")) }
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
            title = { Text(pack.shownLabel) },
            text = {
                Column {
                    Text(
                        if (missing == 0) {
                            tr("All of these are already in your snippets.", "এগুলোর সবই আপনার স্নিপেটে আছে।")
                        } else {
                            tr("Type a shortcut, then space, to insert its text.", "শর্টকাট লিখে স্পেস চাপলেই লেখাটি বসে যাবে।")
                        },
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
                                if (have) MsIcon(UiR.drawable.ms_check, tr("Already added", "আগেই যোগ করা"), Modifier.size(18.dp).padding(start = 4.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = missing > 0,
                    onClick = {
                        onAddPack(pack) { n -> packAdded = pack to n }
                        packShown = null
                    },
                ) { Text(if (missing == 0) tr("Added", "যোগ হয়েছে") else tr("Add $missing", "${num(missing)}টি যোগ করুন")) }
            },
            dismissButton = { TextButton(onClick = { packShown = null }) { Text(tr("Close", "বন্ধ করুন")) } },
        )
    }

    if (blank || creating != null || editing != null) {
        val original = editing
        val template = creating
        val key = original?.id ?: template?.label ?: "blank"
        // The template's name is only the field's starting text; once shown it is the user's to edit.
        var title by remember(key) { mutableStateOf(original?.title ?: template?.let { tr(it.title, it.titleBn) }.orEmpty()) }
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
            title = { Text(if (original == null) tr("New snippet", "নতুন স্নিপেট") else tr("Edit snippet", "স্নিপেট এডিট")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(tr("Name", "নাম")) }, singleLine = true)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(tr("Text", "লেখা")) },
                        modifier = Modifier.heightIn(min = 120.dp),
                    )
                    OutlinedTextField(
                        value = shortcut,
                        onValueChange = { shortcut = it },
                        label = { Text(tr("Shortcut (optional)", "শর্টকাট (না দিলেও চলে)")) },
                        singleLine = true,
                        isError = shortcutTaken || shortcutInvalid,
                        supportingText = {
                            when {
                                shortcutInvalid -> Text(tr("No spaces in a shortcut", "শর্টকাটে স্পেস দেওয়া যাবে না"))
                                shortcutTaken -> Text(tr("Another snippet uses this shortcut", "এই শর্টকাট আরেকটি স্নিপেটে আছে"))
                                else -> Text(tr("Type it, then space, to insert", "লিখে স্পেস চাপলেই বসে যাবে"))
                            }
                        },
                    )
                    Text(
                        if (picked == null) tr("Category (suggested)", "ধরন (প্রস্তাবিত)") else tr("Category", "ধরন"),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SnippetCategory.entries.forEach { k ->
                            FilterChip(selected = category == k, onClick = { picked = k }, label = { Text(k.shownLabel) })
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
                ) { Text(tr("Save", "সেভ করুন")) }
            },
            dismissButton = { TextButton(onClick = ::close) { Text(tr("Cancel", "বাতিল")) } },
        )
    }
}

@Composable
private fun thumbnail(path: String) = produceState<ImageBitmap?>(null, path) {
    value = withContext(Dispatchers.IO) {
        runCatching { ClipImages.decode(path, 480)?.asImageBitmap() }.getOrNull() // stored encrypted
    }
}

/*
 * Names for the clipboard and snippet enums of the shared modules (core-data, clipboard-manager),
 * which keep their English labels. Search still matches the English label, as before.
 */

private val ClipCategory.shownLabel: String
    get() = when (this) {
        ClipCategory.IMAGE -> tr("Images", "ছবি")
        ClipCategory.LINK -> tr("Links", "লিংক")
        ClipCategory.EMAIL -> tr("Emails", "ইমেইল")
        ClipCategory.OTP -> tr("Codes & OTPs", "OTP ও কোড")
        ClipCategory.PHONE -> tr("Phone numbers", "ফোন নম্বর")
        ClipCategory.ADDRESS -> tr("Addresses", "ঠিকানা")
        ClipCategory.CODE -> tr("Code", "প্রোগ্রামিং কোড")
        ClipCategory.TEXT -> tr("Text", "লেখা")
    }

private val SnippetCategory.shownLabel: String
    get() = when (this) {
        SnippetCategory.MESSAGE -> tr("Messages", "মেসেজ")
        SnippetCategory.ISLAMIC -> tr("Islamic", "ইসলামিক")
        SnippetCategory.REPLY -> tr("Quick replies", "দ্রুত উত্তর")
        SnippetCategory.SIGNATURE -> tr("Signatures", "সিগনেচার")
        SnippetCategory.ADDRESS -> tr("Addresses", "ঠিকানা")
        SnippetCategory.PHONE -> tr("Phone numbers", "ফোন নম্বর")
        SnippetCategory.EMAIL -> tr("Emails", "ইমেইল")
        SnippetCategory.CODE -> tr("Code", "কোড")
        SnippetCategory.OTHER -> tr("Other", "অন্যান্য")
    }

private val SnippetPack.shownLabel: String
    get() = when (this) {
        SnippetPack.ISLAMIC -> tr("Islamic greetings", "ইসলামিক শুভেচ্ছা")
        SnippetPack.EVERYDAY -> tr("Everyday messages", "প্রতিদিনের মেসেজ")
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
