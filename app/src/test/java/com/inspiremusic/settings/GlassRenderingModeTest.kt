package com.inspiremusic.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassRenderingModeTest {
    @Test
    fun legacyFullPreferenceFallsBackToAuto() {
        assertEquals(GlassRenderingMode.AUTO, GlassRenderingMode.fromValue("full"))
    }
}
