package app.n_zik.android.components.glass

import android.app.Application
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowView

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassQueueRowSceneTest {
    // Retained RenderEffects need real window traversal before PixelCopy samples the surface.
    @get:Rule(order = 0)
    val nativeTraversal = object : ExternalResource() {
        private var animatorScale: String? = null

        override fun before() {
            if (Build.VERSION.SDK_INT >= 33) {
                val resolver = RuntimeEnvironment.getApplication().contentResolver
                animatorScale = Settings.Global.getString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE)
                // The unused badge press shader uses an AGSL qualifier unsupported by Robolectric.
                Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
                ShadowView.setUseRealDrawTraversals(true)
            }
        }

        override fun after() {
            if (Build.VERSION.SDK_INT >= 33) {
                ShadowView.setUseRealDrawTraversals(false)
                Settings.Global.putString(RuntimeEnvironment.getApplication().contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE, animatorScale)
            }
        }
    }
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun `queue scene excludes its own handle and front peers while retaining objects behind it`() {
        var scene: QueueRowScene? = null
        var underlay: Backdrop? = null
        val elevated = mutableStateOf(-1)
        val material = mutableStateOf(true)
        val settings = GlassSettings(enabled = true, film = 1f, rim = 0f, blur = 0f,
            vibrancy = 0f, depth = 0f, amount = 0f)
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            val player = rememberCanvasBackdrop { drawRect(Color.Red) }
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                Typography.fallback, shape, shape, shape), LocalGlassBackdrop provides player) {
                androidx.compose.foundation.layout.Column {
                    GlassQueuePanel(Modifier.size(180.dp, 80.dp), settings) {
                        val base = rememberQueueRowGlassBackdrop(Color.Transparent, settings)
                        val registry = LocalQueueRowScene.current
                        SideEffect { scene = registry; underlay = base }
                        QueueGlassRowObjects(0, elevated.value == 0) {
                            Box(Modifier.offset(140.dp, 28.dp).size(24.dp).glassQueueObjectBackdrop()) {
                                app.it.fast4x.rimusic.ui.components.themed.IconButton(
                                    icon = app.n_zik.android.R.drawable.reorder, color = Color.Green, onClick = {})
                            }
                            CompositionLocalProvider(LocalQueueRowBackdrop provides base) {
                                GlassSongRowSurface(Color.Transparent, Color.Blue.copy(alpha = .4f), shape, settings,
                                    useNowPlayingMaterial = material.value, paintBackground = false) { surface ->
                                    Box(Modifier.size(180.dp, 80.dp).clip(shape).then(surface))
                                }
                            }
                        }
                        QueueGlassRowObjects(1, elevated.value == 1) {
                            // The consuming row's sibling handle shares its exclusion owner.
                            Box(Modifier.offset(112.dp, 28.dp).size(24.dp).glassQueueObjectBackdrop()) {
                                app.it.fast4x.rimusic.ui.components.themed.IconButton(
                                    icon = app.n_zik.android.R.drawable.reorder, color = Color.Cyan, onClick = {})
                            }
                            CompositionLocalProvider(LocalQueueRowBackdrop provides base) {
                            GlassSongRowSurface(Color.Transparent, null, shape, settings,
                                useNowPlayingMaterial = false, paintBackground = false) { surface ->
                                Box(Modifier.size(180.dp, 80.dp).then(surface))
                            }
                            }
                        }
                        QueueGlassRowObjects(2) {
                            Box(Modifier.offset(80.dp, 28.dp).size(24.dp).glassQueueObjectBackdrop()) {
                                app.it.fast4x.rimusic.ui.components.themed.IconButton(
                                    icon = app.n_zik.android.R.drawable.reorder, color = Color.Magenta, onClick = {})
                            }
                        }
                    }
                    Box(Modifier.size(180.dp, 80.dp).testTag("queue.handle.source").drawBehind {
                        val row = scene?.rows?.lastOrNull { it.owner?.order == 1 }
                        val base = underlay
                        if (row != null && base != null) with(row.source(base)) {
                            drawBackdrop(this@drawBehind, row.foreground.layerCoordinates, null)
                        }
                    })
                }
            }
        }
        compose.waitForIdle()
        fun pixels() = compose.onNodeWithTag("queue.handle.source").captureToImage().toPixelMap()
        fun count(image: PixelMap, expected: Color): Int {
            var found = 0
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val color = image[x, y]
                if (kotlin.math.abs(color.red - expected.red) < .1f &&
                    kotlin.math.abs(color.green - expected.green) < .1f &&
                    kotlin.math.abs(color.blue - expected.blue) < .1f) found++
            }
            return found
        }
        var image = pixels()
        assertTrue("the lower peer's real reorder glyph remains behind the consumer", count(image, Color.Green) > 0)
        assertEquals("the consuming row cannot reflect its own sibling handle", 0, count(image, Color.Cyan))
        assertEquals("a higher peer cannot appear behind the consumer", 0, count(image, Color.Magenta))
        assertPixel("the lower material preserves its rounded corner", Color.Red, image, 178, 78)
        compose.runOnIdle { material.value = false }
        image = pixels()
        assertTrue("the native lower highlight remains behind its reorder glyph", count(image, Color.Green) > 0)
        assertPixel("the native lower highlight keeps the same clipped corner", Color.Red, image, 178, 78)
        compose.runOnIdle { elevated.value = 0 }
        image = pixels()
        assertEquals("a dragged peer is now in front, even with a lower list index", 0, count(image, Color.Green))
        compose.runOnIdle { elevated.value = 1 }
        image = pixels()
        assertTrue("the consuming dragged row now sees the formerly higher peer below it", count(image, Color.Magenta) > 0)
        assertEquals("raising the consumer never exposes its own handle", 0, count(image, Color.Cyan))
    }

    @Test fun `row scene clips a scrolled neighbor to the real list viewport`() {
        var scene: QueueRowScene? = null
        var underlay: Backdrop? = null
        var probeCoordinates: LayoutCoordinates? = null
        val settings = GlassSettings(source = GlassTintSource.None, film = 0f, rim = 0f)
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            val player = rememberCanvasBackdrop { drawRect(Color.Red) }
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                Typography.fallback, shape, shape, shape), LocalGlassBackdrop provides player) {
                Box(Modifier.size(180.dp, 160.dp)) {
                    GlassQueuePanel(Modifier.offset(y = 40.dp).size(180.dp, 80.dp), settings) {
                        val base = rememberQueueRowGlassBackdrop(Color.Transparent, settings)
                        val registry = LocalQueueRowScene.current
                        SideEffect { scene = registry; underlay = base }
                        CompositionLocalProvider(LocalQueueRowBackdrop provides base) {
                            val state = rememberLazyListState(initialFirstVisibleItemScrollOffset = 20)
                            LazyColumn(state = state, modifier = Modifier.size(180.dp, 80.dp).glassQueueListBackdrop(state)) {
                                items(3) { index ->
                                    QueueGlassRowObjects(index) {
                                        GlassSongRowSurface(Color.Transparent, null, shape, settings,
                                            useNowPlayingMaterial = false, paintBackground = false) { surface ->
                                            Box(Modifier.size(180.dp, 80.dp).then(surface).background(if (index == 0) Color.Green else Color.Blue))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Box(Modifier.size(180.dp, 160.dp).testTag("queue.outside.viewport")
                        .onGloballyPositioned { probeCoordinates = it }.drawBehind {
                            val row = scene?.rows?.firstOrNull { it.owner?.order == 1 }
                            val base = underlay
                            if (row != null && base != null) with(row.source(base)) {
                                // Match an optical consumer's revision read so the initial empty
                                // layer is redrawn once the lazy row publishes its first recording.
                                @Suppress("UNUSED_VARIABLE") val revision = contentVersion
                                drawBackdrop(this@drawBehind, probeCoordinates, null)
                            }
                        })
                }
            }
        }
        compose.waitForIdle()
        // A semantic idle alone need not draw the lazy list in the native test renderer.
        compose.onNodeWithTag("queue.outside.viewport").captureToImage()
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("The real preceding row must have a ready recording: " +
                scene?.rows?.map { "${it.owner?.order}: ${it.bounds}, ready=${it.foreground.hasRecording}" },
                scene?.rows?.any { it.owner?.order == 0 && it.foreground.hasRecording } == true)
        }
        val image = compose.onNodeWithTag("queue.outside.viewport").captureToImage().toPixelMap()
        assertPixel("the visible part of the scrolled neighbor is actually sampled", Color.Green, image, 80, 60,
            logicalHeight = 160)
        assertPixel("offscreen row foreground cannot leak above the panel viewport", Color.Red, image, 80, 30,
            logicalHeight = 160)
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun `normal spaced real queue row source excludes its own artwork and badge`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        context.preferences.edit().clear().putBoolean(app.it.fast4x.rimusic.utils.disableScrollingTextKey, true).commit()
        val player = io.mockk.mockk<androidx.media3.common.Player>(relaxed = true)
        io.mockk.every { player.currentMediaItem } returns androidx.media3.common.MediaItem.Builder().setMediaId("local:current").build()
        val binder = io.mockk.mockk<app.n_zik.android.playback.services.PlayerServiceModern.Binder>(relaxed = true)
        io.mockk.every { binder.player } returns player
        io.mockk.every { binder.playerUpdateTrigger } returns kotlinx.coroutines.flow.MutableStateFlow(0)
        var scene: QueueRowScene? = null
        var underlay: Backdrop? = null
        val settings = GlassSettings(source = GlassTintSource.None, blur = 0f, depth = 0f,
            amount = 0f, film = 0f, rim = 0f, areaModes = mapOf(
                GlassArea.NowPlaying to GlassAreaMode.GlassOnly,
                GlassArea.ActionButtons to GlassAreaMode.GlassOnly))
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            val playerSource = rememberCanvasBackdrop { drawRect(Color.Red) }
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                Typography.fallback, shape, shape, shape), app.n_zik.android.LocalPlayerServiceBinder provides binder,
                LocalGlassPreviewSettings provides settings, LocalGlassBackdrop provides playerSource) {
                androidx.compose.foundation.layout.Column {
                    GlassQueuePanel(Modifier.size(280.dp, 180.dp), settings) {
                        val rowUnderlay = rememberQueueRowGlassBackdrop(Color.Transparent, settings)
                        val rowScene = LocalQueueRowScene.current
                        SideEffect { scene = rowScene; underlay = rowUnderlay }
                        CompositionLocalProvider(LocalQueueRowBackdrop provides rowUnderlay) {
                            LazyColumn(Modifier.size(280.dp, 180.dp)) {
                                items(2) { index ->
                                    app.n_zik.android.components.SongItem(
                                        app.it.fast4x.rimusic.models.Song(if (index == 0) "local:neighbor" else "local:current",
                                            "${app.it.fast4x.rimusic.EXPLICIT_PREFIX}Current", "Artist", "1:00", null),
                                        showThumbnail = false, isLiked = true, inPlaylist = false,
                                        backgroundColor = Color.Transparent,
                                        thumbnailOverlay = { Box(Modifier.size(48.dp).background(Color.Green)) })
                                }
                            }
                        }
                    }
                    Box(Modifier.size(280.dp, 80.dp).testTag("real.queue.source").drawBehind {
                        val row = scene?.rows?.lastOrNull()
                        val base = underlay
                        if (row != null && base != null) with(row.source(base)) {
                            drawBackdrop(this@drawBehind, row.foreground.layerCoordinates, null)
                        }
                    })
                }
            }
        }
        compose.waitForIdle()
        val image = compose.onNodeWithTag("real.queue.source").captureToImage().toPixelMap()
        assertPixel("own artwork never echoes in its backdrop", Color.Red, image, 40, 20)
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            assertPixel("real row source continues to exclude its explicit badge and artwork", Color.Red,
                compose.onNodeWithTag("real.queue.source").captureToImage().toPixelMap(), 40, 20)
        }
    }

    @Test fun `retained optical queue row follows draw only foreground changes without recomposition`() {
        val color = mutableStateOf(Color.Green)
        var compositions = 0
        val settings = GlassSettings(source = GlassTintSource.None, blur = 1f, depth = .05f,
            amount = .05f, film = 0f, rim = 0f,
            areaModes = mapOf(GlassArea.NowPlaying to GlassAreaMode.GlassOnly))
        compose.setContent {
            SideEffect { compositions++ }
            val shape = RoundedCornerShape(8.dp)
            val player = rememberCanvasBackdrop { drawRect(Color.Red) }
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                Typography.fallback, shape, shape, shape), LocalGlassBackdrop provides player) {
                GlassQueuePanel(Modifier.size(180.dp, 80.dp), settings) {
                    val underlay = rememberQueueRowGlassBackdrop(Color.Transparent, settings)
                    CompositionLocalProvider(LocalQueueRowBackdrop provides underlay) {
                        GlassSongRowSurface(Color.Transparent, null, shape, settings, useNowPlayingMaterial = false,
                            paintBackground = false) { surface ->
                            Box(Modifier.size(180.dp, 80.dp).then(surface)) {
                                Box(Modifier.size(180.dp, 80.dp).graphicsLayer().drawBehind { drawRect(color.value) })
                            }
                        }
                        GlassSongRowSurface(Color.Transparent, Color.Transparent, shape, settings,
                            paintBackground = false) { surface ->
                            Box(Modifier.size(180.dp, 80.dp).graphicsLayer().testTag("draw.only.optics").then(surface))
                        }
                    }
                }
            }
        }
        fun pixels() = compose.onNodeWithTag("draw.only.optics").captureToImage().toPixelMap()
        assertPixel("first foreground recording reaches optics", Color.Green, pixels(), 80, 40)
        val settledCompositions = compositions
        compose.runOnIdle { color.value = Color.Cyan }
        compose.waitForIdle()
        assertEquals("the source changed only during draw", settledCompositions, compositions)
        assertPixel("draw only update reaches retained optics", Color.Cyan, pixels(), 80, 40)
    }

    @Test
    @Config(sdk = [33])
    fun `retained queue with two current occurrences keeps optics finite badge export and foreground sharp`() {
        val artwork = mutableStateOf(Color.Green)
        val elevated = mutableStateOf(-1)
        val probeIndex = mutableStateOf(0)
        val showProbe = mutableStateOf(false)
        val settings = GlassSettings(
            source = GlassTintSource.None, quality = GlassQuality.Balanced,
            film = 1f, rim = 0f, blur = 3f, vibrancy = 0f, depth = .3f, amount = .3f,
            areaModes = mapOf(
                GlassArea.NowPlaying to GlassAreaMode.GlassOnly,
                GlassArea.ActionButtons to GlassAreaMode.GlassOnly,
            ),
        )
        val highlight = Color.Blue.copy(alpha = .4f)
        val base = highlight.compositeOver(Color.Red)
        val badgeSettings = settings.copy(film = .25f)
        val badgeExpected = Color(0xFF4A4A4E).copy(alpha = badgeSettings.film)
            .compositeOver(highlight.compositeOver(base))
        var playerRecords = 0
        var scene: QueueRowScene? = null
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            val player = rememberLayerBackdrop(onDraw = { playerRecords++; drawContent() })
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
                ),
                LocalGlassBackdrop provides player,
                LocalGlassContainerBackdrop provides player,
            ) {
                Box(Modifier.size(180.dp, 240.dp)) {
                    Box(Modifier.size(180.dp, 140.dp).graphicsLayer().layerBackdrop(player).background(Color.Red))
                    GlassQueuePanel(Modifier.size(180.dp, 140.dp).testTag("retained.queue"), settings) {
                        val underlay = rememberQueueRowGlassBackdrop(Color.Transparent, settings)
                        val listState = rememberLazyListState()
                        val currentScene = LocalQueueRowScene.current
                        SideEffect { scene = currentScene }
                        CompositionLocalProvider(LocalQueueRowBackdrop provides underlay) {
                            // Overlap makes neighboring foreground observable at deterministic optical coordinates.
                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy((-40).dp),
                                modifier = Modifier.size(180.dp, 140.dp).graphicsLayer()
                                    .glassQueueListBackdrop(listState),
                            ) {
                                items(2, key = { "same-song-occurrence-$it" }, contentType = { "current-song" }) { index ->
                                    QueueGlassRowObjects(index, elevated.value == index) {
                                    GlassSongRowSurface(
                                        Color.Transparent, highlight, shape, settings, paintBackground = false,
                                    ) { surface ->
                                        Box(Modifier.size(180.dp, 80.dp).zIndex(if (elevated.value == index) 1f else 0f)
                                            .testTag("retained.current.$index")
                                            .clip(shape).then(surface).clip(shape)) {
                                            if (index == 0) {
                                                Box(Modifier.offset(16.dp, 44.dp).size(48.dp, 32.dp)
                                                    .background(artwork.value))
                                            } else {
                                                Box(Modifier.offset(96.dp, 52.dp).size(16.dp)
                                                    .testTag("retained.sharp.glyph").drawBehind {
                                                        drawRect(Color.Yellow, size = Size(size.width / 2, size.height))
                                                    })
                                                Box(Modifier.offset(140.dp, 24.dp).size(20.dp)
                                                    .testTag("retained.explicit")
                                                    .glassExplicitBadgeBackground(badgeSettings)) {
                                                    Image(painterResource(explicitBadgeIconId(badgeSettings)), "Explicit")
                                                }
                                            }
                                        }
                                    }
                                    }
                                }
                            }
                        }
                    }
                    if (showProbe.value) Box(Modifier.offset(y = 160.dp).size(180.dp, 80.dp)
                        .testTag("retained.material.probe").drawBehind {
                            val material = scene?.rows?.firstOrNull { it.owner?.order == probeIndex.value }?.material
                            if (material != null) with(material) {
                                drawBackdrop(this@drawBehind, layerCoordinates, null)
                            }
                        })
                }
            }
        }
        fun row() = compose.onNodeWithTag("retained.current.1").captureToImage().toPixelMap()
        compose.waitForIdle()
        compose.onNodeWithTag("retained.queue").captureToImage()
        var image = row()
        compose.runOnIdle {
            val captures = requireNotNull(scene).rows
            assertEquals("both occurrences of one song retain a separate foreground", 2, captures.size)
            assertTrue("row identity must not be the song id", captures[0].key !== captures[1].key)
            assertTrue("both foreground recordings are ready: " + captures.map {
                "bounds=${it.bounds}, recorded=${it.foreground.hasRecording}, attached=${it.foreground.layerCoordinates?.isAttached}"
            }, captures.all { it.foreground.hasRecording })
        }
        assertPixel("neighbor foreground passes through nonzero optics", highlight.compositeOver(Color.Green), image, 40, 20)
        assertPixel("overlapping current row includes the material of the occurrence behind it",
            highlight.compositeOver(base), image, 100, 20)
        assertPixel("the nonoverlapping row material contains its player", base, image, 80, 64)
        val badge = compose.onNodeWithTag("retained.explicit").captureToImage().toPixelMap()
        val badgeGap = badge[badge.width / 2, badge.height * 2 / 5]
        assertEquals("badge film composites over its row export red", badgeExpected.red, badgeGap.red, .025f)
        assertEquals("badge film composites over its row export green", badgeExpected.green, badgeGap.green, .025f)
        assertEquals("badge film composites over its row export blue", badgeExpected.blue, badgeGap.blue, .025f)
        val glyph = compose.onNodeWithTag("retained.sharp.glyph").captureToImage().toPixelMap()
        assertEquals("foreground glyph remains sharp", Color.Yellow, glyph[glyph.width / 4, glyph.height / 2])
        assertEquals("optics do not smear the glyph into its right half", base.red,
            glyph[glyph.width * 3 / 4, glyph.height / 2].red, .025f)

        compose.runOnIdle { artwork.value = Color.Cyan }
        compose.waitForIdle()
        image = row()
        assertPixel("retained neighbor artwork change reaches current row", highlight.compositeOver(Color.Cyan), image, 40, 20)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        row()
        val settledRecords = playerRecords
        assertTrue("the retained player was actually recorded", settledRecords > 0)
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            image = row()
            assertPixel("two current materials settle without recursive feedback", base, image, 80, 64)
        }
        assertEquals("idle materials do not continually re-record their retained player", settledRecords, playerRecords)

        repeat(4) { iteration ->
            compose.runOnIdle {
                showProbe.value = false
                elevated.value = if (iteration % 2 == 0) 0 else 1
            }
            compose.waitForIdle()
            val front = if (iteration % 2 == 0) 0 else 1
            val behind = 1 - front
            fun overlap(index: Int) = compose.onNodeWithTag("retained.current.$index")
                .captureToImage().toPixelMap()
            val visibleFront = overlap(front)
            assertPixel("drag reversal $iteration is correct before any diagnostic probe exists",
                highlight.compositeOver(base), visibleFront, 100, if (front == 0) 60 else 20)
            compose.runOnIdle { showProbe.value = true; probeIndex.value = behind }
            val behindExport = compose.onNodeWithTag("retained.material.probe").captureToImage().toPixelMap()
            assertPixel("drag reversal $iteration lower export excludes the front row",
                base, behindExport, 100, if (behind == 0) 60 else 20)
            compose.runOnIdle { probeIndex.value = front }
            val frontExport = compose.onNodeWithTag("retained.material.probe").captureToImage().toPixelMap()
            assertPixel("drag reversal $iteration front export includes the lower material once",
                highlight.compositeOver(base), frontExport, 100, if (front == 0) 60 else 20)
            assertPixel("drag reversal $iteration includes the lower material exactly once",
                highlight.compositeOver(base), overlap(front), 100, if (front == 0) 60 else 20)
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertPixel("drag reversal $iteration settles without a feedback cycle",
                highlight.compositeOver(base), overlap(front), 100, if (front == 0) 60 else 20)
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        row()
        fun materialVersions() = requireNotNull(scene).rows.map { it.material?.contentVersion }
        val settledMaterials = compose.runOnIdle { materialVersions() }
        val settledPlayerAfterReversal = playerRecords
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            row()
        }
        assertEquals("reversed material exports stop recording at idle", settledMaterials,
            compose.runOnIdle { materialVersions() })
        assertEquals("reversal does not restart the player recording loop", settledPlayerAfterReversal, playerRecords)
    }

    @Test
    fun `selected glass follows objects behind it without echoing its own moving foreground`() {
        val showNeighbor = mutableStateOf(false)
        val neighborX = mutableStateOf(8.dp)
        val neighborColor = mutableStateOf(Color.Green)
        val selectedY = mutableStateOf(8.dp)
        val playerColor = mutableStateOf(Color.Red)
        val nativeFill = mutableStateOf(Color.Blue.copy(alpha = .35f))
        val ownMarker = Color.Yellow.copy(alpha = .5f)
        val settings = GlassSettings(
            source = GlassTintSource.None, quality = GlassQuality.Balanced,
            film = 0f, rim = 0f, blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f,
            areaModes = mapOf(GlassArea.NowPlaying to GlassAreaMode.GlassOnly),
        )
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            val player = rememberCanvasBackdrop {
                drawRect(playerColor.value)
                drawRect(Color.White, Offset(size.width * .75f, 0f), Size(size.width * .25f, size.height))
            }
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
                ),
                LocalGlassBackdrop provides player,
                LocalGlassContainerBackdrop provides player,
            ) {
                GlassQueuePanel(Modifier.size(180.dp, 140.dp).drawBehind {
                    drawRect(playerColor.value)
                    drawRect(Color.White, Offset(size.width * .75f, 0f), Size(size.width * .25f, size.height))
                }.background(nativeFill.value), settings) {
                    val underlay = rememberQueueRowGlassBackdrop(nativeFill.value, settings)
                    CompositionLocalProvider(LocalQueueRowBackdrop provides underlay) {
                        if (showNeighbor.value) {
                            QueueGlassRowObjects(0) {
                            GlassSongRowSurface(
                                Color.Transparent, null, shape, settings,
                                useNowPlayingMaterial = false, paintBackground = false,
                            ) { surface ->
                                Box(Modifier.offset(neighborX.value, 24.dp).size(120.dp, 80.dp).then(surface)) {
                                    Box(Modifier.offset(16.dp, 16.dp).size(16.dp).background(neighborColor.value))
                                }
                            }
                            }
                        }
                        QueueGlassRowObjects(1) {
                        GlassSongRowSurface(
                            Color.Transparent, Color.Transparent, shape, settings, paintBackground = false,
                        ) { surface ->
                            Box(Modifier.offset(y = selectedY.value).size(180.dp, 80.dp)
                                .testTag("selected.scene.row").then(surface)) {
                                Box(Modifier.offset(96.dp, 56.dp).size(16.dp).background(ownMarker))
                            }
                        }
                        }
                    }
                }
            }
        }

        fun base() = nativeFill.value.compositeOver(playerColor.value)
        fun pixels(): PixelMap {
            compose.waitForIdle()
            return compose.onNodeWithTag("selected.scene.row").captureToImage().toPixelMap()
        }
        fun assertUnderlay(image: PixelMap) {
            assertPixel("queue alpha over player", base(), image, 64, 16)
            assertPixel("player pattern through queue alpha", nativeFill.value.compositeOver(Color.White), image, 160, 16)
            assertPixel("own marker is drawn exactly once above the material",
                ownMarker.compositeOver(base()), image, 104, 64)
        }

        var image = pixels()
        assertUnderlay(image)
        assertPixel("no neighbor before composition", base(), image, 32, 40)

        compose.runOnIdle { showNeighbor.value = true }
        compose.waitForIdle()
        image = pixels()
        assertPixel("late first neighbor recording reaches selected material", Color.Green, image, 32, 40)
        assertUnderlay(image)

        compose.runOnIdle { neighborColor.value = Color.Cyan }
        compose.waitForIdle()
        assertPixel("mounted neighbor artwork update", Color.Cyan, pixels(), 32, 40)

        compose.runOnIdle { neighborX.value = 56.dp }
        compose.waitForIdle()
        image = pixels()
        assertPixel("neighbor leaves no stale artwork", base(), image, 32, 40)
        assertPixel("neighbor follows its window coordinates", Color.Cyan, image, 80, 40)

        compose.runOnIdle { selectedY.value = 24.dp }
        compose.waitForIdle()
        image = pixels()
        assertPixel("moving selected pane samples the neighbor at its new local position", Color.Cyan, image, 80, 24)
        assertPixel("selected foreground leaves no self echo at its former window position", base(), image, 104, 48)
        assertUnderlay(image)

        compose.runOnIdle { showNeighbor.value = false }
        compose.waitForIdle()
        image = pixels()
        assertPixel("disposed neighbor is absent from retained source", base(), image, 80, 24)
        assertUnderlay(image)

        compose.runOnIdle { playerColor.value = Color.Magenta }
        compose.waitForIdle()
        assertUnderlay(pixels())

        compose.runOnIdle { nativeFill.value = Color.Blue.copy(alpha = .6f) }
        compose.waitForIdle()
        assertUnderlay(pixels())
    }

    @Test
    fun `queue role film retains native highlight alpha and scene objects at full film then settles idle`() {
        val film = mutableStateOf(0f)
        val tintSource = mutableStateOf(GlassTintSource.None)
        var playerSamples = 0
        val player = object : Backdrop {
            override val isCoordinatesDependent = false
            override val contentVersion = 0

            override fun DrawScope.drawBackdrop(
                density: Density,
                coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?,
            ) {
                playerSamples++
                drawRect(Color.Red)
                drawRect(Color.White, Offset(size.width * .75f, 0f), Size(size.width * .25f, size.height))
            }
        }
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            val settings = GlassSettings(
                source = tintSource.value, customColor = Color.Blue.toArgb(), tintStrength = 1f,
                quality = GlassQuality.Balanced, film = film.value, rim = 0f,
                blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f,
                areaModes = mapOf(GlassArea.NowPlaying to GlassAreaMode.GlassOnly),
            )
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
                ),
                LocalGlassBackdrop provides player,
                LocalGlassContainerBackdrop provides player,
            ) {
                GlassQueuePanel(Modifier.size(180.dp, 80.dp).drawBehind {
                    drawRect(Color.Red)
                    drawRect(Color.White, Offset(size.width * .75f, 0f), Size(size.width * .25f, size.height))
                }, settings) {
                    val underlay = rememberQueueRowGlassBackdrop(Color.Transparent, settings)
                    CompositionLocalProvider(LocalQueueRowBackdrop provides underlay) {
                        GlassSongRowSurface(
                            Color.Transparent, null, shape, settings,
                            useNowPlayingMaterial = false, paintBackground = false,
                        ) { surface ->
                            Box(Modifier.size(180.dp, 80.dp).then(surface)) {
                                Box(Modifier.offset(16.dp, 16.dp).size(16.dp).background(Color.Green))
                            }
                        }
                        GlassSongRowSurface(
                            Color.Transparent, Color.Magenta.copy(alpha = .4f), shape, settings,
                            paintBackground = false,
                        ) { surface ->
                            Box(Modifier.size(180.dp, 80.dp).testTag("selected.scene.film").then(surface))
                        }
                    }
                }
            }
        }
        fun pixels(): PixelMap {
            compose.waitForIdle()
            return compose.onNodeWithTag("selected.scene.film").captureToImage().toPixelMap()
        }
        for (source in listOf(GlassTintSource.None, GlassTintSource.Custom)) {
            for (opacity in listOf(0f, .5f, 1f)) {
                compose.runOnIdle { tintSource.value = source; film.value = opacity }
                compose.waitForIdle()
                val image = pixels()
                val tint = (if (source == GlassTintSource.None) Color.Magenta else Color.Blue)
                    .copy(alpha = .4f * opacity)
                assertPixel("$source film=$opacity preserves player", tint.compositeOver(Color.Red), image, 80, 40)
                assertPixel("$source film=$opacity preserves player pattern", tint.compositeOver(Color.White), image, 160, 40)
                assertPixel("$source film=$opacity preserves neighbor object", tint.compositeOver(Color.Green), image, 24, 24)
            }
        }

        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        pixels()
        val settledSamples = playerSamples
        assertTrue("the real selected material must have sampled its player source", settledSamples > 0)
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val image = pixels()
            assertPixel("settled full film retains the neighbor", Color.Blue.copy(alpha = .4f).compositeOver(Color.Green), image, 24, 24)
        }
        assertEquals("an idle queue must reuse its settled optical capture", settledSamples, playerSamples)
    }

    private fun assertPixel(
        message: String, expected: Color, image: PixelMap, x: Int, y: Int, logicalHeight: Int = 80,
    ) {
        val actual = image[x * image.width / 180, y * image.height / logicalHeight]
        assertEquals("$message red", expected.red, actual.red, .015f)
        assertEquals("$message green", expected.green, actual.green, .015f)
        assertEquals("$message blue", expected.blue, actual.blue, .015f)
        assertEquals("$message alpha", expected.alpha, actual.alpha, .015f)
    }
}
