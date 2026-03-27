package com.ember.reader.core.network

import android.util.Base64

fun basicAuthHeader(username: String, password: String): String {
    val encoded = Base64.encodeToString(
        "$username:$password".toByteArray(),
        Base64.NO_WRAP
    )
    return "Basic $encoded"
}
