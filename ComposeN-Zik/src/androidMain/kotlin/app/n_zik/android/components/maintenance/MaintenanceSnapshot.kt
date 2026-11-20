package app.n_zik.android.components.maintenance

import java.io.File

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.work.WorkManager
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.profiles.profilePrefsFile
import app.n_zik.android.core.backup.BackupManager
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.configuredImageCacheBytes
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.maintenance.MaintenanceConvergenceState
import app.n_zik.android.core.maintenance.MaintenanceDbCleanupState
import app.n_zik.android.core.maintenance.MaintenanceDedupState
import app.n_zik.android.core.maintenance.MaintenanceDownloadRevalidationState
import app.n_zik.android.core.maintenance.MaintenanceDownloadSelfHealState
import app.n_zik.android.core.maintenance.MaintenanceStateStore
import app.n_zik.android.core.profiles.allDataDirs
import app.n_zik.android.core.profiles.coilImageCacheDir
import app.n_zik.android.core.profiles.downloadsDir
import app.n_zik.android.core.profiles.totalDirectorySize
import app.n_zik.android.download.services.ProfileDownloadCommands
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.enums.ExoPlayerDiskDownloadCacheMaxSize
import app.n_zik.android.extensions.lastfm.LastFmActions
import app.n_zik.android.extensions.musicbrainz.MBMetadataHelper
import app.n_zik.android.listentogether.ConnectionState
import app.n_zik.android.listentogether.ListenTogetherClient
import app.n_zik.android.listentogether.RoomRole
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.debug.profileDiagnosticsDir
import app.n_zik.android.utils.discordPersonalAccessTokenKey
import app.n_zik.android.utils.encryptedPreferences
import app.n_zik.android.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.getLastSyncTime
import app.n_zik.android.utils.isIgnoringBatteryOptimizations
import app.n_zik.android.utils.logDebugEnabledKey
import app.n_zik.android.utils.preferences
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

private val CRASH_TIMESTAMP_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")
private const val CRASH_STACKTRACE_MARKER = "Stacktrace:"

/**
 * The LAST COMPLETE crash block of a `N-Zik_crash_log.txt` content (blocks are appended,
 * each headed by a `LocalDateTime` line, then device info, then `Stacktrace:`).
 *
 * A trailing block cut off by a brutal process death (the handler is killed mid-write:
 * the timestamp line is flushed but the `Stacktrace:` marker or the first stack line is
 * missing) is skipped in favor of the previous complete block, so a crash never reports
 * as "None" while an older readable block is still there.
 *
 * @return the (timestamp line, first stacktrace line) of the last complete block, or
 * null when the content carries no complete block (file absent/empty -> "None" in the
 * sheet).
 */
internal fun parseLastCrashBlock(content: String): Pair<String, String>? {
    val lines = content.lines()
    var i = lines.size - 1
    while (i >= 0) {
        if (CRASH_TIMESTAMP_REGEX.containsMatchIn(lines[i])) {
            val markerIndex = (i + 1 until lines.size)
                .firstOrNull { lines[it].trim() == CRASH_STACKTRACE_MARKER }
            if (markerIndex != null) {
                val firstLine = (markerIndex + 1 until lines.size)
                    .map { lines[it] }
                    .firstOrNull { it.isNotBlank() && !it.all { c -> c == '=' } }
                    ?.trim()
                if (!firstLine.isNullOrBlank()) {
                    return lines[i] to firstLine
                }
            }
        }
        i--
    }
    return null
}

/** The number of COMPLETE crash blocks in a `N-Zik_crash_log.txt` content — the same completeness rule as [parseLastCrashBlock] (a timestamp line followed by the `Stacktrace:` marker), so the count never includes a block cut off by a brutal process death. */
internal fun countCrashBlocks(content: String): Int {
    val lines = content.lines()
    var count = 0
    for (i in lines.indices) {
        if (CRASH_TIMESTAMP_REGEX.containsMatchIn(lines[i])) {
            val markerIndex = (i + 1 until lines.size)
                .firstOrNull { lines[it].trim() == CRASH_STACKTRACE_MARKER }
            if (markerIndex != null) count++
        }
    }
    return count
}

/** One Listen Together reading taken at refresh time (the client's StateFlows are NOT observed live in the sheet — the spec reads them on open/refresh only). */
internal data class ListenTogetherSnapshot(
    val stateResId: Int,
    val roleResId: Int?,
    val roomCode: String?,
)

/**
 * Everything the sheet needs, loaded ONCE at open (and again on "refresh") — no polling,
 * no background refresh. Disk I/O (crash log, WorkManager, DB counts, cache walks) runs
 * on [NzikDispatchers.DATA]; a failed chunk falls back to its placeholder ("No data")
 * instead of aborting the whole sheet.
 */
internal data class MaintenanceSnapshot(
    val dedup: MaintenanceDedupState? = null,
    val convergence: MaintenanceConvergenceState? = null,
    val dbCleanup: MaintenanceDbCleanupState? = null,
    val downloadRevalidation: MaintenanceDownloadRevalidationState? = null,
    val downloadSelfHeal: MaintenanceDownloadSelfHealState? = null,
    val lastSyncTime: Long = 0L,
    val libraryCounts: IntArray? = null, // [songs, artists, albums, playlists]
    /** distinct library songs with at least one playback event, null when the read failed. */
    val listenedSongs: Int? = null,
    val dbSizeBytes: Long? = null,
    /** the active profile's settings file size, null when absent/empty or the read failed. */
    val settingsSizeBytes: Long? = null,
    /** current/max bytes, null when the reading failed. */
    val imagesCache: Pair<Long, Long>? = null,
    /** current/max bytes, null when the reading failed. */
    val downloadsCache: Pair<Long, Long>? = null,
    /** true when the configured download cache size is Disabled (the row shows "Turn off"). */
    val downloadsCacheDisabled: Boolean = false,
    val downloadsPaused: Boolean = false,
    /** the scheduled jobs, null when the WorkManager read failed (the jobs row shows "No data"). */
    val jobs: List<JobSnapshot>? = null,
    val backupInterval: Int = BackupManager.INTERVAL_NONE,
    val backupJob: JobSnapshot? = null,
    val mbRateLimitedSeconds: Long = 0L,
    val ytSession: MainApplication.CookieStatus = MainApplication.CookieStatus.NOT_LOGGED_IN,
    val listenTogether: ListenTogetherSnapshot? = null,
    /** true when a Discord RPC personal access token is configured (encrypted preferences). */
    val discordTokenConfigured: Boolean = false,
    val batteryOptimizationsIgnored: Boolean = false,
    /** (timestamp line, first stacktrace line) of the last crash block, null when none. */
    val lastCrash: Pair<String, String>? = null,
    /** the number of complete crash blocks in the log file (0 = no crash ever recorded). */
    val crashCount: Int = 0,
    val debugLogsEnabled: Boolean = false,
    /** true when debug is enabled and the log file exists with content (Export is available). */
    val debugLogFileAvailable: Boolean = false,
    val lastfmSessionAvailable: Boolean = false,
)

/**
 * Loads [MaintenanceSnapshot] on the DATA dispatcher. Memory reads are direct; every disk
 * I/O chunk is individually guarded so one failure only blanks its own row.
 */
@OptIn(UnstableApi::class)
internal suspend fun loadMaintenanceSnapshot(context: Context): MaintenanceSnapshot {
    return withContext(NzikDispatchers.DATA) {
        val app = context.applicationContext
        val preferences = app.preferences

        val dedup = runCatching { MaintenanceStateStore.readDedup(app) }.getOrNull()
        val convergence = runCatching { MaintenanceStateStore.readConvergence(app) }.getOrNull()
        val dbCleanup = runCatching { MaintenanceStateStore.readDbCleanup(app) }.getOrNull()
        val downloadRevalidation = runCatching { MaintenanceStateStore.readDownloadRevalidation(app) }.getOrNull()
        val downloadSelfHeal = runCatching { MaintenanceStateStore.readDownloadSelfHeal(app) }.getOrNull()

        val lastSyncTime = runCatching { getLastSyncTime() }.getOrDefault(0L)

        // Library: the 4 COUNT(*) DAO queries + the listened-songs count + the DB file size
        val libraryCounts = runCatching {
            intArrayOf(
                Database.songTable.countAll(),
                Database.artistTable.countAll(),
                Database.albumTable.countAll(),
                Database.playlistTable.countAll(),
            )
        }.getOrNull()
        val listenedSongs = runCatching { Database.eventTable.countListenedSongs() }.getOrNull()
        val dbSizeBytes = runCatching { app.getDatabasePath(Database.FILE_NAME).length() }.getOrNull()

        // Storage: images (coil, memory), downloads (media3 cache, memory)
        val activeProfileId = getActiveProfile(app)
        // The active profile's settings file (the profile sheet's Settings row source)
        val settingsSizeBytes =
            runCatching { profilePrefsFile(app, activeProfileId).length().takeIf { it > 0L } }.getOrNull()
        val imagesCache = runCatching {
            val max = configuredImageCacheBytes(preferences)
            // The ACTIVE profile's coil cache (tracked) + the other distinct coil dirs
            // (base + every profile, deduped — spec-profile-data-separation) walked.
            val activeCoilDir = coilImageCacheDir(app, activeProfileId)
            val activeCoil = ImageCacheFactory.getCacheSize()
            val otherCoil = allDataDirs(app) { coilImageCacheDir(app, it) }
                .filterNot { it.absolutePath == activeCoilDir.absolutePath }
                .sumOf { totalDirectorySize(it) }
            (activeCoil + otherCoil) to max
        }.getOrNull()
        val downloadsCache = runCatching {
            val size = preferences.getEnum(
                exoPlayerDiskDownloadCacheMaxSizeKey,
                ExoPlayerDiskDownloadCacheMaxSize.`2GB`,
            )
            // The ACTIVE profile's download cache (tracked) + the other distinct
            // download dirs (base + every profile, deduped — spec-profile-data-separation)
//
            // walked. Null active dir (a Disabled cache lives in a temp dir) is
            // excluded from the dedup, so it is never double-counted.
            val activeDownloadsDir = downloadsDir(app, activeProfileId)
            val activeDownloads = MyDownloadHelper.getDownloadCache(app).cacheSpace
            val otherDownloads = allDataDirs(app) { downloadsDir(app, it) }
                .filterNot { activeDownloadsDir != null && it.absolutePath == activeDownloadsDir.absolutePath }
                .sumOf { totalDirectorySize(it) }
            (activeDownloads + otherDownloads) to size.bytes
        }.getOrNull()
        val downloadsCacheDisabled = runCatching {
            preferences.getEnum(
                exoPlayerDiskDownloadCacheMaxSizeKey,
                ExoPlayerDiskDownloadCacheMaxSize.`2GB`,
            ) == ExoPlayerDiskDownloadCacheMaxSize.Disabled
        }.getOrDefault(false)
        val downloadsPaused = runCatching {
            ProfileDownloadCommands.capture(app)?.isPaused(app) == true
        }.getOrDefault(false)

        // Scheduled jobs (one chunk feeding TWO rows: the jobs row and the backup row's
        // last-run state — WorkManager is disk-backed: await on the DATA dispatcher)
        val jobs = runCatching {
            val workManager = WorkManager.getInstance(app)
            val all = MAINTENANCE_JOB_NAMES.map { name ->
                val info = workManager.getWorkInfosForUniqueWork(name).await().firstOrNull()
                JobSnapshot(name, info?.state, info?.nextScheduleTimeMillis ?: 0L)
            }
            all to all.firstOrNull { it.name == "AutoBackupWorker" }
        }.getOrNull()

        val backupInterval =
            runCatching { preferences.getInt(BackupManager.PREF_INTERVAL, BackupManager.INTERVAL_NONE) }
                .getOrDefault(BackupManager.INTERVAL_NONE)

        val mbRateLimitedSeconds =
            runCatching { MBMetadataHelper.Default.circuitOpenRemainingSeconds() }.getOrDefault(0L)

        val ytSession = runCatching { MainApplication.cookieStatus }.getOrNull()
            ?: MainApplication.CookieStatus.NOT_LOGGED_IN

        val listenTogether = runCatching {
            val client = ListenTogetherClient.getInstance()
            if (client == null) {
                // LT_NEVER_STARTED (spec matrix): the client was never started in this
                // process — a distinct state from "disconnected", so it gets its own label
                ListenTogetherSnapshot(
                    stateResId = R.string.maintenance_lt_not_started,
                    roleResId = null,
                    roomCode = null,
                )
            } else {
                ListenTogetherSnapshot(
                    stateResId = when (client.connectionState.value) {
                        ConnectionState.DISCONNECTED -> R.string.maintenance_lt_disconnected
                        ConnectionState.CONNECTING -> R.string.maintenance_lt_connecting
                        ConnectionState.CONNECTED -> R.string.maintenance_lt_connected
                        ConnectionState.RECONNECTING -> R.string.maintenance_lt_reconnecting
                        ConnectionState.ERROR -> R.string.maintenance_lt_error
                    },
                    roleResId = when (client.role.value) {
                        RoomRole.HOST -> R.string.maintenance_lt_host
                        RoomRole.GUEST -> R.string.maintenance_lt_guest
                        RoomRole.NONE -> null
                    },
                    roomCode = client.roomState.value?.roomCode,
                )
            }
        }.getOrNull()

        val discordTokenConfigured = runCatching {
            app.encryptedPreferences.getString(discordPersonalAccessTokenKey, "")
                ?.isNotBlank() ?: false
        }.getOrDefault(false)

        val batteryOptimizationsIgnored =
            runCatching { app.isIgnoringBatteryOptimizations }.getOrDefault(false)

        // Last crash: read the log file once — the row shows the LAST complete block's
        // presence (not its content) plus the total number of complete crash blocks
        val crashLogContent = runCatching {
            File(profileDiagnosticsDir(app), "N-Zik_crash_log.txt")
                .takeIf { it.exists() && it.length() > 0 }
                ?.readText()
        }.getOrNull()
        val lastCrash = crashLogContent?.let { parseLastCrashBlock(it) }
        val crashCount = crashLogContent?.let { countCrashBlocks(it) } ?: 0

        val debugLogsEnabled =
            runCatching { preferences.getBoolean(logDebugEnabledKey, false) }.getOrDefault(false)
        // The log file is only created at startup when debug is enabled (Timber tree), so
        // an enabled-but-not-restarted app has debug ON but no exportable file yet
        val debugLogFileAvailable =
            runCatching {
                debugLogsEnabled && File(profileDiagnosticsDir(app), "N-Zik_log.txt")
                    .let { it.exists() && it.length() > 0L }
            }.getOrDefault(false)
        val lastfmSessionAvailable =
            runCatching { LastFmActions.isSessionAvailable() }.getOrDefault(false)

        MaintenanceSnapshot(
            dedup = dedup,
            convergence = convergence,
            dbCleanup = dbCleanup,
            downloadRevalidation = downloadRevalidation,
            downloadSelfHeal = downloadSelfHeal,
            lastSyncTime = lastSyncTime,
            libraryCounts = libraryCounts,
            listenedSongs = listenedSongs,
            dbSizeBytes = dbSizeBytes,
            settingsSizeBytes = settingsSizeBytes,
            imagesCache = imagesCache,
            downloadsCache = downloadsCache,
            downloadsCacheDisabled = downloadsCacheDisabled,
            downloadsPaused = downloadsPaused,
            jobs = jobs?.first,
            backupInterval = backupInterval,
            backupJob = jobs?.second,
            mbRateLimitedSeconds = mbRateLimitedSeconds,
            ytSession = ytSession,
            listenTogether = listenTogether,
            discordTokenConfigured = discordTokenConfigured,
            batteryOptimizationsIgnored = batteryOptimizationsIgnored,
            lastCrash = lastCrash,
            crashCount = crashCount,
            debugLogsEnabled = debugLogsEnabled,
            debugLogFileAvailable = debugLogFileAvailable,
            lastfmSessionAvailable = lastfmSessionAvailable,
        )
    }
}
