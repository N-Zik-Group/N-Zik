package app.n_zik.android.components.glass

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassPlayerOverlayEffectsTest {
    @Test fun `only menus and queue over full player use lighter blur`() {
        for (player in listOf(false, true)) {
            for (menu in listOf(false, true)) {
                for (queue in listOf(false, true)) {
                    val budget = playerOverlayBlurBudget(8f, .3f, player, menu, queue)
                    val reduced = player && (menu || queue)
                    assertEquals(if (reduced) 6f else 8f, budget.radiusDp, .0001f)
                    assertEquals(if (reduced) .25f else .3f, budget.resolution, .0001f)
                }
            }
        }
    }

    @Test fun `clear panes and already cheaper resolutions retain their quality`() {
        assertEquals(GlassOverlayBlurBudget(0f, 1f),
            playerOverlayBlurBudget(0f, 1f, playerActive = true, menu = true, queue = false))
        assertEquals(GlassOverlayBlurBudget(6f, .1f),
            playerOverlayBlurBudget(8f, .1f, playerActive = true, menu = false, queue = true))
    }
}
