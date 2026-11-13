package app.n_zik.android.core.rescue

import android.app.ActivityManager
import app.n_zik.android.core.backup.SettingsPreferencesBridge
import app.n_zik.android.components.glass.GlassProfilePreferences
import app.n_zik.android.components.ui.screens.profiles.evictProfileSecurePrefs
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.migration.RemovedSettingsMigration
import android.content.Context
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Build
import android.os.Process
import android.system.Os
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.PROFILE_NAMES_FILE_NAME
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.clearProfileFaceEntries
import app.it.fast4x.rimusic.utils.currentProfileEntries
import app.it.fast4x.rimusic.utils.discordAvatarKey
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.discordUsernameKey
import app.it.fast4x.rimusic.utils.enableYouTubeLoginKey
import app.it.fast4x.rimusic.utils.enableYouTubeSyncKey
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.isDiscordBrowsingEnabledKey
import app.it.fast4x.rimusic.utils.isDiscordPresenceEnabledKey
import app.it.fast4x.rimusic.utils.isProfileIdSafe
import app.it.fast4x.rimusic.utils.profileDisplayName
import app.it.fast4x.rimusic.utils.profileLastUsed
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.proxyPasswordEncryptedKey
import app.it.fast4x.rimusic.utils.readProfileIds
import app.it.fast4x.rimusic.utils.saveProfileDisplayName
import app.it.fast4x.rimusic.utils.saveProfileLastUsed
import app.it.fast4x.rimusic.utils.useYtLoginOnlyForBrowseKey
import app.it.fast4x.rimusic.utils.writeProfileEntries
import app.it.fast4x.rimusic.utils.ytAccountChannelHandleKey
import app.it.fast4x.rimusic.utils.ytAccountEmailKey
import app.it.fast4x.rimusic.utils.ytAccountNameKey
import app.it.fast4x.rimusic.utils.ytAccountThumbnailKey
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.it.fast4x.rimusic.utils.ytDataSyncIdKey
import app.it.fast4x.rimusic.utils.ytVisitorDataKey
import app.n_zik.android.appContext
import app.n_zik.android.core.backup.DatabaseBackup
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.core.backup.ProfileStateArchive
import app.n_zik.android.core.notifications.deleteProfileChannels
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.download.utils.InactiveDownloadCleanup
import app.n_zik.android.core.profiles.ProtectedProfileHistory
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.ProfileCacheLocationMigration
import app.n_zik.android.core.profiles.clearProfileShareFlags
import app.n_zik.android.core.profiles.coversDir
import app.n_zik.android.core.profiles.downloadsDir
import app.n_zik.android.core.profiles.invalidateProfileStorageIdentity
import app.n_zik.android.core.profiles.isLegacyStorageShared
import app.n_zik.android.core.profiles.isProcessStorageProfile
import app.n_zik.android.core.profiles.privateProfileMediaPaths
import app.n_zik.android.core.profiles.profileIndexDbFile
import app.n_zik.android.core.profiles.profileSharedItems
import app.n_zik.android.core.profiles.profileStorageGeneration
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.core.profiles.resolveProfileDataDirNames
import app.n_zik.android.core.profiles.waveformsDir
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
import app.n_zik.android.utils.debug.profileDiagnosticsDir
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element
import org.xml.sax.InputSource

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


    @Volatile
    private var rescueProfile: String? = null

    private val profileSuffix: String
        get() {
            val profile = checkNotNull(rescueProfile) {
                "RescueFiles.initialize(context) must be called first"
            }

            return if (profile != "default") "_$profile" else ""
        }
    private val DB_FILE_NAME: String
        get() = "data$profileSuffix.db"

    private const val RESCUE_BACKUPS_DIR = "rescue_backups"
    private const val CRASH_LOG_FILE = "N-Zik_crash_log.txt"
    private const val DEBUG_LOG_FILE = "N-Zik_log.txt"
    private const val LOGS_DIR = "logs"

    // Cache directories to clear (NEVER exo_downloads or exoplayer_internal.db)
    private const val STREAMING_CACHE_DIR = "exoplayer"
    private const val IMAGE_CACHE_DIR = "coil"

    private val PREFS_NAME: String
        get() = "preferences$profileSuffix"

    private val ENCRYPTED_PREFS_NAME: String
        get() = "secure_preferences$profileSuffix"
    /**
     * The directory holding every SharedPreferences XML of the app — ALWAYS the plain
     * `shared_prefs` dir: the profile suffix lives in the FILE name
     * (`preferences_<id>.xml`), never in the directory. A suffixed directory does not
     * exist on Android, which would silently back up (and restore) nothing.
     */
    private const val SHARED_PREFS_DIR = "shared_prefs"
    private val SETTINGS_FILE_NAMES: List<String>
        get() = listOf("$PREFS_NAME.xml", "$ENCRYPTED_PREFS_NAME.xml",
            "${GlassProfilePreferences.fileName(rescueTargetProfile())}.xml")

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
//
    // truth — BackupManager and ExportSettingsDialog must reference them, never re-declare.
    internal val YTB_KEYS = listOf(
        ytCookieKey, ytVisitorDataKey, ytDataSyncIdKey,
        ytAccountNameKey, ytAccountEmailKey, ytAccountChannelHandleKey,
        ytAccountThumbnailKey, enableYouTubeLoginKey, enableYouTubeSyncKey,
        useYtLoginOnlyForBrowseKey
    )
    // The legacy section keys plus every advanced-mode key (item 6/7/8): a backup that
    // carries the token but not the customization is an incomplete backup. The advanced
//
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

    fun initialize(context: Context) {
        if (rescueProfile != null) return

        synchronized(this) {
            if (rescueProfile == null) {
                rescueProfile = getActiveProfile(context)
            }
        }
    }

    /**
     * Sets the rescue target profile — the profile the data actions (database and
     * settings import/export, restore) operate on. In-memory only: nothing is moved
     * and the app's active profile is never switched (switching stays an explicit
     * choice in the profiles page). Call after [initialize].
     */
    internal fun setRescueProfile(profile: String) {
        synchronized(this) {
            checkNotNull(rescueProfile) { "RescueFiles.initialize(context) must be called first" }
            rescueProfile = profile
        }
    }

    /** The current rescue target profile (call after [initialize]). */
    internal fun rescueTargetProfile(): String = checkNotNull(rescueProfile) {
        "RescueFiles.initialize(context) must be called first"
    }

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

    // Process guard

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

    // Export database

    /**
     * The database could not be checkpointed because another connection (typically the main app
     * process) is using it. The export is refused rather than producing a file that silently
     * lacks the transactions still in the WAL; the UI tells the user to close the app.
     */
    class DatabaseBusyException(message: String) : IllegalStateException(message)

    /** Captures a rescue export through raw SQLite without starting Room. */
    internal fun prepareDatabaseExport(context: Context): DatabaseBackup.Export =
        DatabaseBackup.prepareRescueExport(context, rescueTargetProfile())

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

    // Import database (with validation)

    /** Validates the SQLite header of a stream. The first 16 bytes of a valid SQLite file are: "SQLite format 3\000" */
    internal fun isValidSqliteHeader(headerBytes: ByteArray): Boolean {
        val magic = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        if (headerBytes.size < magic.size) return false
        return headerBytes.copyOfRange(0, magic.size).contentEquals(magic)
    }

    /** Restores a SQLite or ZIP database backup into the profile selected for rescue. */
    fun importDatabase(context: Context, uri: Uri): Result<Unit> = runCatching {
        DatabaseBackup.restoreFromRescue(context, uri, rescueTargetProfile())
        Timber.tag(TAG).i("Database imported successfully")
    }

    // Export settings (CSV)

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
        val profile = rescueTargetProfile()
        val prefs = profileDataPrefs(context, profile)
        val entries = SettingsPreferencesBridge.export(context, prefs, profile)

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

    /** Writes settings as `Type,Key,Value` CSV using the same writer as the regular settings export, so values containing commas, quotes or line breaks round-trip through [readSettingsCsv]. */
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

    // Import settings (CSV)

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
        val profile = rescueTargetProfile()
        val target = checkNotNull(profileStorageIdentity(context, profile))
        val rows = context.contentResolver.openInputStream(uri)?.use { inStream ->
            readSettingsCsv(inStream)
        } ?: error("Failed to open input stream for settings import")

        if (rows.isEmpty()) error("Empty settings file")

        DatabaseRestoreJournal.withProfileStorageMutation(context) {
            check(profileStorageIdentity(context, profile) == target) { "The profile changed during settings import" }
            val editor = profileDataPrefs(context, profile).edit()
            val encEditor = encryptedPrefsResult?.getOrNull()?.edit()
            val glassEditor = SettingsPreferencesBridge.stageGlass(context, rows, profile)
            val stats = applySettingRows(rows, editor, encEditor) { key, value ->
                SettingsPreferencesBridge.stageStorageRow(context, target, editor, key, value)
            }
            val hasRemovedRows = rows.any { RemovedSettingsMigration.isRemovedKey(it.second) }
            if (stats.imported == 0 && stats.encryptedSkipped > 0 && glassEditor == null && !hasRemovedRows) {
                return@withProfileStorageMutation SettingsOutcome(encryptedSkipped = true)
            }
            check(stats.imported > 0 || glassEditor != null || hasRemovedRows) {
                "No setting could be imported"
            }

            check(editor.commit()) { "Could not save the settings" }
            check(encEditor?.commit() ?: true) { "Could not save the credentials" }
            check(glassEditor?.commit() ?: true) { "Could not save the Glass settings" }
            Timber.tag(TAG).i(
                "Settings imported: %d entries (%d encrypted keys skipped)",
                stats.imported, stats.encryptedSkipped
            )
            SettingsOutcome(stats.encryptedSkipped > 0)
        }
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
        encryptedEditor: SharedPreferences.Editor?,
        stageStorage: (String, String) -> Unit = { _, _ -> error("No storage migration target") },
    ): SettingsImportStats {
        var imported = 0
        var encryptedSkipped = 0
        RemovedSettingsMigration.stageRemoval(plainEditor)

        SettingsPreferencesBridge.normalizeStorageRows(rows).forEach { (type, key, value) ->
            if (SettingsPreferencesBridge.isGlassRecord(type)) return@forEach
            if (RemovedSettingsMigration.isRemovedKey(key)) return@forEach
            if (SettingsPreferencesBridge.isStorageSetting(key)) {
                stageStorage(key, value)
                imported++
                return@forEach
            }
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

    // Profile state (list + faces) — the archive the auto backup writes, managed
    // here by the manual import/export

    /**
     * Exports the combined profile state archive (the list + the base64 face of every
     * profile that has one + the base's custom name when it was renamed) to the document at
     * [uri] as a single .txt file. Errors when there is nothing to archive (no list file,
     * no face, no renamed base: a fresh install).
     */
    fun exportProfileState(context: Context, uri: Uri): Result<Unit> = runCatching {
        if (!ProfileStateArchive.hasState(context)) {
            error("No profile state to export")
        }
        context.contentResolver.openOutputStream(uri)?.use { outStream ->
            ProfileStateArchive.writeArchive(context, outStream)
        } ?: error("Failed to open output stream for profile state export")
        Timber.tag(TAG).i("Profile state exported")
    }

    /**
     * Imports the combined profile state archive from the document at [uri]: the list is
     * validated and written atomically, the display names are restored and every face is
     * materialized after a JPEG check. A legacy list-only .txt (no face lines) is still
     * accepted — the faces are then simply absent. Returns the restored list size plus the
     * number of faces and names actually written (a base-only archive restores 0 profiles,
     * its face and/or name); errors when the file carries no valid profile at all.
     */
    fun importProfileState(context: Context, uri: Uri): Result<ProfileStateArchive.ApplyResult> = runCatching {
        val archive = context.contentResolver.openInputStream(uri)?.use(ProfileStateArchive::readArchive)
            ?: error("Failed to open input stream for profile state import")
        val applied = runRescueMutation(context) { ProfileStateArchive.apply(context, archive) }.getOrThrow()
        Timber.tag(TAG).i(
            "Profile state imported: %d profiles, %d faces, %d names",
            applied.profiles, applied.faces, applied.names
        )
        applied
    }

    /**
     * The `(id, display name)` pairs carried by the profile state archive at [uri], read
     * WITHOUT applying it — the import-target dialog offers these as import targets so
     * every profile a bundle carries is visible before anything is written. The entries
     * are already validated by [ProfileStateArchive.readArchive] (unsafe lines dropped).
     * The base profile is never a list line, so it is only carried here when the archive
     * explicitly holds its renamed name (the `__name__default` line). Returns an empty
     * list when the file is unreadable or lists no valid profile.
     */
    internal fun readProfileStateEntries(context: Context, uri: Uri): List<Pair<String, String>> =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { inStream ->
                val archive = ProfileStateArchive.readArchive(inStream)
                archive.names[DEFAULT_PROFILE_ID]?.let { baseName ->
                    archive.entries + (DEFAULT_PROFILE_ID to baseName)
                } ?: archive.entries
            } ?: emptyList()
        }.getOrDefault(emptyList())

    /**
     * Deletes the user profiles in [ids] (the base ID is ignored — the base is never
     * deleted): the names-file line, the profile's own settings files (plain +
     * encrypted), its database (and its WAL/SHM/journal), its face entries (display
     * name + last use), its face files, and its 6 notification channels + pending
     * notifications (no orphan channel or surviving notification left behind).
     *
     * When the ACTIVE profile is among [ids], the active slot is switched to the base
     * first, so the next launch starts on a valid profile — the app must be relaunched
     * for the deletion to take effect. The caller must ensure the main process is not
     * running (RescueScreen.guardWrite): a live main process would commit its
     * in-memory preferences back over the deleted files.
     *
     * Returns the number of profiles actually deleted.
     */
    internal fun deleteProfiles(context: Context, ids: List<String>): Result<Int> =
        runRescueMutation(context) {
            ids.distinct().count { deleteRegisteredProfile(context, it, allowSelected = true) }
        }

    internal fun deleteRegisteredProfile(context: Context, id: String, allowSelected: Boolean = false): Boolean =
        DatabaseRestoreJournal.withProfileStorageMutation(context) {
            if (id == DEFAULT_PROFILE_ID || id !in context.readProfileIds() ||
                (!allowSelected && isProcessStorageProfile(context, id))
            ) return@withProfileStorageMutation false
            check(!ProfileResetSnapshot.hasPendingOperation(context.filesDir.resolve("$RESCUE_BACKUPS_DIR/profiles"))) {
                "Finish the pending profile reset or restore first"
            }
            val directory = profileDeletionDirectory(context)
            check(directory.canonicalFile == directory && (directory.isDirectory || directory.mkdirs()))
            val marker = directory.resolve(id)
            check(marker.isFile || marker.createNewFile()) { "Could not record the profile deletion" }
            finishProfileDeletion(context, marker)
            true
        }

    /** Called under the storage lock before opening or registering profiles. */
    internal fun resumeProfileDeletions(context: Context, beforeStorageBound: Boolean = false) {
        val directory = profileDeletionDirectory(context)
        if (!directory.exists()) return
        check(directory.canonicalFile == directory && directory.isDirectory)
        checkNotNull(directory.listFiles()).sortedBy { it.name }.forEach { marker ->
            check(beforeStorageBound || !isProcessStorageProfile(context, marker.name)) {
                "Cannot finish deleting the bound profile"
            }
            finishProfileDeletion(context, marker)
        }
    }

    private fun profileDeletionDirectory(context: Context): File =
        context.filesDir.canonicalFile.resolve("profile-deletions")

    private fun finishProfileDeletion(context: Context, marker: File) {
        val id = marker.name
        check(id != DEFAULT_PROFILE_ID && id.isNotBlank() && isProfileIdSafe(id))
        check(marker.canonicalFile == marker && marker.isFile)
        if (id == getActiveProfile(context)) {
            check(context.profilePreferences.edit().putString(activeProfileKey, DEFAULT_PROFILE_ID).commit()) {
                "Could not detach the profile selected for deletion"
            }
        }
        val paths = profileDeletionPaths(context, id)
        paths.forEach(ProfileResetSnapshot::validateTree)
        deleteProfileSettingsFiles(context, id)
        evictProfileSecurePrefs(id)
        paths.forEach(ProfileResetSnapshot::deleteVerifiedTree)
        context.clearProfileShareFlags(id)
        context.clearProfileFaceEntries(id)
        if (id in context.readProfileIds()) {
            invalidateProfileStorageIdentity(context, id)
            check(context.writeProfileEntries(context.currentProfileEntries().filterNot { it.first == id })) {
                "Could not update $PROFILE_NAMES_FILE_NAME"
            }
        }
        runCatching { deleteProfileChannels(context, id) }
            .onFailure { Timber.tag(TAG).w(it, "Could not purge profile notification channels") }
        check(marker.delete()) { "Could not complete the profile deletion" }
    }

    internal fun requireUnoccupiedProfileId(context: Context, id: String) {
        check(profileDeletionPaths(context, id).all { !it.exists() && it.canonicalFile == it }) {
            "The imported profile ID already owns unregistered data"
        }
    }

    private fun profileDeletionPaths(context: Context, id: String): List<File> =
        profileResetPaths(context, id).values.toList() +
            listOfNotNull(context.externalCacheDir, context.cacheDir).distinct()
                .map { it.canonicalFile.resolve("nzik-profile-network/$id") }

    /**
     * Factory-resets the profiles in [ids] (the base included — it is never a
     * names-file line, so it simply starts empty like the others, with its display
     * name back to the app default): each profile's database, its settings files
     * (plain + encrypted, so the account credentials too), its face files and its
     * face entries (display name + last use) are deleted — after [backupProfileData]
     * has moved them to the profile's reset-backup directory, so the reset is
     * reversible ([restoreProfiles], the same pattern as the database and settings
     * resets). Unlike [deleteProfiles], the profiles are NOT removed from the list:
     * their names-file line survives, so the profiles stay switchable — they simply
     * start empty.
     *
     * Because the display names are cleared, the names file (which mirrors the store)
     * is rewritten afterwards so the reset profiles' lines lose their name portion (a
     * no-op for the base, which is never a line). The active slot is untouched: a reset
     * profile still exists, so the next launch stays valid. The app must be relaunched
     * afterwards (the caller shows it — the settings and the database are re-read at
     * startup). The caller must ensure the main process is not running
     * (RescueScreen.guardWrite): a live main process would commit its in-memory
     * preferences back over the deleted files.
     *
     * Returns the number of profiles actually reset.
     */
    internal fun resetProfiles(context: Context, ids: List<String>): Result<Int> =
        runRescueMutation(context) {
            val toReset = ids.distinct()
            if (toReset.isNotEmpty()) {
                toReset.forEach { id ->
                    check(id == DEFAULT_PROFILE_ID || id in context.readProfileIds()) { "Profile no longer exists" }
                    check(GlassProfilePreferences.preferences(context, id).edit().commit()) { "Could not flush Glass settings" }
                    val paths = profileResetPaths(context, id)
                    ProfileResetSnapshot(profileBackupDir(context, id)).reset(paths, { faceEntries(context, id) }) {
                        deleteProfileSettingsFiles(context, id)
                        if (id != DEFAULT_PROFILE_ID) {
                            context.clearProfileShareFlags(id)
                        }
                        context.clearProfileFaceEntries(id)
                        check(context.writeProfileEntries(context.currentProfileEntries())) {
                            "Could not update the profile registry after reset"
                        }
                    }
                }

            }
            Timber.tag(TAG).i("Reset %d profile(s) to factory state", toReset.size)
            toReset.size
        }

    // Profile reset backups + restore (the reset is reversible, like the
    // database and settings resets)

    /** The database file name of [profileId] (data.db for the base — the app's own rule). */
    private fun profileDatabaseName(profileId: String): String =
        "data${if (profileId != DEFAULT_PROFILE_ID) "_$profileId" else ""}.db"

    /**
     * The backup directory of one profile's last reset:
     * `rescue_backups/profiles/<id>/`. It holds the database under its original name
     * (side files included), the settings XMLs under their stored names, the face files
     * (the `profile/` dir) and the face entries (`face_entries.txt`).
     */
    private fun profileBackupDir(context: Context, profileId: String): File {
        val generation = profileStorageGeneration(context, profileId)
        val name = if (generation == "legacy") profileId else "generation-$generation"
        return context.filesDir.canonicalFile.resolve("$RESCUE_BACKUPS_DIR/profiles/$name")
    }

    /** True when [profileId] carries a backup from a previous reset. */
    fun hasProfileBackup(context: Context, profileId: String): Boolean =
        (profileId == DEFAULT_PROFILE_ID || profileId in context.readProfileIds()) &&
            profileBackupDir(context, profileId).let {
                it.list()?.isNotEmpty() == true || ProfileResetSnapshot.hasInterruptedRestore(it)
            }

    /** The profile IDs that carry a reset backup (the non-empty `rescue_backups/profiles/` subdirectories) — the restore actions' availability. */
    fun profileBackupIds(context: Context): Set<String> =
        (context.readProfileIds() + DEFAULT_PROFILE_ID).filterTo(mutableSetOf()) {
            hasProfileBackup(context, it)
        }

    private fun profileResetPaths(context: Context, profileId: String): Map<String, File> = buildMap {
        val database = context.getDatabasePath(profileDatabaseName(profileId))
        val databaseDir = checkNotNull(database.parentFile).canonicalFile
        DB_FILE_SUFFIXES.forEach { suffix ->
            val name = database.name + suffix
            put(name, databaseDir.resolve(name))
        }
        val sharedPrefsDir = File(context.applicationInfo.dataDir, SHARED_PREFS_DIR).canonicalFile
        val suffix = if (profileId == DEFAULT_PROFILE_ID) "" else "_$profileId"
        listOf("preferences$suffix", "secure_preferences$suffix", GlassProfilePreferences.fileName(profileId)).forEach { name ->
            put("$name.xml", sharedPrefsDir.resolve("$name.xml"))
            put("$name.xml.bak", sharedPrefsDir.resolve("$name.xml.bak"))
        }
        put("profile", context.filesDir.canonicalFile.resolve("profiles/$profileId"))
        putAll(privateProfileMediaPaths(context, profileId))
    }

    private fun faceEntries(context: Context, profileId: String): String =
        "name=${context.profileDisplayName(profileId).orEmpty()}\n" +
            "lastUsed=${context.profileLastUsed(profileId) ?: 0L}\n"

    private fun restoreFaceEntries(context: Context, profileId: String, entries: File) {
        if (!entries.isFile) return
        var name = ""
        var lastUsed = 0L
        entries.forEachLine { line ->
            when {
                line.startsWith("name=") -> name = line.removePrefix("name=")
                line.startsWith("lastUsed=") -> lastUsed = line.removePrefix("lastUsed=").toLongOrNull() ?: 0L
            }
        }
        context.clearProfileFaceEntries(profileId)
        if (name.isNotEmpty()) context.saveProfileDisplayName(profileId, name)
        if (lastUsed > 0L) context.saveProfileLastUsed(profileId, lastUsed)
    }

    /**
     * Restores [profileId]'s data from its reset-backup directory: the database is
     * swapped with the live one (a fresh database created since the reset becomes the
     * new backup — the same ping-pong as [restoreDatabase]), the settings XMLs replace
     * the live ones, the face files replace the live dir and the face entries are
     * re-applied to the `profile_preferences` store. Errors when no backup exists.
     */
    private fun restoreProfileData(context: Context, profileId: String) {
        val backup = profileBackupDir(context, profileId)
        if (!hasProfileBackup(context, profileId)) {
            error("No backup to restore for $profileId")
        }

        if (ProfileResetSnapshot.isVerifiedSnapshot(backup) || ProfileResetSnapshot.hasInterruptedRestore(backup)) {
            ProfileResetSnapshot(backup).restore(profileResetPaths(context, profileId), { faceEntries(context, profileId) }) {
                restoreFaceEntries(context, profileId, it)
                check(context.writeProfileEntries(context.currentProfileEntries())) {
                    "Could not update the profile registry after restore"
                }
            }
            return
        }

        val suffix = if (profileId == DEFAULT_PROFILE_ID) "" else "_$profileId"

        // The database: swap with the live one (a profile used since the reset had a
        // fresh database — it becomes the new backup).
        val backupDb = File(backup, profileDatabaseName(profileId))
        if (backupDb.exists()) {
            swapDatabaseFiles(context.getDatabasePath(profileDatabaseName(profileId)), backupDb)
        }

        // The settings XMLs replace the live ones (fresh default files created since
        // the reset are overwritten, like the database is swapped).
        val sharedPrefsDir = File(context.applicationInfo.dataDir, SHARED_PREFS_DIR)
        listOf("preferences$suffix", "secure_preferences$suffix", GlassProfilePreferences.fileName(profileId)).forEach { name ->
            val src = File(backup, "$name.xml")
            if (src.exists()) moveReplacing(src, File(sharedPrefsDir, "$name.xml"))
        }

        // The face files: the backed-up dir replaces the live one.
        val backupFace = File(backup, "profile")
        if (backupFace.exists()) {
            val faceDir = File(context.filesDir, "profiles/$profileId")
            runCatching { faceDir.deleteRecursively() }
            faceDir.parentFile?.mkdirs()
            check(backupFace.renameTo(faceDir)) { "Could not restore the face files of $profileId" }
        }

        // The face entries — re-applied to the store.
        restoreFaceEntries(context, profileId, File(backup, "face_entries.txt"))

        // The SEPARATE non-regenerable data dirs + the suffixed index — ping-pong
        // (the data written since the reset becomes the new backup). An item the
//
        // profile SHARES now is not restored into the base location: its backup copy
        // stays parked in [backup] (spec-profile-data-separation). The sharing flags
//
        // are NOT restored (they were cleared with the reset — the default separate
        // matches the backed-up separate data).
        if (profileId != DEFAULT_PROFILE_ID) {
            val shared = context.profileSharedItems(profileId)
            fun swapBackedDir(backupEntry: String, item: ProfileDataItem, live: File?) {
                if (live == null || item in shared) return
                val backed = File(backup, backupEntry)
                if (backed.exists()) swapDataDir(live, backed)
            }
            swapBackedDir("downloads", ProfileDataItem.DOWNLOADS, downloadsDir(context, profileId))
            swapBackedDir("covers", ProfileDataItem.IMAGES, coversDir(context, profileId))
            swapBackedDir("waveforms", ProfileDataItem.WAVEFORMS, waveformsDir(context, profileId))
            if (ProfileDataItem.DOWNLOADS !in shared || ProfileDataItem.MEDIA_CACHE !in shared) {
                val liveIndex = profileIndexDbFile(context, profileId)
                val backedIndex = File(backup, liveIndex.name)
                if (backedIndex.exists()) swapProfileIndex(liveIndex, backedIndex)
            }
        }
    }

    /**
     * Restores the profiles in [ids] from their reset backups (created by
     * [resetProfiles] — the base included): their database is swapped with the live
     * one, their settings files replace the live ones, their face files are brought
     * back and their face entries (display name + last use) are re-applied. IDs
     * without a backup are skipped (the base has none until it is reset at least
     * once). The names file is rewritten afterwards so the restored display names
     * are mirrored again (a no-op for the base, which is never a line). The app must
     * be relaunched afterwards (the caller shows it). The caller must ensure the main
     * process is not running (RescueScreen.guardWrite).
     *
     * Returns the number of profiles actually restored.
     */
    internal fun restoreProfiles(context: Context, ids: List<String>): Result<Int> =
        runRescueMutation(context) {
            val toRestore = ids.distinct()
                .filter { hasProfileBackup(context, it) }
            toRestore.forEach { id -> restoreProfileData(context, id) }
            if (toRestore.isNotEmpty()) {
                // The restored display names are back in the store: mirror them again
                // in the names file.
                if (!context.writeProfileEntries(context.currentProfileEntries())) {
                    throw IOException("Could not update $PROFILE_NAMES_FILE_NAME")
                }
            }
            Timber.tag(TAG).i("Restored %d profile(s) from their reset backups", toRestore.size)
            toRestore.size
        }

    internal fun recoverProtectedProfileHistory(
        context: Context,
        archive: ProtectedProfileHistory.Archive,
        target: ProfileStorageIdentity,
    ): Result<Unit> = runCatching {
        DatabaseRestoreJournal.withRescueMutation(context, allowLegacyRecovery = true) {
            ProtectedProfileHistory.recover(context, archive, target)
        }
    }

    /**
     * Deletes the settings files of [profileId] (plain + encrypted — the base uses
     * the unsuffixed names, same rule as `Context.preferences`). A missing file is
     * not an error: the profile may have never written settings.
     */
    private fun deleteProfileSettingsFiles(context: Context, profileId: String) {
        GlassProfilePreferences.delete(context, profileId)
        val suffix = if (profileId == DEFAULT_PROFILE_ID) "" else "_$profileId"
        // The same names the app itself uses: plain settings + encrypted settings
        // (secure_preferences — the account credentials). Mirrors PREFS_NAME /
//
        // ENCRYPTED_PREFS_NAME, computed for [profileId] instead of the active profile.
        listOf("preferences$suffix", "secure_preferences$suffix").forEach { name ->
            // deleteSharedPreferences (API 24+) removes the shared_prefs XML from disk.
            if (!context.deleteSharedPreferences(name)) {
                Timber.tag(TAG).w("Could not delete %s.xml (it may be missing)", name)
            }
        }
    }

    // Per-profile data dirs (spec-profile-data-separation: the SEPARATE data of a
    // profile is wiped with the profile — the shared items are NEVER touched, they
//
    // belong to the base)

    /**
     * Deletes the data of [profileId]'s OWN namespace: its suffixed directories
     * (downloads, streaming cache, covers, Coil image cache, waveforms) and its
     * suffixed index database. The suffixed names are the profile's own whatever its
     * sharing flags say now (the base keeps the unsuffixed ones, NEVER touched), so
     * the wipe is UNCONDITIONAL: it also removes the stale suffixed data left behind
     * when an item the profile now shares used to be separate (spec-profile-data-separation).
     * A no-op for the base (its locations are the unsuffixed base ones). A missing
     * file is not an error: the profile may have never written data.
     *
     * Returns the number of files removed.
     */
    internal fun deleteProfileDataFiles(context: Context, profileId: String): Int {
        if (profileId == DEFAULT_PROFILE_ID) return 0
        val own = resolveProfileDataDirNames(profileId, emptySet())
        var deletedCount = 0
        // Downloads / media cache / Coil live under the profile's location setting
        // (System → cacheDir, Private → filesDir): walk BOTH bases — the setting may
//
        // have changed since the data was written (a Disabled cache wrote none).
        listOf(own.downloadsDir, own.mediaCacheDir, own.imageCacheDir).forEach { name ->
            deletedCount += safeDeleteDir(File(context.cacheDir, name))
            deletedCount += safeDeleteDir(File(context.filesDir, name))
        }
        // Covers + waveforms always live under filesDir.
        deletedCount += safeDeleteDir(File(context.filesDir, own.coversDir))
        deletedCount += safeDeleteDir(File(context.filesDir, own.waveformsDir))
        // The suffixed index is the profile's own database (a shared cache points at
        // the base one, never touched) — stale when the profile shares both caches now.
        deletedCount += removeWithSideFiles(profileIndexDbFile(context, profileId))
        return deletedCount
    }

    /** Deletes [file] + its SQLite side files (the [DB_FILE_SUFFIXES] set), returning the number of files removed — the same side-file rule as the global downloads delete, so a stale journal cannot be replayed onto a fresh index. */
    private fun removeWithSideFiles(file: File): Int {
        val directory = checkNotNull(file.parentFile).canonicalFile
        return DB_FILE_SUFFIXES.sumOf { suffix ->
            val target = directory.resolve(file.name + suffix)
            check(!target.exists() || target.isFile) { "Unexpected database side file" }
            ProfileResetSnapshot.deleteVerifiedTree(target)
        }
    }

    /** Both physical image cache roots of every registered non-base profile. */
    private fun profileImageCaches(context: Context): List<File> =
        context.readProfileIds().flatMap { id ->
            val names = resolveProfileDataDirNames(id, emptySet())
            listOf(context.cacheDir.canonicalFile, context.filesDir.canonicalFile).map { root ->
                root.resolve(names.imageCacheDir)
            }
        }

    /** The display name of the document at [uri], or `null` when it cannot be read — used to detect the profile tag carried by a backup/export file name. */
    internal fun documentDisplayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(
                uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()

    /**
     * The profile tag carried by a profiled backup or export file name, `null`
     * when the name is not one of them (legacy unprofiled names, other files).
     *
     * Pure (unit-tested without Android): the backup timestamp
     * (`yyyy-MM-dd_HHmmss`) anchors the match from the end, the app name anchors
     * the prefix — everything between them is the profile ID.
     */
    internal fun backupProfileTagOf(fileName: String, appName: String): String? {
        val match = BACKUP_TAG_SUFFIX.find(fileName) ?: return null
        val prefix = fileName.substring(0, match.range.first)
        val appPrefix = "${appName}_"
        if (!prefix.startsWith(appPrefix)) return null
        val tag = prefix.substring(appPrefix.length)
        return tag.takeIf { it.isNotEmpty() && isProfileIdSafe(it) }
    }

    /** The timestamped backup/export suffix: the auto-backup names (`…_<date>_AutoBackup.sqlite|zip` / `…_<date>_Settings|Profiles|Face_AutoBackup.csv|txt|jpg`) and the manual profile-state export names (`…_<date>_Profiles_Export.txt` / `…_<date>_Face_Export.jpg`). */
    private val BACKUP_TAG_SUFFIX = Regex(
        """_(\d{4}-\d{2}-\d{2}_\d{6})(?:_(?:Settings|Profiles|Face))?(?:_AutoBackup(?: \(\d+\))?\.(?i:sqlite|db|zip|csv|txt|jpg)|_(?:Profiles|Face)_Export\.(?:txt|jpg))$"""
    )

    // Export crash logs

    /**
     * Exports crash and debug logs to the given SAF URI as a combined text file.
     * Returns false if no logs exist.
     */
    fun exportCrashLogs(context: Context, uri: Uri): Result<Boolean> = runCatching {
        val profileLogs = profileDiagnosticsDir(context, rescueTargetProfile())
        val logsDir = if (profileLogs.isDirectory) profileLogs else File(context.filesDir, LOGS_DIR)
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
    fun hasLogs(context: Context, includeHistorical: Boolean = true): Boolean {
        val profileLogs = profileDiagnosticsDir(context, rescueTargetProfile())
        val directory = if (includeHistorical && !profileLogs.isDirectory) File(context.filesDir, LOGS_DIR) else profileLogs
        return directory.resolve(CRASH_LOG_FILE).isFile || directory.resolve(DEBUG_LOG_FILE).isFile
    }

    /**
     * Deletes the crash and debug log files.
     */
    fun deleteLogs(context: Context): Result<Unit> = runRescueMutation(context) {
        val target = checkNotNull(profileStorageIdentity(context, rescueTargetProfile()))
        val logsDir = context.filesDir.canonicalFile.resolve("profiles/${target.id}/diagnostics")
        val crashLog = File(logsDir, CRASH_LOG_FILE)
        val debugLog = File(logsDir, DEBUG_LOG_FILE)

        listOf(crashLog, debugLog).forEach { file ->
            check(!file.exists() || file.isFile) { "Unexpected diagnostic log file" }
            ProfileResetSnapshot.deleteVerifiedTree(file)
        }
        Timber.tag(TAG).i("Logs deleted successfully")
    }

    // Clear cache

    /** Clears verified streaming and image caches across the two internal roots. */
    fun clearCache(context: Context): Result<Int> = runRescueMutation(context) {
        check(ProfileDataItem.ALL.none { item ->
            item != ProfileDataItem.WAVEFORMS && isLegacyStorageShared(context, DEFAULT_PROFILE_ID, item)
        }) { "A legacy cache is still shared and protected" }
        val imageTargets = cacheTargets(
            context.cacheDir.canonicalFile,
            context.filesDir.canonicalFile,
            profileImageCaches(context),
            includeStreaming = false,
        )
        imageTargets.forEach(ProfileResetSnapshot::validateTree)
        val streamingFiles = cleanClosedMedia(context, InactiveDownloadCleanup.Kind.STREAMING)
        streamingFiles + imageTargets.sumOf(::safeDeleteDir)
    }

    /** Clears only recognized cache roots. Older unattributed files are retained. */
    internal fun clearCacheDirs(
        cacheDir: File,
        filesDir: File,
        profileCacheDirs: List<File> = emptyList(),
        includeStreaming: Boolean = true,
    ): Int = cacheTargets(cacheDir, filesDir, profileCacheDirs, includeStreaming).let { targets ->
        targets.forEach(ProfileResetSnapshot::validateTree)
        targets.sumOf(::safeDeleteDir)
    }

    private fun cacheTargets(
        cacheDir: File,
        filesDir: File,
        profileCacheDirs: List<File>,
        includeStreaming: Boolean,
    ): List<File> {
        val bases = listOf(cacheDir.canonicalFile, filesDir.canonicalFile).distinct()
        val known = bases.flatMap { base ->
            buildList {
                add(base.resolve(IMAGE_CACHE_DIR))
                if (includeStreaming) add(base.resolve(STREAMING_CACHE_DIR))
            }
        }
        return (known + profileCacheDirs).distinct()
    }

    private fun safeDeleteDir(dir: File): Int {
        val parent = checkNotNull(dir.parentFile).canonicalFile
        return ProfileResetSnapshot.deleteVerifiedTree(parent.resolve(dir.name))
    }

    /** Clears offline records and spans without replacing the rest of the Media3 index. */
    fun deleteDownloads(context: Context): Result<Int> = runRescueMutation(context) {
        cleanClosedMedia(context, InactiveDownloadCleanup.Kind.DOWNLOADS)
    }

    private fun cleanClosedMedia(context: Context, kind: InactiveDownloadCleanup.Kind): Int {
        val plans = (listOf(DEFAULT_PROFILE_ID) + context.readProfileIds()).distinct().map { id ->
            val profile = checkNotNull(profileStorageIdentity(context, id))
            checkNotNull(InactiveDownloadCleanup.capture(context, profile, kind, allowSelected = true)) {
                "The selected media store is shared or could not be verified"
            }
        }
        var files = 0
        plans.forEach { plan ->
            check(InactiveDownloadCleanup.execute(context, plan, allowSelected = true)) {
                "The selected media store changed after $files files were cleared"
            }
            files += plan.displayedFiles.count { '/' in it }
        }
        return files
    }

    // Reset database (reversible)

    /**
     * Moves `data.db` (+ `-wal`, `-shm`) to `filesDir/rescue_backups/`.
     * Only the last reset is kept (previous backup is overwritten).
     * The app will start with a fresh empty database on next launch.
     *
     * The three files are moved together, untouched: no SQLite connection is opened, so nothing
     * can be lost by a failed checkpoint and a corrupt database is never seen by the default
     * error handler. Room replays the WAL when the backup is restored.
     */
    fun resetDatabase(context: Context): Result<Unit> = runRescueMutation(context) {
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

    /** Moves the directory tree [from] to [to], replacing any stale tree at [to] first (the same "the set can be left half-moved" contract as [moveDatabaseFiles]: a failure part-way throws without a rollback). */
    private fun moveDirReplacing(from: File, to: File) {
        if (to.exists()) check(to.deleteRecursively()) { "Cannot replace ${to.absolutePath}" }
        if (from.renameTo(to)) return
        from.copyRecursively(to, overwrite = true)
        check(from.deleteRecursively()) { "Cannot remove ${from.absolutePath} after copying it" }
    }

    /** Swaps the live directory [live] with [backupDir] (the ping-pong of [swapDatabaseFiles] for directory trees): the live tree is parked first under a per-backup swap name, only then replaced by the backup tree — a tree created since the reset becomes the new backup, and the normal path never removes the last good copy of either. */
    internal fun swapDataDir(live: File, backupDir: File) {
        // The backup dir is per-profile, so the entry name alone is a unique
        // (and filesystem-safe) parking slot.
        val parked = File(backupDir.parentFile, "${backupDir.name}.swap")
        try {
            if (live.exists()) moveDirReplacing(live, parked)
            moveDirReplacing(backupDir, live)
            if (parked.exists()) moveDirReplacing(parked, backupDir)
        } catch (e: Exception) {
            if (parked.exists() && !live.exists()) {
                runCatching { moveDirReplacing(parked, live) }
                    .onFailure { Timber.tag(TAG).e("Could not put the parked data dir back: %s", it.javaClass.simpleName) }
            }
            throw e
        }
    }

    /** Swaps the live suffixed profile index [live] with its backup (the ping-pong of [swapDatabaseFiles]): the live one is parked first under a per-backup swap name (the backup dir is per-profile, so the name is unique), then replaced. */
    private fun swapProfileIndex(live: File, backupDb: File) {
        val parkedDb = File(backupDb.parentFile, "${live.name}.swap")
        try {
            if (live.exists()) moveDatabaseFiles(live, parkedDb)
            moveDatabaseFiles(backupDb, live)
            if (parkedDb.exists()) moveDatabaseFiles(parkedDb, backupDb)
        } catch (e: Exception) {
            if (parkedDb.exists() && !live.exists()) {
                runCatching { moveDatabaseFiles(parkedDb, live) }
                    .onFailure { Timber.tag(TAG).e("Could not put the parked profile index back: %s", it.javaClass.simpleName) }
            }
            throw e
        }
    }

    /**
     * Swaps the current database with the backup from [resetDatabase].
     * The current database becomes the new backup.
     */
    fun restoreDatabase(context: Context): Result<Unit> = runRescueMutation(context) {
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

    // Reset settings

    /**
     * Backs up current settings, then clears both normal preferences and encrypted preferences.
     * The app will use default values for everything on next launch.
     */
    fun resetSettings(
        context: Context,
        encryptedPrefsResult: Result<SharedPreferences>? = null
    ): Result<Unit> = runCatching {
        val glass = GlassProfilePreferences.preferences(context, rescueTargetProfile())
        check(glass.edit().commit()) { "Could not flush Glass settings" }
        backupSettingsFiles(
            sharedPrefsDir = File(context.applicationInfo.dataDir, SHARED_PREFS_DIR),
            backupDir = File(context.filesDir, RESCUE_BACKUPS_DIR)
        )

        val plain = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = plain.edit()
        plain.all.keys.filterNot(::isSettingsOnlyStorageKey).forEach(editor::remove)
        check(editor.commit()) { "Could not clear normal preferences" }
        Timber.tag(TAG).i("Normal preferences cleared")
        check(glass.edit().clear().commit()) { "Could not clear Glass settings" }

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

        val sharedPrefsDir = File(context.applicationInfo.dataDir, SHARED_PREFS_DIR)
        preserveSettingsOnlyStorage(sharedPrefsDir.resolve("$PREFS_NAME.xml"), backupDir.resolve("$PREFS_NAME.xml"))
        swapSettingsFiles(sharedPrefsDir, backupDir)
        Timber.tag(TAG).i("Settings restored from backup")
    }

    private fun isSettingsOnlyStorageKey(key: String): Boolean =
        SettingsPreferencesBridge.isStorageSetting(key) || ProfileCacheLocationMigration.isRequestKey(key)

    /** Settings-only snapshots never change the live paths or quota of the retained media. */
    private fun preserveSettingsOnlyStorage(live: File, backup: File) {
        val builder = DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences = false }.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        fun read(file: File) = if (file.exists()) {
            builder.parse(file).also { check(it.doctype == null && it.documentElement.tagName == "map") }
        } else {
            builder.newDocument().apply { appendChild(createElement("map")) }
        }
        fun storageEntries(root: Element): List<Element> =
            (0 until root.childNodes.length).mapNotNull { root.childNodes.item(it) as? Element }
                .filter { isSettingsOnlyStorageKey(it.getAttribute("name")) }

        val current = storageEntries(read(live).documentElement)
        val restored = read(backup)
        val target = restored.documentElement
        val old = storageEntries(target)
        if (current.isEmpty() && old.isEmpty()) return
        old.forEach(target::removeChild)
        current.forEach { target.appendChild(restored.importNode(it, true)) }
        val stage = File.createTempFile(".settings-storage-", ".xml", backup.parentFile)
        try {
            FileOutputStream(stage).use { output ->
                TransformerFactory.newInstance().newTransformer().transform(DOMSource(restored), StreamResult(output))
                output.fd.sync()
            }
            if (Build.VERSION.SDK_INT == 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Files.move(stage.toPath(), backup.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Os.rename(stage.path, backup.path)
            }
        } finally {
            stage.delete()
        }
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

    /** Serializes rescue file changes with imports and main-process startup. */
    private fun <T> runRescueMutation(context: Context, action: () -> T): Result<T> =
        runCatching { DatabaseRestoreJournal.withRescueMutation(context, action) }

    /** Deletes all backups (database, settings and the profile reset backups) from the rescue_backups directory. */
    fun deleteBackups(context: Context): Result<Unit> = runRescueMutation(context) {
        check(!ProfileResetSnapshot.hasPendingOperation(context.filesDir.resolve("$RESCUE_BACKUPS_DIR/profiles"))) {
            "Finish the pending profile reset or restore before deleting backups"
        }
        val backupDir = File(context.filesDir, RESCUE_BACKUPS_DIR)
        backupDir.takeIf(File::exists)?.let(DatabaseRestoreJournal::retireRescueBackups)
    }.mapCatching { retired ->
        if (retired != null) deleteBackupDir(retired)
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
