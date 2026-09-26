package app.n_zik.android.listentogether

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BypassReadyWaitTest {

    @Test
    fun `already ready - no polling and zero wait`() = runBlocking {
        val outcome = runBypassReadyWait(
            isReady = { true },
            delayMs = 10,
        )
        assertTrue(outcome.ready)
        assertEquals(0L, outcome.waitedMs)
    }

    @Test
    fun `becomes ready mid budget - wait stops as soon as ready`() = runBlocking {
        var polls = 0
        val outcome = runBypassReadyWait(
            isReady = { polls++; polls >= 3 },
            delayMs = 10,
            maxAttempts = 10,
        )
        assertTrue(outcome.ready)
        assertEquals(20L, outcome.waitedMs)
    }

    @Test
    fun `never ready - budget exhausted reports not-ready`() = runBlocking {
        val outcome = runBypassReadyWait(
            isReady = { false },
            delayMs = 10,
            maxAttempts = 5,
        )
        assertFalse(outcome.ready)
        assertEquals(50L, outcome.waitedMs)
    }
}
