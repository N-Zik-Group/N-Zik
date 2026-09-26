package app.n_zik.android.components.ui.screens.listentogether

import org.junit.Assert.assertEquals
import org.junit.Test

class JoinRejectionMessageTest {

    private val denied = "Join request denied"
    private val invalidCode = "Invalid room code"

    @Test
    fun `null reason maps to the generic denial message`() {
        assertEquals(denied, joinRejectionMessage(null, denied, invalidCode))
    }

    @Test
    fun `blank reason maps to the generic denial message`() {
        assertEquals(denied, joinRejectionMessage("   ", denied, invalidCode))
    }

    @Test
    fun `invalid code reason maps to the invalid code message`() {
        assertEquals(invalidCode, joinRejectionMessage("Invalid room code", denied, invalidCode))
    }

    @Test
    fun `invalid marker is matched case-insensitively`() {
        assertEquals(invalidCode, joinRejectionMessage("room code INVALID or expired", denied, invalidCode))
    }

    @Test
    fun `other reasons are appended to the denial message`() {
        assertEquals(
            "$denied: You are blocked",
            joinRejectionMessage("You are blocked", denied, invalidCode),
        )
    }
}
