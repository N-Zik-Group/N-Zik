package app.n_zik.android.components.menu.info

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.ui.screens.info.VideoOrSongInfoResult
import app.it.fast4x.rimusic.ui.screens.info.VideoOrSongInfoScreen
import app.it.fast4x.rimusic.ui.screens.info.loadVideoOrSongInfo
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultLightColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.menu.LocalMenuSheetSurface
import io.mockk.coEvery
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
class InformationMenuSurfaceTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var previousProfile: String? = null
    private val backdrop = Color(0xFF00C853)

    @Before
    fun isolateProfile() {
        previousProfile = context.profilePreferences.getString(activeProfileKey, null)
        setActiveProfile("information_menu_surface_test", context)
        context.preferences.edit().clear().commit()
        GlassStore(context).reset()
    }

    @After
    fun restoreProfile() {
        unmockkAll()
        context.preferences.edit().clear().commit()
        GlassStore(context).reset()
        context.profilePreferences.edit().putString(activeProfileKey, previousProfile).commit()
    }

    @Test
    fun `native information sections preserve theme colors and pitch black bands`() {
        context.preferences.edit().putBoolean(glassEnabledKey, false).commit()
        val palettes = listOf(
            DefaultDarkColorPalette to ColorPaletteMode.Dark,
            DefaultLightColorPalette to ColorPaletteMode.Light,
            ColorPalette.fallback.copy(background0 = Color(0xFF391D47), background1 = Color(0xFF693875)) to ColorPaletteMode.Dark,
            PureBlackColorPalette to ColorPaletteMode.PitchBlack,
            DefaultDarkColorPalette to ColorPaletteMode.PitchBlack,
        )
        val selected = mutableStateOf(0)
        var density = 1f
        composeRule.setContent {
            val palette = palettes[selected.value].first
            CompositionLocalProvider(LocalAppearance provides appearance(palette)) {
                density = LocalDensity.current.density
                Box(Modifier.size(300.dp).background(backdrop).testTag("information")) {
                    InformationMenuSurface {
                        Box(Modifier.fillMaxWidth().height(60.dp).background(informationMenuHeaderColor()))
                        Box(Modifier.fillMaxWidth().height(180.dp).background(informationMenuBodyColor()))
                    }
                }
            }
        }
        palettes.forEachIndexed { index, (palette, mode) ->
            composeRule.runOnIdle {
                context.preferences.edit().putString(colorPaletteModeKey, mode.name).commit()
                selected.value = index
            }
            val pixels = composeRule.onNodeWithTag("information").captureToImage().toPixelMap()
            val pitchBlack = mode == ColorPaletteMode.PitchBlack
            val header = if (pitchBlack) Color(0xFF1A1A1A) else palette.background1
            val body = if (pitchBlack) Color(0xFF101010) else palette.background0
            assertEquals(header.toArgb(), pixels[pixels.width / 2, (30 * density).toInt()].toArgb())
            assertEquals(body.toArgb(), pixels[pixels.width / 2, (120 * density).toInt()].toArgb())
        }
    }

    @Test
    fun `glass information sections each paint only one translucent film`() {
        context.preferences.edit().putBoolean(glassEnabledKey, true)
            .putString(colorPaletteModeKey, ColorPaletteMode.PitchBlack.name).commit()
        var density = 1f
        var header = Color.Unspecified
        var body = Color.Unspecified
        var ownsSurface = false
        val showReference = mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance(PureBlackColorPalette)) {
                density = LocalDensity.current.density
                Box(Modifier.size(300.dp).background(backdrop).testTag("information")) {
                    if (showReference.value) Column {
                        Box(Modifier.fillMaxWidth().height(60.dp).background(header))
                        Box(Modifier.fillMaxWidth().height(180.dp).background(body))
                    } else InformationMenuSurface {
                        ownsSurface = LocalMenuSheetSurface.current
                        header = informationMenuHeaderColor()
                        body = informationMenuBodyColor()
                        Box(Modifier.fillMaxWidth().height(60.dp).background(header))
                        Box(Modifier.fillMaxWidth().height(180.dp).background(body))
                    }
                }
            }
        }
        composeRule.runOnIdle { assertEquals(true, ownsSurface) }
        val pixels = composeRule.onNodeWithTag("information").captureToImage().toPixelMap()
        composeRule.runOnIdle { showReference.value = true }
        val reference = composeRule.onNodeWithTag("information").captureToImage().toPixelMap()
        assertEquals(reference[pixels.width / 2, (30 * density).toInt()].toArgb(),
            pixels[pixels.width / 2, (30 * density).toInt()].toArgb())
        assertEquals(reference[pixels.width / 2, (120 * density).toInt()].toArgb(),
            pixels[pixels.width / 2, (120 * density).toInt()].toArgb())
        assertNotEquals(Color(0xFF101010).toArgb(), body.toArgb())
    }

    @Test
    fun `actual information menu retains local title and playback after loading`() {
        context.preferences.edit().putBoolean(glassEnabledKey, true)
            .putString(colorPaletteModeKey, ColorPaletteMode.PitchBlack.name).commit()
        mockkStatic("app.it.fast4x.rimusic.ui.screens.info.VideoOrSongInfoScreenKt")
        coEvery { loadVideoOrSongInfo(any(), any()) } returns VideoOrSongInfoResult(null, emptyList())
        var playCount = 0
        var density = 1f
        var header = Color.Unspecified
        var body = Color.Unspecified
        val showReference = mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance(PureBlackColorPalette)) {
                density = LocalDensity.current.density
                CompositionLocalProvider(LocalMenuSheetSurface provides true) {
                    header = informationMenuHeaderColor()
                    body = informationMenuBodyColor()
                }
                Box(Modifier.size(320.dp, 600.dp).background(backdrop).testTag("actual-information")) {
                    if (showReference.value) Column {
                        Box(Modifier.fillMaxWidth().height(100.dp).background(header))
                        Box(Modifier.fillMaxWidth().height(500.dp).background(body))
                    } else VideoOrSongInfoScreen(
                        videoId = "information-test", songTitle = "Local information song",
                        onNavigateUp = {}, onClose = {}, onPlay = { playCount++ },
                    )
                }
            }
        }
        composeRule.waitUntil(10_000) {
            // DATA completes on a real dispatcher; apply its state update while the clock is frozen.
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onAllNodesWithText("Local information song").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Local information song").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, playCount) }
        val pixels = composeRule.onNodeWithTag("actual-information").captureToImage().toPixelMap()
        composeRule.runOnIdle { showReference.value = true }
        composeRule.mainClock.advanceTimeByFrame()
        val reference = composeRule.onNodeWithTag("actual-information").captureToImage().toPixelMap()
        val x = (8 * density).toInt()
        assertEquals("the legacy header must not stack a body film below its own film",
            reference[x, (50 * density).toInt()].toArgb(), pixels[x, (50 * density).toInt()].toArgb())
        assertEquals("the legacy body must not mask the shared glass with opaque black",
            reference[x, (550 * density).toInt()].toArgb(), pixels[x, (550 * density).toInt()].toArgb())
    }

    private fun appearance(palette: ColorPalette): Appearance {
        val shape = RoundedCornerShape(24.dp)
        return Appearance(palette, Typography.fallback, shape, shape, shape)
    }
}
