package app.n_zik.android.components.player

import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.enums.ThumbnailType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerCurrentCoverTest {
    @Test fun `preset three hidden thumbnail has full width even when expanded and Modern cover style`() {
        // AppearancePresetsCard onClick2: this is the real third preset, not onClick3.
        assertFalse(playerLandscapeArtworkColumnVisible(PlayerType.Modern, ThumbnailType.Modern,
            expandedPlayer = true, showThumbnail = false))
        assertTrue(playerLandscapeArtworkColumnVisible(PlayerType.Modern, ThumbnailType.Modern,
            expandedPlayer = true, showThumbnail = true))
        assertTrue(playerLandscapeArtworkColumnVisible(PlayerType.Modern, ThumbnailType.Essential,
            expandedPlayer = true, showThumbnail = true))
    }

    @Test fun `incoming cover commits once and late outgoing completion cannot flip it back`() {
        val frames = PlayerCoverFrameState<String>()
        val a = PlayerCoverIdentity("A", "art-A")
        val b = PlayerCoverIdentity("B", "art-B")
        frames.request(a)
        frames.complete(a, "decoded-A")
        val outgoingVisibleAndCaptured = frames.displayed
        frames.request(b)
        assertSame(outgoingVisibleAndCaptured, frames.displayed)
        frames.complete(b, "decoded-B")
        val incomingVisibleAndCaptured = frames.displayed
        frames.complete(a, "late-decoded-A")
        assertSame(incomingVisibleAndCaptured, frames.displayed)
        assertEquals(b, frames.displayed?.identity)
        assertEquals("decoded-B", frames.displayed?.image)
    }

    @Test fun `outgoing request finishing before incoming cannot replace committed frame`() {
        val frames = PlayerCoverFrameState<String>()
        val a = PlayerCoverIdentity("A", "old-art")
        val b = PlayerCoverIdentity("B", "new-art")
        frames.request(a)
        frames.complete(a, "first-A")
        frames.request(b)
        frames.complete(a, "late-A")
        assertEquals("first-A", frames.displayed?.image)
        frames.complete(b, "B")
        assertEquals(b, frames.displayed?.identity)
    }

    @Test fun `same track metadata art refresh owns a distinct request despite unchanged media id`() {
        val frames = PlayerCoverFrameState<String>()
        val old = PlayerCoverIdentity("A", "old-art")
        val fresh = PlayerCoverIdentity("A", "fresh-art")
        frames.request(old)
        frames.request(fresh)
        frames.complete(fresh, "fresh")
        frames.complete(old, "stale")
        assertEquals(fresh, frames.displayed?.identity)
        assertEquals("fresh", frames.displayed?.image)
    }
}
