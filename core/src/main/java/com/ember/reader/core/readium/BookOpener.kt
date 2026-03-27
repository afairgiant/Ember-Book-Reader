package com.ember.reader.core.readium

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookOpener @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val httpClient = DefaultHttpClient()

    private val assetRetriever by lazy {
        AssetRetriever(context.contentResolver, httpClient)
    }

    private val publicationOpener by lazy {
        val parser = DefaultPublicationParser(context, httpClient, assetRetriever, PdfiumDocumentFactory(context))
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
}
