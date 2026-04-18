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
    val kosyncUsername: String,
    val grimmoryUsername: String = "",
    val isGrimmory: Boolean = false,
    val opdsEnabled: Boolean = false,
    val kosyncEnabled: Boolean = false,
    val lastConnected: Instant? = null,
    val canMoveOrganizeFiles: Boolean = false,
    val canDownload: Boolean? = null,
    val canUpload: Boolean? = null,
    val canAccessBookdrop: Boolean? = null,
    val isAdmin: Boolean? = null,
    val permissionsFetchedAt: Instant? = null,
    val accentColorSlot: Int? = null
)
