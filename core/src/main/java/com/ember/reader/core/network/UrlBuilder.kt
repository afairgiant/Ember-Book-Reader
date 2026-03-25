package com.ember.reader.core.network

fun buildServerUrl(baseUrl: String, path: String): String =
    "${baseUrl.trimEnd('/')}$path"

fun resolveUrl(baseUrl: String, href: String): String =
    if (href.startsWith("http")) href else buildServerUrl(baseUrl, href)
