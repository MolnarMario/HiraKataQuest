package com.hive.hiragana.domain.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure [GeometryScorer]. The key behaviours pinned here:
 * exact/scaled/translated copies score ~100 on FORM (position & size are
 * forgiven — the core user goal), while wrong stroke count, reversed
 * strokes, and bad proportion are penalised.
 */
class GeometryScorerTest {

    // A simple 2-stroke reference glyph (like a plus sign) in viewBox-ish space:
    // stroke 1 horizontal, stroke 2 vertical.
    private val refStroke1 = listOf(Pt(10f, 50f), Pt(50f, 50f), Pt(90f, 50f))
    private val refStroke2 = listOf(Pt(50f, 10f), Pt(50f, 50f), Pt(50f, 90f))
    private val reference = listOf(refStroke1, refStroke2)

    @Test
    fun exactReplayScoresNearPerfectForm() {
        val score = GeometryScorer.score(reference, reference, cell = null)
        assertTrue("form should be ~100, was ${score.form}", score.form >= 99)
        assertEquals(2, score.strokeCountExpected)
        assertEquals(2, score.strokeCountActual)
    }

    @Test
    fun scaledAndTranslatedCopyStillScoresNearPerfectForm() {
        // Same shape, 3x bigger and shifted far away — FORM must forgive this
        // because it normalizes by bounding box. This is the "readable but
        // doesn't hug the guide" case the feature exists to reward.
        val user = reference.map { stroke -> stroke.map { Pt(it.x * 3f + 500f, it.y * 3f + 400f) } }
        val score = GeometryScorer.score(user, reference, cell = null)
        assertTrue("form should stay ~100 under scale+translate, was ${score.form}", score.form >= 98)
    }

    @Test
    fun reversedStrokeIsDetectedAndPenalised() {
        val user = listOf(refStroke1, refStroke2.reversed())
        val score = GeometryScorer.score(user, reference, cell = null)
        assertTrue("form should drop for a backwards stroke, was ${score.form}", score.form < 80)
        assertTrue(
            "should coach the backwards stroke: ${score.tags}",
            score.tags.any { it.contains("backwards") },
        )
    }

    @Test
    fun missingStrokeReducesFormAndCoachesCount() {
        val user = listOf(refStroke1) // only 1 of 2 strokes — incomplete character
        val score = GeometryScorer.score(user, reference, cell = null)
        assertEquals(2, score.strokeCountExpected)
        assertEquals(1, score.strokeCountActual)
        // Missing strokes are penalised (below a clean pass) but not slashed.
        assertTrue("missing a stroke should reduce form, was ${score.form}", score.form < 85)
        assertTrue("but shouldn't be gutted, was ${score.form}", score.form > 50)
        assertTrue("should coach stroke count: ${score.tags}", score.tags.any { it.contains("stroke") })
    }

    @Test
    fun extraStrokeIsOnlyMildlyPenalisedAndCoached() {
        // A COMPLETE plus sign plus one extra stroke = a legitimate
        // segmentation variant: coached, but only a small deduction.
        val user = listOf(refStroke1, refStroke2, listOf(Pt(48f, 48f), Pt(52f, 52f)))
        val score = GeometryScorer.score(user, reference, cell = null)
        assertEquals(2, score.strokeCountExpected)
        assertEquals(3, score.strokeCountActual)
        assertTrue("extra stroke should be only mildly penalised, was ${score.form}", score.form >= 80)
        assertTrue(score.tags.any { it.contains("stroke") })
    }

    @Test
    fun emptyInputScoresZero() {
        val score = GeometryScorer.score(emptyList(), reference, cell = null)
        assertEquals(0, score.form)
    }

    @Test
    fun fitIsNullWithoutACellAndComputedWithOne() {
        val noCell = GeometryScorer.score(reference, reference, cell = null)
        assertEquals(null, noCell.fit)

        // Ink that nicely fills and centers a 0..100 cell should fit well.
        val cell = CellRect(left = 0f, top = 0f, size = 100f)
        val wellFilled = GeometryScorer.score(reference, reference, cell = cell)
        assertTrue("well-centered ink should fit well, was ${wellFilled.fit}", (wellFilled.fit ?: 0) >= 70)
    }

    @Test
    fun tinyInkInLargeCellScoresLowFitButNotLowForm() {
        // Same shape but crammed into a corner of a big cell: FORM stays high
        // (shape is right), FIT drops (doesn't fill/center the cell).
        val user = reference.map { stroke -> stroke.map { Pt(it.x * 0.15f + 2f, it.y * 0.15f + 2f) } }
        val cell = CellRect(left = 0f, top = 0f, size = 300f)
        val score = GeometryScorer.score(user, reference, cell = cell)
        assertTrue("form should stay high, was ${score.form}", score.form >= 98)
        assertTrue("fit should be low, was ${score.fit}", (score.fit ?: 100) < 55)
    }

    @Test
    fun resampleProducesRequestedCountAndKeepsEndpoints() {
        val line = listOf(Pt(0f, 0f), Pt(10f, 0f))
        val out = GeometryScorer.resample(line, 5)
        assertEquals(5, out.size)
        assertEquals(0f, out.first().x, 1e-3f)
        assertEquals(10f, out.last().x, 1e-3f)
        assertEquals(5f, out[2].x, 1e-3f) // midpoint by arc length
    }

    @Test
    fun normalizePreservesAspectRatioAndCenters() {
        // A wide, short shape: after normalize the long side spans [0,1],
        // the short side is centered (so not touching 0 or 1).
        val wide = listOf(listOf(Pt(0f, 0f), Pt(100f, 20f)))
        val norm = GeometryScorer.normalizeToUnitSquare(wide).first()
        assertEquals(0f, norm.first().x, 1e-3f)
        assertEquals(1f, norm.last().x, 1e-3f)
        assertTrue("short side should be centered inside (0,1)", norm.first().y > 0f && norm.last().y < 1f)
    }
}
