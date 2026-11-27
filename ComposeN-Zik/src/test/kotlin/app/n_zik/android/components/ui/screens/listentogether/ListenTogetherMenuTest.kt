package app.n_zik.android.components.ui.screens.listentogether

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.settings.ServerChooserSheet
import app.n_zik.android.components.settings.UsernameSheet
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.listentogether.ConnectionState
import app.n_zik.android.listentogether.ListenTogetherEvent
import app.n_zik.android.listentogether.ListenTogetherManager
import app.n_zik.android.listentogether.RoomRole
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class ListenTogetherMenuTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = MutableSharedFlow<ListenTogetherEvent>(extraBufferCapacity = 1)

    @Before
    fun setUp() {
        val manager = mockk<ListenTogetherManager>()
        every { manager.connectionState } returns MutableStateFlow(ConnectionState.CONNECTED)
        every { manager.roomState } returns MutableStateFlow(null)
        every { manager.userId } returns MutableStateFlow("listener")
        every { manager.role } returns MutableStateFlow(RoomRole.NONE)
        every { manager.pendingJoinRequests } returns MutableStateFlow(emptyList())
        every { manager.pendingSuggestions } returns MutableStateFlow(emptyList())
        every { manager.logs } returns MutableStateFlow(emptyList())
        every { manager.blockedUsernames } returns MutableStateFlow(emptySet())
        every { manager.syncHostVolumeEnabled } returns MutableStateFlow(true)
        every { manager.isInRoom } returns false
        every { manager.events } returns events
        mockkObject(ListenTogetherManager.Companion, Toaster)
        every { ListenTogetherManager.getInstance() } returns manager
        justRun { Toaster.e(any<String>(), any<Int>()) }
    }

    @After
    fun tearDown() {
        unmockkObject(ListenTogetherManager.Companion, Toaster)
    }

    @Test
    fun `join rejection is still received while logs are open`() {
        showRoomMenu()
        val activity = composeRule.activity
        composeRule.onNodeWithText(activity.getString(R.string.listen_together_view_logs))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(activity.getString(R.string.listen_together_logs)).assertExists()

        composeRule.runOnIdle {
            check(events.tryEmit(ListenTogetherEvent.JoinRejected("Rejected by host")))
        }
        composeRule.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithText(activity.getString(R.string.listen_together_join_request_denied)).assertExists()
    }

    @Test
    fun `management pages have a button that returns to the room menu`() {
        showRoomMenu()
        val activity = composeRule.activity
        val back = activity.getString(R.string.common_action_back)

        composeRule.onNodeWithText(activity.getString(R.string.listen_together_view_logs))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithContentDescription(back).performClick()

        composeRule.onNodeWithText(activity.getString(R.string.listen_together_blocked_users))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithContentDescription(back).performClick()
        composeRule.onNodeWithText(activity.getString(R.string.listen_together_view_logs)).assertExists()
    }

    @Test
    fun `server chooser shows Back only when opened over the room menu`() {
        var hasParentMenu by mutableStateOf(false)
        var dismissals = 0
        showWithAppearance {
            ServerChooserSheet(
                showSheet = true,
                onSaved = {},
                onDismiss = { dismissals++ },
                currentUrl = "",
                hasParentMenu = hasParentMenu,
            )
        }

        val back = composeRule.activity.getString(R.string.common_action_back)
        composeRule.onNodeWithContentDescription(back).assertDoesNotExist()
        composeRule.runOnIdle { hasParentMenu = true }
        composeRule.onNodeWithContentDescription(back).performClick()
        composeRule.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test
    fun `username sheet retains draft when the user joins a room before saving`() {
        var hasParentMenu by mutableStateOf(false)
        var inRoom = false
        var saves = 0
        var dismissals = 0
        justRun { Toaster.i(R.string.listen_together_cannot_edit_username_in_room, any<Int>()) }
        showWithAppearance {
            UsernameSheet(
                showSheet = true,
                onSaved = { saves++ },
                onDismiss = { dismissals++ },
                savedUsername = "Listener",
                canEditUsername = { !inRoom },
                hasParentMenu = hasParentMenu,
            )
        }

        val activity = composeRule.activity
        val back = activity.getString(R.string.common_action_back)
        composeRule.onNodeWithContentDescription(back).assertDoesNotExist()
        val username = composeRule.onNode(hasSetTextAction())
        username.performTextReplacement("Draft Listener")
        composeRule.runOnIdle {
            inRoom = true
            hasParentMenu = true
        }
        composeRule.onNodeWithText(activity.getString(android.R.string.ok)).performScrollTo().performClick()
        username.assertTextEquals("Draft Listener")
        composeRule.runOnIdle {
            assertEquals(0, saves)
            assertEquals(0, dismissals)
        }
        verify(exactly = 1) { Toaster.i(R.string.listen_together_cannot_edit_username_in_room, any<Int>()) }
        composeRule.onNodeWithContentDescription(back).performClick()
        composeRule.runOnIdle { assertEquals(1, dismissals) }
    }

    private fun showRoomMenu() {
        val menuState = MenuState().apply { display {} }
        showWithAppearance {
            CompositionLocalProvider(LocalMenuState provides menuState) { ListenTogetherMenu() }
        }
    }

    private fun showWithAppearance(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(8.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, content = content)
        }
    }
}
