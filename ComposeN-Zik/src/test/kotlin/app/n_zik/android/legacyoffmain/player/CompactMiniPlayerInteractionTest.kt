package app.n_zik.android.legacyoffmain.player

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.screens.player.MiniPlayer
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.it.fast4x.rimusic.utils.showMiniPlayerAudioOutputKey
import app.it.fast4x.rimusic.utils.showMiniPlayerPlayPauseKey
import app.it.fast4x.rimusic.utils.showMiniPlayerQueueKey
import app.it.fast4x.rimusic.utils.showMiniPlayerSkipBackKey
import app.it.fast4x.rimusic.utils.showMiniPlayerSkipForwardKey
import app.n_zik.android.Dependencies
import app.n_zik.android.LocalPendingMiniPlayerAction
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.LocalPlayerSheetState
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.glass.QueueEmbeddedMini
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.navigation.dock.LocalCompactMiniPlayerProgress
import app.n_zik.android.components.navigation.dock.LocalCompactMiniPlayerProgressProvider
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.core.navigation.LocalMiniPlayerQueueRequest
import app.n_zik.android.enums.MiniPlayerButton
import app.n_zik.android.enums.PendingMiniPlayerAction
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Production compact controls retain the same handlers and queue ownership as the expanded mini. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CompactMiniPlayerInteractionTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var context: Context
    private lateinit var navigation: NavHostController
    private val player = mockk<Player>(relaxed = true)
    private val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
    private var progress by mutableStateOf(1f)
    private var generation by mutableIntStateOf(0)
    private var queueOpens = 0
    private var playerOpens = 0
    private val body = SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        setActiveProfile("default", context)
        context.preferences.edit().clear()
            .putBoolean(glassEnabledKey, false)
            .putBoolean(disableScrollingTextKey, true)
            .putBoolean(showMiniPlayerPlayPauseKey, true)
            .putBoolean(showMiniPlayerSkipBackKey, true)
            .putBoolean(showMiniPlayerSkipForwardKey, true)
            .putBoolean(showMiniPlayerAudioOutputKey, true)
            .putBoolean(showMiniPlayerQueueKey, false).commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        val songs = mockk<SongTable>()
        every { songs.likeState(any()) } returns flowOf(null)
        mockkObject(Database)
        every { Database.songTable } returns songs
        every { binder.player } returns player
        every { binder.playerUpdateTrigger } returns MutableStateFlow(0)
        every { player.currentMediaItem } returns MediaItem.Builder()
            .setMediaId("local:compact-controls")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Compact title").setArtist("Compact artist").build())
            .build()
        every { player.duration } returns 120_000L
        every { player.playbackState } returns Player.STATE_READY
        every { player.playWhenReady } returns false
    }

    @After fun tearDown() = unmockkAll()

    @Test fun defaultsPlayAndNextUseExistingPlaybackHandlers() {
        renderMini()
        compose.onNodeWithContentDescription(context.getString(R.string.play)).performClick()
        compose.onNodeWithContentDescription(context.getString(MiniPlayerButton.SkipForward.labelRes)).performClick()
        compose.runOnIdle {
            verify(exactly = 1) { binder.gracefulPlay() }
            verify(exactly = 1) { player.seekToNextMediaItem() }
            verify(exactly = 1) { player.prepare() }
            assertEquals(0, playerOpens)
            assertEquals(0, queueOpens)
            assertEquals("home", navigation.currentDestination?.route)
        }
    }

    @Test fun configuredActionsStayIdenticalAcrossFoldAndRemount() {
        context.preferences.edit()
            .putBoolean(showMiniPlayerSkipBackKey, false)
            .putBoolean(showMiniPlayerSkipForwardKey, true).commit()
        renderMini(dockProvider = true)
        val next = context.getString(MiniPlayerButton.SkipForward.labelRes)
        val previous = context.getString(MiniPlayerButton.SkipBack.labelRes)
        compose.onAllNodesWithContentDescription(next).assertCountEquals(1)
        compose.onNodeWithContentDescription(previous).assertDoesNotExist()
        compose.runOnIdle { progress = 0f }
        compose.onAllNodesWithContentDescription(next).assertCountEquals(1)
        compose.onNodeWithContentDescription(previous).assertDoesNotExist()
        compose.onNodeWithContentDescription(next).performClick()
        compose.runOnIdle {
            verify(exactly = 1) { player.seekToNextMediaItem() }
            generation++
            progress = 1f
        }
        compose.onAllNodesWithContentDescription(next).assertCountEquals(1)
        compose.onNodeWithContentDescription(previous).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(false, context.preferences.getBoolean(showMiniPlayerSkipBackKey, true))
            assertEquals(true, context.preferences.getBoolean(showMiniPlayerSkipForwardKey, false))
        }
    }

    @Test fun compactQueueButtonAndHoldRequestSameOverlayWithoutOpeningPlayer() {
        setQueueSlots()
        renderMini()
        compose.onNode(hasClickAction() and !body and SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription)).performClick()
        // With four controls the mini's center belongs to a button. Hold the artwork body.
        compose.onNode(body).performTouchInput { longClick(Offset(width * .05f, center.y)) }
        compose.runOnIdle {
            assertEquals(2, queueOpens)
            assertEquals(0, playerOpens)
            assertEquals("home", navigation.currentDestination?.route)
        }
    }

    @Test fun compactEmbeddedQueueDoesNotOpenDuplicateQueue() {
        setQueueSlots()
        renderMini(embedded = true)
        compose.onNode(hasClickAction() and !body and SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription)).performClick()
        // With four controls the mini's center belongs to a button. Hold the artwork body.
        compose.onNode(body).performTouchInput { longClick(Offset(width * .05f, center.y)) }
        compose.runOnIdle {
            assertEquals(0, queueOpens)
            assertEquals(0, playerOpens)
            assertEquals("home", navigation.currentDestination?.route)
        }
    }

    @Test fun dockProviderKeepsAllFourConfiguredActionsThroughoutFold() {
        progress = 0f
        context.preferences.edit()
            .putBoolean(showMiniPlayerPlayPauseKey, true)
            .putBoolean(showMiniPlayerSkipForwardKey, true)
            .putBoolean(showMiniPlayerQueueKey, true)
            .putBoolean(showMiniPlayerAudioOutputKey, false).commit()
        renderMini(dockProvider = true)
        val play = context.getString(R.string.play)
        val next = context.getString(MiniPlayerButton.SkipForward.labelRes)
        val previous = context.getString(MiniPlayerButton.SkipBack.labelRes)
        val actions = hasClickAction() and !body
        val queue = actions and SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription)

        fun assertConfiguredActions() {
            compose.onAllNodes(actions).assertCountEquals(4)
            compose.onAllNodesWithContentDescription(play).assertCountEquals(1)
            compose.onAllNodesWithContentDescription(next).assertCountEquals(1)
            compose.onAllNodesWithContentDescription(previous).assertCountEquals(1)
            compose.onAllNodes(queue).assertCountEquals(1)
        }
        assertConfiguredActions()
        compose.onNodeWithContentDescription(previous).performClick()
        compose.runOnIdle { progress = .5f }
        assertConfiguredActions()
        compose.runOnIdle { progress = 1f }
        assertConfiguredActions()
        compose.onNodeWithContentDescription(play).performClick()
        compose.onNodeWithContentDescription(next).performClick()
        compose.runOnIdle { progress = 0f }
        assertConfiguredActions()
        compose.onNode(queue).performClick()
        compose.runOnIdle {
            verify(exactly = 1) { player.seekToPreviousMediaItem() }
            verify(exactly = 1) { binder.gracefulPlay() }
            verify(exactly = 1) { player.seekToNextMediaItem() }
            assertEquals(1, queueOpens)
            assertEquals(0, playerOpens)
            assertEquals("home", navigation.currentDestination?.route)
        }
    }

    private fun setQueueSlots() {
        context.preferences.edit()
            .putBoolean(showMiniPlayerQueueKey, true)
            .putBoolean(showMiniPlayerAudioOutputKey, false).commit()
    }

    private fun renderMini(embedded: Boolean = false, dockProvider: Boolean = false) {
        val sheet = mockk<PlayerSheetState> { every { progress } returns 0f }
        val pending = mutableStateOf<PendingMiniPlayerAction?>(null)
        compose.setContent {
            navigation = rememberNavController()
            val shape = RoundedCornerShape(12.dp)
            val palette = DefaultDarkColorPalette
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = palette,
                    typography = typographyOf(palette.text, true, false, FontType.Rubik),
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
                // Keep the fallback fixed in this regression: only the production provider drives the fold.
                LocalCompactMiniPlayerProgress provides if (dockProvider) 0f else progress,
                LocalCompactMiniPlayerProgressProvider provides if (dockProvider) ({ progress }) else null,
                LocalPlayerServiceBinder provides binder,
                LocalPlayerSheetState provides sheet,
                LocalPendingMiniPlayerAction provides pending,
                LocalMiniPlayerQueueRequest provides { queueOpens++ },
            ) {
                NavHost(navigation, startDestination = "home") {
                    composable("home") {
                        key(generation) {
                            Box(Modifier.fillMaxWidth()) {
                                if (embedded) QueueEmbeddedMini {
                                    MiniPlayer({ playerOpens++ }, {}, navigation)
                                } else MiniPlayer({ playerOpens++ }, {}, navigation)
                            }
                        }
                    }
                    composable("queue") { Text("Legacy queue destination") }
                }
            }
        }
    }
}
