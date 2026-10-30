package app.n_zik.android.components.menu

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.glass.glassFilmKey
import app.n_zik.android.components.glass.glassTintStrengthKey
import app.n_zik.android.components.menu.song.SongMenuSurface
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
class MenuSheetSurfaceTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var previousProfile: String? = null

    @Before
    fun isolateProfile() {
        previousProfile = context.profilePreferences.getString(activeProfileKey, null)
        setActiveProfile("media_menu_sheet_surface_test", context)
        context.preferences.edit().clear().commit()
        GlassStore(context).reset()
    }

    @After
    fun restoreProfile() {
        context.preferences.edit().clear().commit()
        GlassStore(context).reset()
        context.profilePreferences.edit().putString(activeProfileKey, previousProfile).commit()
    }

    @Test
    fun `song sheet tints both native sections and owns the header material`() {
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        GlassStore(context).apply {
            setColor(Color.Red.toArgb())
            setFloat(glassTintStrengthKey, 1f)
            setFloat(glassFilmKey, 1f)
        }
        var headerHasSurface = false
        var bodyHasSurface = false
        var density = 1f
        val shape = RoundedCornerShape(24.dp)
        val appearance = Appearance(
            colorPalette = ColorPalette.fallback, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                density = LocalDensity.current.density
                Box(Modifier.size(300.dp).background(Color.Green).testTag("song-sheet")) {
                    SongMenuSurface(header = {
                        headerHasSurface = LocalMenuSheetSurface.current
                        Box(Modifier.fillMaxWidth().height(60.dp))
                    }) {
                        bodyHasSurface = LocalMenuSheetSurface.current
                        Box(Modifier.fillMaxWidth().height(180.dp))
                    }
                }
            }
        }
        composeRule.runOnIdle {
            assertEquals(true, headerHasSurface)
            assertEquals(true, bodyHasSurface)
        }
        val pixels = composeRule.onNodeWithTag("song-sheet").captureToImage().toPixelMap()
        for (y in listOf(30, 120)) {
            assertEquals("native section at y=$y follows global tint", Color.Red.toArgb(),
                pixels[pixels.width / 2, (y * density).toInt()].toArgb())
        }
    }

    @Test
    fun `custom media header and either action layout share one glass surface`() {
        val selected = mutableStateOf(true to true)
        val backdrop = Color(0xFF00C853)
        val palette = ColorPalette.fallback.copy(
            background0 = Color(0xFF391D47), background1 = Color(0xFF693875),
        )
        val shape = RoundedCornerShape(32.dp)
        val appearance = Appearance(
            colorPalette = palette, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        var density = 1f
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        composeRule.setContent {
            val (grid, glass) = selected.value
            key(grid, glass) {
                CompositionLocalProvider(LocalAppearance provides appearance) {
                    density = LocalDensity.current.density
                    Box(Modifier.size(300.dp).background(backdrop).testTag("media-menu")) {
                        MenuSheetSurface {
                            Box(Modifier.fillMaxWidth().height(60.dp)
                                .background(menuSheetSectionColor(palette.background1)))
                            HorizontalDivider(Modifier.height(1.dp), color = Color.Red)
                            if (grid) GridMenu.Menu(showDragHandle = false) {}
                            else ListMenu.Menu(showDragHandle = false) {}
                        }
                    }
                }
            }
        }
        for (glass in listOf(true, false)) {
            for (grid in listOf(true, false)) {
                composeRule.runOnIdle {
                    context.preferences.edit().putBoolean(glassEnabledKey, glass).commit()
                    selected.value = grid to glass
                }
                val pixels = composeRule.onNodeWithTag("media-menu").captureToImage().toPixelMap()
                val x = pixels.width / 2
                val header = pixels[x, (30 * density).toInt()].toArgb()
                val body = pixels[x, (120 * density).toInt()].toArgb()
                assertNotEquals("grid=$grid: themed header and body stay distinct", header, body)
                assertEquals("grid=$grid: original header divider stays visible",
                    Color.Red.toArgb(), pixels[x, (60 * density).toInt()].toArgb())
                if (glass) {
                    assertNotEquals("header must not mask the shared glass", palette.background1.toArgb(), header)
                    assertNotEquals("actions must not mask the shared glass", palette.background0.toArgb(), body)
                    assertEquals("the outer shape starts above the media header", backdrop.toArgb(), pixels[1, 1].toArgb())
                    val nearLeftEdge = (8 * density).toInt()
                    assertEquals("actions must not restart a clipped or bordered glass surface",
                        pixels[nearLeftEdge, (120 * density).toInt()].toArgb(),
                        pixels[nearLeftEdge, (64 * density).toInt()].toArgb())
                } else {
                    assertEquals(palette.background1.toArgb(), header)
                    assertEquals(palette.background0.toArgb(), body)
                }
            }
        }
    }
}
