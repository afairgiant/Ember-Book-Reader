package com.ember.reader.core.model

enum class HighlightColor(val argb: Long, val hex: String) {
    YELLOW(0xFFFFEB3B, "#FFEB3B"),
    GREEN(0xFF4CAF50, "#4CAF50"),
    BLUE(0xFF2196F3, "#2196F3"),
    PINK(0xFFE91E63, "#E91E63"),
    ORANGE(0xFFFF9800, "#FF9800"),
    PURPLE(0xFF9C27B0, "#9C27B0");

    companion object {
        fun fromHex(hex: String?): HighlightColor {
            if (hex == null) return YELLOW
            val normalized = hex.uppercase().removePrefix("#")
            return entries.firstOrNull { it.hex.removePrefix("#") == normalized } ?: YELLOW
        }
    }
}
