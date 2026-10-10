package app.n_zik.android.bridge.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A like alone (the companion's §10.2 write or the phone's own UI) triggers no player event: the
 * player source re-samples when a queue track's like moved ([queueLikesMoved]), and the hub
 * then publishes the queue / track delta carrying the new like.
 */
class QueueLikeResampleTest {

    private val hub = BridgeStateHub(clock = { 1_000L })

    @Test
    fun `a like moved on a queue track is a move`() {
        val known = mapOf("aaaaaaaaaaa" to TrackLike.NEUTRAL, "bbbbbbbbbbb" to TrackLike.LIKED)

        assertTrue(queueLikesMoved(known, known + ("aaaaaaaaaaa" to TrackLike.LIKED)))
        assertTrue(queueLikesMoved(known, known + ("bbbbbbbbbbb" to TrackLike.DISLIKED)))
    }

    @Test
    fun `unchanged likes, an unread track or an empty queue are no move`() {
        val known = mapOf("aaaaaaaaaaa" to TrackLike.NEUTRAL, "bbbbbbbbbbb" to TrackLike.LIKED)

        // Another songs write (a play count...) leaves every like where it was: no sample
        assertFalse(queueLikesMoved(known, known))
        assertFalse(queueLikesMoved(known, mapOf("aaaaaaaaaaa" to TrackLike.NEUTRAL)))
        assertFalse(queueLikesMoved(emptyMap(), mapOf("aaaaaaaaaaa" to TrackLike.LIKED)))
    }

    @Test
    fun `the re-sample of a liked current track emits a delta with the new like`() {
        hub.submit(playingSample)
        val liked = testTrack("aaaaaaaaaaa").copy(isLiked = true, like = TrackLike.LIKED)

        val deltas = hub.submit(playingSample.copy(queue = listOf(liked, testTrack("bbbbbbbbbbb"))))

        val queue = deltas.filterIsInstance<QueueChangedMessage>().single().queue
        assertEquals(TrackLike.LIKED, queue[0].like)
        assertTrue(queue[0].isLiked)
        assertEquals(TrackLike.NEUTRAL, queue[1].like)
        // Same current track: only the queue moved
        assertEquals(listOf("QueueChangedMessage"), deltas.map { it::class.simpleName })
    }

    @Test
    fun `a playWhenReady change alone emits a playbackChanged carrying it`() {
        // Contract 1.10.0: the phone's ring is `isBuffering && playWhenReady` (a seek while paused)
        hub.submit(playingSample.copy(isPlaying = false, playWhenReady = true))
        val deltas = hub.submit(playingSample.copy(isPlaying = false, playWhenReady = false))
        val playback = deltas.filterIsInstance<PlaybackChangedMessage>().single()
        assertEquals(false, playback.playWhenReady)
        assertEquals(false, hub.snapshot().playWhenReady)
    }

    @Test
    fun `the re-sample of a liked queued track emits a delta with the new like`() {
        hub.submit(playingSample)
        val disliked = testTrack("bbbbbbbbbbb").copy(like = TrackLike.DISLIKED)

        val deltas = hub.submit(playingSample.copy(queue = listOf(testTrack("aaaaaaaaaaa"), disliked)))

        assertEquals(TrackLike.DISLIKED, deltas.filterIsInstance<QueueChangedMessage>().single().queue[1].like)
    }
}
