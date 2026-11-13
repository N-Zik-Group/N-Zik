package app.n_zik.android.core.profiles

import android.app.Application
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.VersionTable
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.writeProfileEntries
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LegacyMediaPublicationTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: ContextWrapper

    @Before
    fun setUp() {
        val files = temporary.newFolder("files")
        val cache = temporary.newFolder("cache")
        val databases = temporary.newFolder("db")
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = files
            override fun getCacheDir(): File = cache
            override fun getDatabasePath(name: String): File = databases.resolve(name)
        }
        context.profilePreferences.edit().clear().commit()
        assertTrue(context.writeProfileEntries(listOf("trip" to "Trip")))
        context.setProfileShares(ProfileDataItem.DOWNLOADS, "trip", true)
        context.setProfileShares(ProfileDataItem.MEDIA_CACHE, "trip", true)
    }

    private fun fixture(state: Int): Pair<File, File> {
        val downloads = context.cacheDir.resolve("exo_downloads").apply { mkdirs() }
        downloads.resolve("abc.uid").createNewFile()
        val span = downloads.resolve("0").apply { mkdirs() }.resolve("5.0.9.v3.exo")
            .apply { writeText("audio") }
        val index = context.getDatabasePath("exoplayer_internal.db")
        SQLiteDatabase.openOrCreateDatabase(index, null).use { db ->
            VersionTable.setVersion(db, VersionTable.FEATURE_CACHE_CONTENT_METADATA, "abc", 1)
            db.execSQL("CREATE TABLE ExoPlayerCacheIndexabc (id INTEGER, key TEXT, metadata BLOB)")
            db.execSQL("INSERT INTO ExoPlayerCacheIndexabc VALUES (5, 'track', X'00000000')")
            val provider = object : DatabaseProvider {
                override fun getReadableDatabase(): SQLiteDatabase = db
                override fun getWritableDatabase(): SQLiteDatabase = db
            }
            val request = DownloadRequest.Builder("track", Uri.parse("https://example.invalid/track")).setCustomCacheKey("track").build()
            DefaultDownloadIndex(provider).putDownload(
                Download(request, state, 1, 1, 5, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE),
            )
        }
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("data_trip.db"), null).use { db ->
            db.execSQL("CREATE TABLE Song (id TEXT)")
            db.execSQL("CREATE TABLE Format (songId TEXT, contentLength INTEGER)")
            db.execSQL("INSERT INTO Song VALUES ('track')")
            db.execSQL("INSERT INTO Format VALUES ('track', 5)")
        }
        return span to index
    }

    @Test
    fun `verified downloads publish into an independent root and preserve the original`() {
        val (span, index) = fixture(Download.STATE_COMPLETED)
        val original = index.readBytes()
        LegacyMediaPublication.resume(context)

        assertFalse(context.profileShares(ProfileDataItem.DOWNLOADS, "trip"))
        assertFalse(context.profileShares(ProfileDataItem.MEDIA_CACHE, "trip"))
        val destination = context.cacheDir.resolve("exo_downloads_trip")
        val scratch = temporary.newFolder("scratch")
        val cache = ReadOnlyProfileCache.open(destination, context.getDatabasePath("exoplayer_internal_trip.db"), scratch)
        assertTrue(cache.isCached("track", 0, 5))
        destination.resolve("0/5.0.9.v3.exo").delete()
        assertEquals("audio", span.readText())
        assertArrayEquals(original, index.readBytes())
        assertTrue(context.filesDir.resolve("protected-profile-history/media3").listFiles().orEmpty().any { archive ->
            archive.resolve("downloads-system/0/5.0.9.v3.exo").isFile
        })
        val archive = LegacyMediaPublication.archives(context).single()
        val target = checkNotNull(profileStorageIdentity(context, "trip"))
        assertThrows(IllegalStateException::class.java) { LegacyMediaPublication.recover(context, archive, target) }
        assertFalse(LegacyMediaPublication.hasPendingRescueRecovery(context))
        assertTrue(destination.deleteRecursively())
        LegacyMediaPublication.recover(context, archive, target)
        assertEquals("audio", destination.resolve("0/5.0.9.v3.exo").readText())
        assertArrayEquals(original, index.readBytes())
    }

    @Test
    fun `pending shared queue remains read only and no private index is published`() {
        val (span, index) = fixture(Download.STATE_QUEUED)
        val original = index.readBytes()
        LegacyMediaPublication.resume(context)

        assertTrue(context.profileShares(ProfileDataItem.DOWNLOADS, "trip"))
        assertTrue(LegacyMediaAccess.isProtected(context, "trip", ProfileDataItem.DOWNLOADS))
        assertFalse(context.getDatabasePath("exoplayer_internal_trip.db").exists())
        assertEquals("audio", span.readText())
        assertArrayEquals(original, index.readBytes())
    }

    @Test
    fun `verified streaming separates while the historical download queue remains pending`() {
        val (sourceDownload, sourceIndex) = fixture(Download.STATE_QUEUED)
        val streaming = context.cacheDir.resolve("exoplayer").apply { mkdirs() }
        streaming.resolve("def.uid").createNewFile()
        val streamSpan = streaming.resolve("0").apply { mkdirs() }.resolve("8.0.9.v3.exo").apply { writeText("stream") }
        SQLiteDatabase.openDatabase(sourceIndex.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            addCacheIndex(db, "def", 8, "stream")
        }
        val originalIndex = sourceIndex.readBytes()
        val ownIndex = context.getDatabasePath("exoplayer_internal_trip.db")

        LegacyMediaPublication.resume(context)
        LegacyMediaPublication.resume(context)

        assertTrue(context.profileShares(ProfileDataItem.DOWNLOADS, "trip"))
        assertFalse(context.profileShares(ProfileDataItem.MEDIA_CACHE, "trip"))
        assertArrayEquals(originalIndex, sourceIndex.readBytes())
        assertEquals("audio", sourceDownload.readText())
        assertEquals("stream", streamSpan.readText())
        val separated = ReadOnlyProfileCache.open(
            context.cacheDir.resolve("exoplayer_trip"), ownIndex, temporary.newFolder("verify-stream"),
        )
        assertTrue(separated.isCached("stream", 0, 6))
        SQLiteDatabase.openDatabase(ownIndex.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(VersionTable.VERSION_UNSET, VersionTable.getVersion(db, VersionTable.FEATURE_OFFLINE, ""))
            assertEquals(VersionTable.VERSION_UNSET, VersionTable.getVersion(db, VersionTable.FEATURE_CACHE_CONTENT_METADATA, "abc"))
        }
    }

    @Test
    fun `download only migration preserves the independent streaming index and resumes a staged root`() {
        val (sourceSpan, sourceIndex) = fixture(Download.STATE_COMPLETED)
        context.setProfileShares(ProfileDataItem.MEDIA_CACHE, "trip", false)
        val privateStreaming = context.cacheDir.resolve("exoplayer_trip").apply { mkdirs() }
        privateStreaming.resolve("def.uid").createNewFile()
        privateStreaming.resolve("0").apply { mkdirs() }.resolve("8.0.9.v3.exo").writeText("stream")
        val ownIndex = context.getDatabasePath("exoplayer_internal_trip.db")
        SQLiteDatabase.openOrCreateDatabase(ownIndex, null).use { db ->
            addCacheIndex(db, "def", 8, "stream")
        }
        // Mimic an interruption after the selected cache directory was published and before the index.
        val staged = context.cacheDir.resolve("exo_downloads_trip")
        assertTrue(context.cacheDir.resolve("exo_downloads").copyRecursively(staged))
        val originalIndex = sourceIndex.readBytes()

        LegacyMediaPublication.resume(context)

        assertFalse(context.profileShares(ProfileDataItem.DOWNLOADS, "trip"))
        assertArrayEquals(originalIndex, sourceIndex.readBytes())
        assertEquals("audio", sourceSpan.readText())
        val scratch = temporary.newFolder("verify-download")
        assertTrue(ReadOnlyProfileCache.open(staged, ownIndex, scratch).isCached("track", 0, 5))
        assertTrue(ReadOnlyProfileCache.open(privateStreaming, ownIndex, scratch).isCached("stream", 0, 6))
    }

    private fun addCacheIndex(database: SQLiteDatabase, uid: String, id: Int, key: String) {
        VersionTable.setVersion(database, VersionTable.FEATURE_CACHE_CONTENT_METADATA, uid, 1)
        database.execSQL("CREATE TABLE ExoPlayerCacheIndex$uid (id INTEGER, key TEXT, metadata BLOB)")
        database.execSQL("INSERT INTO ExoPlayerCacheIndex$uid VALUES (?, ?, X'00000000')", arrayOf<Any>(id, key))
    }
}
