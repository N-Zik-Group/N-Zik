package app.n_zik.android.components.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnimatedM3EBackgroundTest {
    @Test
    fun `visibility target is visible when playing without skip mask`() {
        assertEquals(1f, m3eVisibilityTarget(animating = true, skipMaskActive = false))
    }

    @Test
    fun `visibility target is hidden while skip mask is active`() {
        assertEquals(0f, m3eVisibilityTarget(animating = true, skipMaskActive = true))
    }

    @Test
    fun `visibility target is hidden when paused without skip mask`() {
        assertEquals(0f, m3eVisibilityTarget(animating = false, skipMaskActive = false))
    }

    @Test
    fun `visibility target is hidden when paused with skip mask`() {
        assertEquals(0f, m3eVisibilityTarget(animating = false, skipMaskActive = true))
    }

    @Test
    fun `visibility target is hidden immediately on skip before effect state is active`() {
        assertEquals(0f, m3eVisibilityTarget(animating = true, skipMaskActive = false, immediateSkipHide = true))
    }
}
