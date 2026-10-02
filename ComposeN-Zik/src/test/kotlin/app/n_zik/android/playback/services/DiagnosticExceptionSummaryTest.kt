package app.n_zik.android.playback.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The lifted `LOAD_RETRY` root-cause formatter (issue #881, spec S3 / M4): the cause walk is
 * bounded so a malformed cyclic chain can never spin the retry thread, and any
 * `scheme://…` blob in the message is scrubbed so the exported log file — shared publicly —
 * never carries the stream URL. Plain JVM: no Android pieces involved.
 */
class DiagnosticExceptionSummaryTest {

    @Test
    fun `the root cause of a deep chain is reported`() {
        val root = IllegalStateException("root-cause-detail")
        val exception = RuntimeException("outer", RuntimeException("middle", root))

        val summary = diagnosticExceptionSummary(exception)

        assertTrue(summary.startsWith("IllegalStateException:"))
        assertTrue(summary.contains("root-cause-detail"))
        assertFalse(summary.contains("outer"))
    }

    @Test
    fun `a url in the root message is scrubbed`() {
        val exception = java.io.IOException(
            "Unable to download https://www.youtube.com/get_video?doc=abc123&potoken=sig%3DSECRET",
        )

        val summary = diagnosticExceptionSummary(exception)

        assertTrue(summary.contains("<url>"), "the URL is replaced by a placeholder: $summary")
        assertFalse(summary.contains("https://"), "no URL may survive: $summary")
        assertFalse(summary.contains("SECRET"), "no signed token may survive: $summary")
    }

    @Test
    fun `a cyclic cause chain terminates`() {
        // Two-node cycle: a -> b -> a. initCause is legal here because each cause is null
        // at call time and differs from the exception itself.
        val a = RuntimeException("a")
        val b = RuntimeException("b")
        b.initCause(a)
        a.initCause(b)

        val summary = diagnosticExceptionSummary(a)

        // The loop must stop at the hop limit instead of spinning forever; the returned
        // node is whichever the walk reached — only the termination is asserted.
        assertTrue(summary.isNotBlank())
    }

    @Test
    fun `the message is truncated at 200 chars`() {
        val exception = RuntimeException("x".repeat(500))

        val summary = diagnosticExceptionSummary(exception)

        val message = summary.removePrefix("RuntimeException: ")
        assertEquals(200, message.length)
    }

    @Test
    fun `a null message renders a dash`() {
        val summary = diagnosticExceptionSummary(RuntimeException())

        assertEquals("RuntimeException: -", summary)
    }
}
