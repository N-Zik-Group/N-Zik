package app.n_zik.android.playback.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CrossfadePositionSchedulerTest {
    @Test
    fun `media delay uses live speed and rounds up`() {
        assertEquals(12_000L, crossfadeDelayMillis(6_000L, 0.5f))
        assertEquals(3_000L, crossfadeDelayMillis(6_000L, 2f))
        assertEquals(1L, crossfadeDelayMillis(1L, 2f))
        assertEquals(0L, crossfadeDelayMillis(-1L, 1f))
    }

    @Test
    fun `audio fades at the media threshold while artwork is pending`() = runTest {
        var artworkStarted = false
        var artworkCancelled = false
        var fadeAt: Long? = null
        var prepared = false
        runCrossfadeSchedule(
            triggerPosition = 12_000L,
            position = { testScheduler.currentTime * 2 },
            speed = { 2f },
            isStillOwner = { true },
            prepareAudio = { prepared = true },
            prefetchArtwork = {
                artworkStarted = true
                try {
                    awaitCancellation()
                } finally {
                    artworkCancelled = true
                }
            },
            beginFade = { fadeAt = testScheduler.currentTime },
        )
        assertTrue(prepared)
        assertTrue(artworkStarted)
        assertTrue(artworkCancelled)
        assertEquals(6_000L, fadeAt)
    }

    @Test
    fun `a queue change prevents a prepared fade and cancels its artwork`() = runTest {
        var prepared = false
        var artworkCancelled = false
        var began = false
        runCrossfadeSchedule(
            triggerPosition = 9_000L,
            position = { testScheduler.currentTime },
            speed = { 1f },
            isStillOwner = { testScheduler.currentTime < 4_000L },
            prepareAudio = { prepared = true },
            prefetchArtwork = {
                try {
                    awaitCancellation()
                } finally {
                    artworkCancelled = true
                }
            },
            beginFade = { began = true },
        )
        assertTrue(prepared)
        assertTrue(artworkCancelled)
        assertFalse(began)
    }
    @Test
    fun `speed change re-arms from the current media position`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scheduler = CrossfadePositionScheduler()
        try {
            var speed = 0.5f
            var initialPosition = 0L
            var speedChangedAt = 0L
            var fadeTime: Long? = null
            fun arm() = scheduler.arm(
                scope = this,
                triggerPosition = 8_000L,
                position = { initialPosition + ((testScheduler.currentTime - speedChangedAt) * speed).toLong() },
                speed = { speed },
                isStillOwner = { true },
                prepareAudio = {},
                prefetchArtwork = { awaitCancellation() },
                beginFade = { fadeTime = testScheduler.currentTime },
            )
            arm()
            runCurrent()
            advanceTimeBy(4_000)
            initialPosition = 2_000L
            speedChangedAt = testScheduler.currentTime
            speed = 2f
            arm()
            runCurrent()
            advanceTimeBy(2_999)
            assertEquals(null, fadeTime)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(7_000L, fadeTime)
        } finally {
            scheduler.cancel()
            Dispatchers.resetMain()
        }
    }
}
