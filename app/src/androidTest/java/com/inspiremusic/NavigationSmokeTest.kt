package com.inspiremusic

import android.Manifest
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.rule.GrantPermissionRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NavigationSmokeTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_AUDIO)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun waitForAppChrome() {
        waitForTag("bottom_nav_library")
    }

    @Test
    fun criticalLibraryPagesOpenWithoutCrashing() {
        composeRule.onNodeWithTag("bottom_nav_library").performClick()
        waitForTag("screen_library")

        listOf(
            "library_playlists" to "screen_playlists",
            "library_artists" to "screen_artists",
            "library_albums" to "screen_albums",
            "library_songs" to "screen_songs",
            "library_favorites" to "screen_favorites"
        ).forEach { (entryTag, screenTag) ->
            composeRule.onNodeWithTag(entryTag).performClick()
            waitForTag(screenTag)
            composeRule.runOnUiThread {
                composeRule.activity.onBackPressedDispatcher.onBackPressed()
            }
            waitForTag("screen_library")
        }
    }

    @Test
    fun diaryMemoriesAndSettingsOpenWithoutCrashing() {
        composeRule.onNodeWithTag("bottom_nav_diary").performClick()
        waitForTag("screen_diary")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.diary_memory)).performClick()
        waitForTag("screen_memories")

        composeRule.onNodeWithTag("bottom_nav_settings").performClick()
        waitForTag("screen_settings")
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.settings_glass_rendering_compatible)
        ).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.settings_glass_rendering_auto)
        ).performClick()
        composeRule.waitForIdle()
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertExists()
    }
}
