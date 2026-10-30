package app.n_zik.android.components.glass

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import app.n_zik.android.components.dialog.common.DialogPresence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassOverlayReadabilityTest {
    // Keep the coroutine animation scale deterministic. Android's setting below controls
    // the app policy, while Compose caches its duration scale when the test owner starts.
    @get:Rule val compose = createComposeRule(effectContext = object : androidx.compose.ui.MotionDurationScale {
        override val scaleFactor: Float = 1f
    })
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: GlassStore

    @Before fun clearSettings() {
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE).edit().clear().commit()
        store = GlassStore(context)
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun assertSceneBrightness(expected: Float) {
        val scene = compose.onNodeWithTag("scene")
        val image = scene.captureToImage().toPixelMap()
        assertEquals(expected, image[2, 2].red, .02f)
        val origin = scene.fetchSemanticsNode().boundsInRoot.topLeft
        val sample = compose.onNodeWithTag("sample").fetchSemanticsNode().boundsInRoot.center - origin
        assertEquals("pane samples the same dimmed scene", expected, image[sample.x.toInt(), sample.y.toInt()].red, .02f)
    }

    private fun assertIntermediateDimming() {
        val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
        val brightness = pixels[2, 2].red
        assertTrue("scrim must be between transparent and its 75% target: $brightness", brightness > .27f && brightness < .98f)
        assertSceneBrightness(brightness)
    }

    private fun assertWindowScrimFades(area: GlassArea) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        compose.mainClock.autoAdvance = false
        val host = GlassMenuHostState()
        var visible by mutableStateOf(true)
        compose.setContent {
            val white = rememberCanvasBackdrop { drawRect(Color.White) }
            SideEffect { host.backdrop = white }
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box(Modifier.size(240.dp).background(Color.White).testTag("scene")) {
                    val window: @Composable () -> Unit = {
                        GlassDialogWindow(true, {}, DialogProperties(), area = area, scrimVisible = visible) { Sample() }
                    }
                    if (area == GlassArea.Dialogs) DialogPresence(visible = visible, content = window) else window()
                    GlassDialogOverlayHost(host)
                }
            }
        }
        compose.mainClock.advanceTimeBy(80)
        assertIntermediateDimming()
        compose.mainClock.advanceTimeBy(300)
        assertSceneBrightness(.25f)
        compose.runOnIdle { visible = false }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        assertIntermediateDimming()
        compose.mainClock.advanceTimeBy(300)
        val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
        assertEquals("exit removes the physical scrim", 1f, pixels[2, 2].red, .02f)
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Test fun `custom sheet forwards explicit colored and transparent scrims to physical and sampled scene`() {
        val host = GlassMenuHostState()
        var scrim by mutableStateOf(Color.Red.copy(alpha = .5f))
        store.setBoolean(glassEnabledKey, true)
        compose.setContent {
            val white = rememberCanvasBackdrop { drawRect(Color.White) }
            val view = androidx.compose.ui.platform.LocalView.current
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            val appearance = Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )
            SideEffect { host.backdrop = white; host.dialogs.windowRoot = view.rootView }
            CompositionLocalProvider(LocalGlassMenuHost provides host, LocalAppearance provides appearance,
                LocalGlassBackdrop provides white) {
                Box(Modifier.size(240.dp).background(Color.White).testTag("scene")) {
                    CustomModalBottomSheet(true, {}, containerColor = Color.Transparent,
                        scrimColor = scrim, dragHandle = null,
                        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0) }) {
                        Sample()
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, host.dialogs.entries.size) }
        fun assertColor(expected: Color) {
            val scene = compose.onNodeWithTag("scene")
            val image = scene.captureToImage().toPixelMap()
            val origin = scene.fetchSemanticsNode().boundsInRoot.topLeft
            val sample = compose.onNodeWithTag("sample").fetchSemanticsNode().boundsInRoot.center - origin
            for (actual in listOf(image[2, 2], image[sample.x.toInt(), sample.y.toInt()])) {
                assertEquals(expected.red, actual.red, .02f)
                assertEquals(expected.green, actual.green, .02f)
                assertEquals(expected.blue, actual.blue, .02f)
            }
        }
        assertColor(Color(1f, .5f, .5f))
        compose.runOnIdle { scrim = Color.Transparent }
        assertColor(Color.White)
    }

    @Test fun `dialog scrim fades on entry and exit with identical sampled dimming`() {
        assertWindowScrimFades(GlassArea.Dialogs)
    }

    @Test fun `menu sheet scrim fades on entry and exit with identical sampled dimming`() {
        assertWindowScrimFades(GlassArea.Menus)
    }

    @Composable
    private fun Sample(tag: String = "sample") {
        val source = requireNotNull(LocalGlassBackdrop.current)
        Box(Modifier.size(80.dp).background(Color.Magenta)
            .drawPlainBackdrop(source, { RectangleShape }, effects = {}).testTag(tag))
    }

    private fun showWindow(area: GlassArea) {
        val host = GlassMenuHostState()
        compose.setContent {
            val white = rememberCanvasBackdrop { drawRect(Color.White) }
            SideEffect { host.backdrop = white }
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box(Modifier.size(240.dp).background(Color.White).testTag("scene")) {
                    GlassDialogWindow(true, {}, DialogProperties(), area = area) {
                        Sample()
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
    }

    @Test fun `dialog dimming updates live independently from menu dimming`() {
        store.setFloat(glassDialogDimmingKey, .25f)
        store.setFloat(glassMenuDimmingKey, .75f)
        showWindow(GlassArea.Dialogs)
        assertSceneBrightness(.75f)
        compose.runOnIdle { store.setFloat(glassMenuDimmingKey, .1f) }
        assertSceneBrightness(.75f)
        compose.runOnIdle { store.setFloat(glassDialogDimmingKey, .75f) }
        assertSceneBrightness(.25f)
    }

    @Test fun `shared menu sheet window uses menu dimming and updates live`() {
        store.setFloat(glassDialogDimmingKey, .25f)
        store.setFloat(glassMenuDimmingKey, .75f)
        showWindow(GlassArea.Menus)
        assertSceneBrightness(.25f)
        compose.runOnIdle { store.setFloat(glassDialogDimmingKey, .9f) }
        assertSceneBrightness(.25f)
        compose.runOnIdle { store.setFloat(glassMenuDimmingKey, .25f) }
        assertSceneBrightness(.75f)
    }

    @Test fun `dropdown host uses menu dimming and updates live`() {
        store.setFloat(glassDialogDimmingKey, .25f)
        store.setFloat(glassMenuDimmingKey, .75f)
        val host = GlassMenuHostState()
        val id = Any()
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
            thumbnailShape = RectangleShape, uiRoundnessShape = RectangleShape,
            artistThumbnailShape = RectangleShape,
        )
        compose.setContent {
            val settings = rememberGlassSettings()
            val white = rememberCanvasBackdrop { drawRect(Color.White) }
            SideEffect {
                host.backdrop = white
                host.show(GlassMenuEntry(
                    id = id, anchor = Rect(80f, 80f, 100f, 100f), settings = settings,
                    onDismiss = {}, shape = RectangleShape,
                    content = { Sample() },
                ))
            }
            CompositionLocalProvider(LocalAppearance provides appearance, LocalGlassMenuHost provides host) {
                Box(Modifier.size(240.dp).background(Color.White).testTag("scene")) {
                    GlassOverlayHost(host)
                }
            }
        }
        assertSceneBrightness(.25f)
        compose.runOnIdle { store.setFloat(glassDialogDimmingKey, .9f) }
        assertSceneBrightness(.25f)
        compose.runOnIdle { store.setFloat(glassMenuDimmingKey, .25f) }
        assertSceneBrightness(.75f)
    }

    @Test fun `nested dialogs apply each scrim once to both page and sampled pane`() {
        val host = GlassMenuHostState()
        compose.setContent {
            val white = rememberCanvasBackdrop { drawRect(Color.White) }
            SideEffect { host.backdrop = white }
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box(Modifier.size(240.dp).background(Color.White).testTag("scene")) {
                    GlassDialogWindow(true, {}, DialogProperties()) {
                        Sample("outer")
                        GlassDialogWindow(true, {}, DialogProperties()) { Sample() }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        assertSceneBrightness(.0625f)
    }

    @Test fun `Glass dialog dims the live page while preserving its own content`() {
        val host = GlassMenuHostState()
        compose.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box(Modifier.size(240.dp).background(Color.White).testTag("scene")) {
                    GlassDialogWindow(true, {}, DialogProperties()) {
                        Box(Modifier.size(80.dp).background(Color.Green))
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        val image = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
        val outside = image[2, 2]
        assertEquals(.25f, outside.red, .02f)
        assertEquals(outside.red, outside.green, .01f)
        val content = image[image.width / 2, image.height / 2]
        assertEquals(1f, content.green, .02f)
        assertEquals(0f, content.red, .02f)
    }
}
