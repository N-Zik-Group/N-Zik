package app.n_zik.android.database

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.migration.From42To43Migration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class From42To43MigrationTest {
    @Test
    fun `Room upgrades v42 without destructive fallback and preserves library data`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "search-history-upgrade-test"
        context.deleteDatabase(name)
        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(42) {
                    override fun onCreate(db: SupportSQLiteDatabase) = applySchemaFixture(db, loadSchemaFixture(42))
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        helper.writableDatabase.apply {
            execSQL("INSERT INTO SearchQuery (query) VALUES ('obsolete search')")
            execSQL("INSERT INTO Song (id, title, totalPlayTimeMs, position, isYoutubeSong) VALUES ('s1', 'song', 10, 0, 0)")
            execSQL("INSERT INTO Playlist (id, name, isEditable, isYoutubePlaylist, isAutoSync, position) VALUES (1, 'playlist', 1, 0, 0, -1)")
            execSQL("INSERT INTO SongPlaylistMap (songId, playlistId, position, dateAdded) VALUES ('s1', 1, 0, 1700000000000)")
            execSQL("INSERT INTO Event (songId, timestamp, playTime) VALUES ('s1', 1700000000000, 1000)")
            execSQL("INSERT INTO Format (songId, itag, mimeType, bitrate, contentLength, lastModified) VALUES ('s1', 251, 'audio/webm', 128000, 1000, 0)")
        }
        helper.close()
        val db = Room.databaseBuilder(context, DatabaseInitializer::class.java, name)
            .addMigrations(From42To43Migration).openHelperFactory(factory)
            .allowMainThreadQueries().build()
        try {
            db.query("PRAGMA user_version", null).use { c ->
                c.moveToFirst()
                assertEquals(43, c.getInt(0))
            }
            db.query("SELECT name FROM sqlite_master WHERE name IN ('SearchQuery', 'index_SearchQuery_query')", null).use {
                assertFalse(it.moveToFirst())
            }
            listOf("Song", "Playlist", "SongPlaylistMap", "Event", "Format").forEach { table ->
                db.query("SELECT COUNT(*) FROM `$table`", null).use { c ->
                    c.moveToFirst()
                    assertEquals("$table rows survive", 1, c.getInt(0))
                }
            }
            db.query("SELECT title FROM Song WHERE id = 's1'", null).use { c ->
                c.moveToFirst()
                assertEquals("song", c.getString(0))
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
