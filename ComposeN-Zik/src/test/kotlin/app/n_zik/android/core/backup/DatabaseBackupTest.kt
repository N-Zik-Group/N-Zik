package app.n_zik.android.core.backup

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.LibraryBackupSchema
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.coversDir
import app.n_zik.android.core.profiles.setProfileShares
import app.n_zik.android.core.rescue.RescueFiles
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DatabaseBackupTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `ZIP round trip rebases owned covers into the destination sharing directory`() {
        val source = device("source")
        val destination = device("destination")
        destination.setProfileShares(ProfileDataItem.IMAGES, "work", true)

        val song = cover(coversDir(source, "source"), "cover_song.jpg", Color.RED)
        val album = cover(coversDir(source, "source"), "cover_album.jpg", Color.GREEN)
        val artist = cover(coversDir(source, "source"), "cover_artist.jpg", Color.BLUE)
        val foreign = image(coversDir(source, "private"), "cover_foreign.jpg", "private")
        image(coversDir(source, "source"), "cover_orphan.jpg", "orphan")
        val playlist = cover(source.filesDir.resolve("thumbnail"), "playlist_7", Color.YELLOW)
        image(source.filesDir.resolve("thumbnail"), "playlist_8", "orphan")
        image(source.filesDir, "profile.jpg", "face")
        image(destination.filesDir.resolve("thumbnail"), "playlist_7", "old playlist")
        val unrelated = image(destination.filesDir.resolve("thumbnail"), "playlist_8", "keep playlist")

        library(source, "source").use { db ->
            db.enableWriteAheadLogging()
            insertCover(db, "Song", "song", owned(song))
            insertCover(db, "Song", "linkedSong", owned(album))
            insertCover(db, "Album", "album", owned(album))
            insertCover(db, "Artist", "artist", owned(artist))
            insertCover(db, "Song", "remote", "${MODIFIED_PREFIX}https://example.test/cover_song.jpg")
            insertCover(db, "Song", "foreign", owned(foreign))
            addPlaylist(db)

            DatabaseBackup.prepareExport(source, "source").use { export ->
                assertEquals(DatabaseBackup.Format.ZIP, export.format)
                ZipFile(export.file).use { zip ->
                    assertEquals(
                        setOf("database.db", "covers/cover_song.jpg", "covers/cover_album.jpg",
                            "covers/cover_artist.jpg", "thumbnail/playlist_7"),
                        zip.entries().toList().map { it.name }.toSet(),
                    )
                }

                // The import detects the archive by signature, independent of its name.
                val renamed = export.file.copyTo(tmp.newFolder().resolve("renamed.db"))
                DatabaseBackup.restore(destination, "work", openInput = renamed::inputStream)
            }
        }

        val shared = destination.filesDir.resolve("app_covers")
        val restored = databaseFile(destination, "work")
        SQLiteDatabase.openDatabase(restored.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(owned(shared.resolve(song.name)), thumbnail(db, "Song", "song"))
            assertEquals(owned(shared.resolve(album.name)), thumbnail(db, "Song", "linkedSong"))
            assertEquals(owned(shared.resolve(album.name)), thumbnail(db, "Album", "album"))
            assertEquals(owned(shared.resolve(artist.name)), thumbnail(db, "Artist", "artist"))
            assertEquals("${MODIFIED_PREFIX}https://example.test/cover_song.jpg", thumbnail(db, "Song", "remote"))
        }
        assertArrayEquals(song.readBytes(), shared.resolve(song.name).readBytes())
        assertArrayEquals(album.readBytes(), shared.resolve(album.name).readBytes())
        assertArrayEquals(artist.readBytes(), shared.resolve(artist.name).readBytes())
        assertFalse(destination.filesDir.resolve("app_covers_work").exists())
        assertArrayEquals(playlist.readBytes(), destination.filesDir.resolve("thumbnail/playlist_7").readBytes())
        assertEquals("keep playlist", unrelated.readText())
        assertTrue(source.cacheDir.listFiles().orEmpty().isEmpty())
        assertTrue(destination.cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `orphan files produce SQLite and bare import preserves destination images`() {
        val source = device("source")
        val destination = device("destination")
        val emptyCover = image(coversDir(source, "default"), "cover_empty.jpg", "")
        image(source.filesDir.resolve("thumbnail"), "playlist_7", "")
        library(source, "default").use { db ->
            insertCover(db, "Song", "empty", owned(emptyCover))
            addPlaylist(db)
        }
        image(coversDir(source, "default"), "cover_orphan.jpg", "orphan")
        image(source.filesDir.resolve("thumbnail"), "playlist_9", "orphan")
        val localCover = image(coversDir(destination, "work"), "cover_local.jpg", "keep")
        val localPlaylist = image(destination.filesDir.resolve("thumbnail"), "playlist_9", "keep")

        DatabaseBackup.prepareExport(source, "default").use { export ->
            assertEquals(DatabaseBackup.Format.SQLITE, export.format)
            DatabaseBackup.restore(destination, "work", openInput = export.file::inputStream)
        }

        assertTrue(databaseFile(destination, "work").isFile)
        assertEquals("keep", localCover.readText())
        assertEquals("keep", localPlaylist.readText())
        assertTrue(destination.cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `Android file URI is rebased from an archive and keeps its prefix`() {
        val source = device("source")
        val destination = device("destination")
        val oldUri = "${MODIFIED_PREFIX}file:///data/user/0/app.example/files/app_covers_old/cover_song.jpg"
        library(source, "default").use {
            insertCover(it, "Song", "song", oldUri)
            insertCover(it, "Song", "alias", oldUri.replace("/data/user/0/", "/data/data/"))
        }
        val contents = backupJpeg(Color.RED)
        val zip = archive(
            "database.db" to databaseFile(source, "default").readBytes(),
            "covers/cover_song.jpg" to contents,
        )

        DatabaseBackup.restore(destination, "work") { zip.inputStream() }

        val installed = coversDir(destination, "work").resolve("cover_song.jpg")
        assertArrayEquals(contents, installed.readBytes())
        SQLiteDatabase.openDatabase(databaseFile(destination, "work").path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(MODIFIED_PREFIX + Uri.fromFile(installed), thumbnail(it, "Song", "song"))
            assertEquals(MODIFIED_PREFIX + Uri.fromFile(installed), thumbnail(it, "Song", "alias"))
        }
    }

    @Test
    fun `a database created by Room passes staged compatibility validation`() {
        val context = RuntimeEnvironment.getApplication()
        val name = "room-created-backup.db"
        val file = context.getDatabasePath(name)
        val destination = device("destination")
        val room = Room.databaseBuilder(context, DatabaseInitializer::class.java, name).build()
        try {
            try {
                room.openHelper.writableDatabase.execSQL("INSERT INTO Playlist (id, name, isEditable) VALUES (7, 'Real Room', 1)")
            } finally {
                room.close()
            }
            DatabaseBackup.restore(destination, "work", openInput = file::inputStream)
            assertTrue(databaseFile(destination, "work").isFile)
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun `a provider close failure aborts before the live database is touched`() {
        val destination = device("destination")
        library(destination, "default").close()
        val current = databaseFile(destination, "default").readBytes()
        val input = object : ByteArrayInputStream(current) {
            override fun close() = throw IOException("provider failed on close")
        }

        assertThrows(IOException::class.java) {
            DatabaseBackup.restore(destination, "default", beforeReplace = {
                error("The database must stay open")
            }) { input }
        }
        assertArrayEquals(current, databaseFile(destination, "default").readBytes())
        assertTrue(destination.cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `malformed archives and invalid databases fail before touching the destination`() {
        val source = device("source")
        val destination = device("destination")
        library(source, "default").use(::addPlaylist)
        library(destination, "default").close()
        val current = databaseFile(destination, "default").readBytes()
        val db = databaseFile(source, "default").readBytes()
        val valid = archive("database.db" to db)
        val duplicate = archive("database.db" to db, "databasf.db" to db)
        val duplicateName = "databasf.db".toByteArray()
        for (index in 0..duplicate.size - duplicateName.size) {
            if (duplicate.copyOfRange(index, index + duplicateName.size).contentEquals(duplicateName)) {
                duplicate[index + "databas".length] = 'e'.code.toByte()
            }
        }

        val invalid = listOf(
            valid.copyOf(valid.size - 22),
            duplicate,
            archive("database.db" to db, "covers/../outside" to byteArrayOf(1)),
            archive("database.db" to db, "thumbnail/playlist_999" to backupJpeg(Color.RED)),
            archive("database.db" to db, "thumbnail/playlist_7" to byteArrayOf(1, 2, 3)),
            archive("database.db" to db, "thumbnail/playlist_7" to byteArrayOf()),
            archive("database.db" to byteArrayOf(1, 2, 3)),
            "not a database".toByteArray(),
        )
        for (input in invalid) {
            var closed = false
            assertThrows(Exception::class.java) {
                DatabaseBackup.restore(destination, "default", beforeReplace = { closed = true }) { input.inputStream() }
            }
            assertFalse(closed)
            assertArrayEquals(current, databaseFile(destination, "default").readBytes())
            assertTrue(destination.cacheDir.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun `valid historical schemas are accepted and a structurally incompatible database is rejected`() {
        val source = device("source")
        val destination = device("destination")
        for ((version, namespace) in listOf(42 to LibraryBackupSchema.CURRENT, 27 to LibraryBackupSchema.LEGACY)) {
            val historical = tmp.newFile()
            openBackupFixture(source, historical, version, namespace).close()
            DatabaseBackup.restore(destination, "work", openInput = historical::inputStream)
            assertArrayEquals(historical.readBytes(), databaseFile(destination, "work").readBytes())
        }

        val incompatible = databaseFile(source, "default")
        library(source, "default").use { it.execSQL("DROP TABLE Album") }
        var closed = false
        assertThrows(IllegalArgumentException::class.java) {
            DatabaseBackup.restore(destination, "work", beforeReplace = { closed = true }, openInput = incompatible::inputStream)
        }
        assertFalse(closed)
    }

    @Test
    fun `rejects foreign SQLite objects before a reserved migration name or trigger can interfere`() {
        val source = device("source")
        val destination = device("destination")
        val historic = tmp.newFile()
        openBackupFixture(source, historic, 40).use { db ->
            db.execSQL("CREATE TABLE Song_41 (id TEXT)")
        }
        val triggered = tmp.newFile()
        openBackupFixture(source, triggered).use { db ->
            db.execSQL("CREATE TRIGGER unexpected_cover_update AFTER UPDATE ON Song BEGIN UPDATE Song SET title = 'unexpected'; END")
        }
        for (backup in listOf(historic, triggered)) {
            assertThrows(IllegalArgumentException::class.java) {
                DatabaseBackup.restore(destination, "work", beforeReplace = { error("Must not replace database") }, openInput = backup::inputStream)
            }
        }
    }

    @Test
    fun `rejects a wrong Room identity even when the structure matches`() {
        val source = device("source")
        val destination = device("destination")
        val backup = tmp.newFile()
        openBackupFixture(source, backup).use { db ->
            db.execSQL("UPDATE room_master_table SET identity_hash = 'wrong' WHERE id = 42")
        }

        assertThrows(IllegalArgumentException::class.java) {
            DatabaseBackup.restore(destination, "work", beforeReplace = { error("Must not replace database") }, openInput = backup::inputStream)
        }
    }

    @Test
    fun `export rejects a nonempty unreadable image and a decodable image over the import limit`() {
        val source = device("source")
        library(source, "default").use(::addPlaylist)
        val thumbnail = source.filesDir.resolve("thumbnail").apply { mkdirs() }.resolve("playlist_7")
        for (bytes in listOf(byteArrayOf(1, 2, 3), backupJpeg(Color.RED, width = 4097))) {
            thumbnail.writeBytes(bytes)

            assertThrows(IllegalArgumentException::class.java) {
                DatabaseBackup.prepareExport(source, "default").close()
            }
            assertTrue(source.cacheDir.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun `mixed backup formats share retention classification and profile tags`() {
        val date = "2026-11-09_123456"
        for (format in DatabaseBackup.Format.entries) {
            val name = autoBackupFileName("N-Zik", "work", date, AutoBackupKind.DATABASE, format.extension)
            assertEquals(AutoBackupKind.DATABASE, autoBackupKindOf(name))
            assertEquals("work", RescueFiles.backupProfileTagOf(name, "N-Zik"))
        }
    }

    @Test
    fun `retention recognizes generated duplicates and ignores unrelated archive files`() {
        val date = "2026-11-09_123456"
        for (extension in listOf("sqlite", "db", "zip")) {
            assertEquals(AutoBackupKind.DATABASE, autoBackupKindOf("N-Zik_work_${date}_AutoBackup (2).$extension"))
            assertEquals("work", RescueFiles.backupProfileTagOf("N-Zik_work_${date}_AutoBackup (2).${extension.uppercase()}", "N-Zik"))
            assertNull(autoBackupKindOf("Other_work_${date}_AutoBackup.$extension"))
            assertNull(autoBackupKindOf("N-Zik_work_AutoBackup.$extension"))
        }
        assertEquals(AutoBackupKind.DATABASE, autoBackupKindOf("N-Zik_${date}_AutoBackup.sqlite"))
        assertNull(autoBackupKindOf("personal AutoBackup.zip"))
    }

    private fun device(name: String): Context {
        val root = tmp.newFolder(name)
        return object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getCacheDir(): File = root.resolve("cache").apply { mkdirs() }
            override fun getFilesDir(): File = root.resolve("files").apply { mkdirs() }
            override fun getDatabasePath(name: String): File =
                root.resolve("databases").apply { mkdirs() }.resolve(name)
        }
    }

    private fun databaseFile(context: Context, profile: String): File =
        context.getDatabasePath(Database.fileNameForProfile(profile))

    private fun library(context: Context, profile: String): SQLiteDatabase =
        openBackupFixture(context, databaseFile(context, profile))

    private fun addPlaylist(db: SQLiteDatabase) {
        db.execSQL("INSERT INTO Playlist (id, name, isEditable) VALUES (7, 'Playlist', 1)")
    }

    private fun insertCover(db: SQLiteDatabase, table: String, id: String, value: String) {
        if (table == "Song") {
            db.execSQL("INSERT INTO Song (id, thumbnailUrl, title, totalPlayTimeMs) VALUES (?, ?, ?, 0)", arrayOf(id, value, id))
        } else {
            db.execSQL("INSERT INTO $table (id, thumbnailUrl) VALUES (?, ?)", arrayOf(id, value))
        }
    }

    private fun thumbnail(db: SQLiteDatabase, table: String, id: String): String =
        db.rawQuery("SELECT thumbnailUrl FROM $table WHERE id = ?", arrayOf(id)).use {
            assertTrue(it.moveToFirst())
            it.getString(0)
        }

    private fun image(directory: File, name: String, text: String): File =
        directory.apply { mkdirs() }.resolve(name).apply { writeText(text) }

    private fun cover(directory: File, name: String, color: Int): File =
        directory.apply { mkdirs() }.resolve(name).apply { writeBytes(backupJpeg(color)) }

    private fun owned(file: File): String = MODIFIED_PREFIX + file.absolutePath

    private fun archive(vararg files: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, bytes) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
