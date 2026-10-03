package app.n_zik.android.playback.services

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Issue #881 (gh-881), Phase 3 Fix C: the playback LoadControl must keep a back buffer so
 * backward seeks resolve in-buffer (no full data-source re-open) — see the investigation in
 * spec §8.5 (media3 1.10.1 `ProgressiveMediaPeriod.seekToUs` + `SampleQueue.seekTo`).
 */
class PlaybackLoadControlTest {

    /** The DurationIndicator rewind buttons go down to −30 s; the back buffer must cover them. */
    @Test
    fun `back buffer covers the rewind increments with headroom`() {
        assertTrue(SEEK_BACK_BUFFER_MS >= 30_000)
        val loadControl = createSeekFriendlyLoadControl()
        assertEquals(
            SEEK_BACK_BUFFER_MS.toLong() * 1_000L,
            loadControl.getBackBufferDurationUs(PlayerId.UNSET),
        )
    }

    /** retainBackBufferFromKeyframe keeps the buffer before the seek target across seeks. */
    @Test
    fun `back buffer is retained across seeks`() {
        val loadControl = createSeekFriendlyLoadControl()
        assertTrue(loadControl.retainBackBufferFromKeyframe(PlayerId.UNSET))
    }

    /**
     * The back buffer is the only back-buffer change: the forward in-buffer window and the
     * other buffer parameters stay at the media3 defaults (guards the premise that nothing
     * else was touched).
     *
     * Review patch: the previous version asserted the media3 DEFAULT constants — which are
     * always true no matter what the builder did. It now asserts the values on the BUILT
     * instance, read via reflection on the same fields `setBufferDurationsMs` writes (media3
     * 1.10.1 exposes no getters for them; a rename fails this test loudly by design).
     */
    @Test
    fun `all other buffer parameters stay at the media3 defaults`() {
        val loadControl = createSeekFriendlyLoadControl() as DefaultLoadControl
        assertTrue(loadControl is DefaultLoadControl)
        assertEquals(50_000L * 1_000L, instanceFieldUs(loadControl, "minBufferUs"))
        assertEquals(50_000L * 1_000L, instanceFieldUs(loadControl, "maxBufferUs"))
        // Seek-start threshold: untouched by the fix — still the media3 default (1000 ms).
        assertEquals(1_000L * 1_000L, instanceFieldUs(loadControl, "bufferForPlaybackUs"))
    }

    /** Reads a pinned `long` instance field (media3 1.10.1 exposes no getter — see test KDocs). */
    private fun instanceFieldUs(instance: Any, name: String): Long {
        val field = instance.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.getLong(instance)
    }

    /**
     * Post-rebuffer settle target is applied. `media3` 1.10.1 exposes no getter for it
     * (`LoadControl` dropped the 2.x accessors), so the pinned field is read directly — the
     * version is pinned in `libs.versions.toml`, and a media3 upgrade renaming the field fails
     * this test loudly by design.
     */
    @Test
    fun `post-rebuffer settle target is lowered to resume faster`() {
        val loadControl = createSeekFriendlyLoadControl() as DefaultLoadControl
        val field = DefaultLoadControl::class.java.getDeclaredField("bufferForPlaybackAfterRebufferUs")
        field.isAccessible = true
        assertEquals(REBUFFER_SETTLE_TARGET_MS.toLong() * 1_000L, field.getLong(loadControl))
    }
}
