package app.n_zik.android.legacyoffmain.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.screens.player.MiniPlayer
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.LocalPendingMiniPlayerAction
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.LocalPlayerSheetState
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.enums.PendingMiniPlayerAction
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MiniPlayerCoverLikePlacementTest {
    @get:Rule val compose = createComposeRule()

    @After fun cleanup() = unmockkAll()

    @Test fun `material mini cover heart retains its complete glyph beyond the artwork edge`() = verifyHeart(false)

    @Test fun `glass mini cover heart retains its complete glyph beyond the artwork edge`() = verifyHeart(true)

    private fun verifyHeart(glass: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        setActiveProfile("default", context)
        context.preferences.edit().clear().putBoolean(glassEnabledKey, glass)
            .putBoolean(disableScrollingTextKey, true).commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        val songs = mockk<SongTable>()
        every { songs.likeState(any()) } returns flowOf(true)
        mockkObject(Database)
        every { Database.songTable } returns songs
        val player = mockk<Player>(relaxed = true)
        every { player.currentMediaItem } returns MediaItem.Builder().setMediaId("local:cover-heart")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Title").setArtist("Artist").build()).build()
        every { player.isCommandAvailable(any()) } returns true
        every { player.duration } returns 120_000L
        every { player.playbackState } returns Player.STATE_READY
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        every { binder.player } returns player
        every { binder.playerUpdateTrigger } returns MutableStateFlow(0)
        val sheet = mockk<PlayerSheetState> { every { progress } returns 0f }
        val pending = mutableStateOf<PendingMiniPlayerAction?>(null)
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            // Neutral backdrops keep blue-channel coverage independent of antialias blending.
            val palette = DefaultDarkColorPalette.copy(
                accent = Color.Blue,
                background0 = Color.Black,
                background1 = Color.Black,
                background2 = Color.Black,
                background3 = Color.Black,
                background4 = Color.Black,
            )
            CompositionLocalProvider(
                LocalAppearance provides Appearance(palette, Typography.fallback, shape, shape, shape),
                LocalPlayerServiceBinder provides binder,
                LocalPlayerSheetState provides sheet,
                LocalPendingMiniPlayerAction provides pending,
                LocalGlassPreviewSettings provides GlassSettings(enabled = glass, tintStrength = 0f),
            ) {
                Column(Modifier.background(Color.Black)) {
                    Box(Modifier.size(40.dp).background(Color.Black)) {
                        HeaderIconButton({}, R.drawable.heart, Color.Blue, iconSize = 10.dp,
                            modifier = Modifier.testTag("reference"))
                    }
                    Box(Modifier.fillMaxWidth().testTag("mini")) { MiniPlayer({}, {}) }
                }
            }
        }
        val badgeMatcher = hasClickAction() and hasAnyAncestor(hasTestTag("mini")) and
            SemanticsMatcher("14 pixel cover badge") { it.boundsInRoot.width == 14f && it.boundsInRoot.height == 14f }
        compose.waitUntil(5_000) { compose.onAllNodes(badgeMatcher).fetchSemanticsNodes().size == 1 }
        val badge = compose.onNode(badgeMatcher)
        val bounds = badge.fetchSemanticsNode().boundsInRoot
        val miniBounds = compose.onNodeWithTag("mini").fetchSemanticsNode().boundsInRoot
        // The row adds its 12dp item gap after the leading spacer, before the 5dp badge overhang.
        assertEquals("The entire badge canvas must move with its glyph", miniBounds.left + 25f, bounds.left, .01f)
        val reference = compose.onNodeWithTag("reference").captureToImage().toPixelMap()
        val actual = badge.captureToImage().toPixelMap()
        assertEquals(reference.width, actual.width)
        assertEquals(reference.height, actual.height)
        fun Color.isHeart() = blue > red + .1f && blue > green + .1f
        var heartPixels = 0
        for (y in 0 until reference.height) for (x in 0 until reference.width) {
            assertEquals("The real mini must preserve both heart lobes at $x,$y " +
                "(reference=${reference[x, y]}, actual=${actual[x, y]})",
                reference[x, y].isHeart(), actual[x, y].isHeart())
            if (actual[x, y].isHeart()) heartPixels++
        }
        assertTrue("The pixel comparison must contain the rendered heart", heartPixels > 15)
    }
}
