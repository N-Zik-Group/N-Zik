package app.n_zik.android.core.backup

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import app.n_zik.android.BuildConfig
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.rescue.RescueFiles
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DatabaseBackupSafTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context: Context by lazy {
        val root = tmp.newFolder()
        object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            // Keep native SQLite below Windows' path limit in Robolectric.
            override fun getCacheDir(): File = root.resolve("cache").apply { mkdirs() }
            override fun getFilesDir(): File = root.resolve("files").apply { mkdirs() }
            override fun getDatabasePath(name: String): File =
                root.resolve("databases").apply { mkdirs() }.resolve(name)
        }
    }

    @Test
    fun `automatic backup creates and writes a ZIP document using the same prepared export`() {
        val provider = registerProvider()
        val image = backupJpeg(Color.BLUE)
        createLibraryWithPlaylist()
        context.filesDir.resolve("thumbnail").apply { mkdirs() }.resolve("playlist_7").writeBytes(image)

        // BackupManager exposes the worker entry point as a suspend function.
        val success = runBlocking { BackupManager.executeBackup(context, "content://$AUTHORITY/tree/root") }

        assertTrue("ZIP backup failed after provider received MIME ${provider.mime}", success)
        assertEquals("application/zip", provider.mime)
        val name = checkNotNull(provider.displayName)
        assertEquals(AutoBackupKind.DATABASE, autoBackupKindOf(name))
        assertTrue(name.endsWith("_AutoBackup.zip"))
        assertEquals("default", RescueFiles.backupProfileTagOf(name, BuildConfig.APP_NAME))
        ZipFile(provider.file).use { archive ->
            val database = tmp.newFile()
            archive.getInputStream(archive.getEntry("database.db")).use { source ->
                database.outputStream().use { source.copyTo(it) }
            }
            assertPlaylist(database)
            assertArrayEquals(image, archive.getInputStream(archive.getEntry("thumbnail/playlist_7")).use { it.readBytes() })
        }
    }

    @Test
    fun `rescue imports a ZIP into the selected profile without replacing the active database`() {
        val provider = registerProvider()
        val image = backupJpeg(Color.RED)
        val active = createLibraryWithPlaylist()
        val previousActive = active.readBytes()
        context.filesDir.resolve("thumbnail").apply { mkdirs() }.resolve("playlist_7").writeBytes(image)
        DatabaseBackup.prepareExport(context, "default").use { export ->
            export.file.copyTo(provider.file, overwrite = true)
        }
        val thumbnail = context.filesDir.resolve("thumbnail/playlist_7")
        assertTrue(thumbnail.delete())
        RescueFiles.initialize(context)
        RescueFiles.setRescueProfile("rescue-work")
        try {
            val selected = context.getDatabasePath(Database.fileNameForProfile("rescue-work"))
            assertFalse(selected.exists())

            RescueFiles.importDatabase(context, provider.documentUri).getOrThrow()

            assertPlaylist(selected)
            assertArrayEquals(image, thumbnail.readBytes())
            assertArrayEquals(previousActive, active.readBytes())
        } finally {
            RescueFiles.setRescueProfile("default")
        }
    }

    @Test
    fun `rescue rejects a valid ZIP while the main process owns the database lease`() {
        val provider = registerProvider()
        val active = createLibraryWithPlaylist()
        val thumbnail = context.filesDir.resolve("thumbnail").apply { mkdirs() }.resolve("playlist_7")
        thumbnail.writeBytes(backupJpeg(Color.RED))
        DatabaseBackup.prepareExport(context, "default").use { export ->
            export.file.copyTo(provider.file, overwrite = true)
        }
        val currentImage = backupJpeg(Color.BLUE)
        thumbnail.writeBytes(currentImage)
        val currentDatabase = active.readBytes()
        RescueFiles.initialize(context)
        RescueFiles.setRescueProfile("default")

        RandomAccessFile(context.filesDir.resolve("database-main.lock"), "rw").use { handle ->
            handle.channel.lock().use {
                val failure = RescueFiles.importDatabase(context, provider.documentUri).exceptionOrNull()

                assertTrue("Unexpected import result: $failure", failure is IllegalStateException)
                assertEquals("The main process is using the database", failure?.message)
                assertEquals(0, provider.openCount)
            }
        }

        assertArrayEquals(currentDatabase, active.readBytes())
        assertArrayEquals(currentImage, thumbnail.readBytes())
    }

    private fun createLibraryWithPlaylist(): File =
        context.getDatabasePath(Database.fileNameForProfile("default")).apply {
            parentFile?.mkdirs()
            openBackupFixture(context, this).use {
                it.execSQL("INSERT INTO Playlist (id, name, isEditable) VALUES (7, 'Backup playlist', 1)")
            }
        }

    private fun assertPlaylist(file: File) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT name FROM Playlist WHERE id = 7", null).use { rows ->
                assertTrue(rows.moveToFirst())
                assertEquals("Backup playlist", rows.getString(0))
            }
        }
    }

    private fun registerProvider(): TestBackupProvider = TestBackupProvider(tmp.newFile()).also { provider ->
        provider.attachInfo(context, ProviderInfo().apply {
            authority = AUTHORITY
            exported = true
            grantUriPermissions = true
        })
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
    }

    private class TestBackupProvider(val file: File) : ContentProvider() {
        val documentUri: Uri = Uri.parse("content://$AUTHORITY/document/backup")
        var mime: String? = null
        var displayName: String? = null
        var openCount = 0
            private set

        override fun onCreate() = true

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
            if (method != "android:createDocument") return super.call(method, arg, extras)
            mime = extras?.getString("mime_type")
            displayName = extras?.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            return Bundle().apply { putParcelable("uri", documentUri) }
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            openCount++
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
        }

        override fun getType(uri: Uri): String? = mime
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null
    }

    private companion object {
        const val AUTHORITY = "nzik.backup.tests"
    }
}
