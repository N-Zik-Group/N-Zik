package app.n_zik.android.core.rescue

import java.io.File
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import timber.log.Timber

internal object RescueDatabaseFiles {
    private const val TAG = "RescueFiles"

    // The empty suffix keeps the main database first in each file operation.
    val fileSuffixes = listOf("", "-wal", "-shm", "-journal")

    // The default SQLite error handler deletes corrupt databases during open.
    private val keepCorruptDatabase by lazy { DatabaseErrorHandler { } }

    fun checkpointWal(dbFile: File) {
        // A read-only connection cannot checkpoint a WAL database.
        SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
            keepCorruptDatabase,
        ).use { db ->
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val busy = cursor.getInt(0)
                    Timber.tag(TAG).d("WAL checkpoint busy flag: %d", busy)
                    if (busy != 0) throw RescueFiles.DatabaseBusyException()
                }
            }
        }
    }

    fun isValidSqliteHeader(headerBytes: ByteArray): Boolean {
        val magic = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        if (headerBytes.size < magic.size) return false
        return headerBytes.copyOfRange(0, magic.size).contentEquals(magic)
    }

    fun deleteWithSideFiles(file: File): Int {
        val directory = checkNotNull(file.parentFile).canonicalFile
        return fileSuffixes.sumOf { suffix ->
            val target = directory.resolve(file.name + suffix)
            check(!target.exists() || target.isFile) { "Unexpected database side file" }
            ProfileResetSnapshot.deleteVerifiedTree(target)
        }
    }

    // Clear stale side files before moving any source file. A partial move is retained on error.
    fun moveDatabaseFiles(from: File, to: File) {
        fileSuffixes.forEach { suffix ->
            val stale = File(to.path + suffix)
            if (stale.exists()) check(stale.delete()) { "Cannot remove ${stale.name}" }
        }
        fileSuffixes.forEach { suffix ->
            val source = File(from.path + suffix)
            if (source.exists()) moveRescueFileReplacing(source, File(to.path + suffix))
        }
    }

    fun swapDatabaseFiles(live: File, backup: File, parked: File, rollbackFailureMessage: String) {
        try {
            if (live.exists()) moveDatabaseFiles(live, parked)
            moveDatabaseFiles(backup, live)
            if (parked.exists()) moveDatabaseFiles(parked, backup)
        } catch (error: Exception) {
            // Keep the parked copy if part of the backup has already moved into place.
            if (parked.exists() && !live.exists()) {
                runCatching { moveDatabaseFiles(parked, live) }
                    .onFailure { Timber.tag(TAG).e(rollbackFailureMessage, it.javaClass.simpleName) }
            }
            throw error
        }
    }
}
