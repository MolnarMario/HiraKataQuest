package com.hive.hiragana.drawing.stroke

/**
 * A single sampled input point. Coordinates are in canvas-local pixels,
 * pressure is the raw MotionEvent pressure (stylus devices report real
 * pressure, fingers usually report ~1.0) and timestamp is the MotionEvent
 * event time in milliseconds since boot.
 */
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val timestamp: Long,
)
