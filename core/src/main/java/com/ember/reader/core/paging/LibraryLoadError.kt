package com.ember.reader.core.paging

/**
 * Typed failure envelope for library paging. [LibraryRemoteMediator] surfaces these through
 * `MediatorResult.Error` so Compose's `LoadState.Error` branch can inspect the cause and render
 * a differentiated UI (auth expired → "sign back in", network → "retry", etc.) without
 * unwrapping raw exception types.
 */
sealed class LibraryLoadError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** Generic network failure (I/O, timeout, connection refused). */
    class Network(val original: Throwable) :
        LibraryLoadError("library fetch failed: ${original.message}", original)

    /** HTTP 401/403 or equivalent — user needs to re-authenticate. */
    class Auth(message: String = "authentication required") : LibraryLoadError(message)

    /** 5xx or parseable server-reported error. */
    class Server(val code: Int, val body: String?) :
        LibraryLoadError("library fetch failed: HTTP $code")

    companion object {
        fun fromThrowable(t: Throwable): LibraryLoadError = when (t) {
            is LibraryLoadError -> t
            else -> Network(t)
        }
    }
}
