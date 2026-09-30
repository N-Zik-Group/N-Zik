package app.n_zik.android.bridge.pairing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PairedDeviceStoreTest {

    private val storage = InMemoryPairedDeviceStorage()
    private val store = JsonPairedDeviceStore(storage, clock = { 1_790_000_000_000L })

    @Test
    fun `issued credential has the contract lengths and authenticates`() {
        val issued = store.issue("PC-SALON")

        assertEquals(43, issued.deviceToken.length)
        assertEquals(11, issued.deviceId.length)
        assertTrue(Regex("^[A-Za-z0-9_-]+$").matches(issued.deviceToken))
        assertEquals(issued.deviceId, store.authenticate(issued.deviceToken))
        assertNull(store.authenticate("unknown-token"))
        assertEquals(listOf("PC-SALON"), store.devices.value.map { it.deviceName })
        assertEquals(1_790_000_000_000L, store.devices.value.single().pairedAtMs)
    }

    @Test
    fun `only the token hash is persisted`() {
        val issued = store.issue("PC-SALON")

        val stored = storage.read().orEmpty()
        assertFalse(stored.contains(issued.deviceToken))
        assertTrue(stored.contains(issued.deviceId))
        assertFalse(issued.toString().contains(issued.deviceToken))
    }

    @Test
    fun `devices survive a reload from the same storage`() {
        val issued = store.issue("PC-SALON")

        val reloaded = JsonPairedDeviceStore(storage)

        assertEquals(issued.deviceId, reloaded.authenticate(issued.deviceToken))
    }

    @Test
    fun `revoking one device leaves the others intact`() {
        val first = store.issue("PC-1")
        val second = store.issue("PC-2")

        assertTrue(store.revoke(first.deviceId))
        assertFalse(store.revoke(first.deviceId))

        assertNull(store.authenticate(first.deviceToken))
        assertEquals(second.deviceId, store.authenticate(second.deviceToken))
        assertEquals(listOf(second.deviceId), store.devices.value.map { it.deviceId })
    }

    @Test
    fun `corrupted storage starts empty`() {
        storage.write("not json")

        assertTrue(JsonPairedDeviceStore(storage).devices.value.isEmpty())
    }
}
