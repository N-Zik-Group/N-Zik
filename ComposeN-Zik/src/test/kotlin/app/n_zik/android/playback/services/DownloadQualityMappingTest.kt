package app.n_zik.android.playback.services

import app.n_zik.android.enums.AudioQualityFormat
import app.n_zik.android.enums.DownloadQualityFormat
import com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests [downloadQualityToInnerTubeX] and [audioQualityToInnerTubeX]: both mappings must be
 * identical — InnerTubeX only supports AUTO/HIGH/LOW, so Auto resolves to LOW on metered
 * connections and AUTO otherwise. The resolved value tags the URL cache entries, so the two
 * mappings must stay in sync or the cache quality-match would drift apart.
 */
class DownloadQualityMappingTest {

    @Test
    fun `high should map to HIGH on any connection`() {
        assertEquals(InnerTubeXAudioQuality.HIGH, downloadQualityToInnerTubeX(DownloadQualityFormat.High, false))
        assertEquals(InnerTubeXAudioQuality.HIGH, downloadQualityToInnerTubeX(DownloadQualityFormat.High, true))
    }

    @Test
    fun `low should map to LOW on any connection`() {
        assertEquals(InnerTubeXAudioQuality.LOW, downloadQualityToInnerTubeX(DownloadQualityFormat.Low, false))
        assertEquals(InnerTubeXAudioQuality.LOW, downloadQualityToInnerTubeX(DownloadQualityFormat.Low, true))
    }

    @Test
    fun `auto should map to LOW when metered and AUTO otherwise`() {
        assertEquals(InnerTubeXAudioQuality.LOW, downloadQualityToInnerTubeX(DownloadQualityFormat.Auto, true))
        assertEquals(InnerTubeXAudioQuality.AUTO, downloadQualityToInnerTubeX(DownloadQualityFormat.Auto, false))
    }

    @Test
    fun `streaming high should map to HIGH on any connection`() {
        assertEquals(InnerTubeXAudioQuality.HIGH, audioQualityToInnerTubeX(AudioQualityFormat.High, false))
        assertEquals(InnerTubeXAudioQuality.HIGH, audioQualityToInnerTubeX(AudioQualityFormat.High, true))
    }

    @Test
    fun `streaming low should map to LOW on any connection`() {
        assertEquals(InnerTubeXAudioQuality.LOW, audioQualityToInnerTubeX(AudioQualityFormat.Low, false))
        assertEquals(InnerTubeXAudioQuality.LOW, audioQualityToInnerTubeX(AudioQualityFormat.Low, true))
    }

    @Test
    fun `streaming auto should map to LOW when metered and AUTO otherwise`() {
        assertEquals(InnerTubeXAudioQuality.LOW, audioQualityToInnerTubeX(AudioQualityFormat.Auto, true))
        assertEquals(InnerTubeXAudioQuality.AUTO, audioQualityToInnerTubeX(AudioQualityFormat.Auto, false))
    }
}
