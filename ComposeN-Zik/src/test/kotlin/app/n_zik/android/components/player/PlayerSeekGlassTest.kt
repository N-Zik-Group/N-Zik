package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.media3.common.Player
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import app.it.fast4x.rimusic.ui.components.SeekBar
import app.it.fast4x.rimusic.ui.components.SeekBarColored
import app.it.fast4x.rimusic.ui.components.SeekBarCustom
import app.it.fast4x.rimusic.ui.components.SeekBarThin
import app.it.fast4x.rimusic.ui.components.SeekBarWaved
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassContextArea
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.BackdropEffectScope
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.extensions.audiobar.views.ProgressPercentage
import app.n_zik.android.extensions.audiobar.views.SeekBarStaticAudioWaves
import app.n_zik.android.extensions.audiobar.views.SeekBarVisualizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerSeekGlassTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `mounted optical thumb applies live blur settings instead of a fixed press blur`() {
        compose.mainClock.autoAdvance = false
        var settings by mutableStateOf(GlassSettings(blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f, rim = 0f))
        val effects = mutableListOf<Boolean>()
        lateinit var motion: PlayerSeekGlassMotion
        val source = object : Backdrop {
            override val isCoordinatesDependent = false
            override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?) {
                effects += (density as BackdropEffectScope).renderEffect != null
                drawRect(Color.Blue)
            }
        }
        compose.setContent { AppearanceProvider {
            CompositionLocalProvider(LocalGlassContainerBackdrop provides source) {
                Box(Modifier.requiredWidth(300.dp).height(120.dp)) {
                    PlayerSeekGlass(PlayerTimelineType.Default, true, settings, fraction = { .5f }) { thumb ->
                        motion = requireNotNull(thumb).motion
                        SeekBar(50, 0, 100, {}, {}, {}, Color.White, Color.Gray,
                            Modifier.padding(vertical = 50.dp).testTag("configured.native"), glassThumb = thumb)
                    }
                }
            }
        } }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("configured.native").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("player.seek.glassThumb").captureToImage()
        compose.runOnIdle {
            assertTrue("Inspect an actual intermediate press frame", motion.pressProgress > .001f && motion.pressProgress < .99f)
            assertTrue("The live optical node must sample its source", effects.isNotEmpty())
            assertTrue("Zero blur must not create a RenderEffect", effects.none { it })
            effects.clear()
            settings = settings.copy(blur = 12f)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("player.seek.glassThumb").captureToImage()
        compose.runOnIdle {
            assertTrue("The same mounted pane must pick up the configured blur", effects.any { it })
            effects.clear()
            settings = settings.copy(blur = 0f)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("player.seek.glassThumb").captureToImage()
        compose.runOnIdle {
            assertTrue(effects.isNotEmpty())
            assertTrue("Returning to zero removes the effect", effects.none { it })
        }
        compose.onNodeWithTag("configured.native").performTouchInput { up() }
    }

    @Test fun `dragging in either direction never paints a second colored track inside the lens`() {
        compose.mainClock.autoAdvance = false
        var value by mutableStateOf(25L)
        var hideLens by mutableStateOf(false)
        var type by mutableStateOf(PlayerTimelineType.Default)
        compose.setContent { AppearanceProvider {
            val source = rememberCanvasBackdrop { drawRect(Color.Red) }
            CompositionLocalProvider(LocalGlassContainerBackdrop provides source) {
                Box(Modifier.requiredWidth(300.dp).height(120.dp).background(Color.Black).testTag("duplicate.scene")) {
                    PlayerSeekGlass(type, true, GlassSettings(rim = 0f), fraction = { value / 100f },
                        timeMillis = { compose.mainClock.currentTime }) { thumb ->
                        // Hide only the pane at the same clock instant. The real native bar
                        // keeps precisely the same motion value, drag state and geometry.
                        val visual = if (hideLens && thumb != null) PlayerSeekGlassThumb(thumb.motion,
                            settings = thumb.settings, opticsEnabled = false) else thumb
                        val start: (Long) -> Unit = { value = it }
                        val drag: (Long) -> Unit = { value = (value + it).coerceIn(0L, 100L) }
                        val modifier = Modifier.padding(vertical = 50.dp).testTag("duplicate.native")
                        if (type == PlayerTimelineType.PinBar) SeekBarCustom(type, value, 0, 100,
                            start, drag, {}, Color.Blue, Color.Transparent, modifier, glassThumb = visual)
                        else SeekBar(value, 0, 100, start, drag, {}, Color.Blue, Color.Transparent,
                            modifier, glassThumb = visual)
                    }
                }
            }
        } }
        fun assertSingleTrack() {
            val withLens = compose.onNodeWithTag("duplicate.scene").captureToImage().toPixelMap()
            compose.runOnIdle { hideLens = true }
            val native = compose.onNodeWithTag("duplicate.scene").captureToImage().toPixelMap()
            var excess = 0f
            var nativeBlue = 0
            for (y in 0 until native.height) for (x in 0 until native.width) {
                excess = maxOf(excess, withLens[x, y].blue - native[x, y].blue)
                if (native[x, y].blue > .5f) nativeBlue++
            }
            assertTrue("$type must render the real native blue track", nativeBlue > 20)
            assertTrue("$type must not replay a second blue stroke: max excess=$excess", excess <= .03f)
            compose.runOnIdle { hideLens = false }
        }
        for (variant in listOf(PlayerTimelineType.Default, PlayerTimelineType.PinBar)) {
            compose.runOnIdle { type = variant; value = 25L }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("duplicate.native").performTouchInput { down(Offset(width * .25f, center.y)) }
            compose.mainClock.advanceTimeBy(80)
            assertSingleTrack()
            for (fraction in listOf(.75f, .35f)) {
                compose.onNodeWithTag("duplicate.native").performTouchInput {
                    advanceEventTime(24)
                    moveTo(Offset(width * fraction, center.y))
                }
                compose.mainClock.advanceTimeBy(80)
                assertSingleTrack()
            }
            compose.onNodeWithTag("duplicate.native").performTouchInput { up() }
            compose.mainClock.advanceTimeBy(900)
        }
    }

    @Test fun `release and cancellation visibly shrink the lens before clearing and can be interrupted by a new press`() {
        compose.mainClock.autoAdvance = false
        lateinit var motion: PlayerSeekGlassMotion
        compose.setContent { AppearanceProvider {
            val source = rememberCanvasBackdrop { drawRect(Color.Red) }
            CompositionLocalProvider(LocalGlassContainerBackdrop provides source) {
                Box(Modifier.requiredWidth(300.dp).height(120.dp).background(Color.Black).testTag("release.scene")) {
                    // Isolate the visible motion/alpha contract from hardware RenderEffect rasterization.
                    // The mounted optical-node test separately checks live effect configuration.
                    PlayerSeekGlass(PlayerTimelineType.Default, true,
                        GlassSettings(rim = 0f, blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f), fraction = { .5f }) { thumb ->
                        motion = requireNotNull(thumb).motion
                        SeekBar(50, 0, 100, {}, {}, {}, Color.Blue, Color.Transparent,
                            Modifier.padding(vertical = 50.dp).testTag("release.native"), glassThumb = thumb)
                    }
                }
            }
        } }
        fun strongestRed(): Float {
            val pixels = compose.onNodeWithTag("release.scene").captureToImage().toPixelMap()
            var red = 0f
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) red = maxOf(red, pixels[x, y].red)
            return red
        }
        compose.mainClock.advanceTimeBy(32)
        for (cancelled in listOf(false, true)) {
            compose.onNodeWithTag("release.native").performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(800)
            assertTrue("Pressed pane must be visible: progress=${motion.pressProgress}", strongestRed() > .9f)
            compose.onNodeWithTag("release.native").performTouchInput { if (cancelled) cancel() else up() }
            val releasedAt = compose.mainClock.currentTime
            compose.mainClock.advanceTimeBy(160)
            val redDuringReturn = strongestRed()
            assertTrue("The return deformation must still be visible at 160ms: cancelled=$cancelled " +
                "elapsed=${compose.mainClock.currentTime - releasedAt} red=$redDuringReturn " +
                "progress=${motion.pressProgress} scaleX=${motion.scaleX} scaleY=${motion.scaleY}",
                redDuringReturn > .25f)
            assertTrue("The visible pane is shrinking", motion.scaleX in .35f..0.9f)
            compose.onNodeWithTag("release.native").performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(800)
            assertTrue("A new press interrupts the fade", strongestRed() > .9f)
            compose.onNodeWithTag("release.native").performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1200)
            assertEquals("A settled release restores the exact native-only scene", 0f, strongestRed(), .01f)
        }
    }

    @Test fun `Performance thumb preserves native seeking without sampling the source`() {
        compose.mainClock.autoAdvance = false
        var samples = 0
        var thumbState: PlayerSeekGlassThumb? = null
        var dragCalls = 0
        compose.setContent {
            AppearanceProvider {
                val source = rememberCanvasBackdrop { samples++; drawRect(Color.Red) }
                CompositionLocalProvider(LocalGlassContainerBackdrop provides source) {
                    PlayerSeekGlass(PlayerTimelineType.Default, true,
                        GlassSettings(quality = app.n_zik.android.components.glass.GlassQuality.Performance),
                        fraction = { .5f }) { thumb ->
                        thumbState = thumb
                        NativeBar(PlayerTimelineType.Default, { dragCalls++ }, {}, thumb)
                    }
                }
            }
        }
        compose.onNodeWithTag("native").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        compose.onNodeWithTag("native").performTouchInput { moveTo(center + Offset(8f, 0f)) }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.onNodeWithTag("player.seek.glassThumb").captureToImage()
        assertEquals(app.n_zik.android.components.glass.GlassRenderer.Tint, requireNotNull(thumbState).renderer)
        assertEquals("Performance must not sample/refraction-render the player", 0, samples)
        compose.onNodeWithTag("native").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1000)
        assertTrue("The native timeline keeps its drag callback", dragCalls > 0)
    }

    @Test fun `Glass preserves native round cursor colors and Pin trait pixels and restores exact off rendering`() {
        var type by mutableStateOf(PlayerTimelineType.Default)
        var glass by mutableStateOf(false)
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.requiredWidth(300.dp).height(80.dp).background(Color.Black).testTag("scene")) {
                    PlayerSeekGlass(type, true, GlassSettings(enabled = glass), fraction = { .5f }) { thumb ->
                        val modifier = Modifier.testTag("native")
                        when (type) {
                            PlayerTimelineType.ColoredBar -> SeekBarColored(50, 0, 100, {}, {}, {},
                                Color.Yellow, Color.Gray, modifier, glassThumb = thumb)
                            PlayerTimelineType.PinBar -> SeekBarCustom(type, 50, 0, 100, {}, {}, {},
                                Color.Yellow, Color.Gray, modifier, glassThumb = thumb)
                            else -> SeekBar(50, 0, 100, {}, {}, {}, Color.Yellow, Color.Gray,
                                modifier, glassThumb = thumb)
                        }
                    }
                }
            }
        }
        for (variant in listOf(PlayerTimelineType.Default, PlayerTimelineType.ColoredBar, PlayerTimelineType.PinBar)) {
            compose.runOnIdle { type = variant; glass = false }
            val native = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
            val bounds = compose.onNodeWithTag("native").fetchSemanticsNode().boundsInRoot
            val scene = compose.onNodeWithTag("scene").fetchSemanticsNode().boundsInRoot
            // Above the 3dp track: only the native round cursor (or Pin's 15dp trait) paints here.
            val x = (bounds.center.x - scene.left - with(compose.density) { 1.dp.toPx() }).toInt()
            val y = (bounds.center.y - scene.top + with(compose.density) { 4.dp.toPx() }).toInt()
            val expected = native[x, y]
            assertTrue("$variant must paint its original cursor/trait outside the thin track",
                expected.red + expected.green + expected.blue > .15f)
            if (variant != PlayerTimelineType.ColoredBar) {
                assertTrue("$variant retains its original yellow color", expected.red > .9f && expected.green > .9f)
            }
            for (enabled in listOf(true, false)) {
                compose.runOnIdle { glass = enabled }
                val actual = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
                for (py in 0 until native.height) for (px in 0 until native.width) {
                    val before = native[px, py]
                    val after = actual[px, py]
                    assertEquals("$variant glass=$enabled red at $px,$py", before.red, after.red, .02f)
                    assertEquals("$variant glass=$enabled green at $px,$py", before.green, after.green, .02f)
                    assertEquals("$variant glass=$enabled blue at $px,$py", before.blue, after.blue, .02f)
                }
            }
        }
    }

    @Test fun `inactive transparent ball never punches a hole in native played tracks`() {
        var type by mutableStateOf(PlayerTimelineType.Default)
        var glass by mutableStateOf(false)
        compose.setContent {
            AppearanceProvider {
                val player = rememberCanvasBackdrop { drawRect(Color.Red) }
                CompositionLocalProvider(LocalGlassContainerBackdrop provides player) {
                    Box(Modifier.requiredWidth(300.dp).height(80.dp).testTag("scene")) {
                        PlayerSeekGlass(type, true, GlassSettings(enabled = glass), fraction = { .5f }) { thumb ->
                            NativeBar(type, {}, {}, thumb)
                        }
                    }
                }
            }
        }
        for (variant in listOf(PlayerTimelineType.Default, PlayerTimelineType.BodiedBar, PlayerTimelineType.Wavy)) {
            compose.runOnIdle { type = variant; glass = false }
            val native = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
            val bounds = compose.onNodeWithTag("native").fetchSemanticsNode().boundsInRoot
            val scene = compose.onNodeWithTag("scene").fetchSemanticsNode().boundsInRoot
            // Sample the played segment inside the resting 14dp circle, before its center.
            val x = (bounds.center.x - scene.left - with(compose.density) { 3.dp.toPx() }).toInt()
            val y = (bounds.center.y - scene.top).toInt()
            val expected = native[x, y]
            assertTrue("Native $variant track must be present at the sample", expected.green > .8f)
            compose.runOnIdle { glass = true }
            val resting = compose.onNodeWithTag("scene").captureToImage().toPixelMap()[x, y]
            assertEquals("Inactive $variant ball must preserve the played track", expected.green, resting.green, .03f)
        }
    }

    @Test fun `native timelines keep bounds and one gesture owner while lens releases on up and cancel`() {
        var type by mutableStateOf(PlayerTimelineType.Default)
        var glass by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var starts = 0
        var finishes = 0
        compose.setContent {
            AppearanceProvider {
                key(type) {
                    Box(Modifier.requiredWidth(300.dp).padding(vertical = 24.dp)) {
                        PlayerSeekGlass(type, enabled,
                            GlassSettings(enabled = glass), fraction = { .5f }) { thumb ->
                            NativeBar(type, { starts++ }, { finishes++ }, thumb)
                        }
                    }
                }
            }
        }
        for (variant in listOf(PlayerTimelineType.Default, PlayerTimelineType.ThinBar,
            PlayerTimelineType.ColoredBar, PlayerTimelineType.PinBar,
            PlayerTimelineType.BodiedBar, PlayerTimelineType.Wavy)) {
            compose.runOnIdle { type = variant; glass = false }
            val nativeBounds = compose.onNodeWithTag("native").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("player.seek.glassThumb").assertDoesNotExist()
            compose.runOnIdle { glass = true }
            assertEquals(nativeBounds, compose.onNodeWithTag("native").fetchSemanticsNode().boundsInRoot)
            assertBall(14f)
            val beforeStarts = starts
            val beforeFinishes = finishes
            compose.onNodeWithTag("native").performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(600)
            assertBall(36f)
            compose.onNodeWithTag("native").performTouchInput { up() }
            compose.mainClock.advanceTimeBy(600)
            assertBall(14f)
            assertEquals(beforeStarts + 1, starts)
            assertEquals(beforeFinishes + 1, finishes)
            compose.onNodeWithTag("native").performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(100)
            compose.onNodeWithTag("native").performTouchInput { cancel() }
            compose.mainClock.advanceTimeBy(600)
            assertBall(14f)
        }
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag("native").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(120)
        assertBall(14f)
        compose.onNodeWithTag("native").performTouchInput { up() }
    }

    @Test fun `track fractions clamp invalid values without changing native padding`() {
        for (inset in listOf(0f, 2f, 6f)) {
            assertEquals(inset, playerSeekLensX(300f, inset, -1f), .001f)
            assertEquals(150f, playerSeekLensX(300f, inset, .5f), .001f)
            assertEquals(300f - inset, playerSeekLensX(300f, inset, 2f), .001f)
            assertTrue(playerSeekLensX(0f, inset, Float.NaN).isFinite())
        }
    }

    @Test fun `real native drag updates seek once per event and glass follows displayed position`() {
        compose.mainClock.autoAdvance = false
        var value by mutableStateOf(25L)
        var starts = 0
        var moves = 0
        var finishes = 0
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.requiredWidth(300.dp).padding(vertical = 24.dp)) {
                    PlayerSeekGlass(PlayerTimelineType.Default, true,
                        GlassSettings(), fraction = { value / 100f },
                        timeMillis = { compose.mainClock.currentTime }) { thumb ->
                        SeekBar(value, 0, 100,
                            { value = it; starts++ },
                            { value = (value + it).coerceIn(0, 100); moves++ },
                            { finishes++ }, Color.White, Color.Gray,
                            Modifier.testTag("native"), glassThumb = thumb)
                    }
                }
            }
        }
        compose.onNodeWithTag("native").performTouchInput { down(Offset(width * .25f, center.y)) }
        compose.mainClock.advanceTimeBy(120)
        val start = compose.onNodeWithTag("player.seek.glassThumb").fetchSemanticsNode().boundsInRoot.center.x
        compose.onNodeWithTag("native").performTouchInput {
            advanceEventTime(24)
            moveTo(Offset(width * .45f, center.y))
            advanceEventTime(24)
            moveTo(Offset(width * .7f, center.y))
        }
        repeat(8) {
            compose.mainClock.advanceTimeBy(16)
            val ball = compose.onNodeWithTag("player.seek.glassThumb").fetchSemanticsNode().boundsInRoot
            val track = compose.onNodeWithTag("player.seek.visualTrack").fetchSemanticsNode().boundsInRoot
            assertEquals(track.right, ball.center.x, 1f)
        }
        val lens = compose.onNodeWithTag("player.seek.glassThumb").fetchSemanticsNode().boundsInRoot
        val native = compose.onNodeWithTag("native").fetchSemanticsNode().boundsInRoot
        val inset = with(compose.density) { 6.dp.toPx() }
        assertTrue(lens.center.x > start)
        val target = native.left + playerSeekLensX(native.width, inset, value / 100f)
        assertTrue(lens.center.x < target)
        compose.mainClock.advanceTimeBy(600)
        assertEquals(target, compose.onNodeWithTag("player.seek.glassThumb").fetchSemanticsNode().boundsInRoot.center.x, 1f)
        assertEquals(1, starts)
        assertEquals(2, moves)
        compose.onNodeWithTag("native").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(600)
        assertBall(14f)
        assertEquals(1, finishes)
    }

    @Test fun `default drag keeps the bubble centered on the native track and follows the selected position`() = verifyNativeDrag(PlayerTimelineType.Default)
    @Test fun `wavy drag keeps the bubble centered on the native track and follows the selected position`() = verifyNativeDrag(PlayerTimelineType.Wavy)
    @Test fun `thin drag keeps the bubble centered on the native track and follows the selected position`() = verifyNativeDrag(PlayerTimelineType.ThinBar)
    @Test fun `colored drag keeps the bubble centered on the native track and follows the selected position`() = verifyNativeDrag(PlayerTimelineType.ColoredBar)
    @Test fun `pin drag keeps the bubble centered on the native track and follows the selected position`() = verifyNativeDrag(PlayerTimelineType.PinBar)
    @Test fun `bodied drag keeps the bubble centered on the native track and follows the selected position`() = verifyNativeDrag(PlayerTimelineType.BodiedBar)
    @Test fun `waveform drag keeps the bubble centered on the native track and follows the selected position`() = verifyNativeDrag(PlayerTimelineType.AudioWaves)
    @Test fun `visualizer drag keeps the bubble centered on the native track and follows the selected position`() = verifyNativeDrag(PlayerTimelineType.VisualizerBar)

    private fun verifyNativeDrag(type: PlayerTimelineType) {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        compose.mainClock.autoAdvance = false
        var value by mutableStateOf(25L)
        var glass by mutableStateOf(true)
        var updates = 0
        var observed: PlayerSeekGlassThumb? = null
        compose.setContent {
            AppearanceProvider {
                key(type) {
                    Box(Modifier.requiredWidth(300.dp).padding(vertical = 60.dp).background(Color.Black)) {
                        PlayerSeekGlass(type, true, GlassSettings(enabled = glass), fraction = { value / 100f },
                            timeMillis = { compose.mainClock.currentTime }) { thumb ->
                            observed = thumb
                            DraggableNativeBar(type, { value }, { value = it; updates++ }, thumb)
                        }
                    }
                }
            }
        }
        val variant = type
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.onNodeWithTag("native").performTouchInput { down(Offset(width * .25f, center.y)) }
        compose.mainClock.advanceTimeBy(200)
        val start = compose.onNodeWithTag("player.seek.glassThumb").fetchSemanticsNode().boundsInRoot.center.x
        compose.onNodeWithTag("native").performTouchInput {
            advanceEventTime(24)
            moveTo(Offset(width * .45f, center.y))
            advanceEventTime(24)
            moveTo(Offset(width * .7f, center.y))
        }
        repeat(8) {
            compose.mainClock.advanceTimeBy(16)
            val ball = compose.onNodeWithTag("player.seek.glassThumb").fetchSemanticsNode().boundsInRoot
            val native = compose.onNodeWithTag("native").fetchSemanticsNode().boundsInRoot
            val inset = with(compose.density) {
                when (variant) {
                    PlayerTimelineType.Default, PlayerTimelineType.ThinBar,
                    PlayerTimelineType.ColoredBar, PlayerTimelineType.Wavy -> 6.dp.toPx()
                    PlayerTimelineType.PinBar, PlayerTimelineType.BodiedBar -> 2.dp.toPx()
                    else -> 0f
                }
            }
            assertEquals("$variant follows the displayed native candidate", native.left +
                playerSeekLensX(native.width, inset, requireNotNull(observed).motion.value), ball.center.x, 1f)
            assertEquals("$variant bubble must stay centered on the native cursor", native.center.y, ball.center.y, 1f)
        }
        compose.mainClock.advanceTimeBy(700)
        assertTrue("$variant native gesture must update the candidate: updates=$updates value=$value", updates > 0 && value > 25L)
        assertTrue("$variant bubble must move with the native gesture",
            compose.onNodeWithTag("player.seek.glassThumb").fetchSemanticsNode().boundsInRoot.center.x > start)
        val withGlass = compose.onNodeWithTag("native").captureToImage().toPixelMap()
        compose.runOnIdle { glass = false }
        compose.mainClock.advanceTimeBy(32)
        val withoutGlass = compose.onNodeWithTag("native").captureToImage().toPixelMap()
        assertEquals(withoutGlass.width, withGlass.width)
        assertEquals(withoutGlass.height, withGlass.height)
        for (y in 0 until withGlass.height) for (x in 0 until withGlass.width) {
            val expected = withoutGlass[x, y]
            // Translucent waveform edges blend with the centered lens behind them.
            // Opaque native cursor/track pixels must stay intact; captured waveform
            // cursor geometry is also checked by PlayerSeekWaveMarkerTest.
            if (expected.red < .95f && expected.green < .95f && expected.blue < .95f) continue
            val actual = withGlass[x, y]
            assertEquals("$variant native red at $x,$y", expected.red, actual.red, .03f)
            assertEquals("$variant native green at $x,$y", expected.green, actual.green, .03f)
            assertEquals("$variant native blue at $x,$y", expected.blue, actual.blue, .03f)
        }
        compose.onNodeWithTag("native").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(32)
    }

    @Test fun `changing the timeline style preserves the next native drag and Glass follow`() {
        compose.mainClock.autoAdvance = false
        var type by mutableStateOf(PlayerTimelineType.Default)
        var value by mutableStateOf(25L)
        var appliedType: PlayerTimelineType? = null
        var updates = 0
        compose.setContent {
            AppearanceProvider {
                run {
                    SideEffect { appliedType = type }
                    Box(Modifier.requiredWidth(300.dp).padding(vertical = 60.dp)) {
                        PlayerSeekGlass(type, true, GlassSettings(), fraction = { value / 100f },
                            timeMillis = { compose.mainClock.currentTime }) { thumb ->
                            DraggableNativeBar(type, { value }, { value = it; updates++ }, thumb)
                        }
                    }
                }
            }
        }
        for (variant in listOf(PlayerTimelineType.Default, PlayerTimelineType.ThinBar,
            PlayerTimelineType.ColoredBar, PlayerTimelineType.PinBar, PlayerTimelineType.Default)) {
            compose.runOnIdle { type = variant; value = 25L; updates = 0 }
            // Manual animation time is independent from snapshot delivery and Android layout.
            compose.waitForIdle()
            compose.mainClock.advanceTimeUntil(500) { appliedType == variant }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.onNodeWithTag("native").performTouchInput {
                down(Offset(width * .25f, center.y))
                advanceEventTime(24)
                moveTo(Offset(width * .45f, center.y))
                advanceEventTime(24)
                moveTo(Offset(width * .7f, center.y))
            }
            compose.mainClock.advanceTimeBy(800)
            compose.waitForIdle()
            assertTrue("$variant must retain real drag callbacks after a style change: updates=$updates value=$value",
                updates > 0 && value > 25L)
            val native = compose.onNodeWithTag("native").fetchSemanticsNode().boundsInRoot
            val bubble = compose.onNodeWithTag("player.seek.glassThumb").fetchSemanticsNode().boundsInRoot
            assertTrue("$variant bubble follows the native candidate after switching", bubble.center.x > native.center.x)
            assertEquals("$variant bubble stays centered on the rail after switching", native.center.y, bubble.center.y, 1f)
            compose.onNodeWithTag("native").performTouchInput { up() }
            compose.mainClock.advanceTimeBy(32)
        }
    }

    @Test fun `contextual contour only preserves native seek callbacks without an optical capture`() {
        val settings = GlassSettings(areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled),
            contextModes = mapOf(GlassContextArea(GlassScope.PlayerSeek, GlassArea.ActionButtons) to GlassAreaMode.ContourOnly))
        var starts = 0
        var finishes = 0
        var observed: PlayerSeekGlassThumb? = null
        compose.setContent {
            AppearanceProvider {
                PlayerSeekGlass(PlayerTimelineType.Default, true, settings, fraction = { .5f }) { thumb ->
                    observed = thumb
                    NativeBar(PlayerTimelineType.Default, { starts++ }, { finishes++ }, thumb)
                }
            }
        }
        compose.onNodeWithTag("native").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(120)
        compose.onNodeWithTag("player.seek.contourThumb").assertExists()
        compose.onNodeWithTag("player.seek.glassThumb").assertDoesNotExist()
        assertEquals(null, requireNotNull(observed).trackBackdrop)
        assertEquals(false, requireNotNull(observed).settings.usesGlassMaterial(GlassArea.ActionButtons))
        compose.onNodeWithTag("native").performTouchInput { up() }
        assertEquals(1, starts)
        assertEquals(1, finishes)
    }

    @Test fun `disabled area has no lens even on visualizer press and master off removes held lens`() {
        var settings by mutableStateOf(GlassSettings(areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled)))
        compose.setContent {
            AppearanceProvider {
                PlayerSeekGlass(PlayerTimelineType.VisualizerBar, true, settings, fraction = { .5f }) { thumb ->
                    NativeBar(PlayerTimelineType.Default, {}, {}, thumb)
                }
            }
        }
        compose.onNodeWithTag("native").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(120)
        compose.onNodeWithTag("player.seek.glassThumb").assertDoesNotExist()
        compose.onNodeWithTag("native").performTouchInput { up() }
        compose.runOnIdle { settings = GlassSettings() }
        compose.onNodeWithTag("native").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(120)
        compose.onNodeWithTag("player.seek.glassThumb").assertExists()
        compose.runOnIdle { settings = settings.copy(enabled = false) }
        compose.onNodeWithTag("player.seek.glassThumb").assertDoesNotExist()
        compose.onNodeWithTag("native").performTouchInput { up() }
    }

    @Test fun `real waveform renderers use same round Glass ball while playing and paused and restore native when off`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        var playing by mutableStateOf(false)
        var glass by mutableStateOf(true)
        compose.setContent {
            AppearanceProvider {
                Column(Modifier.requiredWidth(300.dp)) {
                    SeekBarStaticAudioWaves(uiMedia = null, position = 50, duration = 100,
                        isPlaying = playing, onPositionChange = {},
                        modifier = Modifier.height(40.dp), glassThumb = if (glass) fixedThumb(false) else null)
                    SeekBarVisualizer(isPlaying = playing,
                        progressPercentage = { ProgressPercentage.safeValue(.5f) },
                        playedColor = Color.White, notPlayedColor = Color.Gray, waveInteraction = {},
                        modifier = Modifier.height(50.dp), glassThumb = if (glass) fixedThumb(false) else null)
                    PlayerSeekGlass(PlayerTimelineType.ThinBar, true, GlassSettings(enabled = glass), fraction = { .5f }) { thumb ->
                        NativeBar(PlayerTimelineType.ThinBar, {}, {}, thumb)
                    }
                }
            }
        }
        compose.onAllNodes(hasTestTag("player.seek.glassThumb")).assertCountEquals(3)
        val bounds = compose.onAllNodes(hasTestTag("player.seek.glassThumb")).fetchSemanticsNodes().map { it.boundsInRoot }
        val expectedWidth = with(compose.density) { 14.dp.toPx() }
        bounds.forEach { assertEquals(expectedWidth, it.width, 1f); assertEquals(it.width, it.height, .5f) }
        compose.runOnIdle { playing = true }
        compose.onAllNodes(hasTestTag("player.seek.glassThumb")).assertCountEquals(3)
        compose.runOnIdle { playing = false }
        compose.onAllNodes(hasTestTag("player.seek.glassThumb")).assertCountEquals(3)
        compose.runOnIdle { glass = false }
        compose.onAllNodes(hasTestTag("player.seek.glassThumb")).assertCountEquals(0)
    }

    @Test fun `wave bubbles stay centered on the progress baseline while playing and paused`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        compose.mainClock.autoAdvance = false
        var playing by mutableStateOf(true)
        compose.setContent { AppearanceProvider {
            Column(Modifier.requiredWidth(300.dp).background(Color.Black)) {
                Box(Modifier.padding(vertical = 60.dp)) {
                    SeekBarStaticAudioWaves(uiMedia = null, position = 50, duration = 100,
                        isPlaying = playing, onPositionChange = {},
                        modifier = Modifier.height(40.dp).testTag("wave.baseline"), glassThumb = fixedThumb(true))
                }
                Box(Modifier.padding(vertical = 60.dp)) {
                    SeekBarVisualizer(isPlaying = playing,
                        progressPercentage = { ProgressPercentage.safeValue(.5f) }, playedColor = Color.White,
                        notPlayedColor = Color.Gray, waveInteraction = {},
                        modifier = Modifier.height(40.dp).testTag("visualizer.baseline"), glassThumb = fixedThumb(true))
                }
            }
        } }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        fun verifyCentered() {
            val bubbles = compose.onAllNodes(hasTestTag("player.seek.glassThumb")).fetchSemanticsNodes().map { it.boundsInRoot }
            assertEquals(2, bubbles.size)
            for ((index, tag) in listOf("wave.baseline", "visualizer.baseline").withIndex()) {
                val rail = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertEquals("$tag bubble stays centered on the actual playing point or paused cursor",
                    rail.center.y, bubbles[index].center.y, 1f)
                assertEquals("$tag keeps the horizontal progress anchor", rail.center.x, bubbles[index].center.x, 1f)
            }
        }
        verifyCentered()
        compose.runOnIdle { playing = false }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        verifyCentered()
    }

    @Test fun `vertical scrolling from visualizer does not seek while a completed tap still does`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        var updates = 0
        var soughtFraction = Float.NaN
        var scrolled = 0f
        var parentClicks = 0
        compose.setContent { AppearanceProvider {
            Box(Modifier.requiredWidth(300.dp).height(200.dp)
                .combinedClickable(onClick = { parentClicks++ }, onLongClick = { parentClicks++ },
                    onDoubleClick = { parentClicks++ })
                .scrollable(rememberScrollableState { scrolled += it; it }, Orientation.Vertical)) {
                SeekBarVisualizer(isPlaying = false,
                    progressPercentage = { ProgressPercentage.safeValue(.25f) },
                    playedColor = Color.White, notPlayedColor = Color.Gray,
                    waveInteraction = { updates++; soughtFraction = it.value },
                    modifier = Modifier.padding(top = 60.dp).height(40.dp).testTag("visualizer.gesture"))
            }
        } }
        compose.onNodeWithTag("visualizer.gesture", useUnmergedTree = true).performTouchInput {
            down(Offset(width * .25f, height - 20f))
            moveTo(Offset(width * .26f, height - 70f), delayMillis = 40)
            moveTo(Offset(width * .27f, height - 100f), delayMillis = 40)
            up()
        }
        compose.runOnIdle {
            assertEquals("Vertical scrolling must never seek", 0, updates)
            assertTrue("The parent receives the vertical gesture", scrolled != 0f)
        }
        compose.onNodeWithTag("visualizer.gesture", useUnmergedTree = true).performTouchInput {
            down(Offset(width * .6f, height - 20f))
            up()
        }
        compose.runOnIdle {
            assertEquals("A completed tap seeks once", 1, updates)
            assertEquals("Tap seeks to its horizontal position", .6f, soughtFraction, .01f)
        }
        compose.onNodeWithTag("visualizer.gesture", useUnmergedTree = true).performTouchInput {
            down(Offset(width * .6f, height - 20f))
            advanceEventTime(700)
            up()
        }
        compose.runOnIdle { assertEquals("Seek gestures must not activate player background actions", 0, parentClicks) }
    }

    @Test fun `visualizer bubble follows listener playback state with unchanged and updated arguments`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        val player = mockk<Player>(relaxed = true)
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        val listener = slot<Player.Listener>()
        var playerPlaying = false
        var playingArgument by mutableStateOf(false)
        var activePlayer by mutableStateOf(player)
        every { binder.player } answers { activePlayer }
        every { player.isPlaying } answers { playerPlaying }
        every { player.addListener(capture(listener)) } returns Unit
        compose.mainClock.autoAdvance = false
        compose.setContent { AppearanceProvider {
            CompositionLocalProvider(LocalPlayerServiceBinder provides binder) {
                Box(Modifier.requiredWidth(300.dp).padding(vertical = 60.dp)) {
                    SeekBarVisualizer(isPlaying = playingArgument,
                        progressPercentage = { ProgressPercentage.safeValue(.5f) },
                        playedColor = Color.White, notPlayedColor = Color.Gray, waveInteraction = {},
                        modifier = Modifier.height(40.dp).testTag("visualizer.listener"), glassThumb = fixedThumb(true))
                }
            }
        } }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        val rail = compose.onNodeWithTag("visualizer.listener").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("player.seek.visualizer.pausedCursor").assertExists()
        assertEquals("Initial paused bubble centers on the native cursor", rail.center.y,
            compose.onNodeWithTag("player.seek.glassThumb").fetchSemanticsNode().boundsInRoot.center.y, 1f)
        compose.runOnIdle {
            verify(exactly = 1) { player.addListener(any()) }
            playerPlaying = true
            listener.captured.onIsPlayingChanged(true)
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.onNodeWithTag("player.seek.visualizer.pausedCursor").assertDoesNotExist()
        assertEquals(rail.center.y,
            compose.onNodeWithTag("player.seek.glassThumb").fetchSemanticsNode().boundsInRoot.center.y, 1f)
        compose.runOnIdle {
            verify(exactly = 1) { player.addListener(any()) }
            verify(exactly = 0) { player.removeListener(any()) }
            playerPlaying = false
            listener.captured.onIsPlayingChanged(false)
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.onNodeWithTag("player.seek.visualizer.pausedCursor").assertExists()
        assertEquals(rail.center.y,
            compose.onNodeWithTag("player.seek.glassThumb").fetchSemanticsNode().boundsInRoot.center.y, 1f)
        compose.runOnIdle {
            playingArgument = true
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.onNodeWithTag("player.seek.visualizer.pausedCursor").assertDoesNotExist()
        compose.runOnIdle {
            verify(exactly = 1) { player.addListener(any()) }
            listener.captured.onIsPlayingChanged(false)
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.onNodeWithTag("player.seek.visualizer.pausedCursor").assertExists()
        assertEquals("Listener still controls the state after an argument update", rail.center.y,
            compose.onNodeWithTag("player.seek.glassThumb").fetchSemanticsNode().boundsInRoot.center.y, 1f)
        val replacement = mockk<Player>(relaxed = true)
        val replacementListener = slot<Player.Listener>()
        every { replacement.isPlaying } returns false
        every { replacement.addListener(capture(replacementListener)) } returns Unit
        compose.runOnIdle {
            // The unchanged true argument must not overwrite the new player's paused state.
            activePlayer = replacement
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.onNodeWithTag("player.seek.visualizer.pausedCursor").assertExists()
        compose.runOnIdle {
            verify(exactly = 1) { player.removeListener(listener.captured) }
            verify(exactly = 1) { replacement.addListener(any()) }
        }
    }

    @Test fun `held seek lens samples preceding information content where its pane overlaps`() {
        var informationColor by mutableStateOf(Color.Green)
        var held by mutableStateOf(false)
        val optics = GlassSettings(enabled = true, blur = 0f, film = 0f, rim = 0f,
            depth = 0f, amount = 0f, vibrancy = 0f)
        compose.setContent { AppearanceProvider {
            val player = rememberCanvasBackdrop { drawRect(Color.Blue) }
            CompositionLocalProvider(LocalGlassContainerBackdrop provides player) {
                Box(Modifier.requiredWidth(320.dp).height(180.dp).background(Color.Blue)
                    .testTag("information.overlap.scene")) {
                    PlayerInformationGlassBlock(Modifier.padding(30.dp), optics, landscape = false) {
                        Column {
                            PlayerInformationGlassRegion("info") {
                                Box(Modifier.requiredWidth(260.dp).height(40.dp)
                                    .background(informationColor).testTag("information.overlap.content"))
                            }
                            PlayerInformationGlassRegion("timeline", vertical = true) {
                                SeekBar(50, 0, 100, {}, {}, {}, Color.White, Color.Gray,
                                    glassThumb = fixedThumb(held, settings = optics))
                            }
                        }
                    }
                }
            }
        } }
        fun assertInformationInsideLens() {
            val pane = compose.onNodeWithTag("player.seek.glassThumb").fetchSemanticsNode().boundsInRoot
            val info = compose.onNodeWithTag("information.overlap.content").fetchSemanticsNode().boundsInRoot
            val root = compose.onNodeWithTag("information.overlap.scene").fetchSemanticsNode().boundsInRoot
            val sample = Offset(pane.center.x, pane.center.y - pane.height / 4f)
            assertTrue("Fixture must overlap actual preceding information content", info.contains(sample))
            val pixels = compose.onNodeWithTag("information.overlap.scene").captureToImage().toPixelMap()
            val x = (sample.x - root.left).toInt()
            val y = (sample.y - root.top).toInt()
            val expected = pixels[x - 55, y]
            val actual = pixels[x, y]
            assertEquals("Lens must retain preceding information red", expected.red, actual.red, .04f)
            assertEquals("Lens must retain preceding information green", expected.green, actual.green, .04f)
            assertEquals("Lens must retain preceding information blue", expected.blue, actual.blue, .04f)
        }
        compose.runOnIdle { held = true }
        compose.mainClock.advanceTimeBy(600)
        assertInformationInsideLens()
        compose.runOnIdle { informationColor = Color.Red }
        assertInformationInsideLens()
    }

    @Test fun `held native seek lens reflects its actual information plate and mounted film updates`() {
        var film by mutableStateOf(.45f)
        var scene by mutableStateOf(Color.Blue)
        var held by mutableStateOf(false)
        val optics = GlassSettings(enabled = true, blur = 0f, film = 0f, rim = 0f,
            depth = 0f, amount = 0f, vibrancy = 0f)
        compose.setContent { AppearanceProvider {
            val player = rememberCanvasBackdrop { drawRect(scene) }
            val unrelated = rememberCanvasBackdrop { drawRect(Color.Green) }
            CompositionLocalProvider(LocalGlassContainerBackdrop provides player, LocalGlassBackdrop provides unrelated) {
                Box(Modifier.requiredWidth(320.dp).height(180.dp).background(Color.Blue).testTag("plate.seek.scene")) {
                    PlayerInformationGlassBlock(Modifier.padding(30.dp), optics.copy(film = film), landscape = false) {
                        PlayerInformationGlassRegion("timeline", vertical = true) {
                            SeekBar(50, 0, 100, {}, {}, {}, Color.White, Color.Gray,
                                Modifier.padding(vertical = 40.dp), glassThumb = fixedThumb(held, settings = optics))
                        }
                    }
                }
            }
        } }
        fun assertLensMatchesPlate() {
            val thumb = compose.onNodeWithTag("player.seek.glassThumb").fetchSemanticsNode().boundsInRoot
            val root = compose.onNodeWithTag("plate.seek.scene").fetchSemanticsNode().boundsInRoot
            val pixels = compose.onNodeWithTag("plate.seek.scene").captureToImage().toPixelMap()
            val x = (thumb.center.x - root.left).toInt().coerceIn(0, pixels.width - 1)
            val y = (thumb.center.y - thumb.height / 4 - root.top).toInt().coerceIn(0, pixels.height - 1)
            val expected = pixels[(x - 55).coerceAtLeast(0), y]
            val actual = pixels[x, y]
            assertEquals("Lens must include the rendered panel red", expected.red, actual.red, .04f)
            assertEquals("Lens must include the rendered panel green", expected.green, actual.green, .04f)
            assertEquals("Lens must include the rendered panel blue", expected.blue, actual.blue, .04f)
            assertTrue("Unrelated green page must never enter the lens", actual.green < actual.blue)
        }
        compose.runOnIdle { held = true }
        compose.mainClock.advanceTimeBy(600)
        assertLensMatchesPlate()
        compose.runOnIdle { film = .85f }
        assertLensMatchesPlate()
        compose.runOnIdle { scene = Color(0.3f, 0.1f, 0.9f) }
        assertLensMatchesPlate()
    }

    @Test fun `transparent optical thumb leaves rest clear and reveals live player scene while held without white fill`() {
        var scene by mutableStateOf(Color.Red)
        var held by mutableStateOf(false)
        compose.setContent {
            AppearanceProvider {
                val player = rememberCanvasBackdrop { drawRect(scene) }
                val global = rememberCanvasBackdrop { drawRect(Color.Green) }
                val track = rememberLayerBackdrop()
                CompositionLocalProvider(LocalGlassContainerBackdrop provides player, LocalGlassBackdrop provides global) {
                    Box(Modifier.requiredWidth(300.dp).background(Color.Black).padding(vertical = 60.dp)) {
                        SeekBar(50, 0, 100, {}, {}, {}, Color.White, Color.Gray,
                            glassThumb = fixedThumb(held, track))
                    }
                }
            }
        }
        val image = compose.onNodeWithTag("player.seek.glassThumb").captureToImage().toPixelMap()
        val center = image[0, 0]
        // Capture includes the original track/window beneath this transparent pane.
        assertTrue(center.red < .1f && center.green < .1f && center.blue < .1f)
        compose.runOnIdle { held = true }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { scene = Color.Blue }
        val updated = compose.onNodeWithTag("player.seek.glassThumb").captureToImage().toPixelMap()
        val changed = updated[updated.width / 2, updated.height / 4]
        assertTrue(changed.blue > .5f && changed.green < .15f)
    }

    @Test fun `reference springs lag under the finger stretch by signed velocity and wait before returning`() {
        compose.mainClock.autoAdvance = false
        lateinit var motion: PlayerSeekGlassMotion
        var progressBeforeRelease = 0f
        compose.setContent {
            val scope = rememberCoroutineScope()
            motion = remember { PlayerSeekGlassMotion(scope, .5f) { compose.mainClock.currentTime } }
        }
        compose.runOnIdle { motion.press(); motion.updateValue(.9f) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            assertTrue(motion.value > .5f && motion.value < .9f)
            assertTrue(motion.velocity > 0f)
            assertTrue(motion.opticalScaleX > motion.scaleX)
            assertTrue(motion.opticalScaleY < motion.scaleY)
            assertTrue(motion.scaleX != motion.scaleY)
            motion.updateValue(.1f)
        }
        compose.mainClock.advanceTimeBy(112)
        compose.runOnIdle {
            assertTrue(motion.velocity < 0f)
            assertTrue(motion.opticalScaleX < motion.scaleX)
            assertTrue(motion.opticalScaleY > motion.scaleY)
            progressBeforeRelease = motion.pressProgress
            motion.release()
        }
        compose.mainClock.advanceTimeBy(16)
        compose.runOnIdle { assertTrue("Release waits for the position spring before fading",
            motion.pressProgress >= progressBeforeRelease - .01f) }
        compose.mainClock.advanceTimeBy(1200)
        compose.runOnIdle {
            assertEquals(.1f, motion.value, .001f)
            assertEquals(0f, motion.pressProgress, .001f)
            assertEquals(14f / 36f, motion.opticalScaleX, .003f)
            assertEquals(14f / 36f, motion.opticalScaleY, .003f)
        }
    }

    @Composable private fun fixedThumb(held: Boolean, track: LayerBackdrop? = null,
        settings: GlassSettings = GlassSettings()): PlayerSeekGlassThumb {
        val scope = rememberCoroutineScope()
        val animation = remember { PlayerSeekGlassMotion(scope, .5f) }
        LaunchedEffect(held) { if (held) animation.press() else animation.release() }
        return remember(animation, track, settings) { PlayerSeekGlassThumb(animation, track, settings = settings) }
    }

    @Composable private fun NativeBar(type: PlayerTimelineType, start: () -> Unit, finish: () -> Unit,
        thumb: PlayerSeekGlassThumb? = null) {
        val modifier = Modifier.testTag("native")
        when (type) {
            PlayerTimelineType.Wavy -> SeekBarWaved({ 50f }, {}, modifier,
                onSeekStarted = { start() }, onSeekFinished = finish, color = Color.White, isActive = false,
                glassThumb = thumb)
            PlayerTimelineType.ThinBar -> SeekBarThin(50, 0, 100, { start() }, {}, finish,
                Color.White, Color.Gray, modifier, glassThumb = thumb)
            PlayerTimelineType.ColoredBar -> SeekBarColored(50, 0, 100, { start() }, {}, finish,
                Color.White, Color.Gray, modifier, glassThumb = thumb)
            PlayerTimelineType.PinBar, PlayerTimelineType.BodiedBar -> SeekBarCustom(type, 50, 0, 100, { start() }, {}, finish,
                Color.White, Color.Gray, modifier, glassThumb = thumb)
            else -> SeekBar(50, 0, 100, { start() }, {}, finish, Color.White, Color.Gray, modifier,
                glassThumb = thumb)
        }
    }

    @Composable private fun DraggableNativeBar(type: PlayerTimelineType, value: () -> Long,
        update: (Long) -> Unit, thumb: PlayerSeekGlassThumb?) {
        val modifier = Modifier.testTag("native")
        val delta: (Long) -> Unit = { update((value() + it).coerceIn(0, 100)) }
        when (type) {
            PlayerTimelineType.Default -> SeekBar(value(), 0, 100, update, delta, {},
                Color.White, Color.Gray, modifier, glassThumb = thumb)
            PlayerTimelineType.ThinBar -> SeekBarThin(value(), 0, 100, update, delta, {},
                Color.White, Color.Gray, modifier, glassThumb = thumb)
            PlayerTimelineType.ColoredBar -> SeekBarColored(value(), 0, 100, update, delta, {},
                Color.White, Color.Gray, modifier, glassThumb = thumb)
            PlayerTimelineType.PinBar, PlayerTimelineType.BodiedBar -> SeekBarCustom(type, value(), 0, 100,
                update, delta, {}, Color.White, Color.Gray, modifier, glassThumb = thumb)
            PlayerTimelineType.Wavy -> SeekBarWaved({ value().toFloat() }, { delta(it.toLong()) }, modifier,
                onSeekStarted = { update(it.toLong()) }, onSeekFinished = {}, color = Color.White,
                backgroundColor = Color.Gray, isActive = false, range = 0f..100f, glassThumb = thumb)
            PlayerTimelineType.AudioWaves -> SeekBarStaticAudioWaves(uiMedia = null, position = value(),
                duration = 100, isPlaying = false, onPositionChange = update,
                modifier = modifier.height(40.dp), glassThumb = thumb)
            PlayerTimelineType.VisualizerBar -> SeekBarVisualizer(isPlaying = false,
                progressPercentage = { ProgressPercentage.safeValue(value() / 100f) }, playedColor = Color.White,
                notPlayedColor = Color.Gray, waveInteraction = { update((it.value * 100).toLong()) },
                modifier = modifier.height(40.dp), glassThumb = thumb)
        }
    }

    private fun assertBall(sideDp: Float) {
        val bounds = compose.onNodeWithTag("player.seek.glassThumb").fetchSemanticsNode().boundsInRoot
        val side = with(compose.density) { sideDp.dp.toPx() }
        assertEquals(side, bounds.width, 1.5f)
        assertEquals(side, bounds.height, 1.5f)
    }

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(16.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )) {
            Box(Modifier.padding(top = 60.dp)) { content() }
        }
    }
}
