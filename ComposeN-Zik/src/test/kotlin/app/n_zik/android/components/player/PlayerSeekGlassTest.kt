package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.dp
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
class PlayerSeekGlassTest {
    @get:Rule val compose = createComposeRule()

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

    @Test fun `transparent optical thumb leaves rest clear and reveals live player scene while held without white fill`() {
        var scene by mutableStateOf(Color.Red)
        var held by mutableStateOf(false)
        compose.setContent {
            AppearanceProvider {
                val player = rememberCanvasBackdrop { drawRect(scene) }
                val global = rememberCanvasBackdrop { drawRect(Color.Green) }
                val track = rememberLayerBackdrop()
                CompositionLocalProvider(LocalGlassContainerBackdrop provides player, LocalGlassBackdrop provides global) {
                    Box(Modifier.requiredWidth(300.dp).background(Color.Black).padding(vertical = 24.dp)) {
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
            motion.release()
        }
        compose.mainClock.advanceTimeBy(16)
        compose.runOnIdle { assertTrue(motion.pressProgress > .95f) }
        compose.mainClock.advanceTimeBy(1200)
        compose.runOnIdle {
            assertEquals(.1f, motion.value, .001f)
            assertEquals(0f, motion.pressProgress, .001f)
            assertEquals(14f / 36f, motion.opticalScaleX, .003f)
            assertEquals(14f / 36f, motion.opticalScaleY, .003f)
        }
    }

    @Composable private fun fixedThumb(held: Boolean, track: LayerBackdrop? = null): PlayerSeekGlassThumb {
        val scope = rememberCoroutineScope()
        val animation = remember { PlayerSeekGlassMotion(scope, .5f) }
        LaunchedEffect(held) { if (held) animation.press() else animation.release() }
        return remember(animation, track) { PlayerSeekGlassThumb(animation, track) }
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
        ), content = content)
    }
}
