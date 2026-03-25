package com.ember.reader.core.model

enum class SyncFrequency(val displayName: String, val intervalMinutes: Long?) {
    MANUAL("Manual", null),
    ON_OPEN_CLOSE("On open/close", null),
    EVERY_15_MINUTES("Every 15 minutes", 15),
    EVERY_30_MINUTES("Every 30 minutes", 30),
    EVERY_HOUR("Every hour", 60),
}
