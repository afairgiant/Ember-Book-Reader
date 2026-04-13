package com.ember.reader.core.grimmory

/**
 * HTTP error from a Grimmory API call, carrying the status code so callers
 * (especially [GrimmoryTokenManager.withAuth]) can branch on specific codes
 * (e.g. 401 for token refresh) without fragile string matching.
 */
class GrimmoryHttpException(
    val statusCode: Int,
    message: String
) : Exception(message)
