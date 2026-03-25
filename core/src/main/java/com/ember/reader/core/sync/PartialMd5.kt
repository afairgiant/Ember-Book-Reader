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

    fun compute(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        val step = 1024L

        RandomAccessFile(file, "r").use { raf ->
            for (i in -1..10) {
                val offset = step shl (2 * i)
                if (offset >= raf.length()) break

                raf.seek(offset)
                val buffer = ByteArray(SAMPLE_SIZE)
                val bytesRead = raf.read(buffer)
                if (bytesRead <= 0) break

                if (bytesRead < SAMPLE_SIZE) {
                    digest.update(buffer, 0, bytesRead)
                } else {
                    digest.update(buffer)
                }
            }
        }

        return digest.digest().toHexString()
    }
}
