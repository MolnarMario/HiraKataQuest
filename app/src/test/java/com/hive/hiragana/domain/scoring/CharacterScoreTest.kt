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
    fun fitModeUsesFitAsHeadline() {
        val s = CharacterScore.combine(geo(80, 40), recognition = null, mode = ScoringMode.FIT)
        assertEquals(40, s.headline)
    }

    @Test
    fun bothModeAveragesTheDials() {
        val s = CharacterScore.combine(geo(80, 40), recognition = null, mode = ScoringMode.BOTH)
        assertEquals(60, s.headline)
    }

    @Test
    fun fitModeFallsBackToFormWhenNoFitAvailable() {
        val s = CharacterScore.combine(geo(80, null), recognition = null, mode = ScoringMode.FIT)
        assertEquals(80, s.headline)
    }

    @Test
    fun recognitionMissCapsHeadlineAndCoaches() {
        val miss = RecognitionResult(recognized = false, rank = null, topCandidate = "お")
        val s = CharacterScore.combine(geo(95, 90), miss, mode = ScoringMode.FORM)
        assertTrue("headline should be capped when unreadable, was ${s.headline}", s.headline <= 45)
        assertTrue(s.tags.any { it.contains("お") })
        assertTrue(s.recognitionRan)
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
