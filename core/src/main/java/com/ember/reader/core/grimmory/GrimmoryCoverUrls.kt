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

/** Same as [grimmoryCoverUrl] but for audiobook covers, which are served from a different endpoint. */
fun grimmoryAudiobookCoverUrl(baseUrl: String, bookId: Long, coverUpdatedOn: String? = null): String {
    val base = "${serverOrigin(baseUrl)}/api/v1/audiobooks/$bookId/cover"
    return coverUpdatedOn?.let { "$base?v=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: base
}
