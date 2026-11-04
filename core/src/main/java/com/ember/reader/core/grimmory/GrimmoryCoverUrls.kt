package com.ember.reader.core.grimmory

import com.ember.reader.core.network.serverOrigin

/**
 * Build a Grimmory book cover URL. When [coverUpdatedOn] is provided, a `?v=` query
 * parameter is appended so Coil's URL-keyed cache refetches whenever the server's
 * cover changes (metadata edit, web UI upload, etc.). Auth is added by
 * `CoverAuthInterceptor` on the Coil ImageLoader, not here.
 */
fun grimmoryCoverUrl(baseUrl: String, bookId: Long, coverUpdatedOn: String? = null): String {
    val base = "${serverOrigin(baseUrl)}/api/v1/media/book/$bookId/cover"
    return coverUpdatedOn?.let { "$base?v=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: base
}

/**
 * Same as [grimmoryCoverUrl] but for audiobook covers, which are stored apart from a book's.
 * This is the cover Grimmory's library holds, as its web UI shows it. The audiobook player's
 * `/api/v1/audiobooks/{id}/cover` only reads art embedded in the audio files, so it has none
 * for a book whose files carry no art, even when the library has a cover for it.
 */
fun grimmoryAudiobookCoverUrl(baseUrl: String, bookId: Long, coverUpdatedOn: String? = null): String {
    val base = "${serverOrigin(baseUrl)}/api/v1/media/book/$bookId/audiobook-cover"
    return coverUpdatedOn?.let { "$base?v=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: base
}

/**
 * The cover URL of a Grimmory book whose primary file is [primaryFileType], as Grimmory's
 * models name it: an audiobook's from [grimmoryAudiobookCoverUrl], versioned by
 * [audiobookCoverUpdatedOn] when the model has it, and any other book's from [grimmoryCoverUrl].
 */
fun grimmoryCoverUrlFor(
    baseUrl: String,
    bookId: Long,
    primaryFileType: String?,
    coverUpdatedOn: String? = null,
    audiobookCoverUpdatedOn: String? = null,
): String = if (primaryFileType.equals("AUDIOBOOK", ignoreCase = true)) {
    grimmoryAudiobookCoverUrl(baseUrl, bookId, audiobookCoverUpdatedOn ?: coverUpdatedOn)
} else {
    grimmoryCoverUrl(baseUrl, bookId, coverUpdatedOn)
}
