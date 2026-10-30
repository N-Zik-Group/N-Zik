package app.n_zik.android.components.ui.screens.listentogether

import androidx.lifecycle.SavedStateHandle
import app.n_zik.android.listentogether.INVITE_ROOM_CODE_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InviteActionTest {
    @Test
    fun `consumption clears observable state and permits a fresh delivery`() {
        val handle = SavedStateHandle()
        val invitations = handle.getStateFlow<String?>(INVITE_ROOM_CODE_KEY, null)
        handle[INVITE_ROOM_CODE_KEY] = "ABCDEFGH"
        assertEquals("ABCDEFGH", invitations.value)
        assertEquals("ABCDEFGH", consumeInvitation(handle))
        assertNull(invitations.value)
        assertNull(consumeInvitation(handle))
        handle[INVITE_ROOM_CODE_KEY] = "ABCDEFGH"
        assertEquals("ABCDEFGH", consumeInvitation(handle))
    }

    @Test
    fun `saved username joins and missing username prefills`() {
        assertEquals(InviteAction.JOIN, inviteAction("ABCDEFGH", null, "Alice"))
        assertEquals(InviteAction.PREFILL, inviteAction("ABCDEFGH", null, "  "))
    }

    @Test
    fun `same room only opens even without a saved username`() {
        assertEquals(InviteAction.OPEN, inviteAction("ABCDEFGH", "abcdefgh", ""))
    }

    @Test
    fun `different room requires confirmation regardless of saved username`() {
        assertEquals(InviteAction.CONFIRM_SWITCH, inviteAction("ABCDEFGH", "OTHER", "Alice"))
        assertEquals(InviteAction.CONFIRM_SWITCH, inviteAction("ABCDEFGH", "OTHER", ""))
    }
}
