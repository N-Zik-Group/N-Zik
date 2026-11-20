package app.n_zik.android.core.coil

import app.n_zik.android.core.coil.ImageCacheFactory.ArtworkQualityCooldown
import app.n_zik.android.core.coil.ImageCacheFactory.DownloadDecision
import app.n_zik.android.core.coil.ImageCacheFactory.NetworkQuality.HIGH
import app.n_zik.android.core.coil.ImageCacheFactory.NetworkQuality.LOW
import app.n_zik.android.core.coil.ImageCacheFactory.NetworkQuality.MEDIUM
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun `an already high quality cover needs no network query`() {
        val cooldown = ArtworkQualityCooldown(10_000)
        assertEquals(DownloadDecision(false, HIGH), cooldown.decide("cover", 100, HIGH) {
            error("A cached high quality cover cannot be upgraded")
        })
    }

    @Test fun `prefetch network probing does not hold the cover cache lock`() {
        val cooldown = ArtworkQualityCooldown(10_000)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val prefetch = workers.submit<DownloadDecision> {
                cooldown.decide("cover", 100, null) {
                    started.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS))
                    LOW
                }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val visible = workers.submit<DownloadDecision> { cooldown.decide("cover", 200, null) { HIGH } }
            assertEquals(DownloadDecision(true, HIGH), visible.get(2, TimeUnit.SECONDS))
            release.countDown()
            assertEquals(DownloadDecision(true, HIGH), prefetch.get(2, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            workers.shutdownNow()
        }
    }

    @Test fun `one time sources do not keep their requests for the process lifetime`() {
        val cooldown = ArtworkQualityCooldown(10_000)
        assertEquals(DownloadDecision(true, LOW), cooldown.decide("old", 100, null) { LOW })
        repeat(512) { cooldown.decide("other-$it", 100, null) { MEDIUM } }
        assertEquals(DownloadDecision(true, HIGH), cooldown.decide("old", 200, null) { HIGH })
    }

    @Test fun `a network check started before cache cleanup cannot restore the old decision`() {
        val cooldown = ArtworkQualityCooldown(10_000)
        val stale = cooldown.decide("cover", 100, null) {
            cooldown.clear()
            LOW
        }
        assertEquals(DownloadDecision(true, LOW), stale)
        assertEquals(DownloadDecision(true, HIGH), cooldown.decide("cover", 200, null) { HIGH })
    }
}
