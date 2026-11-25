package app.n_zik.android.core.database

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.models.Song
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LocalSongSearchQueryTest {
    // JUnit needs a synchronous entry point for the suspend Room DAO calls.
    @Test fun `search wildcards are literal in local song titles and artists`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java).build()
        try {
            val songs = db.songTable
            songs.upsert(Song.makePlaceholder("percent").copy(title = "100% vocals"))
            songs.upsert(Song.makePlaceholder("underscore").copy(title = "Another song", artistsText = "Artist_Name"))
            songs.upsert(Song.makePlaceholder("ordinary").copy(title = "100 vocals", artistsText = "Artist Name"))

            assertEquals(listOf("percent"), songs.findAllTitleArtistContainsLiteral("%").first().map(Song::id))
            assertEquals(listOf("underscore"), songs.findAllTitleArtistContainsLiteral("ARTIST_N").first().map(Song::id))
            assertEquals(setOf("percent", "underscore", "ordinary"),
                songs.findAllTitleArtistContains("%").first().map(Song::id).toSet())
        } finally {
            db.close()
        }
    }
}
