package app.n_zik.android.components.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiniPlayerDismissFadeTest {

    private val dismissedBound = 0.dp
    private val collapsedBound = 120.dp

    private fun alpha(value: Dp): Float = miniPlayerDismissAlpha(value, dismissedBound, collapsedBound)

    @Test
    fun `is fully visible at the collapsed bound`() {
        assertEquals(1f, alpha(collapsedBound))
    }

    @Test
    fun `is fully faded at the dismissed bound`() {
        assertEquals(0f, alpha(dismissedBound))
    }

    @Test
    fun `fades linearly across the dismissed zone`() {
        assertEquals(0.25f, alpha(30.dp))
        assertEquals(0.5f, alpha(60.dp))
        assertEquals(0.75f, alpha(90.dp))
    }

    @Test
    fun `stays fully visible above the collapsed bound`() {
        assertEquals(1f, alpha(400.dp))
    }

    @Test
    fun `stays fully faded below the dismissed bound`() {
        assertEquals(0f, alpha(-20.dp))
    }

    @Test
    fun `a degenerate geometry is fully visible without dividing by zero`() {
        assertEquals(1f, miniPlayerDismissAlpha(0.dp, 120.dp, 120.dp))
        assertEquals(1f, miniPlayerDismissAlpha(50.dp, 120.dp, 100.dp))
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
