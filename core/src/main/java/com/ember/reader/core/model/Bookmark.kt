package com.ember.reader.core.model

import java.time.Instant

data class Bookmark(
    val id: Long = 0,
    val bookId: String,
    val locatorJson: String,
    val title: String? = null,
    val createdAt: Instant = Instant.now()
)
