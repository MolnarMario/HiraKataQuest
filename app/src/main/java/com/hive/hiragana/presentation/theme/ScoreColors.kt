package com.hive.hiragana.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Maps a 0..100 handwriting score to a soft background tint on a continuous
 * red → orange → yellow → green spectrum. Reuses the [ScoreFeedbackCard]
 * quality-band palette as gradient anchors so the per-kana word chips and the
 * Library score coloring read consistently. Tints are light on purpose — glyph
 * and number text stays legible on top (use a dark on-color like onSurface).
 */
fun scoreSpectrumColor(score: Int): Color {
    val s = score.coerceIn(0, 100) / 100f
    // (stop, color) anchors, ascending — red → orange → yellow → green.
    val stops = listOf(
        0.0f to Color(0xFFF3B4AC), // red
        0.4f to Color(0xFFF7CFA6), // orange
        0.6f to Color(0xFFF6E4A0), // yellow
        0.8f to Color(0xFFCBE8C0), // light green
        1.0f to Color(0xFFA9DEA2), // green
    )
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
