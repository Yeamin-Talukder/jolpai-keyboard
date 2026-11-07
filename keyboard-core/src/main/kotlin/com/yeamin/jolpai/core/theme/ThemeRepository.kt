package com.yeamin.jolpai.core.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.yeamin.jolpai.data.CustomThemeEntity
import com.yeamin.jolpai.data.ThemeDao
import com.yeamin.jolpai.theme.BuiltInThemes
import com.yeamin.jolpai.theme.KeyboardTheme
import com.yeamin.jolpai.theme.ThemeCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Built-in and user-made themes, and the background images custom themes use. */
@Singleton
class ThemeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ThemeDao,
) {
    val customThemes: Flow<List<KeyboardTheme>> = dao.observeAll().map { list -> list.mapNotNull(::toTheme) }

    private fun toTheme(e: CustomThemeEntity): KeyboardTheme? =
        runCatching { ThemeCodec.decode(e.json, KeyboardTheme.CUSTOM_PREFIX + e.id).copy(backgroundImage = e.backgroundImage) }.getOrNull()

    /** Saves [theme] (new if it isn't a custom theme yet). Returns its id. */
    suspend fun save(theme: KeyboardTheme): String = withContext(Dispatchers.IO) {
        val t = theme.sanitized()
        val json = ThemeCodec.encode(t)
        val now = System.currentTimeMillis()
        val existing = t.id.takeIf { it.startsWith(KeyboardTheme.CUSTOM_PREFIX) }?.removePrefix(KeyboardTheme.CUSTOM_PREFIX)?.toLongOrNull()
        if (existing != null && dao.get(existing) != null) {
            dao.update(CustomThemeEntity(existing, json, t.backgroundImage, now))
            t.id
        } else {
            KeyboardTheme.CUSTOM_PREFIX + dao.insert(CustomThemeEntity(json = json, backgroundImage = t.backgroundImage, updatedAt = now))
        }
    }

    /** A copy of any theme as a new custom theme (the starting point for editing a built-in one). */
    suspend fun duplicate(theme: KeyboardTheme): String =
        save(theme.copy(id = "", name = (theme.name + " copy").take(40), dynamic = false))

    suspend fun delete(theme: KeyboardTheme) = withContext(Dispatchers.IO) {
        val id = theme.id.removePrefix(KeyboardTheme.CUSTOM_PREFIX).toLongOrNull() ?: return@withContext
        theme.backgroundImage?.let { File(it).delete() }
        dao.delete(id)
    }

    /** Imports a theme exported with [export]. Throws [IllegalArgumentException] with a readable message. */
    suspend fun import(json: String): String = save(ThemeCodec.decode(json, ""))

    fun export(theme: KeyboardTheme): String = ThemeCodec.encode(theme)

    /**
     * Copies a picked picture for use as a keyboard background, scaled to at most 1080 px wide
     * so it never costs much memory. Returns the private file path, or null if unreadable.
     */
    suspend fun importBackground(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1080) sample *= 2
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val dir = File(context.filesDir, "themes").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bitmap.recycle()
            file.absolutePath
        }.getOrNull()
    }

    companion object {
        /** The theme to draw for [id], given the custom themes and whether the phone is in dark mode. */
        fun resolve(id: String, customs: List<KeyboardTheme>, systemDark: Boolean): KeyboardTheme = when {
            id == KeyboardTheme.SYSTEM_ID -> BuiltInThemes.forSystem(systemDark)
            id.startsWith(KeyboardTheme.CUSTOM_PREFIX) -> customs.firstOrNull { it.id == id }
            else -> BuiltInThemes.byId(id)
        } ?: BuiltInThemes.DEFAULT
    }
}
