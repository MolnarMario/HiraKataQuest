package com.hive.hiragana.domain.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [CharacterScore.combine] — headline selection per mode and the recognition gate. */
class CharacterScoreTest {

    private fun geo(form: Int, fit: Int?) = GeometryScore(
        form = form,
        fit = fit,
        strokeCountExpected = 2,
        strokeCountActual = 2,
        tags = emptyList(),
    )

    @Test
    fun formModeUsesFormAsHeadline() {
        val s = CharacterScore.combine(geo(80, 40), recognition = null, mode = ScoringMode.FORM)
        assertEquals(80, s.headline)
        assertEquals(80, s.form)
        assertEquals(40, s.fit)
        assertFalse(s.recognitionRan)
    }

    @Test
    fun fitModeUsesFitAsHeadlineWhenTheShapeIsSound() {
        // form 80 is comfortably above the shape gate, so FIT is reported as-is.
        val s = CharacterScore.combine(geo(80, 40), recognition = null, mode = ScoringMode.FIT)
        assertEquals(40, s.headline)
    }

    @Test
    fun fitModeIsGatedByShapeSoNeatNonsenseCannotScore() {
        // FIT only measures the ink's bounding box, so a single centred line
        // scores ~94 on it. Without the gate that became the headline.
        val s = CharacterScore.combine(geo(10, 94), recognition = null, mode = ScoringMode.FIT)
        assertTrue("neat nonsense should not score well, was ${s.headline}", s.headline <= 20)
    }

    @Test
    fun bothModeIsFormDominantNotAnAverage() {
        // Averaging let a shape-blind FIT carry a bad attempt. FIT now
        // modulates FORM within a band: 80 * (0.65 + 0.35 * 0.40) = 63.
        val s = CharacterScore.combine(geo(80, 40), recognition = null, mode = ScoringMode.BOTH)
        assertEquals(63, s.headline)
    }

    @Test
    fun bothModeCannotRescueBadFormWithGoodFit() {
        val s = CharacterScore.combine(geo(12, 94), recognition = null, mode = ScoringMode.BOTH)
        assertTrue("bad form should stay bad, was ${s.headline}", s.headline <= 15)
    }

    @Test
    fun fitModeFallsBackToFormWhenNoFitAvailable() {
        val s = CharacterScore.combine(geo(80, null), recognition = null, mode = ScoringMode.FIT)
        assertEquals(80, s.headline)
    }

    @Test
    fun recognitionMissDiscountsHeadlineAndCoaches() {
        val miss = RecognitionResult(recognized = false, rank = null, topCandidate = "お")
        val s = CharacterScore.combine(geo(95, 90), miss, mode = ScoringMode.FORM)
        // Scaled (95 * 0.70), not clamped to a constant. Stays a good score
        // because the geometry says the shape is right and the recognizer has
        // known blind spots on this deck.
        assertTrue("should be discounted, was ${s.headline}", s.headline in 60..70)
        assertTrue(s.tags.any { it.contains("お") })
        assertTrue(s.recognitionRan)
    }

    /**
     * The property the whole redesign turns on. A clamp is a maximum, so the
     * old `coerceAtMost(65 / 45)` made those two constants the modal score:
     * every misranked-but-well-written character landed on exactly 65 and
     * every scribble whose geometry cleared 45 landed on exactly 45. Both were
     * reported from the device as "the score it defaults to". A multiplier
     * cannot do that.
     */
    @Test
    fun recognitionScalesTheHeadlineAndNeverInventsAFloor() {
        val miss = RecognitionResult(recognized = false, rank = null, topCandidate = "お")
        listOf(0, 5, 12, 30, 60, 95).forEach { form ->
            val s = CharacterScore.combine(geo(form, form), miss, mode = ScoringMode.FORM)
            assertTrue(
                "a miss must never raise $form, was ${s.headline}",
                s.headline <= form,
            )
        }
        // Distinct inputs must stay distinguishable rather than collapsing.
        val headlines = listOf(50, 60, 70, 80).map {
            CharacterScore.combine(geo(it, it), miss, mode = ScoringMode.FORM).headline
        }
        assertEquals(headlines.distinct().size, headlines.size)
    }

    @Test
    fun aMisrankedButReadableAttemptIsOnlyLightlyDiscounted() {
        // The エ/工 case: the recognizer put the target second, behind a glyph
        // that is the same shape. That is doubt, not a verdict.
        val nearMiss = RecognitionResult(recognized = false, rank = 1, topCandidate = "工")
        val s = CharacterScore.combine(geo(86, 85), nearMiss, mode = ScoringMode.FORM)
        assertEquals(79, s.headline)
    }

    @Test
    fun recognitionHitKeepsHeadlineAndConfirms() {
        val hit = RecognitionResult(recognized = true, rank = 0, topCandidate = "あ")
        val s = CharacterScore.combine(geo(88, 70), hit, mode = ScoringMode.FORM)
        assertEquals(88, s.headline)
        assertEquals(100, s.recognitionScore)
    }

    @Test
    fun unavailableRecognitionScoresGeometryOnly() {
        val s = CharacterScore.combine(geo(88, 70), RecognitionResult.UNAVAILABLE, mode = ScoringMode.FORM)
        assertEquals(88, s.headline)
        assertFalse(s.recognitionRan)
        assertEquals(null, s.recognitionScore)
    }

    @Test
    fun tagsCappedAtTwo() {
        val geoWithTags = GeometryScore(70, 50, 2, 2, listOf("a", "b", "c"))
        val s = CharacterScore.combine(geoWithTags, recognition = null, mode = ScoringMode.FORM)
        assertTrue(s.tags.size <= 2)
    }
}
