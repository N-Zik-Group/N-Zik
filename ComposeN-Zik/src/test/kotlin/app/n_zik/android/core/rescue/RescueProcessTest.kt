package app.n_zik.android.core.rescue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Unit tests for the kill-request flag of [RescueProcess].
 *
 * The broadcast side needs a real [android.content.Context] and is out of scope for pure
 * JUnit5 tests. This file covers the flag file that acts as the safety net of the hot
 * kill: write, atomic consume, idempotence (no kill loop), re-request after consumption,
 * the discard that prevents a dead main process from poisoning the next launch, and the
 * nonce verification that gates the hot kill (the API < 33 receiver is exported, so the
 * nonce is the only sender proof).
 */
class RescueProcessTest {

    private fun flagFile(dir: File) = File(dir, RescueProcess.KILL_REQUEST_FLAG_NAME)

    // ──────────────────────────────────────────────────────────────────────
    // Consume without a pending request
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `consuming without a pending flag returns false and creates nothing`(@TempDir tmp: File) {
        assertFalse(RescueProcess.consumeKillRequestFlag(tmp))
        assertFalse(flagFile(tmp).exists())
        assertFalse(File(flagFile(tmp).path + ".consumed").exists())
    }

    // ──────────────────────────────────────────────────────────────────────
    // Write + consume
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `writing a flag then consuming it removes it and returns true`(@TempDir tmp: File) {
        val flag = RescueProcess.writeKillRequestFlag(tmp, "rescue pid=42 at=123456")
        assertTrue(flag.exists())
        assertEquals("rescue pid=42 at=123456", flag.readText())

        assertTrue(RescueProcess.consumeKillRequestFlag(tmp))
        assertFalse(flag.exists(), "the consumed flag must be gone")
        assertFalse(
            File(flag.path + ".consumed").exists(),
            "the parking file must not survive the consumption"
        )
    }

    @Test
    fun `consuming twice is idempotent so a failed kill cannot loop`(@TempDir tmp: File) {
        RescueProcess.writeKillRequestFlag(tmp, "once")

        assertTrue(RescueProcess.consumeKillRequestFlag(tmp), "the first consume must see the request")
        assertFalse(
            RescueProcess.consumeKillRequestFlag(tmp),
            "the second consume must not re-trigger the kill (no kill loop)"
        )
    }

    @Test
    fun `an empty flag is still consumed, only the log payload is affected`(@TempDir tmp: File) {
        flagFile(tmp).writeText("")

        assertTrue(RescueProcess.consumeKillRequestFlag(tmp))
        assertFalse(flagFile(tmp).exists())
    }

    // ──────────────────────────────────────────────────────────────────────
    // Re-request and overwrite
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `a new request after consumption creates a fresh pending flag`(@TempDir tmp: File) {
        RescueProcess.writeKillRequestFlag(tmp, "first")
        assertTrue(RescueProcess.consumeKillRequestFlag(tmp))

        RescueProcess.writeKillRequestFlag(tmp, "second")
        val flag = flagFile(tmp)
        assertTrue(flag.exists())
        assertEquals("second", flag.readText())
        assertTrue(RescueProcess.consumeKillRequestFlag(tmp))
        assertFalse(flag.exists())
    }

    @Test
    fun `writing twice overwrites the previous request`(@TempDir tmp: File) {
        RescueProcess.writeKillRequestFlag(tmp, "stale")
        RescueProcess.writeKillRequestFlag(tmp, "fresh")

        val flag = flagFile(tmp)
        // The latest request must win on every platform (POSIX: atomic rename; Windows: the
        // stale flag is removed and the rename retried, else a direct write) — a stale nonce
        // must never survive a re-request, or the hot kill would be silently refused.
        assertTrue(flag.exists())
        assertEquals("fresh", flag.readText())
        assertFalse(
            File(flag.path + ".tmp").exists(),
            "the temp file must not survive the write"
        )
        assertTrue(RescueProcess.consumeKillRequestFlag(tmp))
        assertFalse(flag.exists())
    }

    // ──────────────────────────────────────────────────────────────────────
    // Cancel (main process observed dead: the request is moot)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `cancelling without a pending flag is a no-op`(@TempDir tmp: File) {
        RescueProcess.cancelKillRequestFlag(tmp)
        assertFalse(flagFile(tmp).exists())
    }

    @Test
    fun `cancelling removes a pending flag so the next launch does not self-kill`(@TempDir tmp: File) {
        RescueProcess.writeKillRequestFlag(tmp, "pending")

        RescueProcess.cancelKillRequestFlag(tmp)

        assertFalse(flagFile(tmp).exists(), "the discarded flag must not leak into the next launch")
        assertFalse(RescueProcess.consumeKillRequestFlag(tmp))
    }

    @Test
    fun `cancelling also clears a leftover parking file from an interrupted consumption`(@TempDir tmp: File) {
        flagFile(tmp).writeText("pending")
        // Simulates a consumption that died between the rename and the delete.
        File(flagFile(tmp).path + ".consumed").writeText("pending")
        flagFile(tmp).renameTo(File(flagFile(tmp).path + ".consumed"))

        RescueProcess.cancelKillRequestFlag(tmp)

        assertFalse(flagFile(tmp).exists())
        assertFalse(File(flagFile(tmp).path + ".consumed").exists())
    }

    // ──────────────────────────────────────────────────────────────────────
    // Nonce verification (hot-kill sender check)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `flagNonce returns the nonce embedded in a requestKillMain-style payload`(@TempDir tmp: File) {
        RescueProcess.writeKillRequestFlag(tmp, "nonce=abc-123 rescue pid=1 at=2")

        assertEquals("abc-123", RescueProcess.flagNonce(tmp))
    }

    @Test
    fun `flagNonce returns null when there is no pending flag`(@TempDir tmp: File) {
        assertNull(RescueProcess.flagNonce(tmp))
    }

    @Test
    fun `flagNonce returns null for a flag that carries no nonce token`(@TempDir tmp: File) {
        flagFile(tmp).writeText("")

        assertNull(RescueProcess.flagNonce(tmp))
    }

    @Test
    fun `flagNonce on a legacy pre-nonce payload returns null so it can never match`(@TempDir tmp: File) {
        // Pre-nonce payload (older payload shape, or a hand-written flag): without the
        // "nonce=" prefix the flag is legacy — it must never yield a matchable nonce,
        // otherwise a broadcast crafted with the first token of the payload could trigger
        // the kill.
        flagFile(tmp).writeText("rescue pid=42 at=123456")

        assertNull(RescueProcess.flagNonce(tmp))
    }

    @Test
    fun `a matching nonce consumes the pending flag`(@TempDir tmp: File) {
        RescueProcess.writeKillRequestFlag(tmp, "nonce=abc-123 rescue pid=1 at=2")

        assertTrue(RescueProcess.consumeKillRequestIfNonceMatches(tmp, "abc-123"))
        assertFalse(flagFile(tmp).exists(), "the consumed flag must be gone")
    }

    @Test
    fun `a wrong nonce is refused and the pending flag stays`(@TempDir tmp: File) {
        RescueProcess.writeKillRequestFlag(tmp, "nonce=abc-123 rescue pid=1 at=2")

        assertFalse(RescueProcess.consumeKillRequestIfNonceMatches(tmp, "evil-nonce"))
        assertTrue(
            flagFile(tmp).exists(),
            "a refused broadcast must leave the pending request intact"
        )
    }

    @Test
    fun `a missing flag is refused and creates nothing`(@TempDir tmp: File) {
        assertFalse(RescueProcess.consumeKillRequestIfNonceMatches(tmp, "abc-123"))
        assertFalse(flagFile(tmp).exists())
        assertFalse(File(flagFile(tmp).path + ".consumed").exists())
    }

    // ──────────────────────────────────────────────────────────────────────
    // Alive marker (main-process liveness, the only reliable signal from 31 on)
    // ──────────────────────────────────────────────────────────────────────

    private fun markerFile(dir: File) = File(dir, RescueProcess.ALIVE_MARKER_NAME)

    @Test
    fun `touching the alive marker creates a fresh marker that reads fresh`(@TempDir tmp: File) {
        RescueProcess.touchMainAliveMarker(tmp, "alive pid=42 at=123456")

        val marker = markerFile(tmp)
        assertTrue(marker.exists(), "the marker must exist after a touch")
        assertEquals("alive pid=42 at=123456\n", marker.readText())
        assertTrue(RescueProcess.isMainAliveMarkerFresh(tmp), "a just-touched marker must read fresh")
    }

    @Test
    fun `a marker older than the threshold reads stale`(@TempDir tmp: File) {
        val now = System.currentTimeMillis()
        RescueProcess.touchMainAliveMarker(tmp, "alive pid=42 at=1")
        // The main process died a while ago: its marker stopped being refreshed long before.
        markerFile(tmp).setLastModified(now - 20_000)

        assertFalse(
            RescueProcess.isMainAliveMarkerFresh(tmp, now),
            "a stale marker must read as a dead main process"
        )
    }

    @Test
    fun `a marker refreshed exactly at the threshold still reads fresh`(@TempDir tmp: File) {
        val now = System.currentTimeMillis()
        RescueProcess.touchMainAliveMarker(tmp, "alive pid=42 at=1")
        // 15 s is the staleness threshold (3 x the 5 s refresh cadence): the boundary is
        // inclusive, so the ticks a background-throttled process may miss still read alive.
        markerFile(tmp).setLastModified(now - 15_000)

        assertTrue(
            RescueProcess.isMainAliveMarkerFresh(tmp, now),
            "the threshold is inclusive: a marker at exactly 15 s must still read fresh"
        )
    }

    @Test
    fun `a marker with a future mtime reads stale`(@TempDir tmp: File) {
        val now = System.currentTimeMillis()
        RescueProcess.touchMainAliveMarker(tmp, "alive pid=42 at=1")
        // The device clock was rewound (or NTP-corrected) after the last refresh: the
        // marker's mtime now sits in the future relative to "now".
        markerFile(tmp).setLastModified(now + 60_000)

        assertFalse(
            RescueProcess.isMainAliveMarkerFresh(tmp, now),
            "a future mtime must read stale: a dead process must not read alive indefinitely"
        )
    }

    @Test
    fun `a missing marker reads stale`(@TempDir tmp: File) {
        assertFalse(
            RescueProcess.isMainAliveMarkerFresh(tmp),
            "without a marker there is no main process to report alive"
        )
    }

    @Test
    fun `re-touching a stale marker makes it fresh again`(@TempDir tmp: File) {
        val now = System.currentTimeMillis()
        RescueProcess.touchMainAliveMarker(tmp, "alive pid=42 at=1")
        markerFile(tmp).setLastModified(now - 20_000)
        assertFalse(RescueProcess.isMainAliveMarkerFresh(tmp, now))

        // The main process is still alive: its next background tick refreshes the marker.
        RescueProcess.touchMainAliveMarker(tmp, "alive pid=42 at=2")
        assertTrue(RescueProcess.isMainAliveMarkerFresh(tmp, now + 1_000))
    }
}
