package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
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
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.menu.GlassDropdownMenu
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A menu badge must sample its immediate plate, including its owner's current film. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassMenuImmediateParentTest {
    @get:Rule val compose = createComposeRule()
    private val shape = RoundedCornerShape(12.dp)
    private val neutral = GlassSettings(enabled = true, quality = GlassQuality.Balanced,
        blur = 0f, film = 0f, rim = 0f, depth = 0f, amount = 0f, vibrancy = 0f,
        menuDimming = 0f, tintStrength = 0f)

    @Test fun `shared dropdown badge samples its rendered plate and mounted changes`() =
        assertImmediateParent(Owner.Dropdown)

    @Test fun `list menu badge samples its rendered body band and mounted changes`() =
        assertImmediateParent(Owner.List)

    @Test fun `grid menu badge samples its rendered body band and mounted changes`() =
        assertImmediateParent(Owner.Grid)

    private enum class Owner { Dropdown, List, Grid }

    private fun assertImmediateParent(owner: Owner) {
        val film = mutableStateOf(.45f)
        val page = mutableStateOf(Color.Blue)
        compose.setContent {
            val source = rememberCanvasBackdrop { drawRect(page.value) }
            val root = LocalView.current.rootView
            val host = remember(root, source) {
                GlassMenuHostState().apply { dialogs.windowRoot = root; backdrop = source }
            }
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette.copy(
                    background0 = Color.Red, background1 = Color.Red), Typography.fallback,
                    shape, shape, shape),
                LocalGlassPreviewSettings provides neutral.copy(film = film.value),
                LocalGlassMenuHost provides host,
                LocalGlassBackdrop provides source,
                LocalGlassContainerBackdrop provides source,
                LocalGlassMaterialSource provides source,
            ) {
                Box(Modifier.fillMaxSize().background(page.value).testTag("menu.scene")) {
                    when (owner) {
                        Owner.Dropdown -> {
                            Box(Modifier.absoluteOffset(32.dp, 48.dp)) {
                                GlassDropdownMenu(true, {}, Color.Red, shape) { Probe() }
                            }
                            GlassOverlayHost(host)
                        }
                        Owner.List -> ListMenu.Menu(showDragHandle = false) { Probe() }
                        Owner.Grid -> GridMenu.Menu(showDragHandle = false) { item { Probe() } }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(600)
        val before = assertBadgeMatchesPlate(owner)
        compose.runOnIdle { film.value = .85f }
        val after = assertBadgeMatchesPlate(owner)
        assertTrue("$owner fixture must change the visible parent film", distance(before, after) > .04f)
        compose.runOnIdle { page.value = Color.Cyan }
        assertBadgeMatchesPlate(owner)
    }

    @Composable private fun Probe() {
        Row(Modifier.size(96.dp, 56.dp)) {
            Box(Modifier.size(32.dp).glassChipBackground(Color.Transparent, shape, neutral)
                .testTag("menu.badge"))
            Spacer(Modifier.width(16.dp))
            Box(Modifier.size(32.dp).testTag("menu.reference"))
        }
    }

    private fun assertBadgeMatchesPlate(owner: Owner): Color {
        compose.waitForIdle()
        val scene = compose.onNodeWithTag("menu.scene")
        val origin = scene.fetchSemanticsNode().boundsInRoot.topLeft
        val pixels = scene.captureToImage().toPixelMap()
        fun sample(tag: String): Color {
            val point = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center - origin
            return pixels[point.x.toInt(), point.y.toInt()]
        }
        val expected = sample("menu.reference")
        val actual = sample("menu.badge")
        assertEquals("$owner child must include parent red", expected.red, actual.red, .035f)
        assertEquals("$owner child must include parent green", expected.green, actual.green, .035f)
        assertEquals("$owner child must include parent blue", expected.blue, actual.blue, .035f)
        return expected
    }

    private fun distance(a: Color, b: Color): Float =
        kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)
}
