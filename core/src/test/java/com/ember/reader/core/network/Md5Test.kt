package com.ember.reader.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Md5Test {

    // --- ByteArray.toHexString ---

    @Test
    fun `toHexString converts empty array`() {
        assertEquals("", byteArrayOf().toHexString())
    }

    @Test
    fun `toHexString converts single byte`() {
        assertEquals("ff", byteArrayOf(0xFF.toByte()).toHexString())
    }

    @Test
    fun `toHexString pads leading zeros`() {
        assertEquals("000a", byteArrayOf(0x00, 0x0A).toHexString())
    }

    @Test
    fun `toHexString converts arbitrary bytes`() {
        assertEquals(
            "deadbeef",
            byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()).toHexString(),
        )
    }

    // --- md5Hash ---

    @Test
    fun `md5Hash of empty string`() {
        // well-known MD5 of ""
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5Hash(""))
    }

    @Test
    fun `md5Hash of hello world`() {
        // well-known MD5 of "hello world"
        assertEquals("5eb63bbbe01eeed093cb22bb8f5acdc3", md5Hash("hello world"))
    }

    @Test
    fun `md5Hash of password string`() {
        // well-known MD5 of "password"
        assertEquals("5f4dcc3b5aa765d61d8327deb882cf99", md5Hash("password"))
    }

    @Test
    fun `md5Hash produces 32 character lowercase hex`() {
        val hash = md5Hash("anything")
        assertEquals(32, hash.length)
        assertEquals(hash, hash.lowercase())
    }
}
