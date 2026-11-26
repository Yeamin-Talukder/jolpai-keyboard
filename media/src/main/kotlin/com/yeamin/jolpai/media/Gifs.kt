package com.yeamin.jolpai.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One GIF search result: a small still [previewUrl] for the grid and the [gifUrl] to send.
 * [slug] is the provider's own name for it (KLIPY counts shares by it).
 */
data class Gif(
    val id: String,
    val title: String,
    val previewUrl: String,
    val gifUrl: String,
    val slug: String = id,
    /** A small moving preview (animated WebP), when the service has one. */
    val animatedUrl: String? = null,
    /** The preview's size in pixels (0 when unknown), to show each GIF at its own shape. */
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * A GIF search service. Tenor's public API closed on 2026-06-30. Jolpai has KLIPY built in
 * ([KlipyProvider]); a user's own GIPHY key ([GiphyProvider]) is used instead when set.
 */
interface GifProvider {
    val name: String
    suspend fun trending(limit: Int = 24): List<Gif>
    suspend fun search(query: String, limit: Int = 24): List<Gif>

    /** Tells the service a GIF was sent (after a search for [query], or from trending when blank). */
    suspend fun shared(gif: Gif, query: String) {}
}

/**
 * GIPHY with the user's own API key (https://developers.giphy.com). Only the search words
 * are sent — never anything typed in a text field — and only when the GIF tab is used.
 */
class GiphyProvider(private val apiKey: String) : GifProvider {
    override val name = "GIPHY"

    override suspend fun trending(limit: Int): List<Gif> =
        fetch("https://api.giphy.com/v1/gifs/trending?api_key=${enc(apiKey)}&limit=$limit&rating=pg-13")

    override suspend fun search(query: String, limit: Int): List<Gif> =
        fetch("https://api.giphy.com/v1/gifs/search?api_key=${enc(apiKey)}&q=${enc(query)}&limit=$limit&rating=pg-13")

    private suspend fun fetch(url: String): List<Gif> = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        try {
            when (val code = conn.responseCode) {
                200 -> parse(conn.inputStream.use { it.readBytes().decodeToString() })
                401, 403 -> throw GifException("GIPHY didn't accept the API key. Check it in Jolpai's settings.")
                429 -> throw GifException("GIPHY's limit for this API key was reached. Try again later.")
                else -> throw GifException("GIPHY returned an error ($code).")
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        /** Parses a GIPHY search/trending response. */
        fun parse(json: String): List<Gif> {
            val data = Json.parseToJsonElement(json).jsonObject["data"]?.jsonArray ?: return emptyList()
            return data.mapNotNull { item ->
                val o = item.jsonObject
                val images = o["images"]?.jsonObject ?: return@mapNotNull null
                fun url(key: String): String? = (images[key] as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
                fun field(key: String, name: String): String? = (images[key] as? JsonObject)?.get(name)?.jsonPrimitive?.contentOrNull
                val preview = url("fixed_width_small_still") ?: url("fixed_width_still") ?: return@mapNotNull null
                val gif = url("fixed_width") ?: url("downsized") ?: return@mapNotNull null
                Gif(
                    id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    previewUrl = preview,
                    gifUrl = gif,
                    animatedUrl = field("fixed_width_small", "webp"),
                    width = field("fixed_width_small_still", "width")?.toIntOrNull() ?: 0,
                    height = field("fixed_width_small_still", "height")?.toIntOrNull() ?: 0,
                )
            }
        }
    }
}

class GifException(message: String) : Exception(message)

/**
 * Downloads GIF previews (kept in a small memory cache) and full GIFs (in the app's cache
 * folder, shared through the FileProvider when sent).
 */
class GifCache(private val context: Context) {
    private val previews = LruCache<String, Bitmap>(60)
    /** Moving previews' bytes (small animated WebPs, about 80 KB each), decoded per cell. */
    private val moving = LruCache<String, ByteArray>(40)
    private val dir: File get() = File(context.cacheDir, "gifs").apply { mkdirs() }

    suspend fun preview(gif: Gif): Bitmap? {
        previews.get(gif.id)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { download(gif.previewUrl).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull()
        }
        bitmap?.let { previews.put(gif.id, it) }
        return bitmap
    }

    /**
     * A moving preview for the grid (Android 9+, which decodes animated WebP), or null to keep
     * the still one.
     */
    suspend fun animation(gif: Gif): android.graphics.drawable.Drawable? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return null
        val url = gif.animatedUrl ?: return null
        val bytes = moving.get(gif.id) ?: withContext(Dispatchers.IO) { runCatching { download(url) }.getOrNull() }
            ?.also { moving.put(gif.id, it) } ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes)))
            }.getOrNull()
        }
    }

    /** Downloads the GIF and returns a content URI for it. */
    suspend fun file(gif: Gif, authority: String): Uri = withContext(Dispatchers.IO) {
        val file = File(dir, "${gif.id.filter { it.isLetterOrDigit() }}.gif")
        if (!file.exists()) file.writeBytes(download(gif.gifUrl))
        trim()
        FileProvider.getUriForFile(context, authority, file)
    }

    private fun download(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode != 200) throw GifException("Couldn't download the GIF (${conn.responseCode}).")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    /** Keeps the newest 30 sent GIFs. */
    private fun trim() {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(30).forEach { it.delete() }
    }
}

/**
 * KLIPY (https://docs.klipy.com), Jolpai's built-in GIF service: free, made for keyboards and
 * chat apps, and Tenor's successor for many of them. Requests go straight from the phone, as
 * KLIPY requires. Only the search words, the phone's country and [customerId] are sent:
 * [customerId] is a random id made once per install (never tied to the user or the device), which
 * KLIPY uses to tailor results and count shares.
 */
class KlipyProvider(
    private val appKey: String,
    private val customerId: String,
    private val locale: String,
) : GifProvider {
    override val name = "KLIPY"

    private val base get() = "https://api.klipy.com/api/v1/${enc(appKey)}/gifs"

    private fun common(limit: Int) =
        "per_page=${limit.coerceIn(8, 50)}&customer_id=${enc(customerId)}&locale=${enc(locale)}&content_filter=medium&format_filter=gif,jpg,webp"

    override suspend fun trending(limit: Int): List<Gif> = fetch("$base/trending?page=1&${common(limit)}")

    override suspend fun search(query: String, limit: Int): List<Gif> =
        fetch("$base/search?page=1&q=${enc(query)}&${common(limit)}")

    override suspend fun shared(gif: Gif, query: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL("$base/share/${enc(gif.slug)}").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.setRequestProperty("Content-Type", "application/json")
                val body = buildJsonObject {
                    put("customer_id", customerId)
                    put("q", query)
                }.toString()
                conn.outputStream.use { it.write(body.toByteArray()) }
                conn.responseCode
                conn.disconnect()
            }
        }
    }

    private suspend fun fetch(url: String): List<Gif> = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        try {
            when (val code = conn.responseCode) {
                200 -> parse(conn.inputStream.use { it.readBytes().decodeToString() })
                401, 403 -> throw GifException("GIF search isn't available right now.")
                429 -> throw GifException("Too many GIF searches right now. Try again in a little while.")
                else -> throw GifException("The GIF service returned an error ($code).")
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        /** GIFs bigger than this are sent in the smaller size (some chat apps refuse big files). */
        private const val MAX_SEND_BYTES = 2_500_000L

        /**
         * Parses a KLIPY search/trending response: `{result, data: {data: [ {id, slug, title, type,
         * file: {hd|md|sm|xs: {gif|jpg|webp|mp4: {url, width, height, size}}}} ]}}`. Ads (type "ad")
         * never appear (Jolpai's key has ads off) and are skipped if they ever do.
         */
        fun parse(json: String): List<Gif> {
            val root = Json.parseToJsonElement(json).jsonObject
            val items = (root["data"] as? JsonObject)?.get("data")?.jsonArray ?: return emptyList()
            return items.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                if (o["type"]?.jsonPrimitive?.contentOrNull == "ad") return@mapNotNull null
                val file = o["file"] as? JsonObject ?: return@mapNotNull null
                fun media(size: String, format: String): JsonObject? = (file[size] as? JsonObject)?.get(format) as? JsonObject
                fun url(m: JsonObject?): String? = m?.get("url")?.jsonPrimitive?.contentOrNull
                fun bytes(m: JsonObject?): Long = m?.get("size")?.jsonPrimitive?.longOrNull ?: 0L
                fun dim(m: JsonObject?, name: String): Int = m?.get(name)?.jsonPrimitive?.intOrNull ?: 0
                val preview = url(media("sm", "jpg")) ?: url(media("xs", "jpg")) ?: url(media("md", "jpg")) ?: return@mapNotNull null
                val md = media("md", "gif")
                val gif = (if (md != null && bytes(md) in 1..MAX_SEND_BYTES) url(md) else null)
                    ?: url(media("sm", "gif")) ?: url(md) ?: url(media("hd", "gif")) ?: return@mapNotNull null
                val slug = o["slug"]?.jsonPrimitive?.contentOrNull
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: slug ?: return@mapNotNull null
                Gif(
                    id = id,
                    title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    previewUrl = preview,
                    gifUrl = gif,
                    slug = slug ?: id,
                    animatedUrl = url(media("sm", "webp")) ?: url(media("xs", "webp")),
                    width = dim(media("sm", "jpg") ?: media("sm", "webp"), "width"),
                    height = dim(media("sm", "jpg") ?: media("sm", "webp"), "height"),
                )
            }
        }
    }
}
