package com.ember.reader.core.grimmory

import kotlinx.serialization.Serializable

/**
 * User record from GET /api/v1/users/me. Ember only parses the fields it needs;
 * defaults apply to anything Grimmory doesn't send or that we haven't modelled.
 *
 * Defaults are false so that an unsuccessful or partial parse fails closed — no
 * action gets enabled unless we have explicit confirmation from the server.
 */
@Serializable
data class GrimmoryUser(
    val id: Long,
    val username: String,
    val permissions: GrimmoryUserPermissions = GrimmoryUserPermissions()
)

@Serializable
data class GrimmoryUserPermissions(
    val isAdmin: Boolean = false,
    val canManageLibrary: Boolean = false,
    val canMoveOrganizeFiles: Boolean = false
)
