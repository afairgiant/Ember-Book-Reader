package com.ember.reader.core.model

import java.time.Instant

data class Server(
    val id: Long = 0,
    val name: String,
    val url: String,
    val opdsUsername: String,
    val opdsPassword: String,
    val kosyncUsername: String,
    val kosyncPassword: String,
    val grimmoryUsername: String = "",
    val grimmoryPassword: String = "",
    val isGrimmory: Boolean = false,
    val lastConnected: Instant? = null
) {
    /** Returns the OPDS feed URL — appends the known OPDS path for Grimmory servers. */
    val opdsUrl: String
        get() = if (isGrimmory && "/opds" !in url) {
            url.trimEnd('/') + GRIMMORY_OPDS_PATH
        } else {
            url
        }

    companion object {
        const val GRIMMORY_OPDS_PATH = "/api/v1/opds"
    }
}
