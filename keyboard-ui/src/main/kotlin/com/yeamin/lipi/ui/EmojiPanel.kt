package com.yeamin.lipi.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val icon: String
    val title: String

    data object Recent : Tab { override val icon = "🕘"; override val title = "Recent" }
    data object Favorites : Tab { override val icon = "⭐"; override val title = "Favourites" }
    data class Group(val group: EmojiGroup) : Tab {
        override val icon get() = group.icon
        override val title get() = group.title
    }
    data object Kaomoji : Tab { override val icon = "(^_^)"; override val title = "Kaomoji" }
    data object Stickers : Tab { override val icon = "🖼"; override val title = "Stickers" }
    data object Gifs : Tab { override val icon = "GIF"; override val title = "GIFs" }
}

/**
 * Emoji (with skin tones, recents, favourites and search in English and Bangla), kaomoji,
 * the user's stickers, and GIFs, shown above the keys (the keys stay usable: pressing one
 * closes the panel and types).
 *
 * Layout: a search bar with backspace on top, the emoji grid with the category's name, and a
 * category bar at the bottom whose highlight slides to the chosen tab. Emoji spring when
 * tapped; long-press opens a floating bar with skin tones and favourite.
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
    val tabs: List<Tab> = buildList {
        add(Tab.Recent)
        add(Tab.Favorites)
        EmojiGroup.entries.forEach { add(Tab.Group(it)) }
        add(Tab.Kaomoji)
        add(Tab.Stickers)
        add(Tab.Gifs)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(top = 6.dp)) {
        // Search bar + backspace.
        Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchPill(
                hint = if (tab == Tab.Gifs) "Search GIFs" else "Search emoji · ইমোজি খুঁজুন",
                query = query,
                colors = colors,
                modifier = Modifier.weight(1f),
                onClick = { actions.startSearch() },
                onClear = { actions.clearSearch() },
            )
            Spacer(Modifier.width(8.dp))
            RoundButton(R.drawable.ms_backspace, "Delete", colors) { actions.backspace() }
        }

        // Title of what is showing, or the notice.
        val title = when {
            notice != null -> notice!!
            searching && tab != Tab.Gifs -> "Results for “$query”"
            else -> tab.title
        }
        Text(
            title,
            color = colors.hint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
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
                                tab == Tab.Favorites -> "Long-press an emoji and tap ☆ to keep it here."
                                else -> "Emoji you use appear here."
                            },
                            colors,
                        )
                    } else {
                        val grid = rememberLazyGridState()
                        LaunchedEffect(tab, query) { grid.scrollToItem(0) }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(44.dp),
                            state = grid,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 4.dp),
                        ) {
                            items(list) { e ->
                                EmojiCell(e, onClick = { actions.insert(e) }, onLongClick = { menuFor = data.emoji?.find(e) })
                            }
                        }
                    }
                }
            }
            // Long-press: floating bar with skin tones and favourite.
            menuFor?.let { e ->
                // Pops in from the top of the grid.
                val shown = remember(e) { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(e) { shown.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) }
                Box(
                    Modifier.align(Alignment.TopCenter).graphicsLayer {
                        alpha = shown.value
                        translationY = (1f - shown.value) * -24f
                        scaleX = 0.9f + 0.1f * shown.value
                        scaleY = 0.9f + 0.1f * shown.value
                    },
                ) { EmojiMenu(e, data, colors, actions) { menuFor = null } }
            }
        }

        // Category bar.
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface.copy(alpha = colors.surface.alpha * 0.7f))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { t ->
                val selected = tab == t && !(searching && t != Tab.Gifs)
                CategoryTab(t.icon, selected, colors) {
                    tab = t
                    if (t != Tab.Gifs) actions.clearSearch()
                }
            }
        }
    }
}

/** One emoji: springs down a little while pressed. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiCell(e: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.78f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "emoji")
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(e, fontSize = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
    }
}

/** A tab in the category bar: the chosen one sits on an accent pill that fades in. */
@Composable
private fun CategoryTab(label: String, selected: Boolean, colors: PanelColors, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) colors.accent else Color.Transparent, label = "tab")
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, spring(dampingRatio = 0.5f), label = "tabScale")
    Box(
        Modifier
            .padding(horizontal = 1.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = if (label.length > 2) 10.dp else 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = if (label.length > 2) 12.sp else 18.sp,
            fontWeight = if (label.length > 2) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.onAccent else colors.text,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
}

/** A rounded search field look-alike: tapping it types the search with the keys. */
@Composable
internal fun SearchPill(hint: String, query: String, colors: PanelColors, modifier: Modifier, onClick: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(19.dp))
            .background(colors.surface)
            .border(1.dp, colors.hint.copy(alpha = 0.25f), RoundedCornerShape(19.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ms_search), null, tint = colors.hint, modifier = Modifier.size(18.dp))
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
            Box(Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onClear), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ms_close), "Clear search", tint = colors.hint, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
internal fun RoundButton(icon: Int, description: String, colors: PanelColors, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(colors.surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = colors.text, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EmojiMenu(e: Emoji, data: EmojiPanelData, colors: PanelColors, actions: EmojiPanelActions, dismiss: () -> Unit) {
    val current = e.withTone(data.skinTone)
    val isFavorite = current in data.favorites || e.value in data.favorites
    Row(
        Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.popupSurface())
            .border(1.dp, colors.hint.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (e.hasTones) {
            (0..5).forEach { tone ->
                Text(
                    e.withTone(tone),
                    fontSize = 26.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (tone == data.skinTone) colors.accent.copy(alpha = 0.35f) else Color.Transparent)
                        .clickable {
                            actions.setSkinTone(tone)
                            actions.insert(e.withTone(tone))
                            dismiss()
                        }
                        .padding(4.dp),
                )
            }
        } else {
            Text(current, fontSize = 26.sp, modifier = Modifier.padding(4.dp))
        }
        Text(
            if (isFavorite) "★" else "☆",
            color = colors.accent,
            fontSize = 22.sp,
            modifier = Modifier.clip(CircleShape).clickable { actions.toggleFavorite(current); dismiss() }.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        data.emoji?.banglaName(e)?.let { Text(it, color = colors.hint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
        Box(Modifier.size(30.dp).clip(CircleShape).clickable { dismiss() }, contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ms_close), "Close", tint = colors.hint, modifier = Modifier.size(16.dp))
        }
    }
}

/** An opaque surface for floating bars (theme keys can be see-through). */
private fun PanelColors.popupSurface(): Color = Color(
    red = surface.red * 0.9f + background.red * 0.1f,
    green = surface.green * 0.9f + background.green * 0.1f,
    blue = surface.blue * 0.9f + background.blue * 0.1f,
    alpha = 1f,
)

@Composable
private fun KaomojiList(colors: PanelColors, actions: EmojiPanelActions) {
    LazyVerticalGrid(columns = GridCells.Adaptive(96.dp), modifier = Modifier.fillMaxSize()) {
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
                    modifier = Modifier.padding(3.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface)
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
    LazyVerticalGrid(columns = GridCells.Adaptive(76.dp), modifier = Modifier.fillMaxSize()) {
        items(stickers, key = { it.file.name }) { s ->
            val bitmap by produceState<Bitmap?>(null, s.file) {
                value = withContext(Dispatchers.IO) { thumbnail(s.file.absolutePath, 160) }
            }
            Box(
                Modifier.padding(3.dp).aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(colors.surface)
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (query.isBlank()) "Trending" else "“$query”", color = colors.hint, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("Powered by GIPHY", color = colors.hint, fontSize = 10.sp)
        }
        val r = result
        when {
            r == null -> Empty("Loading GIFs…", colors)
            r.isFailure -> Empty(r.exceptionOrNull()?.message ?: "Couldn't load GIFs. Check your internet connection.", colors)
            r.getOrThrow().isEmpty() -> Empty("No GIFs found.", colors)
            else -> LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize()) {
                items(r.getOrThrow(), key = { it.id }) { gif ->
                    val preview by produceState<Bitmap?>(null, gif.id) { value = actions.gifPreview(gif) }
                    Box(
                        Modifier.padding(2.dp).height(84.dp).clip(RoundedCornerShape(10.dp)).background(colors.surface)
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
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = colors.hint, fontSize = 14.sp, textAlign = TextAlign.Center)
        action?.let {
            Text(
                "Open Jolpai settings",
                color = colors.onAccent,
                modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(16.dp)).background(colors.accent)
                    .clickable(onClick = it).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Decodes an image file scaled down to roughly [size] pixels. */
internal fun thumbnail(path: String, size: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()
