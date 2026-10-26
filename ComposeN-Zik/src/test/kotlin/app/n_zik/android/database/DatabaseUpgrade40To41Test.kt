package app.n_zik.android.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.migration.From40To41Migration
import app.n_zik.android.core.database.migration.From41To42Migration
import app.n_zik.android.core.database.migration.From42To43Migration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrade 40 -> 42 through the real Room builder, not a direct `migrate()` call: Room must find
 * the registered 40 -> 41 and 41 -> 42 migrations, apply them, and pass post-migration schema
 * verification (tables, column defaults, indexes, foreign keys and views).
 *
 * Two starting states are covered:
 * - a deployed v40 database (frozen `schemas/.../40.json`, no column defaults) — production users
 * - a dev v40 database already carrying the entity-declared column defaults — the device state
 *   behind the 2026-09-18 crash log
 *
 * The fixtures are copies of the frozen schema files (deployed schemas are never edited).
 * Uses JUnit 4 annotations because Robolectric's @RunWith is a JUnit 4 concept.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseUpgrade40To41Test {

    private val dbName = Database.FILE_NAME
    private lateinit var context: Context

    @Before
    fun createContext() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun cleanup() {
        context.getDatabasePath(dbName).delete()
    }

    @Test
    fun `a deployed v40 database without column defaults upgrades to v42 keeping its rows`() {
        assertUpgrade(seedFrom = 40)
    }

    @Test
    fun `a dev v40 database with column defaults upgrades to v42 keeping its rows`() {
        assertUpgrade(seedFrom = 41)
    }

    private fun assertUpgrade(seedFrom: Int) {
        val fixture40 = loadSchemaFixture(40)
        val fixture41 = loadSchemaFixture(41)
        val helperFactory = FrameworkSQLiteOpenHelperFactory()

        // Pre-seed a v40-shaped database file. Lyrics always comes from the v40 fixture because
        // the migration adds its lastFetchedAt column; the other tables come from seedFrom.
        val helper = helperFactory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(40) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val rawDb = helper.writableDatabase
        applySchemaFixture(
            rawDb,
            if (seedFrom == 41) fixture41 else fixture40,
            lyricsCreateSql = lyricsCreateSqlOf(fixture40)
        )
        rawDb.execSQL("PRAGMA user_version = 40")
        rawDb.execSQL("INSERT INTO Song (id, title, totalPlayTimeMs, position, isYoutubeSong) VALUES ('s1', 't', 0, 0, 0)")
        rawDb.execSQL("INSERT INTO Playlist (id, name, isEditable, isYoutubePlaylist, isAutoSync, position) VALUES (1, 'p', 1, 0, 0, -1)")
        rawDb.execSQL("INSERT INTO SongPlaylistMap (songId, playlistId, position, dateAdded) VALUES ('s1', 1, 0, 1700000000000)")
        rawDb.execSQL("INSERT INTO Lyrics (songId, type, data, isEdited) VALUES ('s1', 'Karaoke', '<k>', 0)")
        rawDb.close()
        helper.close()

        val db = Room.databaseBuilder(context, DatabaseInitializer::class.java, dbName)
            .addMigrations(From40To41Migration, From41To42Migration, From42To43Migration)
            .openHelperFactory(helperFactory)
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(43, db.query("PRAGMA user_version", null).use { it.moveToFirst(); it.getInt(0) })

            db.query("SELECT songId, type, data, isEdited, lastFetchedAt FROM Lyrics", null).use { c ->
                assertEquals(1, c.count)
                c.moveToNext()
                assertEquals("s1", c.getString(0))
                assertEquals("Karaoke", c.getString(1))
                assertEquals("<k>", c.getString(2))
                assertEquals(0, c.getInt(3))
                assertNull(c.getString(4))
            }

            // The rebuilt tables carry the entity-declared defaults after the upgrade.
            assertEquals("-1", db.defaultOf("Song", "position"))
            assertEquals("0", db.defaultOf("Song", "isYoutubeSong"))
            assertEquals("NULL", db.defaultOf("Artist", "genres"))
            assertEquals("-1", db.defaultOf("Playlist", "position"))
            assertEquals("NULL", db.defaultOf("SongPlaylistMap", "dateAdded"))

            // Rows and indexes survived the rebuild.
            db.query("SELECT songId, playlistId, position, dateAdded FROM SongPlaylistMap", null).use { c ->
                assertEquals(1, c.count)
                c.moveToNext()
                assertEquals("s1", c.getString(0))
                assertEquals(1, c.getInt(1))
                assertEquals(0, c.getInt(2))
                assertEquals(1700000000000L, c.getLong(3))
            }
            db.query("PRAGMA index_list('SongPlaylistMap')", null).use { c ->
                val indexNames = LinkedHashSet<String>()
                while (c.moveToNext()) indexNames.add(c.getString(1))
                assertTrue(indexNames.contains("index_SongPlaylistMap_songId"))
                assertTrue(indexNames.contains("index_SongPlaylistMap_playlistId"))
            }
            assertTrue(db.query("SELECT name FROM sqlite_master WHERE type = 'view' AND name = 'SortedSongPlaylistMap'", null).use { c -> c.moveToFirst() })
        } finally {
            db.close()
        }
    }

}

private fun androidx.room.RoomDatabase.defaultOf(table: String, column: String): String {
    return query("PRAGMA table_info($table)", null).use { c ->
        var dflt = ""
        while (c.moveToNext()) {
            if (c.getString(1) == column) dflt = c.getString(4) ?: ""
        }
        dflt
    }
}
