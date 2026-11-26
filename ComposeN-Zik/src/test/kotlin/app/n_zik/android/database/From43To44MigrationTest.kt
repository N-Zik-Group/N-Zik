package app.n_zik.android.database

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.migration.From43To44Migration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class From43To44MigrationTest {
    @Test
    fun `saved playlists and memberships survive and source refresh preserves author override`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "playlist-owner-upgrade-test"
        context.deleteDatabase(name)
        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(43) {
                override fun onCreate(db: SupportSQLiteDatabase) = applySchemaFixture(db, loadSchemaFixture(43))
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase.apply {
            execSQL("INSERT INTO Playlist (id, name, browseId, isEditable, isYoutubePlaylist, isAutoSync, position) VALUES (1, 'Saved playlist', 'VLPLold', 0, 1, 1, 3)")
            execSQL("INSERT INTO Song (id, title, totalPlayTimeMs, position, isYoutubeSong) VALUES ('track', 'Track', 10, 0, 0)")
            execSQL("INSERT INTO SongPlaylistMap (songId, playlistId, position, dateAdded) VALUES ('track', 1, 0, 1700000000000)")
        }
        helper.close()

        val db = Room.databaseBuilder(context, DatabaseInitializer::class.java, name)
            .addMigrations(From43To44Migration).openHelperFactory(factory)
            .allowMainThreadQueries().build()
        try {
            val table = db.playlistTable
            // The JUnit entrypoint bridges the suspend Room methods.
            runBlocking {
                val original = checkNotNull(table.findById(1).first())
                assertEquals("Saved playlist", original.name)
                assertEquals(3, original.position)
                assertTrue(original.isYoutubePlaylist)
                assertTrue(original.isAutoSync)
                assertNull(original.displayAuthorName())
                assertEquals(1, table.updateSourceAuthor(1, "VLPLold", "UCowner", "Owner"))
                assertEquals(1, table.updateAuthorNameOverride(1, "My label"))
                assertEquals(1, table.updateSourceAuthor(1, "VLPLold", "UCowner", "Updated online"))
                assertEquals("My label", table.findById(1).first()?.displayAuthorName())
                assertEquals(1, table.updateBrowseId(1, "VLPLnew"))
                assertEquals(0, table.updateSourceAuthor(1, "VLPLold", "UCowner", "Stale"))
                val relinked = checkNotNull(table.findById(1).first())
                assertNull(relinked.authorBrowseId)
                assertNull(relinked.authorName)
                assertEquals("My label", relinked.authorNameOverride)
            }
            db.query("SELECT songId FROM SongPlaylistMap WHERE playlistId = 1", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("track", it.getString(0))
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
