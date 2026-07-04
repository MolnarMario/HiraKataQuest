package com.hive.hiragana.drawing.stroke

/**
 * One continuous pen-down → pen-up stroke drawn by the user.
 */
data class UserStroke(
    val points: MutableList<StrokePoint> = mutableListOf(),
) {
    /** Mean pen pressure across the stroke; 1.0 (neutral) if there are no points. */
    fun averagePressure(): Float {
        if (points.isEmpty()) return 1f
        var sum = 0f
        for (point in points) sum += point.pressure
        return sum / points.size
    }
}
