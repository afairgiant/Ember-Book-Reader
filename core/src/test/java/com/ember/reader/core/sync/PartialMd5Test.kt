package com.ember.reader.core.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PartialMd5Test {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `compute returns 32-char lowercase hex string`() {
        val file = createTempFile(size = 4096)
        val hash = PartialMd5.compute(file)
        assertEquals(32, hash.length)
        assertEquals(hash, hash.lowercase())
    }

    @Test
    fun `compute is deterministic for same content`() {
        val file = createTempFile(size = 4096)
        assertEquals(PartialMd5.compute(file), PartialMd5.compute(file))
    }

    @Test
    fun `compute differs for different content`() {
        val file1 = createTempFile(size = 4096, fill = 0xAA.toByte())
        val file2 = createTempFile(size = 4096, fill = 0xBB.toByte())
        assertNotEquals(PartialMd5.compute(file1), PartialMd5.compute(file2))
    }

    @Test
    fun `compute handles small file under 1024 bytes`() {
        val file = createTempFile(size = 100)
        val hash = PartialMd5.compute(file)
        assertEquals(32, hash.length)
    }

    @Test
    fun `compute handles file exactly 1024 bytes`() {
        val file = createTempFile(size = 1024)
        val hash = PartialMd5.compute(file)
        assertEquals(32, hash.length)
    }

    @Test
    fun `compute handles large file with multiple sample points`() {
        // Large enough that multiple offsets (1024 << 2*i) are within range
        val file = createTempFile(size = 1024 * 1024)
        val hash = PartialMd5.compute(file)
        assertEquals(32, hash.length)
    }

    @Test
    fun `compute same size files with different data at later offsets differ`() {
        val size = 128 * 1024
        val data1 = ByteArray(size) { (it % 256).toByte() }
        val data2 = data1.copyOf()
        // Modify data at an offset that the algorithm samples (1024 << 2*2 = 16384)
        data2[16384] = (data2[16384] + 1).toByte()
        val file1 = File(tempDir, "diff_offset_1.bin").apply { writeBytes(data1) }
        val file2 = File(tempDir, "diff_offset_2.bin").apply { writeBytes(data2) }
        assertNotEquals(PartialMd5.compute(file1), PartialMd5.compute(file2))
    }

    @Test
    fun `compute with 1-byte file`() {
        val file = createTempFile(size = 1)
        val hash = PartialMd5.compute(file)
        assertEquals(32, hash.length)
    }

    private var fileCounter = 0

    private fun createTempFile(size: Int, fill: Byte = 0x42): File {
        val file = File(tempDir, "test_${fileCounter++}.bin")
        file.writeBytes(ByteArray(size) { fill })
        return file
    }
}
