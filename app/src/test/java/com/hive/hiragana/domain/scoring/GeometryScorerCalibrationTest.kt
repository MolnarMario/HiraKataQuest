package com.hive.hiragana.domain.scoring

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Calibration bands for the scoring pipeline.
 *
 * [GeometryScorerTest] pins individual behaviours; this suite pins the thing
 * the learner actually experiences — **what number comes out for what kind of
 * ink**. It exists because the scorer's constants were hand-tuned, which made
 * the resulting scores impossible to argue with or against: a device report of
 * "a random line scores 45, that should be near zero" had nothing objective to
 * be checked against.
 *
 * Every case asserts a BAND, not an exact value, so the constants
 * ([GeometryScorer]'s COVERAGE_TOLERANCE / GATE_FULL / BACKWARDS_PENALTY and
 * [CharacterScore]'s legibility factors) can be retuned freely as long as the
 * behaviour a learner can observe stays honest. If a tuning change breaks a
 * band, that is the suite doing its job: either the change is wrong, or the
 * band was and should be re-pinned deliberately.
 *
 * The reference is katakana エ — the glyph from the original device report —
 * as three strokes in viewBox-109 space.
 */
class GeometryScorerCalibrationTest {

    private val topBar = line(Pt(20f, 25f), Pt(89f, 25f))
    private val stem = line(Pt(54f, 25f), Pt(54f, 80f))
    private val bottomBar = line(Pt(15f, 84f), Pt(94f, 84f))
    private val reference = listOf(topBar, stem, bottomBar)

    /** The guide cell the ink is judged against, in the same space. */
    private val cell = CellRect(left = 0f, top = 0f, size = 109f)

    private fun headline(user: List<List<Pt>>, mode: ScoringMode = ScoringMode.BOTH): Int =
        CharacterScore.combine(
            GeometryScorer.score(user, reference, cell),
            recognition = null,
            mode = mode,
        ).headline

    // --- writing that deserves a good score ---------------------------------

    @Test
    fun `an exact trace scores at the top`() {
        assertBand("exact trace", headline(reference), 95..100)
    }

    @Test
    fun `slightly wobbly writing still scores well`() {
        // A smooth hand wobble (vertices nudged, then resampled), NOT per-point
        // noise: this is what careful-but-human writing looks like.
        val rng = Random(20260802)
        val user = reference.map { stroke ->
            stroke.map { Pt(it.x + rng.nextFloat().times(3f) - 1.5f, it.y + rng.nextFloat().times(3f) - 1.5f) }
        }
        assertBand("wobbly trace", headline(user), 82..100)
    }

    @Test
    fun `the same shape drawn small and off-centre keeps its FORM`() {
        // FORM normalizes by bounding box, so size and placement are forgiven.
        // This is the core promise of the dial and must not regress.
        val user = reference.map { stroke -> stroke.map { Pt(it.x * 0.4f + 6f, it.y * 0.4f + 4f) } }
        assertBand("small off-centre trace", headline(user, ScoringMode.FORM), 90..100)
    }

    // --- ink that is not the character --------------------------------------

    @Test
    fun `a single random line scores near zero`() {
        // The reported bug: this used to display 45, because a shape-blind FIT
        // dial (~94 for anything centred) was averaged into the headline and
        // the recognition CAP became the number. It is one stroke of a
        // three-stroke glyph and resembles none of it.
        val user = listOf(line(Pt(20f, 20f), Pt(89f, 84f)))
        assertBand("single random line", headline(user), 0..12)
    }

    @Test
    fun `a horizontal line that overlaps one real stroke still scores near zero`() {
        // The adversarial case: the ink IS one of the reference strokes, so the
        // per-stroke pass alone would score it perfectly. Only the resemblance
        // gate plus the missing-stroke penalty can tell that two thirds of the
        // character was never drawn.
        assertBand("one correct stroke of three", headline(listOf(topBar)), 0..45)
    }

    @Test
    fun `a scribble scores near zero`() {
        val rng = Random(7)
        val user = (0 until 5).map {
            val cx = rng.nextFloat() * 80f + 10f
            val cy = rng.nextFloat() * 80f + 10f
            val a = rng.nextFloat() * 6.28f
            line(Pt(cx, cy), Pt(cx + 30f * cos(a), cy + 30f * sin(a)))
        }
        assertBand("5-stroke scribble", headline(user), 0..15)
    }

    @Test
    fun `filling the box neatly with nonsense cannot score well in any mode`() {
        // FIT measures only the ink's bounding box, so a single line drawn
        // corner to corner fills and centres the cell perfectly. No mode may
        // let that become a good score.
        val user = listOf(line(Pt(10f, 10f), Pt(99f, 99f)))
        ScoringMode.entries.filter { it != ScoringMode.OFF }.forEach { mode ->
            assertBand("neat nonsense in $mode", headline(user, mode), 0..25)
        }
    }

    @Test
    fun `an empty canvas scores zero`() {
        assertBand("empty", headline(emptyList()), 0..0)
    }

    // --- writing that is recognisably the character but done wrong ----------

    @Test
    fun `correct shapes in the wrong stroke order land in the middle`() {
        val user = listOf(bottomBar, stem, topBar)
        assertBand("reversed stroke order", headline(user), 35..70)
    }

    @Test
    fun `a missing stroke lands in the middle`() {
        val user = listOf(topBar, bottomBar) // no stem
        assertBand("missing stroke", headline(user), 30..65)
    }

    @Test
    fun `every stroke drawn backwards lands in the middle and is named`() {
        val user = reference.map { it.reversed() }
        val score = GeometryScorer.score(user, reference, cell)
        assertBand("all strokes backwards", headline(user), 40..75)
        assertTrue(
            "backwards strokes should be coached, got ${score.tags}",
            score.tags.any { it.contains("backwards") },
        )
    }

    // --- properties that must hold no matter how the dials are tuned --------

    @Test
    fun `no input produces a score that rests on a constant`() {
        // The original failure mode: two clamp constants (65 and 45) became
        // the modal output, so the number shown was not a measurement. Sweep a
        // range of degraded inputs and require the results to actually spread.
        val scores = (0..10).map { step ->
            val t = step / 10f
            // Interpolate from an exact trace toward a single meaningless line.
            val degraded = reference.mapIndexed { i, stroke ->
                if (i == 0) stroke.map { Pt(it.x, it.y + t * 60f) } else stroke
            }
            headline(degraded)
        }
        assertTrue(
            "degrading the input should move the score, got $scores",
            scores.distinct().size >= 4,
        )
        assertTrue(
            "no value should dominate the sweep, got $scores",
            scores.groupBy { it }.values.maxOf { it.size } <= 5,
        )
    }

    @Test
    fun `the recognition verdict can never raise a score`() {
        val geometry = GeometryScorer.score(listOf(line(Pt(20f, 20f), Pt(89f, 84f))), reference, cell)
        val verdicts = listOf(
            RecognitionResult(recognized = true, rank = 0, topCandidate = "エ"),
            RecognitionResult(recognized = false, rank = 1, topCandidate = "工"),
            RecognitionResult(recognized = false, rank = 4, topCandidate = "ロ"),
            RecognitionResult(recognized = false, rank = null, topCandidate = "口"),
        )
        val geometryOnly = CharacterScore.combine(geometry, null, ScoringMode.BOTH).headline
        verdicts.forEach { verdict ->
            val withRecognition = CharacterScore.combine(geometry, verdict, ScoringMode.BOTH).headline
            assertTrue(
                "recognition ($verdict) raised $geometryOnly to $withRecognition",
                withRecognition <= geometryOnly,
            )
        }
    }

    @Test
    fun `a well-formed character misread by the recognizer keeps most of its score`() {
        // The エ/工 case. Even with the homoglyph table fixing this particular
        // pair, a misread must be a discount and not a cliff: the geometry
        // compares against the character's real reference strokes, so it is
        // the better-informed of the two judges.
        val geometry = GeometryScorer.score(reference, reference, cell)
        val misread = RecognitionResult(recognized = false, rank = 1, topCandidate = "工")
        val score = CharacterScore.combine(geometry, misread, ScoringMode.BOTH).headline
        assertBand("well-formed but misread", score, 75..95)
    }

    // --- helpers ------------------------------------------------------------

    /** A straight stroke sampled into vertices, so wobble/resampling behave realistically. */
    private fun line(from: Pt, to: Pt, steps: Int = 8): List<Pt> = (0..steps).map { i ->
        val t = i / steps.toFloat()
        Pt(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
    }

    private fun assertBand(what: String, score: Int, band: IntRange) {
        assertTrue("$what should score in $band, was $score", score in band)
    }
}
