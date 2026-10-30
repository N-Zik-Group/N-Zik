package app.n_zik.android.components.player

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MiniPlayerTopAnchorTest {
    @Test
    fun `landscape mini stays below status area when toolbar scrolls with bars already hidden`() {
        for (hasTopNav in listOf(false, true)) {
            val safeTop = miniPlayerTopInset(24.dp, barsHidden = true, hasTopNavBar = hasTopNav).value
            for (miniHeight in listOf(72f, 48f)) {
                for (scroll in listOf(0f, -32f, -64f, -500f)) {
                    assertEquals(safeTop, miniPlayerTopPaddingPx(
                        insetPx = safeTop,
                        scrollOffsetPx = scroll,
                        hideRangePx = 64f,
                        collapsedHeightPx = miniHeight,
                        retainedTopInsetPx = safeTop,
                        barsHidden = true,
                    ))
                }
            }
        }
    }

    @Test
    fun `side rail and top mini start together below the Android safe inset`() {
        assertEquals(24.dp, miniPlayerTopInset(24.dp, barsHidden = false,
            hasTopNavBar = false, headerHeight = 0.dp, gap = 0.dp))
    }

    @Test
    fun `top mini joins Home inside the native or Glass row after the header hides`() {
        for (navHeight in listOf(59f, 64f)) {
            val expanded = 24f + 64f + navHeight + 8f
            assertEquals(expanded, miniPlayerTopDockPaddingPx(expanded, 0f, 64f, navHeight, 72f, 8f, 0f))
            assertEquals(24f + (navHeight - 48f) / 2f,
                miniPlayerTopDockPaddingPx(expanded, -64f, 64f, navHeight, 48f, 8f, 1f))
            assertEquals(expanded - 32f - (navHeight + 8f - (navHeight - 60f) / 2f) / 2f,
                miniPlayerTopDockPaddingPx(expanded, -32f, 64f, navHeight, 60f, 8f, .5f))
            // Scroll and compression are independent: reversing either never moves the mini above its row.
            assertEquals(24f + 64f + (navHeight - 48f) / 2f,
                miniPlayerTopDockPaddingPx(expanded, 0f, 64f, navHeight, 48f, 8f, 1f))
        }
    }

    @Test
    fun `top mini remains visible with or without Top tabs as only the header leaves`() {
        for (navHeight in listOf(0f, 59f, 64f)) {
            val retainedAnchor = 24f + navHeight + 8f
            val expandedAnchor = retainedAnchor + 64f
            for (miniHeight in listOf(72f, 48f)) {
                for (progress in listOf(0f, 0.5f, 1f)) {
                    assertEquals(
                        expandedAnchor - 64f * progress,
                        miniPlayerTopPaddingPx(
                            insetPx = expandedAnchor,
                            scrollOffsetPx = -64f * progress,
                            hideRangePx = 64f,
                            collapsedHeightPx = miniHeight,
                            retainedTopInsetPx = retainedAnchor,
                        ),
                    )
                }
                assertEquals(retainedAnchor, miniPlayerTopPaddingPx(
                    expandedAnchor, -500f, 64f, miniHeight, retainedAnchor,
                ))
            }
        }
    }

    @Test
    fun `top mini anchor uses the actual native or Glass navigation height`() {
        assertEquals(155.dp, miniPlayerTopInset(24.dp, barsHidden = false,
            hasTopNavBar = true, topNavBarHeight = 59.dp))
        assertEquals(160.dp, miniPlayerTopInset(24.dp, barsHidden = false,
            hasTopNavBar = true, topNavBarHeight = 64.dp))
    }

    private val statusBar = 24.dp
    private val header = 64.dp
    private val topNavBar = 59.dp
    private val gap = 8.dp

    private fun inset(barsHidden: Boolean, hasTopNavBar: Boolean) = miniPlayerTopInset(
        statusBarTop = statusBar,
        barsHidden = barsHidden,
        hasTopNavBar = hasTopNavBar,
        headerHeight = header,
        topNavBarHeight = topNavBar,
        gap = gap,
    )

    @Test
    fun `sits below the app header with a small gap`() {
        assertEquals(statusBar + header + gap, inset(barsHidden = false, hasTopNavBar = false))
    }

    @Test
    fun `sits below the top navigation bar when there is one`() {
        assertEquals(statusBar + header + topNavBar + gap, inset(barsHidden = false, hasTopNavBar = true))
    }

    @Test
    fun `only clears the status bar while the bars are put away`() {
        assertEquals(statusBar, inset(barsHidden = true, hasTopNavBar = true))
    }

    @Test
    fun `stays at its inset while the header is shown`() {
        assertEquals(
            288f,
            miniPlayerTopPaddingPx(insetPx = 288f, scrollOffsetPx = 0f, hideRangePx = 120f, collapsedHeightPx = 200f),
        )
    }

    @Test
    fun `leaves with the header, fully above the screen once it is gone`() {
        assertEquals(
            -200f,
            miniPlayerTopPaddingPx(insetPx = 288f, scrollOffsetPx = -120f, hideRangePx = 120f, collapsedHeightPx = 200f),
        )
    }

    @Test
    fun `moves proportionally while the header slides out`() {
        assertEquals(
            44f,
            miniPlayerTopPaddingPx(insetPx = 288f, scrollOffsetPx = -60f, hideRangePx = 120f, collapsedHeightPx = 200f),
        )
    }

    @Test
    fun `an offset beyond the header travel does not push it further`() {
        assertEquals(
            -200f,
            miniPlayerTopPaddingPx(insetPx = 288f, scrollOffsetPx = -500f, hideRangePx = 120f, collapsedHeightPx = 200f),
        )
    }

    @Test
    fun `without a hide range it stays at its inset`() {
        assertEquals(
            288f,
            miniPlayerTopPaddingPx(insetPx = 288f, scrollOffsetPx = -60f, hideRangePx = 0f, collapsedHeightPx = 200f),
        )
    }
}
