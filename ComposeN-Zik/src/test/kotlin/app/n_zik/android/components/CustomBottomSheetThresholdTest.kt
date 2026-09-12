package app.n_zik.android.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CustomBottomSheetThresholdTest {

    @Test
    fun `playerContentFade is zero before the hand-over threshold`() {
        assertEquals(0f, playerContentFade(0f))
    }

    @Test
    fun `playerContentFade starts exactly at the hand-over threshold`() {
        assertEquals(0f, playerContentFade(PLAYER_SHEET_HANDOVER_PROGRESS))
    }

    @Test
    fun `playerContentFade reaches one at full progress`() {
        assertEquals(1f, playerContentFade(1f))
    }

    @Test
    fun `miniPlayerFade is fully visible at rest`() {
        assertEquals(1f, miniPlayerFade(0f))
    }

    @Test
    fun `miniPlayerFade is fully faded exactly at the hand-over threshold`() {
        assertEquals(0f, miniPlayerFade(PLAYER_SHEET_HANDOVER_PROGRESS))
    }

    @Test
    fun `both surfaces share the same hand-over threshold`() {
        assertEquals(0f, playerContentFade(PLAYER_SHEET_HANDOVER_PROGRESS))
        assertEquals(0f, miniPlayerFade(PLAYER_SHEET_HANDOVER_PROGRESS))
    }
}
