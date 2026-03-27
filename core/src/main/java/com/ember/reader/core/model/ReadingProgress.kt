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
    val needsSync: Boolean = false
)
