package app.n_zik.android.components.glass

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.LocalPlayerSheetState
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.BackdropEffectScope
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalAnimationApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w420dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassQueueFloatingActionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `real queue floating source remains sampled at rest and follows source updates`() {
        RuntimeEnvironment.getApplication().preferences.edit().clear().commit()
        val shown = mutableStateOf(false)
        val stripe = mutableStateOf(Color.Red)
        val shape = RoundedCornerShape(10.dp)
        val settings = GlassSettings(enabled = true, blur = 10f, film = .2f,
            rim = 0f, vibrancy = 0f, depth = 0f, amount = 0f, tintStrength = 0f)
        val sheet = mockk<PlayerSheetState> { every { isVisible } returns false }
        lateinit var view: View
        var foregroundOptics = false
        compose.mainClock.autoAdvance = false
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette.copy(
                    background2 = Color.Yellow, text = Color.Transparent), Typography.fallback,
                    shape, shape, shape),
                LocalGlassPreviewSettings provides settings,
                LocalPlayerSheetState provides sheet,
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
                // A foreground queue restores its budget independently of the covered player.
                LocalGlassOpticsEnabled provides true,
                LocalGlassUnderlayCovered provides false,
            ) {
                GlassQueuePanel(Modifier.size(360.dp, 500.dp), settings) {
                    val list = rememberLazyListState(initialFirstVisibleItemIndex = 20)
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize().glassQueueListBackdrop(list)) {
                        items(60) {
                            Box(Modifier.fillParentMaxWidth().height(64.dp).drawBehind {
                                drawRect(Color.Blue)
                                val width = 4.dp.toPx()
                                var x = 0f
                                while (x < size.width) {
                                    drawRect(stripe.value, Offset(x, 0f), Size(width, size.height))
                                    x += width * 2f
                                }
                            })
                        }
                    }
                    // Hide the list from the window; source colors can only reach us through the optical capture.
                    Box(Modifier.fillMaxSize().background(Color.Black))
                    GlassFloatingActions(LocalQueueGlassBackdrop.current) {
                        val optics = glassOpticsEnabled()
                        SideEffect { foregroundOptics = optics }
                        FloatingActionsContainerWithScrollToTop(list,
                            modifier = Modifier.padding(bottom = 80.dp).testTag("queue.floating"),
                            visible = shown.value, windowInsets = WindowInsets(0, 0, 0, 0))
                    }
                }
            }
        }
        compose.runOnIdle { shown.value = true }
        compose.mainClock.advanceTimeBy(450)
        val observations = mutableListOf<String>()
        fun assertCapturedSource(phase: String) {
            compose.waitForIdle()
            assertTrue("Queue foreground retains optical budget: $phase", foregroundOptics)
            val actions = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("queue.floating")))
            val bounds = actions[actions.fetchSemanticsNodes().lastIndex].fetchSemanticsNode().boundsInRoot
            val pixels = compose.runOnIdle {
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                    view.draw(Canvas(it))
                }.asImageBitmap().toPixelMap()
            }
            val inset = bounds.width * .25f
            var sourceColor = 0
            var blue = 0
            var total = 0
            val histogram = mutableMapOf<Int, Int>()
            for (y in (bounds.top + inset).toInt() until (bounds.bottom - inset).toInt()) {
                for (x in (bounds.left + inset).toInt() until (bounds.right - inset).toInt()) {
                    val pixel = pixels[x, y]
                    val active = if (stripe.value == Color.Red) pixel.red else pixel.green
                    if (active > .6f && pixel.blue < .3f) sourceColor++
                    if (pixel.blue > .6f && active < .3f) blue++
                    histogram[pixel.toArgb()] = (histogram[pixel.toArgb()] ?: 0) + 1
                    total++
                }
            }
            observations += "$phase: source=$sourceColor blue=$blue total=$total bounds=$bounds colors=" +
                histogram.entries.sortedByDescending { it.value }.take(4).joinToString { "${it.key.toUInt().toString(16)}:${it.value}" }
            // View.draw does not execute RenderEffect blur under Robolectric. This verifies
            // actual source sampling against an opaque occluder, not blur quality.
            assertTrue(observations.joinToString("; "), total > 0 && sourceColor > total / 5 && blue > total / 5)
        }
        assertCapturedSource("entering")
        compose.mainClock.advanceTimeBy(1000)
        assertCapturedSource("settled after entry")
        compose.runOnIdle { stripe.value = Color.Green }
        compose.mainClock.advanceTimeByFrame()
        assertCapturedSource("live source changed while settled")
        compose.runOnIdle { shown.value = false }
        compose.mainClock.advanceTimeBy(48)
        assertCapturedSource("exiting")
    }

    @Test fun `stationary queue source is resampled when consumer translates vertically`() {
        val translation = mutableFloatStateOf(0f)
        val repaint = mutableIntStateOf(0)
        val settings = GlassSettings(enabled = true)
        var captures = 0
        var sourceDraws = 0
        lateinit var view: View
        compose.mainClock.autoAdvance = false
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalGlassPreviewSettings provides settings) {
                GlassQueuePanel(Modifier.size(320.dp), settings) {
                    val state = rememberLazyListState()
                    Box(Modifier.fillMaxSize().glassQueueListBackdrop(state).drawBehind {
                        sourceDraws++
                        drawRect(Color.Red)
                        drawRect(Color.Green, Offset(0f, 160.dp.toPx()), Size(size.width, 160.dp.toPx()))
                    })
                    Box(Modifier.fillMaxSize().background(Color.Black))
                    val queue = requireNotNull(LocalQueueGlassBackdrop.current)
                    // The pixels are static. Holding the generation constant isolates positional
                    // invalidation from incidental source re-records by the View.draw harness.
                    val stable = remember(queue) { object : Backdrop by queue {
                        override val contentVersion: Int = 1
                    } }
                    val shape: () -> Shape = remember { { RectangleShape } }
                    val effects: BackdropEffectScope.() -> Unit = remember { {} }
                    val onCapture: DrawScope.(DrawScope.() -> Unit) -> Unit = remember {
                        { drawSource -> captures++; drawSource() }
                    }
                    val onSurface: DrawScope.() -> Unit = remember { { repaint.intValue } }
                    Box(Modifier.padding(20.dp).graphicsLayer { translationY = translation.floatValue }
                        .size(40.dp).testTag("moving.capture").drawPlainBackdrop(
                            backdrop = stable, shape = shape, effects = effects,
                            onDrawBackdrop = onCapture, onDrawSurface = onSurface,
                        ))
                }
            }
        }
        fun sampledColor(): Color {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val bounds = compose.onNodeWithTag("moving.capture").fetchSemanticsNode().boundsInRoot
            return compose.runOnIdle {
                val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(image))
                image.asImageBitmap().toPixelMap()[bounds.center.x.toInt(), bounds.center.y.toInt()]
            }
        }
        repeat(3) { sampledColor() }
        assertEquals("initial sample is the upper red band", Color.Red, sampledColor())
        val initialCaptures = captures
        compose.runOnIdle { translation.floatValue = 180f; repaint.intValue++ }
        val moved = sampledColor()
        assertEquals("translated composite must sample the green band; captures=$initialCaptures->$captures sourceDraws=$sourceDraws",
            Color.Green, moved)
        assertTrue("translation must refresh the processed capture", captures > initialCaptures)
        repeat(3) { assertEquals("settled source remains aligned", Color.Green, sampledColor()) }
    }
}
