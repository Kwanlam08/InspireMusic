package com.inspiremusic.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassRenderingModeTest {
    @Test
    fun legacyFullPreferenceFallsBackToAuto() {
        assertEquals(GlassRenderingMode.AUTO, GlassRenderingMode.fromValue("full"))
    }

    @Test
    fun autoUsesLiveLayerBackdrop() {
        assertEquals(
            OverlayGlassBackend.LAYER_BACKDROP,
            GlassRenderingMode.AUTO.overlayGlassBackend
        )
    }

    @Test
    fun compatibleUsesLiveHazeBackdrop() {
        assertEquals(
            OverlayGlassBackend.HAZE_BACKDROP,
            GlassRenderingMode.COMPATIBLE.overlayGlassBackend
        )
    }
}
