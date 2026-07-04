package com.hive.hiragana.drawing.stroke

import android.view.MotionEvent
import com.hive.hiragana.drawing.canvas.CanvasState

/**
 * Translates raw [MotionEvent]s (delivered through Compose's
 * pointerInteropFilter) into stroke updates on a [CanvasState].
 *
 * - Works with both stylus and finger input.
 * - Captures per-point pressure, including the batched historical
 *   samples Android coalesces into a single ACTION_MOVE.
 * - Tracks a single active pointer; extra fingers that land while a
 *   stroke is in progress are ignored, which doubles as very basic
 *   palm rejection.
 */
class StrokeCapture(private val canvasState: CanvasState) {

    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    /** Tool type of the most recent stroke, e.g. [MotionEvent.TOOL_TYPE_STYLUS]. */
    var lastToolType: Int = MotionEvent.TOOL_TYPE_UNKNOWN
        private set

    val isStylus: Boolean
        get() = lastToolType == MotionEvent.TOOL_TYPE_STYLUS

    /**
     * Feeds one raw event into the stroke state machine. Always returns
     * true so the canvas claims the entire gesture stream.
     */
    fun onMotionEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastToolType = event.getToolType(0)
                canvasState.startStroke(event.strokePointAt(0))
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex != -1) {
                    canvasState.extendStroke(event.collectStrokePoints(pointerIndex))
                }
            }

            MotionEvent.ACTION_UP -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex != -1) {
                    canvasState.extendStroke(listOf(event.strokePointAt(pointerIndex)))
                }
                canvasState.finishStroke()
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Only end the stroke if the pointer drawing it was lifted.
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    canvasState.finishStroke()
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                canvasState.cancelStroke()
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return true
    }

    private fun MotionEvent.strokePointAt(pointerIndex: Int) = StrokePoint(
        x = getX(pointerIndex),
        y = getY(pointerIndex),
        pressure = getPressure(pointerIndex).coerceIn(0f, 2f),
        timestamp = eventTime,
    )

    /** Historical (batched) samples first, then the current sample. */
    private fun MotionEvent.collectStrokePoints(pointerIndex: Int): List<StrokePoint> {
        val points = ArrayList<StrokePoint>(historySize + 1)
        for (h in 0 until historySize) {
            points.add(
                StrokePoint(
                    x = getHistoricalX(pointerIndex, h),
                    y = getHistoricalY(pointerIndex, h),
                    pressure = getHistoricalPressure(pointerIndex, h).coerceIn(0f, 2f),
                    timestamp = getHistoricalEventTime(h),
                )
            )
        }
        points.add(strokePointAt(pointerIndex))
        return points
    }
}
