package com.yeamin.jolpai.ui

import android.content.Context
import com.yeamin.jolpai.core.settings.KeyboardSettings
import com.yeamin.jolpai.media.GifProvider
import com.yeamin.jolpai.media.GiphyProvider
import com.yeamin.jolpai.media.KlipyProvider
import java.util.Locale
import java.util.UUID

/**
 * Which GIF service the emoji panel uses: the user's own GIPHY key when one is set, otherwise
 * KLIPY, built in (when this build has a key; see keyboard-ui/build.gradle.kts). Nothing is
 * searched while GIF search is off in the settings.
 */
object GifSources {
    /** This build has KLIPY's key, so GIFs work without any setup. */
    val builtInAvailable: Boolean get() = BuildConfig.KLIPY_API_KEY.isNotBlank()

    /** The service in use, or null when GIF search is off or there is none. */
    fun provider(context: Context, settings: KeyboardSettings): GifProvider? = when {
        !settings.gifSearch -> null
        settings.giphyApiKey.isNotBlank() -> GiphyProvider(settings.giphyApiKey)
        builtInAvailable -> KlipyProvider(BuildConfig.KLIPY_API_KEY, customerId(context), country())
        else -> null
    }

    /** The service's name for attribution ("KLIPY", "GIPHY"), or null when GIFs are off. */
    fun name(settings: KeyboardSettings): String? = when {
        !settings.gifSearch -> null
        settings.giphyApiKey.isNotBlank() -> "GIPHY"
        builtInAvailable -> "KLIPY"
        else -> null
    }

    /**
     * A random id made once per install, which KLIPY asks for to tailor results and count shares.
     * It isn't tied to the user, the account or the device, and isn't in backups.
     */
    private fun customerId(context: Context): String {
        val prefs = context.getSharedPreferences("gifs", Context.MODE_PRIVATE)
        return prefs.getString("customer_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("customer_id", it).apply()
        }
    }

    /** The phone's country for local trending GIFs ("bd"), defaulting to Bangladesh. */
    private fun country(): String = Locale.getDefault().country.lowercase().ifBlank { "bd" }
}

/**
 * GIFs sent lately (newest first, up to 24), for the GIF tab's Recent chip. Only the GIF
 * service's links and names are kept, on this phone; the GIFs themselves aren't stored.
 */
class RecentGifs(context: Context) {
    private val prefs = context.getSharedPreferences("gifs", Context.MODE_PRIVATE)

    fun all(): List<com.yeamin.jolpai.media.Gif> =
        prefs.getString("recent", null).orEmpty().lines().mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 8) return@mapNotNull null
            com.yeamin.jolpai.media.Gif(
                id = f[0], title = f[1], previewUrl = f[2], gifUrl = f[3], slug = f[4],
                animatedUrl = f[5].ifEmpty { null }, width = f[6].toIntOrNull() ?: 0, height = f[7].toIntOrNull() ?: 0,
            )
        }

    fun add(gif: com.yeamin.jolpai.media.Gif) {
        fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
        val line = listOf(gif.id, gif.title, gif.previewUrl, gif.gifUrl, gif.slug, gif.animatedUrl.orEmpty(), "${gif.width}", "${gif.height}")
            .joinToString("\t") { clean(it) }
        val kept = (listOf(line) + prefs.getString("recent", null).orEmpty().lines().filter { it.isNotBlank() && it.substringBefore('\t') != gif.id })
            .take(24)
        prefs.edit().putString("recent", kept.joinToString("\n")).apply()
    }
}
