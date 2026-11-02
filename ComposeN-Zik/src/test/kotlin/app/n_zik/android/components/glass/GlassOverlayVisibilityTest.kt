package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.menu.MenuSheetSurface
import app.n_zik.android.components.menu.ListMenu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class GlassOverlayVisibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `small native dialog lowers optics but leaves visible decorations active`() {
        val host = GlassMenuHostState()
        var covered = false
        var optics = true
        compose.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                GlassModalContent(host, manageFocus = false) {
                    covered = LocalGlassUnderlayCovered.current
                    optics = glassOpticsEnabled()
                }
                GlassDialogWindow(false, {}, DialogProperties()) { Box(Modifier.size(80.dp)) }
            }
        }
        compose.runOnIdle {
            assertTrue(host.hasOverlays)
            assertFalse("A small dialog leaves most of the player visible", covered)
            assertFalse("Visible optics can still use the inexpensive modal budget", optics)
        }
    }

    @Test fun `actual opaque native skin covers only until its first lowered position`() {
        nativeSkinMotion(layerOnly = false)
    }

    @Test fun `layer-only translation releases opaque native coverage without remeasurement`() {
        nativeSkinMotion(layerOnly = true)
    }

    private fun nativeSkinMotion(layerOnly: Boolean) {
        val host = GlassMenuHostState()
        var lowered by mutableStateOf(false)
        var open by mutableStateOf(true)
        var covered = false
        var optics = true
        val shape = RoundedCornerShape(0.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)
        compose.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host,
                LocalGlassPreviewSettings provides GlassSettings(enabled = false),
                LocalAppearance provides appearance) {
                GlassModalContent(host, manageFocus = false) {
                    covered = LocalGlassUnderlayCovered.current
                    optics = glassOpticsEnabled()
                }
                if (open) GlassDialogWindow(false, {}, DialogProperties(
                    usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                    // The wrapper is transparent; the production menu skin owns the opaque paint.
                    MenuSheetSurface(Modifier.fillMaxSize().then(if (layerOnly)
                        Modifier.graphicsLayer { translationY = if (lowered) 120f else 0f }
                        else Modifier.offset { IntOffset(0, if (lowered) 120 else 0) })) { }
                }
            }
        }
        compose.runOnIdle { assertTrue("Measured full native paint must suspend hidden visuals", covered); lowered = true }
        // Layer properties are applied on a frame without remeasuring the sheet.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse("Lowering exposes the player before modal disposal: ${host.visibility.regions.values}", covered)
            assertFalse(optics)
            lowered = false
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(covered); open = false }
        compose.runOnIdle { assertFalse(covered); assertTrue(optics) }
    }

    @Test fun `shared production sheet responds to opacity and partial expansion`() { sheetPaint(shared = true) }
    @Test fun `native production sheet responds to opacity and partial expansion`() { sheetPaint(shared = false) }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun sheetPaint(shared: Boolean) {
        val host = GlassMenuHostState()
        var film by mutableStateOf(1f)
        var covered = false
        var optics = true
        lateinit var sheet: SheetState
        lateinit var scope: CoroutineScope
        val shape = RoundedCornerShape(0.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)
        compose.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host,
                LocalGlassPreviewSettings provides GlassSettings(enabled = true, film = film),
                LocalAppearance provides appearance) {
                sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                scope = rememberCoroutineScope()
                Box {
                    GlassModalContent(host, manageFocus = false) {
                        covered = LocalGlassUnderlayCovered.current
                        optics = glassOpticsEnabled()
                    }
                    if (shared) SharedGlassMenuSheet(true, {}, sheet, partiallyExpanded = true,
                        shape = shape, contentWindowInsets = { WindowInsets(0) }) {
                        ListMenu.Menu(showDragHandle = false) { Box(Modifier.height(1_200.dp)) }
                    } else CustomModalBottomSheet(true, {}, sheetState = sheet,
                        shape = shape, containerColor = Color.Transparent, dragHandle = null,
                        contentWindowInsets = { WindowInsets(0) }) {
                        ListMenu.Menu(showDragHandle = false) { Box(Modifier.height(1_200.dp)) }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse("Initially partial sheet keeps exposed visuals live", covered); scope.launch { sheet.expand() } }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("Full painted body must cover (shared=$shared)", covered); film = .5f }
        compose.runOnIdle { assertFalse("Transparent body resumes visible animation", covered); assertFalse(optics); film = 1f }
        compose.runOnIdle { assertTrue(covered); scope.launch { sheet.partialExpand() } }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse("Lowered sheet remains registered without suspending visuals", covered); assertFalse(optics) }
    }
}
