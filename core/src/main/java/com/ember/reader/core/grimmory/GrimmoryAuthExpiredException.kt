package com.ember.reader.core.grimmory

/**
 * Thrown when Grimmory rejects the stored refresh token (400/401/403) or when
 * no refresh token is available. Signals that the user must re-authenticate;
 * stored tokens for [serverId] have already been cleared by the thrower.
 */
class GrimmoryAuthExpiredException(
    val serverId: Long,
    message: String = "Grimmory auth expired for server $serverId"
) : Exception(message)
