package com.ember.reader.core.model

import java.time.Instant

data class ReadingProgress(
    val bookId: String,
    val serverId: Long? = null,
    val percentage: Float = 0f,
    val locatorJson: String? = null,
    val kosyncProgress: String? = null,
    val lastReadAt: Instant = Instant.now(),
    val syncedAt: Instant? = null,
    val needsSync: Boolean = false,
) {
    companion object {
        fun fromRemote(
            bookId: String,
            serverId: Long,
            percentage: Float,
        ): ReadingProgress = ReadingProgress(
            bookId = bookId,
            serverId = serverId,
            percentage = percentage,
            lastReadAt = Instant.now(),
            syncedAt = Instant.now(),
            needsSync = false,
        )
    }
}

/**
 * Normalizes a Grimmory percentage (which may be 0-100) to the 0-1 range used internally.
 */
fun Float.normalizeGrimmoryPercentage(): Float = if (this > 1f) this / 100f else this

/**
 * Converts an internal 0-1 percentage to Grimmory's 0-100 range with one decimal place.
 */
fun Float.toGrimmoryPercentage(): Float = kotlin.math.round(this * 1000f) / 10f
