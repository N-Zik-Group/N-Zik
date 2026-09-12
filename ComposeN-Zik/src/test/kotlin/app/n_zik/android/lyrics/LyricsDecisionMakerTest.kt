package app.n_zik.android.lyrics

import app.n_zik.android.components.player.lyrics.LyricsDecisionMaker
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.models.Lyrics
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LyricsDecisionMakerTest {

    @Test
    fun `when wanting karaoke and database is empty, it needs karaoke fetch`() {
        val needs = LyricsDecisionMaker.evaluateFetchNeeds(
            mediaId = "song1",
            lyricsType = LyricsType.Karaoke,
            allLyrics = emptyList(),
            globalLastKaraokeAttemptMediaId = null,
            globalLastSyncedAttemptMediaId = null,
            globalLastUnSyncedAttemptMediaId = null
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
            globalLastUnSyncedAttemptMediaId = null
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
            globalLastUnSyncedAttemptMediaId = null
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
            globalLastUnSyncedAttemptMediaId = null
        )

        assertTrue(needs.needSyncedFetch)
        assertTrue(needs.needKaraokeFetch)
        assertNull(needs.currentLyrics)
    }

}
