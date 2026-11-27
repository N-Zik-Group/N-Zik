package app.n_zik.android.components.dialog.export

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ExportPayloadTest {
    @Test
    fun `a recreated selection export cannot call the browsing provider`() {
        val result = resolveExportPayload<List<String>>(selected = true, captured = null) {
            error("A selected export must never fall back to browsing targets")
        }

        assertNull(result)
    }

    @Test
    fun `a selection change during the picker preserves the previously captured targets`() {
        val selectedSongs = listOf("checked")
        val result = resolveExportPayload(selected = true, captured = selectedSongs) {
            listOf("checked", "unchecked")
        }

        assertEquals(selectedSongs, result)
    }

    @Test
    fun `browsing resolves the live collection when a capture is unavailable`() {
        val result = resolveExportPayload(selected = false, captured = null) { listOf("current") }

        assertEquals(listOf("current"), result)
    }

    @Test
    fun `restored browsing waits for the original scope to become ready`() = runTest {
        val songs = mutableStateOf<List<String>?>(null)
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            awaitRestoredExportPayload("original", { "original" }) { songs.value }
        }
        assertFalse(result.isCompleted)

        songs.value = listOf("current")
        Snapshot.sendApplyNotifications()

        assertEquals(listOf("current"), result.await())
    }

    @Test
    fun `restored browsing waits for a playlist whose identity has not loaded`() = runTest {
        val scope = mutableStateOf<String?>(null)
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            awaitRestoredExportPayload("original", { scope.value }) { listOf("current") }
        }
        assertFalse(result.isCompleted)

        scope.value = "original"
        Snapshot.sendApplyNotifications()

        assertEquals(listOf("current"), result.await())
    }

    @Test
    fun `restored browsing never captures a different scope`() = runTest {
        val result = awaitRestoredExportPayload<List<String>>("original", { "different" }) {
            error("A restored export must stay within its original scope")
        }

        assertNull(result)
    }

    @Test
    fun `restored browsing discards a payload when the scope changes during hydration`() = runTest {
        val scope = mutableStateOf("original")
        val songs = mutableStateOf<List<String>?>(null)
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            awaitRestoredExportPayload("original", { scope.value }) { songs.value }
        }
        assertFalse(result.isCompleted)

        Snapshot.withMutableSnapshot {
            scope.value = "different"
            songs.value = listOf("other playlist")
        }

        assertNull(result.await())
    }
}
