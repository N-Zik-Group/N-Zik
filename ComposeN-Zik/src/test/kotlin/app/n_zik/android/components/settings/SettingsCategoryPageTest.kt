package app.n_zik.android.components.settings

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.components.settings.common.SettingsCategoryPage
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class SettingsCategoryPageTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape, uiRoundnessShape = CircleShape, artistThumbnailShape = CircleShape,
    )

    @Before fun setUp() {
        setActiveProfile("default", context)
        context.preferences.edit().clear().putString("organization-test-sentinel", "unchanged").commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
    }

    private fun pumpUntil(label: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            composeRule.mainClock.advanceTimeBy(250)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            if (condition()) return
        }
        assertTrue(label, condition())
    }

    @Test fun mixedSourceCategoryHasOneScrollContainerAndTargetsTheActualSetting() {
        val before = context.preferences.all.toMap()
        val target = "ui.swipe_to_action.queue_and_local_playlists_right_swipe"
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                SettingsCategoryPage(
                    categoryId = "behavior",
                    navController = mockk(relaxed = true),
                    target = target,
                    parentTarget = "card.1.swipe_to_action",
                )
            }
        }
        pumpUntil("the far-down setting must be brought into view") {
            runCatching {
                composeRule.onNodeWithTag("settings.target.$target", useUnmergedTree = true).assertIsDisplayed()
            }.isSuccess
        }
        composeRule.onAllNodes(hasScrollAction()).assertCountEquals(1)
        composeRule.onNodeWithTag("settings.target.card.0.app_behavior", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("settings.target.card.0.gestures_events", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("settings.target.card.1.swipe_to_action", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("settings.target.card.0.playback", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("settings.target.card.1.theme", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("search navigation must not write settings", before, context.preferences.all)
    }
}
