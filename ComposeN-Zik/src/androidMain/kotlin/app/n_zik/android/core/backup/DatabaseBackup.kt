package app.n_zik.android.core.backup

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseLockedException
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.LibraryBackupSchema
import app.n_zik.android.core.profiles.ProfileDataDirNames
import app.n_zik.android.core.profiles.coversDir
import app.n_zik.android.core.rescue.RescueFiles
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import timber.log.Timber

/** Prepares and installs portable library backups without opening Room. Call off the main thread. */
internal object DatabaseBackup {
    const val SQLITE_MIME = "application/vnd.sqlite3"
    const val ZIP_MIME = "application/zip"

    val importMimes: Array<String>
        get() = arrayOf(SQLITE_MIME, "application/x-sqlite3", ZIP_MIME, "application/x-zip-compressed", "application/octet-stream")

    private const val DATABASE_ENTRY = "database.db"
    private const val COVERS = "covers"
    private const val THUMBNAILS = "thumbnail"
    private const val MAX_DATABASE = 2L * 1024 * 1024 * 1024
    private const val MAX_IMAGE = 64L * 1024 * 1024
    private const val MAX_TOTAL = 4L * 1024 * 1024 * 1024
    private const val MAX_ENTRIES = 50_000
    private val coverTables = listOf("Song", "Album", "Artist")
    private val coverName = Regex("cover_[^/\\\\\\p{Cntrl}]+\\.jpg")
    private val playlistName = Regex("playlist_[0-9]+")
    private val sidecars = listOf("-wal", "-shm", "-journal")
    private val keepCorruptDatabase = DatabaseErrorHandler { }

    internal enum class Format(val extension: String, val mime: String) {
        SQLITE("sqlite", SQLITE_MIME),
        ZIP("zip", ZIP_MIME),
    }

    /** An immutable payload. Closing it removes the staged export. */
    internal class Export internal constructor(val file: File, val format: Format) : Closeable {
        fun writeTo(context: Context, uri: Uri) {
            val resolver = context.contentResolver
            try {
                val output = checkNotNull(resolver.openOutputStream(uri, "w")) {
                    "Could not open the backup destination"
                }
                output.use { sink -> file.inputStream().use { it.copyTo(sink) } }
            } catch (error: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                throw error
            }
        }

        override fun close() {
            file.parentFile?.deleteRecursively()
        }
    }

    /** Captures the database and only the covers associated with that snapshot. */
    @Synchronized
    fun prepareExport(context: Context, profile: String): Export {
        val source = context.getDatabasePath(Database.fileNameForProfile(profile))
        val stage = createStage(context)
        try {
            val snapshot = stage.resolve(DATABASE_ENTRY)
            snapshotDatabase(source, snapshot)
            val images = linkedMapOf<String, File>()
            val thumbnails = context.filesDir.resolve(THUMBNAILS)
            val baseCoverDir = ProfileDataDirNames.COVERS_DIR
            val allowedCoverDirs = setOf(
                coversDir(context, profile).canonicalFile,
                context.filesDir.resolve(baseCoverDir).canonicalFile,
                if (profile == DEFAULT_PROFILE_ID) context.filesDir.resolve(baseCoverDir).canonicalFile
                else context.filesDir.resolve("${baseCoverDir}_$profile").canonicalFile,
            )
            open(snapshot, SQLiteDatabase.OPEN_READONLY).use { db ->
                val origins = mutableMapOf<String, String>()
                val ambiguous = mutableSetOf<String>()
                for (cover in coverReferences(db)) {
                    // A cover may still point at the previously configured sharing directory.
                    val file = cover.path
                    val name = "$COVERS/${file.name}"
                    val origin = coverOrigin(file)
                    val previous = origins.putIfAbsent(name, origin)
                    if (previous != null && previous != origin) ambiguous += name
                    val directory = file.parentFile ?: continue
                    if (directory.canonicalFile !in allowedCoverDirs) continue
                    val owned = childFile(directory, file.name)
                    if (owned.isFile && owned.length() > 0L) images[name] = owned
                }
                require(ambiguous.none(images::containsKey)) { "Ambiguous cover filename" }
                for (name in playlistNames(db)) {
                    val owned = childFile(thumbnails, name)
                    if (owned.isFile && owned.length() > 0L) images["$THUMBNAILS/$name"] = owned
                }
            }
            if (images.isEmpty()) return Export(snapshot, Format.SQLITE)

            val archive = stage.resolve("backup.zip")
            val stagedImage = stage.resolve("image")
            check(images.size + 1 <= MAX_ENTRIES) { "Too many files in the backup" }
            var remaining = MAX_TOTAL
            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                for ((name, file) in mapOf(DATABASE_ENTRY to snapshot) + images) {
                    val entry = if (name == DATABASE_ENTRY) {
                        snapshot
                    } else {
                        // Decode the same bytes that will enter the archive, even if the source
                        // is being edited. One scratch file bounds additional staging space.
                        file.inputStream().use { source ->
                            stagedImage.outputStream().use { source.copyBounded(it, minOf(remaining, MAX_IMAGE)) }
                        }
                        validateImage(stagedImage)
                        stagedImage
                    }
                    zip.putNextEntry(ZipEntry(name))
                    val limit = if (name == DATABASE_ENTRY) MAX_DATABASE else MAX_IMAGE
                    remaining -= entry.inputStream().use { it.copyBounded(zip, minOf(remaining, limit)) }
                    zip.closeEntry()
                }
            }
            stagedImage.delete()
            check(archive.length() <= MAX_TOTAL) { "Backup exceeds its size limit" }
            return Export(archive, Format.ZIP)
        } catch (error: Throwable) {
            stage.deleteRecursively()
            throw error
        }
    }

    /**
     * Validates the entire input and rewrites staged cover references before [beforeReplace]
     * closes a live database. Installation preserves the old database, sidecars, and replaced
     * images if any write fails.
     */
    fun restore(context: Context, uri: Uri, profile: String, beforeReplace: () -> Unit = {}) {
        restore(context, profile, beforeReplace) {
            checkNotNull(context.contentResolver.openInputStream(uri)) { "Could not open the backup" }
        }
    }

    @Synchronized
    internal fun restore(
        context: Context,
        profile: String,
        beforeReplace: () -> Unit = {},
        openInput: () -> InputStream,
    ) {
        val staging = DatabaseRestoreJournal.createImportStage(context)
        val stage = staging.directory
        try {
            val payload = stage.resolve("input")
            val sqlite = openInput().buffered().use { input ->
                input.mark(16)
                val header = ByteArray(16)
                var length = 0
                while (length < header.size) {
                    val read = input.read(header, length, header.size - length)
                    if (read < 0) break
                    length += read
                }
                input.reset()

                val sqliteInput = RescueFiles.isValidSqliteHeader(header) && length == header.size
                val zipInput = header.take(4) == listOf<Byte>(0x50, 0x4b, 0x03, 0x04)
                require(sqliteInput || zipInput) { "Unrecognized database backup" }
                payload.outputStream().use { input.copyBounded(it, if (sqliteInput) MAX_DATABASE else MAX_TOTAL) }
                sqliteInput
            }

            val extracted = if (sqlite) mapOf(DATABASE_ENTRY to payload) else extractZip(payload, stage)
            val database = checkNotNull(extracted[DATABASE_ENTRY]) { "Database entry is missing" }
            val images = extracted - DATABASE_ENTRY
            val targets = validateAndMapImages(context, database, images, profile)
            val dbFile = context.getDatabasePath(Database.fileNameForProfile(profile))
            DatabaseRestoreJournal.install(
                filesDir = context.filesDir,
                database = dbFile,
                files = targets + sidecars.associate { File(dbFile.path + it) to null } + (dbFile to database),
                beforeReplace = beforeReplace,
            )
            if (targets.isNotEmpty()) ArtworkRevisions.invalidate()
        } finally {
            staging.close()
        }
    }

    private fun createStage(context: Context): File {
        val stage = File.createTempFile("database-backup-", ".stage", context.cacheDir)
        check(stage.delete() && stage.mkdir()) { "Could not create backup staging directory" }
        return stage
    }

    /** Reopens the prepared file after activity recreation while the document picker is open. */
    fun resumeExport(context: Context, path: String): Export {
        val file = File(path)
        val stage = checkNotNull(file.parentFile)
        require(stage.parentFile?.canonicalFile == context.cacheDir.canonicalFile &&
            stage.name.startsWith("database-backup-") && stage.name.endsWith(".stage")
        ) { "Invalid staged database export" }
        val format = when (file.name) {
            DATABASE_ENTRY -> Format.SQLITE
            "backup.zip" -> Format.ZIP
            else -> error("Invalid staged database filename")
        }
        return Export(file, format)
    }

    private fun open(file: File, flags: Int): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.absolutePath, null, flags, keepCorruptDatabase)

    private fun snapshotDatabase(source: File, snapshot: File) {
        check(source.isFile) { "Database file does not exist" }
        open(source, SQLiteDatabase.OPEN_READWRITE).use { db ->
            repeat(3) {
                try {
                    RescueFiles.checkpointWal(source)
                    // A nonempty WAL detects writes between checkpoint and lock acquisition.
                    db.beginTransactionNonExclusive()
                } catch (_: RescueFiles.DatabaseBusyException) {
                    return@repeat
                } catch (_: SQLiteDatabaseLockedException) {
                    return@repeat
                }
                try {
                    if (File(source.path + "-wal").length() == 0L) {
                        snapshot.outputStream().use { sink ->
                            source.inputStream().use { it.copyBounded(sink, MAX_DATABASE) }
                        }
                        return
                    }
                } finally {
                    db.endTransaction()
                }
            }
        }
        throw RescueFiles.DatabaseBusyException("Database changed while taking a snapshot")
    }

    private fun extractZip(payload: File, stage: File): Map<String, File> {
        val files = linkedMapOf<String, File>()
        val seen = mutableSetOf<String>()
        var remaining = MAX_TOTAL
        // ZipFile requires a central directory, unlike ZipInputStream, which can silently accept
        // a ZIP truncated just after the last complete entry. Verify each entry's CRC explicitly.
        ZipFile(payload).use { zip ->
            require(zip.size() in 1..MAX_ENTRIES) { "Invalid archive entry count" }
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                require(seen.add(name)) { "Duplicate archive entry" }
                val limit = when {
                    name == DATABASE_ENTRY -> MAX_DATABASE
                    name == "$COVERS/" || name == "$THUMBNAILS/" -> 0L
                    name.startsWith("$COVERS/") && coverName.matches(name.substringAfter('/')) -> MAX_IMAGE
                    name.startsWith("$THUMBNAILS/") && playlistName.matches(name.substringAfter('/')) -> MAX_IMAGE
                    else -> error("Invalid archive entry name")
                }
                val minimum = if (limit == 0L) 0L else 1L
                require(entry.size in minimum..minOf(limit, remaining)) { "Invalid archive entry size" }
                val target = stage.resolve("entry-${seen.size}")
                val crc = CRC32()
                val actual = CheckedInputStream(zip.getInputStream(entry), crc).use { source ->
                    target.outputStream().use { source.copyBounded(it, entry.size) }
                }
                require(actual == entry.size && crc.value == entry.crc) { "Corrupt archive entry" }
                remaining -= actual
                if (limit == MAX_IMAGE) validateImage(target)
                if (limit != 0L) files[name] = target
            }
        }
        return files
    }

    private fun validateImage(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096) { "Invalid backup image dimensions" }
        val image = requireNotNull(BitmapFactory.decodeFile(file.path)) { "Unreadable backup image" }
        image.recycle()
    }

    private fun coverOrigin(file: File): String {
        val path = (if (file.isAbsolute) file.canonicalPath else file.path).replace(File.separatorChar, '/')
        // A backup from another device may refer to either Android user-0 path alias.
        return if (path.startsWith("/data/data/")) "/data/user/0/" + path.removePrefix("/data/data/") else path
    }

    private data class CoverReference(
        val table: String,
        val id: String,
        val path: File,
        val prefix: String,
        val usesFileUri: Boolean,
    )

    private fun coverReferences(db: SQLiteDatabase): Sequence<CoverReference> = sequence {
        for (table in coverTables) {
            val columns = columns(db, table)
            if ("id" !in columns || "thumbnailUrl" !in columns) continue
            db.rawQuery("SELECT id, thumbnailUrl FROM `$table` WHERE thumbnailUrl IS NOT NULL", null).use { rows ->
                while (rows.moveToNext()) {
                    val id = rows.getString(0) ?: continue
                    val url = rows.getString(1)
                    val local = cleanPrefix(url)
                    val uri = Uri.parse(local)
                    val isRawPath = File(local).isAbsolute
                    val path = when {
                        isRawPath -> local
                        uri.scheme == "file" && uri.authority.isNullOrEmpty() -> uri.path
                        else -> null
                    } ?: continue
                    val file = File(path)
                    val coverDir = file.parentFile?.name
                    val appCoverDir = ProfileDataDirNames.COVERS_DIR
                    if ((file.isAbsolute || path.startsWith('/')) && coverName.matches(file.name) &&
                        (coverDir == appCoverDir || coverDir?.startsWith("${appCoverDir}_") == true)
                    ) {
                        yield(CoverReference(table, id, file, url.removeSuffix(local), usesFileUri = !isRawPath))
                    }
                }
            }
        }
    }

    private fun playlistNames(db: SQLiteDatabase): Set<String> {
        if ("id" !in columns(db, "Playlist")) return emptySet()
        return db.rawQuery("SELECT id FROM Playlist", null).use { rows ->
            buildSet {
                while (rows.moveToNext()) {
                    val name = "playlist_${rows.getString(0)}"
                    if (playlistName.matches(name)) add(name)
                }
            }
        }
    }

    private fun columns(db: SQLiteDatabase, table: String): Set<String> =
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
        }

    private fun validateAndMapImages(
        context: Context,
        database: File,
        images: Map<String, File>,
        profile: String,
    ): Map<File, File> {
        val targets = linkedMapOf<File, File>()
        val flags = if (images.isEmpty()) SQLiteDatabase.OPEN_READONLY else SQLiteDatabase.OPEN_READWRITE
        open(database, flags).use { db ->
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()) {
                    "Database integrity check failed"
                }
            }
            LibraryBackupSchema.validate(context, db)
            if (images.isEmpty()) return targets

            // Use a rollback journal for staged edits so the installed file is self-contained.
            db.rawQuery("PRAGMA journal_mode=DELETE", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0).equals("delete", ignoreCase = true))
            }
            val matched = mutableSetOf<String>()
            val coverDir = coversDir(context, profile)
            // Close query cursors before updating their tables.
            val references = coverReferences(db).filter { "$COVERS/${it.path.name}" in images }.toList()
            val origins = mutableMapOf<String, String>()
            db.beginTransaction()
            try {
                for (cover in references) {
                    val name = "$COVERS/${cover.path.name}"
                    val origin = coverOrigin(cover.path)
                    val previous = origins.putIfAbsent(name, origin)
                    require(previous == null || previous == origin) { "Ambiguous cover filename" }
                    val target = childFile(coverDir, cover.path.name)
                    targets[target] = images.getValue(name)
                    matched += name
                    val replacement = if (cover.usesFileUri) Uri.fromFile(target).toString() else target.absolutePath
                    db.execSQL(
                        "UPDATE `${cover.table}` SET thumbnailUrl = ? WHERE id = ?",
                        arrayOf(cover.prefix + replacement, cover.id),
                    )
                }
                val thumbnailDir = context.filesDir.resolve(THUMBNAILS)
                for (playlist in playlistNames(db)) {
                    val name = "$THUMBNAILS/$playlist"
                    val image = images[name] ?: continue
                    targets[childFile(thumbnailDir, playlist)] = image
                    matched += name
                }
                require(matched == images.keys) { "Archive contains an image unrelated to the database" }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        return targets
    }

    private fun childFile(directory: File, name: String): File {
        val target = File(directory, name)
        require(directory.canonicalFile == File(directory.parentFile?.canonicalFile, directory.name) &&
            target.canonicalFile == File(directory.canonicalFile, name)
        ) {
            "Invalid backup image path"
        }
        return target
    }

    private fun InputStream.copyBounded(output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) return total
            total += count
            require(total <= limit) { "Backup exceeds its size limit" }
            output.write(buffer, 0, count)
        }
    }
}
