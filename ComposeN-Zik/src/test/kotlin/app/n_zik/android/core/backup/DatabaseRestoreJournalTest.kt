package app.n_zik.android.core.backup

import android.app.Application
import android.content.ContextWrapper
import app.n_zik.android.MainApplication
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DatabaseRestoreJournalTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `startup rolls back an interrupted restore even after the database was replaced`() {
        val files = tmp.newFolder("files")
        val databases = tmp.newFolder("databases")
        val db = databases.resolve("data_work.db").apply { writeText("new database") }
        val wal = databases.resolve("data_work.db-wal")
        val images = files.resolve("thumbnail").apply { mkdir() }
        val replaced = images.resolve("playlist_7").apply { writeText("new image") }
        val created = images.resolve("playlist_8").apply { writeText("newly created image") }
        val untouched = images.resolve("playlist_9").apply { writeText("already rolled back") }
        val journal = files.resolve("database-restore-journal").apply { mkdir() }
        pending(journal, replaced to true, created to false, untouched to true, wal to true, db to true)
        journal.resolve("previous-0").writeText("old image")
        journal.resolve("previous-3").writeText("old WAL")
        journal.resolve("previous-4").writeText("old database")
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir() = files
            override fun getDatabasePath(name: String) = databases.resolve(name)
        }

        ShadowProcess.setProcessName(context.packageName)
        val application = MainApplication()
        MainApplication::class.java.getDeclaredMethod("attachBaseContext", android.content.Context::class.java)
            .apply { isAccessible = true }
            .invoke(application, context)
        DatabaseRestoreJournal.onProcessStart(context, mainProcess = false)

        assertEquals("old database", db.readText())
        assertEquals("old WAL", wal.readText())
        assertEquals("old image", replaced.readText())
        assertEquals("already rolled back", untouched.readText())
        assertFalse(created.exists())
        assertFalse(journal.exists())
    }

    @Test
    fun `startup discards backups from a completed restore without undoing it`() {
        val files = tmp.newFolder("files")
        val databases = tmp.newFolder("databases")
        val db = databases.resolve("data.db").apply { writeText("new database") }
        val journal = files.resolve("database-restore-journal").apply { mkdir() }
        pending(journal, db to true)
        check(journal.resolve("pending").renameTo(journal.resolve("committed")))
        journal.resolve("previous-0").writeText("old database")

        DatabaseRestoreJournal.Transaction(files, databases).recover()

        assertEquals("new database", db.readText())
        assertFalse(journal.exists())
    }

    @Test
    fun `failed installation restores the original database sidecars and pictures`() {
        val files = tmp.newFolder("files")
        val databases = tmp.newFolder("databases")
        val db = databases.resolve("data.db").apply { writeText("old database") }
        val wal = databases.resolve("data.db-wal").apply { writeText("old WAL") }
        val thumbnail = files.resolve("thumbnail").apply { mkdir() }
            .resolve("playlist_7").apply { writeText("old image") }
        val stagedImage = tmp.newFile().apply { writeText("new image") }
        val missingDb = db.resolveSibling("missing")

        assertThrows(Exception::class.java) {
            DatabaseRestoreJournal.install(files, db, linkedMapOf(thumbnail to stagedImage, wal to null, db to missingDb))
        }

        assertEquals("old database", db.readText())
        assertEquals("old WAL", wal.readText())
        assertEquals("old image", thumbnail.readText())
        assertFalse(files.resolve("database-restore-journal").exists())
    }

    @Test
    fun `successful installation removes stale sidecars`() {
        val files = tmp.newFolder("files")
        val databases = tmp.newFolder("databases")
        val db = databases.resolve("data.db").apply { writeText("old database") }
        val wal = databases.resolve("data.db-wal").apply { writeText("old WAL") }
        val shm = databases.resolve("data.db-shm").apply { writeText("old SHM") }
        val newDb = tmp.newFile().apply { writeText("new database") }

        DatabaseRestoreJournal.install(files, db, linkedMapOf(wal to null, shm to null, db to newDb))

        assertEquals("new database", db.readText())
        assertFalse(wal.exists())
        assertFalse(shm.exists())
        assertFalse(newDb.exists())
        assertFalse(files.resolve("database-restore-journal").exists())
    }

    @Test
    fun `main process lease prevents rescue from replacing the live database`() {
        val files = tmp.newFolder("files")
        val databases = tmp.newFolder("databases")
        val db = databases.resolve("data.db").apply { writeText("live database") }
        val newDb = tmp.newFile().apply { writeText("new database") }
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir() = files
        }

        RandomAccessFile(files.resolve("database-main.lock"), "rw").use { handle ->
            handle.channel.lock().use {
                assertThrows(IllegalStateException::class.java) {
                    DatabaseRestoreJournal.install(files, db, mapOf(db to newDb)) {
                        DatabaseRestoreJournal.requireMainProcessStopped(context)
                    }
                }
            }
        }

        assertEquals("live database", db.readText())
        assertEquals("new database", newDb.readText())
        assertFalse(files.resolve("database-restore-journal").exists())
    }

    @Test
    fun `cleanup keeps active imports and pending exports but discards orphaned imports`() {
        val files = tmp.newFolder("files")
        val cache = tmp.newFolder("cache")
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir() = files
            override fun getCacheDir() = cache
        }
        val orphan = cache.resolve("database-import-orphan.stage").apply { mkdir() }
        val trash = cache.resolve("database-import-retired.trash").apply { mkdir() }
        val export = cache.resolve("database-backup-picker.stage").apply { mkdir() }

        DatabaseRestoreJournal.createImportStage(context).use { active ->
            DatabaseRestoreJournal.cleanAbandonedImports(context)

            assertFalse(orphan.exists())
            assertFalse(trash.exists())
            assertEquals(setOf(active.directory.name, export.name), cache.list().orEmpty().toSet())
        }
        assertEquals(listOf(export.name), cache.list().orEmpty().toList())
    }

    private fun pending(journal: File, vararg files: Pair<File, Boolean>) {
        DataOutputStream(journal.resolve("pending").outputStream()).use { out ->
            out.writeInt(1)
            out.writeInt(files.size)
            for ((file, existed) in files) {
                val isDatabase = file.parentFile.name == "databases"
                out.writeBoolean(isDatabase)
                out.writeUTF(if (isDatabase) file.name else "${file.parentFile.name}/${file.name}")
                out.writeBoolean(existed)
            }
        }
    }
}
