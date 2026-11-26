package app.n_zik.android.utils

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.models.Artist
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.requests.LibraryContinuationPage
import app.n_zik.innertube.requests.LibraryPage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class YouTubeArtistProvenanceTest {
    @Test
    fun incompleteAndRepeatedContinuationAreRejected() = runTest {
        val first = LibraryPage(listOf(artist("first")), "next")
        assertNull(completeYouTubeArtistLibrary(first) { null })
        assertNull(completeYouTubeArtistLibrary(first) { LibraryContinuationPage(emptyList(), "next") })
        assertEquals(
            listOf("first", "second"),
            completeYouTubeArtistLibrary(first) { token ->
                assertEquals("next", token)
                LibraryContinuationPage(listOf(artist("first"), artist("second")), null)
            }?.map { it.key },
        )
    }

    @Test
    fun clearingProvenancePreservesFollowsAndStaleMetadataCannotRestoreTheFlag() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java).build()
        try {
            val artists = database.artistTable
            artists.insertIgnore(Artist("followed", name = "Local artist", bookmarkedAt = 14L))
            artists.insertIgnore(Artist("other", name = "Other"))
            artists.markYouTubeArtistImported("followed", 17L, "cover")
            val followed = checkNotNull(artists.findByIdDirect("followed"))
            assertTrue(followed.isYoutubeArtist)
            assertEquals(14L, followed.bookmarkedAt)
            assertFalse(checkNotNull(artists.findByIdDirect("other")).isYoutubeArtist)

            assertEquals(1, artists.clearYouTubeArtistProvenance())
            artists.update(followed.copy(name = "Updated"))
            artists.upsert(followed.copy(name = "Upserted"))
            val cleared = checkNotNull(artists.findByIdDirect("followed"))
            assertFalse(cleared.isYoutubeArtist)
            assertEquals(14L, cleared.bookmarkedAt)

            artists.markYouTubeArtistImported("other", 23L, null)
            assertTrue(checkNotNull(artists.findByIdDirect("other")).isYoutubeArtist)
            assertEquals(23L, checkNotNull(artists.findByIdDirect("other")).bookmarkedAt)
            assertFalse(checkNotNull(artists.findByIdDirect("followed")).isYoutubeArtist)
        } finally {
            database.close()
        }
    }

    private fun artist(id: String) = Innertube.ArtistItem(
        info = Innertube.Info(id, NavigationEndpoint.Endpoint.Browse(browseId = id)),
        subscribersCountText = null,
        thumbnail = null,
    )
}
