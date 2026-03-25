package com.ember.reader.core.repository

import com.ember.reader.core.database.dao.ServerDao
import com.ember.reader.core.database.entity.ServerEntity
import com.ember.reader.core.database.toDomain
import com.ember.reader.core.database.toEntity
import com.ember.reader.core.model.Server
import com.ember.reader.core.network.CredentialEncryption
import com.ember.reader.core.opds.OpdsClient
import com.ember.reader.core.sync.KosyncClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepository @Inject constructor(
    private val serverDao: ServerDao,
    private val opdsClient: OpdsClient,
    private val kosyncClient: KosyncClient,
    private val credentialEncryption: CredentialEncryption,
) {

    fun observeAll(): Flow<List<Server>> =
        serverDao.observeAll().map { entities -> entities.map { it.withPasswords() } }

    suspend fun getAll(): List<Server> =
        serverDao.getAll().map { it.withPasswords() }

    fun observeById(id: Long): Flow<Server?> =
        serverDao.observeById(id).map { it?.withPasswords() }

    suspend fun getById(id: Long): Server? =
        serverDao.getById(id)?.withPasswords()

    suspend fun save(server: Server): Long {
        val id = serverDao.insert(server.toEntity())
        credentialEncryption.storePassword(
            CredentialEncryption.opdsPasswordKey(id),
            server.opdsPassword,
        )
        credentialEncryption.storePassword(
            CredentialEncryption.kosyncPasswordKey(id),
            server.kosyncPassword,
        )
        return id
    }

    suspend fun delete(serverId: Long) {
        serverDao.deleteById(serverId)
        credentialEncryption.removePassword(CredentialEncryption.opdsPasswordKey(serverId))
        credentialEncryption.removePassword(CredentialEncryption.kosyncPasswordKey(serverId))
    }

    suspend fun testOpdsConnection(
        url: String,
        username: String,
        password: String,
    ): Result<String> =
        opdsClient.testConnection(url, username, password)

    suspend fun testKosyncConnection(
        url: String,
        username: String,
        password: String,
    ): Result<Unit> =
        kosyncClient.authenticate(url, username, password)

    suspend fun updateLastConnected(serverId: Long) {
        serverDao.updateLastConnected(serverId, Instant.now())
    }

    private fun ServerEntity.withPasswords(): Server = toDomain(
        opdsPassword = credentialEncryption.getPassword(
            CredentialEncryption.opdsPasswordKey(id),
        ) ?: "",
        kosyncPassword = credentialEncryption.getPassword(
            CredentialEncryption.kosyncPasswordKey(id),
        ) ?: "",
    )
}
