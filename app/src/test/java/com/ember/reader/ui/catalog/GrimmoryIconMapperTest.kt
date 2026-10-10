package com.ember.reader.ui.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GrimmoryIconMapperTest {

    @Test
    fun `resolves bare Lucide names from Grimmory v3_5`() {
        assertEquals(Icons.Default.Home, GrimmoryIconMapper.resolve("house"))
        assertEquals(Icons.Default.Settings, GrimmoryIconMapper.resolve("settings"))
        assertEquals(Icons.Default.RocketLaunch, GrimmoryIconMapper.resolve("rocket"))
    }

    @Test
    fun `resolves PrimeNG class names from older servers`() {
        assertEquals(Icons.Default.Home, GrimmoryIconMapper.resolve("pi pi-home"))
        assertEquals(Icons.Default.Settings, GrimmoryIconMapper.resolve("pi pi-cog"))
    }

    @Test
    fun `resolves names shared by both icon sets`() {
        assertEquals(Icons.Default.Favorite, GrimmoryIconMapper.resolve("heart"))
        assertEquals(Icons.Default.Favorite, GrimmoryIconMapper.resolve("pi pi-heart"))
    }

    @Test
    fun `returns null for unknown, blank and null names`() {
        assertNull(GrimmoryIconMapper.resolve("no-such-icon"))
        assertNull(GrimmoryIconMapper.resolve(""))
        assertNull(GrimmoryIconMapper.resolve(null))
    }
}
