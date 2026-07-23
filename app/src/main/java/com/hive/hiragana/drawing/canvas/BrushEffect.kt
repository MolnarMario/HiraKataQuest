package com.hive.hiragana.drawing.canvas

/**
 * The artistic **brush effect** the user's ink is drawn with — independent of
 * the ink COLOR (see [com.hive.hiragana.domain.repository.SettingsRepository]).
 *
 * Purely visual: an effect changes only how a stroke LOOKS, never the raw
 * [com.hive.hiragana.drawing.stroke.StrokePoint]s the handwriting scorer reads,
 * so it can never affect scoring. Each effect carries a [BrushSpec] that fully
 * describes it as data — the [WidthProfile] + [Cap] drive the ribbon GEOMETRY
 * baked at input time by [StrokeRenderer.buildBrushPath], while [BrushSpec.alpha]
 * is applied at draw time. Because the geometry differs per effect, switching
 * effects clears the current sheet (same rule as a layout change).
 *
 * Persisted as [prefValue]; [BRUSH] is the default and reproduces the app's
 * original uniform-ribbon look, so an existing install sees no change until the
 * learner picks another effect.
 */
enum class BrushEffect(
    val prefValue: String,
    val displayLabel: String,
    val spec: BrushSpec,
) {
    /** The original even, smooth ribbon. Default. */
    BRUSH("brush", "Brush", BrushSpec(WidthProfile.Uniform, cap = Cap.Round)),

    /**
     * Fude 筆: the width swells where the pen moves slowly / presses hard and
     * thins on fast flicks, with pointed tapered ends — the most fitting effect
     * for kana. Speed drives it for finger input (constant pressure); a stylus's
     * pressure adds on top.
     */
    CALLIGRAPHY(
        "calligraphy",
        "Calligraphy 筆",
        BrushSpec(widthProfile = WidthProfile.Dynamic(), widthScale = 1.05f, cap = Cap.Pointed),
    ),

    /** Thin, crisp, with a mild entry/exit taper — a fine-nibbed pen. */
    FOUNTAIN_PEN(
        "fountain_pen",
        "Fountain pen",
        BrushSpec(
            widthProfile = WidthProfile.Tapered(entryExitFraction = 0.14f),
            widthScale = 0.62f,
            cap = Cap.Round,
        ),
    ),

    /** Bold and flat-capped, drawn semi-transparent so overlaps darken. */
    MARKER(
        "marker",
        "Marker",
        BrushSpec(widthProfile = WidthProfile.Uniform, widthScale = 1.2f, alpha = 0.6f, cap = Cap.Square),
    );

    companion object {
        /** Decodes a stored [prefValue]; unknown/null → [BRUSH] (the default). */
        fun fromPrefValue(value: String?): BrushEffect =
            entries.firstOrNull { it.prefValue == value } ?: BRUSH
    }
}

/**
 * The data description of a [BrushEffect]: how the ribbon width varies along the
 * stroke ([widthProfile]), a multiplier on the base ink width ([widthScale],
 * applied by the caller so [StrokeRenderer] stays width-agnostic), the draw-time
 * fill [alpha], and the end [cap] shape.
 */
data class BrushSpec(
    val widthProfile: WidthProfile = WidthProfile.Uniform,
    val widthScale: Float = 1f,
    val alpha: Float = 1f,
    val cap: Cap = Cap.Round,
)

/** How a stroke's half-width is distributed along its centerline. */
sealed interface WidthProfile {
    /** Constant width everywhere. */
    data object Uniform : WidthProfile

    /** Constant width with a smooth ramp over the first/last [entryExitFraction] of points. */
    data class Tapered(val entryExitFraction: Float) : WidthProfile

    /**
     * Width varies with pen speed (px/ms from timestamps) and pressure, then
     * tapers to a point at both ends — the calligraphy profile.
     */
    data class Dynamic(
        val refSpeedPxPerMs: Float = 2.2f,
        val minFactor: Float = 0.4f,
        val maxFactor: Float = 1.35f,
        val pressureExp: Float = 0.5f,
        val taperFraction: Float = 0.16f,
        val maxSwell: Float = 1.6f,
        val smoothPasses: Int = 2,
    ) : WidthProfile
}

/** End-cap shape of the filled ribbon. */
enum class Cap { Round, Square, Pointed }
