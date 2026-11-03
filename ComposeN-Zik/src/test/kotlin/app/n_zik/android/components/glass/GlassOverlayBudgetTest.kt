package app.n_zik.android.components.glass

import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.n_zik.android.components.dialog.common.DialogPresentation
import app.n_zik.android.components.dialog.common.LocalDialogPresentation
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class GlassOverlayBudgetTest {
    @get:Rule val compose = createComposeRule()


    private fun showContent(content: @Composable () -> Unit) {
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) { content() }
        }
    }

    @Test fun `hot optimization choice preserves nested dialog optics but never unlocks background input`() {
        val host = GlassMenuHostState()
        val settings = mutableStateOf(GlassSettings())
        val values = mutableMapOf<String, Boolean>()
        val clocks = mutableMapOf<String, Boolean>()
        var calls = 0
        showContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host,
                LocalGlassPreviewSettings provides settings.value) {
                Box(Modifier.size(300.dp).testTag("root")) {
                    GlassModalContent(host) {
                        val optics = glassOpticsEnabled()
                        SideEffect { values["base"] = optics }
                        GlassDialogWindow(true, {}, DialogProperties()) {
                            val dialogOptics = glassOpticsEnabled()
                            val clock = LocalGlassLiveFrame.current
                            SideEffect { values["dialog"] = dialogOptics; clocks["dialog"] = clock != null }
                            Box(Modifier.size(100.dp).testTag("dialog-control").clickable { calls++ })
                        }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        val bounds = compose.onNodeWithTag("dialog-control").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            assertFalse(values.getValue("base"))
            assertTrue(values.getValue("dialog"))
            host.show(GlassMenuEntry("menu", Rect(0f, 0f, 20f, 20f), settings.value, {}, {}, shape = RectangleShape))
        }
        compose.runOnIdle {
            assertFalse(values.getValue("dialog"))
            assertFalse(clocks.getValue("dialog"))
            settings.value = settings.value.copy(optimizationEnabled = false)
        }
        compose.runOnIdle {
            assertTrue(values.getValue("base"))
            assertTrue(values.getValue("dialog"))
            assertTrue(clocks.getValue("dialog"))
            assertEquals(1f, host.dialogs.entries.single().exposure.value, 0f)
        }
        compose.onNodeWithTag("dialog-control").assertDoesNotExist()
        compose.onNodeWithTag("root").performTouchInput { click(bounds.center) }
        compose.runOnIdle { assertEquals(0, calls); host.remove("menu") }
        compose.onNodeWithTag("dialog-control").assertExists()
        compose.runOnIdle {
            assertTrue(values.getValue("dialog"))
            host.nativeOverlays.add("native")
        }
        compose.runOnIdle {
            assertTrue(values.getValue("dialog"))
            assertTrue(clocks.getValue("dialog"))
            settings.value = settings.value.copy(optimizationEnabled = true)
        }
        compose.runOnIdle {
            assertFalse(values.getValue("dialog"))
            assertFalse(clocks.getValue("dialog"))
            assertEquals(1f, host.dialogs.entries.single().exposure.value, 0f)
        }
        compose.onNodeWithTag("dialog-control").assertDoesNotExist()
        compose.onNodeWithTag("root").performTouchInput { click(bounds.center) }
        compose.runOnIdle { assertEquals(0, calls); host.nativeOverlays.clear() }
        compose.runOnIdle { assertTrue(values.getValue("dialog")); assertTrue(clocks.getValue("dialog")) }
    }

    @Test fun `retained dialog reads the current dropdown backdrop and presentation`() {
        val host = GlassMenuHostState()
        val changed = mutableStateOf(false)
        val presentation = DialogPresentation(mutableStateOf(true))
        lateinit var first: GlassDropdownBackdropOwner
        lateinit var second: GlassDropdownBackdropOwner
        showContent {
            val firstBackdrop = rememberCanvasBackdrop {}
            val secondBackdrop = rememberCanvasBackdrop {}
            first = remember { GlassDropdownBackdropOwner(host, Any(), firstBackdrop) }
            second = remember { GlassDropdownBackdropOwner(host, Any(), secondBackdrop) }
            CompositionLocalProvider(
                LocalGlassMenuHost provides host,
                LocalGlassDropdownBackdropOwner provides if (changed.value) second else first,
                LocalDialogPresentation provides if (changed.value) presentation else null,
            ) {
                GlassDialogWindow(true, {}, DialogProperties()) {}
            }
        }
        val entry = compose.runOnIdle {
            host.dialogs.entries.single().also {
                assertSame(first.underlying, it.backdrop())
                assertEquals(1f, it.contentAlpha(), 0f)
                changed.value = true
            }
        }
        compose.runOnIdle {
            assertSame(entry, host.dialogs.entries.single())
            assertSame(second.underlying, entry.backdrop())
            assertEquals(0f, entry.contentAlpha(), 0f)
        }
    }

    @Test fun `explicit capture protection and fully opaque coverage survive optimization opt out`() {
        val settings = GlassSettings(optimizationEnabled = false)
        var enabled = true
        val captureAllowed = mutableStateOf(false)
        val hidden = mutableStateOf(false)
        showContent {
            CompositionLocalProvider(LocalGlassOpticsEnabled provides captureAllowed.value,
                LocalGlassUnderlayCovered provides hidden.value,
                LocalGlassOpticalUnderlayCovered provides true) {
                val optics = glassOpticsEnabled(settings)
                SideEffect { enabled = optics }
            }
        }
        compose.runOnIdle { assertFalse(enabled); captureAllowed.value = true }
        compose.runOnIdle { assertTrue(enabled); hidden.value = true }
        compose.runOnIdle { assertFalse(enabled) }
    }

    @Test fun `base and lower dialogs reduce optics while foreground stays active and restores`() {
        val host = GlassMenuHostState()
        val first = mutableStateOf(false)
        val second = mutableStateOf(false)
        val values = mutableMapOf<String, Boolean>()
        @Composable fun Probe(name: String) {
            val enabled = glassOpticsEnabled()
            SideEffect { values[name] = enabled }
            Box(Modifier.size(20.dp))
        }
        showContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box {
                    GlassModalContent(host) {
                        Probe("base")
                        if (first.value) GlassDialogWindow(true, {}, DialogProperties()) {
                            Probe("first")
                            if (second.value) GlassDialogWindow(true, {}, DialogProperties()) { Probe("second") }
                        }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        compose.runOnIdle { assertTrue(values.getValue("base")); first.value = true }
        compose.runOnIdle { assertFalse(values.getValue("base")); assertTrue(values.getValue("first")); second.value = true }
        compose.runOnIdle {
            assertFalse(values.getValue("base")); assertFalse(values.getValue("first"))
            assertTrue(values.getValue("second")); second.value = false
        }
        compose.runOnIdle { assertTrue(values.getValue("first")); first.value = false }
        compose.runOnIdle { assertTrue(values.getValue("base")); assertFalse(host.hasOverlays) }
    }

    @Test fun `coverage cannot be overridden by player budget and native window token lifetime restores base`() {
        val host = GlassMenuHostState()
        val native = mutableStateOf(false)
        var effective = true
        showContent {
            Box {
                GlassModalContent(host) {
                    CompositionLocalProvider(LocalGlassOpticsEnabled provides true) {
                        val enabled = glassOpticsEnabled()
                        SideEffect { effective = enabled }
                    }
                }
                if (native.value) NativeGlassOverlayCoverage(host, remember { Any() })
            }
        }
        compose.runOnIdle { assertTrue(effective); native.value = true }
        compose.runOnIdle { assertFalse(effective); assertTrue(host.hasOverlays); native.value = false }
        compose.runOnIdle { assertTrue(effective); assertFalse(host.hasOverlays) }
    }

    @Test fun `foreground menu overrides disabled captured deployment optics without enabling native material`() {
        val host = GlassMenuHostState()
        var menuOptics = false
        val settings = GlassSettings(enabled = false)
        showContent {
            Box {
                CompositionLocalProvider(LocalGlassOpticsEnabled provides false, LocalGlassUnderlayCovered provides true) {
                    val locals = currentCompositionLocalContext
                    LaunchedEffect(Unit) {
                        host.show(GlassMenuEntry(Any(), androidx.compose.ui.geometry.Rect(0f, 0f, 20f, 20f),
                            settings, {}, {
                                val enabled = glassOpticsEnabled()
                                SideEffect { menuOptics = enabled }
                                Box(Modifier.size(40.dp))
                            }, shape = RectangleShape, locals = locals))
                    }
                }
                GlassOverlayHost(host)
            }
        }
        compose.runOnIdle { assertTrue(menuOptics); assertFalse(requireNotNull(host.entry).settings.enabled) }
    }

    @Test fun `modal content preserves explicit inherited queue coverage`() {
        val host = GlassMenuHostState()
        val covered = mutableStateOf(true)
        var enabled = true
        showContent {
            CompositionLocalProvider(LocalGlassUnderlayCovered provides covered.value) {
                GlassModalContent(host, manageFocus = false) {
                    val optics = glassOpticsEnabled()
                    SideEffect { enabled = optics }
                }
            }
        }
        compose.runOnIdle { assertFalse(enabled); covered.value = false }
        compose.runOnIdle { assertTrue(enabled) }
    }

    @Test fun `native menu coverage remains through exit and clears only on removal`() {
        val host = GlassMenuHostState()
        val settings = GlassSettings(enabled = false)
        val id = Any()
        host.show(GlassMenuEntry(id, androidx.compose.ui.geometry.Rect(0f, 0f, 20f, 20f), settings, {}, {}, shape = RectangleShape))
        assertTrue(host.hasOverlays)
        host.hide(id)
        assertTrue(host.hasOverlays)
        host.clear(id)
        assertFalse(host.hasOverlays)
        assertFalse(settings.enabled)
    }
}
