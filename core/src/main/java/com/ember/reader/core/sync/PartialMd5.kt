package com.ember.reader.core.sync

import com.ember.reader.core.network.toHexString
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Computes a partial MD5 hash matching KOReader's algorithm.
 * Reads 1024-byte samples at exponentially increasing offsets.
 */
object PartialMd5 {

    private const val SAMPLE_SIZE = 1024
    private const val STEP = 1024L

    fun compute(file: File): String = RandomAccessFile(file, "r").use { raf ->
        digestSamples { offset, size ->
            if (offset >= raf.length()) return@digestSamples null
            raf.seek(offset)
            val buffer = ByteArray(size)
            val bytesRead = raf.read(buffer)
            if (bytesRead <= 0) null else buffer.copyOf(bytesRead)
        }
    }

    /**
     * The same hash for a file that is not on the device, such as a book streamed from a
     * server. [readSample] gives up to `size` bytes from `offset`, or null when `offset` is at
     * or past the end of the file.
     */
    suspend fun compute(readSample: suspend (offset: Long, size: Int) -> ByteArray?): String =
        digestSamples { offset, size -> readSample(offset, size) }

    /** KOReader's sampling: it stops at the first offset past the end of the file. */
    private inline fun digestSamples(readSample: (offset: Long, size: Int) -> ByteArray?): String {
        val digest = MessageDigest.getInstance("MD5")
        for (i in -1..10) {
            // For i = -1 the shift wraps round to offset 0, as it does in KOReader.
            val offset = STEP shl (2 * i)
            val sample = readSample(offset, SAMPLE_SIZE) ?: break
            if (sample.isEmpty()) break
            digest.update(sample, 0, minOf(sample.size, SAMPLE_SIZE))
        }
        return digest.digest().toHexString()
    }
}
