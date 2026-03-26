package com.ember.reader.core.network

fun buildServerUrl(baseUrl: String, path: String): String {
    val normalized = baseUrl.trimEnd('/')
    val withScheme = if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
        normalized
    } else {
        "http://$normalized"
    }
    return "$withScheme$path"
}

fun resolveUrl(baseUrl: String, href: String): String =
    if (href.startsWith("http")) href else buildServerUrl(baseUrl, href)
