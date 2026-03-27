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
    val lastConnected: Instant? = null
)
