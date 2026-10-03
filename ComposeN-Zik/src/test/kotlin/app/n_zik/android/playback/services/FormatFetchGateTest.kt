package app.n_zik.android.playback.services

import java.util.Collections
import java.util.concurrent.CyclicBarrier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Issue #881 (Phase 3, Fix A): [FormatFetchGate] — the per-video gate behind
 * `StreamResolver.fetchFormatIfMissing`. A video id is marked in-flight the moment the
 * fire-and-forget fetch starts (not when it finishes), so rapid data-source re-opens (one per
 * seek) can no longer re-launch redundant player-response fetches.
 */
class FormatFetchGateTest {

    @Test
    fun `tryMark succeeds once per video id`() {
        val gate = FormatFetchGate()
        assertTrue(gate.tryMark("abc123xyzAB"))
        assertFalse(gate.tryMark("abc123xyzAB"))
    }

    @Test
    fun `different video ids are independent`() {
        val gate = FormatFetchGate()
        assertTrue(gate.tryMark("idOne123456"))
        assertTrue(gate.tryMark("idTwo789012"))
        assertFalse(gate.tryMark("idOne123456"))
    }

    @Test
    fun `release allows the next open to retry`() {
        val gate = FormatFetchGate()
        assertTrue(gate.tryMark("abc123xyzAB"))
        gate.release("abc123xyzAB")
        assertTrue(gate.tryMark("abc123xyzAB"))
    }

    @Test
    fun `release of an unmarked id is a no-op`() {
        val gate = FormatFetchGate()
        gate.release("neverMarked00")
        assertTrue(gate.tryMark("neverMarked00"))
    }

    @Test
    fun `clear empties the gate`() {
        val gate = FormatFetchGate()
        gate.tryMark("abc123xyzAB")
        gate.tryMark("idTwo789012")
        gate.clear()
        assertTrue(gate.tryMark("abc123xyzAB"))
        assertTrue(gate.tryMark("idTwo789012"))
    }

    @Test
    fun `concurrent tryMark yields exactly one winner`() {
        val threadCount = 32
        val gate = FormatFetchGate()
        val barrier = CyclicBarrier(threadCount)
        val winners = Collections.synchronizedList(mutableListOf<Boolean>())
        val threads = List(threadCount) {
            Thread {
                barrier.await()
                winners.add(gate.tryMark("abc123xyzAB"))
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(1, winners.count { it }, "exactly one thread may own the fetch")
    }
}
