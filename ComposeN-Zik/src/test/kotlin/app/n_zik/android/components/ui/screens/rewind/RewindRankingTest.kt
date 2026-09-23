package app.n_zik.android.components.ui.screens.rewind

import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.core.database.ext.SongListeningStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for [rankTopSongs], the single shared top-song comparator of the deck (top 10,
 * top 5, deep cuts, finale) and the home collages: play count first, then minutes listened,
 * then song id — so the home's #1 can never disagree with the deck's #1 on a tie (spec
 * GH-275, patch "Top-song tie-break"). No truncation: callers take what they need.
 */
class RewindRankingTest {

    private fun stat(id: String, playCount: Long, playTimeMs: Long) = SongListeningStat(
        song = Song.makePlaceholder(id),
        playCount = playCount,
        playTimeMs = playTimeMs
    )

    @Test
    fun ranksByPlayCountThenMinutesThenSongId() {
        val stats = listOf(
            stat("c", 5, 100_000L), // ties with d on count and minutes → smaller id first
            stat("a", 5, 300_000L), // same count as c but more minutes → ranks above
            stat("b", 7, 0L),       // most plays → #1
            stat("d", 5, 100_000L)
        )
        val ranked = rankTopSongs(stats)
        assertEquals(listOf("b", "a", "c", "d"), ranked.map { it.song.id })
    }

    @Test
    fun mapsWindowedMinutesAndPlayCount() {
        val ranked = rankTopSongs(listOf(stat("s", 3, 240_000L)))
        assertEquals(4L, ranked.first().minutes)
        assertEquals(3, ranked.first().playCount)
    }

    @Test
    fun neverTruncatesTheResult() {
        val ranked = rankTopSongs((1..20).map { index -> stat("s$index", index.toLong(), 0L) })
        assertEquals(20, ranked.size)
        assertEquals(20, ranked.first().playCount)
    }

    @Test
    fun emptyInputStaysEmpty() {
        assertTrue(rankTopSongs(emptyList()).isEmpty())
    }
}
