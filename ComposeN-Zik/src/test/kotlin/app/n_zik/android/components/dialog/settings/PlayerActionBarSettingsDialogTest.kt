package app.n_zik.android.components.dialog.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerActionBarSettingsDialogTest {
    @Test
    fun `occupied actions are not offered in other slots until cleared`() {
        val default = listOf("collapsePlayer", "audioVideo", "menu")
        val withShuffle = assignPlayerTopSlot(default, 0, "shuffle")
        assertEquals(listOf("shuffle", "audioVideo", "menu"), withShuffle)
        assertTrue(canAssignPlayerTopSlot(withShuffle, 0, "shuffle"))
        assertFalse(canAssignPlayerTopSlot(withShuffle, 2, "shuffle"))
        assertEquals(withShuffle, assignPlayerTopSlot(withShuffle, 2, "shuffle"))
        val cleared = assignPlayerTopSlot(withShuffle, 0, "none")
        assertTrue(canAssignPlayerTopSlot(cleared, 2, "shuffle"))
        assertEquals(listOf("none", "audioVideo", "shuffle"), assignPlayerTopSlot(cleared, 2, "shuffle"))
    }

    @Test
    fun `two slots may be left empty`() {
        val slots = listOf("none", "audioVideo", "menu")
        assertEquals(listOf("none", "none", "menu"), assignPlayerTopSlot(slots, 1, "none"))
    }
}
