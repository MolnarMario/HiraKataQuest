package com.hive.hiragana.data.recognition

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.hive.hiragana.domain.repository.HandwritingRecognizer
import com.hive.hiragana.domain.repository.RecognizerStrokePoint
import com.hive.hiragana.domain.scoring.RecognitionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "HiraganaRecognizer"
private const val JAPANESE_TAG = "ja"

/**
 * [HandwritingRecognizer] backed by ML Kit Digital Ink Recognition.
 *
 * The Japanese model is downloaded on demand (a few MB, offline once
 * cached). Until it's ready — or if anything fails — [recognize] returns
 * [RecognitionResult.UNAVAILABLE] and kicks off the download in the
 * background, so the quality score degrades gracefully to geometry-only and
 * recognition simply starts working on a later attempt. Nothing here blocks
 * on the network in a way the caller can't tolerate.
 */
class MlKitHandwritingRecognizer : HandwritingRecognizer {

    private val modelManager = RemoteModelManager.getInstance()
    private val model: DigitalInkRecognitionModel? = runCatching {
        val id = DigitalInkRecognitionModelIdentifier.fromLanguageTag(JAPANESE_TAG)
            ?: return@runCatching null
        DigitalInkRecognitionModel.builder(id).build()
    }.getOrNull()

    private val recognizer: DigitalInkRecognizer? = model?.let {
        runCatching {
            DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(it).build())
        }.getOrNull()
    }

    private val downloadMutex = Mutex()
    @Volatile private var modelReady = false

    override suspend fun recognize(
        strokes: List<List<RecognizerStrokePoint>>,
        target: String,
    ): RecognitionResult {
        val recognizer = recognizer ?: return RecognitionResult.UNAVAILABLE
        if (!ensureModel()) return RecognitionResult.UNAVAILABLE

        val ink = buildInk(strokes) ?: return RecognitionResult.UNAVAILABLE
        return runCatching {
            val result = recognizer.recognize(ink).await()
            val candidates = result.candidates.map { it.text }
            val rank = candidates.indexOfFirst { it == target }.takeIf { it >= 0 }
            RecognitionResult(
                recognized = rank == 0,
                rank = rank,
                topCandidate = candidates.firstOrNull(),
            )
        }.getOrElse {
            if (it is CancellationException) throw it
            Log.w(TAG, "Recognition failed", it)
            RecognitionResult.UNAVAILABLE
        }
    }

    /**
     * Ensures the Japanese model is downloaded. Returns true when ready.
     * The first caller triggers the download and returns false (so this
     * attempt scores geometry-only); subsequent attempts succeed once the
     * model is cached. Serialized so concurrent grades trigger one download.
     */
    private suspend fun ensureModel(): Boolean {
        if (modelReady) return true
        val model = model ?: return false
        return downloadMutex.withLock {
            if (modelReady) return@withLock true
            runCatching {
                if (modelManager.isModelDownloaded(model).await()) {
                    modelReady = true
                    return@withLock true
                }
                modelManager.download(model, DownloadConditions.Builder().build()).await()
                modelReady = true
                true
            }.getOrElse {
                if (it is CancellationException) throw it
                Log.w(TAG, "Model download/check failed; scoring geometry-only for now", it)
                false
            }
        }
    }

    private fun buildInk(strokes: List<List<RecognizerStrokePoint>>): Ink? {
        val nonEmpty = strokes.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return null
        // ML Kit uses timestamps for temporal features; make them relative to
        // the first point so absolute since-boot values don't skew anything.
        val t0 = nonEmpty.first().first().t
        val inkBuilder = Ink.builder()
        for (stroke in nonEmpty) {
            val strokeBuilder = Ink.Stroke.builder()
            for (p in stroke) {
                strokeBuilder.addPoint(Ink.Point.create(p.x, p.y, (p.t - t0).coerceAtLeast(0L)))
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }
        return inkBuilder.build()
    }
}

/** Bridges a Play Services [Task] to a coroutine without pulling in kotlinx-coroutines-play-services. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
