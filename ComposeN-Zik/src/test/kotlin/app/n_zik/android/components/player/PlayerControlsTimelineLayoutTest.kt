package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
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
                    val layout = rememberPlayerActionToolbarLayout()
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
    private fun verifyBalancedTransport(portrait: Boolean) {
        val glass = mutableStateOf(false)
        val translation = mutableStateOf(0f)
        val sheet = mockk<PlayerSheetState>(relaxed = true)
        every { sheet.progress } returns 1f
        val nav = mockk<androidx.navigation.NavController>(relaxed = true)
        val media = UiMedia("centered-panel", "Panel title", "Panel artist", 120_000L, true)
        compose.setContent { AppearanceProvider {
            CompositionLocalProvider(LocalPlayerServiceBinder provides binder, LocalPlayerSheetState provides sheet,
                LocalPlayerTransportGeometry provides remember { PlayerTransportGeometry() }) {
                Column(Modifier.size(360.dp, if (portrait) 700.dp else 480.dp)
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
                            modifier = if (portrait) Modifier.fillMaxWidth()
                                else Modifier.fillMaxHeight().weight(1f),
                        )
                    }
                    if (portrait) PlayerInformationGlassBlock(Modifier.weight(1f)) {
                        Column { Box(Modifier.weight(1f)) { RealControls() } }
                    } else RealControls()
                    val layout = rememberPlayerActionToolbarLayout()
                    val settings = GlassSettings(enabled = glass.value)
                    Column(Modifier.fillMaxWidth().requiredHeight(layout.hostHeight(50.dp)),
                        verticalArrangement = Arrangement.SpaceAround) {
                        PlayerActionToolbar(listOf("lyrics", "menu").map { id -> PlayerActionSlot(id) {
                            PlayerActionIcon(app.n_zik.android.R.drawable.play, Color.White, {}, Modifier.size(24.dp))
                        } }, true, settings = settings, layout = layout, landscape = !portrait)
                    }
                }
            }
        } }
        for (size in PlayerTimelineSize.entries) for (info in PlayerInfoType.entries)
            for (controls in app.it.fast4x.rimusic.enums.PlayerControlsType.entries)
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
                    val toolbar = bounds(if (enabled) "player.actionToolbar.material" else "player.actionToolbar.native")
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
                    assertEquals("Visible free gaps are equal $context", transport.top - above, below - transport.bottom, 2f)
                    val transportButtons = compose.onAllNodes(hasClickAction(), useUnmergedTree = true)
                        .fetchSemanticsNodes().map { it.boundsInRoot }.filter {
                            it.center.y >= transport.top - 1f && it.center.y <= transport.bottom + 1f &&
                                it.center.x >= bounds("center.viewport").left && it.center.x <= bounds("center.viewport").right
                        }
                    assertEquals("Actual transport click targets retain their configured count $context",
                        if (controls == app.it.fast4x.rimusic.enums.PlayerControlsType.Modern) 3 else 5, transportButtons.size)
                    val visibleCenter = (transportButtons.minOf { it.top } + transportButtons.maxOf { it.bottom }) / 2f
                    assertEquals("Actual transport glyph/button union is vertically centered $context",
                        (above + below) / 2f, visibleCenter, 2f)
                    assertTrue("Transport stays between its configured neighbors $context",
                        transport.top >= above - 1f && transport.bottom <= below + 1f)
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

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(16.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        ), content = content)
    }
}
