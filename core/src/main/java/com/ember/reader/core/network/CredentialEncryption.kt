package com.ember.reader.core.network

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialEncryption(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ember_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun storePassword(key: String, password: String) {
        prefs.edit().putString(key, password).apply()
    }

    fun getPassword(key: String): String? = prefs.getString(key, null)

    fun removePassword(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        fun opdsPasswordKey(serverId: Long): String = "opds_password_$serverId"
        fun kosyncPasswordKey(serverId: Long): String = "kosync_password_$serverId"
    }
}
