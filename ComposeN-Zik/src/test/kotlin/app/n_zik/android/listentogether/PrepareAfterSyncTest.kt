package app.n_zik.android.listentogether

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [ListenTogetherPlayerBridge.shouldPreparePlayerAfterSync] — the guard that keeps a
 * fresh (IDLE) player from staying IDLE after a Listen Together queue sync.
 *
 * Media3 1.10.1 never moves an IDLE player out of IDLE automatically (setMediaItems /
 * addMediaItems leave it IDLE, play() only flips playWhenReady), so after a process restart
 * the first sync left the guest stuck IDLE with a loaded queue — "le player ne veut plus
 * rien faire après un reconnexion" (user-reported, 2026-09-26). Only prepare() resumes an
 * IDLE player; it is a no-op for every other state, and the ENDED state self-recovers from
 * the sync seek.
 */
class PrepareAfterSyncTest {

    @Test
    fun `idle player with a loaded queue must be prepared`() {
        assertTrue(
            ListenTogetherPlayerBridge.shouldPreparePlayerAfterSync(
                playbackState = Player.STATE_IDLE,
                mediaItemCount = 43,
            ),
        )
    }

    @Test
    fun `idle player without items is left alone`() {
        // prepare() on an empty timeline would just flip IDLE -> ENDED; nothing to load.
        assertFalse(
            ListenTogetherPlayerBridge.shouldPreparePlayerAfterSync(
                playbackState = Player.STATE_IDLE,
                mediaItemCount = 0,
            ),
        )
    }

    @Test
    fun `ended player self-recovers from the sync seek - no prepare needed`() {
        assertFalse(
            ListenTogetherPlayerBridge.shouldPreparePlayerAfterSync(
                playbackState = Player.STATE_ENDED,
                mediaItemCount = 43,
            ),
        )
    }

    @Test
    fun `ready player needs no prepare`() {
        assertFalse(
            ListenTogetherPlayerBridge.shouldPreparePlayerAfterSync(
                playbackState = Player.STATE_READY,
                mediaItemCount = 43,
            ),
        )
    }

    @Test
    fun `buffering player needs no prepare`() {
        assertFalse(
            ListenTogetherPlayerBridge.shouldPreparePlayerAfterSync(
                playbackState = Player.STATE_BUFFERING,
                mediaItemCount = 43,
            ),
        )
    }
}
