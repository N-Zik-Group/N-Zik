package app.n_zik.android.components

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.DefaultLightColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.ModernBlackColorPalette
import app.n_zik.android.ui.styling.PureBlackColorPalette
import app.n_zik.android.ui.styling.Typography
import app.n_zik.android.utils.activeProfileKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.profilePreferences
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
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

    @Test
    fun `refresh reveals the full Glass container and expressive glyph`() {
        val palette = DefaultLightColorPalette
        val mode = mutableStateOf(GlassAreaMode.ContourOnly)
        val film = mutableStateOf(.5f)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val shape = CircleShape
            CompositionLocalProvider(
                LocalAppearance provides Appearance(palette, Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides GlassSettings(
                    quality = GlassQuality.Performance,
                    film = film.value,
                    areaModes = mapOf(GlassArea.PullToRefresh to mode.value),
                ),
            ) {
                Column {
                    AppPullToRefreshBox(
                        isRefreshing = false,
                        onRefresh = {},
                        modifier = Modifier.size(200.dp).background(Color.Magenta).testTag("idle"),
                    ) {}
                    AppPullToRefreshBox(
                        isRefreshing = true,
                        onRefresh = {},
                        modifier = Modifier.size(200.dp).background(Color.Magenta).testTag("refresh"),
                    ) {}
                }
            }
        }

        fun pixelCount(tag: String, matches: (Int) -> Boolean): Int {
            val pixels = composeRule.onNodeWithTag(tag).captureToImage().toPixelMap()
            return (0 until pixels.height).sumOf { y ->
                (0 until pixels.width).count { x -> matches(pixels[x, y].toArgb()) }
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000)
        assertTrue("hidden indicator draws no pixels", pixelCount("idle") { it != Color.Magenta.toArgb() } == 0)
        val visibleAccent = pixelCount("refresh") { it == palette.accent.toArgb() }
        val nativePixels = pixelCount("refresh") { it != Color.Magenta.toArgb() }
        assertTrue("contour preserves the visible native fill ($visibleAccent pixels)", visibleAccent > 100)
        composeRule.runOnIdle { mode.value = GlassAreaMode.GlassOnly }
        composeRule.mainClock.advanceTimeBy(1_000)
        assertTrue("Glass replaces the native fill", pixelCount("refresh") { it == palette.accent.toArgb() } == 0)
        val glassFill = palette.accent.copy(alpha = .5f).compositeOver(Color.Magenta)
        assertTrue("Glass keeps the accent role", pixelCount("refresh") {
            val color = Color(it)
            kotlin.math.abs(color.red - glassFill.red) < .01f &&
                kotlin.math.abs(color.green - glassFill.green) < .01f &&
                kotlin.math.abs(color.blue - glassFill.blue) < .01f
        } > 100)
        val glyphFill = Color.Black.copy(alpha = .5f).compositeOver(glassFill)
        assertTrue("The inner Glass glyph contrasts with the capsule", pixelCount("refresh") {
            val color = Color(it)
            kotlin.math.abs(color.red - glyphFill.red) < .03f &&
                kotlin.math.abs(color.green - glyphFill.green) < .03f &&
                kotlin.math.abs(color.blue - glyphFill.blue) < .03f
        } > 10)
        val glassPixels = pixelCount("refresh") { it != Color.Magenta.toArgb() }
        assertTrue("Glass retains the full container ($glassPixels of $nativePixels pixels)",
            glassPixels in (nativePixels * .9f).toInt()..(nativePixels * 1.1f).toInt())
        composeRule.runOnIdle { film.value = 0f }
        composeRule.mainClock.advanceTimeBy(1_000)
        assertTrue("Tint with no film retains the native glyph", pixelCount("refresh") { it == Color.Black.toArgb() } > 10)
    }
}
