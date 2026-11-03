package app.n_zik.android.components.settings.ui

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.GlassTintSource
import app.n_zik.android.components.glass.glassFilmKey
import app.n_zik.android.components.glass.rememberGlassSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GlassTintSourceAvailabilityTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store by lazy { GlassStore(context) }
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = RoundedCornerShape(12.dp),
        uiRoundnessShape = RoundedCornerShape(16.dp),
        artistThumbnailShape = RoundedCornerShape(12.dp),
    )

    @Before fun setUp() {
        setActiveProfile("default", context)
        context.preferences.edit().clear().commit()
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE).edit().clear().commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        store.setFloat(glassFilmKey, .263f)
    }

    @Test
    @Config(sdk = [24, 30])
    fun `unsupported stored system accent displays none and only explicit selection changes storage`() {
        store.setTint(GlassTintSource.SystemAccent)
        val before = store.read()
        showTintRow()
        composeRule.onNodeWithText(context.getString(R.string.settings_current_value, context.getString(R.string.glass_effect_tint_none))).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.glass_effect_tint_source_system_accent)).assertDoesNotExist()
        assertEquals(before, store.read())

        openSelector()
        composeRule.onNodeWithText(context.getString(R.string.glass_effect_tint_source_system_accent)).assertDoesNotExist()
        supportedLabels.forEach { label ->
            composeRule.onAllNodesWithText(context.getString(label)).onLast().performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText(context.getString(R.string.common_color_source_artwork)).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(before.copy(source = GlassTintSource.Artwork), store.read())
        composeRule.onNodeWithText(context.getString(R.string.settings_current_value, context.getString(R.string.common_color_source_artwork))).assertIsDisplayed()
    }

    @Test
    @Config(sdk = [31, 33])
    fun `supported devices offer and persist system accent alongside every other tint source`() {
        val before = store.read()
        showTintRow()
        openSelector()
        (supportedLabels + R.string.glass_effect_tint_source_system_accent).forEach { label ->
            composeRule.onAllNodesWithText(context.getString(label)).onLast().performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText(context.getString(R.string.glass_effect_tint_source_system_accent)).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(before.copy(source = GlassTintSource.SystemAccent), store.read())
        composeRule.onNodeWithText(context.getString(R.string.settings_current_value, context.getString(R.string.glass_effect_tint_source_system_accent))).assertIsDisplayed()
    }

    private fun showTintRow() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                val config: GlassSettings = rememberGlassSettings()
                Column {
                    TintRow(config, store::setTint, store::setColor) { }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun openSelector() {
        composeRule.onNodeWithTag("settings.glass.tintSource").performClick()
    }

    private val supportedLabels = listOf(
        R.string.glass_effect_tint_none,
        R.string.common_color_source_theme,
        R.string.common_color_source_artwork,
        R.string.glass_effect_tint_source_custom,
    )
}
