package app.n_zik.android.core.backup

import android.app.Application
import android.content.ContextWrapper
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DatabaseExportStagingTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `cleanup protects active preparation and picker handoff then expires an abandoned picker`() {
        val context = context()
        val now = System.currentTimeMillis()
        val future = now + TimeUnit.DAYS.toMillis(8)
        val active = DatabaseExportStaging.create(context)
        val expired = DatabaseExportStaging.create(context)
        try {
            expired.directory.resolve("backup.zip").writeText("pending")
            expired.handOffToPicker()

            DatabaseExportStaging.cleanAbandoned(context, now)
            assertTrue(active.directory.isDirectory)
            assertTrue(expired.directory.isDirectory)

            DatabaseExportStaging.cleanAbandoned(context, future)
            assertTrue(active.directory.isDirectory)
            assertFalse(expired.directory.exists())
        } finally {
            active.close()
            expired.close()
        }
    }

    @Test
    fun `picker result reacquires its payload and protects the write from cleanup`() {
        val context = context()
        val pending = DatabaseExportStaging.create(context)
        val file = pending.directory.resolve("backup.zip").apply { writeText("pending payload") }
        pending.handOffToPicker()
        DatabaseExportStaging.cleanAbandoned(context)

        DatabaseBackup.resumeExport(context, file.path).use { resumed ->
            DatabaseExportStaging.cleanAbandoned(context, System.currentTimeMillis() + TimeUnit.DAYS.toMillis(8))
            assertEquals("pending payload", resumed.file.readText())
        }
        assertFalse(pending.directory.exists())
    }

    @Test
    fun `cleanup skips an externally owned stage until its lease releases`() {
        val context = context()
        val stage = context.cacheDir.resolve("database-backup-external.stage").apply { mkdir() }
        stage.resolve("database.db").writeText("payload")
        RandomAccessFile(stage.resolve(".owner"), "rw").use { owner ->
            owner.channel.lock().use {
                DatabaseExportStaging.cleanAbandoned(context)
                assertTrue(stage.exists())
            }
        }

        DatabaseExportStaging.cleanAbandoned(context)
        assertFalse(stage.exists())
    }

    private fun context(): ContextWrapper {
        val files = tmp.newFolder("files")
        val cache = tmp.newFolder("cache")
        return object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = files
            override fun getCacheDir(): File = cache
        }
    }
}
