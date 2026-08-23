package com.inspiremusic.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppSettingsControllerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs by lazy {
        context.getSharedPreferences(AppSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun clearSettingsBeforeTest() {
        prefs.edit().clear().commit()
    }

    @After
    fun clearSettingsAfterTest() {
        prefs.edit().clear().commit()
    }

    @Test
    fun autoModePersistsAfterItsRenderingTrialStabilizes() {
        val controller = AppSettingsController(context)
        controller.setGlassRenderingMode(GlassRenderingMode.COMPATIBLE)
        controller.setGlassRenderingMode(GlassRenderingMode.AUTO)
        controller.markRenderSessionStable()

        val relaunched = AppSettingsController(context)

        assertEquals(GlassRenderingMode.AUTO, relaunched.settings.value.glassRenderingMode)
        assertFalse(relaunched.recoveredFromUnsafeRendering)
    }

    @Test
    fun unfinishedAutoTrialRecoversToCompatibleOnlyOnce() {
        val controller = AppSettingsController(context)
        controller.setGlassRenderingMode(GlassRenderingMode.COMPATIBLE)
        controller.setGlassRenderingMode(GlassRenderingMode.AUTO)

        val recovered = AppSettingsController(context)
        val nextLaunch = AppSettingsController(context)

        assertEquals(GlassRenderingMode.COMPATIBLE, recovered.settings.value.glassRenderingMode)
        assertTrue(recovered.recoveredFromUnsafeRendering)
        assertEquals(GlassRenderingMode.COMPATIBLE, nextLaunch.settings.value.glassRenderingMode)
        assertFalse(nextLaunch.recoveredFromUnsafeRendering)
    }

    @Test
    fun ordinaryAutoLaunchDoesNotStartAnotherRecoveryTrial() {
        prefs.edit()
            .putString(AppSettingsKeys.GLASS_RENDERING_MODE, GlassRenderingMode.AUTO.value)
            .commit()

        val firstLaunch = AppSettingsController(context)
        val secondLaunch = AppSettingsController(context)

        assertEquals(GlassRenderingMode.AUTO, firstLaunch.settings.value.glassRenderingMode)
        assertEquals(GlassRenderingMode.AUTO, secondLaunch.settings.value.glassRenderingMode)
        assertFalse(secondLaunch.recoveredFromUnsafeRendering)
    }
}
