package com.yeamin.lipi.clipboard

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.yeamin.lipi.data.ClipEntity
import com.yeamin.lipi.data.SnippetEntity
import com.yeamin.lipi.data.ClipCategory
import com.yeamin.lipi.data.SnippetCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Colours handed in by the keyboard so the panel matches its theme. */
data class PanelColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val hint: Color,
    val accent: Color,
    val onAccent: Color,
)

/** What the panel asks the keyboard service to do. */
interface ClipboardPanelActions {
    fun pasteText(text: String)

    /** Inserts an image clip; returns false if the app being typed in doesn't accept images. */
    fun pasteImage(clip: ClipEntity): Boolean

    /** Puts the clip back on the system clipboard. */
    fun copyToClipboard(clip: ClipEntity)

    fun openManager()
    fun close()

    /** Type a search with the keys; the result comes back as the panel's query. */
    fun startSearch()
    fun clearSearch()
}

private enum class Tab { RECENT, PINNED, SNIPPETS }

/**
 * Clipboard history inside the keyboard, in place of the keys.
 * Tap to paste; long-press for pin, copy, folder, save as snippet and delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardPanel(
    repository: ClipboardRepository,
    captureEnabled: Boolean,
    colors: PanelColors,
    actions: ClipboardPanelActions,
    query: String = "",
) {
    val clips by repository.clips.collectAsState(initial = emptyList())
    val folders by repository.folders.collectAsState(initial = emptyList())
    val snippets by repository.snippets.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(Tab.RECENT) }
    var folder by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf<ClipCategory?>(null) }
    var snippetKind by remember { mutableStateOf<SnippetCategory?>(null) }
    // Automatic categories, worked out once per list change.
    val kinds = remember(clips) { clips.associate { it.id to ClipCategory.of(it) } }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2500)
            notice = null
        }
    }
    LaunchedEffect(confirmClear) {
        if (confirmClear) {
            delay(3000)
            confirmClear = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Header: back to keys, tabs, folders, clear.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Chip("🔍", selected = false, colors = colors) { actions.startSearch() }
            Chip("Recent", tab == Tab.RECENT && folder == null && category == null, colors) { tab = Tab.RECENT; folder = null; category = null }
            Chip("📌 Pinned", tab == Tab.PINNED, colors) { tab = Tab.PINNED; folder = null; category = null }
            Chip("✎ Snippets", tab == Tab.SNIPPETS, colors) { tab = Tab.SNIPPETS; folder = null; category = null }
            // Automatic categories that have something in them (links, phone numbers, codes …).
            ClipCategory.entries.filter { it != ClipCategory.TEXT && it in kinds.values }.forEach { k ->
                Chip(k.label, tab == Tab.RECENT && category == k, colors) { tab = Tab.RECENT; folder = null; category = if (category == k) null else k }
            }
            folders.forEach { f -> Chip("📁 $f", tab == Tab.RECENT && folder == f, colors) { tab = Tab.RECENT; folder = f; category = null } }
            if (tab != Tab.SNIPPETS && clips.any { !it.pinned }) {
                Chip(if (confirmClear) "Tap again to clear" else "Clear", confirmClear, colors) {
                    if (confirmClear) {
                        scope.launch { repository.clearUnpinned() }
                        confirmClear = false
                    } else {
                        confirmClear = true
                    }
                }
            }
            Chip("Manage", false, colors) { actions.openManager() }
        }

        notice?.let {
            Text(it, color = colors.hint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
        }
        if (query.isNotBlank()) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Results for “$query”", color = colors.hint, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("✕", color = colors.text, modifier = Modifier.clickable { actions.clearSearch() }.padding(8.dp))
            }
        }

        if (tab == Tab.SNIPPETS) {
            val present = SnippetCategory.entries.filter { k -> snippets.any { it.kind == k } }
            if (present.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chip("All", snippetKind == null, colors) { snippetKind = null }
                    present.forEach { k -> Chip(k.label, snippetKind == k, colors) { snippetKind = if (snippetKind == k) null else k } }
                }
            }
            val matching = snippets.filter { s ->
                (snippetKind == null || s.kind == snippetKind) && (
                    query.isBlank() || s.title.contains(query, true) || s.text.contains(query, true) ||
                        s.shortcut?.contains(query, true) == true || s.kind.label.contains(query, true)
                    )
            }
            SnippetList(matching, colors) { actions.pasteText(it.text) }
            return@Column
        }

        val shown = clips.filter { c ->
            (tab != Tab.PINNED || c.pinned) && (folder == null || c.folder == folder) &&
                (category == null || kinds[c.id] == category) &&
                (query.isBlank() || c.text?.contains(query, ignoreCase = true) == true || kinds[c.id]?.label?.contains(query, ignoreCase = true) == true)
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        !captureEnabled -> "Clipboard history is off. Turn it on in Jolpai's settings."
                        tab == Tab.PINNED -> "Pin items you use often: long-press an item, then 📌."
                        else -> "Text and images you copy appear here while Jolpai is your keyboard."
                    },
                    color = colors.hint,
                    fontSize = 14.sp,
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(shown, key = { it.id }) { clip ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface)
                        .combinedClickable(
                            onClick = {
                                menuFor = null
                                if (clip.isImage) {
                                    if (!actions.pasteImage(clip)) notice = "This app doesn't accept pasted images."
                                } else {
                                    actions.pasteText(clip.text.orEmpty())
                                }
                                scope.launch { repository.markUsed(clip) }
                            },
                            onLongClick = { menuFor = if (menuFor == clip.id) null else clip.id },
                        )
                        .heightIn(min = 64.dp)
                        .padding(8.dp),
                ) {
                    if (menuFor == clip.id) {
                        ClipMenu(
                            clip = clip,
                            folders = folders,
                            colors = colors,
                            onPin = { scope.launch { repository.setPinned(clip, !clip.pinned) }; menuFor = null },
                            onCopy = { actions.copyToClipboard(clip); notice = "Copied"; menuFor = null },
                            onFolder = { f -> scope.launch { repository.setFolder(clip, f) }; menuFor = null },
                            onSnippet = {
                                clip.text?.let { t -> scope.launch { repository.saveSnippet(t.take(30), null, t) } }
                                notice = "Saved to snippets"
                                menuFor = null
                            },
                            onDelete = { scope.launch { repository.delete(clip) }; menuFor = null },
                        )
                    } else {
                        ClipPreview(clip, kinds[clip.id], colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipPreview(clip: ClipEntity, kind: ClipCategory?, colors: PanelColors) {
    Column {
        if (clip.isImage) {
            val bitmap by rememberThumbnail(clip.imagePath.orEmpty())
            bitmap?.let {
                Image(it, contentDescription = "Copied image", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(6.dp)))
            } ?: Text("🖼 Image", color = colors.hint)
        } else {
            Text(clip.text.orEmpty(), color = colors.text, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        val badges = listOfNotNull(
            if (clip.pinned) "📌" else null,
            kind?.takeIf { it != ClipCategory.TEXT && it != ClipCategory.IMAGE }?.label,
            clip.folder?.let { "📁 $it" },
        )
        if (badges.isNotEmpty()) Text(badges.joinToString("  "), color = colors.hint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ClipMenu(
    clip: ClipEntity,
    folders: List<String>,
    colors: PanelColors,
    onPin: () -> Unit,
    onCopy: () -> Unit,
    onFolder: (String?) -> Unit,
    onSnippet: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Chip(if (clip.pinned) "Unpin" else "📌 Pin", false, colors, onPin)
            Chip("Copy", false, colors, onCopy)
            Chip("🗑", false, colors, onDelete)
        }
        if (!clip.isImage) Chip("✎ Save as snippet", false, colors, onSnippet)
        if (folders.isNotEmpty() || clip.folder != null) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                folders.filter { it != clip.folder }.forEach { f -> Chip("→ $f", false, colors) { onFolder(f) } }
                if (clip.folder != null) Chip("No folder", false, colors) { onFolder(null) }
            }
        }
    }
}

@Composable
private fun SnippetList(snippets: List<SnippetEntity>, colors: PanelColors, onPick: (SnippetEntity) -> Unit) {
    if (snippets.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "No snippets yet. Add addresses, templates or replies in Jolpai's settings, or long-press a clipboard item → Save as snippet.",
                color = colors.hint,
                fontSize = 14.sp,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(snippets, key = { it.id }) { s ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .clickable { onPick(s) }
                    .heightIn(min = 64.dp)
                    .padding(8.dp),
            ) {
                Text(s.title, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.text, color = colors.hint, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(s.kind.label, s.shortcut).joinToString(" · "), color = colors.accent, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, colors: PanelColors, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) colors.onAccent else colors.text,
        fontSize = 13.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.accent else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Decodes a small thumbnail off the main thread. */
@Composable
private fun rememberThumbnail(path: String) = produceState<ImageBitmap?>(null, path) {
    value = withContext(Dispatchers.IO) {
        runCatching { ClipImages.decode(path, 240)?.asImageBitmap() }.getOrNull() // stored encrypted
    }
}
