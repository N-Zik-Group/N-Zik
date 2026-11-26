package app.n_zik.android.components.ui.screens.listentogether

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
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
        val menuState = MenuState().apply { display {} }
        val shape = RoundedCornerShape(8.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)
        composeRule.setContent {
            CompositionLocalProvider(LocalMenuState provides menuState, LocalAppearance provides appearance) {
                ListenTogetherMenu()
            }
        }

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
}
