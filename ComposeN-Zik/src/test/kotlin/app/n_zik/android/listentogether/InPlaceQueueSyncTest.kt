package app.n_zik.android.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InPlaceQueueSyncTest {

    @Test
    fun `target already loaded - seeks within queue and trims played head`() {
        val plan = computeInPlaceQueueSyncPlan(
            loadedIds = listOf("A", "B", "C"),
            hostQueueIds = listOf("B", "C"),
        )
        assertEquals(1, plan?.targetIndex)
        assertEquals(0, plan?.appendCount)
        assertEquals(1, plan?.headToRemove)
        assertTrue(plan?.tailMatches ?: false)
    }

    @Test
    fun `target already loaded - stale tail must be realigned`() {
        val plan = computeInPlaceQueueSyncPlan(
            loadedIds = listOf("A", "B", "C"),
            hostQueueIds = listOf("B", "D"),
        )
        assertEquals(1, plan?.targetIndex)
        assertFalse(plan?.tailMatches ?: true)
    }

    @Test
    fun `target already loaded as current - no head trim, tail from host wins`() {
        val plan = computeInPlaceQueueSyncPlan(
            loadedIds = listOf("B", "C"),
            hostQueueIds = listOf("B"),
        )
        assertEquals(0, plan?.targetIndex)
        assertEquals(0, plan?.headToRemove)
        assertFalse(plan?.tailMatches ?: true)
    }

    @Test
    fun `target not loaded - host queue is appended at the end`() {
        val plan = computeInPlaceQueueSyncPlan(
            loadedIds = listOf("A"),
            hostQueueIds = listOf("X", "Y"),
        )
        assertEquals(1, plan?.targetIndex)
        assertEquals(2, plan?.appendCount)
        assertEquals(1, plan?.headToRemove)
        assertTrue(plan?.tailMatches ?: false)
    }

    @Test
    fun `empty player queue - plan appends at index zero`() {
        val plan = computeInPlaceQueueSyncPlan(
            loadedIds = emptyList(),
            hostQueueIds = listOf("X"),
        )
        assertEquals(0, plan?.targetIndex)
        assertEquals(1, plan?.appendCount)
        assertEquals(0, plan?.headToRemove)
        assertTrue(plan?.tailMatches ?: false)
    }

    @Test
    fun `empty host queue - no in-place plan`() {
        assertNull(computeInPlaceQueueSyncPlan(listOf("A"), emptyList()))
    }

    // Bug 2026-09-26 ("timeline cassée"): the player was left IDLE because queue items
    // were removed while a window seek to the target was still pending. These two helpers
    // pin the mutation-order invariants that fix it.

    @Test
    fun `tail removal covers the stale items after the target and nothing else`() {
        assertEquals(4..9, tailRemovalIndices(loadedCount = 10, targetIndex = 3))
        assertEquals(listOf(1, 2, 3), tailRemovalIndices(loadedCount = 4, targetIndex = 0).toList())
    }

    @Test
    fun `tail removal never touches the target or the played head`() {
        assertTrue(tailRemovalIndices(loadedCount = 4, targetIndex = 3).isEmpty())
        assertTrue(tailRemovalIndices(loadedCount = 1, targetIndex = 0).isEmpty())
        val indices = tailRemovalIndices(loadedCount = 10, targetIndex = 3)
        assertTrue(indices.none { it <= 3 })
    }

    @Test
    fun `head trims immediately only when the target is already the current item`() {
        assertTrue(canTrimHeadImmediately(targetIndex = 0, currentMediaItemIndex = 0))
        assertTrue(canTrimHeadImmediately(targetIndex = 2, currentMediaItemIndex = 2))
        // Transition in flight: the head includes the item that was playing — must be deferred.
        assertFalse(canTrimHeadImmediately(targetIndex = 1, currentMediaItemIndex = 0))
        // Player idle (no current item): defer; the fallback path settles the trim.
        assertFalse(canTrimHeadImmediately(targetIndex = 0, currentMediaItemIndex = -1))
    }
}
