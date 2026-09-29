package app.n_zik.android.components.import

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.rewind.RewindPostImportRegenerationWorker
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
// runBlocking (not a test dispatcher): importTo is a plain suspend flow and the tests
// only assert on files and the stubbed Database / worker entry points.
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

/**
 * Tests the per-profile database replacement behind every database import
 * ([ImportDatabase.importTo]): the imported bytes land in the target profile's own
 * data file (`data_<id>.db`) — never the base `data.db` — and only that profile's
 * `-wal`/`-shm` side files are removed. The live-database side effects (checkpoint +
 * close + the post-import rewind regeneration) run only when the target is the
 * active profile; an import into any other profile must not touch the running
 * database at all.
 */
class ImportDatabaseImportToTest {

    private lateinit var files: File
    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var profilePrefs: SharedPreferences
    private lateinit var uri: Uri

    private val importedBytes = byteArrayOf(1, 2, 3, 4)

    @BeforeEach
    fun setup() {
        files = Files.createTempDirectory("nzik-import-db").toFile()
        context = mockk()
        every { context.applicationContext } returns context
        resolver = mockk()
        every { context.contentResolver } returns resolver
        uri = mockk()
        every { resolver.openInputStream(uri) } answers {
            ByteArrayInputStream(importedBytes)
        }
        // The base profile is the active one.
        profilePrefs = mockk()
        every { profilePrefs.getString(any(), any()) } answers { secondArg<String?>() }
        every { context.getSharedPreferences("profile_preferences", Context.MODE_PRIVATE) } returns profilePrefs

        mockkObject(Database)
        every { Database.checkpoint() } returns 0
        every { Database.close() } just Runs
        mockkObject(RewindPostImportRegenerationWorker)
        every { RewindPostImportRegenerationWorker.schedule(any()) } just Runs
    }

    @AfterEach
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
}
