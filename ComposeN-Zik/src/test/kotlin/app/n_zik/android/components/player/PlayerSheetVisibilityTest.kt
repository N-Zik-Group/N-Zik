package app.n_zik.android.components.player

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerSheetVisibilityTest {

    @Test
    fun `unbinding the activity preserves the sheet and reconnecting finds the track`() = runTest {
        var presence: Boolean? = true
        assertEquals(true, resolvePlayerSheetMediaPresence { presence })

        presence = null
        assertNull(resolvePlayerSheetMediaPresence { presence })

        presence = true
        assertEquals(true, resolvePlayerSheetMediaPresence { presence })
    }

    @Test
    fun `a pending empty queue check cannot dismiss the sheet after unbinding`() = runTest {
        var presence: Boolean? = false
        val result = async { resolvePlayerSheetMediaPresence { presence } }
        testScheduler.runCurrent()
        assertFalse(result.isCompleted)

        presence = null
        assertNull(result.await())
    }

    @Test
    fun `an empty queue must persist before the sheet closes`() = runTest {
        var presence = false
        val result = async { resolvePlayerSheetMediaPresence { presence } }
        testScheduler.runCurrent()
        assertFalse(result.isCompleted)

        presence = true
        assertEquals(true, result.await())
        assertEquals(false, resolvePlayerSheetMediaPresence { false })
    }

    @Test
    fun `the sheet is composed while media is present`() {
        assertTrue(shouldComposePlayerSheet(mediaPresent = true, isDismissed = false))
        assertTrue(shouldComposePlayerSheet(mediaPresent = true, isDismissed = true))
    }

    @Test
    fun `the sheet stays composed until the dismiss animation reaches the dismissed bound`() {
        assertTrue(shouldComposePlayerSheet(mediaPresent = false, isDismissed = false))
    }

    @Test
    fun `the sheet is removed once dismissed without media`() {
        assertFalse(shouldComposePlayerSheet(mediaPresent = false, isDismissed = true))
    }
}
