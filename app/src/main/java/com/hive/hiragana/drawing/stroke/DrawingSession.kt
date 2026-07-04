package com.hive.hiragana.drawing.stroke

/**
 * All strokes drawn for the character currently on the canvas.
 * A new session starts whenever the canvas is cleared or the
 * character changes.
 */
data class DrawingSession(
    val strokes: MutableList<UserStroke> = mutableListOf(),
)

/**
 * Wall-clock time between the first and last sampled point, based on
 * MotionEvent timestamps (both are "ms since boot", so the difference
 * is a valid duration).
 */
fun DrawingSession.durationMillis(): Long {
    val first = strokes.firstOrNull()?.points?.firstOrNull()?.timestamp ?: return 0L
    val last = strokes.lastOrNull()?.points?.lastOrNull()?.timestamp ?: return 0L
    return (last - first).coerceAtLeast(0L)
}
