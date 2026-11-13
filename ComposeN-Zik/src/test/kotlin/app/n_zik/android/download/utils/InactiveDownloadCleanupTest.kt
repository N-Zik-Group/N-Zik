package app.n_zik.android.download.utils

import android.app.Application
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.VersionTable
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import app.n_zik.android.utils.profilePreferences
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.components.settings.data.CleanupAction
import app.n_zik.android.components.settings.data.CleanupResult
import app.n_zik.android.components.settings.data.DeferredProfileCleanup
import app.n_zik.android.utils.writeProfileEntries
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.core.rescue.RescueFiles
import java.io.File
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
class InactiveDownloadCleanupTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: ContextWrapper
    private lateinit var index: File

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
        assertTrue(context.writeProfileEntries(listOf("voyage" to "Voyage")))
        context.cacheDir.resolve("exo_downloads").apply { mkdirs() }.resolve("main.exo").writeText("active")
        context.cacheDir.resolve("exo_downloads_voyage").apply { mkdirs() }.let {
            it.resolve("abc.uid").createNewFile()
            it.resolve("span.exo").writeText("offline")
        }
        context.filesDir.resolve("exo_downloads_voyage").apply { mkdirs() }.resolve("inactive.exo").writeText("other root")
        context.cacheDir.resolve("exoplayer_voyage").apply { mkdirs() }.resolve("def.uid").createNewFile()
        index = context.getDatabasePath("exoplayer_internal_voyage.db")
        SQLiteDatabase.openOrCreateDatabase(index, null).use { db ->
            listOf("abc", "def").forEach { uid ->
                VersionTable.setVersion(db, VersionTable.FEATURE_CACHE_CONTENT_METADATA, uid, 1)
                db.execSQL("CREATE TABLE ExoPlayerCacheIndex$uid (id INTEGER)")
            }
            listOf("offline" to Download.STATE_COMPLETED, "pending" to Download.STATE_QUEUED).forEach { (id, state) ->
                val request = DownloadRequest.Builder(id, Uri.parse("https://example.invalid/$id")).build()
                downloadIndex(db).putDownload(
                    Download(request, state, 1, 1, 7, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE),
                )
            }
        }
    }

    private fun downloadIndex(db: SQLiteDatabase) = DefaultDownloadIndex(object : DatabaseProvider {
        override fun getReadableDatabase(): SQLiteDatabase = db
        override fun getWritableDatabase(): SQLiteDatabase = db
    })

    private fun target(): InactiveDownloadCleanup.Target {
        val identity = checkNotNull(profileStorageIdentity(context, "voyage"))
        return checkNotNull(InactiveDownloadCleanup.capture(context, identity))
    }

    @Test
    fun `resumes after index publication without touching active or inactive streaming data`() {
        val confirmed = target()
        assertThrows(IllegalStateException::class.java) {
            InactiveDownloadCleanup.execute(context, confirmed) { error("Interrupted after index publication") }
        }
        assertTrue(context.cacheDir.resolve("exo_downloads_voyage").isDirectory)

        DatabaseRestoreJournal.withProfileStorageMutation(context) {
            InactiveDownloadCleanup.resume(context)
        }

        assertFalse(context.cacheDir.resolve("exo_downloads_voyage").exists())
        assertFalse(context.filesDir.resolve("exo_downloads_voyage").exists())
        assertEquals("active", context.cacheDir.resolve("exo_downloads/main.exo").readText())
        assertTrue(context.cacheDir.resolve("exoplayer_voyage/def.uid").isFile)
        SQLiteDatabase.openDatabase(index.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            downloadIndex(db).getDownloads().use { assertFalse(it.moveToNext()) }
            assertEquals(VersionTable.VERSION_UNSET, VersionTable.getVersion(db, VersionTable.FEATURE_CACHE_CONTENT_METADATA, "abc"))
            assertEquals(1, VersionTable.getVersion(db, VersionTable.FEATURE_CACHE_CONTENT_METADATA, "def"))
        }
    }

    @Test
    fun `changed files after confirmation skip the entire inactive target`() {
        val confirmed = target()
        val source = context.cacheDir.resolve("exo_downloads_voyage/span.exo")
        source.writeText("changed")

        assertFalse(InactiveDownloadCleanup.execute(context, confirmed))
        assertEquals("changed", source.readText())
        SQLiteDatabase.openDatabase(index.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            downloadIndex(db).getDownloads().use { assertTrue(it.moveToNext()) }
        }
    }

    @Test
    fun `Rescue streaming cleanup preserves download spans and queue rows`() {
        RescueFiles.clearCache(context).getOrThrow()
        assertFalse(context.cacheDir.resolve("exoplayer_voyage").exists())
        assertEquals("offline", context.cacheDir.resolve("exo_downloads_voyage/span.exo").readText())
        SQLiteDatabase.openDatabase(index.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            downloadIndex(db).getDownloads().use { assertTrue(it.moveToNext()) }
            assertEquals(1, VersionTable.getVersion(db, VersionTable.FEATURE_CACHE_CONTENT_METADATA, "abc"))
            assertEquals(VersionTable.VERSION_UNSET, VersionTable.getVersion(db, VersionTable.FEATURE_CACHE_CONTENT_METADATA, "def"))
        }
    }

    @Test
    fun `Rescue download cleanup preserves streaming metadata`() {
        RescueFiles.deleteDownloads(context).getOrThrow()
        assertFalse(context.cacheDir.resolve("exo_downloads_voyage").exists())
        assertTrue(context.cacheDir.resolve("exoplayer_voyage/def.uid").isFile)
        SQLiteDatabase.openDatabase(index.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            downloadIndex(db).getDownloads().use { assertFalse(it.moveToNext()) }
            assertEquals(1, VersionTable.getVersion(db, VersionTable.FEATURE_CACHE_CONTENT_METADATA, "def"))
        }
    }

    @Test
    fun `active offline cleanup runs before cache open and skips a changed confirmation`() {
        setActiveProfile("voyage", context)
        val owner = checkNotNull(profileStorageIdentity(context, "voyage"))
        val span = context.cacheDir.resolve("exo_downloads_voyage/span.exo")
        fun schedule() {
            val confirmed = checkNotNull(InactiveDownloadCleanup.capture(context, owner, allowSelected = true))
            assertTrue(DeferredProfileCleanup.scheduleMedia(context, confirmed))
        }
        fun restart() = DatabaseRestoreJournal.withProfileStorageMutation(context) { DeferredProfileCleanup.resume(context) }

        schedule()
        assertTrue(owner to CleanupAction.DOWNLOADS in DeferredProfileCleanup.pendingActions(context))
        span.writeText("changed after confirmation")
        restart()
        assertEquals(listOf(CleanupResult.SKIPPED), DeferredProfileCleanup.lastRun.value)
        assertEquals("changed after confirmation", span.readText())

        schedule()
        restart()
        assertEquals(listOf(CleanupResult.COMPLETED), DeferredProfileCleanup.lastRun.value)
        assertFalse(span.exists())
        assertTrue(context.cacheDir.resolve("exoplayer_voyage/def.uid").isFile)
    }
}
