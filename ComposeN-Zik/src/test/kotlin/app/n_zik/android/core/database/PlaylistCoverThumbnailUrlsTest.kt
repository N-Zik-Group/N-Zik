package app.n_zik.android.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.Song
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaylistCoverThumbnailUrlsTest {
    private lateinit var db: DatabaseInitializer

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            DatabaseInitializer::class.java,
        ).build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    // JUnit's synchronous test method needs a bridge to Room's suspend methods.
    @Test
    fun `covers keep the last four URLs in play time and mapping insertion order`() = runBlocking<Unit> {
        db.playlistTable.insertIgnore(Playlist(id = 1, name = "Main"))
        db.playlistTable.insertIgnore(Playlist(id = 2, name = "Secondary"))
        db.playlistTable.insertIgnore(Playlist(id = 3, name = "Without covers"))
        val tracks = listOf(
            Song(id = "top", title = "Top", durationText = null, thumbnailUrl = "top.png", totalPlayTimeMs = 20),
            Song(id = "tie1", title = "Tie 1", durationText = null, thumbnailUrl = "tie1.png", totalPlayTimeMs = 10),
            Song(id = "tie2", title = "Tie 2", durationText = null, thumbnailUrl = "tie2.png", totalPlayTimeMs = 10),
            Song(id = "tie3", title = "Tie 3", durationText = null, thumbnailUrl = "same.png", totalPlayTimeMs = 10),
            Song(id = "tie4", title = "Tie 4", durationText = null, thumbnailUrl = "same.png", totalPlayTimeMs = 10),
            Song(id = "noCover", title = "No cover", durationText = null, thumbnailUrl = null, totalPlayTimeMs = 30),
            Song(id = "other", title = "Other", durationText = null, thumbnailUrl = "other.png", totalPlayTimeMs = 40),
        )
        tracks.forEach { db.songTable.upsert(it) }
        val maps = db.songPlaylistMapTable
        tracks.dropLast(1).forEachIndexed { position, song ->
            maps.mapAtPosition(song.id, playlistId = 1, position = tracks.lastIndex - position)
        }
        maps.mapAtPosition("other", playlistId = 2, position = 0)
        maps.mapAtPosition("noCover", playlistId = 3, position = 0)

        assertEquals(listOf("tie2.png", "same.png", "same.png", "top.png"), maps.coverThumbnailUrls(1).first())
        assertEquals(listOf("other.png"), maps.coverThumbnailUrls(2).first())
        assertEquals(emptyList<String>(), maps.coverThumbnailUrls(3).first())
    }
}
