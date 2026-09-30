package app.n_zik.android.bridge.pairing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PairingRateLimiterTest {

    private var now = 0L
    private val limiter = PairingRateLimiter(clock = { now })

    @Test
    fun `sixth attempt from one IP within 60 s is limited with retryAfterMs`() {
        repeat(5) {
            assertEquals(RateLimitResult.Allowed, limiter.tryAcquire("192.168.1.20"))
            now += 1_000L
        }

        assertEquals(RateLimitResult.Limited(retryAfterMs = 55_000L), limiter.tryAcquire("192.168.1.20"))
        assertEquals(RateLimitResult.Allowed, limiter.tryAcquire("192.168.1.21"))
    }

    @Test
    fun `sliding window frees an attempt once the oldest leaves it`() {
        repeat(5) { limiter.tryAcquire("10.0.0.5") }
        now += 59_999L
        assertEquals(RateLimitResult.Limited(retryAfterMs = 1L), limiter.tryAcquire("10.0.0.5"))

        now += 1L

        assertEquals(RateLimitResult.Allowed, limiter.tryAcquire("10.0.0.5"))
    }

    @Test
    fun `twenty-first attempt in 10 min is limited globally`() {
        repeat(20) { index ->
            assertEquals(RateLimitResult.Allowed, limiter.tryAcquire("10.0.0.${index / 4}"))
            now += 10_000L
        }

        val result = limiter.tryAcquire("10.0.1.1")

        assertEquals(RateLimitResult.Limited(retryAfterMs = 600_000L - 200_000L), result)
    }

    @Test
    fun `limited calls do not consume budget`() {
        repeat(5) { limiter.tryAcquire("10.0.0.5") }
        repeat(10) { limiter.tryAcquire("10.0.0.5") }
        now += 60_000L

        repeat(5) { assertEquals(RateLimitResult.Allowed, limiter.tryAcquire("10.0.0.5")) }
    }
}
