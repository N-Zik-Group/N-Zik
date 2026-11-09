package app.n_zik.android.components.ui.screens.listentogether

import org.junit.Assert.assertEquals
import org.junit.Test

class JoinRejectionMessageTest {

    private val denied = "Join request denied"
    private val invalidCode = "Invalid room code"
    private val blocked = "Blocked by host"

    private fun message(reason: String?) =
        joinRejectionMessage(reason, denied, invalidCode, blocked) { "$denied: $it" }

    @Test
    fun `null reason maps to the generic denial message`() {
        assertEquals(denied, message(null))
    }

    @Test
    fun `blank reason maps to the generic denial message`() {
        assertEquals(denied, message("   "))
    }

    @Test
    fun `invalid code reason maps to the invalid code message`() {
        assertEquals(invalidCode, message("Invalid room code"))
    }

    @Test
    fun `invalid marker is matched case-insensitively`() {
        assertEquals(invalidCode, message("room code INVALID or expired"))
    }

    @Test
    fun `legacy host reasons are localized and custom reasons are preserved`() {
        assertEquals(blocked, message("You are blocked"))
        assertEquals(blocked, message("Blocked by host"))
        assertEquals(denied, message("Rejected by host"))
        assertEquals("$denied: Custom reason", message("Custom reason"))
    }
}
