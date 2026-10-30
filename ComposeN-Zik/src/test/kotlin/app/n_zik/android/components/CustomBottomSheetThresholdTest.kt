package app.n_zik.android.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomBottomSheetThresholdTest {

    @Test
    fun `transition glass exists only between settled endpoints`() {
        listOf(-1f, 0f, 1f, 2f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertFalse(isPlayerDeployGlassActive(it))
        }
        listOf(.001f, .12f, .45f, .999f).forEach {
            assertTrue(isPlayerDeployGlassActive(it))
        }
    }

    @Test
    fun `glass foreground stays visible while its body fades and yields to full player`() {
        listOf(0f, .1f, .225f, PLAYER_SHEET_HANDOVER_PROGRESS).forEach {
            assertEquals(1f, miniPlayerContentFade(it, glassMaterial = true))
        }
        assertEquals(.5f, miniPlayerContentFade(.725f, glassMaterial = true), .001f)
        assertEquals(0f, miniPlayerContentFade(1f, glassMaterial = true))
        listOf(0f, .1f, .45f, .8f, 1f).forEach {
            assertEquals(1f, miniPlayerContentFade(it, glassMaterial = true) + playerContentFade(it), .001f)
        }
    }

    @Test
    fun `native foreground keeps original fade and glass clamps invalid progress`() {
        listOf(-1f, 0f, .2f, .45f, .8f, 1f, 2f).forEach {
            assertEquals(miniPlayerFade(it), miniPlayerContentFade(it, glassMaterial = false))
        }
        assertEquals(1f, miniPlayerContentFade(Float.NaN, glassMaterial = true))
        assertEquals(1f, miniPlayerContentFade(-1f, glassMaterial = true))
        assertEquals(0f, miniPlayerContentFade(2f, glassMaterial = true))
    }

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
