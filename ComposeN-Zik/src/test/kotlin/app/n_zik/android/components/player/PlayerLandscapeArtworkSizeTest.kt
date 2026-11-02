package app.n_zik.android.components.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.enums.ThumbnailType
import app.it.fast4x.rimusic.ui.screens.player.Thumbnail
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.thumbnailTypeKey
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.appContext
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.player.slide.playerThumbnailCover
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.FormatTable
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.playback.services.PlayerServiceModern
import coil3.ImageLoader
import coil3.asImage
import coil3.memory.MemoryCache
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
@Config(sdk = [32], application = Application::class, qualifiers = "w1200dp-h600dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
class PlayerLandscapeArtworkSizeTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val files = TemporaryFolder()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val player = mockk<Player>(relaxed = true)
    private val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
    private lateinit var loader: ImageLoader
    private lateinit var artworkCache: MemoryCache

    @Before fun setup() {
        context.preferences.edit().clear().putBoolean("buttonzoomout", false)
            .putString(thumbnailTypeKey, ThumbnailType.Modern.name).commit()
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
        mockkObject(Database)
        every { Database.songTable } returns songs
        every { Database.formatTable } returns formats
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

    @After fun cleanup() {
        loader.shutdown()
        context.preferences.edit().clear().commit()
        unmockkAll()
    }

    @Test fun `actual landscape covers honor saved insets and maximize square at100 above every toolbar mode`() {
        val artworkUri = cachedArtwork("landscape-cover.png")
        val item = MediaItem.Builder().setMediaId("landscape-size")
            .setMediaMetadata(MediaMetadata.Builder().setArtworkUri(artworkUri).build()).build()
        val timeline = object : Timeline() {
            override fun getWindowCount() = 1
            override fun getPeriodCount() = 1
            override fun getWindow(index: Int, window: Timeline.Window, projectionUs: Long): Timeline.Window =
                window.set(Timeline.Window.SINGLE_WINDOW_UID, item, null, C.TIME_UNSET, C.TIME_UNSET,
                    C.TIME_UNSET, true, false, null, 0L, 246_000_000L, 0, 0, 0L)
            override fun getPeriod(index: Int, period: Timeline.Period, setIds: Boolean): Timeline.Period =
                period.set(null, if (setIds) item.mediaId else null, 0, 246_000_000L, 0L)
            override fun getIndexOfPeriod(uid: Any) = if (uid == item.mediaId) 0 else C.INDEX_UNSET
            override fun getUidOfPeriod(index: Int): Any = item.mediaId
        }
        every { player.currentTimeline } returns timeline
        every { player.currentMediaItem } returns item
        every { player.currentMediaItemIndex } returns 0
        every { player.mediaItemCount } returns 1
        every { player.playerError } returns null
        every { player.duration } returns 246_000L
        val type = mutableStateOf(PlayerType.Essential)
        val preference = mutableStateOf(100f)
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val height = mutableStateOf(460.dp)
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            val geometry = remember { PlayerTransportGeometry() }
            val settings = GlassSettings(areaModes = mapOf(GlassArea.ActionButtons to mode.value))
            val toolbarLayout = rememberPlayerActionToolbarLayout(settings)
            CompositionLocalProvider(LocalPlayerServiceBinder provides binder,
                LocalPlayerTransportGeometry provides geometry,
                LocalAppearance provides Appearance(DefaultDarkColorPalette,
                    typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape)) {
                Box(Modifier.requiredSize(1000.dp, height.value).playerTransportGeometryHost().testTag("landscape.root")) {
                    PlayerLandscapeColumns(contentInsets = WindowInsets(0), artwork = {
                        val padding = playerThumbnailPadding(preference.value, landscape = true)
                        if (type.value == PlayerType.Essential) {
                            PlayerLandscapeThumbnailFrame(padding) {
                                Thumbnail(true, false, {}, false, {}, false, {}, {}, {}, true,
                                    modifier = Modifier.padding(padding).testTag("landscape.cover"), verticalPadding = 0.dp)
                            }
                        } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val state = rememberPagerState { 1 }
                            val carousel = playerCarouselGeometry(maxWidth, maxHeight, padding, 10.dp, false)
                            VerticalPager(state, pageSize = PageSize.Fixed(carousel.pageSize),
                                pageSpacing = carousel.pageSpacing,
                                contentPadding = PaddingValues(vertical = (maxHeight - carousel.pageSize) / 2),
                                modifier = Modifier.fillMaxSize()) { page ->
                                val painter = ImageCacheFactory.Painter(item.mediaMetadata.artworkUri.toString())
                                PlayerCarouselCover(page, 0, item, item.mediaId, false, true, false, true,
                                    false, {}, { 246_000L }, true, true,
                                    modifier = Modifier.playerThumbnailCover(state, page, carousel.coverPadding,
                                        false, false, .9f, thumbnailType = ThumbnailType.Modern,
                                        showCoverThumbnailAnimation = false, onTap = {}, onLongClick = {})
                                        .testTag("landscape.cover")) {
                                    Image(painter, "", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                }
                            }
                        }
                    }, controls = {
                        Box(Modifier.weight(1f).fillMaxWidth().testTag("landscape.controls"))
                        Column(Modifier.fillMaxWidth().requiredHeight(toolbarLayout.hostHeight(50.dp)),
                            verticalArrangement = Arrangement.SpaceAround) {
                            PlayerActionToolbar(listOf("a", "b").map { PlayerActionSlot(it) { Box(Modifier.size(24.dp)) } },
                                true, settings = settings, layout = toolbarLayout, horizontalInsets = WindowInsets(0), landscape = true)
                        }
                    })
                }
            }
        }
        compose.waitUntil(10_000) { paintedCover().first > 0 }
        for (slotHeight in listOf(460.dp, 560.dp)) for (optical in GlassAreaMode.entries) {
            compose.runOnIdle { height.value = slotHeight; mode.value = optical }
            val controls = bounds("landscape.controls")
            for (playerType in listOf(PlayerType.Essential, PlayerType.Modern)) {
                compose.runOnIdle { type.value = playerType }
                compose.waitUntil(10_000) { paintedCover().first > 0 }
                for (size in listOf(100f, 50f, 0f, 100f)) {
                    compose.runOnIdle { preference.value = size }
                    val viewport = bounds("player.landscapeArtwork.viewport")
                    val toolbar = bounds(if (optical.hasMaterial) "player.actionToolbar.material" else "player.actionToolbar.native")
                    val painted = paintedCover()
                    val expected = minOf(viewport.width, viewport.height) - (100f - size) * compose.density.density -
                        if (playerType == PlayerType.Modern) with(compose.density) { 20.dp.toPx() } else 0f
                    val label = "$slotHeight $optical $playerType size=$size"
                    assertEquals("Actual saved artwork width $label", expected, painted.first.toFloat(), 2f)
                    assertEquals("Actual artwork stays square $label", painted.first.toFloat(), painted.second.toFloat(), 1f)
                    assertTrue("Viewport clears toolbar bottom $label", viewport.bottom <= toolbar.bottom + 1f)
                    val cover = bounds("landscape.cover")
                    assertEquals("Measured image canvas stays square $label", cover.width, cover.height, 1f)
                    assertTrue("Image canvas clears toolbar bottom $label", cover.bottom <= toolbar.bottom + 1f)
                    assertEquals("Changing artwork never moves controls $label", controls, bounds("landscape.controls"))
                }
            }
        }
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

    private fun paintedCover(): Pair<Int, Int> {
        val pixels = compose.onNodeWithTag("player.landscapeArtwork.viewport", useUnmergedTree = true)
            .captureToImage().toPixelMap()
        fun red(x: Int, y: Int) = pixels[x, y].let { it.red > .9f && it.green < .1f && it.blue < .1f }
        var widest = 0
        var tallest = 0
        for (y in 0 until pixels.height) widest = maxOf(widest, (0 until pixels.width).count { red(it, y) })
        for (x in 0 until pixels.width) tallest = maxOf(tallest, (0 until pixels.height).count { red(x, it) })
        return widest to tallest
    }
}
