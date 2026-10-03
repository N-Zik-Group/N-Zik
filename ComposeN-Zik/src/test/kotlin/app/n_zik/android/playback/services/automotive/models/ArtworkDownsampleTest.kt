package app.n_zik.android.playback.services.automotive.models

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM tests for [SessionMediaItemMapper.computeInSampleSize]: the
 * power-of-two decode stride used to downscale embedded Android Auto artwork
 * toward the 512px target without ever decoding above it.
 */
class ArtworkDownsampleTest {

    @Test
    fun `image already at or below the target is not downsampled`() {
        assertEquals(1, SessionMediaItemMapper.computeInSampleSize(512, 512, 512))
        assertEquals(1, SessionMediaItemMapper.computeInSampleSize(300, 800, 512))
    }

    @Test
    fun `samples in powers of two until the longest side is below the target`() {
        assertEquals(2, SessionMediaItemMapper.computeInSampleSize(1024, 1024, 512))
        assertEquals(2, SessionMediaItemMapper.computeInSampleSize(2000, 1000, 512))
        assertEquals(4, SessionMediaItemMapper.computeInSampleSize(4000, 3000, 512))
    }

    @Test
    fun `invalid dimensions fall back to full resolution`() {
        assertEquals(1, SessionMediaItemMapper.computeInSampleSize(0, 0, 512))
        assertEquals(1, SessionMediaItemMapper.computeInSampleSize(-1, 100, 512))
        assertEquals(1, SessionMediaItemMapper.computeInSampleSize(1024, 1024, 0))
    }
}
