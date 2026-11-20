package app.n_zik.android.core.coil

import app.n_zik.android.core.coil.ImageCacheFactory.ArtworkQualityCooldown
import app.n_zik.android.core.coil.ImageCacheFactory.DownloadDecision
import app.n_zik.android.core.coil.ImageCacheFactory.NetworkQuality.HIGH
import app.n_zik.android.core.coil.ImageCacheFactory.NetworkQuality.LOW
import app.n_zik.android.core.coil.ImageCacheFactory.NetworkQuality.MEDIUM
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkQualityCooldownTest {
    @Test fun `prefetch and visible artwork keep the same cache size before and after load`() {
        val cooldown = ArtworkQualityCooldown(10_000)
        assertEquals(DownloadDecision(true, HIGH), cooldown.decide("cover", 100, null) { HIGH })
        assertEquals(DownloadDecision(true, HIGH), cooldown.decide("cover", 200, null) { HIGH })
        assertEquals(DownloadDecision(true, HIGH), cooldown.decide("cover", 300, null) { LOW })
        assertEquals(DownloadDecision(false, HIGH), cooldown.decide("cover", 400, HIGH) { LOW })
    }

    @Test fun `a failed upgrade can be retried at the current quality after the cooldown`() {
        val cooldown = ArtworkQualityCooldown(10_000)
        assertEquals(DownloadDecision(true, HIGH), cooldown.decide("cover", 100, LOW) { HIGH })
        assertEquals(DownloadDecision(true, HIGH), cooldown.decide("cover", 200, LOW) { MEDIUM })
        assertEquals(DownloadDecision(true, MEDIUM), cooldown.decide("cover", 10_100, LOW) { MEDIUM })
        assertEquals(DownloadDecision(false, MEDIUM), cooldown.decide("cover", 10_200, MEDIUM) { LOW })
    }
}
