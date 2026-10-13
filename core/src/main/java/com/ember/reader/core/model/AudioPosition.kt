package com.ember.reader.core.model

import org.json.JSONObject

/**
 * A place in an audiobook: [positionMs] into the track at [trackIndex]. A book that is one
 * file has no track index. This is what an audiobook's bookmark holds where an EPUB's holds
 * a locator, and it is how Grimmory stores one too.
 */
data class AudioPosition(val positionMs: Long, val trackIndex: Int?) {

    /** As stored in a bookmark's `locatorJson`. */
    fun toLocatorJson(): String = JSONObject().apply {
        put(KEY_TYPE, TYPE_AUDIOBOOK)
        put(KEY_POSITION, positionMs)
        trackIndex?.let { put(KEY_TRACK, it) }
    }.toString()

    /** Whether [other] is close enough to be the same bookmark; Grimmory allows one every five seconds. */
    fun isNear(other: AudioPosition): Boolean =
        trackIndex == other.trackIndex && kotlin.math.abs(positionMs - other.positionMs) < NEAR_MS

    companion object {
        const val NEAR_MS = 5_000L
        private const val KEY_TYPE = "type"
        private const val KEY_POSITION = "positionMs"
        private const val KEY_TRACK = "trackIndex"
        private const val TYPE_AUDIOBOOK = "audiobook"

        /** The position a bookmark's `locatorJson` holds; null when it is not an audiobook's. */
        fun fromLocatorJson(locatorJson: String): AudioPosition? = runCatching {
            val json = JSONObject(locatorJson)
            if (json.optString(KEY_TYPE) != TYPE_AUDIOBOOK || !json.has(KEY_POSITION)) return@runCatching null
            AudioPosition(
                positionMs = json.getLong(KEY_POSITION),
                trackIndex = if (json.has(KEY_TRACK)) json.getInt(KEY_TRACK) else null
            )
        }.getOrNull()
    }
}
