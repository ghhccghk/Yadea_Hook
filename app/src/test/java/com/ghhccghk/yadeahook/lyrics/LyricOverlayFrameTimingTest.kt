package com.ghhccghk.yadeahook.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricOverlayFrameTimingTest {
    @Test
    fun frameIntervalUsesNormalizedFrameRate() {
        assertEquals(200L, calculateFrameIntervalMs(5f))
        assertEquals(33L, calculateFrameIntervalMs(30f))
        assertEquals(16L, calculateFrameIntervalMs(60f))
        assertEquals(200L, calculateFrameIntervalMs(1f))
        assertEquals(16L, calculateFrameIntervalMs(120f))
    }

    @Test
    fun nonFiniteFrameRateFallsBackToDefault() {
        assertEquals(33L, calculateFrameIntervalMs(Float.NaN))
        assertEquals(33L, calculateFrameIntervalMs(Float.POSITIVE_INFINITY))
        assertEquals(33L, calculateFrameIntervalMs(Float.NEGATIVE_INFINITY))
    }
}
