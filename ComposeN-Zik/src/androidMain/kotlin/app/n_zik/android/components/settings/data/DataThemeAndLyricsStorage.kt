package app.n_zik.android.components.settings.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.system.Os
import android.system.OsConstants
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.isCurrentProcessStorageProfile
import app.n_zik.android.core.profiles.isProcessStorageProfile
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.profiles.profileStorageIdentity
import java.io.File
import timber.log.Timber

internal fun themeProfilePayloadBytes(context: Context, profile: String): Long =
    profileDataPrefs(context, profile).all.entries.sumOf { (key, value) ->
        if (key.startsWith("themeStore.download.")) {
            (value as? String)?.encodeToByteArray()?.size?.toLong() ?: 0L
        } else 0L
    }

internal fun measureLyricsTextBytes(file: File): Long =
    try {
        openLyricsDatabase(file, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT TOTAL(length(CAST(data AS BLOB))) FROM Lyrics", null).use { rows ->
                if (rows.moveToFirst()) rows.getLong(0) else 0L
            }
        }
    } catch (error: SQLiteException) {
        Timber.tag("DataStorage").d(error, "Lyrics size is unavailable")
        0L
    }

internal data class FetchedLyricsCleanup(
    val owner: ProfileStorageIdentity,
    val cutoff: Long,
    val device: Long,
    val inode: Long,
)

private fun lyricsDatabaseFile(context: Context, owner: String): File {
    val path = context.getDatabasePath(Database.fileNameForProfile(owner))
    return checkNotNull(path.parentFile).canonicalFile.resolve(path.name)
}

private fun openLyricsDatabase(file: File, flags: Int): SQLiteDatabase =
    SQLiteDatabase.openDatabase(file.path, null, flags) {
        // A storage operation must not remove a damaged library database.
    }

internal fun captureFetchedLyrics(context: Context, owner: ProfileStorageIdentity): FetchedLyricsCleanup? =
    DatabaseRestoreJournal.withProfileStorageMutation(context) {
        if (profileStorageIdentity(context, owner.id) != owner) return@withProfileStorageMutation null
        val file = lyricsDatabaseFile(context, owner.id)
        if (!file.isFile || file.canonicalFile != file) return@withProfileStorageMutation null
        val stat = Os.lstat(file.path)
        if (!OsConstants.S_ISREG(stat.st_mode)) return@withProfileStorageMutation null
        val cutoff = System.currentTimeMillis()
        openLyricsDatabase(file, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery(
                "SELECT 1 FROM Lyrics WHERE isEdited = 0 AND (lastFetchedAt IS NULL OR lastFetchedAt <= ?) LIMIT 1",
                arrayOf(cutoff.toString()),
            ).use { rows ->
                if (rows.moveToFirst()) FetchedLyricsCleanup(owner, cutoff, stat.st_dev, stat.st_ino) else null
            }
        }
    }

internal fun clearFetchedLyrics(context: Context, target: FetchedLyricsCleanup): CleanupResult =
    DatabaseRestoreJournal.withProfileStorageMutation(context) {
        val owner = target.owner
        if (profileStorageIdentity(context, owner.id) != owner) return@withProfileStorageMutation CleanupResult.SKIPPED
        val file = lyricsDatabaseFile(context, owner.id)
        if (!file.isFile || file.canonicalFile != file) return@withProfileStorageMutation CleanupResult.SKIPPED
        val stat = Os.lstat(file.path)
        if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_dev != target.device || stat.st_ino != target.inode) {
            return@withProfileStorageMutation CleanupResult.SKIPPED
        }
        if (isProcessStorageProfile(context, owner.id)) {
            if (!isCurrentProcessStorageProfile(context, owner)) return@withProfileStorageMutation CleanupResult.SKIPPED
            Database.lyricsTable.deleteFetchedLyrics(target.cutoff)
        } else {
            openLyricsDatabase(file, SQLiteDatabase.OPEN_READWRITE).use { database ->
                database.delete("Lyrics", "isEdited = 0 AND (lastFetchedAt IS NULL OR lastFetchedAt <= ?)", arrayOf(target.cutoff.toString()))
            }
        }
        CleanupResult.COMPLETED
    }
