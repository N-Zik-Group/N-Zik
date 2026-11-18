package app.n_zik.android.lyrics

import app.n_zik.android.components.player.lyrics.LyricsDecisionMaker
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.models.Lyrics
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LyricsDecisionMakerTest {

    private val NOW = 1_700_000_000_000L
    private val TTL = LyricsDecisionMaker.REFRESH_TTL_MILLIS

    private val wordTimedData = "[00:01.00]Hello\n<Hello:1.0:1.5|world:1.5:2.0>"

    @Test
    fun `when wanting karaoke and database is empty, it needs karaoke fetch`() {
        val needs = LyricsDecisionMaker.evaluateFetchNeeds(
            mediaId = "song1",
            lyricsType = LyricsType.Karaoke,
            allLyrics = emptyList(),
            globalLastKaraokeAttemptMediaId = null,
            globalLastSyncedAttemptMediaId = null,
            globalLastUnSyncedAttemptMediaId = null,
            now = NOW
        )

        assertTrue(needs.needKaraokeFetch)
        assertFalse(needs.needSyncedFetch)
        assertNull(needs.currentLyrics)
    }

    @Test
    fun `when wanting karaoke and karaoke has no timings, but already attempted, it skips fetch`() {
        // Simulating the fallback behavior where BetterLyrics Sync was stored in Karaoke slot
        val fallbackLyrics = Lyrics(
            songId = "song1",
            type = LyricsType.Karaoke.name,
            data = "[00:10.00] Sync line 1" // No '<' timings
        )

        val needs = LyricsDecisionMaker.evaluateFetchNeeds(
            mediaId = "song1",
            lyricsType = LyricsType.Karaoke,
            allLyrics = listOf(fallbackLyrics),
            globalLastKaraokeAttemptMediaId = "song1", // Already attempted in this session
            globalLastSyncedAttemptMediaId = null,
            globalLastUnSyncedAttemptMediaId = null,
            now = NOW
        )

        // It does not have word timings, but because we already attempted it, needKaraokeFetch should be false
        assertFalse(needs.needKaraokeFetch)
        assertEquals(fallbackLyrics, needs.currentLyrics)
    }

    @Test
    fun `when wanting karaoke and karaoke has no timings and NOT attempted yet, it needs fetch`() {
        val fallbackLyrics = Lyrics(
            songId = "song1",
            type = LyricsType.Karaoke.name,
            data = "[00:10.00] Sync line 1" // No '<' timings
        )

        val needs = LyricsDecisionMaker.evaluateFetchNeeds(
            mediaId = "song1",
            lyricsType = LyricsType.Karaoke,
            allLyrics = listOf(fallbackLyrics),
            globalLastKaraokeAttemptMediaId = null, // NOT attempted yet (e.g. app restart)
            globalLastSyncedAttemptMediaId = null,
            globalLastUnSyncedAttemptMediaId = null,
            now = NOW
        )

        assertTrue(needs.needKaraokeFetch)
        assertEquals(fallbackLyrics, needs.currentLyrics)
    }

    @Test
    fun `when wanting synced and database is empty, it needs synced fetch`() {
        val needs = LyricsDecisionMaker.evaluateFetchNeeds(
            mediaId = "song1",
            lyricsType = LyricsType.Synced,
            allLyrics = emptyList(),
            globalLastKaraokeAttemptMediaId = null,
            globalLastSyncedAttemptMediaId = null,
            globalLastUnSyncedAttemptMediaId = null,
            now = NOW
        )

        assertTrue(needs.needSyncedFetch)
        assertTrue(needs.needKaraokeFetch)
        assertNull(needs.currentLyrics)
    }

    private fun needsFor(
        lyricsType: LyricsType,
        allLyrics: List<Lyrics>,
        now: Long = NOW,
        forceAutoRetry: Boolean = false,
    ) = LyricsDecisionMaker.evaluateFetchNeeds(
        mediaId = "song1",
        lyricsType = lyricsType,
        allLyrics = allLyrics,
        globalLastKaraokeAttemptMediaId = "otherSong",
        globalLastSyncedAttemptMediaId = "otherSong",
        globalLastUnSyncedAttemptMediaId = "otherSong",
        now = now,
        forceAutoRetry = forceAutoRetry,
    )

    @Test
    fun `word-timed row younger than the TTL needs no fetch`() {
        val fresh = Lyrics("song1", LyricsType.Karaoke.name, wordTimedData, lastFetchedAt = NOW - 10L * 24 * 60 * 60 * 1000)

        val needs = needsFor(LyricsType.Karaoke, listOf(fresh))

        assertFalse(needs.needKaraokeFetch)
        assertSame(fresh, needs.currentLyrics)
    }

    @Test
    fun `word-timed row at or beyond the TTL is re-fetched`() {
        val atTtl = Lyrics("song1", LyricsType.Karaoke.name, wordTimedData, lastFetchedAt = NOW - TTL)
        val pastTtl = Lyrics("song1", LyricsType.Karaoke.name, wordTimedData, lastFetchedAt = NOW - TTL - 1)

        assertTrue(needsFor(LyricsType.Karaoke, listOf(atTtl)).needKaraokeFetch)
        assertTrue(needsFor(LyricsType.Karaoke, listOf(pastTtl)).needKaraokeFetch)
    }

    @Test
    fun `word-timed row without lastFetchedAt is treated as expired`() {
        // Rows stored before the column existed: fetched once, then the write stamps the clock.
        val legacy = Lyrics("song1", LyricsType.Karaoke.name, wordTimedData)

        assertTrue(needsFor(LyricsType.Karaoke, listOf(legacy)).needKaraokeFetch)
    }

    @Test
    fun `edited row is never re-fetched by its own check, even past the TTL`() {
        val cases = mapOf(
            LyricsType.Auto to LyricsType.Unsynced,
            LyricsType.Karaoke to LyricsType.Karaoke,
            LyricsType.Synced to LyricsType.Synced,
            LyricsType.Unsynced to LyricsType.Unsynced
        )
        cases.forEach { (mode, storedType) ->
            val edited = Lyrics("song1", storedType.name, wordTimedData, isEdited = true, lastFetchedAt = NOW - TTL - 1)

            val needs = needsFor(mode, listOf(edited))

            when (storedType) {
                LyricsType.Karaoke -> assertFalse(needs.needKaraokeFetch, "karaoke check in $mode")
                LyricsType.Synced -> assertFalse(needs.needSyncedFetch, "synced check in $mode")
                else -> assertFalse(needs.needUnsyncedFetch, "unsynced check in $mode")
            }
            assertSame(edited, needs.currentLyrics)
        }
    }

    @Test
    fun `Auto uses edited lyrics without looking for alternatives`() {
        val edited = Lyrics("song1", LyricsType.Unsynced.name, "my own text", isEdited = true, lastFetchedAt = NOW - TTL - 1)

        val needs = needsFor(LyricsType.Auto, listOf(edited))

        assertFalse(needs.needKaraokeFetch)
        assertFalse(needs.needSyncedFetch)
        assertFalse(needs.needUnsyncedFetch)
        assertSame(edited, needs.currentLyrics)
    }

    @Test
    fun `Auto uses fresh Karaoke without maintaining missing fallbacks`() {
        val karaoke = Lyrics("song1", LyricsType.Karaoke.name, wordTimedData, lastFetchedAt = NOW - 10L * 24 * 60 * 60 * 1000)

        val needs = needsFor(LyricsType.Auto, listOf(karaoke))

        assertFalse(needs.needKaraokeFetch)
        assertFalse(needs.needSyncedFetch)
        assertFalse(needs.needUnsyncedFetch)
        assertSame(karaoke, needs.currentLyrics)
    }

    @Test
    fun `Synced mode with only a Karaoke fallback row keeps fetching the real Synced`() {
        val karaoke = Lyrics("song1", LyricsType.Karaoke.name, wordTimedData, lastFetchedAt = NOW - 1)

        val needs = needsFor(LyricsType.Synced, listOf(karaoke))

        assertTrue(needs.needSyncedFetch) // upgrade hunt: the displayed row is the Karaoke fallback
        assertFalse(needs.needKaraokeFetch) // fresh word-timed row: TTL not expired
        assertSame(karaoke, needs.currentLyrics)
    }

    @Test
    fun `Karaoke hunt stays off in Synced mode when the Synced row already has word timings`() {
        val synced = Lyrics("song1", LyricsType.Synced.name, wordTimedData, lastFetchedAt = NOW - 1)

        val needs = needsFor(LyricsType.Synced, listOf(synced))

        assertFalse(needs.needKaraokeFetch) // word timings already served: no separate Karaoke row wanted
        assertFalse(needs.needSyncedFetch) // fresh word-timed row: TTL not expired
        assertSame(synced, needs.currentLyrics)
    }

    @Test
    fun `Auto uses fresh plain text without querying timed providers`() {
        val unsynced = Lyrics("song1", LyricsType.Unsynced.name, "plain text", lastFetchedAt = NOW - 1)

        val needs = needsFor(LyricsType.Auto, listOf(unsynced))

        assertFalse(needs.needKaraokeFetch)
        assertFalse(needs.needSyncedFetch)
        assertFalse(needs.needUnsyncedFetch)
        assertSame(unsynced, needs.currentLyrics)
    }

    @Test
    fun `unsynced row without word timings is re-fetched only after the TTL`() {
        val fresh = Lyrics("song1", LyricsType.Unsynced.name, "plain text", lastFetchedAt = NOW - TTL / 2)
        val expired = Lyrics("song1", LyricsType.Unsynced.name, "plain text", lastFetchedAt = NOW - TTL - 1)
        val legacy = Lyrics("song1", LyricsType.Unsynced.name, "plain text")

        assertFalse(needsFor(LyricsType.Unsynced, listOf(fresh)).needUnsyncedFetch)
        assertTrue(needsFor(LyricsType.Unsynced, listOf(expired)).needUnsyncedFetch)
        assertTrue(needsFor(LyricsType.Unsynced, listOf(legacy)).needUnsyncedFetch)
    }

    @Test
    fun `Auto revalidates the expired display format only`() {
        val stored = Lyrics("song1", LyricsType.Unsynced.name, "plain text")

        val needs = needsFor(LyricsType.Auto, listOf(stored))

        assertFalse(needs.needKaraokeFetch)
        assertFalse(needs.needSyncedFetch)
        assertTrue(needs.needUnsyncedFetch) // no stamp yet: treated as expired, fetched once
        assertSame(stored, needs.currentLyrics)
    }

    @Test
    fun `explicit Auto retry bypasses a fresh cached result`() {
        val stored = Lyrics("song1", LyricsType.Synced.name, "[00:01.00]text", lastFetchedAt = NOW)

        val needs = needsFor(LyricsType.Auto, listOf(stored), forceAutoRetry = true)

        assertTrue(needs.needKaraokeFetch)
        assertTrue(needs.needSyncedFetch)
        assertTrue(needs.needUnsyncedFetch)
        assertSame(stored, needs.currentLyrics)
    }

    @Test
    fun `each mode activates exactly its documented checks when the database is empty`() {
        // Pins the off-side of the per-mode active sets: Karaoke -> K, Synced -> K+S,
        // Unsynced -> U, Auto -> K+S+U. Widen any mode set and one of these fails.
        val karaoke = needsFor(LyricsType.Karaoke, emptyList())
        assertTrue(karaoke.needKaraokeFetch)
        assertFalse(karaoke.needSyncedFetch)
        assertFalse(karaoke.needUnsyncedFetch)

        val synced = needsFor(LyricsType.Synced, emptyList())
        assertTrue(synced.needKaraokeFetch)
        assertTrue(synced.needSyncedFetch)
        assertFalse(synced.needUnsyncedFetch)

        val unsynced = needsFor(LyricsType.Unsynced, emptyList())
        assertFalse(unsynced.needKaraokeFetch)
        assertFalse(unsynced.needSyncedFetch)
        assertTrue(unsynced.needUnsyncedFetch)

        val auto = needsFor(LyricsType.Auto, emptyList())
        assertTrue(auto.needKaraokeFetch)
        assertTrue(auto.needSyncedFetch)
        assertTrue(auto.needUnsyncedFetch)
    }

    @Test
    fun `a stamp in the future (clock rolled back) is treated as expired`() {
        val future = Lyrics("song1", LyricsType.Karaoke.name, wordTimedData, lastFetchedAt = NOW + 24 * 60 * 60 * 1000)

        assertTrue(needsFor(LyricsType.Karaoke, listOf(future)).needKaraokeFetch)
    }

    @Test
    fun `emptied edit is treated as no lyrics and allows fetching again`() {
        listOf<String?>("", null).forEach { emptied ->
            val edited = Lyrics("song1", LyricsType.Unsynced.name, emptied, isEdited = true)

            val needs = needsFor(LyricsType.Auto, listOf(edited))

            assertTrue(needs.needKaraokeFetch)
            assertTrue(needs.needSyncedFetch)
            assertTrue(needs.needUnsyncedFetch)
        }
    }

    @Test
    fun `edited karaoke row displayed as Synced fallback is never re-fetched itself`() {
        val edited = Lyrics("song1", LyricsType.Karaoke.name, "[00:10.00] mine", isEdited = true)

        val needs = needsFor(LyricsType.Synced, listOf(edited))

        assertFalse(needs.needKaraokeFetch)
        assertSame(edited, needs.currentLyrics)
    }

}
