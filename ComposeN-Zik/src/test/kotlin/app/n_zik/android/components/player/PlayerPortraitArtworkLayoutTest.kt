package app.n_zik.android.components.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.PlayerControlsType
import app.it.fast4x.rimusic.enums.PlayerInfoType
import app.it.fast4x.rimusic.enums.PlayerTimelineSize
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.enums.ThumbnailType
import app.it.fast4x.rimusic.models.ui.UiMedia
import app.it.fast4x.rimusic.ui.screens.player.Controls
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.it.fast4x.rimusic.ui.screens.player.Thumbnail
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.playerControlsTypeKey
import app.it.fast4x.rimusic.utils.playerInfoTypeKey
import app.it.fast4x.rimusic.utils.playerTimelineSizeKey
import app.it.fast4x.rimusic.utils.playerTimelineTypeKey
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showlyricsthumbnailKey
import app.it.fast4x.rimusic.utils.showthumbnailKey
import app.it.fast4x.rimusic.utils.thumbnailSizeDpKey
import app.it.fast4x.rimusic.utils.thumbnailTypeKey
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.LocalPlayerSheetState
import app.n_zik.android.appContext
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.components.player.slide.playerThumbnailCover
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.FormatTable
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.extensions.audiobar.utils.WaveformResult
import app.n_zik.android.playback.services.PlayerServiceModern
import coil3.ImageLoader
import coil3.asImage
import coil3.memory.MemoryCache
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w500dp-h1100dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class, ExperimentalTextApi::class)
class PlayerPortraitArtworkLayoutTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val files = TemporaryFolder()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val preferences get() = context.preferences
    private val player = mockk<Player>(relaxed = true)
    private val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
    private lateinit var loader: ImageLoader
    private lateinit var artworkCache: MemoryCache

    @Before fun setup() {
        preferences.edit().clear().putBoolean("buttonzoomout", false).commit()
        every { binder.player } returns player
        val cache = mockk<Cache>(relaxed = true)
        every { binder.downloadCache } returns cache
        every { binder.cache } returns cache
        every { binder.service.currentSongStateDownload } returns MutableStateFlow(Download.STATE_STOPPED)
        every { binder.playerUpdateTrigger } returns MutableStateFlow(0)
        val songs = mockk<SongTable>()
        val formats = mockk<FormatTable>()
        every { songs.isLiked(any()) } returns flowOf(false)
        every { songs.likeState(any()) } returns flowOf(null)
        every { songs.findById(any()) } returns flowOf(null)
        every { formats.findBySongId(any()) } returns flowOf(null)
        mockkObject(Database, WaveformExtractor)
        every { Database.songTable } returns songs
        every { Database.formatTable } returns formats
        coEvery { WaveformExtractor.getOrExtractWaveform(any(), any(), any()) } returns
            WaveformResult.Success(List(150) { 50 })
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        artworkCache = MemoryCache.Builder().maxSizeBytes(1_048_576L).build()
        loader = ImageLoader.Builder(context).diskCache(null).memoryCache(artworkCache).build()
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.LOADER } returns loader
        every { ImageCacheFactory.generateCacheKeySync(any(), any()) } returns "local-artwork-fixture"
        every { ImageCacheFactory.getDownloadDecision(any()) } returns
            ImageCacheFactory.DownloadDecision(false, ImageCacheFactory.NetworkQuality.HIGH)
    }

    @Test fun `real Essential and Modern covers respect full width preference and tiny portrait slots`() {
        val artworkUri = cachedArtwork("small-slot-cover.png")
        val item = MediaItem.Builder().setMediaId("small-slot-cover")
            .setMediaMetadata(MediaMetadata.Builder().setArtworkUri(artworkUri).build()).build()
        setPlayingItem(item)
        preferences.edit().putString(thumbnailTypeKey, ThumbnailType.Modern.name).commit()
        val type = mutableStateOf(PlayerType.Essential)
        val slotHeight = mutableStateOf(464.dp)
        val preference = mutableStateOf(100f)
        compose.setContent { AppearanceProvider {
            CompositionLocalProvider(LocalPlayerServiceBinder provides binder) {
                Box(Modifier.requiredSize(448.dp, 500.dp).testTag("small.viewport"), contentAlignment = Alignment.Center) {
                    BoxWithConstraints(Modifier.fillMaxWidth().height(slotHeight.value), contentAlignment = Alignment.Center) {
                        val padding = playerThumbnailPadding(preference.value)
                        if (type.value == PlayerType.Essential) {
                            PlayerPortraitThumbnailFrame(true, 16.dp, padding) { RealThumbnail(preference.value) }
                        } else {
                            val state = rememberPagerState { 1 }
                            val geometry = playerCarouselGeometry(maxWidth, maxHeight, padding, 10.dp, false)
                            if (geometry.pageSize > geometry.coverPadding * 2) {
                                VerticalPager(state = state, pageSize = PageSize.Fixed(geometry.pageSize),
                                    contentPadding = PaddingValues(vertical = (maxHeight - geometry.pageSize) / 2),
                                    modifier = Modifier.fillMaxSize()) { page ->
                                    val painter = ImageCacheFactory.Painter(item.mediaMetadata.artworkUri.toString())
                                    PlayerCarouselCover(page, 0, item, item.mediaId, false, true, false, true,
                                        false, {}, { 246_000L }, true, false,
                                        modifier = Modifier.playerThumbnailCover(state, page, geometry.coverPadding,
                                            false, false, .9f, thumbnailType = ThumbnailType.Modern,
                                            showCoverThumbnailAnimation = false, onTap = {}, onLongClick = {})) {
                                        Image(painter, "", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } }
        compose.waitUntil(10_000) { paintedCoverSize("small.viewport").first > 0 }
        for (playerType in listOf(PlayerType.Essential, PlayerType.Modern)) {
            compose.runOnIdle { type.value = playerType; slotHeight.value = 464.dp; preference.value = 100f }
            compose.waitUntil(10_000) { paintedCoverSize("small.viewport").first > 0 }
            for (height in listOf(464.dp, 448.dp, 148.dp, 60.dp, 32.dp, 31.dp, 30.dp, 17.dp, 16.dp, 0.dp)) {
                for (size in listOf(100f, 90f, 100f)) {
                    compose.runOnIdle { type.value = playerType; slotHeight.value = height; preference.value = size }
                    val decoration = if (playerType == PlayerType.Essential) 16.dp else 0.dp
                    val expected = (minOf(448.dp, (height - decoration).coerceAtLeast(0.dp)) -
                        playerThumbnailPadding(size) * 2 - if (playerType == PlayerType.Modern) 20.dp else 0.dp).coerceAtLeast(0.dp)
                    val painted = paintedCoverSize("small.viewport")
                    val label = "type=$playerType height=$height size=$size"
                    assertEquals("Actual cover width $label", with(compose.density) { expected.toPx() },
                        painted.first.toFloat(), 2f)
                    assertEquals("Actual cover remains square $label", painted.first.toFloat(), painted.second.toFloat(), 1f)
                }
            }
        }
    }

    @After fun cleanup() {
        loader.shutdown()
        preferences.edit().clear().commit()
        unmockkAll()
    }

    @Test fun `allocator spends flexible whitespace before reducing the requested artwork`() {
        for (requested in listOf(1, 148, 418, 448, 464)) {
            val minimum = 350
            for (delta in listOf(-1, 0, 1, 100)) {
                val available = requested + minimum + delta
                val budget = playerPortraitArtworkBudget(available, requested, minimum)
                assertEquals(requested + minOf(delta, 0), budget.artwork)
                assertEquals(available - budget.artwork, budget.lower)
                assertTrue(budget.lower >= minimum)
            }
        }
        assertEquals(PlayerPortraitArtworkBudget(0, 200), playerPortraitArtworkBudget(200, 448, 350))
        assertEquals(0.dp, playerThumbnailPadding(100f))
        assertEquals(15.dp, playerThumbnailPadding(90f))
    }

    @Test fun `lookahead obtains natural content without unsupported intrinsics and retains lower weighted space`() {
        val optional = mutableStateOf(false)
        compose.setContent {
            PlayerGlassBudgetLayout(enabled = false, prioritizeArtwork = true,
                modifier = Modifier.requiredSize(448.dp, 900.dp),
                header = { Box(Modifier.size(448.dp, 73.dp)) },
                artwork = { Box(Modifier.weight(1f).fillMaxWidth().testTag("budget.cover")) },
                information = {
                    PlayerPortraitInformationLayout(artworkPrioritized = true, weightedControls = true,
                        above = {}, controls = {
                            Column {
                                BoxWithConstraints(Modifier.fillMaxWidth().height(100.dp).testTag("budget.info")) { }
                                Spacer(Modifier.weight(1f))
                            }
                        }, below = {
                            if (optional.value) Box(Modifier.size(448.dp, 80.dp))
                            Box(Modifier.size(448.dp, 50.dp).testTag("budget.toolbar"))
                        })
                })
        }
        for (enabled in listOf(false, true, false)) {
            compose.runOnIdle { optional.value = enabled }
            val cover = bounds("budget.cover")
            val info = bounds("budget.info")
            val toolbar = bounds("budget.toolbar")
            assertEquals(448f * compose.density.density, cover.height, 1f)
            assertEquals(cover.bottom, info.top, 1f)
            assertEquals(900f * compose.density.density, toolbar.bottom, 1f)
            assertTrue(toolbar.top > info.bottom)
        }
    }

    @Test fun `real Thumbnail honors preference100 before flexible gaps in usable900 and925 portrait with actual AudioWaves controls`() {
        val artworkUri = cachedArtwork("portrait-cover.png")
        val item = MediaItem.Builder().setMediaId("portrait-cover")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Panel title").setArtist("Panel artist")
                .setArtworkUri(artworkUri).build()).build()
        setPlayingItem(item)
        val height = mutableStateOf(900.dp)
        val glass = mutableStateOf(false)
        val expanded = mutableStateOf(true)
        val sheet = mockk<PlayerSheetState>(relaxed = true)
        every { sheet.progress } returns 1f
        val nav = mockk<androidx.navigation.NavController>(relaxed = true)
        val media = UiMedia("portrait-cover", "Panel title", "Panel artist", 246_000L, true)
        var measuredNaturalLower = 0
        preferences.edit().putFloat(thumbnailSizeDpKey, 100f)
            .putString(thumbnailTypeKey, ThumbnailType.Modern.name)
            .putString(playerTypeKey, PlayerType.Essential.name)
            .putString(playerInfoTypeKey, PlayerInfoType.Modern.name)
            .putString(playerControlsTypeKey, PlayerControlsType.Modern.name)
            .putString(playerTimelineTypeKey, PlayerTimelineType.AudioWaves.name)
            .putString(playerTimelineSizeKey, PlayerTimelineSize.Biggest.name)
            .putBoolean(showlyricsthumbnailKey, true).putBoolean(showthumbnailKey, true).commit()
        compose.setContent { AppearanceProvider {
            CompositionLocalProvider(LocalPlayerServiceBinder provides binder, LocalPlayerSheetState provides sheet,
                LocalPlayerTransportGeometry provides remember { PlayerTransportGeometry() }) {
                val size by rememberPreference(thumbnailSizeDpKey, 90f)
                PlayerGlassBudgetLayout(enabled = false, prioritizeArtwork = true, artworkVerticalPadding = 16.dp,
                    modifier = Modifier.requiredSize(448.dp, height.value).playerTransportGeometryHost().testTag("real.viewport"),
                    header = { Box(Modifier.size(448.dp, 73.dp).testTag("real.header")) },
                    artwork = {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("real.artworkSlot"),
                            contentAlignment = Alignment.Center) {
                            PlayerPortraitThumbnailFrame(true, 16.dp, playerThumbnailPadding(size)) {
                                RealThumbnail(size)
                            }
                        }
                    }, information = {
                        PlayerInformationGlassBlock(Modifier.layout { measurable, constraints ->
                            val body = measurable.measure(constraints)
                            if (isLookingAhead) measuredNaturalLower = body.height
                            layout(body.width, body.height) { body.placeRelative(0, 0) }
                        }) {
                            PlayerPortraitInformationLayout(true, true,
                                above = { Spacer(Modifier.height(10.dp)) }, controls = {
                                    Controls(navController = nav, onCollapse = {}, onBlurScaleChange = {},
                                        expandedplayer = expanded.value, titleExpanded = true, timelineExpanded = true,
                                        controlsExpanded = true, isShowingLyrics = false, media = media, mediaId = media.id,
                                        title = media.title, artist = media.artist, artistIds = null, albumId = null,
                                        shouldBePlaying = false, isBuffering = false, position = { 2_000L }, duration = { 246_000L },
                                        isExplicit = false, dynamicColorPalette = DefaultDarkColorPalette,
                                        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth())
                                }, below = {
                                val toolbarLayout = rememberPlayerActionToolbarLayout(GlassSettings(enabled = glass.value))
                                Column(Modifier.fillMaxWidth().requiredHeight(toolbarLayout.hostHeight(50.dp))
                                    .testTag("real.actions"), verticalArrangement = Arrangement.SpaceAround) {
                                    PlayerActionToolbar(listOf("lyrics", "menu").map { id -> PlayerActionSlot(id) {
                                        PlayerActionIcon(app.n_zik.android.R.drawable.play, Color.White, {},
                                            Modifier.size(24.dp).testTag("real.lower.$id"))
                                    } }, true, settings = GlassSettings(enabled = glass.value), layout = toolbarLayout,
                                        landscape = false)
                                }
                            })
                        }
                    })
            }
        } }
        compose.waitUntil(10_000) { paintedCoverSize().first > 0 }
        for (usableHeight in listOf(900.dp, 925.dp, 997.dp)) for (isExpanded in listOf(true, false)) {
            for (enabled in listOf(false, true, false)) for (preference in listOf(100f, 90f, 100f)) {
                compose.runOnIdle {
                    height.value = usableHeight
                    expanded.value = isExpanded
                    glass.value = enabled
                    preferences.edit().putFloat(thumbnailSizeDpKey, preference).putBoolean(glassEnabledKey, enabled).commit()
                    Snapshot.sendApplyNotifications()
                }
                val label = "height=$usableHeight expanded=$isExpanded glass=$enabled size=$preference"
                val pixels = paintedCoverSize()
                val width = with(compose.density) { 448.dp.toPx() }
                val verticalPadding = with(compose.density) { 16.dp.toPx() }
                val available = bounds("real.viewport").height - bounds("real.header").height - measuredNaturalLower
                val slotHeight = minOf(width + verticalPadding, available)
                val expected = minOf(width, slotHeight - verticalPadding) -
                    with(compose.density) { playerThumbnailPadding(preference).toPx() * 2 }
                assertEquals("Selected artwork width $label", expected, pixels.first.toFloat(), 2f)
                assertEquals("Artwork stays square $label", pixels.first.toFloat(), pixels.second.toFloat(), 1f)
                if (usableHeight == 997.dp && preference == 100f)
                    assertEquals("Full width is actually painted when it fits $label", width, pixels.first.toFloat(), 2f)
                val slot = bounds("real.artworkSlot")
                val info = bounds("player.informationRegion.info")
                val transport = bounds("player.transport.row")
                val actions = bounds("real.actions")
                assertTrue("Cover below header $label", slot.top >= bounds("real.header").bottom - 1f)
                assertTrue("Metadata below artwork slot $label", info.top >= slot.bottom - 1f)
                assertTrue("Transport clears metadata $label", transport.top >= info.bottom - 1f)
                assertTrue("Transport clears toolbar $label", transport.bottom <= bounds(
                    if (enabled) "player.actionToolbar.material" else "real.lower.lyrics").top + 1f)
                assertTrue("Toolbar remains accessible $label", actions.bottom <= bounds("real.viewport").bottom + 1f)
            }
        }
    }

    @Test fun `actual Essential AudioWaves metadata timeline and transport remain visible inside portrait allocator`() {
        val item = MediaItem.Builder().setMediaId("visible-controls")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Panel title").setArtist("Panel artist").build()).build()
        setPlayingItem(item)
        val height = mutableStateOf(900.dp)
        val glass = mutableStateOf(false)
        val expanded = mutableStateOf(true)
        val sheet = mockk<PlayerSheetState>(relaxed = true)
        every { sheet.progress } returns 1f
        val nav = mockk<androidx.navigation.NavController>(relaxed = true)
        val media = UiMedia("visible-controls", "Panel title", "Panel artist", 246_000L, true)
        preferences.edit().putFloat(thumbnailSizeDpKey, 100f)
            .putString(playerTypeKey, PlayerType.Essential.name)
            .putString(playerInfoTypeKey, PlayerInfoType.Modern.name)
            .putString(playerControlsTypeKey, PlayerControlsType.Modern.name)
            .putString(playerTimelineTypeKey, PlayerTimelineType.AudioWaves.name)
            .putString(playerTimelineSizeKey, PlayerTimelineSize.Biggest.name)
            .putBoolean(showlyricsthumbnailKey, true).putBoolean(showthumbnailKey, true).commit()
        compose.setContent { AppearanceProvider {
            CompositionLocalProvider(LocalPlayerServiceBinder provides binder, LocalPlayerSheetState provides sheet,
                LocalPlayerTransportGeometry provides remember { PlayerTransportGeometry() }) {
                PlayerGlassBudgetLayout(enabled = false, prioritizeArtwork = true, artworkVerticalPadding = 16.dp,
                    informationGap = playerPortraitInformationGap(),
                    modifier = Modifier.requiredSize(448.dp, height.value).playerTransportGeometryHost()
                        .testTag("visible.viewport"),
                    header = { Box(Modifier.size(448.dp, 73.dp).testTag("visible.header")) },
                    artwork = {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("visible.artwork"),
                            contentAlignment = Alignment.Center) {
                            val edge = minOf(maxWidth, (maxHeight - 16.dp).coerceAtLeast(0.dp))
                            Box(Modifier.size(edge).background(Color.Red).testTag("visible.cover"))
                        }
                    }, information = {
                        // Match Player.kt: the natural pass must see through both the
                        // information container and the flexible controller slot.
                        PlayerInformationGlassBlock {
                            PlayerPortraitInformationLayout(artworkPrioritized = true, weightedControls = true,
                                above = { Spacer(Modifier.height(10.dp)) }, controls = {
                                    Controls(navController = nav, onCollapse = {}, onBlurScaleChange = {},
                                        expandedplayer = expanded.value, titleExpanded = true, timelineExpanded = true,
                                        controlsExpanded = true, isShowingLyrics = false, media = media, mediaId = media.id,
                                        title = media.title, artist = media.artist, artistIds = null, albumId = null,
                                        shouldBePlaying = false, isBuffering = false, position = { 2_000L }, duration = { 246_000L },
                                        isExplicit = false, dynamicColorPalette = DefaultDarkColorPalette,
                                        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth())
                                }, below = {
                                    val toolbarLayout = rememberPlayerActionToolbarLayout(GlassSettings(enabled = glass.value))
                                    Column(Modifier.fillMaxWidth().requiredHeight(toolbarLayout.hostHeight(50.dp))
                                        .testTag("visible.actions"), verticalArrangement = Arrangement.SpaceAround) {
                                        PlayerActionToolbar(listOf(PlayerActionSlot("menu") {
                                            PlayerActionIcon(app.n_zik.android.R.drawable.play, Color.White, {},
                                                Modifier.size(24.dp).testTag("visible.menu"))
                                        }), true, settings = GlassSettings(enabled = glass.value), layout = toolbarLayout,
                                            landscape = false)
                                    }
                                })
                        }
                    })
            }
        } }
        for (usableHeight in listOf(900.dp, 925.dp, 997.dp)) for (isExpanded in listOf(true, false)) {
            for (enabled in listOf(false, true, false)) {
                compose.runOnIdle {
                    height.value = usableHeight
                    expanded.value = isExpanded
                    glass.value = enabled
                    preferences.edit().putBoolean(glassEnabledKey, enabled).commit()
                    Snapshot.sendApplyNotifications()
                }
                val label = "height=$usableHeight expanded=$isExpanded glass=$enabled"
                val cover = bounds("visible.cover")
                val slot = bounds("visible.artwork")
                val info = bounds("player.informationRegion.info")
                val timeline = bounds("player.informationRegion.timeline")
                val transport = bounds("player.transport.row")
                val menu = bounds("visible.menu")
                val viewport = bounds("visible.viewport")
                assertTrue("Metadata retains real height $label", info.height > 0f)
                assertTrue("Timeline retains real height $label", timeline.height > 0f)
                assertTrue("Transport retains real height $label", transport.height > 0f)
                assertTrue("Cover stays square $label", kotlin.math.abs(cover.width - cover.height) <= 1f)
                assertTrue("Metadata follows cover $label", info.top >= slot.bottom - 1f)
                assertTrue("Transport clears information $label", transport.top >= timeline.bottom - 1f)
                assertTrue("Transport clears actions $label", transport.bottom <= menu.top + 1f)
                assertTrue("Actions stay inside viewport $label", menu.bottom <= viewport.bottom + 1f)
                assertEquals("Actions retain bottom slot $label", viewport.bottom, bounds("visible.actions").bottom, 1f)
            }
        }
    }

    private fun setPlayingItem(item: MediaItem) {
        // Use real Window/Period data: Timeline's final two-argument getWindow must
        // delegate to this implementation, rather than a relaxed mock returning an empty Window.
        val timeline = object : Timeline() {
            override fun getWindowCount() = 1
            override fun getPeriodCount() = 1
            override fun getWindow(index: Int, window: Timeline.Window, projectionUs: Long): Timeline.Window =
                window.set(Timeline.Window.SINGLE_WINDOW_UID, item, null, C.TIME_UNSET, C.TIME_UNSET,
                    C.TIME_UNSET, true, false, null, 0L, 246_000_000L, 0, 0, 0L)
            override fun getPeriod(index: Int, period: Timeline.Period, setIds: Boolean): Timeline.Period =
                period.set(if (setIds) item.mediaId else null, if (setIds) item.mediaId else null,
                    0, 246_000_000L, 0L)
            override fun getIndexOfPeriod(uid: Any) = if (uid == item.mediaId) 0 else C.INDEX_UNSET
            override fun getUidOfPeriod(index: Int): Any = item.mediaId
        }
        every { player.currentTimeline } returns timeline
        every { player.currentMediaItem } returns item
        every { player.currentMediaItemIndex } returns 0
        every { player.mediaItemCount } returns 1
        every { player.playerError } returns null
        every { player.duration } returns 246_000L
    }

    @Composable private fun RealThumbnail(size: Float) {
        Thumbnail(thumbnailTapEnabledKey = true, isShowingLyrics = false, onShowLyrics = {},
            isShowingStatsForNerds = false, onShowStatsForNerds = {}, isShowingVisualizer = false,
            onShowEqualizer = {}, onMaximize = {}, onDoubleTap = {}, showthumbnail = true,
            modifier = Modifier.testTag("real.thumbnail").padding(playerThumbnailPadding(size)))
    }

    private fun cachedArtwork(filename: String): Uri {
        val png = files.newFile(filename)
        val source = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
        }
        png.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        source.recycle()
        val decoded = requireNotNull(BitmapFactory.decodeFile(png.absolutePath))
        assertEquals(android.graphics.Color.RED, decoded.getPixel(8, 8))
        // These tests measure the real painter's rendered bounds. Cache the decoded PNG so
        // asynchronous file decoding does not become a second condition for measuring layout.
        artworkCache[MemoryCache.Key("local-artwork-fixture")] = MemoryCache.Value(decoded.asImage())
        return Uri.fromFile(png)
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun paintedCoverSize(tag: String = "real.thumbnail"): Pair<Int, Int> {
        val pixels = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        fun red(x: Int, y: Int) = pixels[x, y].let { it.red > .9f && it.green < .1f && it.blue < .1f }
        return (0 until pixels.width).count { red(it, pixels.height / 2) } to
            (0 until pixels.height).count { red(pixels.width / 2, it) }
    }

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(16.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
            typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape), content = content)
    }
}
