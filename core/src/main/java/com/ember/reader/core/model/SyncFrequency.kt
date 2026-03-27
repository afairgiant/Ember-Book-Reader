package com.ember.reader.core.model

enum class SyncFrequency(val displayName: String, val intervalMinutes: Long?, val syncOnOpenClose: Boolean) {
    MANUAL("Manual", null, false),
    ON_OPEN_CLOSE("On open/close only", null, true),
    EVERY_15_MINUTES("On open/close + every 15 min", 15, true),
    EVERY_30_MINUTES("On open/close + every 30 min", 30, true),
    EVERY_HOUR("On open/close + every hour", 60, true),
}
