package com.yeamin.jolpai.clipboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.yeamin.jolpai.data.DatabaseKey
import java.io.File

/**
 * Clipboard pictures are stored encrypted (AES-GCM with the Keystore key, see [DatabaseKey]):
 * an 8-byte marker, then the encrypted bytes. Files saved by older versions (plain) are still
 * read, and [encryptLegacy] converts them.
 */
object ClipImages {
    private val MAGIC = "LIPIENC1".toByteArray(Charsets.US_ASCII)

    fun write(file: File, plain: ByteArray) {
        val tmp = File(file.path + ".tmp")
        tmp.outputStream().use { it.write(MAGIC); it.write(DatabaseKey.encrypt(plain)) }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            error("could not save picture")
        }
    }

    /** The picture's bytes, or null if missing or unreadable. */
    fun read(path: String): ByteArray? = runCatching {
        val bytes = File(path).readBytes()
        if (isEncrypted(bytes)) DatabaseKey.decrypt(bytes.copyOfRange(MAGIC.size, bytes.size)) else bytes
    }.getOrNull()

    /** Decodes the picture scaled down to roughly [size] pixels on its shorter side. */
    fun decode(path: String, size: Int): Bitmap? {
        val bytes = read(path) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Encrypts pictures saved before encryption existed. */
    fun encryptLegacy(dir: File) {
        dir.listFiles()?.forEach { f ->
            if (!f.isFile || f.name.endsWith(".tmp")) return@forEach
            runCatching {
                val bytes = f.readBytes()
                if (!isEncrypted(bytes)) write(f, bytes)
            }
        }
    }

    private fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size && MAGIC.indices.all { bytes[it] == MAGIC[it] }
}
