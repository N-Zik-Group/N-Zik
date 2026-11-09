package app.n_zik.android.components.import

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import app.n_zik.android.components.image.ownedCoverMutationMutex
import app.n_zik.android.core.backup.backupJpeg
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.backup.openBackupFixture
import app.n_zik.android.core.rewind.RewindPostImportRegenerationWorker
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
// runBlocking (not a test dispatcher): importTo is a plain suspend flow and the tests
// only assert on files and the stubbed Database / worker entry points.
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Tests the per-profile database replacement behind every database import
 * ([ImportDatabase.importTo]): the imported bytes land in the target profile's own
 * data file (`data_<id>.db`) — never the base `data.db` — and only that profile's
 * `-wal`/`-shm` side files are removed. The live-database side effects (checkpoint +
 * close + the post-import rewind regeneration) run only when the target is the
 * active profile; an import into any other profile must not touch the running
 * database at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ImportDatabaseImportToTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var files: File
    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var profilePrefs: SharedPreferences
    private lateinit var uri: Uri

    private lateinit var importedBytes: ByteArray

    @Before
    fun setup() {
        files = tmp.newFolder()
        val backup = tmp.newFile()
        val application = RuntimeEnvironment.getApplication()
        openBackupFixture(application, backup).use { db ->
            db.execSQL("INSERT INTO Song (id, title, totalPlayTimeMs) VALUES ('song', 'Song', 0)")
            db.execSQL("INSERT INTO Playlist (id, name, isEditable) VALUES (7, 'Playlist', 1)")
        }
        importedBytes = backup.readBytes()
        context = mockk()
        every { context.applicationContext } returns application
        every { context.assets } returns application.assets
        every { context.cacheDir } returns tmp.newFolder()
        every { context.filesDir } returns tmp.newFolder()
        resolver = mockk()
        every { context.contentResolver } returns resolver
        uri = mockk()
        every { resolver.openInputStream(uri) } answers {
            ByteArrayInputStream(importedBytes)
        }
        // The base profile is the active one.
        profilePrefs = mockk()
        every { profilePrefs.getString(any(), any()) } answers { secondArg<String?>() }
        every { profilePrefs.getBoolean(any(), any()) } answers { secondArg<Boolean>() }
        every { context.getSharedPreferences("profile_preferences", Context.MODE_PRIVATE) } returns profilePrefs

        mockkObject(Database)
        every { Database.checkpoint() } returns 0
        every { Database.close() } just Runs
        mockkObject(RewindPostImportRegenerationWorker)
        every { RewindPostImportRegenerationWorker.schedule(any()) } just Runs
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun dbPath(name: String): File {
        val f = File(files, name)
        every { context.getDatabasePath(name) } returns f
        return f
    }

    private fun sideFile(name: String): File {
        val f = File(files, name)
        f.writeBytes(byteArrayOf(9))
        return f
    }

    @Test
    fun anInactiveProfileImportReplacesOnlyThatProfilesFiles() = runBlocking {
        val baseDb = dbPath("data.db")
        baseDb.writeBytes(byteArrayOf(0))
        val baseWal = sideFile("data.db-wal")
        val baseShm = sideFile("data.db-shm")
        val workDb = dbPath("data_work.db")
        workDb.writeBytes(byteArrayOf(7))
        val workWal = sideFile("data_work.db-wal")
        val workShm = sideFile("data_work.db-shm")

        ImportDatabase.importTo(context, uri, "work")

        // The bytes land in the work profile's file, the base profile's files are
        // untouched.
        assertArrayEquals(importedBytes, workDb.readBytes())
        assertArrayEquals(byteArrayOf(0), baseDb.readBytes())
        // Only the work profile's side files are removed.
        assertFalse(workWal.exists())
        assertFalse(workShm.exists())
        assertTrue(baseWal.exists())
        assertTrue(baseShm.exists())
        // The running database is not checkpointed, closed, nor regenerated.
        verify(exactly = 0) { Database.checkpoint() }
        verify(exactly = 0) { Database.close() }
        verify(exactly = 0) { RewindPostImportRegenerationWorker.schedule(any()) }
    }

    @Test
    fun anActiveProfileImportChecksPointsClosesAndSchedulesRegeneration() = runBlocking {
        val baseDb = dbPath("data.db")
        baseDb.writeBytes(byteArrayOf(0))
        val baseWal = sideFile("data.db-wal")

        ImportDatabase.importTo(context, uri, "default")

        assertArrayEquals(importedBytes, baseDb.readBytes())
        assertFalse(baseWal.exists())
        verify(exactly = 1) { Database.checkpoint() }
        verify(exactly = 1) { Database.close() }
        verify(exactly = 1) { RewindPostImportRegenerationWorker.schedule(context) }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `ZIP restore waits for cover edit rollback before replacing the image`() = runBlocking {
        val baseDb = dbPath("data.db").apply { writeText("current") }
        val thumbnail = context.filesDir.resolve("thumbnail/playlist_7").apply {
            parentFile?.mkdirs()
            writeText("pending edit")
        }
        val restored = backupJpeg(Color.BLUE)
        val archive = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zip ->
                for ((name, bytes) in listOf("database.db" to importedBytes, "thumbnail/playlist_7" to restored)) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        every { resolver.openInputStream(uri) } answers { ByteArrayInputStream(archive) }

        val import = ownedCoverMutationMutex.withLock {
            val pending = launch(start = CoroutineStart.UNDISPATCHED) {
                ImportDatabase.importTo(context, uri, "default")
            }
            verify(exactly = 0) { Database.close() }
            assertEquals("current", baseDb.readText())
            thumbnail.writeText("before the edit")
            pending
        }
        import.join()

        verify(exactly = 1) { Database.close() }
        assertArrayEquals(restored, thumbnail.readBytes())
    }

    @Test
    fun aFailedCopyLeavesTheExistingDatabaseIntact() = runBlocking {
        val baseDb = dbPath("data.db")
        baseDb.writeBytes(byteArrayOf(9))
        val wal = sideFile("data.db-wal")
        val shm = sideFile("data.db-shm")
        var firstRead = true
        val failing = object : ByteArrayInputStream(importedBytes) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!firstRead) {
                    val staged = context.cacheDir.listFiles().orEmpty().single().resolve("input")
                    assertTrue("The failure happens after the staged write starts", staged.length() > 0)
                    throw IOException("source revoked")
                }
                firstRead = false
                return super.read(b, off, len)
            }
        }
        every { resolver.openInputStream(uri) } returns failing

        val error = runCatching { ImportDatabase.importTo(context, uri, "default") }.exceptionOrNull()

        assertTrue("The source read failure must surface to the caller", error is IOException)
        assertArrayEquals(byteArrayOf(9), baseDb.readBytes())
        assertArrayEquals(byteArrayOf(9), wal.readBytes())
        assertArrayEquals(byteArrayOf(9), shm.readBytes())
        assertTrue("The partial temp file is cleaned up", context.cacheDir.listFiles().orEmpty().isEmpty())
        verify(exactly = 0) { Database.close() }
    }
}
