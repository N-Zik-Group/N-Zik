package app.n_zik.android.components.player

import android.app.Application
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.PlayerPlayButtonType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.LocalPlayerSheetState
import app.it.fast4x.rimusic.models.ui.UiMedia
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.enums.PlayerInfoType
import app.it.fast4x.rimusic.enums.PlayerTimelineSize
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.FormatTable
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.extensions.audiobar.utils.WaveformResult
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.junit.Rule
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
class PlayerControlsTimelineLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val player = mockk<Player>(relaxed = true)
    private val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
    private val preferences get() = ApplicationProvider.getApplicationContext<Application>().preferences

    @Before fun setup() {
        preferences.edit().clear().putBoolean("buttonzoomout", false).commit()
        every { binder.player } returns player
        every { player.currentMediaItem } returns null
        val songs = mockk<SongTable>()
        val formats = mockk<FormatTable>()
        every { songs.likeState(any()) } returns flowOf(null)
        every { songs.findById(any()) } returns flowOf(null)
        every { formats.findBySongId(any()) } returns flowOf(null)
        mockkObject(Database)
        every { Database.songTable } returns songs
        every { Database.formatTable } returns formats
        listenTogetherGuestLock.value = false
    }

    @After fun cleanup() {
        preferences.edit().clear().commit()
        listenTogetherGuestLock.value = false
        unmockkAll()
    }

    @Test @Config(qualifiers = "w800dp-h1200dp")
    fun `actual GetControls fits native buttons across timeline sizes styles and both orientation widths`() {
        val width = mutableStateOf(360.dp)
        val inset = mutableStateOf(90.dp)
        compose.setContent { AppearanceProvider {
            Box(Modifier.requiredWidth(width.value).testTag("viewport")) {
                Column(Modifier.padding(horizontal = inset.value)) {
                    app.it.fast4x.rimusic.utils.GetControls(binder, { 0L }, false, false, null,
                        "transport-layout", {}, DefaultDarkColorPalette, timelineHorizontalInset = inset.value)
                }
            }
        } }
        for (w in listOf(360.dp, 640.dp)) for (size in app.it.fast4x.rimusic.enums.PlayerTimelineSize.entries)
            for (variant in app.it.fast4x.rimusic.enums.PlayerControlsType.entries)
                for (style in PlayerPlayButtonType.entries) {
                    compose.runOnIdle {
                        width.value = w; inset.value = size.size.dp
                        preferences.edit().putString(app.it.fast4x.rimusic.utils.playerControlsTypeKey, variant.name)
                            .putString(app.it.fast4x.rimusic.utils.playerPlayButtonTypeKey, style.name)
                            .putBoolean(glassEnabledKey, false).commit()
                    }
                    val native = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }
                    assertEquals(if (variant == app.it.fast4x.rimusic.enums.PlayerControlsType.Modern) 3 else 5, native.size)
                    val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
                    assertTrue("Native buttons stay visible $w $size $variant $style", native.all {
                        it.width > 0 && it.left >= viewport.left && it.right <= viewport.right
                    })
                    assertEquals("Transport must stay centered $w $size $variant $style", viewport.center.x,
                        (native.first().left + native.last().right) / 2f, 1f)
                    val slotWidth = with(compose.density) { (w - size.size.dp * 2).toPx() }
                    val maxWidth = with(compose.density) { (w - minOf(size.size.dp, 10.dp) * 2).toPx() }
                    // Modern Disabled uses a 54dp shadow/layout frame around its 44dp
                    // centered play hit target and 54dp skip frames around 34dp targets.
                    // SpaceEvenly distributes the frames; clickable glyph gaps include
                    // their inner padding. Assert the native frames' actual arrangement.
                    val disabledModern = variant == app.it.fast4x.rimusic.enums.PlayerControlsType.Modern &&
                        style == PlayerPlayButtonType.Disabled
                    val essential = variant == app.it.fast4x.rimusic.enums.PlayerControlsType.Essential
                    // Small Essential controls draw and accept input on 46dp canvases, but
                    // keep their original 26dp Row slots. Intrinsic fitting reserves the canvas.
                    val frameWidths = if (disabledModern) List(3) { with(compose.density) { 54.dp.toPx() } }
                        else native.mapIndexed { index, bounds ->
                            if (essential && index in listOf(0, native.lastIndex))
                                with(compose.density) { 26.dp.toPx() } else bounds.width
                        }
                    val usedWidth = frameWidths.sum()
                    val minimumEnvelopeWidth = if (essential) native.sumOf { it.width.toDouble() }.toFloat() else usedWidth
                    val transportWidth = maxOf(slotWidth, minimumEnvelopeWidth + with(compose.density) { 20.dp.toPx() })
                        .coerceAtMost(maxWidth)
                    val expectedGap = (transportWidth - usedWidth) / (native.size + 1)
                    val centers = native.map { it.center.x }
                    centers.zipWithNext().forEachIndexed { index, (leftCenter, rightCenter) ->
                        val expectedCenterDistance = frameWidths[index] / 2 + expectedGap + frameWidths[index + 1] / 2
                        assertEquals("Native evenly-spaced frame distance $w $size $variant $style", expectedCenterDistance,
                            rightCenter - leftCenter, 2f)
                    }
                    native.zipWithNext().forEach { (left, right) ->
                        assertTrue("Native buttons never overlap $w $size $variant $style", left.right <= right.left + 1f)
                    }
                    compose.runOnIdle { preferences.edit().putBoolean(glassEnabledKey, true).commit() }
                    assertEquals("Glass retains corrected native transport $w $size $variant $style", native,
                        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot })
                }
    }

    @Test @Config(qualifiers = "w800dp-h400dp-land")
    @OptIn(androidx.compose.animation.ExperimentalAnimationApi::class,
        androidx.compose.ui.text.ExperimentalTextApi::class)
    fun `actual landscape Controls reserves lower toolbar within short panel constraints`() {
        val panelHeight = mutableStateOf(320.dp)
        val playerType = mutableStateOf(PlayerType.Essential)
        val glass = mutableStateOf(false)
        val sheet = mockk<PlayerSheetState>(relaxed = true)
        every { sheet.progress } returns 1f
        val nav = mockk<androidx.navigation.NavController>(relaxed = true)
        val media = UiMedia("short-panel", "Panel title", "Panel artist", 120_000L, true)
        compose.setContent { AppearanceProvider {
            CompositionLocalProvider(LocalPlayerServiceBinder provides binder, LocalPlayerSheetState provides sheet,
                LocalPlayerTransportGeometry provides remember { PlayerTransportGeometry() }) {
                Column(Modifier.size(360.dp, panelHeight.value).playerTransportGeometryHost().testTag("short.panel")
                    .padding(top = if (playerType.value == PlayerType.Essential) 40.dp else 20.dp)) {
                    if (playerType.value == PlayerType.Modern) Spacer(Modifier.weight(1f))
                    app.it.fast4x.rimusic.ui.screens.player.Controls(
                        navController = nav, onCollapse = {}, onBlurScaleChange = {},
                        expandedplayer = false, titleExpanded = false, timelineExpanded = false,
                        controlsExpanded = false, isShowingLyrics = false, media = media,
                        mediaId = media.id, title = "Panel title", artist = "Panel artist",
                        artistIds = null, albumId = null, shouldBePlaying = false, isBuffering = false,
                        position = { 30_000L }, duration = { 120_000L }, isExplicit = false,
                        dynamicColorPalette = DefaultDarkColorPalette,
                        modifier = if (playerType.value == PlayerType.Essential)
                            Modifier.padding(vertical = 8.dp).fillMaxHeight().weight(1f).testTag("short.controls")
                        else Modifier.padding(vertical = 8.dp).weight(1f).testTag("short.controls"),
                    )
                    val layout = rememberPlayerActionToolbarLayout(GlassSettings(enabled = glass.value))
                    val settings = GlassSettings(enabled = glass.value)
                    Column(Modifier.fillMaxWidth().requiredHeight(layout.hostHeight(50.dp))
                        .testTag("short.toolbar.host"), verticalArrangement = Arrangement.SpaceAround) {
                        PlayerActionToolbar(listOf("lyrics", "menu").map { id -> PlayerActionSlot(id) {
                            PlayerActionIcon(app.n_zik.android.R.drawable.play, Color.White, {}, Modifier.size(24.dp))
                        } }, true, settings = settings, layout = layout, landscape = true)
                    }
                }
            }
        } }
        val nativeBounds = mutableMapOf<String, List<androidx.compose.ui.geometry.Rect>>()
        for (height in listOf(320.dp, 360.dp)) for (type in listOf(PlayerType.Essential, PlayerType.Modern))
            for (info in PlayerInfoType.entries) for (size in PlayerTimelineSize.entries)
                for (enabled in listOf(false, true, false)) {
                    compose.runOnIdle {
                        panelHeight.value = height; playerType.value = type; glass.value = enabled
                        preferences.edit().putString(app.it.fast4x.rimusic.utils.playerTypeKey, type.name)
                            .putString(app.it.fast4x.rimusic.utils.playerInfoTypeKey, info.name)
                            .putString(app.it.fast4x.rimusic.utils.playerTimelineSizeKey, size.name)
                            .putString(app.it.fast4x.rimusic.utils.playerTimelineTypeKey,
                                app.it.fast4x.rimusic.enums.PlayerTimelineType.Default.name)
                            .putBoolean(glassEnabledKey, enabled).commit()
                    }
                    val panel = compose.onNodeWithTag("short.panel").fetchSemanticsNode().boundsInRoot
                    val toolbar = compose.onNodeWithTag("short.toolbar.host").fetchSemanticsNode().boundsInRoot
                    val controls = compose.onNodeWithTag("short.controls").fetchSemanticsNode().boundsInRoot
                    val context = "$height $type $info $size glass=$enabled panel=$panel controls=$controls toolbar=$toolbar"
                    assertTrue("Controls leave the toolbar its own space $context",
                        controls.bottom <= toolbar.top + 1f)
                    assertTrue("Toolbar fits the actual viewport $context",
                        toolbar.top >= panel.top && toolbar.bottom <= panel.bottom + 1f)
                    if (enabled) {
                        val surface = compose.onNodeWithTag("player.actionToolbar.material", useUnmergedTree = true)
                            .fetchSemanticsNode().boundsInRoot
                        assertTrue("Glass material fits its toolbar host $context surface=$surface",
                            surface.top >= toolbar.top && surface.bottom <= toolbar.bottom + 1f)
                        val plate = compose.onNodeWithTag("player.informationBlock.surface", useUnmergedTree = true)
                            .fetchSemanticsNode().boundsInRoot
                        assertTrue("Information plate stays inside the panel viewport $context plate=$plate",
                            plate.top >= panel.top - 1f && plate.bottom <= panel.bottom + 1f)
                        assertEquals("Information plate remains horizontally centered $context plate=$plate",
                            panel.center.x, plate.center.x, 1f)
                    } else {
                        val key = "$height $type $info $size"
                        val bounds = listOf(controls, toolbar) + compose.onAllNodes(hasClickAction())
                            .fetchSemanticsNodes().map { it.boundsInRoot }
                        val baseline = nativeBounds.putIfAbsent(key, bounds)
                        if (baseline != null) assertEquals("Turning Glass off restores native layout exactly $key", baseline, bounds)
                    }
                }
    }

    @Test @Config(qualifiers = "w800dp-h500dp-land")
    @OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.ui.text.ExperimentalTextApi::class)
    fun `landscape transport balances visible gaps in both modes and timeline orders`() = verifyBalancedTransport(false)

    @Test @Config(qualifiers = "w800dp-h1200dp-port")
    @OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.ui.text.ExperimentalTextApi::class)
    fun `portrait transport balances visible gaps in both modes and timeline orders`() = verifyBalancedTransport(true)

    @OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.ui.text.ExperimentalTextApi::class)
    private fun verifyBalancedTransport(portrait: Boolean, focused: Boolean = false) {
        val glass = mutableStateOf(false)
        val translation = mutableStateOf(0f)
        val viewportSize = mutableStateOf(360.dp to if (portrait) 700.dp else 480.dp)
        val portraitLayout = mutableStateOf(portrait)
        val sheet = mockk<PlayerSheetState>(relaxed = true)
        every { sheet.progress } returns 1f
        val nav = mockk<androidx.navigation.NavController>(relaxed = true)
        val media = UiMedia("centered-panel", "Panel title", "Panel artist", 120_000L, true)
        compose.setContent { AppearanceProvider {
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = if (portraitLayout.value) Configuration.ORIENTATION_PORTRAIT
                    else Configuration.ORIENTATION_LANDSCAPE
                screenWidthDp = viewportSize.value.first.value.toInt()
                screenHeightDp = viewportSize.value.second.value.toInt()
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalPlayerServiceBinder provides binder, LocalPlayerSheetState provides sheet,
                LocalPlayerTransportGeometry provides remember { PlayerTransportGeometry() }) {
                val currentPortrait = portraitLayout.value
                Column(Modifier.size(viewportSize.value.first, viewportSize.value.second)
                    .graphicsLayer { translationY = translation.value * 16.dp.toPx() }
                    .playerTransportGeometryHost().testTag("center.viewport")) {
                    @Composable fun RealControls() {
                        app.it.fast4x.rimusic.ui.screens.player.Controls(
                            navController = nav, onCollapse = {}, onBlurScaleChange = {},
                            expandedplayer = false, titleExpanded = false, timelineExpanded = false,
                            controlsExpanded = false, isShowingLyrics = false, media = media, mediaId = media.id,
                            title = "Panel title", artist = "Panel artist", artistIds = null, albumId = null,
                            shouldBePlaying = false, isBuffering = false, position = { 30_000L }, duration = { 120_000L },
                            isExplicit = false, dynamicColorPalette = DefaultDarkColorPalette,
                            modifier = if (currentPortrait) Modifier.fillMaxWidth()
                                else Modifier.fillMaxHeight().weight(1f),
                        )
                    }
                    if (currentPortrait) PlayerInformationGlassBlock(Modifier.weight(1f)) {
                        Column { Box(Modifier.weight(1f)) { RealControls() } }
                    } else RealControls()
                    val layout = rememberPlayerActionToolbarLayout(GlassSettings(enabled = glass.value))
                    val settings = GlassSettings(enabled = glass.value)
                    Column(Modifier.fillMaxWidth().requiredHeight(layout.hostHeight(50.dp)),
                        verticalArrangement = Arrangement.SpaceAround) {
                        PlayerActionToolbar(listOf("lyrics", "menu").map { id -> PlayerActionSlot(id) {
                            PlayerActionIcon(app.n_zik.android.R.drawable.play, Color.White, {}, Modifier.size(24.dp).testTag("center.lower.$id"))
                        } }, true, settings = settings, layout = layout, landscape = !currentPortrait)
                    }
                }
            }
        } }
        for (size in if (focused) listOf(PlayerTimelineSize.Small) else PlayerTimelineSize.entries)
            for (info in if (focused) listOf(PlayerInfoType.Essential) else PlayerInfoType.entries)
            for (controls in if (focused) listOf(app.it.fast4x.rimusic.enums.PlayerControlsType.Modern)
                else app.it.fast4x.rimusic.enums.PlayerControlsType.entries)
                for (swapped in listOf(false, true)) for (enabled in listOf(false, true)) {
                    compose.runOnIdle {
                        glass.value = enabled
                        preferences.edit().putBoolean(glassEnabledKey, enabled)
                            .putString(app.it.fast4x.rimusic.utils.playerTypeKey, PlayerType.Essential.name)
                            .putString(app.it.fast4x.rimusic.utils.playerInfoTypeKey, info.name)
                            .putString(app.it.fast4x.rimusic.utils.playerControlsTypeKey, controls.name)
                            .putString(app.it.fast4x.rimusic.utils.playerTimelineSizeKey, size.name)
                            .putBoolean(app.it.fast4x.rimusic.utils.playerSwapControlsWithTimelineKey, swapped)
                            .putString(app.it.fast4x.rimusic.utils.playerTimelineTypeKey,
                                app.it.fast4x.rimusic.enums.PlayerTimelineType.Default.name).commit()
                    }
                    fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val transport = bounds("player.transport.row")
                    val timeline = bounds("player.informationRegion.timeline")
                    val metadata = bounds("player.informationRegion.info")
                    val toolbar = bounds(if (enabled) "player.actionToolbar.material" else "center.lower.lyrics")
                    val padding = with(compose.density) { if (enabled) 10.dp.toPx() else 0f }
                    val infoCapsules = compose.onAllNodes(hasTestTag("player.infoRail.surface"), useUnmergedTree = true)
                        .fetchSemanticsNodes().map { it.boundsInRoot }
                    val timeCapsules = compose.onAllNodes(hasTestTag("player.skipTime.material"), useUnmergedTree = true)
                        .fetchSemanticsNodes().map { it.boundsInRoot }
                    val above = if (swapped) maxOf(metadata.bottom, infoCapsules.maxOfOrNull { it.bottom } ?: metadata.bottom) + padding
                        else if (enabled) bounds("player.informationBlock.surface").bottom else maxOf(metadata.bottom, timeline.bottom)
                    val below = if (swapped) minOf(timeline.top, timeCapsules.minOfOrNull { it.top } ?: timeline.top) - padding
                        else toolbar.top
                    val context = "portrait=$portrait glass=$enabled size=$size info=$info controls=$controls swap=$swapped " +
                        "above=$above below=$below transport=$transport toolbar=$toolbar"
                    val duration = bounds("player.timeline.duration")
                    val anchorAbove = if (!swapped) {
                        if (enabled) bounds("player.informationBlock.surface").bottom else duration.center.y
                    } else above
                    val anchorBelow = if (!enabled && swapped) duration.center.y else below
                    val requestedTop = (anchorAbove + anchorBelow - transport.height) / 2f
                    val expectedTop = if (below - transport.height >= above)
                        requestedTop.coerceIn(above, below - transport.height) else requestedTop
                    assertEquals("Transport balances against the information edge or time labels within collision bounds $context",
                        expectedTop, transport.top, 2f)
                    val transportButtons = compose.onAllNodes(hasClickAction(), useUnmergedTree = true)
                        .fetchSemanticsNodes().map { it.boundsInRoot }.filter {
                            it.center.y >= transport.top - 1f && it.center.y <= transport.bottom + 1f &&
                                it.center.x >= bounds("center.viewport").left && it.center.x <= bounds("center.viewport").right
                        }
                    assertEquals("Actual transport click targets retain their configured count $context",
                        if (controls == app.it.fast4x.rimusic.enums.PlayerControlsType.Modern) 3 else 5, transportButtons.size)
                    val visibleCenter = (transportButtons.minOf { it.top } + transportButtons.maxOf { it.bottom }) / 2f
                    assertEquals("Actual transport glyph/button union is vertically centered $context",
                        expectedTop + transport.height / 2f, visibleCenter, 2f)
                    assertTrue("Transport stays between its configured neighbors $context",
                        transport.top >= above - 1f && transport.bottom <= below + 1f)
                    if (focused && !swapped) {
                        compose.mainClock.autoAdvance = false
                        for ((width, height) in listOf(700.dp to 420.dp, 360.dp to 700.dp,
                            620.dp to 380.dp, 360.dp to 700.dp)) {
                            compose.runOnIdle {
                                viewportSize.value = width to height
                                portraitLayout.value = width < height
                                Snapshot.sendApplyNotifications()
                            }
                            compose.mainClock.advanceTimeByFrame()
                            compose.waitForIdle()
                            val firstFrameTransport = bounds("player.transport.row")
                            val firstFrameDuration = bounds("player.timeline.duration")
                            val firstFrameToolbar = bounds(if (enabled)
                                "player.actionToolbar.material" else "center.lower.lyrics")
                            val firstFrameReference = if (enabled)
                                bounds("player.informationBlock.surface").bottom else firstFrameDuration.center.y
                            val clearance = if (enabled) firstFrameReference else firstFrameDuration.bottom
                            val desired = (firstFrameReference + firstFrameToolbar.top - firstFrameTransport.height) / 2f
                            val expected = if (firstFrameToolbar.top - firstFrameTransport.height >= clearance)
                                desired.coerceIn(clearance, firstFrameToolbar.top - firstFrameTransport.height) else desired
                            assertEquals("First frame after player resize $width x $height glass=$enabled",
                                expected, firstFrameTransport.top, 2f)
                            assertTrue("First frame transport does not pile into metadata after resize",
                                firstFrameTransport.top >= bounds("player.informationRegion.info").bottom - 1f)
                            compose.mainClock.advanceTimeByFrame()
                        }
                        compose.mainClock.autoAdvance = true
                    }
                    if (size == PlayerTimelineSize.Small && info == PlayerInfoType.Essential &&
                        controls == app.it.fast4x.rimusic.enums.PlayerControlsType.Essential && !swapped) {
                        val viewport = bounds("center.viewport")
                        val before = transportButtons.map { it.translate(-viewport.left, -viewport.top) }
                        for (progress in listOf(0f, .25f, .75f, 1f, .75f, .25f, 0f)) {
                            compose.runOnIdle { translation.value = progress }
                            val movedViewport = bounds("center.viewport")
                            val movedTransport = bounds("player.transport.row")
                            val movedButtons = compose.onAllNodes(hasClickAction(), useUnmergedTree = true)
                                .fetchSemanticsNodes().map { it.boundsInRoot }.filter {
                                    it.center.y >= movedTransport.top - 1f && it.center.y <= movedTransport.bottom + 1f &&
                                        it.center.x >= movedViewport.left && it.center.x <= movedViewport.right
                                }
                            assertEquals("Opening/closing preserves all relative button rectangles progress=$progress $context",
                                before, movedButtons.map { it.translate(-movedViewport.left, -movedViewport.top) })
                            assertEquals("Only the player translation moves the actual buttons progress=$progress $context",
                                transportButtons.first().top + with(compose.density) { (16.dp * progress).toPx() },
                                movedButtons.first().top, 1f)
                        }
                    }
                }
    }

    @Test @Config(qualifiers = "w800dp-h1200dp-port")
    @OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.ui.text.ExperimentalTextApi::class)
    fun `real portrait controls keep artwork and metadata at identical positions across materials and dynamic budgets`() {
        val glass = mutableStateOf(false)
        val viewportHeight = mutableStateOf(700.dp)
        val headerHeight = mutableStateOf(44.dp)
        val nextTrack = mutableStateOf(false)
        val playerType = mutableStateOf(PlayerType.Essential)
        var minimumControlsHeight = 0
        val sheet = mockk<PlayerSheetState>(relaxed = true)
        every { sheet.progress } returns 1f
        val nav = mockk<androidx.navigation.NavController>(relaxed = true)
        val media = UiMedia("budget-parity", "Panel title", "Panel artist", 120_000L, true)
        compose.setContent { AppearanceProvider {
            CompositionLocalProvider(LocalPlayerServiceBinder provides binder, LocalPlayerSheetState provides sheet,
                LocalPlayerTransportGeometry provides remember { PlayerTransportGeometry() }) {
                val prioritizeArtwork = playerPortraitPrioritizesArtwork(true, playerType.value)
                val weightedControls = playerPortraitUsesWeightedInformation(false, false, playerType.value,
                    carousel = false, lyricsOnCover = false)
                PlayerGlassBudgetLayout(
                    enabled = false,
                    prioritizeArtwork = prioritizeArtwork,
                    expandInformation = weightedControls,
                    informationGap = playerPortraitInformationGap(),
                    modifier = Modifier.size(360.dp, viewportHeight.value).playerTransportGeometryHost().testTag("parity.viewport"),
                    header = { Box(Modifier.fillMaxWidth().height(headerHeight.value).testTag("parity.header")) },
                    artwork = {
                        val artworkSlot = if (360.dp <= viewportHeight.value / 2)
                            Modifier.height(360.dp) else Modifier.weight(1f)
                        BoxWithConstraints(artworkSlot.fillMaxWidth().testTag("parity.artwork"),
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.size(minOf(maxWidth, maxHeight)).testTag("parity.cover"))
                        }
                    },
                    information = {
                        PlayerInformationGlassBlock {
                            PlayerPortraitInformationLayout(artworkPrioritized = prioritizeArtwork,
                                weightedControls = weightedControls, above = {}, controls = {
                                    Box(Modifier.recordNaturalPlayerHeight { minimumControlsHeight = it }
                                        .testTag("parity.controls")) {
                                        app.it.fast4x.rimusic.ui.screens.player.Controls(
                                            navController = nav, onCollapse = {}, onBlurScaleChange = {},
                                            expandedplayer = false, titleExpanded = false, timelineExpanded = false,
                                            controlsExpanded = false, isShowingLyrics = false, media = media, mediaId = media.id,
                                            title = media.title, artist = media.artist, artistIds = null, albumId = null,
                                            shouldBePlaying = false, isBuffering = false, position = { 30_000L }, duration = { 120_000L },
                                            isExplicit = false, dynamicColorPalette = DefaultDarkColorPalette,
                                            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth())
                                    }
                                }, below = {
                                    if (nextTrack.value) PlayerWhatsNextGlassSurface(Color.Transparent,
                                        settings = GlassSettings(enabled = glass.value), landscape = false) { surface ->
                                        Box(Modifier.fillMaxWidth().height(48.dp)) {
                                            Box(surface.matchParentSize().testTag("parity.next"))
                                        }
                                    }
                                    val toolbarLayout = rememberPlayerActionToolbarLayout(GlassSettings(enabled = glass.value))
                                    Column(Modifier.fillMaxWidth().requiredHeight(toolbarLayout.hostHeight(50.dp))
                                        .testTag("parity.toolbar"), verticalArrangement = Arrangement.SpaceAround) {
                                        PlayerActionToolbar(listOf("lyrics", "menu").map { id -> PlayerActionSlot(id) {
                                            PlayerActionIcon(app.n_zik.android.R.drawable.play, Color.White, {}, Modifier.size(24.dp).testTag("parity.lower.$id"))
                                        } }, true, settings = GlassSettings(enabled = glass.value), layout = toolbarLayout,
                                            landscape = false)
                                    }
                            })
                        }
                    })
            }
        } }
        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        for (height in listOf(700.dp, 800.dp, 900.dp)) for (info in PlayerInfoType.entries)
            for (type in PlayerType.entries) {
                val native = mutableMapOf<String, androidx.compose.ui.geometry.Rect>()
                var nativeInformationTop = 0f
                for (enabled in listOf(false, true, false)) {
                    compose.runOnIdle {
                        viewportHeight.value = height
                        headerHeight.value = if (height == 700.dp) 64.dp else 44.dp
                        nextTrack.value = height == 900.dp
                        playerType.value = type
                        glass.value = enabled
                        preferences.edit().putBoolean(glassEnabledKey, enabled)
                            .putString(app.it.fast4x.rimusic.utils.playerTypeKey, type.name)
                            .putString(app.it.fast4x.rimusic.utils.playerInfoTypeKey, info.name)
                            .putBoolean(app.it.fast4x.rimusic.utils.playerSwapControlsWithTimelineKey, false).commit()
                        Snapshot.sendApplyNotifications()
                    }
                    if (native.isEmpty()) {
                        compose.waitForIdle()
                        compose.mainClock.autoAdvance = false
                    } else {
                        compose.mainClock.advanceTimeByFrame()
                        compose.waitForIdle()
                    }
                    val context = "height=$height info=$info player=$type glass=$enabled"
                    val available = bounds("parity.viewport").bottom - bounds("parity.header").bottom
                    val toolbarHeight = with(compose.density) { (if (enabled) 82.dp else 50.dp).toPx() }
                    val nextHeight = if (nextTrack.value) with(compose.density) { 48.dp.toPx() } else 0f
                    assertTrue("Natural controls measurement must contain real content $context", minimumControlsHeight > 0)
                    val minimumLower = minimumControlsHeight + toolbarHeight + nextHeight
                    val expectedArtwork = minOf(bounds("parity.viewport").width, (available - minimumLower).coerceAtLeast(0f))
                    val surplus = (available - expectedArtwork - minimumLower).coerceAtLeast(0f)
                    val gap = minOf(with(compose.density) {
                        (if (enabled && type == PlayerType.Essential) 8.dp else 0.dp).toPx()
                    }, surplus)
                    val informationTop = bounds("parity.header").bottom + expectedArtwork + gap
                    if (native.isEmpty()) nativeInformationTop = informationTop
                    assertEquals("100 percent artwork takes all room left after the measured minimum $context",
                        expectedArtwork, bounds("parity.artwork").height, 1f)
                    val cover = bounds("parity.cover")
                    assertEquals("A constrained cover stays square $context", expectedArtwork, cover.width, 1f)
                    assertEquals("A constrained cover uses its whole slot $context", expectedArtwork, cover.height, 1f)
                    assertEquals("Only surplus space may fund the Glass separation $context", informationTop,
                        bounds("parity.controls").top, 1f)
                    if (expectedArtwork < bounds("parity.viewport").width - 1f)
                        assertEquals("Shrunk artwork leaves no arbitrary flexible control whitespace $context",
                            minimumControlsHeight.toFloat(), bounds("parity.controls").height, 1f)
                    for (tag in listOf("parity.header", "parity.toolbar",
                        "player.informationRegion.info", "player.informationRegion.timeline", "player.timeline.duration")) {
                        val actual = bounds(tag)
                        val original = native.putIfAbsent(tag, actual)
                        if (original != null) {
                            val expected = when {
                                tag == "parity.toolbar" && enabled -> androidx.compose.ui.geometry.Rect(original.left,
                                    original.top - with(compose.density) { 32.dp.toPx() }, original.right, original.bottom)
                                tag.startsWith("player.") -> original.translate(0f, informationTop - nativeInformationTop)
                                else -> original
                            }
                            assertEquals("Measured artwork budget and exact Glass gap retain the internal grouping $tag $context", expected, actual)
                        }
                    }
                    assertEquals("The native 50dp action host gains exactly 32dp for Glass $context",
                        with(compose.density) { (if (enabled) 82.dp else 50.dp).toPx() }, bounds("parity.toolbar").height, .5f)
                    assertEquals("The visible lower row restores native padding when Glass is disabled $context",
                        with(compose.density) { (if (enabled) 56.dp else 24.dp).toPx() },
                        bounds(if (enabled) "player.actionToolbar.material" else "player.actionToolbar.native").height, .5f)
                    val metadata = bounds("player.informationRegion.info")
                    val artwork = bounds("parity.artwork")
                    assertTrue("Metadata retains its measured content $context", metadata.height > 0f)
                    assertTrue("Timeline retains its measured content $context", bounds("player.informationRegion.timeline").height > 0f)
                    assertTrue("Artwork remains below the header $context", artwork.top >= bounds("parity.header").bottom - 1f)
                    assertTrue("Metadata follows artwork $context", metadata.top >= artwork.bottom - 1f)
                    val transport = bounds("player.transport.row")
                    assertTrue("Transport retains its measured content $context", transport.height > 0f)
                    assertTrue("Transport never piles into the title $context", transport.top >= metadata.bottom - 1f)
                    val visibleToolbar = bounds(if (enabled) "player.actionToolbar.material" else "parity.lower.lyrics")
                    assertTrue("Transport stays above visible lower actions $context transport=$transport toolbar=$visibleToolbar",
                        transport.bottom <= visibleToolbar.top + 1f)
                    repeat(20) { frame ->
                        compose.mainClock.advanceTimeByFrame()
                        val movingTransport = bounds("player.transport.row")
                        val movingLower = if (nextTrack.value) bounds("player.whatsNext.surface").top
                            else bounds(if (enabled) "player.actionToolbar.material" else "parity.lower.lyrics").top
                        assertTrue("Transport stays above lower actions during reflow frame=$frame $context",
                            movingTransport.bottom <= movingLower + 1f)
                        assertTrue("Transport stays below metadata during reflow frame=$frame $context",
                            movingTransport.top >= bounds("player.informationRegion.info").bottom - 1f)
                    }
                    compose.mainClock.advanceTimeBy(1_000L)
                    val settledTransport = bounds("player.transport.row")
                    val settledToolbar = bounds(if (enabled) "player.actionToolbar.material" else "parity.lower.lyrics")
                    val lower = if (nextTrack.value) bounds("player.whatsNext.surface").top else settledToolbar.top
                    val duration = bounds("player.timeline.duration")
                    val reference = if (enabled) bounds("player.informationBlock.surface").bottom else duration.center.y
                    val clearance = if (enabled) reference else duration.bottom
                    val desired = (reference + lower - settledTransport.height) / 2f
                    val expected = if (lower - settledTransport.height >= clearance)
                        desired.coerceIn(clearance, lower - settledTransport.height) else lower - settledTransport.height
                    assertEquals("Transport balances its real visible neighbors within the available minimum $context",
                        expected, settledTransport.top, 2f)
                }
                compose.mainClock.autoAdvance = true
            }
    }

    @Test @Config(qualifiers = "w800dp-h1200dp-port")
    @OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.ui.text.ExperimentalTextApi::class)
    fun `expanded Essential cover lyrics and AudioWaves retain original artwork metadata and lower action grouping`() {
        val glass = mutableStateOf(false)
        val expanded = mutableStateOf(true)
        var minimumControlsHeight = 0
        val sheet = mockk<PlayerSheetState>(relaxed = true)
        every { sheet.progress } returns 1f
        val nav = mockk<androidx.navigation.NavController>(relaxed = true)
        val media = UiMedia("expanded-essential", "Panel title", "Panel artist", 246_000L, true)
        val cache = mockk<Cache>(relaxed = true)
        every { binder.downloadCache } returns cache
        every { binder.cache } returns cache
        every { binder.service.currentSongStateDownload } returns MutableStateFlow(Download.STATE_STOPPED)
        mockkObject(WaveformExtractor)
        coEvery { WaveformExtractor.getOrExtractWaveform(any(), any(), any()) } returns
            WaveformResult.Success(List(150) { 50 })
        preferences.edit().putString(app.it.fast4x.rimusic.utils.playerTypeKey, PlayerType.Essential.name)
            .putString(app.it.fast4x.rimusic.utils.playerInfoTypeKey, PlayerInfoType.Modern.name)
            .putString(app.it.fast4x.rimusic.utils.playerControlsTypeKey,
                app.it.fast4x.rimusic.enums.PlayerControlsType.Modern.name)
            .putString(app.it.fast4x.rimusic.utils.playerTimelineTypeKey,
                app.it.fast4x.rimusic.enums.PlayerTimelineType.AudioWaves.name)
            .putString(app.it.fast4x.rimusic.utils.playerTimelineSizeKey, PlayerTimelineSize.Biggest.name)
            .putBoolean(app.it.fast4x.rimusic.utils.showlyricsthumbnailKey, true)
            .putBoolean(app.it.fast4x.rimusic.utils.showthumbnailKey, true)
            .putBoolean(app.it.fast4x.rimusic.utils.playerSwapControlsWithTimelineKey, false).commit()
        compose.setContent { AppearanceProvider {
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT
                screenWidthDp = 448
                screenHeightDp = 997
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalPlayerServiceBinder provides binder,
                LocalPlayerSheetState provides sheet,
                LocalPlayerTransportGeometry provides remember { PlayerTransportGeometry() }) {
                val weighted = playerPortraitUsesWeightedInformation(expanded.value, false, PlayerType.Essential,
                    carousel = false, lyricsOnCover = true)
                val prioritizeArtwork = playerPortraitPrioritizesArtwork(true, PlayerType.Essential)
                PlayerGlassBudgetLayout(enabled = false, prioritizeArtwork = prioritizeArtwork,
                    informationGap = playerPortraitInformationGap(), modifier = Modifier.size(448.dp, 997.dp)
                    .playerTransportGeometryHost().testTag("exact.viewport"),
                    header = { Box(Modifier.fillMaxWidth().height(73.dp).testTag("exact.header")) },
                    artwork = {
                        val slot = if (expanded.value) Modifier.weight(1f) else Modifier.height(448.dp)
                        Box(slot.fillMaxWidth().testTag("exact.artworkSlot"), contentAlignment = Alignment.Center) {
                            Box(Modifier.fillMaxWidth(.95f).aspectRatio(1f).testTag("exact.artwork"))
                        }
                    }, information = {
                        PlayerInformationGlassBlock {
                            PlayerPortraitInformationLayout(artworkPrioritized = prioritizeArtwork,
                                weightedControls = weighted, above = { Spacer(Modifier.height(10.dp)) }, controls = {
                                    Box(Modifier.recordNaturalPlayerHeight { minimumControlsHeight = it }
                                        .testTag("exact.controls")) {
                                        app.it.fast4x.rimusic.ui.screens.player.Controls(
                                            navController = nav, onCollapse = {}, onBlurScaleChange = {},
                                            expandedplayer = expanded.value, titleExpanded = true, timelineExpanded = true,
                                            controlsExpanded = true, isShowingLyrics = false, media = media, mediaId = media.id,
                                            title = media.title, artist = media.artist, artistIds = null, albumId = null,
                                            shouldBePlaying = false, isBuffering = false, position = { 2_000L }, duration = { 246_000L },
                                            isExplicit = false, dynamicColorPalette = DefaultDarkColorPalette,
                                            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth())
                                    }
                                }, below = {
                                    val toolbarLayout = rememberPlayerActionToolbarLayout(GlassSettings(enabled = glass.value))
                                    Column(Modifier.fillMaxWidth().requiredHeight(toolbarLayout.hostHeight(50.dp))
                                        .testTag("exact.actions"), verticalArrangement = Arrangement.SpaceAround) {
                                        PlayerActionToolbar(listOf("lyrics", "menu").map { id -> PlayerActionSlot(id) {
                                            PlayerActionIcon(app.n_zik.android.R.drawable.play, Color.White, {}, Modifier.size(24.dp).testTag("exact.lower.$id"))
                                        } }, true, settings = GlassSettings(enabled = glass.value), layout = toolbarLayout, landscape = false)
                                    }
                            })
                        }
                    })
            }
        } }
        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        for (isExpanded in listOf(true, false, true)) {
            compose.runOnIdle { expanded.value = isExpanded }
            val native = mutableMapOf<String, androidx.compose.ui.geometry.Rect>()
            var nativeInformationTop = 0f
            for (enabled in listOf(false, true, false)) {
                compose.runOnIdle {
                    glass.value = enabled
                    preferences.edit().putBoolean(glassEnabledKey, enabled).commit()
                    Snapshot.sendApplyNotifications()
                }
                val context = "expanded=$isExpanded glass=$enabled"
                val viewport = bounds("exact.viewport")
                val artwork = bounds("exact.artwork")
                val metadata = bounds("player.informationRegion.info")
                val timeline = bounds("player.informationRegion.timeline")
                val transport = bounds("player.transport.row")
                val actions = bounds("exact.actions")
                val aboveHeight = with(compose.density) { 10.dp.toPx() }
                val toolbarHeight = with(compose.density) { (if (enabled) 82.dp else 50.dp).toPx() }
                val available = viewport.bottom - bounds("exact.header").bottom
                assertTrue("Natural controls measurement must contain real content $context", minimumControlsHeight > 0)
                val minimumLower = minimumControlsHeight + aboveHeight + toolbarHeight
                val expectedArtwork = minOf(viewport.width, (available - minimumLower).coerceAtLeast(0f))
                val surplus = (available - expectedArtwork - minimumLower).coerceAtLeast(0f)
                val gap = minOf(with(compose.density) { (if (enabled) 8.dp else 0.dp).toPx() }, surplus)
                val informationTop = bounds("exact.header").bottom + expectedArtwork + gap + aboveHeight
                if (native.isEmpty()) nativeInformationTop = informationTop
                assertEquals("Artwork retains priority over flexible control whitespace $context", expectedArtwork,
                    bounds("exact.artworkSlot").height, 1f)
                assertEquals("Only the exact surplus-funded Glass gap separates the groups $context", informationTop,
                    bounds("exact.controls").top, 1f)
                assertTrue("Metadata retains its measured content $context", metadata.height > 0f)
                assertTrue("Timeline retains its measured content $context", timeline.height > 0f)
                assertTrue("Transport retains its measured content $context", transport.height > 0f)
                assertTrue("Original large square cover remains visible $context artwork=$artwork",
                    artwork.width >= viewport.width * .9f && artwork.height >= viewport.width * .9f)
                assertEquals("Artwork keeps its aspect ratio $context", artwork.width, artwork.height, 1f)
                assertTrue("Artwork owns a real slot instead of overdrawing a collapsed slot $context",
                    bounds("exact.artworkSlot").height >= artwork.height - 1f)
                assertTrue("Artwork stays below the header $context", artwork.top >= bounds("exact.header").bottom - 1f)
                assertTrue("Metadata follows the cover $context", metadata.top >= artwork.bottom - 1f)
                assertTrue("Metadata keeps the original cover grouping $context artwork=$artwork metadata=$metadata",
                    metadata.top - artwork.bottom <= with(compose.density) { 48.dp.toPx() })
                assertTrue("Timeline follows metadata $context", timeline.top >= metadata.bottom - 1f)
                assertTrue("Controls stay below time labels $context", transport.top >= bounds("player.timeline.duration").bottom - 1f)
                val visibleToolbar = bounds(if (enabled) "player.actionToolbar.material" else "exact.lower.lyrics")
                assertTrue("Transport clears visible lower actions $context transport=$transport actions=$actions",
                    transport.bottom <= visibleToolbar.top + 1f)
                val duration = bounds("player.timeline.duration")
                val reference = if (enabled) bounds("player.informationBlock.surface").bottom else duration.center.y
                val clearance = if (enabled) reference else duration.bottom
                val desired = (reference + visibleToolbar.top - transport.height) / 2f
                val expected = if (visibleToolbar.top - transport.height >= clearance)
                    desired.coerceIn(clearance, visibleToolbar.top - transport.height) else visibleToolbar.top - transport.height
                assertEquals("Transport centers against the actual visible lower neighbor $context",
                    expected, transport.top, 2f)
                assertTrue("Actions remain inside the viewport $context", actions.bottom <= viewport.bottom + 1f)
                for (tag in listOf("exact.artwork", "player.informationRegion.info", "player.informationRegion.timeline", "exact.actions")) {
                    val actual = bounds(tag)
                    val baseline = native.putIfAbsent(tag, actual)
                    if (baseline != null) {
                        val expected = when {
                            tag == "exact.actions" && enabled -> androidx.compose.ui.geometry.Rect(baseline.left,
                                baseline.top - with(compose.density) { 32.dp.toPx() }, baseline.right, baseline.bottom)
                            tag.startsWith("player.") -> baseline.translate(0f, informationTop - nativeInformationTop)
                            else -> baseline
                        }
                        assertEquals("Measured artwork budget and exact Glass gap retain the internal grouping $tag $context", expected, actual)
                    }
                }
                assertEquals("Expanded layout restores the native action host when disabled $context",
                    with(compose.density) { (if (enabled) 82.dp else 50.dp).toPx() }, actions.height, .5f)
            }
        }
    }

    @Test @Config(qualifiers = "w800dp-h1200dp-port")
    @OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.ui.text.ExperimentalTextApi::class)
    fun `information edge and native time labels remain the references through first frame orientation changes`() = verifyBalancedTransport(true, focused = true)

    private fun Modifier.recordNaturalPlayerHeight(onHeight: (Int) -> Unit): Modifier = layout { measurable, constraints ->
        val child = measurable.measure(constraints)
        if (!constraints.hasBoundedHeight) onHeight(child.height)
        layout(child.width, child.height) { child.placeRelative(0, 0) }
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
