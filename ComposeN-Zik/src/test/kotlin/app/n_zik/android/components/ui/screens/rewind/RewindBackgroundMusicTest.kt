package app.n_zik.android.components.ui.screens.rewind

import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Contract of the Rewind deck's background-music logic (spec 3): pool construction
 * (downloaded-first, seeded deterministic shuffle, cap 30, distinct, blank ids dropped),
 * cyclic card → track assignment, the 800 ms settle debounce, the start offset (downloaded:
 * triangular mid-bias bounded `duration - 20 s`; streamed: always 0; unknown/short: 0) and the
 * yield state machine against the main player. Pure — no player, no UI.
 */
class RewindBackgroundMusicTest {

    private fun song(id: String) = Song(
        id = id,
        title = "T-$id",
        durationText = null,
        thumbnailUrl = null
    )

    // ── Pool construction ────────────────────────────────────────────────────────────

    @Test
    fun poolPutsDownloadedFirst() {
        val songs = listOf(song("s1"), song("s2"), song("d1"), song("d2"))
        val pool = buildBackgroundPool(
            songs,
            isDownloaded = { it == "d1" || it == "d2" },
            random = Random(1)
        )
        assertEquals(setOf("d1", "d2"), pool.take(2).map { it.id }.toSet())
        assertEquals(setOf("s1", "s2"), pool.drop(2).map { it.id }.toSet())
    }

    @Test
    fun poolCapsAtThirty() {
        val songs = (0 until 40).map { song("s$it") }
        val pool = buildBackgroundPool(songs, isDownloaded = { false }, random = Random(2))
        assertEquals(30, pool.size)
        // No track left the source list (cap keeps the first 30 of the shuffled session)
        assertTrue(pool.all { s -> songs.any { it.id == s.id } })
    }

    @Test
    fun poolDropsDuplicatesAndBlankIds() {
        val songs = listOf(song("a"), song("a"), song("b"), song(" "))
        val pool = buildBackgroundPool(songs, isDownloaded = { false }, random = Random(3))
        assertEquals(setOf("a", "b"), pool.map { it.id }.toSet())
    }

    @Test
    fun poolShuffleIsDeterministicForASeed() {
        val songs = (0 until 10).map { song("s$it") }
        val first = buildBackgroundPool(songs, isDownloaded = { false }, random = Random(42))
        val second = buildBackgroundPool(songs, isDownloaded = { false }, random = Random(42))
        assertEquals(first.map { it.id }, second.map { it.id })
    }

    // ── Card → track assignment ──────────────────────────────────────────────────────

    @Test
    fun trackForPageIsCyclic() {
        val pool = (0 until 4).map { song("t$it") }
        assertEquals("t0", trackForPage(pool, 0)?.id)
        assertEquals("t3", trackForPage(pool, 3)?.id)
        assertEquals("t0", trackForPage(pool, 4)?.id)
        assertEquals("t2", trackForPage(pool, 10)?.id) // 10 mod 4 = 2
    }

    @Test
    fun trackForPageIsNullForAnEmptyPool() {
        assertNull(trackForPage(emptyList(), 0))
    }

    // ── Debounce ─────────────────────────────────────────────────────────────────────

    @Test
    fun debounceSkipsFastSettlesAndSwitchesPastTheGap() {
        assertFalse(shouldSwitchTrack(799L))
        assertTrue(shouldSwitchTrack(800L))
        assertTrue(shouldSwitchTrack(5_000L))
    }

    // ── Start offset ─────────────────────────────────────────────────────────────────

    @Test
    fun streamedTracksAlwaysStartAtZero() {
        val random = Random(7)
        repeat(50) {
            assertEquals(0L, startOffset(240_000L, isDownloaded = false, random))
        }
    }

    @Test
    fun downloadedOffsetsStayWithinTheSafeBound() {
        val random = Random(8)
        repeat(500) {
            val offset = startOffset(240_000L, isDownloaded = true, random)
            assertTrue("offset $offset out of bounds", offset in 0L..220_000L)
        }
    }

    @Test
    fun downloadedOffsetsBiasTowardTheMiddle() {
        val random = Random(9)
        val duration = 240_000L // a 4-minute track
        val hi = duration - REWIND_BGM_MIN_REMAINING_MS
        val offsets = (0 until 4000).map { startOffset(duration, isDownloaded = true, random) }
        val mean = offsets.sum().toDouble() / offsets.size
        // Triangular peak at the middle of [0, duration - 20 s]
        assertTrue("mean $mean should sit near ${hi / 2}", abs(mean - hi / 2.0) < 10_000.0)
        val middleThird = offsets.count { it in hi / 3..2 * hi / 3 }
        val edges = offsets.size - middleThird
        assertTrue("middle third $middleThird should outweigh the edges $edges", middleThird > edges)
    }

    @Test
    fun shortOrUnknownDurationStartsAtZero() {
        val random = Random(10)
        assertEquals(0L, startOffset(0L, isDownloaded = true, random))
        assertEquals(0L, startOffset(-1L, isDownloaded = true, random)) // C.TIME_UNSET
        assertEquals(0L, startOffset(REWIND_BGM_MIN_REMAINING_MS, isDownloaded = true, random))
        assertEquals(0L, startOffset(5_000L, isDownloaded = true, random))
    }

    // ── Yield state machine ──────────────────────────────────────────────────────────

    @Test
    fun yieldPausesWhenTheMainPlayerStarts() {
        assertEquals(BgmYieldDecision.PauseAll, bgmYieldDecision(wasMainPlaying = false, isMainPlaying = true))
    }

    @Test
    fun yieldResumesWhenTheMainPlayerStops() {
        assertEquals(BgmYieldDecision.ResumeActive, bgmYieldDecision(wasMainPlaying = true, isMainPlaying = false))
    }

    @Test
    fun yieldIsInertWithoutAStateChange() {
        assertEquals(BgmYieldDecision.None, bgmYieldDecision(wasMainPlaying = false, isMainPlaying = false))
        assertEquals(BgmYieldDecision.None, bgmYieldDecision(wasMainPlaying = true, isMainPlaying = true))
    }

    // ── Content → track mapping (content-tied cards, pool fallback on stats cards) ──

    private fun contentSong(id: String, artists: String) = TopSong(
        song = Song(
            id = id,
            title = "T-$id",
            durationText = null,
            thumbnailUrl = null,
            artistsText = artists
        ),
        minutes = 10,
        playCount = 3
    )

    private fun deckData(
        topSongs: List<TopSong> = emptyList(),
        topArtists: List<TopArtist> = emptyList(),
        topAlbums: List<TopAlbum> = emptyList()
    ): RewindData = RewindData(
        topSongs = topSongs,
        topArtists = topArtists,
        topAlbums = topAlbums,
        topPlaylists = emptyList(),
        stats = ListeningStats(
            totalPlays = 0,
            totalMinutes = 0,
            mostActiveDay = null,
            mostActiveHour = null,
            mostActiveMonth = null,
            averageDailyMinutes = 0.0,
            firstPlayDate = null,
            lastPlayDate = null
        ),
        monthlyStats = emptyList(),
        dailyStats = emptyList(),
        hourlyStats = emptyList(),
        totalUniqueSongs = topSongs.size,
        totalUniqueArtists = topArtists.size,
        totalUniqueAlbums = topAlbums.size,
        totalUniquePlaylists = 0,
        period = RewindPeriod.Year(2026),
        daysWithMusic = 0,
        periodLabel = "2026",
        daysInPeriod = 365
    )

    private fun songIdOf(track: BgmContentTrack): String? = (track as? BgmContentTrack.SongTrack)?.songId
    private fun albumIdOf(track: BgmContentTrack): String? = (track as? BgmContentTrack.AlbumTrack)?.albumId

    @Test
    fun importantSingleCardsAlwaysPlayTheirNumberOne() {
        val data = deckData(
            topSongs = listOf(
                contentSong("s1", "Artist A"),
                contentSong("s2", "Artist A"),
                contentSong("s3", "Artist B"),
                contentSong("s4", "Artist C"),
                contentSong("s5", "Artist D"),
                contentSong("s6", "Artist E")
            ),
            topArtists = listOf(
                TopArtist(artist = Artist(id = "a1", name = "Artist A"), minutes = 50, songCount = 2),
                TopArtist(artist = Artist(id = "a2", name = "Artist B"), minutes = 30, songCount = 1)
            ),
            topAlbums = listOf(
                TopAlbum(album = Album(id = "al1", title = "One"), minutes = 20, songCount = 10),
                TopAlbum(album = Album(id = "al2", title = "Two"), minutes = 10, songCount = 10)
            )
        )
        // The Top song card, the spotlight and the Top album always play their #1 item…
        assertEquals(BgmContentTrack.SongTrack("s1"), contentTrackForPage(data, page = 3, random = Random(1)))
        assertEquals(BgmContentTrack.SongTrack("s1"), contentTrackForPage(data, page = 5, random = Random(1)))
        assertEquals(BgmContentTrack.AlbumTrack("al1"), contentTrackForPage(data, page = 11, random = Random(1)))
        // …and the finale bookends the deck with the period's #1
        assertEquals(BgmContentTrack.SongTrack("s1"), contentTrackForPage(data, page = 15, random = Random(1)))
    }

    @Test
    fun listCardsRollFromTheFiveItemsTheyDisplay() {
        val data = deckData(
            topSongs = (0 until 8).map { contentSong("s${it + 1}", "Artist ${it + 1}") },
            topArtists = (0 until 5).map {
                TopArtist(artist = Artist(id = "a${it + 1}", name = "Artist ${it + 1}"), minutes = 10, songCount = 1)
            },
            topAlbums = (0 until 3).map {
                TopAlbum(album = Album(id = "al${it + 1}", title = "A${it + 1}"), minutes = 10, songCount = 5)
            }
        )
        // Top songs list: only the five displayed songs are eligible…
        repeat(30) { i ->
            val rolled = songIdOf(contentTrackForPage(data, page = 6, random = Random(i.toLong())))
            assertTrue("rolled $rolled outside the displayed top five", rolled != null && rolled in "s1".."s5")
        }
        // …deep cuts: only the displayed cuts (s6..s8 with eight songs)…
        repeat(30) { i ->
            val rolled = songIdOf(contentTrackForPage(data, page = 7, random = Random(i.toLong())))
            assertTrue("rolled $rolled outside the displayed deep cuts", rolled != null && rolled in "s6".."s8")
        }
        // …top artists list: only the songs of the five displayed artists…
        repeat(30) { i ->
            val rolled = songIdOf(contentTrackForPage(data, page = 4, random = Random(i.toLong())))
            assertTrue("rolled $rolled outside the displayed artists", rolled != null && rolled in "s1".."s5")
        }
        // …and the albums list: only the displayed albums
        repeat(30) { i ->
            val rolled = albumIdOf(contentTrackForPage(data, page = 12, random = Random(i.toLong())))
            assertTrue("rolled $rolled outside the displayed albums", rolled != null && rolled in "al1".."al3")
        }
    }

    @Test
    fun listCardRollsAreDeterministicForASeedAndVaryBetweenVisits() {
        val data = deckData(topSongs = (0 until 5).map { contentSong("s${it + 1}", "Artist ${it + 1}") })
        // Same seed → same item (a resolution is stable while the card is on screen)
        assertEquals(
            contentTrackForPage(data, page = 6, random = Random(42)),
            contentTrackForPage(data, page = 6, random = Random(42))
        )
        // Revisits (fresh rolls) eventually land on different items — variety, still in-list
        val ids = (0 until 50).map { songIdOf(contentTrackForPage(data, page = 6, random = Random(it.toLong()))) }
        assertTrue("expected variety across visits, got $ids", ids.distinct().size > 1)
    }

    @Test
    fun artistListCardOnlyRollsArtistsThatResolveToASong() {
        val data = deckData(
            topSongs = listOf(
                contentSong("s1", "Artist A"),
                contentSong("s2", "Ghost Artist") // not in the top artists list
            ),
            topArtists = listOf(
                TopArtist(artist = Artist(id = "a1", name = "Artist A"), minutes = 10, songCount = 1),
                TopArtist(artist = Artist(id = "a2", name = "No Song In Top Songs"), minutes = 10, songCount = 1)
            )
        )
        repeat(20) { i ->
            // The unresolvable artist is excluded from the roll — no silent pool fallback
            assertEquals(
                BgmContentTrack.SongTrack("s1"),
                contentTrackForPage(data, page = 4, random = Random(i.toLong()))
            )
        }
    }

    @Test
    fun modifiedPrefixDoesNotBreakTheArtistMatch() {
        val data = deckData(
            topSongs = listOf(contentSong("s1", "${MODIFIED_PREFIX}Artist A")),
            topArtists = listOf(
                TopArtist(artist = Artist(id = "a1", name = "${MODIFIED_PREFIX}Artist A"), minutes = 10, songCount = 1)
            )
        )
        assertEquals(BgmContentTrack.SongTrack("s1"), contentTrackForPage(data, page = 5, random = Random(1)))
    }

    @Test
    fun artistListCardFallsBackToTheFirstArtistWhenThereIsOnlyOne() {
        val data = deckData(
            topSongs = listOf(contentSong("s1", "Solo Artist")),
            topArtists = listOf(TopArtist(artist = Artist(id = "a1", name = "Solo Artist"), minutes = 10, songCount = 1))
        )
        assertEquals(BgmContentTrack.SongTrack("s1"), contentTrackForPage(data, page = 4))
    }

    @Test
    fun albumListCardReusesTheFirstAlbumWhenThereIsOnlyOne() {
        val data = deckData(
            topAlbums = listOf(TopAlbum(album = Album(id = "al1", title = "One"), minutes = 20, songCount = 10))
        )
        assertEquals(BgmContentTrack.AlbumTrack("al1"), contentTrackForPage(data, page = 12))
    }

    @Test
    fun scrollTargetFollowsTheSwipeDirection() {
        // First half of a forward scroll (offset negative) → next card
        assertEquals(5, scrollTargetPage(page = 4, offset = -0.2f))
        // Second half of a forward scroll / first half of a backward one (offset positive)
        // → the currentPage itself (a forward landing page, or corrected by the settle path)
        assertEquals(4, scrollTargetPage(page = 4, offset = 0.2f))
        // Still inside the dead zone (swipe just started) → the deck's forward flow
        assertEquals(5, scrollTargetPage(page = 4, offset = 0.02f))
        assertEquals(5, scrollTargetPage(page = 4, offset = 0f))
    }

    @Test
    fun fadeTailPreparePageKeepsTheMidFadeRequest() {
        // A target requested mid-fade (queued by the fade guard) wins over the next card
        assertEquals(3, fadeTailPreparePage(deferredPage = 3, defaultNextPage = 4))
        // Nothing deferred → the default next card
        assertEquals(4, fadeTailPreparePage(deferredPage = null, defaultNextPage = 4))
    }

    @Test
    fun standbySwapPageLandsOnTheCardOnScreen() {
        // The preloaded track of a fast swipe swaps as soon as it is ready (v7)
        assertEquals(5, standbySwapPage(currentPage = 5, standbyHoldPage = 5, activeTrackPage = 3))
        // Stale hold — the deck moved on while the load was in flight → no swap
        assertNull(standbySwapPage(currentPage = 5, standbyHoldPage = 4, activeTrackPage = 3))
        // Nothing settled yet → no swap
        assertNull(standbySwapPage(currentPage = -1, standbyHoldPage = -1, activeTrackPage = null))
    }

    @Test
    fun standbySwapPageNeverSwapsToTheAlreadyActiveCard() {
        // The card's track is already the active one → no double swap
        assertNull(standbySwapPage(currentPage = 5, standbyHoldPage = 5, activeTrackPage = 5))
    }

    @Test
    fun statsCardsRollFromThePool() {
        val data = deckData(topSongs = listOf(contentSong("s1", "Artist A")))
        listOf(0, 1, 2, 8, 9, 10, 13, 14).forEach { page ->
            assertEquals(BgmContentTrack.None, contentTrackForPage(data, page = page))
        }
    }

    @Test
    fun contentMappingIsNoneWhenTheDeckHasNoData() {
        val empty = deckData(topSongs = emptyList())
        (0 until 16).forEach { page ->
            assertEquals(BgmContentTrack.None, contentTrackForPage(empty, page = page))
        }
    }
}
