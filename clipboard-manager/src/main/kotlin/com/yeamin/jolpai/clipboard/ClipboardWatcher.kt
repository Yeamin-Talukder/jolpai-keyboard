package com.yeamin.jolpai.clipboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.text.Html
import com.yeamin.jolpai.data.ClipRetention
import com.yeamin.jolpai.data.ClipCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Saves copies into the clipboard history while Jolpai is the active keyboard.
 *
 * Android 10+ only lets the focused app and the *current default keyboard* read the
 * clipboard, so this works only while Jolpai is selected; copies made while another keyboard
 * is active are not seen. The listener reacts to changes only and never polls.
 *
 * Nothing is saved when:
 * - clipboard history is off;
 * - the field being typed in is a password or private field;
 * - the copying app marked the clip sensitive (`EXTRA_IS_SENSITIVE`, used by password
 *   managers and banking apps on Android 13+; honoured on any version if present).
 */
class ClipboardWatcher(
    context: Context,
    private val repository: ClipboardRepository,
    private val scope: CoroutineScope,
    private val policy: () -> Policy,
) {
    data class Policy(
        val enabled: Boolean,
        val saveImages: Boolean,
        val retention: ClipRetention,
        val inSensitiveField: Boolean,
    )

    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { onChanged() }
    private var started = false

    fun start() {
        if (started) return
        clipboard?.addPrimaryClipChangedListener(listener)
        started = true
    }

    fun stop() {
        if (!started) return
        clipboard?.removePrimaryClipChangedListener(listener)
        started = false
    }

    private fun onChanged() {
        val p = policy()
        if (!p.enabled || p.inSensitiveField) return
        val clip = runCatching { clipboard?.primaryClip }.getOrNull() ?: return
        if (clip.itemCount == 0 || isSensitive(clip.description)) return
        val item = clip.getItemAt(0)
        val text = item.text?.toString()
            ?: item.htmlText?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT).toString().trim() }
        val uri = item.uri
        val imageType = (0 until clip.description.mimeTypeCount)
            .map { clip.description.getMimeType(it) }
            .firstOrNull { it.startsWith("image/") }
        scope.launch {
            when {
                !text.isNullOrBlank() -> repository.addText(text)
                uri != null && p.saveImages && imageType != null -> repository.addImage(uri, imageType)
            }
            repository.prune(p.retention)
            // A copied one-time code is removed again after a minute (unless pinned meanwhile).
            if (!text.isNullOrBlank() && ClipCategory.classify(text) == ClipCategory.OTP) {
                kotlinx.coroutines.delay(ClipboardRepository.CODE_LIFETIME_MS + 1_000)
                repository.expireCodes()
            }
        }
    }

    companion object {
        /** Same key as `ClipDescription.EXTRA_IS_SENSITIVE` (API 33), usable on every version. */
        private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

        fun isSensitive(description: ClipDescription): Boolean =
            description.extras?.getBoolean(EXTRA_IS_SENSITIVE, false) == true
    }
}
