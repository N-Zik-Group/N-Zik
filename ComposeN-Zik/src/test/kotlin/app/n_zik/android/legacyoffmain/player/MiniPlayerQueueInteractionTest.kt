package app.n_zik.android.legacyoffmain.player

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
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
import app.n_zik.android.components.glass.QueueEmbeddedMini
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.core.navigation.LocalMiniPlayerQueueRequest
import app.n_zik.android.enums.PendingMiniPlayerAction
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.utils.appNavBarPresentForRoute
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the production mini-player controls with a real navigation back stack. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MiniPlayerQueueInteractionTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var navigation: NavHostController
    private var queueShown by mutableStateOf(false)
    private var queueOpenCount = 0
    private var fullPlayerOpenCount = 0
    private val player = mockk<Player>(relaxed = true)
    private val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
    private val body = SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        setActiveProfile("default", context)
        // Queue is the only enabled slot: its anonymous legacy icon remains selectable
        // without introducing test-only semantics into production controls.
        context.preferences.edit().clear()
            .putBoolean(glassEnabledKey, false)
            .putBoolean(disableScrollingTextKey, true)
            .putBoolean(showMiniPlayerPlayPauseKey, false)
            .putBoolean(showMiniPlayerSkipBackKey, false)
            .putBoolean(showMiniPlayerSkipForwardKey, false)
            .putBoolean(showMiniPlayerAudioOutputKey, false)
            .putBoolean(showMiniPlayerQueueKey, true).commit()
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
            .setMediaId("local:queue-interaction")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Queue interaction").setArtist("Artist").build())
            .build()
        every { player.duration } returns 120_000L
    }

    @After fun tearDown() {
        unmockkAll()
    }

    @Test fun queueButtonAndHoldOpenOverlayWithoutChangingHomeOrOpeningPlayer() {
        renderMini(embedded = false)
        val homeEntry = homeEntryId()
        queueButton().performClick()
        assertHomeAndOverlay(homeEntry, expectedOpenCount = 1, expectedShown = true)
        compose.runOnIdle { queueShown = false }
        compose.onNode(body).performTouchInput { longClick(center) }
        assertHomeAndOverlay(homeEntry, expectedOpenCount = 2, expectedShown = true)
        compose.runOnIdle { queueShown = false }
        assertHomeAndOverlay(homeEntry, expectedOpenCount = 2, expectedShown = false)
    }

    @Test fun embeddedMiniQueueButtonAndHoldDoNotReopenQueue() {
        queueShown = true
        renderMini(embedded = true)
        val homeEntry = homeEntryId()
        queueButton().performClick()
        assertHomeAndOverlay(homeEntry, expectedOpenCount = 0, expectedShown = true)
        compose.onNode(body).performTouchInput { longClick(center) }
        assertHomeAndOverlay(homeEntry, expectedOpenCount = 0, expectedShown = true)
    }

    private fun queueButton() = compose.onNode(hasClickAction() and !body)

    private fun renderMini(embedded: Boolean) {
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
                LocalPlayerServiceBinder provides binder,
                LocalPlayerSheetState provides sheet,
                LocalPendingMiniPlayerAction provides pending,
                LocalMiniPlayerQueueRequest provides {
                    queueOpenCount++
                    queueShown = true
                },
            ) {
                NavHost(navigation, startDestination = "home") {
                    composable("home") {
                        Box(Modifier.fillMaxWidth()) {
                            if (embedded) QueueEmbeddedMini {
                                MiniPlayer({ fullPlayerOpenCount++ }, {}, navigation)
                            } else MiniPlayer({ fullPlayerOpenCount++ }, {}, navigation)
                        }
                    }
                    composable("queue") { Text("Legacy queue destination") }
                }
            }
        }
    }

    private fun homeEntryId(): String {
        var entryId = ""
        compose.runOnIdle {
            assertEquals("home", navigation.currentDestination?.route)
            entryId = requireNotNull(navigation.currentBackStackEntry).id
        }
        assertFalse(entryId.isBlank())
        return entryId
    }

    private fun assertHomeAndOverlay(entryId: String, expectedOpenCount: Int, expectedShown: Boolean) {
        compose.runOnIdle {
            assertEquals(expectedOpenCount, queueOpenCount)
            assertEquals(expectedShown, queueShown)
            assertEquals(0, fullPlayerOpenCount)
            assertEquals("home", navigation.currentDestination?.route)
            assertEquals(entryId, navigation.currentBackStackEntry?.id)
            assertTrue(appNavBarPresentForRoute(navigation.currentDestination?.route, activeHomeTabCount = 4))
        }
    }
}
