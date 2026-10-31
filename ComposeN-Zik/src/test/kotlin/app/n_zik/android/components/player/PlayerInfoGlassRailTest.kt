package app.n_zik.android.components.player

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.models.Info
import app.it.fast4x.rimusic.models.ui.UiMedia
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.ui.screens.player.components.controls.InfoAlbumAndArtistModern
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.playerInfoShowIconsKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w800dp-h600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
class PlayerInfoGlassRailTest {
    @get:Rule val compose = createComposeRule()
    private val preferences get() = ApplicationProvider.getApplicationContext<Application>().preferences

    @Before fun setup() { preferences.edit().clear().putBoolean(glassEnabledKey, true).commit() }

    @Test fun `standalone information feedback can expand beyond its resting canvas when material is disabled`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppearanceProvider(RoundedCornerShape(0.dp)) {
                Box(Modifier.size(140.dp).background(Color.Black).testTag("standalone.info"),
                    contentAlignment = Alignment.Center) {
                    PlayerInfoIconButton({}, R.drawable.album, Color.Cyan, Modifier.size(26.dp),
                        settings = GlassSettings(areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled)))
                }
            }
        }
        val root = compose.onNodeWithTag("standalone.info")
        val canvas = compose.onNodeWithTag("player.infoIcon.canvas.${R.drawable.album}", useUnmergedTree = true)
        val original = canvas.fetchSemanticsNode().boundsInRoot
        val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
        val x = (original.right - origin.x + with(compose.density) { 1.dp.toPx() }).toInt()
        val y = (original.center.y - origin.y).toInt()
        assertEquals(Color.Black, root.captureToImage().toPixelMap()[x, y])
        canvas.performTouchInput { down(center); moveTo(center + Offset(100f, 0f), 200) }
        shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeBy(400)
        assertTrue("The held spring paints beyond the stationary 34dp canvas instead of being clipped",
            root.captureToImage().toPixelMap()[x, y].red > .02f)
        canvas.performTouchInput { up() }
    }

    @Test fun `information action held highlight uses ordinary corners without changing native geometry`() {
        val radius = mutableStateOf(0.dp)
        val glass = mutableStateOf(false)
        compose.setContent {
            AppearanceProvider(RoundedCornerShape(BoundedCornerSize(radius.value, .15f))) {
                Box(Modifier.size(100.dp).background(Color.Black), contentAlignment = Alignment.Center) {
                    PlayerInfoIconButton({}, R.drawable.album, Color.Cyan, Modifier.size(26.dp),
                        settings = GlassSettings(enabled = glass.value))
                }
            }
        }
        val canvas = compose.onNodeWithTag("player.infoIcon.canvas.${R.drawable.album}", useUnmergedTree = true)
        val glyph = compose.onNodeWithTag("player.infoIcon.glyph.${R.drawable.album}", useUnmergedTree = true)
        for (value in listOf(0.dp, 4.dp, 50.dp)) {
            compose.runOnIdle { radius.value = value; glass.value = false }
            val nativeCanvas = canvas.fetchSemanticsNode().boundsInRoot
            val nativeCenter = glyph.fetchSemanticsNode().boundsInRoot.center
            compose.runOnIdle { glass.value = true }
            assertEquals(nativeCanvas, canvas.fetchSemanticsNode().boundsInRoot)
            assertEquals(nativeCenter, glyph.fetchSemanticsNode().boundsInRoot.center)
            canvas.performTouchInput { down(center) }
            shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(400)
            val pixels = canvas.captureToImage().toPixelMap()
            val x = with(compose.density) { 5.dp.roundToPx() }
            val y = with(compose.density) { 2.dp.roundToPx() }
            assertTrue("The actual held highlight reaches ordinary corner at radius=$value",
                pixels[x, y].red > .02f)
            canvas.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1500)
        }
    }
    @Test fun `pressing one information action pulls both glyphs and the rail while text stays fixed`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppearanceProvider {
                val scene = rememberCanvasBackdrop { drawRect(Color.Red) }
                CompositionLocalProvider(LocalGlassContainerBackdrop provides scene) {
                    Box(Modifier.size(220.dp, 160.dp).background(Color.Black).testTag("connectedInfo")) {
                        Box(Modifier.padding(20.dp)) {
                            PlayerInfoGlassRail(true, GlassSettings(film = 0f, rim = 0f,
                                blur = 0f, depth = 0f, amount = 0f, tintStrength = 0f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PlayerInfoIconButton({}, R.drawable.album, Color.Cyan,
                                        Modifier.size(26.dp).albumIconBounds())
                                    Box(Modifier.padding(start = 16.dp).size(80.dp, 10.dp)
                                        .background(Color.Green).testTag("fixedInfoText"))
                                }
                                Spacer(Modifier.height(10.dp))
                                Row {
                                    PlayerInfoIconButton({}, R.drawable.person, Color.Cyan,
                                        Modifier.size(24.dp).padding(start = 2.dp).artistIconBounds())
                                }
                            }
                        }
                    }
                }
            }
        }
        // The measured rail and its shared spring are installed after the first layout.
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        val root = compose.onNodeWithTag("connectedInfo")
        val beforeImage = root.captureToImage()
        export("group-idle", beforeImage)
        val before = beforeImage.toPixelMap()
        val text = compose.onNodeWithTag("fixedInfoText").fetchSemanticsNode().boundsInRoot
        val artist = compose.onNodeWithTag("player.infoIcon.glyph.${R.drawable.person}", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val split = artist.top.toInt() - 3
        fun cyanCenter(pixels: androidx.compose.ui.graphics.PixelMap, top: Boolean): Float {
            val points = buildList {
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    val color = pixels[x, y]
                    if ((y < split) == top && color.red < .4f && color.green > .8f && color.blue > .8f)
                        add(x)
                }
            }
            assertTrue(points.isNotEmpty())
            return points.average().toFloat()
        }
        val action = compose.onNodeWithTag("player.infoIcon.canvas.${R.drawable.album}", useUnmergedTree = true)
        action.performTouchInput { down(center); moveTo(center + Offset(70f, 0f), 200) }
        shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeBy(400)
        val heldImage = root.captureToImage()
        export("group-held", heldImage)
        val held = heldImage.toPixelMap()
        val albumBefore = cyanCenter(before, true)
        val albumHeld = cyanCenter(held, true)
        assertTrue("Pressed album moves with the connected capsule: $albumBefore -> $albumHeld", albumHeld > albumBefore + 1f)
        assertTrue("Unpressed artist moves with the same capsule", cyanCenter(held, false) > cyanCenter(before, false) + 1f)
        var changedRailPixels = 0
        for (y in 0 until before.height) for (x in 0 until before.width) {
            if (before[x, y] == Color.Red && held[x, y] == Color.Black) changedRailPixels++
            if (before[x, y] == Color.Green) assertEquals("Metadata text cannot be pulled", Color.Green, held[x, y])
        }
        assertTrue("The passive rail must move with its actions", changedRailPixels > 0)
        assertEquals(text, compose.onNodeWithTag("fixedInfoText").fetchSemanticsNode().boundsInRoot)
        action.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1500)
    }

    @Test fun `information glyph belongs to the canvas jelly feedback`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                    PlayerInfoIconButton({}, R.drawable.album, Color.White, Modifier.size(26.dp))
                }
            }
        }
        val glyph = compose.onNodeWithTag("player.infoIcon.glyph.${R.drawable.album}", useUnmergedTree = true)
        val canvas = compose.onNodeWithTag("player.infoIcon.canvas.${R.drawable.album}", useUnmergedTree = true)
        val idle = glyph.fetchSemanticsNode().boundsInRoot
        canvas.performTouchInput { down(center) }
        shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeBy(300)
        val pressed = glyph.fetchSemanticsNode().boundsInRoot
        assertTrue("The glyph must inherit its canvas spring transform", pressed.width > idle.width)
        canvas.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1500)
    }

    @Test fun `actual rows preserve text icon bounds and callbacks at portrait and landscape widths`() {
        val width = mutableStateOf(360.dp)
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        every { binder.player } returns player
        every { player.currentMediaItem } returns null
        val nav = mockk<NavController>(relaxed = true)
        var collapses = 0
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.requiredWidth(width.value).testTag("infoWidth")) {
                    InfoAlbumAndArtistModern(binder = binder, navController = nav,
                        media = UiMedia("id", "Title", "Artist", 0L, true), albumId = "album",
                        mediaId = "id", title = "Title", likedAt = null,
                        artistIds = listOf(Info("artist", "Artist")), isExplicit = false,
                        artist = "Artist", onCollapse = { collapses++ }, disableScrollingText = true)
                }
            }
        }
        for (size in listOf(360.dp, 600.dp)) {
            compose.runOnIdle { width.value = size; preferences.edit().putBoolean(glassEnabledKey, false).commit() }
            compose.onNodeWithTag("infoWidth").assertWidthIsEqualTo(size)
            val nativeIcons = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }.distinct()
            val nativeText = listOf("Title", "Artist").map { compose.onAllNodes(hasText(it)).fetchSemanticsNodes().map { node -> node.boundsInRoot }.distinct() }
            compose.onNodeWithTag("player.infoRail.surface").assertDoesNotExist()
            val nativeCanvases = listOf(R.drawable.album, R.drawable.person).map { icon ->
                compose.onNodeWithTag("player.infoIcon.canvas.$icon", useUnmergedTree = true)
                    .assertWidthIsEqualTo(34.dp).assertHeightIsEqualTo(34.dp)
                    .fetchSemanticsNode().boundsInRoot
            }
            assertTrue("Information canvases cannot overlap", nativeCanvases[0].bottom <= nativeCanvases[1].top)
            val nativeGlyphs = listOf(R.drawable.album, R.drawable.person).map { icon ->
                compose.onNodeWithTag("player.infoIcon.canvas.$icon", useUnmergedTree = true)
                    .captureToImage().also { export("native-$size-$icon", it) }
            }
            export("native-rows-$size", compose.onNodeWithTag("infoWidth").captureToImage())
            compose.onNodeWithTag("player.infoIcon.glyph.${R.drawable.album}", useUnmergedTree = true)
                .assertWidthIsEqualTo(26.dp).assertHeightIsEqualTo(26.dp)
            compose.onNodeWithTag("player.infoIcon.glyph.${R.drawable.person}", useUnmergedTree = true)
                .assertWidthIsEqualTo(22.dp).assertHeightIsEqualTo(24.dp)
            compose.runOnIdle { preferences.edit().putBoolean(glassEnabledKey, true).commit() }
            compose.onAllNodes(hasClickAction()).assertCountEquals(4)
            assertEquals(nativeIcons, compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }.distinct())
            assertEquals(nativeText, listOf("Title", "Artist").map { compose.onAllNodes(hasText(it)).fetchSemanticsNodes().map { node -> node.boundsInRoot }.distinct() })
            compose.onAllNodes(hasTestTag("player.infoRail.surface")).assertCountEquals(1)
            export("glass-rows-$size", compose.onNodeWithTag("infoWidth").captureToImage())
            for ((index, icon) in listOf(R.drawable.album, R.drawable.person).withIndex()) {
                val canvas = compose.onNodeWithTag("player.infoIcon.canvas.$icon", useUnmergedTree = true)
                    .assertWidthIsEqualTo(34.dp).assertHeightIsEqualTo(34.dp).fetchSemanticsNode().boundsInRoot
                assertEquals(nativeCanvases[index], canvas)
                val before = nativeGlyphs[index].toPixelMap()
                val after = compose.onNodeWithTag("player.infoIcon.canvas.$icon", useUnmergedTree = true)
                    .captureToImage().toPixelMap()
                assertEquals(before.width, after.width)
                assertEquals(before.height, after.height)
                fun glyphBounds(pixels: androidx.compose.ui.graphics.PixelMap): Rect {
                    val points = buildList {
                        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                            val color = pixels[x, y]
                            if (color.red > .8f && color.green > .8f && color.blue > .8f) add(x to y)
                        }
                    }
                    val brightest = (0 until pixels.height).flatMap { y ->
                        (0 until pixels.width).map { x -> pixels[x, y] }
                    }.maxBy { it.red + it.green + it.blue }
                    assertTrue("Actual vector $icon at $size must paint visible glyph pixels; brightest=$brightest", points.isNotEmpty())
                    return Rect(points.minOf { it.first }.toFloat(), points.minOf { it.second }.toFloat(),
                        points.maxOf { it.first }.toFloat() + 1f, points.maxOf { it.second }.toFloat() + 1f)
                }
                val nativePaint = glyphBounds(before)
                val glassPaint = glyphBounds(after)
                assertTrue("Glass glyph has breathing room inside its fixed canvas", glassPaint.width < nativePaint.width)
                assertTrue(glassPaint.height < nativePaint.height)
                assertEquals(nativePaint.center.x, glassPaint.center.x, 1f)
                assertEquals(nativePaint.center.y, glassPaint.center.y, 1f)
            }
            compose.onAllNodes(hasClickAction())[0].performClick()
            compose.onAllNodes(hasClickAction())[2].performClick()
        }
        assertEquals(4, collapses)
        compose.runOnIdle { preferences.edit().putBoolean(playerInfoShowIconsKey, false).commit() }
        compose.onNodeWithTag("player.infoRail.surface").assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
    }

    private fun export(name: String, image: ImageBitmap) {
        val directory = File("build/reports/player-info-pixels").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun `measured rail is passive and follows contour native and hidden modes`() {
        val enabled = mutableStateOf(true)
        val visible = mutableStateOf(true)
        val mode = mutableStateOf(GlassAreaMode.GlassAndContour)
        var clicks = 0
        compose.setContent {
            AppearanceProvider {
                PlayerInfoGlassRail(visible.value, GlassSettings(enabled = enabled.value,
                    areaModes = mapOf(GlassArea.ActionButtons to mode.value))) {
                    IconButton({ clicks++ }, R.drawable.album, Color.White,
                        Modifier.size(26.dp).albumIconBounds().testTag("album"), enabled = false)
                    Spacer(Modifier.height(19.dp))
                    IconButton({ clicks++ }, R.drawable.person, Color.White,
                        Modifier.size(24.dp).artistIconBounds().testTag("artist"))
                }
            }
        }
        val rail = compose.onNodeWithTag("player.infoRail.surface").fetchSemanticsNode().boundsInRoot
        val album = compose.onNodeWithTag("album").fetchSemanticsNode().boundsInRoot
        val artist = compose.onNodeWithTag("artist").fetchSemanticsNode().boundsInRoot
        assertTrue(rail.top < album.top && rail.bottom > artist.bottom)
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        compose.onNodeWithTag("album").performClick()
        compose.onNodeWithTag("artist").performClick()
        assertEquals(1, clicks)
        compose.runOnIdle { mode.value = GlassAreaMode.ContourOnly }
        compose.onNodeWithTag("player.infoRail.surface").assertExists()
        compose.runOnIdle { mode.value = GlassAreaMode.Disabled }
        compose.onNodeWithTag("player.infoRail.surface").assertDoesNotExist()
        compose.runOnIdle { enabled.value = false; mode.value = GlassAreaMode.GlassAndContour }
        compose.onNodeWithTag("player.infoRail.surface").assertDoesNotExist()
        compose.runOnIdle { enabled.value = true; visible.value = false }
        compose.onNodeWithTag("player.infoRail.surface").assertDoesNotExist()
    }

    @Test fun `rail samples live player scene without an unrelated fallback`() {
        val sceneColor = mutableStateOf(Color.Red)
        val available = mutableStateOf(true)
        compose.setContent {
            AppearanceProvider {
                val scene = rememberCanvasBackdrop { drawRect(sceneColor.value) }
                val unrelated = rememberCanvasBackdrop { drawRect(Color.Green) }
                CompositionLocalProvider(LocalGlassBackdrop provides unrelated,
                    LocalGlassContainerBackdrop provides if (available.value) scene else null) {
                    Box(Modifier.size(100.dp).background(Color.Black)) {
                        PlayerInfoGlassRail(true, GlassSettings(film = 0f, rim = 0f, blur = 0f,
                            depth = 0f, amount = 0f, vibrancy = 0f)) {
                            Box(Modifier.size(26.dp).albumIconBounds())
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.size(24.dp).artistIconBounds())
                        }
                    }
                }
            }
        }
        for (color in listOf(Color.Red, Color.Blue)) {
            compose.runOnIdle { sceneColor.value = color }
            val pixels = compose.onNodeWithTag("player.infoRail.surface").captureToImage().toPixelMap()
            val actual = pixels[pixels.width / 2, pixels.height / 2]
            assertEquals(color.red, actual.red, .08f)
            assertEquals(color.blue, actual.blue, .08f)
        }
        compose.runOnIdle { available.value = false }
        val pixels = compose.onNodeWithTag("player.infoRail.surface").captureToImage().toPixelMap()
        assertEquals(Color.Black, pixels[pixels.width / 2, pixels.height / 2])
    }

    @Test fun `vertical album artist rail follows the production UI corner cap at zero eight and forty eight`() {
        val radius = mutableStateOf(0.dp)
        compose.setContent {
            AppearanceProvider(shape = RoundedCornerShape(BoundedCornerSize(radius.value, .4f))) {
                val scene = rememberCanvasBackdrop { drawRect(Color.Red) }
                CompositionLocalProvider(LocalGlassContainerBackdrop provides scene) {
                    Box(Modifier.size(100.dp).background(Color.Black).padding(10.dp)) {
                        PlayerInfoGlassRail(true, GlassSettings(film = 0f, rim = 0f, blur = 0f,
                            depth = 0f, amount = 0f, vibrancy = 0f)) {
                            Box(Modifier.size(26.dp).albumIconBounds())
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.size(24.dp).artistIconBounds())
                        }
                    }
                }
            }
        }
        for (value in listOf(0.dp, 8.dp, 48.dp)) {
            compose.runOnIdle { radius.value = value }
            val pixels = compose.onNodeWithTag("player.infoRail.surface").captureToImage().toPixelMap()
            // A38dp-wide rail uses at most15.2dp corners with the ordinary .4 UI cap.
            // This sample is inside that outline but outside the former19dp search capsule.
            assertEquals("UI=$value keeps the ordinary vertical-group corner", Color.Red, pixels[2, 8])
            assertEquals(if (value == 0.dp) Color.Red else Color.Black, pixels[0, 0])
            assertEquals(Color.Red, pixels[2, pixels.height / 2])
            assertEquals(Color.Red, pixels[pixels.width / 2, pixels.height / 2])
        }
    }
    @Test fun `moving through a clipped viewport never truncates dynamic information geometry`() {
        val titleHeight = mutableStateOf(26.dp)
        val shift = mutableStateOf(0.dp)
        var measured: PlayerInfoGlassRailScope? = null
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.size(100.dp).clipToBounds()) {
                    Box(Modifier.offset(x = shift.value, y = shift.value).requiredSize(80.dp, 160.dp)) {
                        PlayerInfoGlassRail(true) {
                            measured = this
                            Row(Modifier.requiredHeight(titleHeight.value), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(26.dp).albumIconBounds())
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.requiredHeight(24.dp)) {
                                Box(Modifier.size(24.dp).artistIconBounds())
                            }
                        }
                    }
                }
            }
        }
        for (height in listOf(26.dp, 50.dp, 34.dp)) {
            for (offset in listOf(0.dp, (-18).dp, 70.dp, 0.dp)) {
                compose.runOnIdle { titleHeight.value = height; shift.value = offset }
                compose.runOnIdle {
                    val scope = checkNotNull(measured)
                    val top = with(compose.density) { ((height - 26.dp) / 2).toPx() }
                    val icon = with(compose.density) { 26.dp.toPx() }
                    val artistTop = with(compose.density) { (height + 10.dp).toPx() }
                    val artistSize = with(compose.density) { 24.dp.toPx() }
                    assertEquals(Rect(0f, top, icon, top + icon), scope.album)
                    assertEquals(Rect(0f, artistTop, artistSize, artistTop + artistSize), scope.artist)
                }
                val size = compose.onNodeWithTag("player.infoRail.surface").fetchSemanticsNode().layoutInfo.coordinates.size
                assertEquals(with(compose.density) { 38.dp.roundToPx() }, size.width)
                assertEquals(with(compose.density) { (height + 46.dp - (height - 26.dp) / 2).roundToPx() }, size.height)
            }
        }
    }
    @Test fun `actual information cap survives Controls size animation without shifting native text`() {
        val nativeClip = mutableStateOf(true)
        val modern = mutableStateOf(true)
        val height = mutableStateOf(70.dp)
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        every { binder.player } returns player
        every { player.currentMediaItem } returns null
        val nav = mockk<NavController>(relaxed = true)
        compose.setContent {
            AppearanceProvider {
                val scene = rememberCanvasBackdrop { drawRect(Color.Red) }
                CompositionLocalProvider(LocalGlassContainerBackdrop provides scene) {
                    Box(Modifier.size(200.dp).background(Color.Black).testTag("animationRoot")) {
                        Box(Modifier.padding(24.dp)) {
                            Box(if (nativeClip.value) Modifier.animateContentSize()
                                else Modifier.playerInfoAwareContentSize(modern.value)) {
                                Box(Modifier.requiredHeight(height.value)) {
                                    InfoAlbumAndArtistModern(binder = binder, navController = nav,
                                        media = UiMedia("id", "Title", "Artist", 0L, true), albumId = "album",
                                        mediaId = "id", title = "Title", likedAt = null,
                                        artistIds = listOf(Info("artist", "Artist")), isExplicit = false,
                                        artist = "Artist", onCollapse = {}, disableScrollingText = true)
                                }
                            }
                        }
                    }
                }
            }
        }
        val titleBounds = compose.onAllNodes(hasText("Title")).fetchSemanticsNodes().map { it.boundsInRoot }.distinct()
        fun capPixel() = compose.onNodeWithTag("animationRoot").captureToImage().toPixelMap()[47, 20]
        assertEquals(Color.Black, capPixel())
        compose.runOnIdle { nativeClip.value = false }
        assertTrue(capPixel() != Color.Black)
        assertEquals(titleBounds, compose.onAllNodes(hasText("Title")).fetchSemanticsNodes().map { it.boundsInRoot }.distinct())
        for (next in listOf(100.dp, 70.dp)) {
            compose.runOnIdle { height.value = next }
            assertTrue(capPixel() != Color.Black)
        }
        compose.runOnIdle { modern.value = false }
        // Glass transport can move beyond this slot even with Essential metadata.
        assertTrue(capPixel() != Color.Black)
        compose.runOnIdle { modern.value = true; preferences.edit().putBoolean(glassEnabledKey, false).commit() }
        compose.onNodeWithTag("player.infoRail.surface").assertDoesNotExist()
        assertEquals(titleBounds, compose.onAllNodes(hasText("Title")).fetchSemanticsNodes().map { it.boundsInRoot }.distinct())
    }
    @Test fun `unclipped animation retains native spring dimensions frame by frame`() {
        val size = mutableStateOf(50.dp)
        compose.setContent {
            AppearanceProvider {
                Row {
                    Box(Modifier.testTag("nativeSize").animateContentSize()) { Box(Modifier.size(size.value)) }
                    Box(Modifier.testTag("railSize").playerInfoAwareContentSize(true)) { Box(Modifier.size(size.value)) }
                    Box(Modifier.testTag("offSize").playerInfoAwareContentSize(true, GlassSettings(enabled = false))) {
                        Box(Modifier.size(size.value))
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        for (target in listOf(80.dp, 40.dp)) {
            compose.runOnIdle { size.value = target }
            for (frame in 0..12) {
                compose.mainClock.advanceTimeByFrame()
                val native = compose.onNodeWithTag("nativeSize").fetchSemanticsNode().boundsInRoot.size
                assertEquals(native, compose.onNodeWithTag("railSize").fetchSemanticsNode().boundsInRoot.size)
                assertEquals(native, compose.onNodeWithTag("offSize").fetchSemanticsNode().boundsInRoot.size)
            }
            compose.mainClock.advanceTimeBy(1500)
        }
        compose.mainClock.autoAdvance = true
    }
    @Composable private fun AppearanceProvider(
        shape: Shape = RoundedCornerShape(16.dp),
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(LocalAppearance provides Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        ), content = content)
    }
}
