package com.ember.reader.core.network

/**
 * Ensures a URL has a scheme (defaults to http://).
 */
fun normalizeUrl(url: String): String {
    val trimmed = url.trimEnd('/')
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "http://$trimmed"
    }
}

/**
 * Extracts the origin (scheme://host:port) from a URL.
 * e.g., "http://192.168.0.174:6060/api/v1/opds" → "http://192.168.0.174:6060"
 */
fun serverOrigin(url: String): String {
    val normalized = normalizeUrl(url)
    val schemeEnd = normalized.indexOf("://") + 3
    val pathStart = normalized.indexOf('/', schemeEnd)
    return if (pathStart == -1) normalized else normalized.substring(0, pathStart)
}

/**
 * Resolves a href against a server URL.
 * - Full URLs (http://...) are returned as-is.
 * - Absolute paths (/api/...) are resolved against the origin.
 * - Relative paths are appended to the base URL.
 */
fun resolveUrl(baseUrl: String, href: String): String = when {
    href.startsWith("http") -> href
    href.startsWith("/") -> "${serverOrigin(baseUrl)}$href"
    else -> "${normalizeUrl(baseUrl).trimEnd('/')}/$href"
}

