package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.playlist.LocalPlaylistHeaderSurfaceScope
import app.n_zik.android.components.playlist.localPlaylistHeaderSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassAuditedParentSourceTest {
    @get:Rule val compose = createComposeRule()
    private val shape = RoundedCornerShape(12.dp)
    private val clear = GlassSettings(enabled = true, blur = 0f, film = 0f,
        rim = 0f, depth = 0f, amount = 0f, vibrancy = 0f)

    @Test fun `playlist header actions include the visible header film and mounted updates`() {
        val film = mutableStateOf(.45f)
        compose.setContent { AppearanceProvider {
            val page = rememberCanvasBackdrop { drawRect(Color.Blue) }
            CompositionLocalProvider(LocalGlassContainerBackdrop provides page,
                LocalGlassMaterialSource provides page, LocalGlassBackdrop provides page) {
                Box(Modifier.size(260.dp, 160.dp).background(Color.Blue).testTag("owner.scene")) {
                    val settings = clear.copy(film = film.value)
                    LocalPlaylistHeaderSurfaceScope(settings) { export ->
                        Row(Modifier.size(200.dp, 100.dp).localPlaylistHeaderSurface(settings, export)) {
                            Box(Modifier.size(40.dp).glassChipBackground(Color.White, shape, clear)
                                .testTag("owner.child"))
                            Spacer(Modifier.width(32.dp))
                            Box(Modifier.size(40.dp).testTag("owner.reference"))
                        }
                    }
                }
            }
        } }
        val before = assertChildMatchesPanel()
        compose.runOnIdle { film.value = .85f }
        val after = assertChildMatchesPanel()
        assertTrue("Mounted optical child must follow header film", distance(before, after) > .1f)
    }

    @Test fun `real dialog child includes the rendered dialog plate before its own film`() {
        val film = mutableStateOf(.45f)
        compose.setContent { AppearanceProvider {
            val page = rememberCanvasBackdrop { drawRect(Color.Blue) }
            val view = LocalView.current
            val host = remember(view.rootView, page) {
                GlassMenuHostState().apply { dialogs.windowRoot = view.rootView; backdrop = page }
            }
            CompositionLocalProvider(LocalGlassMenuHost provides host,
                LocalGlassBackdrop provides page,
                LocalGlassPreviewSettings provides clear.copy(film = film.value, menuDimming = 0f)) {
                Box(Modifier.size(400.dp, 400.dp).background(Color.Blue).testTag("owner.scene")) {
                    DialogFrame(onDismiss = {}) {
                        Row {
                            Box(Modifier.size(40.dp).glassDialogButtonBackground(Color.White, shape,
                                settings = clear).testTag("owner.child"))
                            Spacer(Modifier.width(32.dp))
                            Box(Modifier.size(40.dp).testTag("owner.reference"))
                        }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        } }
        compose.mainClock.advanceTimeBy(600)
        val before = assertChildMatchesPanel()
        compose.runOnIdle { film.value = .85f }
        val after = assertChildMatchesPanel()
        assertTrue("Mounted optical child must follow dialog film", distance(before, after) > .1f)
    }

    private fun assertChildMatchesPanel(): Color {
        val root = compose.onNodeWithTag("owner.scene")
        val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
        val pixels = root.captureToImage().toPixelMap()
        fun color(tag: String): Color {
            val center = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center - origin
            return pixels[center.x.toInt(), center.y.toInt()]
        }
        val expected = color("owner.reference")
        val actual = color("owner.child")
        assertEquals("Child includes parent red", expected.red, actual.red, .035f)
        assertEquals("Child includes parent green", expected.green, actual.green, .035f)
        assertEquals("Child includes parent blue", expected.blue, actual.blue, .035f)
        return actual
    }

    private fun distance(a: Color, b: Color) = kotlin.math.abs(a.red-b.red) +
        kotlin.math.abs(a.green-b.green) + kotlin.math.abs(a.blue-b.blue)

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalAppearance provides Appearance(
            DefaultDarkColorPalette.copy(background0 = Color.Blue, background1 = Color.Red),
            Typography.fallback, shape, shape, shape), content = content)
    }
}
