package app.n_zik.android.components.settings.appearance

import android.app.Application
import android.content.Context
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
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
import app.it.fast4x.rimusic.utils.showlyricsthumbnailKey
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
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
class AppearanceSettingsWiringTest {
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

    private fun show() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                AppearanceSettings(navController = mockk(relaxed = true))
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun cardsKeepBaselineOrderAndConditionalLyricsVisibility() {
        context.preferences.edit().putBoolean(showlyricsthumbnailKey, false).commit()
        show()
        val cards = composeRule.onAllNodes(SemanticsMatcher("Appearance cards") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("appearance.card.") == true
        }, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(listOf("presets", "cover", "timeline", "colors", "behavior", "lyrics", "actionBar", "fullscreenLyrics", "notification", "reset"),
            cards.map { it.config[SemanticsProperties.TestTag].removePrefix("appearance.card.") })
        composeRule.runOnIdle { context.preferences.edit().putBoolean(showlyricsthumbnailKey, true).commit() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("appearance.card.fullscreenLyrics", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("appearance.card.lyrics", useUnmergedTree = true).assertExists()
    }
}
