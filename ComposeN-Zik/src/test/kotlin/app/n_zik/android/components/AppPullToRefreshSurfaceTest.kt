package app.n_zik.android.components

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultLightColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.ModernBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppPullToRefreshSurfaceTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var previousProfile: String? = null

    @Before
    fun isolateProfile() {
        previousProfile = context.profilePreferences.getString(activeProfileKey, null)
        setActiveProfile("pull_refresh_surface_test", context)
        context.preferences.edit().clear().commit()
    }

    @After
    fun restoreProfile() {
        context.preferences.edit().clear().commit()
        context.profilePreferences.edit().putString(activeProfileKey, previousProfile).commit()
    }

    @Test
    fun `refresh spinner is black on pitch black surfaces and preserves palette containers`() {
        val fixtures = listOf(
            Triple(DefaultDarkColorPalette, ColorPaletteMode.PitchBlack, DefaultDarkColorPalette.accent),
            Triple(PureBlackColorPalette, ColorPaletteMode.Dark, PureBlackColorPalette.accent),
            Triple(ModernBlackColorPalette, ColorPaletteMode.Dark, ModernBlackColorPalette.accent),
            Triple(DefaultDarkColorPalette, ColorPaletteMode.Dark, DefaultDarkColorPalette.accent),
            Triple(DefaultLightColorPalette, ColorPaletteMode.Light, DefaultLightColorPalette.accent),
        )
        val selected = mutableStateOf(0)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val palette = fixtures[selected.value].first
            val shape = CircleShape
            CompositionLocalProvider(LocalAppearance provides Appearance(palette, Typography.fallback, shape, shape, shape)) {
                AppPullToRefreshBox(
                    isRefreshing = true,
                    onRefresh = {},
                    modifier = Modifier.size(200.dp).background(Color.Magenta).testTag("refresh"),
                ) {}
            }
        }
        fixtures.forEachIndexed { index, (palette, mode, expected) ->
            composeRule.runOnIdle {
                context.preferences.edit().putString(colorPaletteModeKey, mode.name).commit()
                selected.value = index
            }
            composeRule.mainClock.advanceTimeBy(1_000)
            val pixels = composeRule.onNodeWithTag("refresh").captureToImage().toPixelMap()
            val counts = mutableMapOf<Int, Int>()
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val argb = pixels[x, y].toArgb()
                counts[argb] = (counts[argb] ?: 0) + 1
            }
            assertTrue("visible $mode refresh container", (counts[expected.toArgb()] ?: 0) > 100)
            val pitchBlack = mode == ColorPaletteMode.PitchBlack || palette === PureBlackColorPalette || palette === ModernBlackColorPalette
            val spinnerColor = if (pitchBlack || !palette.isDark) Color.Black else Color.White
            assertTrue("visible $mode refresh spinner", (counts[spinnerColor.toArgb()] ?: 0) > 10)
        }
    }
}
