package app.n_zik.android.core.matching

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.models.Song
import app.n_zik.android.utils.SongMatchOutcome

class SongMatchTest {
    private val owner = ProfileStorageIdentity("default", "legacy")

    @Test
    fun `an old notification cannot cancel or dismiss a newer request`() {
        val sessions = SongMatchSessions { it == owner }
        val first = run(id = "first")
        val second = run(id = "second")
        assertSame(first, sessions.reserve(first))
        sessions.accept(first)
        assertNull(sessions.reserve(second))
        assertSame(first, sessions.claim(first.id))
        sessions.finish(first, first.initialState.copy(status = SongMatchStatus.Finished))

        assertSame(second, sessions.reserve(second))
        sessions.accept(second)
        assertNull(sessions.claim(first.id))
        sessions.cancel(first.id)
        sessions.dismiss(first.id)
        sessions.report(first.id, processed = 1, merged = 1)
        assertEquals(second.initialState, sessions.state.value)
    }

    @Test
    fun `cancellation preserves retry candidates and suppresses the automatic remainder after dismissal`() {
        val sessions = SongMatchSessions { it == owner }
        val cancelled = run(id = "cancelled", automatic = true, ids = listOf("matched", "failed", "waiting"))
        assertSame(cancelled, sessions.reserve(cancelled))
        sessions.accept(cancelled)
        sessions.cancel(cancelled.id)
        assertEquals(SongMatchStatus.Cancelling, sessions.state.value?.status)

        val result = songMatchResult(
            cancelled,
            outcomes = mapOf(
                "matched" to SongMatchOutcome.Merged,
                "failed" to SongMatchOutcome.Unmatched("renamed"),
            ),
            cancelled = true,
        )
        sessions.finish(cancelled, result)
        assertEquals(SongMatchStatus.Cancelled, result.status)
        assertEquals(2, result.processed)
        assertEquals(1, result.matched)
        assertEquals(1, result.failed)
        assertEquals(1, result.unprocessed)
        assertEquals(1, result.merged)
        assertEquals(setOf("renamed", "waiting"), result.failedIds)
        sessions.dismiss(cancelled.id)
        assertNull(sessions.state.value)
        assertNull(sessions.reserve(run(id = "automatic", automatic = true, ids = listOf("waiting"))))
        val next = checkNotNull(
            sessions.reserve(run(id = "new-automatic", automatic = true, ids = listOf("waiting", "new"))),
        )
        assertEquals(listOf("new"), next.request.songs.map(Song::id))
        assertEquals(1, next.initialState.total)
        sessions.abandon(next)
        assertNull(sessions.reserve(run(id = "rejected-automatic", automatic = true, ids = listOf("new"))))
        assertNotNull(sessions.reserve(run(id = "manual", ids = listOf("waiting", "new"))))
    }

    @Test
    fun `a storage generation change hides stale progress and rejects the old request`() {
        var boundOwner = owner
        val sessions = SongMatchSessions { it == boundOwner }
        val first = run(id = "first", automatic = true)
        assertSame(first, sessions.reserve(first))
        sessions.accept(first)

        boundOwner = owner.copy(generation = "replacement")
        sessions.invalidateUnboundState()
        sessions.cancel(first.id)
        sessions.report(first.id, processed = 1, merged = 1)
        sessions.finish(first, first.initialState.copy(status = SongMatchStatus.Cancelled))
        assertNull(sessions.state.value)
        assertNull(sessions.reserve(first))
        assertNotNull(sessions.reserve(run(id = "replacement", automatic = true, owner = boundOwner)))
    }

    @Test
    fun `a system interruption publishes failed status without replacing completed counters`() {
        val sessions = SongMatchSessions { it == owner }
        val timedOut = run(id = "timed-out", ids = listOf("matched", "waiting"))
        assertSame(timedOut, sessions.reserve(timedOut))
        sessions.accept(timedOut)
        sessions.report(timedOut.id, processed = 1, merged = 1)
        sessions.report(timedOut.id, processed = 0, merged = 0)
        assertEquals(1, sessions.state.value?.processed)
        assertEquals(1, sessions.state.value?.merged)

        sessions.cancel(timedOut.id, failed = true)
        sessions.finish(timedOut, songMatchResult(timedOut, mapOf("matched" to SongMatchOutcome.Matched), true))
        assertEquals(SongMatchStatus.Failed, sessions.state.value?.status)
        assertEquals(1, sessions.state.value?.matched)
        assertEquals(setOf("waiting"), sessions.state.value?.failedIds)
    }

    @Test
    fun `a new playlist preserves the result of the previous playlist`() {
        val sessions = SongMatchSessions { it == owner }
        val first = run(id = "first")
        val second = run(id = "second", scopeKey = "playlist:2", automatic = true)
        assertSame(first, sessions.reserve(first))
        sessions.accept(first)
        sessions.finish(first, first.initialState.copy(status = SongMatchStatus.Finished))

        assertSame(second, sessions.reserve(second))
        sessions.accept(second)
        assertEquals(first.id, sessions.results.value[first.request.scopeKey]?.requestId)
        sessions.dismiss(first.id)
        assertEquals(second.initialState, sessions.state.value)
        assertTrue(sessions.results.value.isEmpty())
    }

    @Test
    fun `a skipped candidate stays eligible for manual retry`() {
        val skipped = run(id = "skipped", ids = listOf("skipped"))
        val result = songMatchResult(skipped, mapOf("skipped" to SongMatchOutcome.Skipped), false)

        assertEquals(0, result.processed)
        assertEquals(0, result.matched)
        assertEquals(0, result.failed)
        assertEquals(1, result.unprocessed)
        assertEquals(setOf("skipped"), result.failedIds)
    }

    @Test
    fun `reconciliation failure retains completed results and exact retry candidates`() = runTest {
        val request = run(id = "reconcile", ids = listOf("matched", "pending"))
        val result = songMatchResult(request, mapOf("matched" to SongMatchOutcome.Merged), true)

        val failed = reconcileSongMatch(result) { error("Simulated reconciliation failure") }

        assertEquals(result.copy(status = SongMatchStatus.Failed), failed)
    }

    private fun run(
        id: String,
        automatic: Boolean = false,
        ids: List<String> = listOf("title"),
        owner: ProfileStorageIdentity = this.owner,
        scopeKey: String = "playlist:1",
    ) = SongMatchRun(
        id,
        owner,
        SongMatchRequest(scopeKey, ids.map { Song.makePlaceholder(it) }, playlistId = 1L, automatic = automatic),
    )
}
