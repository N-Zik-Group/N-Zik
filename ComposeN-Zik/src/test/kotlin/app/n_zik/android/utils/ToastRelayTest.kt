package app.n_zik.android.utils

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Decision 7 of `spec-pc-ui-parity-audit-fixes`: every legacy `Toaster` toast is relayed, once. */
class ToastRelayTest {

    private val received = mutableListOf<ToastRelay.Relayed>()
    private val listener = ToastRelay.Listener { received += it }

    @AfterEach
    fun tearDown() = ToastRelay.removeListener(listener)

    @Test
    fun `a plain-string toast is relayed as text with its type`() {
        ToastRelay.addListener(listener)
        ToastRelay.onLegacyToast("Copied", "SUCCESS")
        assertEquals(listOf(ToastRelay.Relayed("", emptyList(), ToastRelay.Kind.SUCCESS, "Copied")), received)
    }

    @Test
    fun `a resource toast carries its key and arguments, an unknown type is normal`() {
        val relayed = ToastRelay.relayedOf("12 songs shuffled", "WHATEVER", "songs_shuffled" to listOf("12"))
        assertEquals(ToastRelay.Relayed("songs_shuffled", listOf("12"), ToastRelay.Kind.NORMAL, "12 songs shuffled"), relayed)
    }

    @Test
    fun `each toast is relayed exactly once and nothing without a listener`() {
        ToastRelay.onLegacyToast("ignored", "INFO")
        assertTrue(received.isEmpty())
        ToastRelay.addListener(listener)
        ToastRelay.onLegacyToast("one", "INFO")
        assertEquals(1, received.size)
    }
}
