package com.ember.reader.core.repository

import android.content.Context
import android.graphics.Bitmap
import com.ember.reader.core.readium.BookOpener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import org.readium.r2.shared.publication.services.cover
import timber.log.Timber

data class BookMetadata(
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val publisher: String? = null,
    val language: String? = null,
    val subjects: String? = null,
    val pageCount: Int? = null,
    val publishedDate: String? = null,
    val description: String? = null
)

@Singleton
class BookMetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookOpener: BookOpener
) {

    private val coversDir: File by lazy {
        File(context.filesDir, "covers").also { it.mkdirs() }
    }

    suspend fun extractMetadata(file: File): BookMetadata {
        val publication = bookOpener.open(file).getOrNull()
            ?: return BookMetadata(file.nameWithoutExtension, null, null)

        val meta = publication.metadata
        val title = meta.title?.takeIf { it.isNotBlank() }
            ?: file.nameWithoutExtension
        val author = meta.authors.firstOrNull()?.name
        val publisher = meta.publishers.firstOrNull()?.name
        val language = meta.languages.firstOrNull()
        val subjects = meta.subjects.map { it.name }.takeIf { it.isNotEmpty() }?.joinToString(", ")
        val pageCount = meta.numberOfPages
        val publishedDate = meta.published?.toString()
        val description = meta.description

        val coverUrl = try {
            val bitmap = publication.cover()
            if (bitmap != null) {
                val coverFile = File(coversDir, "${file.nameWithoutExtension}.jpg")
                FileOutputStream(coverFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                coverFile.toURI().toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract cover from ${file.name}")
            null
        }

        publication.close()
        return BookMetadata(title, author, coverUrl, publisher, language, subjects, pageCount, publishedDate, description)
    }
}
