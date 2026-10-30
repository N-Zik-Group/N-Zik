package app.n_zik.android.components

import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
import app.n_zik.android.components.player.miniPlayerSideInset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CardGeometryInsetsTest {

    private val size = Size(width = 1000f, height = 2000f)

    @Test
    fun `mini roundness uses its actual compact or expanded height instead of the fullscreen viewport`() {
        val shape = RoundedCornerShape(BoundedCornerSize(48.dp, .4f))
        val viewport = Size(360f, 800f)
        assertEquals(19.2f, collapsedCardCornerPx(shape, viewport, 48f, 0f,
            startInsetPx = 78f, endInsetPx = 78f, density = Density(1f)), .0001f)
        assertEquals(28.8f, collapsedCardCornerPx(shape, viewport, 72f, 16f,
            density = Density(1f)), .0001f)
    }

    @Test
    fun `zero user roundness stays square in compact and expanded mini presentations`() {
        val shape = RoundedCornerShape(BoundedCornerSize(0.dp, .4f))
        listOf(48f, 72f).forEach { height ->
            assertEquals(0f, collapsedCardCornerPx(shape, Size(360f, 800f), height, 0f,
                startInsetPx = 78f, endInsetPx = 78f, density = Density(1f)))
        }
    }

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
        assertEquals(50.dp, miniPlayerSideInset(railWidth = 50.dp, safeInset = 0.dp))
        assertEquals(74.dp, miniPlayerSideInset(railWidth = 50.dp, safeInset = 24.dp))
    }

    @Test
    fun `without a rail system clearance preserves the usual capsule margin`() {
        assertEquals(48.dp, miniPlayerSideInset(railWidth = 0.dp, safeInset = 48.dp))
        assertEquals(24.dp, miniPlayerSideInset(railWidth = 0.dp, safeInset = 24.dp))
        // Top and Bottom use the same inset; asymmetric landscape bars retain 16dp on both sides.
        for (safe in listOf(0.dp, 8.dp, 24.dp, 48.dp)) {
            assertEquals(safe + 16.dp, miniPlayerSideInset(0.dp, safe) + 16.dp)
        }
    }

    @Test
    fun `no bar and no rail leaves the mini-player untouched`() {
        assertEquals(0.dp, miniPlayerSideInset(railWidth = 0.dp, safeInset = 0.dp))
    }

    @Test
    fun `a thin system bar also preserves the usual margin`() {
        assertEquals(8.dp, miniPlayerSideInset(railWidth = 0.dp, safeInset = 8.dp))
    }
}
