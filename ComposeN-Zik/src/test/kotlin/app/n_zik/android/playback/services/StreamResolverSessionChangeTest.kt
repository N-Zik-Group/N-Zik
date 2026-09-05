package app.n_zik.android.playback.services

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamResolverSessionChangeTest {
    @Test
    fun `session change cancellation is detected`() {
        val exception = CancellationException("InnerTube session changed")

        assertTrue(isInnerTubeSessionChangeCancellation(exception))
    }

    @Test
    fun `session change detection is case insensitive`() {
        val exception = CancellationException("innertube Session CHANGED")

        assertTrue(isInnerTubeSessionChangeCancellation(exception))
    }

    @Test
    fun `other cancellations are not treated as session changes`() {
        assertFalse(isInnerTubeSessionChangeCancellation(CancellationException("Other cancellation")))
        assertFalse(isInnerTubeSessionChangeCancellation(CancellationException("")))
        assertFalse(isInnerTubeSessionChangeCancellation(CancellationException(null)))
    }
}
