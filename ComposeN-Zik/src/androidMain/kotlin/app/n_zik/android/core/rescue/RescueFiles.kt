package app.n_zik.android.core.rescue

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
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

    // Well-known encrypted credential keys (same list as ExportSettingsDialog.buildCredentialEntries)
    internal val YTB_KEYS = listOf(
        "ytCookie", "ytVisitorData", "ytDataSyncId",
        "ytAccountName", "ytAccountEmail", "ytAccountChannelHandle",
        "ytAccountThumbnail", "enableYoutubeLogin", "enableYoutubeSync",
        "useYtLoginOnlyForBrowse"
    )
    internal val DISCORD_KEYS = listOf(
        "DiscordPersonalAccessToken", "discord_avatar", "discord_username",
        "isDiscordPresenceEnabled", "isDiscordBrowsingEnabled"
    )
    internal val LASTFM_KEYS = listOf(
        "lastfmSession", "lastfmUsername", "lastfmAvatarUrl",
        "isLastfmScrobblingEnabled", "isLastfmNowPlayingEnabled",
        "isLastfmScrobbleEnabled", "lastfmMinTrackDurationSeconds",
        "lastfmScrobbleThresholdPercent", "lastfmMaxScrobbleDelaySeconds"
    )
    internal val ALL_ENCRYPTED_KEYS = YTB_KEYS + DISCORD_KEYS + LASTFM_KEYS

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
        val myPid = android.os.Process.myPid()
        val packageName = context.packageName
        return am.runningAppProcesses?.any { proc ->
            proc.processName == packageName && proc.pid != myPid
        } == true
    }

    // ──────────────────────────────────────────────────────────────────────
    // Export database
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Exports the database to the given SAF URI.
     * Opens it as raw SQLite (no Room, no migration), checkpoints WAL, and copies.
     */
    fun exportDatabase(context: Context, uri: Uri): Result<Unit> = runCatching {
        val dbFile = context.getDatabasePath(DB_FILE_NAME)
        if (!dbFile.exists()) {
            error("Database file does not exist: ${dbFile.absolutePath}")
        }

        // Open raw SQLite, checkpoint WAL, then close before copying
        SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    Timber.tag(TAG).d("WAL checkpoint result: %d", cursor.getInt(0))
                }
            }
        }

        context.contentResolver.openOutputStream(uri)?.use { outStream ->
            FileInputStream(dbFile).use { inStream ->
                val bytes = inStream.copyTo(outStream)
                Timber.tag(TAG).i("Database exported: %d bytes", bytes)
            }
        } ?: error("Failed to open output stream for database export")
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
     * Copies to a temp file, validates SQLite header + quick_check, then replaces the real DB.
     */
    fun importDatabase(context: Context, uri: Uri): Result<Unit> = runCatching {
        val dbFile = context.getDatabasePath(DB_FILE_NAME)
        val tempFile = File(context.cacheDir, "rescue_import_temp.db")

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
                SQLiteDatabase.OPEN_READONLY
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

            // Delete WAL and SHM files
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            // Replace the actual DB
            tempFile.copyTo(dbFile, overwrite = true)
            Timber.tag(TAG).i("Database imported successfully")
        } finally {
            tempFile.delete()
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Export settings (CSV)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Exports settings to a CSV file at the given SAF URI.
     *
     * @param encryptedPrefsResult the Result of opening EncryptedSharedPreferences; if null
     *   or failure, encrypted credentials are skipped with a warning.
     */
    fun exportSettings(
        context: Context,
        uri: Uri,
        encryptedPrefsResult: Result<SharedPreferences>? = null,
        includeYtb: Boolean = false,
        includeDiscord: Boolean = false,
        includeLastfm: Boolean = false
    ): Result<Unit> = runCatching {
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
        if (includeYtb || includeDiscord || includeLastfm) {
            val encPrefs = encryptedPrefsResult?.getOrNull()
            if (encPrefs != null) {
                val all = encPrefs.all
                val keysToInclude = mutableListOf<String>()
                if (includeYtb) keysToInclude.addAll(YTB_KEYS)
                if (includeDiscord) keysToInclude.addAll(DISCORD_KEYS)
                if (includeLastfm) keysToInclude.addAll(LASTFM_KEYS)

                keysToInclude.forEach { key ->
                    all[key]?.let { value ->
                        val type = value::class.simpleName ?: "null"
                        if (type != "null") entries.add(Triple(type, key, value))
                    }
                }
            } else {
                Timber.tag(TAG).w("Cannot access encrypted preferences; exporting without credentials")
            }
        }

        Timber.tag(TAG).d("Exporting %d settings entries", entries.size)
        writeSettingsCsv(context, uri, entries)
    }

    private fun writeSettingsCsv(
        context: Context,
        uri: Uri,
        entries: List<Triple<String, String, Any>>
    ) {
        context.contentResolver.openOutputStream(uri)?.use { outStream ->
            outStream.bufferedWriter().use { writer ->
                writer.write("Type,Key,Value")
                writer.newLine()
                entries.forEach { (type, key, value) ->
                    // Escape CSV: double-quote values containing commas, quotes, or newlines
                    val escapedValue = csvEscape(value.toString())
                    val escapedKey = csvEscape(key)
                    writer.write("$type,$escapedKey,$escapedValue")
                    writer.newLine()
                }
            }
            Timber.tag(TAG).i("Settings exported: %d entries", entries.size)
        } ?: error("Failed to open output stream for settings export")
    }

    internal fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Import settings (CSV)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Imports settings from a CSV file.
     *
     * Normal keys go to `"preferences"` SharedPreferences.
     * Keys recognized as encrypted credentials go to [encryptedPrefsResult] if available.
     */
    fun importSettings(
        context: Context,
        uri: Uri,
        encryptedPrefsResult: Result<SharedPreferences>? = null
    ): Result<Unit> = runCatching {
        val lines = context.contentResolver.openInputStream(uri)?.use { inStream ->
            inStream.bufferedReader().readLines()
        } ?: error("Failed to open input stream for settings import")

        if (lines.isEmpty()) error("Empty settings file")

        // Skip header
        val dataLines = if (lines.first().startsWith("Type")) lines.drop(1) else lines

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val encPrefs = encryptedPrefsResult?.getOrNull()
        val encEditor = encPrefs?.edit()
        var importedCount = 0
        var encryptedSkipped = 0

        dataLines.forEach { line ->
            val parts = parseCsvLine(line)
            if (parts.size < 3) return@forEach
            val type = parts[0]
            val key = parts[1]
            val value = parts[2]

            val isEncrypted = key in ALL_ENCRYPTED_KEYS
            val targetEditor = if (isEncrypted) {
                if (encEditor == null) {
                    encryptedSkipped++
                    Timber.tag(TAG).d("Skipping encrypted key '%s' (keystore unavailable)", key)
                    return@forEach
                }
                encEditor
            } else {
                editor
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
                importedCount++
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "Failed to import key '%s' (type=%s, value=%s)", key, type, value)
            }
        }

        editor.commit()
        encEditor?.commit()
        Timber.tag(TAG).i(
            "Settings imported: %d entries (%d encrypted keys skipped)",
            importedCount, encryptedSkipped
        )
    }

    /**
     * Simple CSV line parser that handles quoted values.
     * Returns a list of field values from a single CSV line.
     */
    internal fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++ // skip escaped quote
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
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
        var deletedCount = 0
        val bases = listOf(context.cacheDir, context.filesDir)

        bases.forEach { base ->
            // Streaming cache (exoplayer)
            deletedCount += safeDeleteDir(File(base, STREAMING_CACHE_DIR))
            // Image cache (coil)
            deletedCount += safeDeleteDir(File(base, IMAGE_CACHE_DIR))
        }

        // OkHttp cache in externalCacheDir
        context.externalCacheDir?.let { extCache ->
            // OkHttp uses the externalCacheDir directly; clear non-protected children
            extCache.listFiles()?.forEach { child ->
                if (child.name != DOWNLOAD_CACHE_DIR && child.name != DOWNLOAD_DB_FILE) {
                    deletedCount += safeDeleteDir(child)
                }
            }
        }

        // Temp files in cacheDir
        context.cacheDir.listFiles()?.forEach { child ->
            if (child.isFile && (child.name.startsWith("temp_") ||
                    child.name.startsWith("edit_meta_") ||
                    child.name == "widget_thumbnail.png" ||
                    child.name.startsWith("rescue_import_"))
            ) {
                if (child.delete()) deletedCount++
            }
        }

        Timber.tag(TAG).i("Cache cleared: %d items deleted", deletedCount)
        deletedCount
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
        var deletedCount = 0
        val bases = listOfNotNull(context.cacheDir, context.filesDir, context.externalCacheDir)

        bases.forEach { base ->
            deletedCount += safeDeleteDir(File(base, DOWNLOAD_CACHE_DIR))
            val dbFile = File(base, DOWNLOAD_DB_FILE)
            if (dbFile.exists() && dbFile.delete()) {
                deletedCount++
            }
        }

        Timber.tag(TAG).i("Downloads deleted: %d items", deletedCount)
        deletedCount
    }

    // ──────────────────────────────────────────────────────────────────────
    // Reset database (reversible)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Moves `data.db` (+ `-wal`, `-shm`) to `filesDir/rescue_backups/`.
     * Only the last reset is kept (previous backup is overwritten).
     * The app will start with a fresh empty database on next launch.
     */
    fun resetDatabase(context: Context): Result<Unit> = runCatching {
        val dbFile = context.getDatabasePath(DB_FILE_NAME)
        if (!dbFile.exists()) {
            error("No database to reset")
        }

        val backupDir = File(context.filesDir, RESCUE_BACKUPS_DIR)
        backupDir.mkdirs()

        // Checkpoint WAL before moving
        runCatching {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            }
        }.onFailure {
            Timber.tag(TAG).w(it, "WAL checkpoint failed during reset; proceeding anyway")
        }

        // Move DB files to backup
        dbFile.copyTo(File(backupDir, DB_FILE_NAME), overwrite = true)
        dbFile.delete()

        val walFile = File(dbFile.path + "-wal")
        val shmFile = File(dbFile.path + "-shm")
        if (walFile.exists()) walFile.delete()
        if (shmFile.exists()) shmFile.delete()

        Timber.tag(TAG).i("Database reset: backed up to %s", backupDir.absolutePath)
    }

    /**
     * Swaps the current database with the backup from [resetDatabase].
     * The current database becomes the new backup.
     */
    fun restoreDatabase(context: Context): Result<Unit> = runCatching {
        val backupDir = File(context.filesDir, RESCUE_BACKUPS_DIR)
        val backupFile = File(backupDir, DB_FILE_NAME)
        if (!backupFile.exists()) {
            error("No backup to restore")
        }

        val dbFile = context.getDatabasePath(DB_FILE_NAME)
        val tempFile = File(context.cacheDir, "rescue_swap_temp.db")

        try {
            // Checkpoint current DB if it exists
            if (dbFile.exists()) {
                runCatching {
                    SQLiteDatabase.openDatabase(
                        dbFile.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READWRITE
                    ).use { db ->
                        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                    }
                }

                // Move current to temp
                dbFile.copyTo(tempFile, overwrite = true)

                // Delete current WAL/SHM
                val walFile = File(dbFile.path + "-wal")
                val shmFile = File(dbFile.path + "-shm")
                if (walFile.exists()) walFile.delete()
                if (shmFile.exists()) shmFile.delete()
            }

            // Restore backup to DB location
            backupFile.copyTo(dbFile, overwrite = true)

            // Move current (if any) to backup for future restore
            if (tempFile.exists()) {
                tempFile.copyTo(backupFile, overwrite = true)
            } else {
                backupFile.delete()
            }

            Timber.tag(TAG).i("Database restored from backup")
        } finally {
            tempFile.delete()
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
        val backupDir = File(context.filesDir, RESCUE_BACKUPS_DIR)
        backupDir.mkdirs()

        // Backup normal preferences
        val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/$PREFS_NAME.xml")
        val backupPrefsFile = File(backupDir, "$PREFS_NAME.xml")
        if (prefsFile.exists()) {
            prefsFile.copyTo(backupPrefsFile, overwrite = true)
        } else {
            backupPrefsFile.delete() // No normal prefs to backup
        }

        // Backup encrypted preferences
        val encPrefsFile = File(context.applicationInfo.dataDir, "shared_prefs/$ENCRYPTED_PREFS_NAME.xml")
        val backupEncPrefsFile = File(backupDir, "$ENCRYPTED_PREFS_NAME.xml")
        if (encPrefsFile.exists()) {
            encPrefsFile.copyTo(backupEncPrefsFile, overwrite = true)
        } else {
            backupEncPrefsFile.delete() // No encrypted prefs to backup
        }

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
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
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
     */
    fun restoreSettings(context: Context): Result<Unit> = runCatching {
        val backupDir = File(context.filesDir, RESCUE_BACKUPS_DIR)
        val backupPrefsFile = File(backupDir, "$PREFS_NAME.xml")
        val backupEncPrefsFile = File(backupDir, "$ENCRYPTED_PREFS_NAME.xml")
        
        if (!backupPrefsFile.exists() && !backupEncPrefsFile.exists()) {
            error("No settings backup to restore")
        }

        val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        sharedPrefsDir.mkdirs()

        val prefsFile = File(sharedPrefsDir, "$PREFS_NAME.xml")
        val encPrefsFile = File(sharedPrefsDir, "$ENCRYPTED_PREFS_NAME.xml")

        // Temporary swap space
        val tempPrefsFile = File(context.cacheDir, "rescue_swap_prefs.xml")
        val tempEncPrefsFile = File(context.cacheDir, "rescue_swap_enc_prefs.xml")

        try {
            // Move current to temp
            if (prefsFile.exists()) prefsFile.copyTo(tempPrefsFile, overwrite = true)
            if (encPrefsFile.exists()) encPrefsFile.copyTo(tempEncPrefsFile, overwrite = true)

            // Restore backup to active
            if (backupPrefsFile.exists()) {
                backupPrefsFile.copyTo(prefsFile, overwrite = true)
            } else {
                prefsFile.delete()
            }
            if (backupEncPrefsFile.exists()) {
                backupEncPrefsFile.copyTo(encPrefsFile, overwrite = true)
            } else {
                encPrefsFile.delete()
            }

            // Move temp to backup (for reversible swap)
            if (tempPrefsFile.exists()) {
                tempPrefsFile.copyTo(backupPrefsFile, overwrite = true)
            } else {
                backupPrefsFile.delete()
            }
            if (tempEncPrefsFile.exists()) {
                tempEncPrefsFile.copyTo(backupEncPrefsFile, overwrite = true)
            } else {
                backupEncPrefsFile.delete()
            }

            Timber.tag(TAG).i("Settings restored from backup")
        } finally {
            tempPrefsFile.delete()
            tempEncPrefsFile.delete()
        }
    }

    /**
     * Returns true if a settings backup exists from a previous [resetSettings].
     */
    fun hasSettingsBackup(context: Context): Boolean {
        val backupDir = File(context.filesDir, RESCUE_BACKUPS_DIR)
        return File(backupDir, "$PREFS_NAME.xml").exists() || 
               File(backupDir, "$ENCRYPTED_PREFS_NAME.xml").exists()
    }

    /**
     * Deletes all backups (database and settings) from the rescue_backups directory.
     */
    fun deleteBackups(context: Context): Result<Unit> = runCatching {
        val backupDir = File(context.filesDir, RESCUE_BACKUPS_DIR)
        if (backupDir.exists()) {
            val deletedCount = safeDeleteDir(backupDir)
            Timber.tag(TAG).i("Deleted backups directory (%d items)", deletedCount)
        } else {
            Timber.tag(TAG).d("No backups directory found to delete")
        }
    }
}
