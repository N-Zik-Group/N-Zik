package app.n_zik.android.core.rescue

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Process
import app.it.fast4x.rimusic.utils.discordAvatarKey
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.discordUsernameKey
import app.it.fast4x.rimusic.utils.enableYouTubeLoginKey
import app.it.fast4x.rimusic.utils.enableYouTubeSyncKey
import app.it.fast4x.rimusic.utils.isDiscordBrowsingEnabledKey
import app.it.fast4x.rimusic.utils.isDiscordPresenceEnabledKey
import app.it.fast4x.rimusic.utils.proxyPasswordEncryptedKey
import app.it.fast4x.rimusic.utils.useYtLoginOnlyForBrowseKey
import app.it.fast4x.rimusic.utils.ytAccountChannelHandleKey
import app.it.fast4x.rimusic.utils.ytAccountEmailKey
import app.it.fast4x.rimusic.utils.ytAccountNameKey
import app.it.fast4x.rimusic.utils.ytAccountThumbnailKey
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.it.fast4x.rimusic.utils.ytDataSyncIdKey
import app.it.fast4x.rimusic.utils.ytVisitorDataKey
import app.n_zik.android.extensions.discord.discordAdvancedSettingKeys
import app.n_zik.android.extensions.lastfm.isLastfmNowPlayingEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobbleEnabledKey
import app.n_zik.android.extensions.lastfm.isLastfmScrobblingEnabledKey
import app.n_zik.android.extensions.lastfm.lastfmAvatarUrlKey
import app.n_zik.android.extensions.lastfm.lastfmMaxScrobbleDelaySecondsKey
import app.n_zik.android.extensions.lastfm.lastfmMinTrackDurationSecondsKey
import app.n_zik.android.extensions.lastfm.lastfmScrobbleThresholdPercentKey
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import app.n_zik.android.extensions.lastfm.lastfmUsernameKey
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Pure file operations for the Rescue Center.
 *
 * Designed to run in the `:rescue` process where Room, `Dependencies`, `appContext()`,
 * and `context.encryptedPreferences` are NOT available. All operations use raw files
 * and [Context] only.
 *
 * Write operations (import, reset, restore, clear cache, reset settings) are gated
 * by [isMainProcessRunning] to prevent concurrent file access.
 */
object RescueFiles {

    private const val TAG = "RescueFiles"
    private const val DB_FILE_NAME = "data.db"
    private const val RESCUE_BACKUPS_DIR = "rescue_backups"
    private const val CRASH_LOG_FILE = "N-Zik_crash_log.txt"
    private const val DEBUG_LOG_FILE = "N-Zik_log.txt"
    private const val LOGS_DIR = "logs"

    // Cache directories to clear (NEVER exo_downloads or exoplayer_internal.db)
    private const val STREAMING_CACHE_DIR = "exoplayer"
    private const val IMAGE_CACHE_DIR = "coil"
    private const val DOWNLOAD_CACHE_DIR = "exo_downloads" // PROTECTED — never delete
    private const val DOWNLOAD_DB_FILE = "exoplayer_internal.db" // PROTECTED — never delete

    private const val PREFS_NAME = "preferences"
    private const val ENCRYPTED_PREFS_NAME = "secure_preferences"
    private const val SHARED_PREFS_DIR = "shared_prefs"
    private val SETTINGS_FILE_NAMES = listOf("$PREFS_NAME.xml", "$ENCRYPTED_PREFS_NAME.xml")

    /** Suffixes of the files that make up one SQLite database, main file first. */
    private val DB_FILE_SUFFIXES = listOf("", "-wal", "-shm", "-journal")

    /**
     * Error handler that leaves a corrupt database alone. The default one DELETES the file when
     * SQLite reports corruption at open time, which is unacceptable in a tool whose job is to
     * save the user's data.
     */
    private val keepCorruptDatabase by lazy { DatabaseErrorHandler { } }

    // Encrypted credential keys, built from the real constants so they cannot drift
    // (const vals are inlined: no app init needed). These lists are the single source of
    // truth — BackupManager and ExportSettingsDialog must reference them, never re-declare.
    internal val YTB_KEYS = listOf(
        ytCookieKey, ytVisitorDataKey, ytDataSyncIdKey,
        ytAccountNameKey, ytAccountEmailKey, ytAccountChannelHandleKey,
        ytAccountThumbnailKey, enableYouTubeLoginKey, enableYouTubeSyncKey,
        useYtLoginOnlyForBrowseKey
    )
    // The legacy section keys plus every advanced-mode key (item 6/7/8): a backup that
    // carries the token but not the customization is an incomplete backup. The advanced
    // keys live in the same encrypted prefs, so import routing covers them too.
    internal val DISCORD_KEYS = listOf(
        discordPersonalAccessTokenKey, discordAvatarKey, discordUsernameKey,
        isDiscordPresenceEnabledKey, isDiscordBrowsingEnabledKey
    ) + discordAdvancedSettingKeys
    internal val LASTFM_KEYS = listOf(
        lastfmSessionKey, lastfmUsernameKey, lastfmAvatarUrlKey,
        isLastfmScrobblingEnabledKey, isLastfmNowPlayingEnabledKey,
        isLastfmScrobbleEnabledKey, lastfmMinTrackDurationSecondsKey,
        lastfmScrobbleThresholdPercentKey, lastfmMaxScrobbleDelaySecondsKey
    )
    /**
     * The app's own encrypted setting (the proxy password): not a service credential group, so it
     * gets its own checkbox instead of riding along with the credential groups. It lives in the
     * encrypted prefs, so it cannot appear in the plain `prefs.all` export.
     */
    internal val PROXY_KEYS = listOf(proxyPasswordEncryptedKey)
    // Every key that lives in the encrypted prefs: import routing must send each one to the
    // encrypted editor, never to the plain preferences.
    internal val ALL_ENCRYPTED_KEYS = YTB_KEYS + DISCORD_KEYS + LASTFM_KEYS + PROXY_KEYS

    /**
     * CSV rows for [PROXY_KEYS] present in [encryptedPrefs]; empty when the password was
     * never set. Same `Type,Key,Value` shape as the credential entries.
     */
    internal fun buildProxyEntries(encryptedPrefs: Map<String, Any?>): List<Triple<String, String, Any>> =
        PROXY_KEYS.mapNotNull { key ->
            val value = encryptedPrefs[key] ?: return@mapNotNull null
            val type = value::class.simpleName ?: "null"
            if (type == "null") return@mapNotNull null
            Triple(type, key, value)
        }

    /**
     * The full credential section of a settings export: the selected groups plus, when its own
     * flag is on, the app-owned proxy key. Single source of truth for the assembly — the manual
     * export dialog, the auto backup and the rescue export must all build the same entries, so
     * the user's credential selection is respected identically on every path. Empty when nothing
     * is selected.
     */
    internal fun buildCredentialExport(
        encryptedPrefs: Map<String, Any?>,
        includeYtb: Boolean,
        includeDiscord: Boolean,
        includeLastfm: Boolean,
        includeProxy: Boolean = false
    ): List<Triple<String, String, Any>> {
        if (!includeYtb && !includeDiscord && !includeLastfm && !includeProxy) return emptyList()
        val keys = mutableListOf<String>()
        if (includeYtb) keys.addAll(YTB_KEYS)
        if (includeDiscord) keys.addAll(DISCORD_KEYS)
        if (includeLastfm) keys.addAll(LASTFM_KEYS)
        if (includeProxy) keys.addAll(PROXY_KEYS)
        return keys.mapNotNull { key ->
            val value = encryptedPrefs[key] ?: return@mapNotNull null
            val type = value::class.simpleName ?: "null"
            if (type == "null") return@mapNotNull null
            Triple(type, key, value)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Process guard
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns true when the main app process (not `:rescue`) is currently running.
     * Write operations should be blocked when this is true.
     */
    fun isMainProcessRunning(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val myPid = Process.myPid()
        val packageName = context.packageName
        return am.runningAppProcesses?.any { proc ->
            proc.processName == packageName && proc.pid != myPid
        } == true
    }

    // ──────────────────────────────────────────────────────────────────────
    // Export database
    // ──────────────────────────────────────────────────────────────────────

    /**
     * The database could not be checkpointed because another connection (typically the main app
     * process) is using it. The export is refused rather than producing a file that silently
     * lacks the transactions still in the WAL; the UI tells the user to close the app.
     */
    class DatabaseBusyException(message: String) : IllegalStateException(message)

    /**
     * Exports the database to the given SAF URI.
     *
     * Opens it as raw SQLite (no Room, no migration) to fold the WAL into `data.db`, then copies
     * the file. The WAL must be merged first: after a crash a `-wal` file is left next to
     * `data.db`, and a bare copy of `data.db` would lack every transaction still in it.
     */
    fun exportDatabase(context: Context, uri: Uri): Result<Unit> = runCatching {
        val dbFile = context.getDatabasePath(DB_FILE_NAME)
        if (!dbFile.exists()) {
            error("Database file does not exist: ${dbFile.absolutePath}")
        }

        checkpointWal(dbFile)

        context.contentResolver.openOutputStream(uri)?.use { outStream ->
            FileInputStream(dbFile).use { inStream ->
                val bytes = inStream.copyTo(outStream)
                Timber.tag(TAG).i("Database exported: %d bytes", bytes)
            }
        } ?: error("Failed to open output stream for database export")
    }

    /**
     * Folds the WAL into [dbFile] through a raw SQLite connection, and throws when it cannot.
     *
     * The connection is read-write on purpose: a read-only one cannot checkpoint (it raises
     * "disk I/O error" on a WAL database). It uses [keepCorruptDatabase] because the default
     * error handler DELETES the database file when SQLite reports corruption at open time.
     */
    internal fun checkpointWal(dbFile: File) {
        SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
            keepCorruptDatabase
        ).use { db ->
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val busy = cursor.getInt(0)
                    Timber.tag(TAG).d("WAL checkpoint busy flag: %d", busy)
                    if (busy != 0) throw DatabaseBusyException("Database is busy: its WAL could not be merged")
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Import database (with validation)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Validates the SQLite header of a stream.
     * The first 16 bytes of a valid SQLite file are: "SQLite format 3\000"
     */
    internal fun isValidSqliteHeader(headerBytes: ByteArray): Boolean {
        val magic = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        if (headerBytes.size < magic.size) return false
        return headerBytes.copyOfRange(0, magic.size).contentEquals(magic)
    }

    /**
     * Imports a database from the given SAF URI after validation.
     *
     * The file is copied next to `data.db` (same filesystem), validated there (SQLite header +
     * quick_check), then renamed over the live database: a failure while copying can never leave
     * a truncated `data.db`.
     */
    fun importDatabase(context: Context, uri: Uri): Result<Unit> = runCatching {
        val dbFile = context.getDatabasePath(DB_FILE_NAME)
        dbFile.parentFile?.mkdirs()
        val tempFile = File(dbFile.parentFile, "$DB_FILE_NAME.import")
        // A killed earlier import may have left the temporary copy, or its -wal/-shm, behind.
        deleteWithSideFiles(tempFile)

        try {
            // Copy to temp file first
            context.contentResolver.openInputStream(uri)?.use { inStream ->
                FileOutputStream(tempFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            } ?: error("Failed to open input stream for database import")

            // Validate SQLite header
            val header = ByteArray(16)
            FileInputStream(tempFile).use { fis ->
                val read = fis.read(header)
                if (read < 16 || !isValidSqliteHeader(header)) {
                    error("Invalid SQLite file: header check failed")
                }
            }

            // Validate with PRAGMA quick_check
            SQLiteDatabase.openDatabase(
                tempFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
                keepCorruptDatabase
            ).use { db ->
                db.rawQuery("PRAGMA quick_check", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val result = cursor.getString(0)
                        if (result != "ok") {
                            error("Database integrity check failed: $result")
                        }
                        Timber.tag(TAG).d("Database quick_check passed")
                    }
                }
            }

            replaceDatabaseFile(tempFile, dbFile)
            Timber.tag(TAG).i("Database imported successfully")
        } finally {
            // Validation opens the copy: a WAL-mode file makes SQLite create -wal/-shm next to it.
            deleteWithSideFiles(tempFile)
        }
    }

    /** Deletes [dbFile] and its `-wal`, `-shm` and `-journal` files, whichever exist. */
    internal fun deleteWithSideFiles(dbFile: File) {
        DB_FILE_SUFFIXES.forEach { suffix -> File(dbFile.path + suffix).delete() }
    }

    /**
     * Puts [newDb] in place of [dbFile]. The side files of the old database are removed first:
     * a stale `-wal` must never be replayed onto a different database. The rename itself is atomic,
     * but a failure between removing the side files and renaming loses the old `-wal`.
     */
    internal fun replaceDatabaseFile(newDb: File, dbFile: File) {
        DB_FILE_SUFFIXES.drop(1).forEach { suffix ->
            val sideFile = File(dbFile.path + suffix)
            if (sideFile.exists()) check(sideFile.delete()) { "Cannot remove ${sideFile.name}" }
        }
        moveReplacing(newDb, dbFile)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Export settings (CSV)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Outcome of a settings export/import.
     *
     * @property encryptedSkipped true when encrypted credentials were requested (export) or
     *   present in the file (import) but the encrypted store could not be opened, so only the
     *   regular settings were processed. The UI must warn the user.
     */
    data class SettingsOutcome(val encryptedSkipped: Boolean)

    /**
     * Exports settings to a CSV file at the given SAF URI.
     *
     * @param encryptedPrefsResult the Result of opening EncryptedSharedPreferences; if null
     *   or failure, encrypted credentials are skipped and [SettingsOutcome.encryptedSkipped]
     *   is set.
     */
    fun exportSettings(
        context: Context,
        uri: Uri,
        encryptedPrefsResult: Result<SharedPreferences>? = null,
        includeYtb: Boolean = false,
        includeDiscord: Boolean = false,
        includeLastfm: Boolean = false,
        includeProxy: Boolean = false
    ): Result<SettingsOutcome> = runCatching {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = mutableListOf<Triple<String, String, Any>>()

        // Normal preferences
        prefs.all.forEach { (key, value) ->
            if (value != null) {
                val type = value::class.simpleName ?: "null"
                if (type != "null") entries.add(Triple(type, key, value))
            }
        }

        // Encrypted credentials (optional, may fail)
        var encryptedSkipped = false
        if (includeYtb || includeDiscord || includeLastfm || includeProxy) {
            val encPrefs = encryptedPrefsResult?.getOrNull()
            if (encPrefs != null) {
                // Single source of truth: the selected groups plus, when checked, the proxy key.
                entries.addAll(buildCredentialExport(encPrefs.all, includeYtb, includeDiscord, includeLastfm, includeProxy))
            } else {
                encryptedSkipped = true
                Timber.tag(TAG).w("Cannot access encrypted preferences; exporting without credentials")
            }
        }

        Timber.tag(TAG).d("Exporting %d settings entries", entries.size)
        context.contentResolver.openOutputStream(uri)?.use { outStream ->
            writeSettingsCsv(outStream, entries)
            Timber.tag(TAG).i("Settings exported: %d entries", entries.size)
        } ?: error("Failed to open output stream for settings export")

        SettingsOutcome(encryptedSkipped)
    }

    /**
     * Writes settings as `Type,Key,Value` CSV using the same writer as the regular settings
     * export, so values containing commas, quotes or line breaks round-trip through
     * [readSettingsCsv].
     */
    internal fun writeSettingsCsv(outStream: OutputStream, entries: List<Triple<String, String, Any>>) {
        csvWriter().open(outStream) {
            writeRow("Type", "Key", "Value")
            entries.forEach { (type, key, value) -> writeRow(type, key, value) }
        }
    }

    /** Parses a `Type,Key,Value` CSV into (type, key, value) rows; rows missing a key are dropped. */
    internal fun readSettingsCsv(inStream: InputStream): List<Triple<String, String, String>> =
        csvReader().readAllWithHeader(inStream).mapNotNull { row ->
            val key = row["Key"]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Triple(row["Type"].orEmpty(), key, row["Value"].orEmpty())
        }

    // ──────────────────────────────────────────────────────────────────────
    // Import settings (CSV)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Imports settings from a CSV file.
     *
     * Normal keys go to `"preferences"` SharedPreferences.
     * Keys recognized as encrypted credentials go to [encryptedPrefsResult] if available;
     * otherwise they are skipped and [SettingsOutcome.encryptedSkipped] is set.
     */
    fun importSettings(
        context: Context,
        uri: Uri,
        encryptedPrefsResult: Result<SharedPreferences>? = null
    ): Result<SettingsOutcome> = runCatching {
        val rows = context.contentResolver.openInputStream(uri)?.use { inStream ->
            readSettingsCsv(inStream)
        } ?: error("Failed to open input stream for settings import")

        if (rows.isEmpty()) error("Empty settings file")

        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        val encEditor = encryptedPrefsResult?.getOrNull()?.edit()

        val stats = applySettingRows(rows, editor, encEditor)
        // A file holding only credentials, with the keystore unavailable: nothing to write, but
        // the user must be told the credentials were skipped (a warning, not a generic error).
        if (stats.imported == 0 && stats.encryptedSkipped > 0) {
            return@runCatching SettingsOutcome(encryptedSkipped = true)
        }
        // Nothing is written until commit(), so failing here leaves the settings untouched.
        check(stats.imported > 0) { "No setting could be imported" }

        check(editor.commit()) { "Could not save the settings" }
        check(encEditor?.commit() ?: true) { "Could not save the credentials" }
        Timber.tag(TAG).i(
            "Settings imported: %d entries (%d encrypted keys skipped)",
            stats.imported, stats.encryptedSkipped
        )
        SettingsOutcome(stats.encryptedSkipped > 0)
    }

    /** Counters returned by [applySettingRows]. */
    internal data class SettingsImportStats(val imported: Int, val encryptedSkipped: Int)

    /**
     * Stages [rows] into the editors: credential keys go to [encryptedEditor], everything else to
     * [plainEditor]. Credential keys are skipped (never written to plain preferences) when
     * [encryptedEditor] is null. Nothing is committed here.
     */
    internal fun applySettingRows(
        rows: List<Triple<String, String, String>>,
        plainEditor: SharedPreferences.Editor,
        encryptedEditor: SharedPreferences.Editor?
    ): SettingsImportStats {
        var imported = 0
        var encryptedSkipped = 0

        rows.forEach { (type, key, value) ->
            val targetEditor = if (key in ALL_ENCRYPTED_KEYS) {
                if (encryptedEditor == null) {
                    encryptedSkipped++
                    Timber.tag(TAG).d("Skipping encrypted key '%s' (keystore unavailable)", key)
                    return@forEach
                }
                encryptedEditor
            } else {
                plainEditor
            }

            runCatching {
                when (type.lowercase()) {
                    "string" -> targetEditor.putString(key, value)
                    "int", "integer" -> targetEditor.putInt(key, value.toInt())
                    "long" -> targetEditor.putLong(key, value.toLong())
                    "float" -> targetEditor.putFloat(key, value.toFloat())
                    "boolean" -> targetEditor.putBoolean(key, value.toBoolean())
                    else -> {
                        Timber.tag(TAG).w("Unknown type '%s' for key '%s', skipping", type, key)
                        return@forEach
                    }
                }
                imported++
            }.onFailure { e ->
                // Neither the value nor the exception message: NumberFormatException quotes the
                // input, and the value may be a credential (cookie, token).
                Timber.tag(TAG).e("Failed to import key '%s' (type=%s): %s", key, type, e.javaClass.simpleName)
            }
        }

        return SettingsImportStats(imported, encryptedSkipped)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Export crash logs
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Exports crash and debug logs to the given SAF URI as a combined text file.
     * Returns false if no logs exist.
     */
    fun exportCrashLogs(context: Context, uri: Uri): Result<Boolean> = runCatching {
        val logsDir = File(context.filesDir, LOGS_DIR)
        val crashLog = File(logsDir, CRASH_LOG_FILE)
        val debugLog = File(logsDir, DEBUG_LOG_FILE)

        if (!crashLog.exists() && !debugLog.exists()) {
            Timber.tag(TAG).i("No log files found")
            return@runCatching false
        }

        context.contentResolver.openOutputStream(uri)?.use { outStream ->
            outStream.bufferedWriter().use { writer ->
                if (crashLog.exists()) {
                    writer.write("=== CRASH LOG ===\n")
                    writer.write(crashLog.readText())
                    writer.write("\n\n")
                }
                if (debugLog.exists()) {
                    writer.write("=== DEBUG LOG ===\n")
                    writer.write(debugLog.readText())
                }
            }
            Timber.tag(TAG).i("Logs exported successfully")
        } ?: error("Failed to open output stream for log export")

        true
    }

    /**
     * Returns true if crash or debug log files exist.
     */
    fun hasLogs(context: Context): Boolean {
        val logsDir = File(context.filesDir, LOGS_DIR)
        return File(logsDir, CRASH_LOG_FILE).exists() || File(logsDir, DEBUG_LOG_FILE).exists()
    }

    /**
     * Deletes the crash and debug log files.
     */
    fun deleteLogs(context: Context): Result<Unit> = runCatching {
        val logsDir = File(context.filesDir, LOGS_DIR)
        val crashLog = File(logsDir, CRASH_LOG_FILE)
        val debugLog = File(logsDir, DEBUG_LOG_FILE)

        var deletedAny = false
        if (crashLog.exists() && crashLog.delete()) deletedAny = true
        if (debugLog.exists() && debugLog.delete()) deletedAny = true

        if (!deletedAny && hasLogs(context)) {
            error("Failed to delete log files")
        }
        Timber.tag(TAG).i("Logs deleted successfully")
    }

    // ──────────────────────────────────────────────────────────────────────
    // Clear cache
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Clears streaming cache, image cache, OkHttp cache, and temp files.
     * NEVER touches [DOWNLOAD_CACHE_DIR] or [DOWNLOAD_DB_FILE].
     *
     * Iterates over both [Context.getCacheDir] and [Context.getFilesDir] because
     * the user can configure the cache location to either via [ExoPlayerCacheLocation].
     */
    fun clearCache(context: Context): Result<Int> = runCatching {
        clearCacheDirs(context.cacheDir, context.filesDir, context.externalCacheDir)
    }

    /** File-based core of [clearCache], separated so it can be unit-tested with temp dirs. */
    internal fun clearCacheDirs(cacheDir: File, filesDir: File, externalCacheDir: File?): Int {
        var deletedCount = 0
        val bases = listOf(cacheDir, filesDir)

        bases.forEach { base ->
            // Streaming cache (exoplayer)
            deletedCount += safeDeleteDir(File(base, STREAMING_CACHE_DIR))
            // Image cache (coil)
            deletedCount += safeDeleteDir(File(base, IMAGE_CACHE_DIR))
        }

        // OkHttp cache in externalCacheDir
        externalCacheDir?.let { extCache ->
            // OkHttp uses the externalCacheDir directly; clear non-protected children
            extCache.listFiles()?.forEach { child ->
                if (child.name != DOWNLOAD_CACHE_DIR && child.name != DOWNLOAD_DB_FILE) {
                    deletedCount += safeDeleteDir(child)
                }
            }
        }

        // Temp files in cacheDir
        cacheDir.listFiles()?.forEach { child ->
            if (child.isFile && (child.name.startsWith("temp_") ||
                    child.name.startsWith("edit_meta_") ||
                    child.name == "widget_thumbnail.png")
            ) {
                if (child.delete()) deletedCount++
            }
        }

        Timber.tag(TAG).i("Cache cleared: %d items deleted", deletedCount)
        return deletedCount
    }

    private fun safeDeleteDir(dir: File): Int {
        if (!dir.exists()) return 0
        return try {
            val count = dir.walkBottomUp().count { it.delete() }
            Timber.tag(TAG).d("Deleted %s (%d items)", dir.name, count)
            count
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to delete %s", dir.absolutePath)
            0
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Delete downloads
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Deletes all downloaded media files and the ExoPlayer download database.
     */
    fun deleteDownloads(context: Context): Result<Int> = runCatching {
        deleteDownloadFiles(
            mediaBases = listOfNotNull(context.cacheDir, context.filesDir, context.externalCacheDir),
            // The download index lives in the databases dir (StandaloneDatabaseProvider), not in a
            // cache dir: deleting only the media would leave the index claiming songs are downloaded.
            downloadDatabase = context.getDatabasePath(DOWNLOAD_DB_FILE)
        )
    }

    /** File-based core of [deleteDownloads], separated so it can be unit-tested with temp dirs. */
    internal fun deleteDownloadFiles(mediaBases: List<File>, downloadDatabase: File): Int {
        var deletedCount = 0

        mediaBases.forEach { base ->
            deletedCount += safeDeleteDir(File(base, DOWNLOAD_CACHE_DIR))
        }

        // SQLite side files too, so a stale journal cannot be replayed onto a fresh index.
        listOf("", "-journal", "-wal", "-shm").forEach { suffix ->
            val file = File(downloadDatabase.path + suffix)
            if (file.exists() && file.delete()) deletedCount++
        }

        Timber.tag(TAG).i("Downloads deleted: %d items", deletedCount)
        return deletedCount
    }

    // ──────────────────────────────────────────────────────────────────────
    // Reset database (reversible)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Moves `data.db` (+ `-wal`, `-shm`) to `filesDir/rescue_backups/`.
     * Only the last reset is kept (previous backup is overwritten).
     * The app will start with a fresh empty database on next launch.
     *
     * The three files are moved together, untouched: no SQLite connection is opened, so nothing
     * can be lost by a failed checkpoint and a corrupt database is never seen by the default
     * error handler. Room replays the WAL when the backup is restored.
     */
    fun resetDatabase(context: Context): Result<Unit> = runCatching {
        val dbFile = context.getDatabasePath(DB_FILE_NAME)
        if (!dbFile.exists()) {
            error("No database to reset")
        }

        val backupDir = File(context.filesDir, RESCUE_BACKUPS_DIR)
        backupDir.mkdirs()

        moveDatabaseFiles(dbFile, File(backupDir, DB_FILE_NAME))

        Timber.tag(TAG).i("Database reset: backed up to %s", backupDir.absolutePath)
    }

    /**
     * Moves a SQLite database and its side files (`-wal`, `-shm`, `-journal`) from [from] to [to].
     * Stale side files at [to] are removed first, so a previous backup at [to] is replaced.
     * Files are renamed one after the other: an I/O failure part-way throws, but there is no
     * rollback, so the set can be left half-moved.
     */
    internal fun moveDatabaseFiles(from: File, to: File) {
        DB_FILE_SUFFIXES.forEach { suffix ->
            val stale = File(to.path + suffix)
            if (stale.exists()) check(stale.delete()) { "Cannot remove ${stale.name}" }
        }
        DB_FILE_SUFFIXES.forEach { suffix ->
            val source = File(from.path + suffix)
            if (source.exists()) moveReplacing(source, File(to.path + suffix))
        }
    }

    /**
     * Renames [source] to [target], replacing it. Falls back to copy + delete when the rename is
     * refused (existing target on some filesystems, or a different filesystem).
     */
    private fun moveReplacing(source: File, target: File) {
        if (source.renameTo(target)) return
        if (target.exists()) check(target.delete()) { "Cannot replace ${target.name}" }
        if (source.renameTo(target)) return
        source.copyTo(target, overwrite = true)
        check(source.delete()) { "Cannot remove ${source.name} after copying it" }
    }

    /**
     * Swaps the current database with the backup from [resetDatabase].
     * The current database becomes the new backup.
     */
    fun restoreDatabase(context: Context): Result<Unit> = runCatching {
        val backupFile = File(File(context.filesDir, RESCUE_BACKUPS_DIR), DB_FILE_NAME)
        if (!backupFile.exists()) {
            error("No backup to restore")
        }

        swapDatabaseFiles(context.getDatabasePath(DB_FILE_NAME), backupFile)
        Timber.tag(TAG).i("Database restored from backup")
    }

    /**
     * Swaps the live database with the backup, side files included. The live one is parked first
     * and only then replaced, so the normal path never removes the last good copy. On a failure the
     * parked database is put back only while the live one is still absent: a failure part-way
     * through bringing the backup in can leave the parked files under `data.db.swap`.
     */
    internal fun swapDatabaseFiles(dbFile: File, backupDb: File) {
        val parkedDb = File(backupDb.parentFile, "$DB_FILE_NAME.swap")
        try {
            if (dbFile.exists()) moveDatabaseFiles(dbFile, parkedDb)
            moveDatabaseFiles(backupDb, dbFile)
            // The database that was live becomes the new backup.
            if (parkedDb.exists()) moveDatabaseFiles(parkedDb, backupDb)
        } catch (e: Exception) {
            if (parkedDb.exists() && !dbFile.exists()) {
                runCatching { moveDatabaseFiles(parkedDb, dbFile) }
                    .onFailure { Timber.tag(TAG).e("Could not put the parked database back: %s", it.javaClass.simpleName) }
            }
            throw e
        }
    }

    /**
     * Returns true if a backup exists from a previous [resetDatabase].
     */
    fun hasBackup(context: Context): Boolean {
        return File(File(context.filesDir, RESCUE_BACKUPS_DIR), DB_FILE_NAME).exists()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Reset settings
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Backs up current settings, then clears both normal preferences and encrypted preferences.
     * The app will use default values for everything on next launch.
     */
    fun resetSettings(
        context: Context,
        encryptedPrefsResult: Result<SharedPreferences>? = null
    ): Result<Unit> = runCatching {
        backupSettingsFiles(
            sharedPrefsDir = File(context.applicationInfo.dataDir, SHARED_PREFS_DIR),
            backupDir = File(context.filesDir, RESCUE_BACKUPS_DIR)
        )

        // Clear normal preferences
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        Timber.tag(TAG).i("Normal preferences cleared")

        // Clear encrypted preferences
        val encPrefs = encryptedPrefsResult?.getOrNull()
        if (encPrefs != null) {
            encPrefs.edit().clear().commit()
            Timber.tag(TAG).i("Encrypted preferences cleared")
        } else {
            // If keystore is broken, try to delete the file directly
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.deleteSharedPreferences(ENCRYPTED_PREFS_NAME)
                    Timber.tag(TAG).i("Encrypted preferences file deleted (keystore unavailable)")
                } else {
                    Timber.tag(TAG).w("Cannot clear encrypted preferences: keystore unavailable and API < 24")
                }
            }.onFailure {
                Timber.tag(TAG).e(it, "Failed to delete encrypted preferences file")
            }
        }
    }

    /**
     * Swaps current settings with the backup from [resetSettings].
     * The current settings become the new backup.
     *
     * The XML files are swapped on disk, behind the SharedPreferences instances the `:rescue`
     * process already holds in memory: a later `commit()` would write that stale in-memory map
     * back over the restored file. The caller must therefore end the `:rescue` process right
     * after a successful restore.
     */
    fun restoreSettings(context: Context): Result<Unit> = runCatching {
        val backupDir = File(context.filesDir, RESCUE_BACKUPS_DIR)
        if (SETTINGS_FILE_NAMES.none { File(backupDir, it).exists() }) {
            error("No settings backup to restore")
        }

        swapSettingsFiles(File(context.applicationInfo.dataDir, SHARED_PREFS_DIR), backupDir)
        Timber.tag(TAG).i("Settings restored from backup")
    }

    /**
     * Copies the settings XML files of [sharedPrefsDir] into [backupDir], replacing the previous
     * backup. A settings file that does not exist leaves no stale copy behind.
     */
    internal fun backupSettingsFiles(sharedPrefsDir: File, backupDir: File) {
        backupDir.mkdirs()
        SETTINGS_FILE_NAMES.forEach { name ->
            val source = File(sharedPrefsDir, name)
            val backup = File(backupDir, name)
            if (source.exists()) source.copyTo(backup, overwrite = true) else backup.delete()
        }
    }

    /**
     * Swaps each settings XML file of [sharedPrefsDir] with its copy in [backupDir]: the backup
     * becomes live and the file that was live becomes the new backup. The live file is parked
     * first, so no step removes the last good copy.
     */
    internal fun swapSettingsFiles(sharedPrefsDir: File, backupDir: File) {
        sharedPrefsDir.mkdirs()
        SETTINGS_FILE_NAMES.forEach { name ->
            val live = File(sharedPrefsDir, name)
            val backup = File(backupDir, name)
            val parked = File(backupDir, "$name.swap")

            parked.delete()
            if (live.exists()) moveReplacing(live, parked)
            if (backup.exists()) moveReplacing(backup, live)
            if (parked.exists()) moveReplacing(parked, backup)
        }
    }

    /**
     * Returns true if a settings backup exists from a previous [resetSettings].
     */
    fun hasSettingsBackup(context: Context): Boolean {
        val backupDir = File(context.filesDir, RESCUE_BACKUPS_DIR)
        return SETTINGS_FILE_NAMES.any { File(backupDir, it).exists() }
    }

    /**
     * Deletes all backups (database and settings) from the rescue_backups directory.
     */
    fun deleteBackups(context: Context): Result<Unit> = runCatching {
        deleteBackupDir(File(context.filesDir, RESCUE_BACKUPS_DIR))
    }

    /** Deletes [backupDir] entirely and throws if anything is left, so the UI never reports a false success. */
    internal fun deleteBackupDir(backupDir: File) {
        if (!backupDir.exists()) {
            Timber.tag(TAG).d("No backups directory found to delete")
            return
        }
        val deletedCount = safeDeleteDir(backupDir)
        check(!backupDir.exists()) { "Failed to delete the backups directory" }
        Timber.tag(TAG).i("Deleted backups directory (%d items)", deletedCount)
    }
}
