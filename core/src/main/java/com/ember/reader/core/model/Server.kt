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
    val opdsEnabled: Boolean = false,
    val kosyncEnabled: Boolean = false,
    val lastConnected: Instant? = null,
    val canMoveOrganizeFiles: Boolean = false,
    val canDownload: Boolean? = null,
    val canUpload: Boolean? = null,
    val canAccessBookdrop: Boolean? = null,
    val isAdmin: Boolean? = null,
    val permissionsFetchedAt: Instant? = null
) {
    /** Returns the OPDS feed URL — appends the known OPDS path for Grimmory servers. */
    val opdsUrl: String
        get() = if (isGrimmory && "/opds" !in url) {
            url.trimEnd('/') + GRIMMORY_OPDS_PATH
        } else {
            url
        }

    /** True iff OPDS catalog browsing should be attempted for this server. */
    val opdsActive: Boolean get() = opdsEnabled && opdsUsername.isNotBlank()

    /** True iff kosync progress push/pull should be attempted for this server. */
    val kosyncActive: Boolean get() = kosyncEnabled && kosyncUsername.isNotBlank()

    companion object {
        const val GRIMMORY_OPDS_PATH = "/api/v1/opds"
    }
}
