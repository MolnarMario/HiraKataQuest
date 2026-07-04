package com.hive.hiragana.domain.repository

import com.hive.hiragana.domain.scoring.RecognitionResult

/** One captured ink point handed to the recognizer (canvas px + relative time in ms). */
data class RecognizerStrokePoint(val x: Float, val y: Float, val t: Long)

/**
 * Recognizes handwritten strokes and reports whether they read as an
 * intended character — the "is it legible?" half of the hybrid quality
 * score (the other half is [com.hive.hiragana.domain.scoring.GeometryScorer]).
 *
 * Implementations must be resilient: when recognition can't run (model not
 * yet downloaded, disabled, or errored) they return
 * [RecognitionResult.UNAVAILABLE] so callers fall back to geometry-only
 * scoring rather than blocking or failing.
 */
interface HandwritingRecognizer {

    /**
     * Recognizes [strokes] (one point-list per pen-down..up) and reports how
     * the ink relates to [target] (the intended glyph, e.g. "あ").
     */
    suspend fun recognize(
        strokes: List<List<RecognizerStrokePoint>>,
        target: String,
    ): RecognitionResult
}
