package app.n_zik.android.core.database

import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongArtistMap
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.ArtistConjunctions
import it.fast4x.innertube.models.NavigationEndpoint
import kotlinx.coroutines.runBlocking
import org.junit.After
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Contract for upsert artist mapping anchored on channel identity (v6,
 * 2026-09-26): one author entry carrying a browseId = one artist = one row.
 *
 * The previous implementation split a single entry's display name
 * ("Bigflo & OLi" -> "Bigflo" + "OLi"); every split part lost the channel
 * browseId, the author list failed the completeness check, the upsert fell
 * into the add-only path and spawned one online search + junk row per part
 * on every playback (device captures 2026-09-26 12:11 / 12:18:
 * added=2 -> dropped=2 loop).
 *
 * The channel page is the table of truth for the name:
 * - an existing channel row keeps its stored name (page name or
 *   user-modified) and is never clobbered by the raw entry name;
 * - artistsText follows the resolved row names.
 */
class DatabaseUpsertChannelIdentityTest {

    private val songTable = mockk<SongTable>(relaxed = true)
    private val artistTable = mockk<ArtistTable>(relaxed = true)
    private val mapTable = mockk<SongArtistMapTable>(relaxed = true)
    private val internal = mockk<DatabaseInitializer>(relaxed = true)

    private val conjunctionsBackup = ArtistConjunctions.conjunctions

    private fun author( name: String, browseId: String? ): Innertube.Info<NavigationEndpoint.Endpoint.Browse> =
        Innertube.Info(
            name = name,
            endpoint = browseId?.let { NavigationEndpoint.Endpoint.Browse(browseId = it) }
        )

    private fun songItem( authors: List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>> ): Innertube.SongItem =
        Innertube.SongItem(
            info = Innertube.Info(
                name = "Dommage",
                endpoint = NavigationEndpoint.Endpoint.Watch(videoId = "ORxckE7oN6g")
            ),
            authors = authors,
            album = null,
            durationText = null,
            thumbnail = null
        )

    @Before
    fun setup() {
        mockkObject(Database)
        every { Database.songTable } returns songTable
        every { Database.artistTable } returns artistTable
        every { Database.songArtistMapTable } returns mapTable
        // The upsert wraps its writes in a Room transaction: keep the lazy
        // Room database out of the JVM by stubbing the initializer instance
        // (relaxed -> begin/end transaction are no-ops, the block still runs).
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns internal
        // Room 2.8 withTransaction runs its block on the database's
        // transactionExecutor (startTransactionCoroutine). The relaxed mock
        // would return a fake Executor that swallows the runnable, leaving
        // the upsert suspended forever; run the transaction inline instead.
        every { internal.transactionExecutor } returns Executor { it.run() }
        // Deterministic regardless of the locale wiring done at app startup
        ArtistConjunctions.conjunctions = listOf("and")
        every { artistTable.findByIdDirect(any()) } returns null
        every { artistTable.findByNameDirect(any()) } returns null
        every { songTable.findByIdDirect(any()) } returns null
    }

    @After
    fun tearDown() {
        ArtistConjunctions.conjunctions = conjunctionsBackup
        unmockkAll()
    }

    @Test
    fun singleAmpersandEntryMapsToOneChannelArtist() {
        val item = songItem(listOf(author("Bigflo & OLi", "UC_BIGFLO")))

        runBlocking { Database.upsert(item) }

        val upserted = slot<List<Artist>>()
        verify(exactly = 1) { artistTable.upsert(capture(upserted)) }
        assertEquals(listOf(Artist(id = "UC_BIGFLO", name = "Bigflo & OLi")), upserted.captured)
        // complete author list -> reconcile path, not add-only
        verify(exactly = 1) { mapTable.deleteBySongId("ORxckE7oN6g") }
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("ORxckE7oN6g", "UC_BIGFLO")) }

        val song = slot<Song>()
        verify(exactly = 1) { songTable.upsert(capture(song)) }
        // whole entry name, not the split join "Bigflo, OLi"
        assertEquals("Bigflo & OLi", song.captured.artistsText)
    }

    @Test
    fun existingChannelRowKeepsPageName() {
        val pageRow = Artist(id = "UC_BIGFLO", name = "Bigflo & Oli")
        every { artistTable.findByIdDirect("UC_BIGFLO") } returns pageRow
        every { songTable.findByIdDirect("ORxckE7oN6g") } returns
            Song(id = "ORxckE7oN6g", title = "Dommage", artistsText = "Bigflo, OLi", durationText = null, thumbnailUrl = null)
        val item = songItem(listOf(author("Bigflo & OLi", "UC_BIGFLO")))

        runBlocking { Database.upsert(item) }

        // the raw entry name ("OLi" quirk) must not clobber the stored page name
        verify(exactly = 0) {
            artistTable.upsert(match<List<Artist>> { list -> list.any { it.name == "Bigflo & OLi" } })
        }

        val song = slot<Song>()
        verify(exactly = 1) { songTable.upsert(capture(song)) }
        // artistsText follows the row name (table of truth)
        assertEquals("Bigflo & Oli", song.captured.artistsText)
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("ORxckE7oN6g", "UC_BIGFLO")) }
    }

    @Test
    fun multiEntrySongMapsEachEntryToItsOwnChannel() {
        every { artistTable.findByIdDirect("UC_A") } returns Artist(id = "UC_A", name = "Tanchiky")
        every { artistTable.findByIdDirect("UC_B") } returns Artist(id = "UC_B", name = "siromaru")
        val item = songItem(listOf(author("Tanchiky", "UC_A"), author("siromaru", "UC_B")))

        runBlocking { Database.upsert(item) }

        verify(exactly = 1) { mapTable.deleteBySongId("ORxckE7oN6g") }
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("ORxckE7oN6g", "UC_A")) }
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("ORxckE7oN6g", "UC_B")) }

        val song = slot<Song>()
        verify(exactly = 1) { songTable.upsert(capture(song)) }
        assertEquals("Tanchiky, siromaru", song.captured.artistsText)
    }

    @Test
    fun modifiedArtistsTextIsPreserved() {
        every { artistTable.findByIdDirect("UC_BIGFLO") } returns Artist(id = "UC_BIGFLO", name = "Bigflo & Oli")
        // A stale title forces a write so the persisted song can be
        // inspected; the user-modified artistsText must survive the rewrite.
        every { songTable.findByIdDirect("ORxckE7oN6g") } returns
            Song(id = "ORxckE7oN6g", title = "Old title", artistsText = "modified:Custom", durationText = null, thumbnailUrl = null)
        val item = songItem(listOf(author("Bigflo & OLi", "UC_BIGFLO")))

        runBlocking { Database.upsert(item) }

        val song = slot<Song>()
        verify(exactly = 1) { songTable.upsert(capture(song)) }
        assertEquals("modified:Custom", song.captured.artistsText)
    }

    @Test
    fun entryWithoutBrowseIdKeepsNameOnlyPath() {
        every { artistTable.findByNameDirect("LocalArtist") } returns Artist(id = "LOCAL_1", name = "LocalArtist")
        val item = songItem(listOf(author("LocalArtist", null)))

        runBlocking { Database.upsert(item) }

        // incomplete author list -> add-only: no delete, no online search per part
        verify(exactly = 0) { mapTable.deleteBySongId(any()) }
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("ORxckE7oN6g", "LOCAL_1")) }

        val song = slot<Song>()
        verify(exactly = 1) { songTable.upsert(capture(song)) }
        assertEquals("LocalArtist", song.captured.artistsText)
    }

    @Test
    fun separatorAndConjunctionEntriesAreSkipped() {
        every { artistTable.findByIdDirect("UC_BIGFLO") } returns Artist(id = "UC_BIGFLO", name = "Bigflo & Oli")
        val item = songItem(listOf(
            author("Bigflo & OLi", "UC_BIGFLO"),
            author("&", "UC_SEPARATOR"),
            author("and", "UC_CONJUNCTION"),
        ))

        runBlocking { Database.upsert(item) }

        verify(exactly = 1) { mapTable.insertIgnore(any<SongArtistMap>()) }
        verify(exactly = 1) { mapTable.insertIgnore(SongArtistMap("ORxckE7oN6g", "UC_BIGFLO")) }
    }
}
