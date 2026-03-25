package com.ember.reader.core.readium

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.readium.r2.shared.publication.Publication
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

    private val assetRetriever = AssetRetriever(
        contentResolver = context.contentResolver,
        httpClient = httpClient,
    )

    private val publicationParser = DefaultPublicationParser(
        context = context,
        httpClient = httpClient,
        assetRetriever = assetRetriever,
    )

    private val publicationOpener = PublicationOpener(publicationParser)

    suspend fun open(file: File): Result<Publication> {
        val asset = assetRetriever.retrieve(file)
            .getOrElse { return Result.failure(it) }

        return publicationOpener.open(asset, allowUserInteraction = false)
            .fold(
                onSuccess = { Result.success(it) },
                onFailure = {
                    Timber.e(it, "Failed to open publication: ${file.name}")
                    Result.failure(it)
                },
            )
    }
}
