package com.yeamin.jolpai.clipboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeamin.jolpai.data.ClipCategory
import com.yeamin.jolpai.data.ClipEntity
import com.yeamin.jolpai.data.SnippetCategory
import com.yeamin.jolpai.data.SnippetEntity
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

    /** Inserts a snippet, filling in variables such as {date} and {bangla_date}. */
    fun insertSnippet(text: String) = pasteText(text)

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

private enum class Tab(val label: String) { RECENT("Recent"), PINNED("Pinned"), SNIPPETS("Snippets") }

/**
 * Clipboard history, shown above the keys (pressing a key closes it and types).
 *
 * Layout: a search bar and a Recent · Pinned · Snippets switch whose highlight slides, a row of
 * filter chips (links, codes, phone numbers, folders …, Clear, Manage), and clip cards in two
 * columns that fit their text. Each card shows what kind of clip it is and when it was copied.
 * Tap to paste (the card sinks a little); long-press for pin, copy, folder, save as snippet and
 * delete.
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

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(top = 6.dp)) {
        // Search + Recent / Pinned / Snippets.
        Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
            Search(
                query = query,
                hint = if (tab == Tab.SNIPPETS) "Search snippets" else "Search clipboard",
                colors = colors,
                modifier = Modifier.weight(1f),
                onClick = { actions.startSearch() },
                onClear = { actions.clearSearch() },
            )
            Spacer(Modifier.width(8.dp))
            Segmented(tab, colors) { t ->
                tab = t
                folder = null
                category = null
                menuFor = null
            }
        }

        // Filters and tools.
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tab == Tab.SNIPPETS) {
                val present = SnippetCategory.entries.filter { k -> snippets.any { it.kind == k } }
                if (present.size > 1) {
                    Chip("All", snippetKind == null, colors) { snippetKind = null }
                    present.forEach { k -> Chip(k.label, snippetKind == k, colors) { snippetKind = if (snippetKind == k) null else k } }
                }
            } else {
                ClipCategory.entries.filter { it != ClipCategory.TEXT && it in kinds.values }.forEach { k ->
                    Chip("${glyph(k)} ${k.label}", category == k, colors) { category = if (category == k) null else k; folder = null }
                }
                folders.forEach { f -> Chip("📁 $f", folder == f, colors) { folder = if (folder == f) null else f; category = null } }
                if (clips.any { !it.pinned }) {
                    Chip(if (confirmClear) "Tap again to clear" else "Clear", confirmClear, colors, danger = true) {
                        if (confirmClear) {
                            scope.launch { repository.clearUnpinned() }
                            confirmClear = false
                            notice = "Cleared (pinned items kept)"
                        } else {
                            confirmClear = true
                        }
                    }
                }
            }
            Chip("Manage", false, colors) { actions.openManager() }
        }

        val status = notice ?: if (query.isNotBlank()) "Results for “$query”" else null
        if (status != null) {
            Text(status, color = colors.hint, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
            if (tab == Tab.SNIPPETS) {
                val matching = snippets.filter { s ->
                    (snippetKind == null || s.kind == snippetKind) && (
                        query.isBlank() || s.title.contains(query, true) || s.text.contains(query, true) ||
                            s.shortcut?.contains(query, true) == true || s.kind.label.contains(query, true)
                        )
                }
                SnippetList(matching, colors) { actions.insertSnippet(it.text) }
                return@Box
            }
            val shown = clips.filter { c ->
                (tab != Tab.PINNED || c.pinned) && (folder == null || c.folder == folder) &&
                    (category == null || kinds[c.id] == category) &&
                    (query.isBlank() || c.text?.contains(query, ignoreCase = true) == true || kinds[c.id]?.label?.contains(query, ignoreCase = true) == true)
            }
            if (shown.isEmpty()) {
                Empty(
                    "📋",
                    when {
                        !captureEnabled -> "Clipboard history is off. Turn it on in Jolpai's settings."
                        query.isNotBlank() -> "Nothing copied matches “$query”."
                        tab == Tab.PINNED -> "Pin what you paste often: long-press an item, then 📌 Pin."
                        else -> "Text and pictures you copy appear here while Jolpai is your keyboard."
                    },
                    colors,
                )
                return@Box
            }
            val now = remember(shown) { System.currentTimeMillis() }
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                verticalItemSpacing = 6.dp,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 6.dp),
            ) {
                items(shown, key = { it.id }) { clip ->
                    ClipCard(
                        clip = clip,
                        kind = kinds[clip.id],
                        now = now,
                        menuOpen = menuFor == clip.id,
                        folders = folders,
                        colors = colors,
                        onPaste = {
                            menuFor = null
                            if (clip.isImage) {
                                if (!actions.pasteImage(clip)) notice = "This app doesn't accept pasted pictures."
                            } else {
                                actions.pasteText(clip.text.orEmpty())
                            }
                            scope.launch { repository.markUsed(clip) }
                        },
                        onMenu = { menuFor = if (menuFor == clip.id) null else clip.id },
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
                }
            }
        }
    }
}

/** A clip: what kind it is and when it was copied, then the text (or picture). Sinks a little when pressed. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipCard(
    clip: ClipEntity,
    kind: ClipCategory?,
    now: Long,
    menuOpen: Boolean,
    folders: List<String>,
    colors: PanelColors,
    onPaste: () -> Unit,
    onMenu: () -> Unit,
    onPin: () -> Unit,
    onCopy: () -> Unit,
    onFolder: (String?) -> Unit,
    onSnippet: () -> Unit,
    onDelete: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "clip")
    val border by animateColorAsState(if (menuOpen) colors.accent else colors.hint.copy(alpha = 0.18f), label = "clipBorder")
    Column(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .combinedClickable(interactionSource = source, indication = ripple(), onClick = onPaste, onLongClick = onMenu)
            .heightIn(min = 60.dp)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Plain text needs no label; links, codes, phone numbers … say what they are.
            val k = kind ?: ClipCategory.TEXT
            Text(
                if (k == ClipCategory.TEXT) "" else "${glyph(k)} ${k.label.removeSuffix("s").removeSuffix(" & OTP")}",
                color = colors.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (clip.pinned) Text("📌", fontSize = 11.sp)
            Text(ago(now - clip.createdAt), color = colors.hint, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
        }
        Spacer(Modifier.height(4.dp))
        if (menuOpen) {
            ClipMenu(clip, folders, colors, onPin, onCopy, onFolder, onSnippet, onDelete)
        } else if (clip.isImage) {
            val bitmap by rememberThumbnail(clip.imagePath.orEmpty())
            bitmap?.let {
                Image(it, contentDescription = "Copied picture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(10.dp)))
            } ?: Text("🖼 Picture", color = colors.hint)
        } else {
            Text(clip.text.orEmpty().trim(), color = colors.text, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
        }
        clip.folder?.let { Text("📁 $it", color = colors.hint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
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
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Chip(if (clip.pinned) "Unpin" else "📌 Pin", false, colors, onClick = onPin)
            Chip("Copy", false, colors, onClick = onCopy)
            Chip("🗑", false, colors, danger = true, onClick = onDelete)
        }
        if (!clip.isImage) Chip("✎ Save as snippet", false, colors, onClick = onSnippet)
        if (folders.isNotEmpty() || clip.folder != null) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                folders.filter { it != clip.folder }.forEach { f -> Chip("→ $f", false, colors) { onFolder(f) } }
                if (clip.folder != null) Chip("No folder", false, colors) { onFolder(null) }
            }
        }
    }
}

@Composable
private fun SnippetList(snippets: List<SnippetEntity>, colors: PanelColors, onPick: (SnippetEntity) -> Unit) {
    if (snippets.isEmpty()) {
        Empty("✎", "No snippets yet. Add addresses, templates or replies in Jolpai's settings, or long-press a clipboard item → Save as snippet.", colors)
        return
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        verticalItemSpacing = 6.dp,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 6.dp),
    ) {
        items(snippets, key = { it.id }) { s ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.hint.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                    .clickable { onPick(s) }
                    .padding(10.dp),
            ) {
                Text(listOfNotNull(s.kind.label, s.shortcut).joinToString(" · "), color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(s.title, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                Text(s.text, color = colors.hint, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A search field look-alike: tapping it types the search with the keys. */
@Composable
private fun Search(query: String, hint: String, colors: PanelColors, modifier: Modifier, onClick: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(19.dp))
            .background(colors.surface)
            .border(1.dp, colors.hint.copy(alpha = 0.25f), RoundedCornerShape(19.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔍", fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            query.ifBlank { hint },
            color = if (query.isBlank()) colors.hint else colors.text,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (query.isNotBlank()) {
            Text("✕", color = colors.hint, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClear).padding(6.dp))
        }
    }
}

/** Recent · Pinned · Snippets, with the chosen one on an accent pill. */
@Composable
private fun Segmented(selected: Tab, colors: PanelColors, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(19.dp))
            .background(colors.surface)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val on = t == selected
            val bg by animateColorAsState(if (on) colors.accent else Color.Transparent, label = "seg")
            Box(
                Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .clickable { onSelect(t) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (t) {
                        Tab.RECENT -> "Recent"
                        Tab.PINNED -> "📌"
                        Tab.SNIPPETS -> "✎"
                    },
                    color = if (on) colors.onAccent else colors.text,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, colors: PanelColors, danger: Boolean = false, onClick: () -> Unit) {
    val bg by animateColorAsState(
        when {
            selected && danger -> Color(0xFFE5484D)
            selected -> colors.accent
            else -> Color.Transparent
        },
        label = "chip",
    )
    Text(
        label,
        color = when {
            selected -> colors.onAccent.takeIf { !danger } ?: Color.White
            danger -> Color(0xFFE5484D)
            else -> colors.text
        },
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else colors.hint.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    )
}

@Composable
private fun Empty(icon: String, message: String, colors: PanelColors) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 28.sp)
        Text(message, color = colors.hint, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun glyph(k: ClipCategory): String = when (k) {
    ClipCategory.IMAGE -> "🖼"
    ClipCategory.LINK -> "🔗"
    ClipCategory.EMAIL -> "✉"
    ClipCategory.OTP -> "🔢"
    ClipCategory.PHONE -> "📞"
    ClipCategory.ADDRESS -> "📍"
    ClipCategory.CODE -> "⌨"
    ClipCategory.TEXT -> "¶"
}

/** "now", "5m", "3h", "2d". */
private fun ago(ms: Long): String {
    val m = ms / 60_000
    return when {
        m < 1 -> "now"
        m < 60 -> "${m}m"
        m < 60 * 24 -> "${m / 60}h"
        else -> "${m / (60 * 24)}d"
    }
}

/** Decodes a small thumbnail off the main thread. */
@Composable
private fun rememberThumbnail(path: String) = produceState<ImageBitmap?>(null, path) {
    value = withContext(Dispatchers.IO) {
        runCatching { ClipImages.decode(path, 240)?.asImageBitmap() }.getOrNull() // stored encrypted
    }
}
