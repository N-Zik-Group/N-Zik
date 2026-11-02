package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.menu.song.SongMenuSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassMaterialConsistencyTest {
    @get:Rule val compose = createComposeRule()
    private val shape = RoundedCornerShape(0.dp)

    @Composable private fun AppearanceProvider(palette: ColorPalette = DefaultDarkColorPalette, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalAppearance provides Appearance(
            palette, Typography.fallback, shape, shape, shape,
        )) { content() }
    }

    private fun center(tag: String): Color {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    private fun assertColor(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, .02f)
        assertEquals(expected.green, actual.green, .02f)
        assertEquals(expected.blue, actual.blue, .02f)
        assertEquals(expected.alpha, actual.alpha, .02f)
    }

    @Test fun `role RGB survives while film alone controls coverage in live and tint materials`() {
        val film = mutableStateOf(0f)
        val nativeAlpha = mutableStateOf(.1f)
        val quality = mutableStateOf(GlassQuality.Balanced)
        compose.setContent {
            AppearanceProvider {
                val raw = rememberCanvasBackdrop { drawRect(Color.Blue) }
                val settings = GlassSettings(quality = quality.value, film = film.value, rim = 0f,
                    blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f)
                Box(Modifier.background(Color.Blue)) {
                    Box(Modifier.size(80.dp).testTag("role").glassMaterial(settings, shape,
                        ownBackdrop = raw, filmColor = Color.Red.copy(alpha = nativeAlpha.value)))
                }
            }
        }
        for (renderer in listOf(GlassQuality.Balanced, GlassQuality.Performance)) {
            for (alpha in listOf(.1f, .2f, .9f, 1f)) for (opacity in listOf(0f, .5f, 1f)) {
                compose.runOnIdle { quality.value = renderer; nativeAlpha.value = alpha; film.value = opacity }
                assertColor(Color.Red.copy(alpha = opacity).compositeOver(Color.Blue), center("role"))
            }
        }
    }

    @Test fun `nested controls sample raw live source while processed export remains available`() {
        val film = mutableStateOf(.5f)
        val parentColor = mutableStateOf(Color.Yellow)
        val sceneColor = mutableStateOf(Color.Cyan)
        compose.setContent {
            AppearanceProvider {
                val raw = rememberCanvasBackdrop { drawRect(sceneColor.value) }
                val settings = GlassSettings(film = film.value, rim = 0f, blur = 8f,
                    vibrancy = 0f, depth = 0f, amount = 0f)
                Column {
                    GlassMaterialContainerScope(Modifier.size(100.dp), materialSource = raw,
                        material = { exported -> Modifier.glassMaterial(settings.copy(film = .8f), shape,
                            ownBackdrop = raw, filmColor = parentColor.value, exportedBackdrop = exported) }) { surface ->
                        Box(surface, contentAlignment = Alignment.Center) {
                            GlassMaterialContainerColumn(includeParent = true,
                                sectionFilm = Color.Magenta.copy(alpha = .6f),
                                sectionFilmIsMaterial = true,
                                modifier = Modifier.background(Color.Magenta.copy(alpha = .6f))) {
                                Box(Modifier.size(70.dp).testTag("nested").glassMaterial(settings, shape,
                                    ownBackdrop = LocalGlassContainerBackdrop.current, filmColor = Color.Red))
                            }
                        }
                    }
                    Box(Modifier.size(70.dp).testTag("standalone").glassMaterial(settings, shape,
                        ownBackdrop = raw, filmColor = Color.Red))
                }
            }
        }
        for (scene in listOf(Color.Cyan, Color.Blue)) for (parent in listOf(Color.Green, Color.Yellow)) {
            for (opacity in listOf(0f, .5f, 1f)) {
                compose.runOnIdle { sceneColor.value = scene; parentColor.value = parent; film.value = opacity }
                assertColor(center("standalone"), center("nested"))
                assertColor(Color.Red.copy(alpha = opacity).compositeOver(scene), center("nested"))
            }
        }
    }

    @Test fun `native song menu header and body remain distinct sources for Glass badges`() {
        val enabled = mutableStateOf(true)
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val header = mutableStateOf(Color.Green)
        val body = mutableStateOf(Color.Blue)
        compose.setContent {
            AppearanceProvider(DefaultDarkColorPalette.copy(background0 = body.value, background1 = header.value)) {
                val settings = GlassSettings(enabled = enabled.value, film = 0f, rim = 0f,
                    blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f,
                    areaModes = mapOf(GlassArea.Menus to mode.value,
                        GlassArea.ActionButtons to GlassAreaMode.GlassOnly))
                CompositionLocalProvider(LocalGlassPreviewSettings provides settings) {
                    SongMenuSurface(header = {
                        Box(Modifier.size(80.dp).testTag("native.header.badge")
                            .glassExplicitBadgeBackground(settings))
                    }) {
                        Box(Modifier.size(80.dp).testTag("native.body.badge")
                            .glassExplicitBadgeBackground(settings))
                    }
                }
            }
        }
        for (nativeMode in listOf(GlassAreaMode.Disabled, GlassAreaMode.ContourOnly)) {
            for (colors in listOf(Color.Green to Color.Blue, Color.Yellow to Color.Cyan)) {
                for (glassEnabled in listOf(true, false)) {
                    compose.runOnIdle {
                        mode.value = nativeMode; header.value = colors.first; body.value = colors.second
                        enabled.value = glassEnabled
                    }
                    assertColor(colors.first, center("native.header.badge"))
                    assertColor(colors.second, center("native.body.badge"))
                }
            }
        }
    }

    @Test fun `native section inside optical parent replaces raw source with actual composited fill`() {
        val fill = mutableStateOf(Color.Green)
        val parentFilm = mutableStateOf(Color.Yellow)
        compose.setContent {
            AppearanceProvider {
                val raw = rememberCanvasBackdrop { drawRect(Color.Blue) }
                val settings = GlassSettings(film = 0f, rim = 0f, blur = 0f,
                    vibrancy = 0f, depth = 0f, amount = 0f)
                GlassMaterialContainerColumn(modifier = Modifier.size(100.dp), materialSource = raw,
                    material = { exported -> Modifier.glassMaterial(settings.copy(film = 1f), shape,
                        ownBackdrop = raw, filmColor = parentFilm.value, exportedBackdrop = exported) }) {
                    GlassMaterialContainerColumn(modifier = Modifier.background(fill.value),
                        includeParent = true, sectionFilm = fill.value) {
                        Box(Modifier.size(80.dp).testTag("nested.native.badge")
                            .glassExplicitBadgeBackground(settings))
                    }
                }
            }
        }
        for (parent in listOf(Color.Yellow, Color.Cyan)) {
            for (native in listOf(Color.Green, Color.Red.copy(alpha = .5f))) {
                compose.runOnIdle { parentFilm.value = parent; fill.value = native }
                assertColor(native.compositeOver(parent), center("nested.native.badge"))
            }
        }
    }

    @Test fun `performance dialog without shared host retains selected Glass material`() {
        val enabled = mutableStateOf(true)
        compose.setContent {
            AppearanceProvider {
                CompositionLocalProvider(LocalGlassMenuHost provides null,
                    LocalGlassPreviewSettings provides GlassSettings(enabled = enabled.value,
                        quality = GlassQuality.Performance, film = 0f)) {
                    DialogFrame(onDismiss = {}) { Box(Modifier.size(80.dp)) }
                }
            }
        }
        compose.onNodeWithTag("dialogFrameGlass").assertExists()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("dialogFrameNative").assertExists()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun `native sheet child source is transparent for Glass and exact native fill when disabled`() {
        val enabled = mutableStateOf(true)
        var installedSource: Backdrop? = null
        var installedEnabled: Boolean? = null
        compose.setContent {
            AppearanceProvider {
                CompositionLocalProvider(LocalGlassMenuHost provides null,
                    LocalGlassPreviewSettings provides GlassSettings(enabled = enabled.value,
                        quality = GlassQuality.Performance, film = .5f)) {
                    CustomModalBottomSheet(showSheet = true, onDismissRequest = {}, containerColor = Color.Red,
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                        val source = LocalGlassMaterialSource.current
                        val currentEnabled = rememberGlassSettings().enabled
                        SideEffect {
                            installedSource = requireNotNull(source)
                            installedEnabled = currentEnabled
                        }
                        Box(Modifier.size(100.dp))
                    }
                }
            }
        }
        fun assertInstalledSource(expectedEnabled: Boolean, expected: Color) {
            compose.runOnIdle {
                assertEquals(expectedEnabled, installedEnabled)
                assertNotNull(installedSource)
                // Probe the actual installed source on a fresh bitmap. Native
                // dialogs have their own window, while the covered activity can
                // retain stale pixels; neither window crop is the source contract.
                val bitmap = ImageBitmap(16, 16)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(16f, 16f)) {
                    drawRect(Color.Blue)
                    with(requireNotNull(installedSource)) { drawBackdrop(this@draw, null, null) }
                }
                assertColor(expected, bitmap.toPixelMap()[8, 8])
            }
        }
        assertInstalledSource(true, Color.Blue)
        compose.runOnIdle { enabled.value = false }
        assertInstalledSource(false, Color.Red)
    }
}
