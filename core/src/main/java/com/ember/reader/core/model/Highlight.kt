package com.ember.reader.core.model

import java.time.Instant

data class Highlight(
    val id: Long = 0,
    val bookId: String,
    val locatorJson: String,
    val color: HighlightColor = HighlightColor.YELLOW,
    val annotation: String? = null,
    val selectedText: String? = null,
    val createdAt: Instant = Instant.now()
)
