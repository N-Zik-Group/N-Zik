package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.ui.test.hasText
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import app.it.fast4x.rimusic.models.ui.UiMedia
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.it.fast4x.rimusic.utils.DURATION_INDICATOR_HEIGHT
import app.it.fast4x.rimusic.utils.GetSeekBar
import app.it.fast4x.rimusic.utils.playerTimelineTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.LocalPlayerSheetState
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.every
import io.mockk.mockk
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.PlayerTimelineSize
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.LocalGlassOpticsEnabled
import app.n_zik.android.components.glass.LocalGlassUnderlayCovered
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
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
class PlayerInformationGlassBlockTest {
    @get:Rule val compose = createComposeRule()

    @org.junit.Before fun enableGlassFixture() {
        ApplicationProvider.getApplicationContext<Application>().preferences.edit()
            .clear().putBoolean(glassEnabledKey, true).commit()
    }

    @org.junit.After fun resetPreferences() {
        ApplicationProvider.getApplicationContext<Application>().preferences.edit().clear().commit()
    }

    @Test @Config(qualifiers = "w800dp-h1200dp")
    fun `one material preserves native bounds and actions at both widths and swapped placements`() {
        val enabled = mutableStateOf(false)
        val swapped = mutableStateOf(false)
        val width = mutableStateOf(300.dp)
        var clicks = 0
        compose.setContent { AppearanceProvider {
            PlayerInformationGlassBlock(Modifier.requiredWidth(width.value), GlassSettings(enabled = enabled.value)) {
                Column {
                    PlayerInformationGlassRegion("duration") { PlayerQueueDurationGlass { Box(Modifier.size(60.dp, 15.dp).testTag("duration")) } }
                    Spacer(Modifier.height(10.dp))
                    PlayerInformationGlassBlock { // The nested Controls adapter must never add a second plate.
                        Column {
                            PlayerInformationGlassRegion("info") {
                                PlayerInfoGlassRail(true) {
                                    Box(Modifier.size(220.dp, 42.dp).testTag("info")) {
                                        Box(Modifier.size(12.dp).albumIconBounds())
                                        Box(Modifier.offset(y = 25.dp).size(12.dp).artistIconBounds())
                                    }
                                }
                            }
                            Spacer(Modifier.height(25.dp))
                            if (swapped.value) Box(Modifier.size(80.dp).testTag("transport").clickable { clicks++ })
                            PlayerInformationGlassRegion("timeline") { Box(Modifier.size(240.dp, 40.dp).testTag("timeline")) }
                            if (!swapped.value) Box(Modifier.size(80.dp).testTag("transport").clickable { clicks++ })
                        }
                    }
                }
            }
        } }
        for (w in listOf(300.dp, 560.dp)) for (swap in listOf(false, true)) {
            compose.runOnIdle { width.value = w; swapped.value = swap; enabled.value = false }
            val native = listOf("duration", "info", "timeline", "transport").map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
            compose.onNodeWithTag("player.informationBlock.surface").assertDoesNotExist()
            compose.runOnIdle { enabled.value = true }
            assertEquals(native, listOf("duration", "info", "timeline", "transport").map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot })
            compose.onAllNodes(hasTestTag("player.informationBlock.surface")).assertCountEquals(1)
            compose.onAllNodes(hasTestTag("player.queueDuration.material")).assertCountEquals(1)
            compose.onAllNodes(hasTestTag("player.infoRail.surface")).assertCountEquals(1)
            compose.onAllNodes(hasClickAction()).assertCountEquals(1)
            val pane = compose.onNodeWithTag("player.informationBlock.surface").fetchSemanticsNode().boundsInRoot
            val transport = compose.onNodeWithTag("transport").fetchSemanticsNode().boundsInRoot
            if (swap) {
                assertTrue("Swapped transport lies between registered metadata and timeline: width=$w pane=$pane transport=$transport " +
                    "info=${compose.onNodeWithTag("info").fetchSemanticsNode().boundsInRoot} " +
                    "timeline=${compose.onNodeWithTag("timeline").fetchSemanticsNode().boundsInRoot}",
                    pane.top <= transport.top && pane.bottom >= transport.bottom)
            } else {
                val outset = with(compose.density) { 10.dp.toPx() }
                assertTrue("Unswapped transport follows the registered timeline",
                    pane.bottom <= transport.top + outset + 1f)
            }
            compose.onNodeWithTag("transport").performClick()
        }
        assertEquals(4, clicks)
    }

    @Test fun `outer material has equal ten dp outsets without moving content`() {
        compose.setContent { AppearanceProvider {
            Box(Modifier.padding(20.dp)) {
                PlayerInformationGlassBlock(settings = GlassSettings(enabled = true)) {
                    PlayerInformationGlassRegion("info") { Box(Modifier.size(200.dp, 100.dp).testTag("content")) }
                }
            }
        } }
        val content = compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
        val pane = compose.onNodeWithTag("player.informationBlock.surface").fetchSemanticsNode().boundsInRoot
        val padding = with(compose.density) { 10.dp.toPx() }
        assertEquals(padding, content.left - pane.left, 0.1f)
        assertEquals(padding, content.top - pane.top, 0.1f)
        assertEquals(padding, pane.right - content.right, 0.1f)
        assertEquals(padding, pane.bottom - content.bottom, 0.1f)
    }

    @Test @Config(qualifiers = "w800dp-h1200dp")
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun `real native timeline and timestamps retain full rectangles through block and glass toggles`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val preferences = context.preferences
        preferences.edit().clear().commit()
        val grouped = mutableStateOf(false)
        val enabled = mutableStateOf(false)
        val width = mutableStateOf(300.dp)
        val player = mockk<Player>(relaxed = true)
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        every { binder.player } returns player
        every { player.currentPosition } returns 30_000L
        every { player.duration } returns 120_000L
        val sheet = mockk<PlayerSheetState>(relaxed = true)
        every { sheet.progress } returns 1f
        val media = UiMedia("regression", "Title", "Artist", 120_000L, true)
        compose.setContent { AppearanceProvider {
            CompositionLocalProvider(LocalPlayerServiceBinder provides binder, LocalPlayerSheetState provides sheet) {
                Box(Modifier.padding(20.dp).requiredWidth(width.value)) {
                    @Composable fun NativeFlow() {
                        Column(Modifier.testTag("flow")) {
                            if (grouped.value) PlayerInformationGlassRegion("timeline", vertical = true,
                                horizontalContentInset = 10.dp) {
                                GetSeekBar({ 30_000L }, { 120_000L }, media.id, media, false, false)
                            } else GetSeekBar({ 30_000L }, { 120_000L }, media.id, media, false, false)
                        }
                    }
                    if (grouped.value) PlayerInformationGlassBlock(settings = GlassSettings(enabled = enabled.value)) {
                        NativeFlow()
                    } else NativeFlow()
                }
            }
        } }
        for (w in listOf(300.dp, 560.dp)) for (style in listOf(PlayerTimelineType.Default,
            PlayerTimelineType.Wavy, PlayerTimelineType.BodiedBar)) for (glass in listOf(false, true)) {
            compose.runOnIdle {
                width.value = w; grouped.value = false; enabled.value = glass
                preferences.edit().putString(playerTimelineTypeKey, style.name).putBoolean(glassEnabledKey, glass).commit()
            }
            // Compare the original parent Column and the new registration under the SAME mode.
            val nativeFlow = compose.onNodeWithTag("flow").fetchSemanticsNode().boundsInRoot
            if (style == PlayerTimelineType.BodiedBar) {
                assertTrue("BodiedBar must render its 15dp track with Glass off and on",
                    nativeFlow.height >= with(compose.density) { (15 + 8 + DURATION_INDICATOR_HEIGHT).dp.toPx() } - 1f)
            }
            val nativeTimes = compose.onAllNodes(hasText("0:30") or hasText("2:00"), useUnmergedTree = true)
                .fetchSemanticsNodes().map { it.boundsInRoot }
            assertTrue("Real DurationIndicator must emit timestamps", nativeTimes.isNotEmpty())
            val durationHeight = with(compose.density) { 20.dp.toPx() }
            assertTrue("Native timestamps must follow the track and 8dp gap", nativeTimes.all {
                it.top > nativeFlow.top && it.top >= nativeFlow.bottom - durationHeight - 1f
            })
            compose.runOnIdle { grouped.value = true }
            assertEquals("Track+8dp+timestamps height and origin must equal native $style $w glass=$glass",
                nativeFlow, compose.onNodeWithTag("flow").fetchSemanticsNode().boundsInRoot)
            assertEquals("Timestamp rectangles must equal native $style $w glass=$glass", nativeTimes,
                compose.onAllNodes(hasText("0:30") or hasText("2:00"), useUnmergedTree = true)
                    .fetchSemanticsNodes().map { it.boundsInRoot })
        }
    }

    @Test @Config(qualifiers = "w800dp-h1200dp")
    fun `outer padding follows visible inner capsules rather than their smaller layout slots`() {
        compose.setContent { AppearanceProvider {
            Box(Modifier.padding(30.dp)) {
                PlayerInformationGlassBlock {
                    Column {
                        PlayerInformationGlassRegion("duration") {
                            PlayerQueueDurationGlass { Box(Modifier.size(50.dp, 15.dp)) }
                        }
                        Spacer(Modifier.height(12.dp))
                        PlayerInformationGlassRegion("info", horizontalContentInset = 10.dp) {
                            PlayerInfoGlassRail(true) {
                                Box(Modifier.size(200.dp, 50.dp).padding(horizontal = 10.dp)) {
                                    Box(Modifier.size(26.dp).albumIconBounds())
                                    Box(Modifier.offset(y = 24.dp).size(26.dp).artistIconBounds())
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        PlayerInformationGlassRegion("timeline", vertical = true, horizontalContentInset = 10.dp) {
                            Box(Modifier.size(200.dp, 20.dp).testTag("timeline.anchor").padding(horizontal = 10.dp)) {
                                PlayerSkipTimeGlass { Box(Modifier.size(20.dp)) }
                                Box(Modifier.offset(x = 160.dp)) { PlayerSkipTimeGlass { Box(Modifier.size(20.dp)) } }
                            }
                        }
                    }
                }
            }
        } }
        val pane = compose.onNodeWithTag("player.informationBlock.surface").fetchSemanticsNode().boundsInRoot
        val inner = listOf("player.queueDuration.material", "player.infoRail.surface", "player.skipTime.material")
            .flatMap { tag -> compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().map { it.boundsInRoot } }
        val padding = with(compose.density) { 10.dp.toPx() }
        val leftOutset = inner.minOf { it.left } - pane.left
        val rightOutset = pane.right - inner.maxOf { it.right }
        assertTrue("Centered plate keeps at least the requested margin around every capsule",
            leftOutset >= padding - 0.1f && rightOutset >= padding - 0.1f)
        assertEquals("Symmetric expansion retains the requested margin on its limiting side",
            padding, minOf(leftOutset, rightOutset), 0.1f)
        val anchor = compose.onNodeWithTag("timeline.anchor").fetchSemanticsNode().boundsInRoot
        assertEquals("Plate stays centered on the native timeline", anchor.center.x, pane.center.x, 0.1f)
        assertEquals(padding, inner.minOf { it.top } - pane.top, 0.1f)
        assertEquals(padding, pane.bottom - inner.maxOf { it.bottom }, 0.1f)
    }

    @Test fun `hidden regions clear stale bounds and all area policies retain native footprint`() {
        val info = mutableStateOf(true)
        val seek = mutableStateOf(true)
        val mode = mutableStateOf(GlassAreaMode.GlassAndContour)
        compose.setContent { AppearanceProvider {
            PlayerInformationGlassBlock(settings = GlassSettings(enabled = true,
                areaModes = mapOf(GlassArea.ActionButtons to mode.value))) {
                Column {
                    if (info.value) PlayerInformationGlassRegion("info") { Box(Modifier.size(200.dp, 40.dp)) }
                    Spacer(Modifier.height(40.dp))
                    if (seek.value) PlayerInformationGlassRegion("seek") { Box(Modifier.size(200.dp, 35.dp).testTag("seek")) }
                }
            }
        } }
        val full = compose.onNodeWithTag("player.informationBlock.surface").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { info.value = false }
        val short = compose.onNodeWithTag("player.informationBlock.surface").fetchSemanticsNode().boundsInRoot
        assertTrue(short.height < full.height)
        val seekBounds = compose.onNodeWithTag("seek").fetchSemanticsNode().boundsInRoot
        for (policy in GlassAreaMode.entries) {
            compose.runOnIdle { mode.value = policy }
            assertEquals(seekBounds, compose.onNodeWithTag("seek").fetchSemanticsNode().boundsInRoot)
        }
        compose.runOnIdle { mode.value = GlassAreaMode.GlassAndContour; seek.value = false }
        compose.onNodeWithTag("player.informationBlock.surface").assertDoesNotExist()
    }

    @Test fun `plate samples child free player source with global optics and modal gates`() {
        val scene = mutableStateOf(Color.Red)
        val optical = mutableStateOf(true)
        val covered = mutableStateOf(false)
        compose.setContent { AppearanceProvider {
            val backdrop = rememberCanvasBackdrop { drawRect(scene.value) }
            val unrelated = rememberCanvasBackdrop { drawRect(Color.Green) }
            CompositionLocalProvider(LocalGlassContainerBackdrop provides backdrop,
                LocalGlassBackdrop provides unrelated, LocalGlassOpticsEnabled provides optical.value,
                LocalGlassUnderlayCovered provides covered.value) {
                Box(Modifier.size(300.dp).background(Color.Black).testTag("scene")) {
                    PlayerInformationGlassBlock(settings = GlassSettings(enabled = true, film = 0f)) {
                        PlayerInformationGlassRegion("info") { Box(Modifier.size(240.dp, 120.dp)) }
                    }
                }
            }
        } }
        fun pixel() = compose.onNodeWithTag("scene").captureToImage().toPixelMap().let { it[80, 60] }
        val red = pixel()
        assertTrue(red.red > red.blue && red.red > red.green)
        compose.runOnIdle { scene.value = Color.Blue }
        val blue = pixel()
        assertTrue(blue.blue > blue.red && blue.blue > blue.green)
        compose.runOnIdle { optical.value = false }
        val gated = pixel()
        assertTrue(gated.blue < blue.blue)
        compose.runOnIdle { optical.value = true; covered.value = true }
        assertEquals(gated, pixel())
    }

    @Test @Config(qualifiers = "w800dp-h1200dp")
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    fun `actual metadata preserves native sibling flow for all sizes and both orientation widths`() {
        val preferences = ApplicationProvider.getApplicationContext<Application>().preferences
        preferences.edit().clear().putString(playerTimelineTypeKey, PlayerTimelineType.Default.name).commit()
        val grouped = mutableStateOf(false)
        val modern = mutableStateOf(false)
        val glass = mutableStateOf(false)
        val width = mutableStateOf(360.dp)
        val inset = mutableStateOf(90.dp)
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        every { binder.player } returns player
        every { player.currentMediaItem } returns null
        every { player.currentPosition } returns 30_000L
        every { player.duration } returns 120_000L
        val sheet = mockk<PlayerSheetState>(relaxed = true)
        every { sheet.progress } returns 1f
        val nav = mockk<androidx.navigation.NavController>(relaxed = true)
        val media = UiMedia("layout", "Layout title", "Layout artist", 120_000L, true)
        compose.setContent { AppearanceProvider {
            CompositionLocalProvider(LocalPlayerServiceBinder provides binder, LocalPlayerSheetState provides sheet) {
            Box(Modifier.requiredWidth(width.value).testTag("viewport")) {
                @Composable fun Metadata() {
                    if (modern.value) app.it.fast4x.rimusic.ui.screens.player.components.controls.InfoAlbumAndArtistModern(
                        binder, nav, "album", media, media.id, "Layout title", null, null, false, "Layout artist", {}, true)
                    else app.it.fast4x.rimusic.ui.screens.player.components.controls.InfoAlbumAndArtistEssential(
                        binder, nav, "album", media, media.id, "Layout title", null, null, "Layout artist", false, {}, true)
                }
                @Composable fun NativeFlow() {
                    Column(Modifier.padding(horizontal = inset.value).testTag("metadata.flow"),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        PlayerInformationGlassRegion("queueDuration") {
                            PlayerQueueDurationGlass {
                                androidx.compose.foundation.text.BasicText("2 h 15 min", Modifier.testTag("queue.label"))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (grouped.value) PlayerInformationGlassRegion("info", vertical = !modern.value,
                            horizontalContentInset = 10.dp) { Metadata() }
                        else Metadata()
                        Spacer(Modifier.height(25.dp))
                        if (grouped.value) PlayerInformationGlassRegion("timeline", vertical = true,
                            horizontalContentInset = 10.dp) {
                            GetSeekBar({ 30_000L }, { 120_000L }, media.id, media, false, false)
                        } else GetSeekBar({ 30_000L }, { 120_000L }, media.id, media, false, false)
                    }
                }
                if (grouped.value) PlayerInformationGlassBlock(settings = GlassSettings(enabled = glass.value)) {
                    NativeFlow()
                } else NativeFlow()
            }
        } } }
        for (w in listOf(360.dp, 640.dp)) for (size in app.it.fast4x.rimusic.enums.PlayerTimelineSize.entries)
            for (isModern in listOf(false, true)) for (enabled in listOf(false, true)) {
                compose.runOnIdle {
                    width.value = w; inset.value = size.size.dp; modern.value = isModern
                    grouped.value = false; glass.value = enabled
                    preferences.edit().putBoolean(glassEnabledKey, enabled).commit()
                }
                val native = compose.onNodeWithTag("metadata.flow").fetchSemanticsNode().boundsInRoot
                val nativeText = compose.onAllNodes(hasText("Layout title") or hasText("Layout artist"), useUnmergedTree = true)
                    .fetchSemanticsNodes().map { it.boundsInRoot }
                assertTrue("Real metadata must emit both lines", nativeText.size >= 2)
                if (!isModern) {
                    val titleBottom = compose.onAllNodes(hasText("Layout title"), useUnmergedTree = true)
                        .fetchSemanticsNodes().maxOf { it.boundsInRoot.bottom }
                    val artistTop = compose.onAllNodes(hasText("Layout artist"), useUnmergedTree = true)
                        .fetchSemanticsNodes().minOf { it.boundsInRoot.top }
                    assertTrue("Essential artist must follow title and spacer", artistTop > titleBottom)
                }
                compose.runOnIdle { grouped.value = true }
                assertEquals("Native metadata flow $w $size modern=$isModern glass=$enabled", native,
                    compose.onNodeWithTag("metadata.flow").fetchSemanticsNode().boundsInRoot)
                assertEquals(nativeText, compose.onAllNodes(hasText("Layout title") or hasText("Layout artist"), useUnmergedTree = true)
                    .fetchSemanticsNodes().map { it.boundsInRoot })
                if (enabled) {
                    val pane = compose.onNodeWithTag("player.informationBlock.surface").fetchSemanticsNode().boundsInRoot
                    val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
                    val queue = compose.onNodeWithTag("player.queueDuration.material").fetchSemanticsNode().boundsInRoot
                    assertEquals("Queue capsule centered on plate $w $size modern=$isModern", queue.center.x, pane.center.x, 1f)
                    assertEquals("Plate centered on native viewport $w $size modern=$isModern", viewport.center.x, pane.center.x, 1f)
                    assertEquals("Equal viewport side gaps $w $size modern=$isModern",
                        pane.left - viewport.left, viewport.right - pane.right, 1f)
                    val visualCapsules = listOf("player.queueDuration.material", "player.infoRail.surface", "player.skipTime.material")
                        .flatMap { tag -> compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().map { it.boundsInRoot } }
                    if (size != app.it.fast4x.rimusic.enums.PlayerTimelineSize.Expanded) {
                        val minimumOutset = with(compose.density) { 10.dp.toPx() } - 1f
                        assertTrue("At least 10dp outside every passive capsule $w $size modern=$isModern",
                            visualCapsules.all { it.left - pane.left >= minimumOutset && pane.right - it.right >= minimumOutset })
                    }
                    assertTrue("Plate must remain within viewport $w $size", pane.left >= viewport.left && pane.right <= viewport.right)
                }
            }
    }

    @Test @Config(qualifiers = "w1000dp-h500dp-land")
    fun `landscape plate shares toolbar edges across timeline sizes without moving native content`() {
        val enabled = mutableStateOf(false)
        val inset = mutableStateOf(30.dp)
        val modern = mutableStateOf(false)
        val safeInset = mutableStateOf(0)
        compose.setContent { AppearanceProvider {
            PlayerLandscapeColumns(
                modifier = Modifier.requiredWidth(800.dp).height(300.dp),
                contentInsets = WindowInsets(left = safeInset.value, right = safeInset.value / 2),
                showArtworkColumn = false,
                artwork = {},
                controls = {
                    PlayerInformationGlassBlock(
                        modifier = Modifier.fillMaxWidth(),
                        settings = GlassSettings(enabled = enabled.value),
                        landscape = true,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = inset.value)) {
                            PlayerInformationGlassRegion("info", horizontalContentInset = 10.dp) {
                                Box(Modifier.fillMaxWidth().height(if (modern.value) 44.dp else 32.dp)
                                    .testTag("landscape.metadata"))
                            }
                            Spacer(Modifier.height(25.dp))
                            PlayerInformationGlassRegion("timeline", horizontalContentInset = 10.dp) {
                                Box(Modifier.fillMaxWidth().height(48.dp).testTag("landscape.timeline"))
                            }
                        }
                    }
                    Box(Modifier.playerActionToolbarWidth(landscape = true).height(56.dp)
                        .testTag("landscape.toolbar"))
                },
            )
        } }
        fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        for (safe in listOf(0, 48)) for (size in PlayerTimelineSize.entries)
            for (isModern in listOf(false, true)) {
                compose.runOnIdle {
                    enabled.value = false; safeInset.value = safe
                    inset.value = size.size.dp; modern.value = isModern
                }
                val nativeContent = listOf(bounds("landscape.metadata"), bounds("landscape.timeline"))
                val toolbar = bounds("landscape.toolbar")
                compose.runOnIdle { enabled.value = true }
                val plate = bounds("player.informationBlock.surface")
                assertEquals("Left edge $safe $size modern=$isModern", toolbar.left, plate.left, 1f)
                assertEquals("Right edge $safe $size modern=$isModern", toolbar.right, plate.right, 1f)
                assertEquals(nativeContent, listOf(bounds("landscape.metadata"), bounds("landscape.timeline")))
                assertEquals(toolbar, bounds("landscape.toolbar"))
            }
    }

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(16.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
            typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape), content = content)
    }
}
