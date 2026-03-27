package com.ember.reader.core.model

import java.time.Instant

data class ReadingSession(
    val id: Long = 0,
    val bookId: String,
    val startTime: Instant,
    val endTime: Instant,
    val durationSeconds: Long,
    val startProgress: Float,
    val endProgress: Float
)
