package app.n_zik.android.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.player.miniPlayerSideInset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CardGeometryInsetsTest {

    private val size = Size(width = 1000f, height = 2000f)

    private fun geometry(p: Float, startInsetPx: Float = 0f, endInsetPx: Float = 0f) = computeCardGeometry(
        p = p,
        size = size,
        collapsedHeightPx = 200f,
        horizontalPaddingPx = 40f,
        baseCornerPx = 16f,
        corner28Px = 28f,
        startInsetPx = startInsetPx,
        endInsetPx = endInsetPx,
    )

    @Test
    fun `without insets the collapsed card keeps its symmetric margin`() {
        val collapsed = geometry(p = 0f)

        assertEquals(40f, collapsed.left)
        assertEquals(920f, collapsed.width)
    }

    @Test
    fun `a start inset narrows the collapsed card from the left`() {
        val collapsed = geometry(p = 0f, startInsetPx = 100f)

        assertEquals(140f, collapsed.left)
        assertEquals(820f, collapsed.width)
    }

    @Test
    fun `an end inset narrows the collapsed card from the right`() {
        val collapsed = geometry(p = 0f, endInsetPx = 100f)

        assertEquals(40f, collapsed.left)
        assertEquals(820f, collapsed.width)
    }

    @Test
    fun `the card still deploys to the full width whatever the insets`() {
        val expanded = geometry(p = 1f, startInsetPx = 100f, endInsetPx = 60f)

        assertEquals(0f, expanded.left)
        assertEquals(1000f, expanded.width)
    }

    @Test
    fun `the expanded card is full size with square corners whatever the insets`() {
        // The sheet's opaque base (issue #855) full-bleeds this geometry at
        // progress 1, so its height and corner radius must be pinned.
        val expanded = geometry(p = 1f, startInsetPx = 100f, endInsetPx = 60f)

        assertEquals(2000f, expanded.height)
        assertEquals(0f, expanded.cornerPx)
    }

    @Test
    fun `a rail leaves room on its side, counting the usual margin`() {
        // 50dp rail + 8dp gap - 16dp usual margin
        assertEquals(42.dp, miniPlayerSideInset(railWidth = 50.dp, safeInset = 0.dp))
        assertEquals(66.dp, miniPlayerSideInset(railWidth = 50.dp, safeInset = 24.dp))
    }

    @Test
    fun `without a rail the card still clears the side system bar`() {
        // 48dp landscape bar - 16dp usual margin: the card ends flush with the bar's edge
        assertEquals(32.dp, miniPlayerSideInset(railWidth = 0.dp, safeInset = 48.dp))
        // 24dp status bar - 16dp usual margin
        assertEquals(8.dp, miniPlayerSideInset(railWidth = 0.dp, safeInset = 24.dp))
    }

    @Test
    fun `no bar and no rail leaves the mini-player untouched`() {
        assertEquals(0.dp, miniPlayerSideInset(railWidth = 0.dp, safeInset = 0.dp))
    }

    @Test
    fun `a bar thinner than the usual margin adds nothing`() {
        assertEquals(0.dp, miniPlayerSideInset(railWidth = 0.dp, safeInset = 8.dp))
    }
}
