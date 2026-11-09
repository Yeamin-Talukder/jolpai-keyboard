package com.yeamin.jolpai.clipboard

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.yeamin.jolpai.data.ClipEntity
import com.yeamin.jolpai.data.ClipRetention
import com.yeamin.jolpai.data.ClipboardDao
import com.yeamin.jolpai.data.SnippetDao
import com.yeamin.jolpai.data.SnippetEntity
import com.yeamin.jolpai.data.SnippetCategory
import com.yeamin.jolpai.data.ClipCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Clipboard history and snippets, stored on the device.
 *
 * Images are copied into app-private storage when captured, because the app that copied an
 * image only lends its URI for a short time. They are shared back through this module's
 * FileProvider, one paste at a time.
 */
@Singleton
class ClipboardRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ClipboardDao,
    private val snippetDao: SnippetDao,
) {
    val clips: Flow<List<ClipEntity>> = dao.observeAll()
    val folders: Flow<List<String>> = dao.observeFolders()
    val snippets: Flow<List<SnippetEntity>> = snippetDao.observeAll()

    private val imageDir: File get() = File(context.filesDir, "clips").apply { mkdirs() }

    /** Saves copied text. Copying the same text again moves the existing entry to the top. */
    suspend fun addText(text: String, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        if (text.isBlank() || text.length > MAX_TEXT_CHARS) return@withContext
        val existing = dao.findByText(text)
        if (existing != null) {
            dao.touch(existing.id, now)
        } else {
            dao.insert(ClipEntity(text = text, imagePath = null, mimeType = "text/plain", createdAt = now, lastUsedAt = now))
        }
    }

    /** Copies an image from [uri] into private storage. Returns false if it can't be read or is too big. */
    suspend fun addImage(uri: Uri, mimeType: String, now: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "img"
        val file = File(imageDir, "${UUID.randomUUID()}.$ext")
        // Read into memory (pictures are at most 10 MB), then store encrypted.
        val copied = runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_IMAGE_BYTES) return@runCatching false
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            } ?: return@runCatching false
            ClipImages.write(file, bytes)
            true
        }.getOrDefault(false)
        if (!copied) {
            file.delete()
            return@withContext false
        }
        dao.insert(ClipEntity(text = null, imagePath = file.absolutePath, mimeType = mimeType, createdAt = now, lastUsedAt = now))
        true
    }

    /** Removes unpinned entries older than [retention], and keeps at most [MAX_UNPINNED] unpinned entries. */
    suspend fun prune(retention: ClipRetention, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        ClipImages.encryptLegacy(imageDir)
        expireCodes(now)
        File(context.cacheDir, SHARE_DIR).listFiles()?.forEach { if (it.lastModified() < now - SHARE_LIFETIME_MS) it.delete() }
        val expired = retention.millis?.let { dao.unpinnedOlderThan(now - it) }.orEmpty()
        val overflow = dao.unpinnedBeyond(MAX_UNPINNED)
        remove((expired + overflow).distinctBy { it.id })
    }

    /**
     * One-time codes (OTPs, 4–8 digit codes) are deleted [CODE_LIFETIME_MS] after they were
     * copied, unless pinned: they are useless afterwards and shouldn't linger.
     */
    suspend fun expireCodes(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        remove(dao.allUnpinned().filter { it.createdAt <= now - CODE_LIFETIME_MS && ClipCategory.of(it) == ClipCategory.OTP })
    }

    suspend fun delete(clip: ClipEntity) = withContext(Dispatchers.IO) { remove(listOf(clip)) }

    /** Deletes everything that isn't pinned. */
    suspend fun clearUnpinned() = withContext(Dispatchers.IO) { remove(dao.allUnpinned()) }

    suspend fun setPinned(clip: ClipEntity, pinned: Boolean) = withContext(Dispatchers.IO) { dao.setPinned(clip.id, pinned) }

    suspend fun setFolder(clip: ClipEntity, folder: String?) = withContext(Dispatchers.IO) {
        dao.setFolder(clip.id, folder?.trim()?.takeIf { it.isNotEmpty() })
    }

    suspend fun markUsed(clip: ClipEntity) = withContext(Dispatchers.IO) { dao.touch(clip.id, System.currentTimeMillis()) }

    /**
     * A content:// URI for a saved image, readable by the app it is pasted into. The stored
     * picture is encrypted, so a decrypted copy is made in the cache for this one paste; copies
     * older than a few minutes are deleted each time.
     */
    fun contentUri(clip: ClipEntity): Uri? {
        val path = clip.imagePath ?: return null
        val bytes = ClipImages.read(path) ?: return null
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - SHARE_LIFETIME_MS
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        val out = File(dir, "clip-${clip.id}." + File(path).extension)
        runCatching { out.writeBytes(bytes) }.getOrElse { return null }
        return FileProvider.getUriForFile(context, authority(context), out)
    }

    /** Saves a snippet; with no [category] one is suggested from the text. */
    suspend fun saveSnippet(title: String, shortcut: String?, text: String, id: Long = 0, category: SnippetCategory? = null) = withContext(Dispatchers.IO) {
        val s = SnippetEntity(
            id = id,
            title = title.trim().ifEmpty { text.take(30) },
            shortcut = shortcut?.trim()?.takeIf { it.isNotEmpty() },
            text = text,
            createdAt = System.currentTimeMillis(),
            category = (category ?: SnippetCategory.suggest(text)).storageKey,
        )
        if (id == 0L) snippetDao.insert(s) else snippetDao.update(s)
    }

    suspend fun deleteSnippet(snippet: SnippetEntity) = withContext(Dispatchers.IO) { snippetDao.delete(snippet.id) }

    /**
     * Adds ready-made snippets, skipping any whose text is already saved. A shortcut that another
     * snippet already uses is left off (the text is still added). Returns how many were added.
     */
    suspend fun addSnippets(items: List<PackSnippet>): Int = withContext(Dispatchers.IO) {
        packLock.withLock {
            val existing = snippetDao.all()
            val texts = existing.map { it.text.trim() }.toMutableSet()
            val shortcuts = existing.mapNotNull { it.shortcut }.toMutableSet()
            var added = 0
            val now = System.currentTimeMillis()
            items.forEach { p ->
                if (!texts.add(p.text.trim())) return@forEach
                val shortcut = p.shortcut.takeIf { it.isNotBlank() && shortcuts.add(it) }
                snippetDao.insert(SnippetEntity(title = p.title, shortcut = shortcut, text = p.text, createdAt = now, category = p.category.storageKey))
                added++
            }
            added
        }
    }

    /**
     * Adds every [SnippetPack] once per install (also for people updating from a version without
     * them). Called when the app and when the keyboard start; a flag keeps it to one time.
     */
    suspend fun addPacksOnce() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PACK_PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(PACK_VERSION_KEY, 0) >= PACK_VERSION) return@withContext
        addSnippets(SnippetPack.entries.flatMap { it.items })
        prefs.edit().putInt(PACK_VERSION_KEY, PACK_VERSION).apply()
    }

    private val packLock = kotlinx.coroutines.sync.Mutex()

    private suspend fun remove(clips: List<ClipEntity>) {
        if (clips.isEmpty()) return
        clips.forEach { c -> c.imagePath?.let { File(it).delete() } }
        dao.deleteAll(clips.map { it.id })
    }

    companion object {
        private const val PACK_PREFS = "snippet_packs"
        private const val PACK_VERSION_KEY = "added_version"
        /** Raise when packs gain items that existing installs should get too. */
        private const val PACK_VERSION = 1
        const val MAX_TEXT_CHARS = 50_000
        private const val SHARE_DIR = "clip-share"
        const val CODE_LIFETIME_MS = 60_000L
        private const val SHARE_LIFETIME_MS = 5 * 60 * 1000L
        const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
        const val MAX_UNPINNED = 100

        fun authority(context: Context) = "${context.packageName}.clips"
    }
}
