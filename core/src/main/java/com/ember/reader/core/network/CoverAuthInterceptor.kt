package com.ember.reader.core.network

import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Server
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that authenticates image requests to book servers.
 * - Grimmory servers: appends `?token=JWT` (required by CoverJwtFilter)
 * - OPDS servers: adds `Authorization: Basic` header
 *
 * Installed in Coil's ImageLoader so all cover images are authenticated
 * automatically, with no per-screen auth handling needed.
 */
@Singleton
class CoverAuthInterceptor @Inject constructor(
    private val grimmoryTokenManager: GrimmoryTokenManager
) : Interceptor {

    @Volatile
    private var serverAuth: List<ServerAuthEntry> = emptyList()

    fun updateServers(servers: List<Server>) {
        serverAuth = servers.map { server ->
            ServerAuthEntry(
                serverId = server.id,
                origin = serverOrigin(server.url),
                isGrimmory = server.isGrimmory,
                basicAuth = if (!server.isGrimmory && server.opdsUsername.isNotBlank()) {
                    basicAuthHeader(server.opdsUsername, server.opdsPassword)
                } else {
                    null
                }
            )
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        if (request.url.queryParameter("token") != null ||
            request.header("Authorization") != null
        ) {
            return chain.proceed(request)
        }

        for (entry in serverAuth) {
            if (!url.startsWith(entry.origin)) continue

            // Looked up live (not cached at updateServers() time) — the JWT is rotated
            // independently by GrimmoryTokenManager.withAuth() on 401, which doesn't
            // touch the Server row and so wouldn't otherwise be observed here, leaving
            // cover requests silently using a dead token.
            val token = if (entry.isGrimmory) grimmoryTokenManager.getAccessToken(entry.serverId) else null
            if (token != null) {
                val authenticatedUrl = request.url.newBuilder()
                    .addQueryParameter("token", token)
                    .build()
                return chain.proceed(request.newBuilder().url(authenticatedUrl).build())
            } else if (entry.basicAuth != null) {
                return chain.proceed(
                    request.newBuilder()
                        .header("Authorization", entry.basicAuth)
                        .build()
                )
            }
            break
        }

        return chain.proceed(request)
    }

    private data class ServerAuthEntry(
        val serverId: Long,
        val origin: String,
        val isGrimmory: Boolean,
        val basicAuth: String?
    )
}
