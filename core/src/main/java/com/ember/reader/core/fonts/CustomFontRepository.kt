package com.ember.reader.core.fonts

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ember.reader.core.coroutine.runCatchingCancellable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

/** The fonts the user has added, kept as files in the app's private storage. */
@Singleton
class CustomFontRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** The directory the font files live in. It may not exist yet. */
    val directory: File = File(context.filesDir, "fonts")

    private val _fonts = MutableStateFlow(scan())
    val fonts: StateFlow<List<CustomFont>> = _fonts.asStateFlow()

    /**
     * Copies the font at [uri] into the app. A font with the same file name is replaced.
     * Fails for anything that is not a TrueType or OpenType file, or is too large.
     */
    suspend fun import(uri: Uri): Result<CustomFont> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val font = displayName(uri)?.let { CustomFont.fromDisplayName(it) }
                ?: throw UnsupportedFontException()
            directory.mkdirs()
            val incoming = File(directory, font.fileName + ".part")
            try {
                val input = context.contentResolver.openInputStream(uri) ?: throw UnsupportedFontException()
                input.use { source ->
                    incoming.outputStream().use { target ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var first = true
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            if (first && !CustomFont.isFontHeader(buffer.copyOf(read))) throw UnsupportedFontException()
                            first = false
                            total += read
                            if (total > MAX_FONT_BYTES) throw UnsupportedFontException()
                            target.write(buffer, 0, read)
                        }
                        if (first) throw UnsupportedFontException()
                    }
                }
                val file = File(directory, font.fileName)
                file.delete()
                if (!incoming.renameTo(file)) throw IllegalStateException("Could not store ${font.fileName}")
            } finally {
                incoming.delete()
            }
            _fonts.value = scan()
            font
        }.onFailure { Timber.w(it, "Fonts: import failed") }
    }

    suspend fun delete(font: CustomFont) = withContext(Dispatchers.IO) {
        File(directory, font.fileName).delete()
        _fonts.value = scan()
    }

    private fun scan(): List<CustomFont> =
        directory.listFiles().orEmpty()
            .filter { it.isFile }
            .mapNotNull { CustomFont.fromDisplayName(it.name)?.copy(fileName = it.name) }
            .sortedBy { it.name.lowercase() }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment

    /** The picked file is not a font Ember can use. */
    class UnsupportedFontException : Exception("Not a TrueType or OpenType font, or larger than 20 MB")

    private companion object {
        const val MAX_FONT_BYTES = 20L * 1024 * 1024
    }
}
