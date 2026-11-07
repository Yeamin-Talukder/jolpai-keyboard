package com.yeamin.jolpai.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.proofreading.Proofreader
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import java.util.concurrent.Executor

/** Whether on-device AI proofreading can run on this phone. */
enum class AiStatus { UNAVAILABLE, DOWNLOADABLE, DOWNLOADING, AVAILABLE }

/**
 * Deeper English proofreading with Google's on-device AI (Gemini Nano through the phone's AICore
 * app, ML Kit GenAI Proofreading, beta). Only some newer phones have it; everywhere else
 * [status] reports UNAVAILABLE and the Check card simply doesn't offer it. The text is processed
 * on the phone. Callbacks arrive on the main thread.
 */
class AiProofreader(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val onMain = Executor { main.post(it) }
    private var client: Proofreader? = null

    private fun client(): Proofreader? = client ?: runCatching {
        val options = ProofreaderOptions.builder(appContext)
            .setInputType(ProofreaderOptions.InputType.KEYBOARD)
            .setLanguage(ProofreaderOptions.Language.ENGLISH)
            .build()
        Proofreading.getClient(options)
    }.getOrNull()?.also { client = it }

    fun status(result: (AiStatus) -> Unit) {
        val c = client() ?: return result(AiStatus.UNAVAILABLE)
        val future = runCatching { c.checkFeatureStatus() }.getOrNull() ?: return result(AiStatus.UNAVAILABLE)
        future.addListener({
            val s = runCatching { future.get() }.getOrNull()
            result(
                when (s) {
                    FeatureStatus.AVAILABLE -> AiStatus.AVAILABLE
                    FeatureStatus.DOWNLOADABLE -> AiStatus.DOWNLOADABLE
                    FeatureStatus.DOWNLOADING -> AiStatus.DOWNLOADING
                    else -> AiStatus.UNAVAILABLE
                },
            )
        }, onMain)
    }

    /** Downloads the AI model (through AICore); [done] gets true when it is ready. */
    fun download(done: (Boolean) -> Unit) {
        val c = client() ?: return done(false)
        runCatching {
            c.downloadFeature(object : DownloadCallback {
                override fun onDownloadStarted(bytesToDownload: Long) = Unit
                override fun onDownloadProgress(totalBytesDownloaded: Long) = Unit
                override fun onDownloadCompleted() { main.post { done(true) } }
                override fun onDownloadFailed(e: GenAiException) { main.post { done(false) } }
            })
        }.onFailure { done(false) }
    }

    /** The best rewrite of [text], or null (no suggestion, unavailable, or failed). */
    fun proofread(text: String, result: (String?) -> Unit) {
        val c = client() ?: return result(null)
        val future = runCatching { c.runInference(ProofreadingRequest.builder(text).build()) }.getOrNull() ?: return result(null)
        future.addListener({
            val best = runCatching { future.get().results.firstOrNull()?.text }.getOrNull()
            result(best?.takeIf { it.isNotBlank() })
        }, onMain)
    }

    fun close() {
        runCatching { client?.close() }
        client = null
    }

    companion object {
        /** The model takes under 256 tokens; about this many characters is safely below that. */
        const val MAX_CHARS = 800
    }
}
