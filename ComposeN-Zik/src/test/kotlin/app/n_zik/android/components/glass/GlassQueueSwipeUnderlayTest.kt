package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.components.SwipeableContent
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
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
class GlassQueueSwipeUnderlayTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @After fun clearPreferences() {
        context.preferences.edit().clear().commit()
    }

    @OptIn(ExperimentalFoundationApi::class)
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun `partially swiped real current row samples underlying swipe film and glyph without its own foreground`() {
        context.preferences.edit().clear().putBoolean(disableScrollingTextKey, true).commit()
        val player = mockk<Player>(relaxed = true)
        every { player.currentMediaItem } returns MediaItem.Builder().setMediaId("local:current").build()
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        every { binder.player } returns player
        every { binder.playerUpdateTrigger } returns MutableStateFlow(0)
        var scene: QueueRowScene? = null
        var underlay: Backdrop? = null
        var swipeCoordinates: LayoutCoordinates? = null
        var actions = 0
        val settings = GlassSettings(enabled = true, source = GlassTintSource.None,
            film = .65f, rim = 0f, blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f,
            areaModes = mapOf(GlassArea.NowPlaying to GlassAreaMode.GlassOnly,
                GlassArea.ActionButtons to GlassAreaMode.GlassOnly))
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            val playerSource = rememberCanvasBackdrop { drawRect(Color.Red) }
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette.copy(accent = Color.Cyan),
                    Typography.fallback, shape, shape, shape),
                LocalPlayerServiceBinder provides binder,
                LocalGlassPreviewSettings provides settings,
                LocalGlassBackdrop provides playerSource,
            ) {
                Column {
                    GlassQueuePanel(Modifier.size(360.dp, 80.dp), settings) {
                        val base = rememberQueueRowGlassBackdrop(Color.Transparent, settings)
                        val registry = LocalQueueRowScene.current
                        SideEffect { scene = registry; underlay = base }
                        CompositionLocalProvider(LocalQueueRowBackdrop provides base) {
                            QueueGlassRowObjects(0) {
                                SwipeableContent(
                                    swipeToRightIcon = R.drawable.reorder,
                                    onSwipeToLeft = { actions++ }, onSwipeToRight = { actions++ },
                                    backgroundColor = Color.Transparent,
                                    modifier = Modifier.testTag("queue.swipe")
                                        .onGloballyPositioned { swipeCoordinates = it },
                                ) {
                                    SongItem(Song("local:current", "Current", "Artist", "1:00", null),
                                        modifier = Modifier.testTag("queue.swipe.row"),
                                        showThumbnail = false, isLiked = true, inPlaylist = false,
                                        backgroundColor = Color.Transparent,
                                        thumbnailOverlay = { Box(Modifier.size(48.dp).background(Color.Magenta)) })
                                }
                            }
                        }
                    }
                    Box(Modifier.size(360.dp, 80.dp).testTag("queue.swipe.source").drawBehind {
                        val row = scene?.rows?.firstOrNull { it.material != null }
                        val base = underlay
                        if (row != null && base != null) with(row.source(base)) {
                            @Suppress("UNUSED_VARIABLE") val revision = contentVersion
                            drawBackdrop(this@drawBehind, swipeCoordinates, null)
                        }
                    })
                }
            }
        }
        compose.waitForIdle()
        val initial = compose.onNodeWithTag("queue.swipe.row").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("queue.swipe").performTouchInput {
            down(Offset(width * .4f, center.y))
            moveTo(Offset(width * .4f + 40f, center.y), delayMillis = 32)
            moveTo(Offset(width * .4f + 50f, center.y), delayMillis = 32)
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val moved = compose.onNodeWithTag("queue.swipe.row").fetchSemanticsNode().boundsInRoot
        val offset = moved.left - initial.left
        assertTrue("Fixture must retain a partially translated actual row: $offset", offset > 8f && offset < 90f)
        assertEquals("The held partial swipe must not execute an action", 0, actions)
        val visible = compose.onNodeWithTag("queue.swipe").captureToImage().toPixelMap()
        val source = compose.onNodeWithTag("queue.swipe.source").captureToImage().toPixelMap()
        val y = 10.coerceAtMost(visible.height - 1)
        val exposedX = (offset / 2).toInt().coerceIn(8, visible.width - 1)
        val expectedFilm = visible[exposedX, y]
        assertTrue("Fixture exposes a non-red swipe material", expectedFilm.green > .05f)
        val insideMovingRowX = (offset + 100).toInt().coerceAtMost(source.width - 1)
        val sampledFilm = source[insideMovingRowX, y]
        assertEquals("Row source must include actual swipe film red", expectedFilm.red, sampledFilm.red, .03f)
        assertEquals("Row source must include actual swipe film green", expectedFilm.green, sampledFilm.green, .03f)
        assertEquals("Row source must include actual swipe film blue", expectedFilm.blue, sampledFilm.blue, .03f)
        var cyan = 0
        var magenta = 0
        for (py in 0 until source.height) for (px in 0 until source.width) {
            val color = source[px, py]
            if (color.red < .1f && color.green > .8f && color.blue > .8f) cyan++
            if (color.red > .8f && color.green < .1f && color.blue > .8f) magenta++
        }
        assertTrue("The underlying action glyph belongs in the row source", cyan > 0)
        assertEquals("The moving row cannot sample its own artwork", 0, magenta)
        compose.onNodeWithTag("queue.swipe").performTouchInput { cancel() }
    }
}
