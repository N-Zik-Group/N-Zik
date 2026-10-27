package app.n_zik.android.components.settings.appearance

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.PlayerBackgroundColors
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.blurStrengthKey
import app.it.fast4x.rimusic.utils.playerBackgroundColorsKey
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.tab.Search
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
@OptIn(ExperimentalAnimationApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AppearancePresetLoadingTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape, uiRoundnessShape = CircleShape, artistThumbnailShape = CircleShape,
    )

    @Before fun setUp() {
        setActiveProfile("default", context)
        context.preferences.edit().clear().commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
    }

    @Test fun presetOnlyStateIsLoadedOnDemandAndStillAppliesTheSelectedPreset() {
        val tracked = ReadTrackingPreferences(context.preferences)
        composeRule.setContent {
            val activityContext = LocalContext.current
            val wrapper = remember(activityContext) {
                object : ContextWrapper(activityContext) {
                    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                        if (name == "preferences") tracked else super.getSharedPreferences(name, mode)
                }
            }
            CompositionLocalProvider(LocalAppearance provides appearance, LocalContext provides wrapper) {
                AppearancePresetsCard(Search(initialVisible = true))
            }
        }
        composeRule.waitForIdle()
        assertTrue("visible selectors still observe their values", playerTypeKey in tracked.readKeys)
        assertFalse("closed preset chooser must not load its preset-only state", blurStrengthKey in tracked.readKeys)
        assertTrue("opening the page must not apply a preset", context.preferences.all.isEmpty())

        composeRule.onNodeWithText(context.getString(R.string.appearancepresets)).performClick()
        composeRule.waitForIdle()
        assertTrue("opening the chooser loads its state", blurStrengthKey in tracked.readKeys)
        assertTrue("opening the chooser must not apply a preset", context.preferences.all.isEmpty())
        composeRule.onNode(
            hasAnyAncestor(isDialog()) and hasClickAction() and
                hasText(context.getString(R.string.dialog_apply))
        ).performClick()
        composeRule.waitForIdle()
        assertEquals(50f, context.preferences.getFloat(blurStrengthKey, -1f), 0f)
        assertEquals(PlayerBackgroundColors.BlurredCoverColor.name,
            context.preferences.getString(playerBackgroundColorsKey, null))
    }

    private class ReadTrackingPreferences(private val delegate: SharedPreferences) : SharedPreferences by delegate {
        val readKeys = mutableSetOf<String>()
        override fun getString(key: String, defValue: String?): String? {
            readKeys += key
            return delegate.getString(key, defValue)
        }
        override fun getFloat(key: String, defValue: Float): Float {
            readKeys += key
            return delegate.getFloat(key, defValue)
        }
    }
}
