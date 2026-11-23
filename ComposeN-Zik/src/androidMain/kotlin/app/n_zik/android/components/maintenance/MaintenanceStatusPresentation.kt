package app.n_zik.android.components.maintenance

import java.util.Locale

import androidx.compose.ui.graphics.Color
import androidx.media3.exoplayer.offline.Download
import androidx.work.WorkInfo

import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.extensions.discord.DiscordRpcError
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.updater.services.UpdateDownloadManager

/** The WorkManager one-shot jobs reported by the sheet (same names as their private constants). */
internal val MAINTENANCE_JOB_NAMES = listOf(
    "AutoBackupWorker",
    "MbBackfillWorker",
    "RewindMonthlyPlaylistWorker",
    "RewindYearlyPlaylistWorker",
    "RewindPostImportRegenerationWorker",
    "RewindReminderWorker",
    "RewindYearlyReminderWorker",
)

/** A job absent from WorkManager has a null state and no next run time. */
internal data class JobSnapshot(
    val name: String,
    val state: WorkInfo.State?,
    val runAtStartTime: Long,
)

/** Pending, running and absent jobs do not need attention. */
internal fun abnormalJobs(jobs: List<JobSnapshot>): List<JobSnapshot> =
    jobs.filter { it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED }

/** The string resource of a WorkManager state (SUCCEEDED maps to the shared "Done" word, shown on the backup row). */
internal fun workStateResId(state: WorkInfo.State): Int = when (state) {
    WorkInfo.State.SUCCEEDED -> R.string.maintenance_migration_done
    WorkInfo.State.FAILED -> R.string.maintenance_work_failed
    WorkInfo.State.RUNNING -> R.string.maintenance_work_running
    WorkInfo.State.ENQUEUED -> R.string.maintenance_work_pending
    WorkInfo.State.BLOCKED -> R.string.maintenance_work_pending
    WorkInfo.State.CANCELLED -> R.string.maintenance_work_cancelled
}

/** Health shown by the Maintenance settings card. */
internal sealed interface MaintenanceHealth {
    data object NoData : MaintenanceHealth
    data object Operational : MaintenanceHealth
    data class NeedsAttention(val count: Int) : MaintenanceHealth
}

/** Counts the sheet's error rows for the settings card. */
internal fun maintenanceHealth(
    snapshot: MaintenanceSnapshot?,
    discordError: DiscordRpcError?,
    updateState: UpdateDownloadManager.DownloadState,
    downloadsFailed: Int,
): MaintenanceHealth {
    if (snapshot == null) return MaintenanceHealth.NoData
    val broken = brokenSystemsCount(snapshot, discordError, updateState, downloadsFailed)
    return if (broken == 0) {
        MaintenanceHealth.Operational
    } else {
        MaintenanceHealth.NeedsAttention(broken)
    }
}

/** Counts each failing subsystem once. */
internal fun brokenSystemsCount(
    snapshot: MaintenanceSnapshot,
    discordError: DiscordRpcError?,
    updateState: UpdateDownloadManager.DownloadState,
    downloadsFailed: Int,
): Int {
    var count = 0
    if (snapshot.jobs?.let { abnormalJobs(it) }.orEmpty().isNotEmpty()) count++
    if (snapshot.lastCrash != null) count++
    if (snapshot.ytSession == MainApplication.CookieStatus.INVALID ||
        snapshot.ytSession == MainApplication.CookieStatus.EXPIRED
    ) count++
    if (discordError != null) count++
    if (snapshot.listenTogether?.stateResId == R.string.maintenance_lt_error) count++
    if (updateState is UpdateDownloadManager.DownloadState.Failed) count++
    if (snapshot.mbRateLimitedSeconds > 0L) count++
    if (downloadsFailed > 0) count++
    return count
}

/** Localized WorkManager state for the expanded Jobs row. */
internal data class JobDetailLine(
    val name: String,
    val stateResId: Int?,
    val abnormal: Boolean,
)

/** Jobs in alphabetical order, including jobs that have never run. */
internal fun jobDetailLines(jobs: List<JobSnapshot>?): List<JobDetailLine> {
    if (jobs.isNullOrEmpty()) return emptyList()
    return jobs
        .sortedBy { it.name.lowercase(Locale.ROOT) }
        .map { job ->
            val state = job.state
            JobDetailLine(
                name = job.name,
                stateResId = state?.let { workStateResId(it) } ?: R.string.maintenance_work_never,
                abnormal = state == WorkInfo.State.FAILED || state == WorkInfo.State.CANCELLED,
            )
        }
}

/** A row color key: the pure display mappings return keys instead of Compose colors so they stay unit-testable; [rowColor] resolves a key to the palette color. */
internal enum class RowColorKey {
    /** the secondary text color (the sheet's single neutral gray) */
    Secondary,
    /** the full accent color */
    Accent,
    /** the softened accent (the LT card's connecting/reconnecting states) */
    AccentSoft,
    /** the broken-state red */
    Red,
    /** the paused-state blue (the downloads pause flag) */
    Blue,
}

/** Resolves a [RowColorKey] to the palette color (keeps the pure mappings Compose-free). */
internal fun ColorPalette.rowColor(key: RowColorKey): Color = when (key) {
    RowColorKey.Secondary -> textSecondary
    RowColorKey.Accent -> accent
    RowColorKey.AccentSoft -> accent.copy(alpha = 0.6f)
    RowColorKey.Red -> red
    RowColorKey.Blue -> blue
}

/** Localized label and color for a download state. */
internal data class DownloadDetailLine(
    val titleResId: Int?,
    val stateResId: Int,
    val color: RowColorKey,
)

/** The decoded download title can be blank or absent. */
internal fun downloadDetailLine(title: String?, state: Int): DownloadDetailLine {
    val stateResId = when (state) {
        Download.STATE_QUEUED -> R.string.maintenance_download_queued
        Download.STATE_DOWNLOADING,
        Download.STATE_RESTARTING,
            -> R.string.maintenance_download_in_progress
        Download.STATE_COMPLETED -> R.string.maintenance_download_completed
        Download.STATE_FAILED -> R.string.maintenance_download_failed
        else -> R.string.maintenance_downloads_paused
    }
    val color = when (state) {
        Download.STATE_QUEUED,
        Download.STATE_DOWNLOADING,
        Download.STATE_RESTARTING,
            -> RowColorKey.Accent
        Download.STATE_FAILED -> RowColorKey.Red
        Download.STATE_COMPLETED -> RowColorKey.Secondary
        else -> RowColorKey.Blue
    }
    return DownloadDetailLine(
        titleResId = if (title.isNullOrBlank()) R.string.metadata_unknown_title else null,
        stateResId = stateResId,
        color = color,
    )
}

/** Status and relative time displayed for library sync. */
internal data class SyncRow(
    val resId: Int,
    val arg: Long? = null,
    val color: RowColorKey,
)

internal fun syncRow(running: Boolean, nowMillis: Long, lastSyncMillis: Long): SyncRow {
    if (running) return SyncRow(R.string.maintenance_sync_ytm_running, color = RowColorKey.Accent)
    return if (lastSyncMillis > 0L) {
        // coerceAtLeast(0): a clock rollback (NTP/user) must not render negative minutes
        SyncRow(R.string.sync_status_last, ((nowMillis - lastSyncMillis) / 60_000L).coerceAtLeast(0L), color = RowColorKey.Secondary)
    } else {
        SyncRow(R.string.sync_status_never, color = RowColorKey.Secondary)
    }
}

/** The Listen Together row's color key, aligned with the ConnectionStatusCard. */
internal fun ltColorKey(stateResId: Int?): RowColorKey = when (stateResId) {
    R.string.maintenance_lt_connected -> RowColorKey.Accent
    R.string.maintenance_lt_connecting,
    R.string.maintenance_lt_reconnecting,
        -> RowColorKey.AccentSoft
    R.string.maintenance_lt_error -> RowColorKey.Red
    else -> RowColorKey.Secondary
}

/** The auto-update row, aligned with the About update card. */
internal data class UpdateRow(
    val resId: Int,
    val arg: String? = null,
    val color: RowColorKey,
)

internal fun updateRow(
    isAutoUpdate: Boolean,
    newVersionAvailable: Boolean,
    updateState: UpdateDownloadManager.DownloadState,
): UpdateRow {
    if (!isAutoUpdate) return UpdateRow(R.string.maintenance_update_unavailable, color = RowColorKey.Secondary)
    return when (updateState) {
        is UpdateDownloadManager.DownloadState.Idle ->
            if (newVersionAvailable) {
                UpdateRow(R.string.updates_available, color = RowColorKey.Accent)
            } else {
                UpdateRow(R.string.updates_up_to_date, color = RowColorKey.Secondary)
            }
        UpdateDownloadManager.DownloadState.Starting,
        is UpdateDownloadManager.DownloadState.Downloading ->
            UpdateRow(
                R.string.maintenance_update_downloading,
                UpdateDownloadManager.downloadingVersion.orEmpty(),
                color = RowColorKey.Accent,
            )
        is UpdateDownloadManager.DownloadState.Completed ->
            UpdateRow(R.string.maintenance_update_completed, color = RowColorKey.Secondary)
        is UpdateDownloadManager.DownloadState.Failed ->
            UpdateRow(R.string.maintenance_update_failed, color = RowColorKey.Red)
    }
}

/** The MusicBrainz cooldown in whole minutes, rounded UP from seconds (0 = no cooldown). */
internal fun mbCooldownMinutes(seconds: Long): Long = if (seconds <= 0L) 0L else (seconds + 59L) / 60L

/** An enabled log can be unavailable until startup creates the file. */
internal data class DebugLogsRow(
    val baseResId: Int,
    val restartNeeded: Boolean = false,
)

internal fun debugLogsRow(debugEnabled: Boolean, logFileAvailable: Boolean): DebugLogsRow = when {
    !debugEnabled -> DebugLogsRow(R.string.maintenance_debug_off)
    logFileAvailable -> DebugLogsRow(R.string.maintenance_debug_on)
    else -> DebugLogsRow(R.string.maintenance_debug_on, restartNeeded = true)
}
