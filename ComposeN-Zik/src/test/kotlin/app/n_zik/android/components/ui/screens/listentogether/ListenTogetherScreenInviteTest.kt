package app.n_zik.android.components.ui.screens.listentogether

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.checkUpdateStateKey
import app.it.fast4x.rimusic.utils.lastBuildTypeKey
import app.it.fast4x.rimusic.utils.lastVersionCodeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.seenChangelogsVersionKey
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.BuildConfig
import app.n_zik.android.Dependencies
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.listentogether.ConnectionState
import app.n_zik.android.listentogether.INVITE_ROOM_CODE_KEY
import app.n_zik.android.listentogether.ListenTogetherEvent
import app.n_zik.android.listentogether.ListenTogetherManager
import app.n_zik.android.listentogether.RoomRole
import app.n_zik.android.listentogether.RoomState
import app.n_zik.android.updater.models.CheckUpdateState
import app.n_zik.android.updater.models.MajorUpdateConfig
import app.n_zik.android.utils.DataStoreUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w480dp-h1600dp")
class ListenTogetherScreenInviteTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = mockk<ListenTogetherManager>(relaxed = true)
    private val room = MutableStateFlow<RoomState?>(null)
    private val connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val events = MutableSharedFlow<ListenTogetherEvent>(extraBufferCapacity = 8)
    private lateinit var controller: NavHostController

    @Before
    fun setUp() {
        setActiveProfile("default", context)
        context.preferences.edit().clear()
            .putString(checkUpdateStateKey, CheckUpdateState.Disabled.name)
            .putInt(lastVersionCodeKey, BuildConfig.VERSION_CODE)
            .putString(lastBuildTypeKey, MajorUpdateConfig.getCurrentBuildType())
            .putString(seenChangelogsVersionKey, BuildConfig.VERSION_NAME).commit()
        DataStoreUtils.saveString(context, PREF_USERNAME, "")
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        mockkObject(ListenTogetherManager.Companion)
        every { ListenTogetherManager.getInstance() } returns manager
        every { manager.connectionState } returns connection
        every { manager.roomState } returns room
        every { manager.userId } returns MutableStateFlow("guest")
        every { manager.role } returns MutableStateFlow(RoomRole.GUEST)
        every { manager.pendingJoinRequests } returns MutableStateFlow(emptyList())
        every { manager.pendingSuggestions } returns MutableStateFlow(emptyList())
        every { manager.logs } returns MutableStateFlow(emptyList())
        every { manager.blockedUsernames } returns MutableStateFlow(emptySet())
        every { manager.syncHostVolumeEnabled } returns MutableStateFlow(true)
        every { manager.events } returns events
        every { manager.disconnect() } answers {
            connection.value = ConnectionState.DISCONNECTED
            events.tryEmit(ListenTogetherEvent.Disconnected)
            Unit
        }
        every { manager.joinRoom(any(), any()) } answers {
            connection.value = ConnectionState.CONNECTING
        }
        every { manager.isInRoom } answers { room.value != null }
        every { manager.leaveRoom() } answers { room.value = null }
    }

    @After
    fun tearDown() {
        unmockkAll()
        context.preferences.edit().clear().commit()
        DataStoreUtils.saveString(context, PREF_USERNAME, "")
    }

    private fun showScreen(username: String = "", activeRoom: String? = null) {
        DataStoreUtils.saveString(context, PREF_USERNAME, username)
        room.value = activeRoom?.let {
            RoomState.newBuilder().setRoomCode(it).setHostId("host").build()
        }
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = Typography.fallback,
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        compose.setContent {
            controller = rememberNavController()
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
            ) {
                NavHost(controller, startDestination = NavRoutes.listenTogether.name) {
                    composable(NavRoutes.listenTogether.name) { ListenTogetherScreen(controller) }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun deliver(code: String = "ABCDEFGH") {
        compose.runOnIdle { controller.currentBackStackEntry?.savedStateHandle?.set(INVITE_ROOM_CODE_KEY, code) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertNull(controller.currentBackStackEntry?.savedStateHandle?.get<String>(INVITE_ROOM_CODE_KEY))
        }
    }

    @Test
    fun blankUsernamePrefillsTheVisibleRoomCodeWithoutJoining() {
        showScreen()
        deliver()
        compose.onNode(hasSetTextAction() and hasText("ABCDEFGH")).assertIsDisplayed()
        verify(exactly = 0) { manager.joinRoom(any(), any()) }
    }

    @Test
    fun savedUsernameJoinsOnceAndCancelsPriorPendingJoinBeforeReplacement() {
        showScreen(username = "Alice")
        deliver()
        verify(exactly = 1) { manager.joinRoom("ABCDEFGH", "Alice") }
        deliver("12345678")
        verifyOrder {
            manager.joinRoom("ABCDEFGH", "Alice")
            manager.disconnect()
            manager.joinRoom("12345678", "Alice")
        }
        compose.onNodeWithText(context.getString(R.string.listen_together_waiting_for_approval)).assertIsDisplayed()
    }

    @Test
    fun sameRoomInvitationDoesNotJoinOrLeave() {
        showScreen(username = "Alice", activeRoom = "ABCDEFGH")
        deliver()
        verify(exactly = 0) { manager.joinRoom(any(), any()) }
        verify(exactly = 0) { manager.leaveRoom() }
    }

    @Test
    fun differentRoomDismissalKeepsCurrentRoomAndConfirmationLeavesBeforeJoin() {
        showScreen(username = "Alice", activeRoom = "12345678")
        deliver()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.waitForIdle()
        verify(exactly = 0) { manager.leaveRoom() }
        verify(exactly = 0) { manager.joinRoom(any(), any()) }
        deliver()
        compose.onNode(hasText(context.getString(R.string.listen_together_leave_room)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        verifyOrder {
            manager.leaveRoom()
            manager.joinRoom("ABCDEFGH", "Alice")
        }
    }

    @Test
    fun confirmationDoesNothingWhenAlreadyInTheInvitedRoom() {
        showScreen(username = "Alice", activeRoom = "12345678")
        deliver()
        compose.runOnIdle {
            room.value = RoomState.newBuilder().setRoomCode("ABCDEFGH").setHostId("host").build()
        }
        compose.waitForIdle()
        compose.onNode(hasText(context.getString(R.string.listen_together_leave_room)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        verify(exactly = 0) { manager.leaveRoom() }
        verify(exactly = 0) { manager.joinRoom(any(), any()) }
    }
}
