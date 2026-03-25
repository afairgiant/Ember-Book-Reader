package com.ember.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val opdsUsername: String,
    val opdsPassword: String,
    val kosyncUsername: String,
    val kosyncPassword: String,
    val lastConnected: Instant? = null,
)
