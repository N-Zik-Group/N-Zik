package app.n_zik.android.components.menu

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
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
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultLightColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.ModernBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.setActiveProfile
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
class MenuSurfaceRenderingTest {
    @get:Rule val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var previousProfile: String? = null
    private val elevatedPitchBlack = Color(0xFF1A1A1A)
    private val recessedPitchBlack = Color(0xFF101010)

    private data class Fixture(
        val name: String,
        val palette: ColorPalette,
        val mode: ColorPaletteMode = ColorPaletteMode.Dark,
        val pitchBlack: Boolean = false,
    )

    private val fixtures = listOf(
        Fixture("dark", DefaultDarkColorPalette),
        Fixture("light", DefaultLightColorPalette, ColorPaletteMode.Light),
        Fixture("dynamic", dynamicColorPaletteOf(Color(0xFF3278AB), true)),
        Fixture("custom", ColorPalette.fallback.copy(background0 = Color(0xFF391D47), background1 = Color(0xFF693875))),
        Fixture("pitch mode", DefaultDarkColorPalette.copy(), ColorPaletteMode.PitchBlack, true),
        Fixture("pure black identity", PureBlackColorPalette, pitchBlack = true),
        Fixture("modern black identity", ModernBlackColorPalette, pitchBlack = true),
    )

    @Before
    fun isolateProfile() {
        previousProfile = context.profilePreferences.getString(activeProfileKey, null)
        setActiveProfile("menu_surface_rendering_test", context)
        context.preferences.edit().clear().commit()
    }

    @After
    fun restoreProfile() {
        context.preferences.edit().clear().commit()
        context.profilePreferences.edit().putString(activeProfileKey, previousProfile).commit()
    }

    @Test
    fun `grid menu renders separate tones and retains pitch black elevated surfaces`() {
        verifySurfaces(grid = true)
    }

    @Test
    fun `list menu renders separate tones and retains pitch black elevated surfaces`() {
        verifySurfaces(grid = false)
    }

    @Test
    fun `normal headers and headerless menus retain rounded top clipping over a backdrop`() {
        val selected = mutableStateOf(true to true)
        val backdrop = Color(0xFF00C853)
        val palette = ColorPalette.fallback.copy(
            background0 = Color(0xFF391D47),
            background1 = Color(0xFF693875),
        )
        val shape = RoundedCornerShape(32.dp)
        val appearance = Appearance(
            colorPalette = palette,
            typography = Typography.fallback,
            thumbnailShape = shape,
            uiRoundnessShape = shape,
            artistThumbnailShape = shape,
        )
        context.preferences.edit().putString(colorPaletteModeKey, ColorPaletteMode.Dark.name).commit()
        composeRule.setContent {
            val (grid, hasHeader) = selected.value
            key(grid, hasHeader) {
                CompositionLocalProvider(LocalAppearance provides appearance) {
                    Box(Modifier.size(300.dp).background(backdrop).testTag("rounded-menu")) {
                        if (grid) {
                            GridMenu.Menu(showDragHandle = hasHeader, title = if (hasHeader) "Menu title" else null) {}
                        } else {
                            ListMenu.Menu(showDragHandle = hasHeader, title = if (hasHeader) "Menu title" else null) {}
                        }
                    }
                }
            }
        }

        for (grid in listOf(true, false)) {
            for (hasHeader in listOf(true, false)) {
                composeRule.runOnIdle { selected.value = grid to hasHeader }
                val pixels = composeRule.onNodeWithTag("rounded-menu").captureToImage().toPixelMap()
                val label = "grid=$grid, header=$hasHeader"
                assertEquals("$label: clipped top left", backdrop.toArgb(), pixels[1, 1].toArgb())
                assertEquals("$label: clipped top right", backdrop.toArgb(), pixels[pixels.width - 2, 1].toArgb())
                assertEquals(
                    "$label: top tone outside title and handle",
                    (if (hasHeader) palette.background1 else palette.background0).toArgb(),
                    pixels[pixels.width / 4, pixels.height / 20].toArgb(),
                )
                assertEquals("$label: body", palette.background0.toArgb(), pixels[pixels.width / 2, pixels.height / 3].toArgb())
                assertEquals("$label: square bottom container", palette.background0.toArgb(), pixels[1, pixels.height - 2].toArgb())
            }
        }
    }

    private fun verifySurfaces(grid: Boolean) {
        val selected = mutableStateOf(fixtures.first() to true)
        context.preferences.edit().putString(colorPaletteModeKey, fixtures.first().mode.name).commit()
        composeRule.setContent {
            val (fixture, hasHeader) = selected.value
            key(fixture.name, hasHeader) {
                val shape = RoundedCornerShape(0.dp)
                val appearance = Appearance(
                    colorPalette = fixture.palette,
                    typography = Typography.fallback,
                    thumbnailShape = shape,
                    uiRoundnessShape = shape,
                    artistThumbnailShape = shape,
                )
                CompositionLocalProvider(LocalAppearance provides appearance) {
                    Box(Modifier.size(300.dp).testTag("menu")) {
                        if (grid) {
                            GridMenu.Menu(
                                showDragHandle = false,
                                headerTrailing = if (hasHeader) ({ Box(Modifier.size(48.dp)) }) else null,
                            ) {}
                        } else {
                            ListMenu.Menu(
                                showDragHandle = false,
                                headerTrailing = if (hasHeader) ({ Box(Modifier.size(48.dp)) }) else null,
                            ) {}
                        }
                    }
                }
            }
        }

        for (fixture in fixtures) {
            if (!fixture.pitchBlack) {
                assertNotEquals(fixture.name, fixture.palette.background0, fixture.palette.background1)
            }
            for (hasHeader in listOf(true, false)) {
                composeRule.runOnIdle {
                    context.preferences.edit().putString(colorPaletteModeKey, fixture.mode.name).commit()
                    selected.value = fixture to hasHeader
                }
                val pixels = composeRule.onNodeWithTag("menu").captureToImage().toPixelMap()
                val body = if (fixture.pitchBlack) recessedPitchBlack else fixture.palette.background0
                val header = if (fixture.pitchBlack) elevatedPitchBlack else fixture.palette.background1
                assertEquals(
                    "${fixture.name}, header=$hasHeader: body",
                    body.toArgb(), pixels[pixels.width / 2, pixels.height / 3].toArgb(),
                )
                assertEquals(
                    "${fixture.name}, header=$hasHeader: top",
                    (if (hasHeader) header else body).toArgb(),
                    pixels[pixels.width / 2, pixels.height / 20].toArgb(),
                )
            }
        }
    }
}
