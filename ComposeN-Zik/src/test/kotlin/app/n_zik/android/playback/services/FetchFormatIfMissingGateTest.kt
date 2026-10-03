package app.n_zik.android.playback.services

import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.FormatTable
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import it.fast4x.innertube.models.PlayerResponse
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import timber.log.Timber

/**
 * Issue #881 (Phase 3, Fix A, review patch): the gate wiring INSIDE `fetchFormatIfMissing` —
 * [FormatFetchGateTest] pins the gate class; this test pins the call site: the mark happens
 * before the fire-and-forget launch, a second call for the same video while the first is
 * still in flight launches no new fetch (dedup), and a hard failure releases the gate so the
 * next re-open retries.
 *
 * `fetchFormatIfMissing` is a private top-level function: it is invoked by reflection on the
 * file facade (pinned to the name — a rename fails this test loudly, same contract as the
 * [PlaybackLoadControlTest] reflection). `playerResponseForMetadata` is stubbed statically;
 * the fire-and-forget launch runs on the REAL PLAYBACK dispatcher and is verified with
 * mockk's timeout verify (StreamResolverPageCacheTest pattern).
 */
class FetchFormatIfMissingGateTest {

    private val formatTable = mockk<FormatTable>(relaxed = true)

    private val capturedLogs = mutableListOf<String>()

    private val logCapture = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            synchronized(capturedLogs) { capturedLogs += message }
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        Timber.uproot(logCapture)
        capturedLogs.clear()
    }

    @Test
    fun `a second call for the same video is deduplicated in-flight, then retried after a hard failure`() {
        Timber.plant(logCapture)
        mockkObject(Database)
        every { Database.formatTable } returns formatTable
        mockkStatic("app.n_zik.android.playback.services.StreamResolverKt")

        val id = "abcdefghijk" // 11 chars — passes the id length check
        val holdFetch = CompletableDeferred<Unit>()

        // Suspends until the test releases the first fetch, then returns a hard failure
        // (network / session) — the outcome that must release the gate.
        coEvery { playerResponseForMetadata(id) } coAnswers {
            holdFetch.await()
            Result.failure<PlayerResponse>(IOException("simulated network failure"))
        }

        // Call #1: marks the gate, launches the fetch (which suspends inside the stub).
        invokeFetchFormatIfMissing(id)
        coVerify(timeout = 5_000) { playerResponseForMetadata(id) }

        // Call #2, same id while call #1 is still in flight: deduplicated — it returns
        // before the launch, no new fetch.
        invokeFetchFormatIfMissing(id)
        coVerify(exactly = 1) { playerResponseForMetadata(id) }

        // Release call #1: it fails → the gate is released (the log below is emitted after
        // the release). Call #3 must fetch again.
        holdFetch.complete(Unit)
        awaitLog("gate released, will retry")
        invokeFetchFormatIfMissing(id)
        coVerify(timeout = 5_000, exactly = 2) { playerResponseForMetadata(id) }
    }

    /** Bounded wait (with diagnostics) for a log line — the release runs on the real PLAYBACK dispatcher. */
    private fun awaitLog(needle: String, budgetMs: Long = 10_000L) {
        val deadline = System.currentTimeMillis() + budgetMs
        while (System.currentTimeMillis() < deadline && capturedLogs.none { it.contains(needle) }) {
            Thread.sleep(5)
        }
        assertTrue(
            capturedLogs.any { it.contains(needle) },
            "expected a log containing '$needle' within $budgetMs ms; captured: $capturedLogs",
        )
    }

    /**
     * The file facade `StreamResolverKt` is package-private in bytecode (every top-level
     * function in StreamResolver.kt is internal/private), so it cannot be referenced at
     * compile time — the test lives in the SAME package, so the runtime lookup is
     * package-visible. Pinned to the class + function names: a rename fails this test
     * loudly (by design, same contract as the PlaybackLoadControlTest reflection).
     */
    private val facadeClass = Class.forName("app.n_zik.android.playback.services.StreamResolverKt")

    private fun invokeFetchFormatIfMissing(videoId: String) {
        val method = facadeClass.getDeclaredMethod("fetchFormatIfMissing", String::class.java)
        method.isAccessible = true
        method.invoke(null, videoId)
    }
}
