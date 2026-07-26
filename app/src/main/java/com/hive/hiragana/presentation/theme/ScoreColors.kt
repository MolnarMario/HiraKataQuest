package com.hive.hiragana.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// (stop, color) anchors, ascending — red → orange → yellow → green.
private val LightSpectrumStops = listOf(
    0.0f to Color(0xFFF3B4AC), // red
    0.4f to Color(0xFFF7CFA6), // orange
    0.6f to Color(0xFFF6E4A0), // yellow
    0.8f to Color(0xFFCBE8C0), // light green
    1.0f to Color(0xFFA9DEA2), // green
)

// The dark-appearance twin: the SAME hue ramp at low luminance, so the dark
// scheme's LIGHT onSurface/onSurfaceVariant text reads on every tint (the
// light pastels would swallow it). Stays distinct from the unscored fallback
// tint (SumiSurfaceVariant 0xFF3D362D) so scored vs. unscored still reads.
private val DarkSpectrumStops = listOf(
    0.0f to Color(0xFF5C2E28), // deep clay red
    0.4f to Color(0xFF5C4326), // deep amber
    0.6f to Color(0xFF57502A), // deep olive-yellow
    0.8f to Color(0xFF3A5232), // deep leaf green
    1.0f to Color(0xFF2E5B33), // deep green
)

/**
 * Maps a 0..100 handwriting score to a soft background tint on a continuous
 * red → orange → yellow → green spectrum. Reuses the [ScoreFeedbackCard]
 * quality-band palette as gradient anchors so the per-kana word chips and the
 * Library score coloring read consistently. Light tints keep glyph/number
 * text legible with a dark on-color; pass `dark = true` (read
 * `LocalKanaColors.current.isDark` at the call site) for the dark-appearance
 * stops — same hues, low luminance, so the light on-color text reads.
 */
fun scoreSpectrumColor(score: Int, dark: Boolean = false): Color {
    val s = score.coerceIn(0, 100) / 100f
    val stops = if (dark) DarkSpectrumStops else LightSpectrumStops
    for (i in 0 until stops.size - 1) {
        val (lo, loColor) = stops[i]
        val (hi, hiColor) = stops[i + 1]
        if (s <= hi) {
            val t = if (hi == lo) 0f else (s - lo) / (hi - lo)
            return lerp(loColor, hiColor, t.coerceIn(0f, 1f))
        }
    }
    return stops.last().second
}
