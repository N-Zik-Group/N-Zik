package it.fast4x.invidious

import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Invidious root-level offline gate (spec-full-offline-mode, wired by the app in
 * MainApplication): with [Invidious.offlineGate] returning false, a request
 * fast-fails with the synthetic bodyless 503 from the OkHttp interceptor BEFORE
 * any socket is opened — this also pins the `Response.Builder().build()` shape
 * through the Ktor OkHttp engine; with the gate open the same call proceeds to
 * the real network instead.
 *
 * Timing note: `api.videos` targets the real YEWTU instance. The gate-closed
 * path never reaches it, but `HttpRequestRetry(maxRetries = 2, exponentialDelay())`
 * retries the synthetic 503 (the default retry covers server errors), adding
 * roughly 1s + 2s of delays — the whole test stays well under 15s.
 */
class InvidiousOfflineGateTest {

    @AfterEach
    fun tearDown() {
        // Null until wired keeps the module self-contained (standalone/tests default).
        Invidious.offlineGate = null
    }

    @Test
    fun `gate closed short-circuits with the synthetic 503 before reaching upstream`() = runBlocking {
        Invidious.offlineGate = { false }
        val exception = Invidious.api.videos("bogus-video-id")
            ?.exceptionOrNull()
            ?: fail("api.videos must return a failed Result, not null")
        assertTrue(exception is ResponseException)
        val responseException = exception as ResponseException
        assertEquals(503, responseException.response.status.value)
        assertEquals("No network connection", responseException.response.status.description)
    }

    @Test
    fun `gate open proceeds to the network instead of the synthetic 503`() = runBlocking {
        Invidious.offlineGate = { true }
        val exception = Invidious.api.videos("bogus-video-id")?.exceptionOrNull()
        // The real instance answers (a 4xx/5xx) or the connection itself fails —
        // either way it is NOT the bodyless synthetic gate response. A null
        // exception (a 2xx answer) is equally proof that the gate did not fire.
        val isSyntheticGate503 = exception is ResponseException &&
            exception.response.status.value == 503 &&
            exception.response.status.description == "No network connection"
        assertFalse(isSyntheticGate503)
    }
}
