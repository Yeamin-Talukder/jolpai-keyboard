package com.yeamin.jolpai.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import com.yeamin.jolpai.theme.AppTint

/**
 * The main colour of an app, read from its icon, for Chameleon themes. Icons are drawn small
 * (48 × 48) and kept only as a colour in memory; nothing is saved or sent anywhere.
 *
 * An input method may look up the app it is typing into (Android's package visibility rules
 * make that app visible to the keyboard), so no extra permission is needed.
 */
object AppColours {
    private const val SIZE = 48

    /** Package → colour; [NONE] for an app with no clear colour. */
    private val cache = LruCache<String, Int>(48)
    private const val NONE = 0

    /** The colour if it has been worked out already; [known] tells apart "not yet" from "none". */
    fun cached(packageName: String): Int? = cache.get(packageName)?.takeIf { it != NONE }

    fun known(packageName: String): Boolean = cache.get(packageName) != null

    /**
     * Works out (and remembers) the colour of [packageName]'s icon; null for none. Takes a few
     * milliseconds the first time, so the keyboard calls it off the main thread.
     */
    fun colourNow(context: Context, packageName: String): Int? {
        cache.get(packageName)?.let { return it.takeIf { c -> c != NONE } }
        val colour = runCatching {
            val icon = context.packageManager.getApplicationIcon(packageName)
            val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, SIZE, SIZE)
            icon.draw(Canvas(bitmap))
            val pixels = IntArray(SIZE * SIZE)
            bitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            bitmap.recycle()
            AppTint.dominantColour(pixels)
        }.getOrNull()
        cache.put(packageName, colour ?: NONE)
        return colour
    }
}
