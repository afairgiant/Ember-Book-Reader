package com.ember.reader.core.sync

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class DeviceIdentity(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ember_device", Context.MODE_PRIVATE)

    val deviceId: String
        get() {
            val existing = prefs.getString(KEY_DEVICE_ID, null)
            if (existing != null) return existing

            val newId = UUID.randomUUID().toString().replace("-", "").uppercase()
            prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
            return newId
        }

    val deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, DEFAULT_DEVICE_NAME) ?: DEFAULT_DEVICE_NAME

    fun setDeviceName(name: String) {
        prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val DEFAULT_DEVICE_NAME = "Ember"
    }
}
