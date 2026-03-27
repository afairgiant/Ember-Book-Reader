package com.ember.reader.core.grimmory

import com.ember.reader.core.network.CredentialEncryption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrimmoryTokenManager @Inject constructor(
    private val credentialEncryption: CredentialEncryption
) {

    fun getAccessToken(serverId: Long): String? =
        credentialEncryption.getPassword(accessTokenKey(serverId))

    fun getRefreshToken(serverId: Long): String? =
        credentialEncryption.getPassword(refreshTokenKey(serverId))

    fun storeTokens(serverId: Long, tokens: GrimmoryTokens) {
        credentialEncryption.storePassword(accessTokenKey(serverId), tokens.accessToken)
        credentialEncryption.storePassword(refreshTokenKey(serverId), tokens.refreshToken)
    }

    fun isLoggedIn(serverId: Long): Boolean = getAccessToken(serverId) != null

    fun logout(serverId: Long) {
        credentialEncryption.removePassword(accessTokenKey(serverId))
        credentialEncryption.removePassword(refreshTokenKey(serverId))
    }

    companion object {
        private fun accessTokenKey(serverId: Long) = "grimmory_access_$serverId"
        private fun refreshTokenKey(serverId: Long) = "grimmory_refresh_$serverId"
    }
}
