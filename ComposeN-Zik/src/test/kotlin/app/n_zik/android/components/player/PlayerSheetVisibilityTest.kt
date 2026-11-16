package app.n_zik.android.components.player

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerSheetVisibilityTest {

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
