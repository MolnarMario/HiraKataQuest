package com.hive.hiragana.presentation.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hive.hiragana.domain.audio.VoiceStatus

/**
 * A single speaker button that says [glyph] aloud with the device's Japanese
 * text-to-speech. Behaviour follows [status]:
 *
 * - [VoiceStatus.READY]: tap speaks the glyph.
 * - [VoiceStatus.NEEDS_DOWNLOAD]: the device supports Japanese but the voice
 *   isn't installed — tap opens the system "install voice data" flow so the
 *   user can download it for free (returning to the app re-checks — see the
 *   ON_RESUME refresh in PracticeScreen).
 * - [VoiceStatus.UNAVAILABLE]: no Japanese voice at all — the button greys out.
 */
@Composable
fun PronunciationButton(
    glyph: String,
    status: VoiceStatus,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    IconButton(
        onClick = {
            when (status) {
                VoiceStatus.READY -> onSpeak(glyph)
                VoiceStatus.NEEDS_DOWNLOAD -> installVoiceData(context)
                VoiceStatus.UNAVAILABLE -> Unit
            }
        },
        enabled = status != VoiceStatus.UNAVAILABLE,
        modifier = modifier.size(ButtonSize),
    ) {
        val color = LocalContentColor.current
        Canvas(modifier = Modifier.size(IconSize)) { drawSpeakerGlyph(color) }
    }
}

/**
 * Open the platform's text-to-speech data installer so the user can download a
 * Japanese voice. Falls back to the system Text-to-speech settings screen if no
 * installer activity is present.
 */
private fun installVoiceData(context: Context) {
    val install = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(install)
        return
    } catch (_: ActivityNotFoundException) {
        // no installer — fall through to TTS settings
    }
    try {
        context.startActivity(
            Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        // Nothing to open — leave the button as a no-op on this device.
    }
}

// Sized to match the stroke-order playback controls (a 24dp glyph in a 32dp
// button, same as their play button) so the two icon rows read as a set.
private val ButtonSize = 32.dp
private val IconSize = 24.dp

/**
 * A speaker cone with two sound-wave arcs — hand-drawn like the app's other
 * bespoke glyphs (grid/ghost toggles) because the speaker icon lives in
 * material-icons-extended, which the project deliberately avoids. Proportions
 * mirror a standard Material volume icon: a square-ish cone on the left, two
 * concentric circular arcs radiating right.
 */
private fun DrawScope.drawSpeakerGlyph(color: Color) {
    val s = size.minDimension
    val stroke = s * 0.085f
    // Cone: a small back box that flares into a tall front, as one filled shape.
    val cone = Path().apply {
        moveTo(s * 0.10f, s * 0.40f)   // back top-left
        lineTo(s * 0.28f, s * 0.40f)   // back top-right
        lineTo(s * 0.46f, s * 0.22f)   // front top
        lineTo(s * 0.46f, s * 0.78f)   // front bottom
        lineTo(s * 0.28f, s * 0.60f)   // back bottom-right
        lineTo(s * 0.10f, s * 0.60f)   // back bottom-left
        close()
    }
    drawPath(cone, color = color)
    // Two concentric round arcs (equal width/height = circular, not egg-shaped)
    // centred just past the cone front so only their right side shows.
    val cx = s * 0.44f
    val cy = s * 0.50f
    fun wave(radius: Float) {
        drawArc(
            color = color,
            startAngle = -48f,
            sweepAngle = 96f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = stroke),
        )
    }
    wave(s * 0.20f)
    wave(s * 0.33f)
}
