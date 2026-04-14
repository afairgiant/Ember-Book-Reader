package com.ember.reader.core.readium

import android.content.Context
import com.ember.reader.core.grimmory.GrimmoryClient
import com.ember.reader.core.grimmory.GrimmoryTokenManager
import com.ember.reader.core.model.Server
import com.ember.reader.core.network.serverOrigin
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.shared.util.http.HttpRequest
import org.readium.r2.shared.util.http.HttpTry
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import timber.log.Timber

@Singleton
class BookOpener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val grimmoryTokenManager: GrimmoryTokenManager,
    private val grimmoryClient: GrimmoryClient
) {

    private val httpClient = DefaultHttpClient()

    private val assetRetriever by lazy {
        AssetRetriever(context.contentResolver, httpClient)
    }

    private val publicationOpener by lazy {
        val parser =
            DefaultPublicationParser(context, httpClient, assetRetriever, PdfiumDocumentFactory(context))
        PublicationOpener(parser)
    }

    suspend fun open(file: File): Result<Publication> {
        val asset = when (val result = assetRetriever.retrieve(file)) {
            is Try.Success -> result.value
            is Try.Failure -> {
                Timber.e("Failed to retrieve asset: ${file.name}")
                return Result.failure(Exception("Failed to retrieve asset: ${result.value}"))
            }
        }

        return when (val result = publicationOpener.open(asset, allowUserInteraction = false)) {
            is Try.Success -> Result.success(result.value)
            is Try.Failure -> {
                Timber.e("Failed to open publication: ${file.name}")
                Result.failure(Exception("Failed to open publication: ${result.value}"))
            }
        }
    }

    /**
     * Opens a publication by streaming it over HTTP from a Grimmory server.
     * Readium reads the archive via range requests — no local file is written.
     *
     * Auth is injected per-request via a [DefaultHttpClient.Callback] that pulls
     * the current token from [GrimmoryTokenManager] and adds it as a Bearer
     * header. Only requests matching the server's origin are augmented; third-party
     * requests (e.g. remote fonts or images referenced by the EPUB) pass through.
     */
    suspend fun openStreaming(
        server: Server,
        grimmoryBookId: Long
    ): Result<Publication> {
        val urlString = grimmoryClient.bookContentUrl(server.url, grimmoryBookId)
        val absoluteUrl = AbsoluteUrl(urlString)
            ?: return Result.failure(IllegalArgumentException("Invalid streaming URL: $urlString"))
        val origin = serverOrigin(server.url)
        val serverId = server.id

        val authedHttpClient = DefaultHttpClient(
            callback = object : DefaultHttpClient.Callback {
                override suspend fun onStartRequest(request: HttpRequest): HttpTry<HttpRequest> {
                    if (!request.url.toString().startsWith(origin)) return Try.success(request)
                    val token = grimmoryTokenManager.getAccessToken(serverId)
                        ?: return Try.success(request)
                    return Try.success(
                        request.copy { setHeader("Authorization", "Bearer $token") }
                    )
                }

                override suspend fun onRecoverRequest(
                    request: HttpRequest,
                    error: HttpError
                ): HttpTry<HttpRequest> {
                    // `withAuth` coalesces concurrent refreshes across the app so
                    // parallel Readium subresource fetches don't each spawn one.
                    if (error !is HttpError.ErrorResponse || error.status.code != 401) {
                        return Try.failure(error)
                    }
                    val refreshed = grimmoryTokenManager.withAuth(server.url, serverId) { it }
                        .getOrNull() ?: return Try.failure(error)
                    return Try.success(
                        request.copy { setHeader("Authorization", "Bearer $refreshed") }
                    )
                }
            }
        )
        val streamingRetriever = AssetRetriever(context.contentResolver, authedHttpClient)
        val streamingParser = DefaultPublicationParser(
            context,
            authedHttpClient,
            streamingRetriever,
            PdfiumDocumentFactory(context)
        )
        val streamingOpener = PublicationOpener(streamingParser)

        val asset = when (val result = streamingRetriever.retrieve(absoluteUrl)) {
            is Try.Success -> result.value
            is Try.Failure -> {
                Timber.e("Streaming: failed to retrieve asset at $urlString: ${result.value}")
                return Result.failure(Exception("Failed to retrieve asset: ${result.value}"))
            }
        }

        return when (val result = streamingOpener.open(asset, allowUserInteraction = false)) {
            is Try.Success -> Result.success(result.value)
            is Try.Failure -> {
                Timber.e("Streaming: failed to open publication: ${result.value}")
                Result.failure(Exception("Failed to open publication: ${result.value}"))
            }
        }
    }
}
