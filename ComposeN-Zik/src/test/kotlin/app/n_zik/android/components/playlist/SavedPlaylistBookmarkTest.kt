package app.n_zik.android.components.playlist

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.models.Playlist
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SavedPlaylistBookmarkTest {
    private lateinit var database: DatabaseInitializer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java).build()
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns database
    }

    @After
    fun tearDown() {
        unmockkObject(DatabaseInitializer.Companion)
        database.close()
    }

    @Test
    fun `committing a bookmark keeps newer playlist metadata and rejects stale state`() = runBlocking {
        val initial = Playlist(name = "Source title", browseId = "VLPLsource")
        val expected = initial.copy(id = database.playlistTable.insert(initial))
        val edited = expected.copy(name = "Updated title", authorName = "Source owner")
        database.playlistTable.update(edited)

        assertNull(commitSavedPlaylistBookmark(expected, true) { false })
        assertEquals(edited, database.playlistTable.findById(edited.id).first())

        val bookmarked = edited.copy(isYoutubePlaylist = true)
        assertEquals(bookmarked, commitSavedPlaylistBookmark(expected, true) { true })
        assertNull(commitSavedPlaylistBookmark(expected, true) { true })

        val relinked = bookmarked.copy(browseId = "VLPLnew")
        database.playlistTable.update(relinked)
        assertNull(commitSavedPlaylistBookmark(bookmarked, false) { true })
        assertEquals(relinked, database.playlistTable.findById(edited.id).first())
    }
}
