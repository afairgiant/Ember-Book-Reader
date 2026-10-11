package com.ember.reader.core.model

import java.time.Instant

data class Bookmark(
    val id: Long = 0,
    val bookId: String,
    val locatorJson: String,
    val title: String? = null,
    /** The bookmark's real EPUB CFI, when one is known. See `EpubCfi.isRealCfi`. */
    val cfi: String? = null,
    val createdAt: Instant = Instant.now(),
    val remoteId: Long? = null,
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null
)
