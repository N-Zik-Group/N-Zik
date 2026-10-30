package app.n_zik.android.components.pullrefresh

import app.n_zik.android.components.player.miniPlayerTopDockPaddingPx
import app.n_zik.android.components.player.miniPlayerTopPaddingPx
import org.junit.Assert.assertEquals
import org.junit.Test

class PullRefreshOverlayInsetTest {
    @Test fun bottomMiniOnlyClearsTheVisibleHeaderAndTopNavigation() {
        for (headerBottom in listOf(88f, 56f, 24f)) {
            assertEquals(headerBottom, pullRefreshOverlayInsetPx(headerBottom, 0f), 0f)
            assertEquals(headerBottom + 64f, pullRefreshOverlayInsetPx(headerBottom, 64f), 0f)
        }
    }

    @Test fun standaloneTopMiniRemainsProtectedWhenHeaderIsHiddenAndMiniIsCompact() {
        for (offset in listOf(0f, -32f, -64f)) for (fold in listOf(0f, .5f, 1f)) {
            val height = 72f + (48f - 72f) * fold
            val miniTop = miniPlayerTopPaddingPx(96f, offset, 64f, 72f, retainedTopInsetPx = 32f)
            val inset = pullRefreshOverlayInsetPx(88f + offset, 0f, miniTop, height)
            assertEquals(miniTop + height, inset, 0f)
        }
        assertEquals(80f, pullRefreshOverlayInsetPx(24f, 0f, 32f, 48f), 0f)
    }

    @Test fun attachedTopMiniClearsItsActualBottomThroughoutFoldAndHeaderTravel() {
        for (nav in listOf(59f, 64f)) for (offset in listOf(0f, -32f, -64f)) {
            for (fold in listOf(0f, .5f, 1f)) {
                val height = 72f + (48f - 72f) * fold
                val miniTop = miniPlayerTopDockPaddingPx(96f + nav, offset, 64f, nav, height, 8f, fold)
                val navBottom = 88f + offset + nav
                assertEquals(maxOf(navBottom, miniTop + height),
                    pullRefreshOverlayInsetPx(88f + offset, nav, miniTop, height), 0f)
            }
        }
    }
}
