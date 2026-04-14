package com.ember.reader.core.repository

import android.content.Context
import android.graphics.Bitmap
import com.ember.reader.core.database.dao.BookDao
import com.ember.reader.core.database.dao.ServerDao
import com.ember.reader.core.database.entity.ServerEntity
import com.ember.reader.core.database.toDomain
import com.ember.reader.core.database.toEntity
import com.ember.reader.core.grimmory.GrimmoryAppClient
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.grimmory.GrimmoryUser
import com.ember.reader.core.model.Server
import com.ember.reader.core.network.CredentialEncryption
import com.ember.reader.core.opds.OpdsClient
import com.ember.reader.core.readium.BookOpener
import com.ember.reader.core.sync.KosyncClient
import com.ember.reader.core.sync.SyncStatusRepository
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
    private val grimmoryAppClient: GrimmoryAppClient,
    private val grimmoryTokenManager: GrimmoryTokenManager,
    private val credentialEncryption: CredentialEncryption,
    private val syncStatusRepository: SyncStatusRepository
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
                    // Fetch the user's permission flags so admin-only actions (e.g.
                    // Organize Files) show up without the user having to configure
                    // anything. Failing closed: on error the flag stays at its default.
                    refreshGrimmoryPermissions(id, server.url)
                }
                .onFailure { Timber.w(it, "Grimmory auto-login failed for server $id") }
        }

        return id
    }

    /**
     * Re-fetches the current user's [com.ember.reader.core.grimmory.GrimmoryUserPermissions]
     * for a single Grimmory server and persists the permission flags + fetch timestamp
     * to the server record. Silent — any network or auth failure is logged and swallowed,
     * leaving previous values intact.
     *
     * Call this on app start for each Grimmory server, after login, on every server probe,
     * and on a 403 from a download/organize action so the cached value can self-correct.
     */
    suspend fun refreshGrimmoryPermissions(serverId: Long) {
        val entity = serverDao.getById(serverId) ?: return
        if (!entity.isGrimmory) return
        refreshGrimmoryPermissions(serverId, entity.url)
    }

    private suspend fun refreshGrimmoryPermissions(serverId: Long, baseUrl: String) {
        runCatching {
            val user = grimmoryAppClient.getCurrentUser(baseUrl, serverId).getOrNull() ?: return
            persistGrimmoryPermissions(serverId, user)
        }.onFailure { e ->
            Timber.w(e, "Failed to refresh Grimmory permissions for server $serverId")
        }
    }

    /**
     * Writes fetched permissions to the Server row. Skips the write when no flag
     * changed so Room doesn't trigger downstream `observeAll()` re-emissions on
     * every probe tick.
     */
    suspend fun persistGrimmoryPermissions(serverId: Long, user: GrimmoryUser) {
        val existing = serverDao.getById(serverId) ?: return
        val perms = user.permissions
        val unchanged = existing.canMoveOrganizeFiles == perms.canMoveOrganizeFiles &&
            existing.canDownload == perms.canDownload &&
            existing.canUpload == perms.canUpload &&
            existing.canAccessBookdrop == perms.canAccessBookdrop &&
            existing.isAdmin == perms.isAdmin
        if (unchanged) return
        serverDao.updateGrimmoryPermissions(
            id = serverId,
            canMoveOrganizeFiles = perms.canMoveOrganizeFiles,
            canDownload = perms.canDownload,
            canUpload = perms.canUpload,
            canAccessBookdrop = perms.canAccessBookdrop,
            isAdmin = perms.isAdmin,
            fetchedAt = Instant.now()
        )
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
        syncStatusRepository.clear(serverId)
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
    ): Result<GrimmoryUser> {
        val tokens = grimmoryClient.login(url, username, password).getOrElse {
            return Result.failure(it)
        }
        return grimmoryAppClient.fetchCurrentUser(url, tokens.accessToken)
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
