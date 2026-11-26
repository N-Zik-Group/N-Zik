package app.n_zik.android.core.database

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import app.n_zik.android.models.SongArtistMap
import app.n_zik.android.utils.ARTIST_LINKED_NAMES_EXTRA
import app.n_zik.android.utils.asSong
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

/**
 * Contract for artist↔song mapping reconciliation in [Database.insertIgnore]
 * (bug 2026-09-25, confirmed against a DB export: YTM author lists vary by
 * playback context — a channel playlist/radio credits the channel as an
 * "author" — so insert-only accumulation polluted SongArtistMap and a
 * channel-derived artist row ("E.T") collected hundreds of plays of unrelated
 * songs, ranking as a Rewind top artist).
 *
 * - existing song + id-bearing author list → stale mappings are deleted first,
 *   then the fresh list is mapped (delete strictly before any insert);
 * - fresh song → nothing to delete, only the fresh mappings;
 * - re-insert with no author info (local playback) → existing mappings
 *   untouched, no reconciliation.
 */
class DatabaseInsertIgnoreReconcileTest {

    private val songTable = mockk<SongTable>(relaxed = true)
    private val artistTable = mockk<ArtistTable>(relaxed = true)
    private val mapTable = mockk<SongArtistMapTable>(relaxed = true)

    private val extras = mockk<Bundle>(relaxed = true)

    private fun existingSong() = Song(id = "s1", title = "Song", durationText = null, thumbnailUrl = null)

    private fun mediaItemWithAuthors(names: List<String>, ids: List<String>): MediaItem {
        every { extras.getStringArrayList("artistNames") } returns ArrayList(names)
        every { extras.getStringArrayList("artistIds") } returns ArrayList(ids)
        every { extras.getStringArrayList(ARTIST_LINKED_NAMES_EXTRA) } returns null
        // Platform-type default of a relaxed Bundle mock is "" (not null) — pin it so
        // the album branch stays out of the test
        every { extras.getString("albumId") } returns null
        val metadata = MediaMetadata.Builder()
            .setExtras(extras)
            .build()
        mockkStatic("app.n_zik.android.utils.UtilsKt")
        every { any<MediaItem>().asSong } returns existingSong()
        return MediaItem.Builder()
            .setMediaId("s1")
            .setMediaMetadata(metadata)
            .build()
    }

    private fun mediaItemWithNamesOnly(names: List<String>): MediaItem {
        every { extras.getStringArrayList("artistNames") } returns ArrayList(names)
        // No browse IDs: forces the names-only branch
        every { extras.getStringArrayList("artistIds") } returns null
        every { extras.getStringArrayList(ARTIST_LINKED_NAMES_EXTRA) } returns null
        every { extras.getString("albumId") } returns null
        val metadata = MediaMetadata.Builder()
            .setExtras(extras)
            .build()
        mockkStatic("app.n_zik.android.utils.UtilsKt")
        every { any<MediaItem>().asSong } returns existingSong()
        return MediaItem.Builder()
            .setMediaId("s1")
            .setMediaMetadata(metadata)
            .build()
    }

    @Before
    fun setup() {
        mockkObject(Database)
        every { Database.songTable } returns songTable
        every { Database.artistTable } returns artistTable
        every { Database.songArtistMapTable } returns mapTable
        coEvery { songTable.findByIdDirect("s1") } returns existingSong()
        coEvery { artistTable.allByNameIgnoreCase(any()) } returns emptyList()
        // A relaxed mock returns a child mock (not null) for findByIdDirect - pin the
        // default to null: in those fixtures the row is (re)created under the context

        // name, so the alignment read-back falls back to the context name. Tests that
        // need an existing row under a different name stub the specific id explicitly.
        coEvery { artistTable.findByIdDirect(any()) } returns null
        coEvery { Database.insertIgnore(any(), false) } coAnswers { callOriginal() }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun trackMediaMetadataPreservesTheAlbumsAuthorsAndUserTitle() = runBlocking<Unit> {
        val albums = mockk<AlbumTable>(relaxed = true)
        val mappings = mockk<SongAlbumMapTable>(relaxed = true)
        every { Database.albumTable } returns albums
        every { Database.songAlbumMapTable } returns mappings
        val albumId = "MPREb_compilation"
        val canonical = Album(
            id = albumId,
            title = "modified:My compilation",
            authorsText = "Compilation artist",
            year = "2026",
        )
        coEvery { albums.findByIdDirect(albumId) } returns canonical
        val track = mediaItemWithAuthors(listOf("Track artist"), listOf("UC_TRACK_ARTIST"))
        every { extras.getString("albumId") } returns albumId
        val metadata = track.mediaMetadata.buildUpon()
            .setArtist("Track artist")
            .setAlbumTitle("Compilation")
            .setReleaseYear(2025)
            .build()

        Database.insertIgnore(track.buildUpon().setMediaMetadata(metadata).build(), autoFix = false)

        coVerify(exactly = 1) { albums.upsert(canonical.copy(year = "2025")) }
        coVerify(exactly = 1) { mappings.map("s1", albumId) }
    }

    @Test
    fun reinsertWithAuthorListReconcilesStaleMappings() = runBlocking<Unit> {
        val item = mediaItemWithAuthors(listOf("A", "B"), listOf("UC_A", "UC_B"))

        Database.insertIgnore(item, autoFix = false)

        coVerify(exactly = 1) { mapTable.deleteBySongId("s1") }
        coVerify(exactly = 2) { mapTable.insertIgnore(any<SongArtistMap>()) }
        coVerifyOrder {
            mapTable.deleteBySongId("s1")
            mapTable.insertIgnore(any<SongArtistMap>())
        }
    }

    @Test
    fun reinsertWithNamesOnlyListKeepsExistingMappings() = runBlocking<Unit> {
        coEvery { artistTable.allByNameIgnoreCase("A") } returns listOf(Artist(id = "UC_A", name = "A"))
        coEvery { artistTable.allByNameIgnoreCase("B") } returns listOf(Artist(id = "UC_B", name = "B"))
        val item = mediaItemWithNamesOnly(listOf("A", "B"))

        Database.insertIgnore(item, autoFix = false)

        coVerify(exactly = 0) { mapTable.deleteBySongId("s1") }
        coVerify(exactly = 2) { mapTable.insertIgnore(any<SongArtistMap>()) }
    }

    @Test
    fun mixedAuthorsMapOnlyVerifiedPairsAndKeepExistingMappings() = runBlocking<Unit> {
        val item = mediaItemWithAuthors(listOf("No endpoint", "Linked artist"), listOf("UC_LINKED"))
        every { extras.getStringArrayList(ARTIST_LINKED_NAMES_EXTRA) } returns arrayListOf("Linked artist")
        coEvery { artistTable.allByNameIgnoreCase("Linked artist") } returns listOf(Artist("UC_HOMONYM", "Linked artist"))

        Database.insertIgnore(item, autoFix = false)

        coVerify(exactly = 0) { mapTable.deleteBySongId("s1") }
        coVerify(exactly = 1) { artistTable.insertIgnore(Artist("UC_LINKED", "Linked artist")) }
        coVerify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("s1", "UC_LINKED")) }
        coVerify(exactly = 0) { mapTable.insertIgnore(SongArtistMap("s1", "UC_HOMONYM")) }
    }

    @Test
    fun oldMismatchedExtrasCanLinkAnExistingIdWithoutGuessingItsName() = runBlocking<Unit> {
        val item = mediaItemWithAuthors(listOf("No endpoint", "Linked artist"), listOf("UC_LINKED"))
        coEvery { artistTable.findByIdDirect("UC_LINKED") } returns Artist("UC_LINKED", "Exact row")

        Database.insertIgnore(item, autoFix = false)

        coVerify(exactly = 0) { mapTable.deleteBySongId("s1") }
        coVerify(exactly = 0) { artistTable.insertIgnore(any<Artist>()) }
        coVerify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("s1", "UC_LINKED")) }
    }

    @Test
    fun completeCreditsPreserveExplicitlyLinkedManualArtist() = runBlocking<Unit> {
        val edited = Artist("LOCAL_ARTIST_MANUAL", "modified:Manual credit")
        coEvery { artistTable.findBySongIdDirect("s1") } returns listOf(edited)
        val item = mediaItemWithAuthors(listOf("Remote artist"), listOf("UC_REMOTE"))

        Database.insertIgnore(item, autoFix = false)

        coVerify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("s1", "UC_REMOTE")) }
        coVerify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("s1", edited.id)) }
    }

    @Test
    fun localPlaybackKeepsIndexedArtistLinksWhenQueueCarriesOnlineIds() = runBlocking<Unit> {
        val item = mediaItemWithAuthors(listOf("A"), listOf("UC_A"))
            .buildUpon().setMediaId("local:42").build()
        every { any<MediaItem>().asSong } returns existingSong().copy(id = "local:42")

        Database.insertIgnore(item, autoFix = false)

        coVerify(exactly = 1) { songTable.upsert(match<Song> { it.id == "local:42" }) }
        coVerify(exactly = 0) { mapTable.deleteBySongId(any()) }
        coVerify(exactly = 0) { mapTable.insertIgnore(any<SongArtistMap>()) }
    }

    @Test
    fun onlinePlaybackDoesNotReuseAnOnDeviceArtistWithTheSameName() = runBlocking<Unit> {
        coEvery { artistTable.allByNameIgnoreCase("A") } returns listOf(Artist(id = "LOCAL_ARTIST_DEVICE_1", name = "A"))
        Database.insertIgnore(mediaItemWithAuthors(listOf("A"), listOf("UC_A")), autoFix = false)

        coVerify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("s1", "UC_A")) }
        coVerify(exactly = 0) { mapTable.insertIgnore(SongArtistMap("s1", "LOCAL_ARTIST_DEVICE_1")) }
    }

    @Test
    fun freshSongOnlyInsertsMappingsWithoutDelete() = runBlocking<Unit> {
        coEvery { songTable.findByIdDirect("s1") } returns null
        val item = mediaItemWithAuthors(listOf("A"), listOf("UC_A"))

        Database.insertIgnore(item, autoFix = false)

        coVerify(exactly = 0) { mapTable.deleteBySongId(any()) }
        coVerify(exactly = 1) { mapTable.insertIgnore(any<SongArtistMap>()) }
    }

    @Test
    fun reinsertWithoutAuthorInfoLeavesMappingsUntouched() = runBlocking<Unit> {
        val metadata = MediaMetadata.Builder().build()
        val item = MediaItem.Builder()
            .setMediaId("s1")
            .setMediaMetadata(metadata)
            .build()
        mockkStatic("app.n_zik.android.utils.UtilsKt")
        every { any<MediaItem>().asSong } returns existingSong()

        Database.insertIgnore(item, autoFix = false)

        coVerify(exactly = 0) { mapTable.deleteBySongId(any()) }
        coVerify(exactly = 0) { mapTable.insertIgnore(any<SongArtistMap>()) }
    }

    // ---- artistsText alignment: one source of truth for the mapping + display ----
    //
    // The startup sweep (DbCleanup) judges links by name equality against artistsText,
    // so whenever the mapping is (re)written from this context's author list, the

    // displayed artistsText must be aligned to the same names. `modified:` values are
    // never overwritten and an unchanged value skips the write.

    private fun existingSongWithArtists(artistsText: String) = Song(
        id = "s1", title = "Song", artistsText = artistsText,
        durationText = null, thumbnailUrl = null
    )

    @Test
    fun reinsertWithAuthorIdsAlignsArtistsTextToMappedNames() = runBlocking<Unit> {
        val upserted = mutableListOf<Song>()
        coEvery { songTable.upsert(any<Song>()) } coAnswers { upserted += firstArg<Song>() }
        // stored display text is JP, the fresh context author list is romaji
        coEvery { songTable.findByIdDirect("s1") } returns existingSongWithArtists("稲葉曇")
        val item = mediaItemWithAuthors(listOf("Inabakumori"), listOf("UC_INABA"))

        Database.insertIgnore(item, autoFix = false)

        // final upsert carries the romaji join (single source of truth)
        assertEquals("Inabakumori", upserted.last().artistsText)
        // mapping is unchanged: delete strictly before insert
        coVerify(exactly = 1) { mapTable.deleteBySongId("s1") }
        coVerify(exactly = 1) { mapTable.insertIgnore(any<SongArtistMap>()) }
    }

    @Test
    fun reinsertWithAuthorIdsKeepsModifiedArtistsText() = runBlocking<Unit> {
        val upserted = mutableListOf<Song>()
        coEvery { songTable.upsert(any<Song>()) } coAnswers { upserted += firstArg<Song>() }
        coEvery { songTable.findByIdDirect("s1") } returns existingSongWithArtists("modified: Custom")
        val item = mediaItemWithAuthors(listOf("Inabakumori"), listOf("UC_INABA"))

        Database.insertIgnore(item, autoFix = false)

        // a `modified:` value is never overwritten: only the baseline upsert, no aligning write
        assertEquals(1, upserted.size)
        assertEquals("modified: Custom", upserted.last().artistsText)
    }

    @Test
    fun reinsertWithNamesOnlyAlignsArtistsTextToMappedNames() = runBlocking<Unit> {
        val upserted = mutableListOf<Song>()
        coEvery { songTable.upsert(any<Song>()) } coAnswers { upserted += firstArg<Song>() }
        coEvery { songTable.findByIdDirect("s1") } returns existingSongWithArtists("稲葉曇")
        coEvery { artistTable.allByNameIgnoreCase("Inabakumori") } returns listOf(Artist(id = "UC_INABA", name = "Inabakumori"))
        val item = mediaItemWithNamesOnly(listOf("Inabakumori"))

        Database.insertIgnore(item, autoFix = false)

        assertEquals("Inabakumori", upserted.last().artistsText)
        coVerify(exactly = 0) { mapTable.deleteBySongId("s1") }
        coVerify(exactly = 1) { mapTable.insertIgnore(any<SongArtistMap>()) }
    }

    @Test
    fun reinsertWithoutAuthorInfoKeepsArtistsText() = runBlocking<Unit> {
        val upserted = mutableListOf<Song>()
        coEvery { songTable.upsert(any<Song>()) } coAnswers { upserted += firstArg<Song>() }
        coEvery { songTable.findByIdDirect("s1") } returns existingSongWithArtists("稲葉曇")
        val metadata = MediaMetadata.Builder().build()
        val item = MediaItem.Builder()
            .setMediaId("s1")
            .setMediaMetadata(metadata)
            .build()
        mockkStatic("app.n_zik.android.utils.UtilsKt")
        every { any<MediaItem>().asSong } returns existingSong()

        Database.insertIgnore(item, autoFix = false)

        // no author list -> the alignment never runs, only the baseline upsert
        assertEquals(1, upserted.size)
        assertEquals("稲葉曇", upserted.last().artistsText)
    }

    @Test
    fun freshSongWithAuthorIdsAlignsArtistsTextToMappedNames() = runBlocking<Unit> {
        val upserted = mutableListOf<Song>()
        coEvery { songTable.upsert(any<Song>()) } coAnswers { upserted += firstArg<Song>() }
        // Fresh song: no DB row, so the stored artistsText starts blank
        coEvery { songTable.findByIdDirect("s1") } returns null
        val item = mediaItemWithAuthors(listOf("Inabakumori"), listOf("UC_INABA"))

        Database.insertIgnore(item, autoFix = false)

        // The alignment also runs for fresh songs: the play→sweep loop must not be
        // created on the very first play (mapping and display share one source of truth)
        assertEquals("Inabakumori", upserted.last().artistsText)
        coVerify(exactly = 0) { mapTable.deleteBySongId(any()) }
    }

    @Test
    fun reinsertWithAuthorIdsAlignsArtistsTextToStoredRowName() = runBlocking<Unit> {
        val upserted = mutableListOf<Song>()
        coEvery { songTable.upsert(any<Song>()) } coAnswers { upserted += firstArg<Song>() }
        // Stored display text and the fresh context author list are romaji, but the
        // artist row already exists under its JP spelling with the same channel id
        coEvery { songTable.findByIdDirect("s1") } returns existingSongWithArtists("Shaito")
        coEvery { artistTable.allByNameIgnoreCase("Shaito") } returns emptyList()
        coEvery { artistTable.findByIdDirect("UC_SHAITO") } returns Artist(id = "UC_SHAITO", name = "しゃいと")
        val item = mediaItemWithAuthors(listOf("Shaito"), listOf("UC_SHAITO"))

        Database.insertIgnore(item, autoFix = false)

        // artistsText follows the row the link actually points to: findByNameDirect
        // missed the existing row and the insertIgnore no-op must not rename it -

        // the startup sweep compares that stored row name against artistsText
        assertEquals("しゃいと", upserted.last().artistsText)
        coVerify(exactly = 1) { mapTable.deleteBySongId("s1") }
        coVerify(exactly = 1) { mapTable.insertIgnore(any<SongArtistMap>()) }
    }

    // ---- leak of the `modified:` storage marker into the aligned display copy ----
    //
    // The resolved names are STORAGE values (raw context extras, or the stored row
    // name which carries `modified:` when the row was pinned/renamed): they must be

    // cleaned before the join, otherwise the marker is embedded MID-STRING in the
    // natural copy (cleanPrefix only cuts at the head) and re-appears in the song

    // list after each playback — the "after the stream it shows modified:X" bug.

    @Test
    fun alignmentCleansTheModifiedPrefixOfResolvedRowNames() = runBlocking<Unit> {
        val upserted = mutableListOf<Song>()
        coEvery { songTable.upsert(any<Song>()) } coAnswers { upserted += firstArg<Song>() }
        coEvery { songTable.findByIdDirect("s1") } returns existingSongWithArtists("Yunosuke")
        // the extras carry the pinned row names verbatim (raw prefetch of the row names)
        val item = mediaItemWithAuthors(
            listOf("Yunosuke", "modified:Hatsune Miku"),
            listOf("UC_YUNOSUKE", "UC_MIKU")
        )

        Database.insertIgnore(item, autoFix = false)

        assertEquals("Yunosuke, Hatsune Miku", upserted.last().artistsText)
    }

    @Test
    fun alignmentCleansTheModifiedPrefixReadBackFromTheRow() = runBlocking<Unit> {
        val upserted = mutableListOf<Song>()
        coEvery { songTable.upsert(any<Song>()) } coAnswers { upserted += firstArg<Song>() }
        coEvery { songTable.findByIdDirect("s1") } returns existingSongWithArtists("Yunosuke")
        coEvery { artistTable.allByNameIgnoreCase("Hatsune Miku") } returns emptyList()
        // the link resolves to an existing row stored under its pinned name
        coEvery { artistTable.findByIdDirect("UC_MIKU") } returns Artist(id = "UC_MIKU", name = "modified:Hatsune Miku")
        val item = mediaItemWithAuthors(
            listOf("Hatsune Miku"),
            listOf("UC_MIKU")
        )

        Database.insertIgnore(item, autoFix = false)

        assertEquals("Hatsune Miku", upserted.last().artistsText)
    }

    @Test
    fun alignmentKeepsCustomModifiedCopyUntouchedByTheCleaning() = runBlocking<Unit> {
        val upserted = mutableListOf<Song>()
        coEvery { songTable.upsert(any<Song>()) } coAnswers { upserted += firstArg<Song>() }
        // a custom copy is never overwritten: cleaning the resolved names must not
        // change that rule (only one write: the baseline upsert)
        coEvery { songTable.findByIdDirect("s1") } returns existingSongWithArtists("modified: Custom")
        val item = mediaItemWithAuthors(listOf("modified:Hatsune Miku"), listOf("UC_MIKU"))

        Database.insertIgnore(item, autoFix = false)

        assertEquals(1, upserted.size)
        assertEquals("modified: Custom", upserted.last().artistsText)
    }

    // ---- diagnostics logging: per-pair detail before the delete (I/O matrix rows) ----

    private class DatabaseCaptureTree(val captured: MutableList<String>) : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (tag == "Database") captured += message
        }
    }

    @Test
    fun reconcileIdsLogsOneLinePerDroppedPair() = runBlocking<Unit> {
        val captured = mutableListOf<String>()
        val captureTree = DatabaseCaptureTree(captured)
        Timber.plant(captureTree)
        try {
            coEvery { mapTable.pairsBySongIdDirect("s1") } returns listOf(
                SongArtistMap("s1", "UC_A"), SongArtistMap("s1", "UC_B")
            )
            coEvery { artistTable.findByIdDirect("UC_A") } returns Artist(id = "UC_A", name = "A")
            coEvery { artistTable.findByIdDirect("UC_B") } returns Artist(id = "UC_B", name = "B")
            val item = mediaItemWithAuthors(listOf("C", "D"), listOf("UC_C", "UC_D"))

            Database.insertIgnore(item, autoFix = false)

            assertEquals(2, captured.count { it.startsWith("reconcile dropped link:") })
            assertTrue(
                "expected per-pair detail with song title + artist names, got: $captured",
                captured.any {
                    it.startsWith("reconcile dropped link: song=s1") &&
                        it.contains("\"Song\"") &&
                        it.contains("artist=\"A\"")
                } && captured.any {
                    it.startsWith("reconcile dropped link: song=s1") &&
                        it.contains("\"Song\"") &&
                        it.contains("artist=\"B\"")
                }
            )
            // per-pair detail comes before the existing summary, which stays untouched
            assertTrue(
                "per-pair lines must precede the summary, got: $captured",
                captured.indexOfFirst { it.startsWith("insertIgnore RECONCILE ids") } >
                    captured.indexOfLast { it.startsWith("reconcile dropped link:") }
            )
            assertTrue(captured.any { it.startsWith("insertIgnore RECONCILE ids song=s1 dropped=0") })
            // targeted query only — no full-table scan
            coVerify(exactly = 1) { mapTable.pairsBySongIdDirect("s1") }
            coVerify(exactly = 0) { mapTable.allPairsDirect() }
        } finally {
            Timber.uproot(captureTree)
        }
    }

    @Test
    fun namesOnlyListDoesNotReadOrLogDroppedLinks() = runBlocking<Unit> {
        val captured = mutableListOf<String>()
        val captureTree = DatabaseCaptureTree(captured)
        Timber.plant(captureTree)
        try {
            Database.insertIgnore(mediaItemWithNamesOnly(listOf("C")), autoFix = false)

            coVerify(exactly = 0) { mapTable.pairsBySongIdDirect("s1") }
            assertTrue(captured.none { it.startsWith("reconcile dropped link:") })
        } finally {
            Timber.uproot(captureTree)
        }
    }

    @Test
    fun noStaleLinksMeansNoPerPairLines() = runBlocking<Unit> {
        val captured = mutableListOf<String>()
        val captureTree = DatabaseCaptureTree(captured)
        Timber.plant(captureTree)
        try {
            coEvery { mapTable.pairsBySongIdDirect("s1") } returns emptyList()
            val item = mediaItemWithAuthors(listOf("A", "B"), listOf("UC_A", "UC_B"))

            Database.insertIgnore(item, autoFix = false)

            assertTrue(captured.none { it.startsWith("reconcile dropped link:") })
            assertTrue(captured.any { it.startsWith("insertIgnore RECONCILE ids song=s1 dropped=0") })
        } finally {
            Timber.uproot(captureTree)
        }
    }
}
