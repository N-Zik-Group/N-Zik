package app.n_zik.android.components.player.slide

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlideHelpersTest {

    @Test
    fun `current page has highest z-index`() {
        assertEquals(1f, itemZIndexFor(page = 4, currentPage = 4))
    }

    @Test
    fun `adjacent pages have lower z-index than current page`() {
        assertEquals(0.85f, itemZIndexFor(page = 3, currentPage = 4))
        assertEquals(0.85f, itemZIndexFor(page = 5, currentPage = 4))
    }

    @Test
    fun `distant pages keep decreasing z-index`() {
        assertEquals(0.78f, itemZIndexFor(page = 2, currentPage = 4))
        assertEquals(0.78f, itemZIndexFor(page = 6, currentPage = 4))
        assertEquals(0.73f, itemZIndexFor(page = 1, currentPage = 4))
        assertEquals(0.73f, itemZIndexFor(page = 7, currentPage = 4))
        assertEquals(0.68f, itemZIndexFor(page = 0, currentPage = 4))
        assertEquals(0.68f, itemZIndexFor(page = 8, currentPage = 4))
        assertEquals(0.63f, itemZIndexFor(page = -1, currentPage = 4))
        assertEquals(0.63f, itemZIndexFor(page = 9, currentPage = 4))
    }

    @Test
    fun `pages beyond five use fallback z-index`() {
        assertEquals(0.57f, itemZIndexFor(page = -2, currentPage = 4))
        assertEquals(0.57f, itemZIndexFor(page = 10, currentPage = 4))
    }

    @Test
    fun `settled page change triggers playback only when page and current index differ`() {
        assertTrue(shouldPlaySettledPage(previousPage = 0, settledPage = 1, currentMediaItemIndex = 0))
        assertFalse(shouldPlaySettledPage(previousPage = 1, settledPage = 1, currentMediaItemIndex = 0))
        assertFalse(shouldPlaySettledPage(previousPage = 0, settledPage = 1, currentMediaItemIndex = 1))
    }

    @Test
    fun `thumbnailCoverTransform current page is fully visible`() {
        val (alpha, scaleY, scaleX) = thumbnailCoverTransform(pageOffset = 0f, alphaStart = 0.9f, scaleStart = 0.85f)

        assertEquals(1f, alpha)
        assertEquals(1f, scaleY)
        assertEquals(1f, scaleX)
    }

    @Test
    fun `thumbnailCoverTransform adjacent page keeps separate alpha and scale starts`() {
        val (alpha, scaleY, scaleX) = thumbnailCoverTransform(pageOffset = 1f, alphaStart = 0.9f, scaleStart = 0.85f)

        assertEquals(0.9f, alpha)
        assertEquals(0.85f, scaleY)
        assertEquals(0.85f, scaleX)
    }

    @Test
    fun `thumbnailCoverTransform mid offset interpolates alpha and scale independently`() {
        val (alpha, scaleY, scaleX) = thumbnailCoverTransform(pageOffset = 0.5f, alphaStart = 0f, scaleStart = 0f)

        assertEquals(0.5f, alpha)
        assertEquals(0.5f, scaleY)
        assertEquals(0.5f, scaleX)
    }
}
