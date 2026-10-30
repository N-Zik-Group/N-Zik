package app.n_zik.android.components.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TopMiniPlayerStackReserveTest {
    @Test fun `attached top mini reserve folds into the fixed row and reverses without a gap`() {
        for (navHeight in listOf(59f, 64f)) {
            for (offset in listOf(0f, -32f, -64f)) {
                for (progress in listOf(0f, .5f, 1f, .5f, 0f)) {
                    assertEquals(81f * (1f - progress), topMiniPlayerStackReservePx(
                        81f, 81f, navHeight, navHeight, offset, 64f,
                        keepVisible = true, attachedMiniProgress = progress,
                    ))
                }
            }
        }
        assertEquals(0f, topMiniPlayerStackReservePx(
            0f, 81f, 64f, 64f, keepVisible = true, attachedMiniProgress = 1f,
        ))
        // A caller that has not reserved the row must still retain that row at full fold.
        assertEquals(64f, topMiniPlayerStackReservePx(
            81f, 81f, 64f, 0f, keepVisible = true, attachedMiniProgress = 1f,
        ))
    }
    @Test fun `a measured top navigation bar is never reserved twice`() {
        assertEquals(81f, topMiniPlayerStackReservePx(81f, 81f, 59f, 59f))
        assertEquals(81f, topMiniPlayerStackReservePx(81f, 81f, 59f, 64f))
        assertEquals(81f, topMiniPlayerStackReservePx(81f, 81f, 64f, 64f))
    }

    @Test fun `a partially reserved visible navigation anchor adds only its missing height`() {
        assertEquals(140f, topMiniPlayerStackReservePx(81f, 81f, 59f, 0f))
        assertEquals(145f, topMiniPlayerStackReservePx(81f, 81f, 64f, 0f))
        assertEquals(120f, topMiniPlayerStackReservePx(81f, 81f, 59f, 20f))
    }

    @Test fun `top mini under visible tabs keeps its reserve throughout header scroll`() {
        for (navHeight in listOf(59f, 64f)) {
            for (offset in listOf(0f, -32f, -64f)) {
                assertEquals(81f, topMiniPlayerStackReservePx(
                    81f, 81f, navHeight, navHeight, offset, 64f, keepVisible = true,
                ))
            }
        }
    }

    @Test fun `without visible top tabs mini reserve still leaves with the header`() {
        assertEquals(40.5f, topMiniPlayerStackReservePx(81f, 81f, 0f, 0f, -32f, 64f))
        assertEquals(0f, topMiniPlayerStackReservePx(81f, 81f, 0f, 0f, -64f, 64f))
        assertEquals(0f, topMiniPlayerStackReservePx(0f, 81f, 64f, 64f, -64f, 64f, keepVisible = true))
    }

    @Test fun `missing navigation space follows mini appearance and disappears without media`() {
        assertEquals(70f, topMiniPlayerStackReservePx(40.5f, 81f, 59f, 0f))
        assertEquals(0f, topMiniPlayerStackReservePx(0f, 81f, 59f, 0f))
        assertEquals(81f, topMiniPlayerStackReservePx(81f, 81f, 0f, 0f))
    }
}
