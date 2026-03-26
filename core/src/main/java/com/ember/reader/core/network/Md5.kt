package com.ember.reader.core.network

import java.security.MessageDigest

fun ByteArray.toHexString(): String =
    joinToString("") { "%02x".format(it) }

fun md5Hash(input: String): String {
    val digest = MessageDigest.getInstance("MD5")
    return digest.digest(input.toByteArray()).toHexString()
}
