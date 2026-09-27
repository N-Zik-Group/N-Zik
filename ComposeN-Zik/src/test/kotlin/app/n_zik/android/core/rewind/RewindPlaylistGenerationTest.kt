package app.n_zik.android.core.rewind

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Event
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.rewind.RewindPlaylists.rewindDisplayName
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * Test-only application: [MainApplication.onCreate] is not needed here — the only
 * requirement is a real Context so the global [Database] singleton can be built
 * (same pattern as MediaItemUtilsTest, which also avoids MainApplication's
 * AndroidKeyStore migration, unavailable on the Robolectric JVM).
 */
class RewindGenerationTestApplication : Application()

/**
 * Tests the shared rewind generation contract (spec 2):
 * - the pure naming/window/display-name helpers of [RewindPlaylists];
 * - the single write path [generateRewindPlaylist] against a real Room database:
 *   top-order positions, the [GenerateMode.CreateIfMissing] skip, the
 *   [GenerateMode.Regenerate] delete-then-recreate, and the empty-window no-op.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = RewindGenerationTestApplication::class)
class RewindPlaylistGenerationTest {

    @Before
    fun initDependencies() {
        val app = ApplicationProvider.getApplicationContext<RewindGenerationTestApplication>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
    }

    // ------------------------------------------------------------------
    // Pure contract: names, windows, display names
    // ------------------------------------------------------------------

    @Test
    fun monthlyNameZeroPadsTheMonth() {
        assertEquals("rewind-monthly:202602", RewindPlaylists.monthlyName(2026, 2))
        assertEquals("rewind-monthly:202612", RewindPlaylists.monthlyName(2026, 12))
    }

    @Test
    fun yearlyNameBuildsTheYearlyName() {
        assertEquals("rewind-yearly:2026", RewindPlaylists.yearlyName(2026))
    }

    @Test
    fun windowForDerivesTheWholeMonth() {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.of(2026, 2, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 3, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(start to end, RewindPlaylists.windowFor("rewind-monthly:202602", now = 0L))
    }

    @Test
    fun windowForDerivesTheWholeYear() {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2027, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(start to end, RewindPlaylists.windowFor("rewind-yearly:2026", now = 0L))
    }

    @Test
    fun windowForAlltimeCoversTheWholeHistory() {
        val now = 1_750_000_000_000L
        assertEquals(0L to now, RewindPlaylists.windowFor("rewind-alltime", now = now))
    }

    @Test
    fun windowForReturnsNullForForeignAndMalformedNames() {
        assertNull(RewindPlaylists.windowFor("my playlists"))
        // the legacy "monthly:" prefix is a different feature and must not resolve
        assertNull(RewindPlaylists.windowFor("monthly:202602"))
        assertNull(RewindPlaylists.windowFor("rewind-monthly:20262")) // 5-digit tail
        assertNull(RewindPlaylists.windowFor("rewind-monthly:202613")) // month 13
        assertNull(RewindPlaylists.windowFor("rewind-yearly:202")) // 3-digit year
        // a user-created playlist can carry a prefix-shaped but non-numeric tail:
        // "anything else" must resolve to null, never an exception
        assertNull(RewindPlaylists.windowFor("rewind-monthly:2026ab"))
        assertNull(RewindPlaylists.windowFor("rewind-monthly:ab2602"))
        assertNull(RewindPlaylists.windowFor("rewind-yearly:abcd"))
    }

    @Test
    fun rewindClassification() {
        assertTrue(RewindPlaylists.isRewind("rewind-monthly:202602"))
        assertTrue(RewindPlaylists.isRewind("rewind-yearly:2026"))
        assertTrue(RewindPlaylists.isRewind("rewind-alltime"))
        assertTrue(RewindPlaylists.isAlltime("REWIND-ALLTIME")) // ignoreCase
        assertTrue(RewindPlaylists.isRewind("REWIND-ALLTIME"))
        // the legacy monthly: prefix (different feature) is not a rewind name
        assertFalse(RewindPlaylists.isRewind("monthly:202602"))
        assertFalse(RewindPlaylists.isAlltime("rewind-alltimex"))
    }

    @Test
    fun rewindDisplayNameFormatsMonthlyWithTheLocalizedMonth() {
        val app = ApplicationProvider.getApplicationContext<RewindGenerationTestApplication>()
        val expected = app.getString(R.string.rewind) + " " +
            String.format(app.getString(R.string.month_february_s), "2026")
        assertEquals(expected, app.rewindDisplayName("rewind-monthly:202602"))
    }

    @Test
    fun rewindDisplayNameFormatsYearlyWithTheYear() {
        val app = ApplicationProvider.getApplicationContext<RewindGenerationTestApplication>()
        assertEquals(
            app.getString(R.string.rewind) + " 2026",
            app.rewindDisplayName("rewind-yearly:2026")
        )
    }

    @Test
    fun rewindDisplayNameFormatsAlltime() {
        val app = ApplicationProvider.getApplicationContext<RewindGenerationTestApplication>()
        assertEquals(
            app.getString(R.string.rewind) + " " + app.getString(R.string.rewind_alltime_name),
            app.rewindDisplayName("rewind-alltime")
        )
    }

    @Test
    fun rewindDisplayNameIsANoOpForForeignNames() {
        val app = ApplicationProvider.getApplicationContext<RewindGenerationTestApplication>()
        assertEquals("My Playlist", app.rewindDisplayName("My Playlist"))
    }

    // ------------------------------------------------------------------
    // generateRewindPlaylist against the real database
    // ------------------------------------------------------------------

    @Test
    fun creationWritesTheWindowTopSongsToPositionsInTopOrder() = runBlocking {
        val from = 1_750_000_000_000L
        val to = from + 86_400_000L
        insertSong("gen_song_a")
        insertSong("gen_song_b")
        insertSong("gen_song_c")
        insertEvent("gen_song_a", from + 1_000, 100_000)
        insertEvent("gen_song_b", from + 2_000, 50_000)
        insertEvent("gen_song_c", from + 3_000, 10_000)

        val count = generateRewindPlaylist("rewind-gen-order", from, to, GenerateMode.CreateIfMissing)

        assertEquals(3, count)
        val id = awaitPlaylistContent("rewind-gen-order", listOf("gen_song_a", "gen_song_b", "gen_song_c"))
        val mapped = listOf("gen_song_a", "gen_song_b", "gen_song_c").map { songId ->
            withContext(NzikDispatchers.DATA) {
                Database.songPlaylistMapTable.findPositionOf(songId, id)
            }
        }
        // `map` appends at MAX(position)+1 -> 0,1,2 in the window's top order:
        // this IS the "Rewind Top" snapshot read back by sortSongsByPosition
        assertEquals(listOf(0, 1, 2), mapped)
    }

    @Test
    fun createIfMissingLeavesAnExistingPlaylistUntouched() = runBlocking {
        val from = 1_760_000_000_000L
        val to = from + 86_400_000L
        insertSong("gen_skip_old")
        insertSong("gen_skip_new")
        insertEvent("gen_skip_new", from + 1_000, 100_000)
        val existingId = withContext(NzikDispatchers.DATA) {
            Database.playlistTable.insert(Playlist(name = "rewind-gen-skip"))
        }
        withContext(NzikDispatchers.DATA) {
            Database.songPlaylistMapTable.map("gen_skip_old", existingId)
        }

        val count = generateRewindPlaylist("rewind-gen-skip", from, to, GenerateMode.CreateIfMissing)

        assertEquals("an existing playlist must be skipped, not rewritten", 0, count)
        // the skip path enqueues no write: an immediate read is deterministic
        assertEquals(existingId, playlistIdNow("rewind-gen-skip"))
        assertEquals(
            "the pre-existing content must stay, and the window songs must not be added",
            listOf("gen_skip_old"),
            songsIn(existingId)
        )
    }

    @Test
    fun regenerateReplacesTheExistingContent() = runBlocking {
        val from = 1_770_000_000_000L
        val to = from + 86_400_000L
        insertSong("gen_regen_old")
        insertSong("gen_regen_new")
        insertEvent("gen_regen_old", from + 1_000, 100_000)

        val first = generateRewindPlaylist("rewind-gen-regen", from, to, GenerateMode.CreateIfMissing)
        assertEquals(1, first)
        val oldId = awaitPlaylistContent("rewind-gen-regen", listOf("gen_regen_old"))

        // the window now has a new top song
        insertEvent("gen_regen_new", from + 2_000, 200_000)

        val second = generateRewindPlaylist("rewind-gen-regen", from, to, GenerateMode.Regenerate)
        assertEquals(2, second)
        // poll the settled content: Regenerate deletes and recreates, so the row id changes
        // and only the current window top list remains, in top order
        val newId = awaitPlaylistContent("rewind-gen-regen", listOf("gen_regen_new", "gen_regen_old"))
        assertTrue("Regenerate deletes and recreates the playlist (new row id)", oldId != newId)
    }

    @Test
    fun emptyWindowCreatesNothing() = runBlocking {
        val from = 1_780_000_000_000L
        val to = from + 86_400_000L
        // a song exists but has no event inside the window
        insertSong("gen_empty")

        val count = generateRewindPlaylist("rewind-gen-empty", from, to, GenerateMode.CreateIfMissing)

        assertEquals(0, count)
        // count == 0 means no write was ever enqueued for this name: an immediate read
        // is deterministic (nothing is in flight)
        assertEquals(
            "an empty window must not create a playlist",
            null,
            Database.playlistTable.findByName("rewind-gen-empty").first()
        )
    }

    @Test
    fun emptyWindowLeavesAnExistingPlaylistIntact() = runBlocking {
        val from = 1_790_000_000_000L
        val to = from + 86_400_000L
        insertSong("gen_empty_keep")
        val existingId = withContext(NzikDispatchers.DATA) {
            Database.playlistTable.insert(Playlist(name = "rewind-gen-empty-keep"))
        }
        withContext(NzikDispatchers.DATA) {
            Database.songPlaylistMapTable.map("gen_empty_keep", existingId)
        }

        val count = generateRewindPlaylist("rewind-gen-empty-keep", from, to, GenerateMode.Regenerate)

        assertEquals(0, count)
        // the empty window enqueues no write: an immediate read is deterministic
        assertEquals(
            "the Regenerate path must NOT delete the playlist on an empty window",
            existingId,
            playlistIdNow("rewind-gen-empty-keep")
        )
        assertEquals(listOf("gen_empty_keep"), songsIn(existingId))
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private suspend fun insertSong(id: String) {
        withContext(NzikDispatchers.DATA) {
            Database.songTable.upsert(Song.makePlaceholder(id))
        }
    }

    private suspend fun insertEvent(songId: String, timestamp: Long, playTimeMs: Long) {
        withContext(NzikDispatchers.DATA) {
            Database.eventTable.insertIgnore(Event(songId = songId, timestamp = timestamp, playTime = playTimeMs))
        }
    }

    /**
     * Polls until the rewind playlist exists AND its content equals [expectedSongs]. The
     * generation path writes through asyncTransaction (fire-and-forget): the playlist row
     * appears before its mappings, so existence alone is not a completion signal.
     */
    private suspend fun awaitPlaylistContent(name: String, expectedSongs: List<String>): Long {
        val deadline = System.currentTimeMillis() + 10_000
        while (true) {
            val id = Database.playlistTable.findByName(name).first()?.id
            val songs = id?.let { Database.songPlaylistMapTable.allSongsOf(it).first() }?.map { it.id }
                ?: emptyList()
            if (id != null && songs == expectedSongs) return id
            assertTrue(
                "timed out waiting for the rewind playlist content of \"$name\"",
                System.currentTimeMillis() < deadline
            )
            delay(50)
        }
    }

    private suspend fun playlistIdNow(name: String): Long? =
        Database.playlistTable.findByName(name).first()?.id

    private suspend fun songsIn(playlistId: Long): List<String> =
        Database.songPlaylistMapTable.allSongsOf(playlistId).first().map { it.id }
}
