package app.n_zik.android.components.player

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.foundation.interaction.MutableInteractionSource
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.PlayerPlayButtonType
import app.it.fast4x.rimusic.ui.screens.player.components.controls.ControlsEssential
import app.it.fast4x.rimusic.ui.screens.player.components.controls.ControlsModern
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.jumpPreviousKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.glassFilmKey
import app.n_zik.android.components.glass.glassRimKey
import app.n_zik.android.components.glass.glassTintStrengthKey
import app.n_zik.android.components.glass.glassBlurKey
import app.n_zik.android.components.glass.glassDepthKey
import app.n_zik.android.components.glass.glassAmountKey
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
class PlayerTransportGlassTest {
    @get:Rule val compose = createComposeRule()
    private val player = mockk<Player>(relaxed = true)
    private val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
    private val preferences get() = ApplicationProvider.getApplicationContext<Application>().preferences

    @Before fun setup() {
        preferences.edit().clear().putBoolean("buttonzoomout", false).commit()
        every { binder.player } returns player
        every { player.currentMediaItem } returns null
        val songs = mockk<SongTable>()
        every { songs.likeState(any()) } returns flowOf(null)
        mockkObject(Database)
        every { Database.songTable } returns songs
        listenTogetherGuestLock.value = false
    }

    @After fun cleanup() {
        listenTogetherGuestLock.value = false
        unmockkAll()
    }

    @Config(qualifiers = "w700dp-h400dp")
    @Test fun `actual transport variants pull their pane and glyph together across every style`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val store = GlassStore(context)
        store.reset()
        for (key in listOf(glassFilmKey, glassRimKey, glassTintStrengthKey,
            glassBlurKey, glassDepthKey, glassAmountKey)) store.setFloat(key, 0f)
        preferences.edit().putBoolean(glassEnabledKey, true).commit()
        val modern = mutableStateOf(false)
        val style = mutableStateOf(PlayerPlayButtonType.Default)
        val palette = DefaultDarkColorPalette.copy(text = Color.Cyan, accent = Color.Cyan,
            red = Color.Cyan, background1 = Color.Red, background2 = Color.Red, background4 = Color.Red)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppearanceProvider(palette) {
                val scene = rememberCanvasBackdrop { drawRect(Color.Red) }
                CompositionLocalProvider(LocalGlassContainerBackdrop provides scene) {
                    Box(Modifier.requiredSize(600.dp, 200.dp).background(Color.Black).testTag("actualTransport"),
                        contentAlignment = Alignment.Center) {
                        Row(Modifier.width(560.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically) {
                            if (modern.value) ControlsModern(binder, { 0L }, 1f, false, false,
                                style.value, false, {}, palette)
                            else ControlsEssential(binder, { 0L }, 1f, false, null, "transport-test",
                                style.value, false, false, {}, palette)
                        }
                    }
                }
            }
        }
        val root = compose.onNodeWithTag("actualTransport")
        fun centerOf(pixels: androidx.compose.ui.graphics.PixelMap, region: Rect, pane: Boolean): Float {
            val points = buildList {
                for (y in region.top.toInt().coerceAtLeast(0) until region.bottom.toInt().coerceAtMost(pixels.height)) {
                    for (x in region.left.toInt().coerceAtLeast(0) until region.right.toInt().coerceAtMost(pixels.width)) {
                        val color = pixels[x, y]
                        if (if (pane) color.red > .5f && color.green < .4f && color.blue < .4f
                            else color.red < .4f && color.green > .6f && color.blue > .6f) add(x)
                    }
                }
            }
            assertTrue("Actual ${if (pane) "pane" else "glyph"} must be visible in $region", points.isNotEmpty())
            return points.average().toFloat()
        }
        for (variant in listOf(false, true)) for (type in PlayerPlayButtonType.entries) {
            compose.runOnIdle { modern.value = variant; style.value = type }
            // Apply composition and Android placement before recording this style's pixels.
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1800)
            compose.waitForIdle()
            val count = if (variant) 3 else 5
            val play = compose.onAllNodes(hasClickAction())[if (variant) 1 else 2]
            // Modern keeps its native 44dp disabled glyph target and 115dp ribbed shadow host.
            val playWidth = when {
                variant && type == PlayerPlayButtonType.Disabled -> 44.dp
                variant && type == PlayerPlayButtonType.CircularRibbed -> 115.dp
                else -> type.width.dp
            }
            val playHeight = if (variant && type in listOf(PlayerPlayButtonType.Disabled,
                PlayerPlayButtonType.CircularRibbed)) playWidth else type.height.dp
            play.assertWidthIsEqualTo(playWidth).assertHeightIsEqualTo(playHeight)
            for (index in 0 until count) {
                val action = compose.onAllNodes(hasClickAction())[index]
                val bounds = action.fetchSemanticsNode().boundsInRoot
                val rootBounds = root.fetchSemanticsNode().boundsInRoot
                val region = bounds.translate(-rootBounds.topLeft).inflate(20f)
                val before = root.captureToImage().toPixelMap()
                action.performTouchInput { down(center); moveTo(center + Offset(70f, 0f), 200) }
                shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
                compose.mainClock.advanceTimeBy(400)
                val held = root.captureToImage().toPixelMap()
                val label = "$variant $type control $index"
                val directory = File("build/reports/player-transport-pixels").apply { mkdirs() }
                for ((name, image) in listOf("idle" to before, "held" to held)) {
                    val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                    for (y in 0 until image.height) for (x in 0 until image.width)
                        bitmap.setPixel(x, y, image[x, y].toArgb())
                    File(directory, "$variant-$type-$index-$name.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
                val glyphBefore = centerOf(before, region, false)
                val glyphHeld = centerOf(held, region, false)
                assertTrue("$label glyph moves with its spring: $glyphBefore -> $glyphHeld",
                    glyphHeld > glyphBefore + .5f)
                val hasPane = !(type == PlayerPlayButtonType.Disabled && (variant || index == 2))
                if (hasPane) assertTrue("$label pane moves with the glyph",
                    centerOf(held, region, true) > centerOf(before, region, true) + .5f)
                action.performTouchInput { up() }
                compose.mainClock.advanceTimeBy(1800)
            }
        }
        store.reset()
    }

    @Test fun `both actual variants retain native bounds and gesture owners across five styles`() {
        val modern = mutableStateOf(false)
        val style = mutableStateOf(PlayerPlayButtonType.Default)
        val buffering = mutableStateOf(false)
        var holds = 0
        compose.setContent {
            AppearanceProvider {
                Row(Modifier.width(360.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    if (modern.value) ControlsModern(binder, { 0L }, 1.5f, true, buffering.value,
                        style.value, false, { holds++ }, DefaultDarkColorPalette)
                    else ControlsEssential(binder, { 0L }, 1.5f, true, null, "transport-test",
                        style.value, buffering.value, false, { holds++ }, DefaultDarkColorPalette)
                }
            }
        }
        for (variant in listOf(false, true)) {
            compose.runOnIdle { modern.value = variant }
            for (type in PlayerPlayButtonType.entries) {
                compose.runOnIdle { style.value = type; preferences.edit().putBoolean(glassEnabledKey, false).commit() }
                val native = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }
                for ((left, right) in native.zipWithNext()) assertTrue("$variant $type native canvases cannot overlap",
                    left.right <= right.left)
                if (!variant) compose.onAllNodes(hasTestTag("player.transport.smallGlyph"), useUnmergedTree = true)
                    .fetchSemanticsNodes().forEach { glyph ->
                        assertEquals(with(compose.density) { 26.dp.toPx() }, glyph.boundsInRoot.width, .01f)
                        assertEquals(with(compose.density) { 26.dp.toPx() }, glyph.boundsInRoot.height, .01f)
                    }
                compose.onAllNodes(hasClickAction()).assertCountEquals(if (variant) 3 else 5)
                compose.runOnIdle { preferences.edit().putBoolean(glassEnabledKey, true).commit() }
                val glass = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }
                assertEquals("$variant $type", native, glass)
                val playIndex = if (variant) 1 else 2
                compose.onAllNodes(hasClickAction())[playIndex].performTouchInput { click() }
                compose.onAllNodes(hasClickAction())[playIndex].performTouchInput { longClick() }
                if (variant && type in listOf(PlayerPlayButtonType.CircularRibbed, PlayerPlayButtonType.Disabled))
                    compose.onNodeWithText("1.5x").assertDoesNotExist()
                else compose.onNodeWithText("1.5x").assertExists()
                compose.runOnIdle { buffering.value = true }
                compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
                    .assertCountEquals(if (variant && type == PlayerPlayButtonType.Disabled) 0 else 1)
                compose.onAllNodes(hasClickAction()).assertCountEquals(if (variant) 3 else 5)
                compose.runOnIdle { buffering.value = false }
            }
        }
        verify(exactly = 10) { binder.gracefulPause() }
        assertEquals(10, holds)
    }

    @Test fun `small Essential surfaces match skip envelope without moving slots or enlarging glyphs`() {
        preferences.edit().putBoolean(glassEnabledKey, false).commit()
        compose.setContent {
            AppearanceProvider {
                Row(Modifier.width(360.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ControlsEssential(binder, { 0L }, 1f, false, null, "transport-test",
                        PlayerPlayButtonType.Default, false, false, {}, DefaultDarkColorPalette)
                }
            }
        }
        val native = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }
        for (id in listOf("like", "repeat")) compose.onNodeWithTag("player.transport.$id.material").assertDoesNotExist()
        compose.runOnIdle { preferences.edit().putBoolean(glassEnabledKey, true).commit() }
        val glass = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals(native, glass)
        for ((id, index) in listOf("like" to 0, "repeat" to 4)) {
            compose.onAllNodes(hasClickAction())[index].assertWidthIsEqualTo(46.dp).assertHeightIsEqualTo(46.dp)
            val material = compose.onNodeWithTag("player.transport.$id.material")
                .assertWidthIsEqualTo(46.dp).assertHeightIsEqualTo(46.dp).fetchSemanticsNode().boundsInRoot
            assertEquals(native[index].center, material.center)
        }
        compose.onAllNodes(hasClickAction()).assertCountEquals(5)
    }

    @Test fun `guest skip stays inert while play and allowed like keep a single owner`() {
        val modern = mutableStateOf(false)
        compose.setContent {
            AppearanceProvider {
                Row {
                    if (modern.value) ControlsModern(binder, { 0L }, 1f, false, false,
                        PlayerPlayButtonType.Default, false, {}, DefaultDarkColorPalette)
                    else ControlsEssential(binder, { 0L }, 1f, false, null, "transport-test",
                        PlayerPlayButtonType.Default, false, false, {}, DefaultDarkColorPalette)
                }
            }
        }
        for (variant in listOf(false, true)) {
            compose.runOnIdle { modern.value = variant; listenTogetherGuestLock.value = true }
            val start = if (variant) 0 else 1
            compose.onAllNodes(hasClickAction())[start].performTouchInput { click() }
            compose.onAllNodes(hasClickAction())[start + 1].performTouchInput { click() }
            compose.onAllNodes(hasClickAction())[start + 2].performTouchInput { click() }
        }
        verify(exactly = 0) { player.seekTo(any<Long>()) }
        verify(exactly = 0) { player.seekToNextMediaItem() }
        verify(exactly = 0) { player.seekToPreviousMediaItem() }
        verify(exactly = 2) { binder.gracefulPlay() }
    }

    @Test fun `previous restart rules stay native with missing previous and configured threshold`() {
        val modern = mutableStateOf(false)
        var previousExists = false
        var position = 0L
        every { player.hasPreviousMediaItem() } answers { previousExists }
        every { player.currentPosition } answers { position }
        preferences.edit().putString(jumpPreviousKey, "3").commit()
        compose.setContent {
            AppearanceProvider {
                Row {
                    if (modern.value) ControlsModern(binder, { position }, 1f, false, false,
                        PlayerPlayButtonType.Default, false, {}, DefaultDarkColorPalette)
                    else ControlsEssential(binder, { position }, 1f, false, null, "transport-test",
                        PlayerPlayButtonType.Default, false, false, {}, DefaultDarkColorPalette)
                }
            }
        }
        for (variant in listOf(false, true)) for (glass in listOf(false, true)) {
            compose.runOnIdle { modern.value = variant; preferences.edit().putBoolean(glassEnabledKey, glass).commit() }
            for (hasPrevious in listOf(false, true)) {
                compose.runOnIdle { previousExists = hasPrevious; position = if (hasPrevious) 4000L else 0L }
                compose.onAllNodes(hasClickAction())[if (variant) 0 else 1].performTouchInput { click() }
            }
        }
        verify(exactly = 8) { player.seekTo(0L) }
        verify(exactly = 0) { player.seekToPreviousMediaItem() }
    }

    @Test fun `native ribbed press keeps artwork and ripple inside the actual silhouette`() {
        preferences.edit().putBoolean(glassEnabledKey, false).commit()
        compose.setContent {
            AppearanceProvider {
                Row(Modifier.background(Color.Black)) {
                    ControlsModern(binder, { 0L }, 1f, false, false,
                        PlayerPlayButtonType.CircularRibbed, false, {}, DefaultDarkColorPalette)
                }
            }
        }
        val play = compose.onAllNodes(hasClickAction())[1]
        play.performTouchInput { down(center) }
        val pixels = play.captureToImage().toPixelMap()
        assertEquals(Color.Black, pixels[0, 0])
        assertEquals(Color.Black, pixels[pixels.width - 1, pixels.height - 1])
        play.performTouchInput { up() }
        verify(exactly = 1) { binder.gracefulPlay() }
    }

    @Test fun `ribbed and ordinary passive material route live player backdrop and respect area modes`() {
        val sceneColor = mutableStateOf(Color.Red)
        val playerSceneAvailable = mutableStateOf(true)
        val mode = mutableStateOf(GlassAreaMode.GlassOnly)
        val settings = GlassSettings(film = 0f, rim = 0f, blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f)
        compose.setContent {
            AppearanceProvider {
                val scene = rememberCanvasBackdrop { drawRect(sceneColor.value) }
                val wrongScene = rememberCanvasBackdrop { drawRect(Color.Green) }
                CompositionLocalProvider(LocalGlassBackdrop provides wrongScene, LocalGlassContainerBackdrop provides if (playerSceneAvailable.value) scene else null) {
                    val configured = settings.copy(areaModes = mapOf(GlassArea.ActionButtons to mode.value))
                    Row(Modifier.background(Color.Black)) {
                        Box(Modifier.size(60.dp).testTag("ordinary").playerTransportSurface(Color.White, settings = configured))
                        Box(Modifier.size(100.dp).testTag("ribbed")) {
                            PlayerTransportRibbedSurface(Color.White, settings = configured)
                        }
                    }
                }
            }
        }
        for (color in listOf(Color.Red, Color.Blue)) {
            compose.runOnIdle { sceneColor.value = color }
            for (tag in listOf("ordinary", "ribbed")) {
                val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                val actual = pixels[pixels.width / 2, pixels.height / 2]
                assertEquals(color.red, actual.red, .08f)
                assertEquals(color.blue, actual.blue, .08f)
                if (tag == "ribbed") assertEquals(Color.Black, pixels[0, 0])
            }
        }
        compose.runOnIdle { mode.value = GlassAreaMode.Disabled }
        val cleared = compose.onNodeWithTag("ribbed").captureToImage().toPixelMap()
        assertEquals(Color.Black, cleared[50, 50])
        compose.runOnIdle { mode.value = GlassAreaMode.GlassOnly; playerSceneAvailable.value = false }
        for (tag in listOf("ordinary", "ribbed")) {
            val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            assertEquals(Color.Black, pixels[pixels.width / 2, pixels.height / 2])
        }
    }

    @Test fun `small transport draws one bounded highlight and keeps its native hit slot fixed`() {
        preferences.edit().putBoolean(glassEnabledKey, true).commit()
        val source = MutableInteractionSource()
        var clicks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.size(80.dp).background(Color.Black).testTag("feedback.host"), contentAlignment = Alignment.Center) {
                    PlayerTransportSmallControl("feedback", interactionSource = source, settings = GlassSettings(
                        film = 0f, rim = 0f, blur = 0f, tintStrength = 0f)) {
                        PlayerTransportIconButton({ clicks++ }, app.n_zik.android.R.drawable.play,
                            Color.Transparent, source, Modifier.size(26.dp).testTag("feedback.icon"))
                    }
                }
            }
        }
        val icon = compose.onNodeWithTag("feedback.icon")
        val originalBounds = icon.fetchSemanticsNode().boundsInRoot
        icon.performTouchInput { down(center) }
        shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeBy(300)
        assertEquals(originalBounds, icon.fetchSemanticsNode().boundsInRoot)
        val pixels = compose.onNodeWithTag("feedback.host").captureToImage().toPixelMap()
        val center = pixels[pixels.width / 2, pixels.height / 2]
        assertTrue("One highlight paints once across surface and glyph", center.red > .15f && center.red < .30f)
        assertEquals("Highlight cannot cover the surrounding touch space", Color.Black,
            pixels[2, pixels.height / 2])
        assertEquals("Rounded pane corners stay outside the highlight", Color.Black,
            pixels[pixels.width / 2 - 24, pixels.height / 2 - 24])
        icon.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.onAllNodes(hasClickAction())[0].performTouchInput { click(Offset(1f, this.center.y)) }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { assertEquals("The enlarged canvas edge is actionable", 2, clicks) }
    }

    @Test fun `small transport controls keep the contour envelope in contour only mode`() {
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.size(70.dp).background(Color.Black), contentAlignment = Alignment.Center) {
                    PlayerTransportSmallControl("contour", settings = GlassSettings(
                        areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.ContourOnly))) {
                        Box(Modifier.size(26.dp).testTag("contour.slot"))
                    }
                }
            }
        }
        compose.onNodeWithTag("player.transport.contour.material")
            .assertWidthIsEqualTo(46.dp).assertHeightIsEqualTo(46.dp)
        compose.onNodeWithTag("contour.slot").assertWidthIsEqualTo(26.dp).assertHeightIsEqualTo(26.dp)
    }

    @Test fun `small surface has one uniform film across glyph slot and overdraw`() {
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.size(70.dp).background(Color.Black), contentAlignment = Alignment.Center) {
                    PlayerTransportSmallControl("fixture", settings = GlassSettings(
                        film = .5f, rim = 0f, tintStrength = 0f, blur = 0f)) {
                        Box(Modifier.size(26.dp))
                    }
                }
            }
        }
        val material = compose.onNodeWithTag("player.transport.fixture.material")
            .assertWidthIsEqualTo(46.dp).assertHeightIsEqualTo(46.dp)
        val pixels = material.captureToImage().toPixelMap()
        val center = pixels[pixels.width / 2, pixels.height / 2]
        val overdraw = pixels[4, pixels.height / 2]
        assertEquals(center.red, overdraw.red, .02f)
        assertEquals(center.green, overdraw.green, .02f)
        assertEquals(center.blue, overdraw.blue, .02f)
    }

    @Composable private fun AppearanceProvider(palette: ColorPalette = DefaultDarkColorPalette,
        content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(16.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(
            colorPalette = palette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        ), content = content)
    }
}
