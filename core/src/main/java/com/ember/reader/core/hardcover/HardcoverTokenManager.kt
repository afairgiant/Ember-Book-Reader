package com.ember.reader.core.hardcover

import com.ember.reader.core.network.CredentialEncryption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HardcoverTokenManager @Inject constructor(
    private val credentialEncryption: CredentialEncryption,
) {

    fun getToken(): String? = credentialEncryption.getPassword(TOKEN_KEY)

    fun storeToken(token: String) {
        // Strip "Bearer " prefix if user pasted it from the Hardcover UI
        val cleanToken = token.removePrefix("Bearer ").trim()
        credentialEncryption.storePassword(TOKEN_KEY, cleanToken)
    }

    fun isConnected(): Boolean = getToken() != null

    fun disconnect() {
        credentialEncryption.removePassword(TOKEN_KEY)
    }

    companion object {
        private const val TOKEN_KEY = "hardcover_api_token"
    }
}
