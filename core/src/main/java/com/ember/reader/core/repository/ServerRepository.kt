package com.ember.reader.core.repository

import android.content.Context
import android.graphics.Bitmap
import com.ember.reader.core.database.dao.BookDao
import com.ember.reader.core.database.dao.ServerDao
import com.ember.reader.core.database.entity.ServerEntity
import com.ember.reader.core.database.toDomain
import com.ember.reader.core.database.toEntity
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Server
import com.ember.reader.core.network.CredentialEncryption
import com.ember.reader.core.opds.OpdsClient
import com.ember.reader.core.readium.BookOpener
import com.ember.reader.core.sync.KosyncClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.cover
import timber.log.Timber

@Singleton
class ServerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverDao: ServerDao,
    private val bookDao: BookDao,
    private val bookOpener: BookOpener,
    private val opdsClient: OpdsClient,
    private val kosyncClient: KosyncClient,
    private val grimmoryClient: GrimmoryClient,
    private val grimmoryTokenManager: GrimmoryTokenManager,
    private val credentialEncryption: CredentialEncryption
) {

    fun observeAll(): Flow<List<Server>> =
        serverDao.observeAll().map { entities -> entities.map { it.withPasswords() } }

    suspend fun getAll(): List<Server> = serverDao.getAll().map { it.withPasswords() }

    fun observeById(id: Long): Flow<Server?> = serverDao.observeById(id).map { it?.withPasswords() }

    suspend fun getById(id: Long): Server? = serverDao.getById(id)?.withPasswords()

    suspend fun save(server: Server): Long {
        val id = if (server.id > 0) {
            serverDao.update(server.toEntity())
            server.id
        } else {
            serverDao.insert(server.toEntity())
        }
        credentialEncryption.storePassword(CredentialEncryption.opdsPasswordKey(id), server.opdsPassword)
        credentialEncryption.storePassword(CredentialEncryption.kosyncPasswordKey(id), server.kosyncPassword)
        credentialEncryption.storePassword(grimmoryPasswordKey(id), server.grimmoryPassword)

        // Auto-login to Grimmory if credentials are provided (regardless of isGrimmory flag)
        if (server.grimmoryUsername.isNotBlank() && server.grimmoryPassword.isNotBlank()) {
            Timber.d("Grimmory auto-login: attempting for server $id")
            grimmoryClient.login(server.url, server.grimmoryUsername, server.grimmoryPassword)
                .onSuccess { tokens ->
                    grimmoryTokenManager.storeTokens(id, tokens)
                    // Mark as Grimmory if login succeeds
                    if (!server.isGrimmory) {
                        serverDao.update(server.copy(id = id, isGrimmory = true).toEntity())
                    }
                    Timber.d("Grimmory auto-login: tokens stored for server $id")
                }
                .onFailure { Timber.w(it, "Grimmory auto-login failed for server $id") }
        }

        return id
    }

    suspend fun delete(serverId: Long) {
        // Extract local covers for downloaded books before server deletion
        // (SET_NULL will clear serverId, making server cover URLs inaccessible)
        withContext(Dispatchers.IO) {
            extractLocalCoversForServer(serverId)
        }

        serverDao.deleteById(serverId)
        credentialEncryption.removePassword(CredentialEncryption.opdsPasswordKey(serverId))
        credentialEncryption.removePassword(CredentialEncryption.kosyncPasswordKey(serverId))
        credentialEncryption.removePassword(grimmoryPasswordKey(serverId))
        grimmoryTokenManager.logout(serverId)
    }

    private suspend fun extractLocalCoversForServer(serverId: Long) {
        val coversDir = File(context.filesDir, "covers").also { it.mkdirs() }
        val books = bookDao.getDownloadedBooksForServer(serverId)

        for (entity in books) {
            val localPath = entity.localPath ?: continue
            val file = File(localPath)
            if (!file.exists()) continue
            // Skip if cover is already local
            if (entity.coverUrl?.startsWith("file:") == true) continue

            runCatching {
                val publication = bookOpener.open(file).getOrNull() ?: return@runCatching
                val bitmap = publication.cover()
                if (bitmap != null) {
                    val coverFile = File(coversDir, "${file.nameWithoutExtension}.jpg")
                    FileOutputStream(coverFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                    bookDao.update(entity.copy(coverUrl = coverFile.toURI().toString()))
                }
                publication.close()
            }.onFailure {
                Timber.w(it, "Failed to extract cover for ${entity.title}")
            }
        }
    }

    suspend fun testOpdsConnection(
        url: String,
        username: String,
        password: String
    ): Result<String> = opdsClient.testConnection(url, username, password)

    suspend fun testKosyncConnection(
        url: String,
        username: String,
        password: String
    ): Result<Unit> = kosyncClient.authenticate(url, username, password)

    suspend fun testGrimmoryConnection(
        url: String,
        username: String,
        password: String
    ): Result<String> {
        val tokens = grimmoryClient.login(url, username, password).getOrElse {
            return Result.failure(it)
        }
        return Result.success("Logged in as $username")
    }

    suspend fun detectGrimmory(url: String): Boolean = grimmoryClient.checkHealth(url)

    /** Attempt to re-login to Grimmory using stored credentials. Returns true on success. */
    suspend fun tryGrimmoryRelogin(server: Server): Boolean {
        if (server.grimmoryUsername.isBlank() || server.grimmoryPassword.isBlank()) return false
        return grimmoryClient.login(server.url, server.grimmoryUsername, server.grimmoryPassword)
            .onSuccess { tokens -> grimmoryTokenManager.storeTokens(server.id, tokens) }
            .onFailure { Timber.w(it, "Grimmory re-login failed for server ${server.id}") }
            .isSuccess
    }

    suspend fun updateLastConnected(serverId: Long) {
        serverDao.updateLastConnected(serverId, Instant.now())
    }

    private fun ServerEntity.withPasswords(): Server = toDomain(
        opdsPassword = credentialEncryption.getPassword(CredentialEncryption.opdsPasswordKey(id)) ?: "",
        kosyncPassword = credentialEncryption.getPassword(CredentialEncryption.kosyncPasswordKey(id)) ?: "",
        grimmoryPassword = credentialEncryption.getPassword(grimmoryPasswordKey(id)) ?: ""
    )

    companion object {
        private fun grimmoryPasswordKey(serverId: Long) = "grimmory_password_$serverId"
    }
}
