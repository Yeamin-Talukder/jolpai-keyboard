package com.yeamin.jolpai.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.yeamin.jolpai.core.fonts.FontFile
import com.yeamin.jolpai.core.fonts.KeyFontCheck
import com.yeamin.jolpai.core.fonts.checkKeyFont
import com.yeamin.jolpai.core.fonts.fontDisplayName
import java.io.File

/**
 * The font file the user picked for the keys (Size & layout → Key font → Your own font). It is
 * copied into the app's private storage, so the keyboard can read it later without asking for
 * the file again; it never leaves the phone and isn't in backups.
 */
object CustomKeyFont {
    fun file(context: Context): File = File(context.filesDir, "key-fonts/custom-key-font")

    /** What [import] did. [check] is [KeyFontCheck.Ok] when the font was saved; [name] is its display name. */
    data class Imported(val check: KeyFontCheck, val name: String)

    /**
     * Checks the picked file and, if it is a usable font, saves it in place of any earlier one.
     * Call off the main thread. A bad file leaves the current font as it was.
     */
    fun import(context: Context, uri: Uri): Imported {
        val name = displayName(context, uri)
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // Read one byte past the limit, so a too-big file is noticed without reading it all.
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                var total = 0
                while (total <= FontFile.MAX_BYTES) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                }
                out.toByteArray()
            }
        }.getOrNull() ?: return Imported(KeyFontCheck.NotAFont, name)

        val check = checkKeyFont(bytes)
        if (check !is KeyFontCheck.Ok) return Imported(check, name)

        val target = file(context)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "incoming")
        return runCatching {
            temp.writeBytes(bytes)
            // Android must be able to load it too, before it replaces the current font.
            KeyFonts.typefaceFrom(temp) ?: error("not loadable")
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            KeyFonts.forgetCustom()
            Imported(check, name)
        }.getOrElse {
            temp.delete()
            Imported(KeyFontCheck.NotAFont, name)
        }
    }

    fun remove(context: Context) {
        file(context).delete()
        KeyFonts.forgetCustom()
    }

    /** The file's name without its extension ("Kalpurush.ttf" → "Kalpurush"), at most 40 characters. */
    private fun displayName(context: Context, uri: Uri): String {
        val raw = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "Font"
        return fontDisplayName(raw)
    }
}

