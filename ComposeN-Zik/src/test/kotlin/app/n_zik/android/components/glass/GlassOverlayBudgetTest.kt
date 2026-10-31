package app.n_zik.android.components.glass

import android.app.Application
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.junit.Assert.assertFalse
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
        compose.setContent {
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
        compose.setContent {
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
        compose.setContent {
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
