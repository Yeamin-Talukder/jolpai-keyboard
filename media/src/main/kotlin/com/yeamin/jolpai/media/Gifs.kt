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

/** One GIF search result: a small still [previewUrl] for the grid and the [gifUrl] to send. */
data class Gif(val id: String, val title: String, val previewUrl: String, val gifUrl: String)

/** A GIF search service. Tenor's public API closed on 2026-06-30, so GIPHY is the provider for now. */
interface GifProvider {
    val name: String
    suspend fun trending(limit: Int = 24): List<Gif>
    suspend fun search(query: String, limit: Int = 24): List<Gif>
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
                val preview = url("fixed_width_small_still") ?: url("fixed_width_still") ?: return@mapNotNull null
                val gif = url("fixed_width") ?: url("downsized") ?: return@mapNotNull null
                Gif(
                    id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    previewUrl = preview,
                    gifUrl = gif,
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
    private val dir: File get() = File(context.cacheDir, "gifs").apply { mkdirs() }

    suspend fun preview(gif: Gif): Bitmap? {
        previews.get(gif.id)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { download(gif.previewUrl).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull()
        }
        bitmap?.let { previews.put(gif.id, it) }
        return bitmap
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
