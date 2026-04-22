package com.ember.reader.ui.reader.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BrightnessGestureTest {

    private val heightPx = 2000f

    @Test
    fun `no movement returns startBrightness`() {
        val result = computeBrightnessFromAnchor(
            startBrightness = 0.4f,
            anchorY = 1000f,
            currentY = 1000f,
            heightPx = heightPx,
        )
        assertEquals(0.4f, result, 0.0001f)
    }

    @Test
    fun `drag up from middle raises brightness proportionally`() {
        val result = computeBrightnessFromAnchor(
            startBrightness = 0.2f,
            anchorY = 1000f,
            currentY = 500f,
            heightPx = heightPx,
        )
        assertEquals(0.7f, result, 0.0001f)
    }

    @Test
    fun `drag down from middle lowers brightness proportionally`() {
        val result = computeBrightnessFromAnchor(
            startBrightness = 0.8f,
            anchorY = 1000f,
            currentY = 1400f,
            heightPx = heightPx,
        )
        assertEquals(0.4f, result, 0.0001f)
    }

    @Test
    fun `clamps to maximum 1_0`() {
        val result = computeBrightnessFromAnchor(
            startBrightness = 0.9f,
            anchorY = 1500f,
            currentY = 0f,
            heightPx = heightPx,
        )
        assertEquals(1.0f, result, 0.0001f)
    }

    @Test
    fun `clamps to minimum 0_01`() {
        val result = computeBrightnessFromAnchor(
            startBrightness = 0.1f,
            anchorY = 500f,
            currentY = 2000f,
            heightPx = heightPx,
        )
        assertEquals(0.01f, result, 0.0001f)
    }

    @Test
    fun `half screen travel covers full range at default sensitivity`() {
        val result = computeBrightnessFromAnchor(
            startBrightness = 0.01f,
            anchorY = 1500f,
            currentY = 500f,
            heightPx = heightPx,
        )
        assertEquals(1.0f, result, 0.0001f)
    }

    @Test
    fun `zero height returns clamped startBrightness`() {
        val result = computeBrightnessFromAnchor(
            startBrightness = 0.5f,
            anchorY = 0f,
            currentY = 0f,
            heightPx = 0f,
        )
        assertEquals(0.5f, result, 0.0001f)
    }

    @Test
    fun `custom sensitivity factor scales travel`() {
        val result = computeBrightnessFromAnchor(
            startBrightness = 0.3f,
            anchorY = 1000f,
            currentY = 500f,
            heightPx = heightPx,
            sensitivityFactor = 1.0f,
        )
        assertEquals(0.55f, result, 0.0001f)
    }
}
