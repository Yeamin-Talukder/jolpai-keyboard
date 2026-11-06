package com.yeamin.lipi.ui

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether offline Bangla translation can run yet. */
enum class TranslateStatus { CHECKING, NOT_DOWNLOADED, DOWNLOADING, READY, FAILED }

/**
 * English ⇄ Bangla translation on the phone, with Google's ML Kit on-device translator.
 *
 * The Bangla language pack (about 30 MB) is downloaded once, only when the user taps Download;
 * after that every translation runs offline and the typed text never leaves the phone.
 * Callbacks arrive on the main thread.
 */
class OfflineTranslator {
    private val _status = MutableStateFlow(TranslateStatus.CHECKING)
    val status: StateFlow<TranslateStatus> = _status.asStateFlow()

    private val banglaPack = TranslateRemoteModel.Builder(TranslateLanguage.BENGALI).build()
    private val models = RemoteModelManager.getInstance()
    private var toBangla: Translator? = null
    private var toEnglish: Translator? = null

    /** Checks whether the Bangla pack is on the phone. */
    fun refresh() {
        if (_status.value == TranslateStatus.DOWNLOADING) return
        models.isModelDownloaded(banglaPack)
            .addOnSuccessListener { downloaded -> _status.value = if (downloaded) TranslateStatus.READY else TranslateStatus.NOT_DOWNLOADED }
            .addOnFailureListener { _status.value = TranslateStatus.NOT_DOWNLOADED }
    }

    /** Downloads the Bangla pack (any network; the user asked for it). */
    fun download() {
        if (_status.value == TranslateStatus.DOWNLOADING || _status.value == TranslateStatus.READY) return
        _status.value = TranslateStatus.DOWNLOADING
        client(true).downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { _status.value = TranslateStatus.READY }
            .addOnFailureListener { _status.value = TranslateStatus.FAILED }
    }

    /** Translates [text]; [result] gets null if the pack is missing or translation failed. */
    fun translate(text: String, intoBangla: Boolean, result: (String?) -> Unit) {
        if (_status.value != TranslateStatus.READY || text.isBlank()) {
            result(null)
            return
        }
        client(intoBangla).translate(text)
            .addOnSuccessListener { result(it) }
            .addOnFailureListener { result(null) }
    }

    /** Removes the downloaded Bangla pack (from the app's settings). */
    fun delete(done: () -> Unit = {}) {
        close()
        models.deleteDownloadedModel(banglaPack)
            .addOnCompleteListener {
                _status.value = TranslateStatus.NOT_DOWNLOADED
                done()
            }
    }

    fun close() {
        toBangla?.close()
        toEnglish?.close()
        toBangla = null
        toEnglish = null
    }

    private fun client(intoBangla: Boolean): Translator =
        if (intoBangla) {
            toBangla ?: Translation.getClient(options(TranslateLanguage.ENGLISH, TranslateLanguage.BENGALI)).also { toBangla = it }
        } else {
            toEnglish ?: Translation.getClient(options(TranslateLanguage.BENGALI, TranslateLanguage.ENGLISH)).also { toEnglish = it }
        }

    private fun options(from: String, to: String) =
        TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build()

    companion object {
        /** Rough size of the Bangla pack, shown before downloading. */
        const val PACK_SIZE_MB = 30
    }
}
