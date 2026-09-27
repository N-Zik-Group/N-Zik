package app.n_zik.android.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongArtistMap
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.ArtistConjunctions
import it.fast4x.innertube.models.NavigationEndpoint
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests the artistsText write of [Database.upsert] against an in-memory Room
 * database: the stored copy follows the resolved row names, and a storage
 * marker (`modified:`) must never be copied into a display copy — a song whose
 * stored copy is natural (no leading `modified:`) and linked to a renamed row
 * `modified:X` gets a clean `X` on every playback, not a prefix re-embedded
 * mid-string (which `retainIfModified` cannot cut, it only protects custom
 * copies).
 *
 * Uses JUnit 4 annotations (@Before / @After) because Robolectric's @RunWith
 * is a JUnit 4 concept. The artist rows pre-exist under test, so upsert's
 * network phase is skipped entirely.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseUpsertArtistsTextTest {

    private lateinit var db: DatabaseInitializer
    private val conjunctionsBackup = ArtistConjunctions.conjunctions

    @Before
    fun createDb() {
        // Deterministic regardless of the locale wiring done at app startup
        ArtistConjunctions.conjunctions = listOf("and")
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        // Redirect the lazy singleton to the in-memory database so the real
        // [Database.upsert] body runs against the DAOs (the Database object
        // itself stays unmocked).
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns db
    }

    @After
    fun closeDb() {
        unmockkAll()
        ArtistConjunctions.conjunctions = conjunctionsBackup
        db.close()
    }

    private fun author(name: String?, browseId: String? = null) =
        Innertube.Info<NavigationEndpoint.Endpoint.Browse>(
            name = name,
            endpoint = browseId?.let { NavigationEndpoint.Endpoint.Browse(browseId = it) }
        )

    private fun songItem(
        songId: String,
        title: String,
        authors: List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>>,
    ) = Innertube.SongItem(
        info = Innertube.Info(
            name = title,
            endpoint = NavigationEndpoint.Endpoint.Watch(videoId = songId)
        ),
        authors = authors,
        album = null,
        durationText = "3:00",
        thumbnail = null
    )

    private fun insertArtist(id: String, name: String) {
        db.artistTable.upsert(Artist(id = id, name = name))
    }

    private fun insertLink(songId: String, artistId: String) {
        db.songArtistMapTable.insertIgnore(SongArtistMap(songId, artistId))
    }

    @Test
    fun upsertWritesACleanArtistsTextForANaturalSongLinkedToAModifiedRow() = runTest {
        // The row was renamed (the storage marker lives on the row only); the
        // song's stored copy is natural (no leading `modified:`), so
        // retainIfModified takes the freshly fetched text - it must come out
        // clean, without a prefix re-embedded at a token position.
        insertArtist("UC_Y", "Yunosuke")
        insertArtist("UC_M", "modified:Hatsune Miku")
        db.songTable.upsert(
            Song.makePlaceholder("s1").copy(title = "Track", artistsText = "Yunosuke, Hatsune Miku")
        )
        insertLink("s1", "UC_Y")
        insertLink("s1", "UC_M")

        Database.upsert(
            songItem(
                "s1",
                "Track",
                listOf(author("Yunosuke", "UC_Y"), author("Hatsune Miku", "UC_M"))
            )
        )

        assertEquals("Yunosuke, Hatsune Miku", db.songTable.findByIdDirect("s1")?.artistsText)
    }

    @Test
    fun upsertRepairsAMidStringPrefixLeakInANaturalCopy() = runTest {
        // A copy polluted with a leaked mid-string prefix (old behavior) is
        // rewritten clean from the resolved row names on the next playback.
        insertArtist("UC_Y", "Yunosuke")
        insertArtist("UC_M", "modified:Hatsune Miku")
        db.songTable.upsert(
            Song.makePlaceholder("s1")
                .copy(title = "Track", artistsText = "Yunosuke, modified:Hatsune Miku")
        )
        insertLink("s1", "UC_Y")
        insertLink("s1", "UC_M")

        Database.upsert(
            songItem(
                "s1",
                "Track",
                listOf(author("Yunosuke", "UC_Y"), author("Hatsune Miku", "UC_M"))
            )
        )

        assertEquals("Yunosuke, Hatsune Miku", db.songTable.findByIdDirect("s1")?.artistsText)
    }

    @Test
    fun upsertLeavesACustomCopyUntouchedEvenWhenLinkedToAModifiedRow() = runTest {
        // The user's custom copy (leading `modified:`) is kept by
        // retainIfModified - the row marker must not leak into it either.
        insertArtist("UC_M", "modified:Hatsune Miku")
        db.songTable.upsert(
            Song.makePlaceholder("s1").copy(title = "Track", artistsText = "modified:Hatsune Miku (Custom)")
        )
        insertLink("s1", "UC_M")

        Database.upsert(
            songItem("s1", "Track", listOf(author("Hatsune Miku", "UC_M")))
        )

        assertEquals("modified:Hatsune Miku (Custom)", db.songTable.findByIdDirect("s1")?.artistsText)
    }
}
