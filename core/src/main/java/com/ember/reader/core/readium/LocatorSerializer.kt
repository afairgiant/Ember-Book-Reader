package com.ember.reader.core.readium

import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

fun Locator.toJsonString(): String =
    toJSON().toString()

fun String.toLocator(): Locator? =
    runCatching { Locator.fromJSON(JSONObject(this)) }.getOrNull()

fun Locator.toPercentage(): Float =
    locations.totalProgression?.toFloat() ?: 0f
