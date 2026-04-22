package com.ember.reader.ui.reader.common

internal const val BRIGHTNESS_MIN = 0.01f
internal const val BRIGHTNESS_MAX = 1.0f
internal const val BRIGHTNESS_SENSITIVITY = 0.5f
internal const val BRIGHTNESS_DRAG_THRESHOLD_PX = 4f

internal fun computeBrightnessFromAnchor(
    startBrightness: Float,
    anchorY: Float,
    currentY: Float,
    heightPx: Float,
    sensitivityFactor: Float = BRIGHTNESS_SENSITIVITY,
): Float {
    val fullRangePx = heightPx * sensitivityFactor
    if (fullRangePx <= 0f) {
        return startBrightness.coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX)
    }
    val delta = (anchorY - currentY) / fullRangePx
    return (startBrightness + delta).coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX)
}
