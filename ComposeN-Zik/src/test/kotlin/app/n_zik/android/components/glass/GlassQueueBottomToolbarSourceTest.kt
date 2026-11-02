package app.n_zik.android.components.glass

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Icon
import app.it.fast4x.rimusic.ui.screens.player.QueueToolBar
import app.it.fast4x.rimusic.ui.screens.player.QueueToolBarState
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassQueueBottomToolbarSourceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    @OptIn(ExperimentalFoundationApi::class)
    @Test fun `real bottom panel reflects queue and its controls sample the rendered panel`() {
        val listColor = mutableStateOf(Color.Red)
        val panelFill = mutableStateOf(Color.Transparent)
        val film = mutableStateOf(0f)
        val enabled = mutableStateOf(true)
        val nativeToolbarFill = mutableStateOf(DefaultDarkColorPalette.background1)
        var actionFilm = Color.Unspecified
        var nativeHeightPx = 0f
        val controls = List(3) { object : Icon {
            override val iconId = R.drawable.musical_notes
            override fun onShortClick() = Unit
            @Composable override fun ToolBarButton() {
                TabToolBar.Icon(iconId = iconId, onShortClick = {}, onLongClick = {})
            }
        } }
        QueueToolBarState.reset()
        QueueToolBarState.buttons = controls.take(2)
        QueueToolBarState.queueArrow = controls.last()
        try {
            compose.setContent {
                view = LocalView.current
                nativeHeightPx = with(LocalDensity.current) { 60.dp.toPx() }
                val shape = RoundedCornerShape(8.dp)
                val settings = GlassSettings(enabled = enabled.value, film = film.value, blur = 0f,
                    rim = 0f, vibrancy = 0f, depth = 0f, amount = 0f, tintStrength = 0f,
                    areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled),
                    contextModes = mapOf(GlassContextArea(GlassScope.Queue, GlassArea.ActionButtons)
                        to GlassAreaMode.GlassOnly))
                val player = rememberCanvasBackdrop { drawRect(Color.Blue) }
                CompositionLocalProvider(LocalAppearance provides Appearance(
                    DefaultDarkColorPalette.copy(background1 = nativeToolbarFill.value),
                    Typography.fallback, shape, shape, shape),
                    LocalQueuePlayerBackdrop provides player,
                    LocalGlassPreviewSettings provides settings) {
                    actionFilm = glassActionFilmColor()
                    GlassQueuePanel(Modifier.size(360.dp, 100.dp), settings,
                        panelBackgroundColor = panelFill.value, panelShape = shape) {
                        Box(Modifier.fillMaxSize().glassQueueListBackdrop(rememberLazyListState())
                            .background(listColor.value))
                        QueueToolBar(Modifier.align(Alignment.BottomCenter).testTag("bottom.toolbar"))
                    }
                }
            }
            compose.runOnIdle {
                ViewCompat.dispatchApplyWindowInsets(view, WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 24))
                    .setVisible(WindowInsetsCompat.Type.navigationBars(), true).build())
            }
            for (fill in listOf(Color.Transparent, Color.Black)) {
                for (scene in listOf(Color.Red, Color.Green, Color.Green.copy(alpha = .5f)))
                    for (opacity in listOf(0f, .5f, 1f)) {
                    compose.runOnIdle { panelFill.value = fill; listColor.value = scene; film.value = opacity }
                    compose.mainClock.advanceTimeBy(500)
                    val underlay = scene.compositeOver(fill.compositeOver(Color.Blue))
                    val panel = nativeToolbarFill.value.copy(alpha = opacity).compositeOver(underlay)
                    assertPanelColor(panel, "scene=$scene panel=$fill film=$opacity")
                    val toolbar = compose.onNodeWithTag("bottom.toolbar").fetchSemanticsNode().boundsInRoot
                    assertEquals("Material covers navigation inset", nativeHeightPx + 24, toolbar.height, .5f)
                    val expected = actionFilm.copy(alpha = opacity).compositeOver(panel)
                    for (tag in listOf("queue.toolbar.actions", "queue.toolbar.arrow")) {
                        assertSurfaceContains(tag, expected, "scene=$scene panel=$fill film=$opacity")
                    }
                }
            }
            for (alpha in listOf(1f, .4f, 0f)) {
                compose.runOnIdle {
                    enabled.value = false
                    panelFill.value = Color.Transparent
                    listColor.value = Color.Red
                    nativeToolbarFill.value = DefaultDarkColorPalette.background1.copy(alpha = alpha)
                }
                assertPanelColor(nativeToolbarFill.value.compositeOver(Color.Red), "native alpha=$alpha")
            }
        } finally {
            QueueToolBarState.reset()
        }
    }

    private fun assertSurfaceContains(tag: String, expected: Color, context: String) {
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pixels = captureView()
        val left = bounds.left.toInt().coerceIn(0, pixels.width)
        val top = bounds.top.toInt().coerceIn(0, pixels.height)
        val right = bounds.right.toInt().coerceIn(0, pixels.width)
        val bottom = bounds.bottom.toInt().coerceIn(0, pixels.height)
        val area = (right - left) * (bottom - top)
        assertTrue("$tag must be visible: bounds=$bounds, view=${pixels.width}x${pixels.height}", area > 0)
        var matches = 0
        for (y in top until bottom) for (x in left until right) {
            val actual = pixels[x, y]
            if (abs(expected.red - actual.red) < .03f && abs(expected.green - actual.green) < .03f &&
                abs(expected.blue - actual.blue) < .03f) matches++
        }
        assertTrue("$tag must show rendered panel through the material ($context), matches=$matches/$area",
            matches > area / 4)
    }

    private fun assertPanelColor(expected: Color, context: String) {
        val bounds = compose.onNodeWithTag("bottom.toolbar").fetchSemanticsNode().boundsInRoot
        val pixels = captureView()
        val actual = pixels[bounds.center.x.toInt(), bounds.bottom.toInt() - 2]
        assertEquals("panel red $context", expected.red, actual.red, .03f)
        assertEquals("panel green $context", expected.green, actual.green, .03f)
        assertEquals("panel blue $context", expected.blue, actual.blue, .03f)
    }

    private fun captureView(): PixelMap = compose.runOnIdle {
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
            view.draw(Canvas(it))
        }.asImageBitmap().toPixelMap()
    }
}
