package com.yeamin.lipi.ui

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeamin.lipi.clipboard.PanelColors
import com.yeamin.lipi.emoji.Emoji
import com.yeamin.lipi.emoji.EmojiData
import com.yeamin.lipi.emoji.EmojiGroup
import com.yeamin.lipi.emoji.Kaomoji
import com.yeamin.lipi.media.Gif
import com.yeamin.lipi.media.Sticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the emoji panel asks the keyboard service to do. */
interface EmojiPanelActions {
    fun insert(text: String)
    fun toggleFavorite(emoji: String)
    fun setSkinTone(tone: Int)
    fun startSearch()
    fun clearSearch()
    fun backspace()
    fun close()

    /** Sends an image; returns an error message, or null when sent. */
    fun sendSticker(sticker: Sticker): String?
    suspend fun loadGifs(query: String): Result<List<Gif>>
    suspend fun gifPreview(gif: Gif): Bitmap?
    suspend fun sendGif(gif: Gif): String?
    fun openSettings(screen: String)
}

/** Everything the emoji panel shows. */
class EmojiPanelData(
    val emoji: EmojiData?,
    val canDraw: (String) -> Boolean,
    val recents: List<String>,
    val favorites: List<String>,
    val stickers: List<Sticker>,
    val skinTone: Int,
    val gifsEnabled: Boolean,
    /** Open on this tab (a GIF or sticker shortcut), or null for the usual start. */
    val startTab: com.yeamin.lipi.core.gesture.EmojiTab? = null,
)

private sealed interface Tab {
    data object Recent : Tab
    data object Favorites : Tab
    data class Group(val group: EmojiGroup) : Tab
    data object Kaomoji : Tab
    data object Stickers : Tab
    data object Gifs : Tab
}

/**
 * Emoji (with skin tones, recents, favourites and search in English and Bangla), kaomoji,
 * the user's stickers, and GIFs. Long-press an emoji for skin tones and favourite.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmojiPanel(data: EmojiPanelData, query: String, colors: PanelColors, actions: EmojiPanelActions) {
    var tab by remember(data.startTab) {
        mutableStateOf<Tab>(
            when (data.startTab) {
                com.yeamin.lipi.core.gesture.EmojiTab.GIFS -> Tab.Gifs
                com.yeamin.lipi.core.gesture.EmojiTab.STICKERS -> Tab.Stickers
                null -> if (data.recents.isEmpty()) Tab.Group(EmojiGroup.SMILEYS) else Tab.Recent
            },
        )
    }
    var menuFor by remember { mutableStateOf<Emoji?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2500)
            notice = null
        }
    }
    val searching = query.isNotBlank()

    Column(Modifier.fillMaxSize().background(colors.background)) {
        // Tabs (scrolling), with backspace fixed at the end. The toolbar's emoji button returns to the keys.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            TabIcon("🔍", false, colors) { actions.startSearch() }
            TabIcon("🕘", tab == Tab.Recent && !searching, colors) { tab = Tab.Recent; actions.clearSearch() }
            TabIcon("⭐", tab == Tab.Favorites && !searching, colors) { tab = Tab.Favorites; actions.clearSearch() }
            EmojiGroup.entries.forEach { g -> TabIcon(g.icon, tab == Tab.Group(g) && !searching, colors) { tab = Tab.Group(g); actions.clearSearch() } }
            TabIcon("(^_^)", tab == Tab.Kaomoji, colors) { tab = Tab.Kaomoji; actions.clearSearch() }
            TabIcon("🖼", tab == Tab.Stickers, colors) { tab = Tab.Stickers; actions.clearSearch() }
                TabIcon("GIF", tab == Tab.Gifs, colors) { tab = Tab.Gifs }
            }
            TabIcon("⌫", false, colors) { actions.backspace() }
        }

        menuFor?.let { e -> EmojiMenu(e, data, colors, actions) { menuFor = null } }
        notice?.let { Text(it, color = colors.hint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp)) }
        if (searching && tab != Tab.Gifs) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Results for “$query”", color = colors.hint, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("✕", color = colors.text, modifier = Modifier.clickable { actions.clearSearch() }.padding(8.dp))
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                tab == Tab.Gifs -> GifGrid(data.gifsEnabled, query, colors, actions) { notice = it }
                tab == Tab.Stickers -> StickerGrid(data.stickers, colors, actions) { notice = it }
                tab == Tab.Kaomoji -> KaomojiList(colors, actions)
                else -> {
                    val list: List<String> = when {
                        searching -> data.emoji?.search(query).orEmpty().filter { data.canDraw(it.value) }.map { it.withTone(data.skinTone) }
                        tab == Tab.Recent -> data.recents
                        tab == Tab.Favorites -> data.favorites
                        tab is Tab.Group -> data.emoji?.group((tab as Tab.Group).group).orEmpty()
                            .filter { data.canDraw(it.value) }
                            .map { it.withTone(data.skinTone) }
                        else -> emptyList()
                    }
                    if (list.isEmpty()) {
                        Empty(
                            when {
                                data.emoji == null -> "Loading emoji…"
                                searching -> "No emoji found for “$query”."
                                tab == Tab.Favorites -> "Long-press an emoji and tap ☆ to add favourites."
                                else -> "Emoji you use appear here."
                            },
                            colors,
                        )
                    } else {
                        LazyVerticalGrid(columns = GridCells.Adaptive(44.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                            items(list) { e ->
                                Text(
                                    e,
                                    fontSize = 26.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .combinedClickable(
                                            onClick = { actions.insert(e) },
                                            onLongClick = { menuFor = data.emoji?.find(e) },
                                        )
                                        .padding(vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiMenu(e: Emoji, data: EmojiPanelData, colors: PanelColors, actions: EmojiPanelActions, dismiss: () -> Unit) {
    val current = e.withTone(data.skinTone)
    val isFavorite = current in data.favorites || e.value in data.favorites
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(colors.surface).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (e.hasTones) {
            (0..5).forEach { tone ->
                Text(
                    e.withTone(tone),
                    fontSize = 26.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (tone == data.skinTone) colors.accent else colors.surface)
                        .clickable {
                            actions.setSkinTone(tone)
                            actions.insert(e.withTone(tone))
                            dismiss()
                        }
                        .padding(4.dp),
                )
            }
        }
        Text(
            if (isFavorite) "★ Unfavourite" else "☆ Favourite",
            color = colors.text,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { actions.toggleFavorite(current); dismiss() }.padding(8.dp),
        )
        data.emoji?.banglaName(e)?.let { Text(it, color = colors.hint, fontSize = 12.sp) }
        Text("✕", color = colors.text, modifier = Modifier.clickable { dismiss() }.padding(8.dp))
    }
}

@Composable
private fun KaomojiList(colors: PanelColors, actions: EmojiPanelActions) {
    LazyVerticalGrid(columns = GridCells.Adaptive(96.dp), modifier = Modifier.fillMaxSize().padding(4.dp)) {
        Kaomoji.groups.forEach { (title, faces) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(title, color = colors.hint, fontSize = 12.sp, modifier = Modifier.padding(6.dp))
            }
            items(faces) { face ->
                Text(
                    face,
                    color = colors.text,
                    fontSize = 15.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(2.dp).clip(RoundedCornerShape(8.dp)).background(colors.surface)
                        .clickable { actions.insert(face) }.padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun StickerGrid(stickers: List<Sticker>, colors: PanelColors, actions: EmojiPanelActions, notify: (String) -> Unit) {
    if (stickers.isEmpty()) {
        Empty("No stickers yet. Add your own pictures as stickers in the Jolpai app (Emoji & GIFs).", colors) {
            actions.openSettings("stickers")
        }
        return
    }
    LazyVerticalGrid(columns = GridCells.Adaptive(76.dp), modifier = Modifier.fillMaxSize().padding(4.dp)) {
        items(stickers, key = { it.file.name }) { s ->
            val bitmap by produceState<Bitmap?>(null, s.file) {
                value = withContext(Dispatchers.IO) { thumbnail(s.file.absolutePath, 160) }
            }
            Box(
                Modifier.padding(3.dp).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(colors.surface)
                    .clickable { actions.sendSticker(s)?.let(notify) },
                contentAlignment = Alignment.Center,
            ) {
                bitmap?.let { Image(it.asImageBitmap(), "Sticker", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp)) }
            }
        }
    }
}

@Composable
private fun GifGrid(enabled: Boolean, query: String, colors: PanelColors, actions: EmojiPanelActions, notify: (String) -> Unit) {
    if (!enabled) {
        Empty(
            "GIF search needs a free GIPHY API key (Google closed Tenor's API in June 2026). " +
                "Add your key in the Jolpai app under Emoji & GIFs.",
            colors,
        ) { actions.openSettings("gifs") }
        return
    }
    var result by remember { mutableStateOf<Result<List<Gif>>?>(null) }
    LaunchedEffect(query) {
        result = null
        result = actions.loadGifs(query.trim())
    }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (query.isBlank()) "Trending" else "“$query”",
                color = colors.hint,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text("Powered by GIPHY", color = colors.hint, fontSize = 10.sp)
        }
        val r = result
        when {
            r == null -> Empty("Loading GIFs…", colors)
            r.isFailure -> Empty(r.exceptionOrNull()?.message ?: "Couldn't load GIFs. Check your internet connection.", colors)
            r.getOrThrow().isEmpty() -> Empty("No GIFs found.", colors)
            else -> LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(4.dp)) {
                items(r.getOrThrow(), key = { it.id }) { gif ->
                    val preview by produceState<Bitmap?>(null, gif.id) { value = actions.gifPreview(gif) }
                    Box(
                        Modifier.padding(2.dp).height(84.dp).clip(RoundedCornerShape(6.dp)).background(colors.surface)
                            .clickable {
                                notify("Sending GIF…")
                                scope.launch { actions.sendGif(gif)?.let(notify) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        preview?.let { Image(it.asImageBitmap(), gif.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                            ?: Text("GIF", color = colors.hint)
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(message: String, colors: PanelColors, action: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = colors.hint, fontSize = 14.sp, textAlign = TextAlign.Center)
        action?.let {
            Text(
                "Open Jolpai settings",
                color = colors.onAccent,
                modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(colors.accent)
                    .clickable(onClick = it).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun TabIcon(label: String, selected: Boolean, colors: PanelColors, onClick: () -> Unit) {
    Text(
        label,
        fontSize = if (label.length > 2) 13.sp else 20.sp,
        color = if (selected) colors.onAccent else colors.text,
        modifier = Modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accent else colors.background)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Decodes an image file scaled down to roughly [size] pixels. */
internal fun thumbnail(path: String, size: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()
