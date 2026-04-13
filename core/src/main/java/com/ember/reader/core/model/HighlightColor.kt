package com.ember.reader.core.model

enum class HighlightColor(val argb: Long, val hex: String) {
    YELLOW(0xFFFFEB3B, "#FFEB3B"),
    GREEN(0xFF4CAF50, "#4CAF50"),
    BLUE(0xFF2196F3, "#2196F3"),
    PINK(0xFFE91E63, "#E91E63"),
    ORANGE(0xFFFF9800, "#FF9800"),
    PURPLE(0xFF9C27B0, "#9C27B0");

    companion object {
        // Grimmory uses different hex values than Ember for the same logical colors
        private val grimmoryColorMap = mapOf(
            "FACC15" to YELLOW,
            "FFFF00" to YELLOW,
            "4ADE80" to GREEN,
            "90EE90" to GREEN,
            "38BDF8" to BLUE,
            "87CEEB" to BLUE,
            "F472B6" to PINK,
            "FFB6C1" to PINK,
            "FB923C" to ORANGE,
            "FFD580" to ORANGE
        )

        fun fromHex(hex: String?): HighlightColor {
            if (hex == null) return YELLOW
            val normalized = hex.uppercase().removePrefix("#")
            // Try exact match first, then Grimmory color map
            return entries.firstOrNull { it.hex.removePrefix("#") == normalized }
                ?: grimmoryColorMap[normalized]
                ?: YELLOW
        }
    }
}
