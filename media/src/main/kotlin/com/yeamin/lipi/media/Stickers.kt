package com.yeamin.lipi.media

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** A sticker: an image the user imported, stored privately. */
data class Sticker(val file: File, val mimeType: String)

/**
 * The user's own stickers: images picked in the Lipi app and copied into private storage.
 * Sent with `commitContent` to apps that accept images.
 */
class StickerStore(private val context: Context) {
    private val dir: File get() = File(context.filesDir, "stickers").apply { mkdirs() }

    private val _stickers = MutableStateFlow<List<Sticker>>(emptyList())
    val stickers: StateFlow<List<Sticker>> = _stickers.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _stickers.value = dir.listFiles().orEmpty()
            .filter { it.isFile }
            .sortedByDescending { it.lastModified() }
            .mapNotNull { f -> mimeFor(f)?.let { Sticker(f, it) } }
    }

    /** Copies an image from [uri]. Returns false for unsupported types or files over 2 MB. */
    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: return@withContext false
        if (mime !in SUPPORTED) return@withContext false
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: return@withContext false
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return@withContext false
        if (bytes.size > MAX_BYTES) return@withContext false
        File(dir, "${UUID.randomUUID()}.$ext").writeBytes(bytes)
        refresh()
        true
    }

    suspend fun delete(sticker: Sticker) = withContext(Dispatchers.IO) {
        sticker.file.delete()
        refresh()
    }

    fun contentUri(sticker: Sticker, authority: String): Uri = FileProvider.getUriForFile(context, authority, sticker.file)

    private fun mimeFor(f: File): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase())?.takeIf { it in SUPPORTED }

    companion object {
        val SUPPORTED = setOf("image/png", "image/webp", "image/jpeg", "image/gif")
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}
