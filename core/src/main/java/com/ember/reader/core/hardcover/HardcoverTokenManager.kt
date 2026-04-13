package com.ember.reader.core.hardcover

import com.ember.reader.core.network.CredentialEncryption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HardcoverTokenManager @Inject constructor(
    private val credentialEncryption: CredentialEncryption
) {

    fun getToken(): String? = credentialEncryption.getPassword(TOKEN_KEY)

    fun storeToken(token: String) {
        // Store as-is — the Hardcover token includes "Bearer " and the API expects it
        credentialEncryption.storePassword(TOKEN_KEY, token.trim())
    }

    fun isConnected(): Boolean = getToken() != null

    fun disconnect() {
        credentialEncryption.removePassword(TOKEN_KEY)
    }

    companion object {
        private const val TOKEN_KEY = "hardcover_api_token"
    }
}
