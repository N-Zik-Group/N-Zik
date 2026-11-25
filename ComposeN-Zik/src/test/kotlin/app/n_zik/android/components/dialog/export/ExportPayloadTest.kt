package app.n_zik.android.components.dialog.export

import org.junit.Assert.assertEquals
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
}
