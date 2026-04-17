package com.ember.reader.core.model

import com.ember.reader.core.testutil.TestFixtures.server
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ServerTest {

    @Nested
    inner class OpdsActive {

        @Test
        fun `true when enabled and username non-blank`() {
            val s = server(opdsEnabled = true).copy(opdsUsername = "alex")
            assertTrue(s.opdsActive)
        }

        @Test
        fun `false when enabled but username blank`() {
            val s = server(opdsEnabled = true).copy(opdsUsername = "")
            assertFalse(s.opdsActive)
        }

        @Test
        fun `false when disabled but username non-blank`() {
            val s = server(opdsEnabled = false).copy(opdsUsername = "alex")
            assertFalse(s.opdsActive)
        }

        @Test
        fun `false when disabled and username blank`() {
            val s = server(opdsEnabled = false).copy(opdsUsername = "")
            assertFalse(s.opdsActive)
        }
    }

    @Nested
    inner class KosyncActive {

        @Test
        fun `true when enabled and username non-blank`() {
            val s = server(kosyncEnabled = true).copy(kosyncUsername = "alex")
            assertTrue(s.kosyncActive)
        }

        @Test
        fun `false when enabled but username blank`() {
            val s = server(kosyncEnabled = true).copy(kosyncUsername = "")
            assertFalse(s.kosyncActive)
        }

        @Test
        fun `false when disabled but username non-blank`() {
            val s = server(kosyncEnabled = false).copy(kosyncUsername = "alex")
            assertFalse(s.kosyncActive)
        }

        @Test
        fun `false when disabled and username blank`() {
            val s = server(kosyncEnabled = false).copy(kosyncUsername = "")
            assertFalse(s.kosyncActive)
        }
    }
}
