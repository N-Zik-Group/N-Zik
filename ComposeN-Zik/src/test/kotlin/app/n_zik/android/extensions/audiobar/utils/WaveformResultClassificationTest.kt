package app.n_zik.android.extensions.audiobar.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/** Regression coverage for the WaveformExtractor.classifyExtractionFailure classifier: this is the exact distinction (transient vs fatal) whose absence caused a false "error updating waveform" toast for a transiently-incomplete download cache (gh-606 follow-up bug). */
class WaveformResultClassificationTest {

    @Test
    fun `PlaceholderDataSource IOException classifies as NotReady`() {
        val e = IOException("PlaceholderDataSource -> song not fully cached yet")

        assertEquals(WaveformResult.NotReady, WaveformExtractor.classifyExtractionFailure(e))
    }

    @Test
    fun `other IOException classifies as Failed with the exception message as reason`() {
        val e = IOException("disk full")

        val result = WaveformExtractor.classifyExtractionFailure(e)

        assertTrue(result is WaveformResult.Failed)
        assertEquals("disk full", (result as WaveformResult.Failed).reason)
    }

    @Test
    fun `non-IOException classifies as Failed`() {
        val e = RuntimeException("boom")

        val result = WaveformExtractor.classifyExtractionFailure(e)

        assertTrue(result is WaveformResult.Failed)
        assertEquals("boom", (result as WaveformResult.Failed).reason)
    }

    @Test
    fun `exception with no message falls back to the exception class name as reason`() {
        val e = RuntimeException()

        val result = WaveformExtractor.classifyExtractionFailure(e)

        assertTrue(result is WaveformResult.Failed)
        assertEquals("RuntimeException", (result as WaveformResult.Failed).reason)
    }
}
