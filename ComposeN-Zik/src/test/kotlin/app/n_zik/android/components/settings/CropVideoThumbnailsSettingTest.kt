package app.n_zik.android.components.settings

import android.app.Application
import android.content.Context
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.screens.settings.AppearanceSettings
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.n_zik.android.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the "Crop video thumbnails" row in [AppearanceSettings].
 *
 * The row must be visible for BOTH player types (Essential and Modern) — it used to be
 * rendered only inside the `playerType == PlayerType.Essential` block, while the setting
 * itself (`cropVideoThumbnails`) is applied by `Thumbnail.kt` regardless of the player
 * type, leaving Modern users with an applied setting they could not change.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine
 * on the JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4),
 * with the plain [Application] so the app's heavy init (DI, Room, player) is skipped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@OptIn(ExperimentalAnimationApi::class)
class CropVideoThumbnailsSettingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cropLabel: String = context.getString(R.string.crop_video_thumbnails)

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    /**
     * The app reads `playerType` from the per-profile preferences file; with the default
     * profile (`getActiveProfile` → "default") the file name is "preferences" and the enum
     * is stored as its name (see `putEnum`).
     */
    private fun setPlayerType(value: String) {
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
            .edit()
            .putString(playerTypeKey, value)
            .apply()
    }

    private fun appearanceSettings(playerType: String) {
        setPlayerType(playerType)
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                AppearanceSettings(navController = rememberNavController())
            }
        }
    }

    // `assertExists` (not `assertIsDisplayed`): the row is deep in the settings screen,
    // outside the Robolectric test viewport, so visibility cannot be asserted — but the
    // behavior under test is composition itself: before the fix the row was only
    // composed for the Essential player, so a missing node is exactly the failure mode.
    @Test
    fun cropVideoThumbnailsRowIsComposedWhenPlayerTypeIsModern() {
        appearanceSettings("Modern")

        composeRule.onNodeWithText(cropLabel).assertExists()
    }

    @Test
    fun cropVideoThumbnailsRowIsComposedWhenPlayerTypeIsEssential() {
        appearanceSettings("Essential")

        composeRule.onNodeWithText(cropLabel).assertExists()
    }
}
