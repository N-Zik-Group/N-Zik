package app.n_zik.android.components.maintenance

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.it.fast4x.rimusic.enums.CoilDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerDiskDownloadCacheMaxSize
import app.it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.utils.coilCustomDiskCacheKey
import app.it.fast4x.rimusic.utils.coilDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.exoPlayerCustomCacheKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.getLastSyncTime
import app.it.fast4x.rimusic.utils.isIgnoringBatteryOptimizations
import app.it.fast4x.rimusic.utils.logDebugEnabledKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.syncStatus
import app.n_zik.android.BuildConfig
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.logs.CopyLogsDialog
import app.n_zik.android.components.dialog.logs.CrashLogDialog
import app.n_zik.android.components.dialog.logs.DebugLogDialog
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.settings.settingsEntryEnter
import app.n_zik.android.components.settings.settingsEntryExit
import app.n_zik.android.components.ui.header.clampedLabelMaxWidth
import app.n_zik.android.core.backup.BackupManager
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.profiles.allDataDirs
import app.n_zik.android.core.profiles.coilImageCacheDir
import app.n_zik.android.core.profiles.downloadsDir
import app.n_zik.android.core.profiles.totalDirectorySize
import app.n_zik.android.core.maintenance.DedupGroupRecord
import app.n_zik.android.core.maintenance.DedupGroupStatus
import app.n_zik.android.core.maintenance.DedupSkipReason
import app.n_zik.android.core.maintenance.MaintenanceConvergenceState
import app.n_zik.android.core.maintenance.MaintenanceDedupState
import app.n_zik.android.core.maintenance.MaintenanceDbCleanupState
import app.n_zik.android.core.maintenance.MaintenanceStateStore
import app.n_zik.android.core.migration.ConvergenceSummary
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.extensions.discord.DiscordRpcError
import app.n_zik.android.extensions.discord.DiscordRpcErrorState
import app.n_zik.android.extensions.lastfm.LastFmActions
import app.n_zik.android.extensions.musicbrainz.MBMetadataHelper
import app.n_zik.android.listentogether.ConnectionState
import app.n_zik.android.listentogether.ListenTogetherClient
import app.n_zik.android.listentogether.RoomRole
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.updater.services.UpdateDownloadManager
import app.n_zik.android.updater.services.Updater
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TAG = "MaintenanceSheet"

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

/**
 * One scheduled WorkManager job as displayed by the sheet.
 *
 * @param state the [WorkInfo.State], or null when no work info exists (never enqueued — OK)
 * @param runAtStartTime the next run time (0 when the job has none)
 */
internal data class JobSnapshot(
    val name: String,
    val state: WorkInfo.State?,
    val runAtStartTime: Long,
)

/**
 * The jobs that need attention: FAILED or CANCELLED ONLY (user decision — ENQUEUED /
 * BLOCKED / RUNNING are in-flight states, not alarm states). Absent jobs (state == null)
 * count as OK (JOBS_QUIET edge case of the spec).
 */
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

/**
 * The expansion lines of the artist-link-cleanup row: one line per removed link
 * (song title + removed artist name), mirroring the dedup group lines — so the three
 * chevron rows (dedup / convergence / cleanup) share the same "summary on top, detail
 * list underneath" shape when opened or closed.
 */
internal fun dbCleanupLines(state: MaintenanceDbCleanupState?): List<GroupLine> =
    state?.removedLinks?.map { link -> GroupLine(link.songTitle, link.artistName.orEmpty()) }
        ?: emptyList()

/**
 * The expanded artist-link-cleanup message when there are no per-link lines: "No data"
 * without a state, the clean sentence on a clean run, the counter sentence on a state
 * predating per-link records (one version cycle).
 */
internal fun dbCleanupEmptyRes(state: MaintenanceDbCleanupState?): Pair<Int, Int?> = when {
    state == null -> R.string.maintenance_no_data to null
    state.removed > 0 -> R.string.maintenance_db_cleanup_detail_removed to state.removed
    else -> R.string.maintenance_db_cleanup_detail_clean to null
}

/**
 * The trailing status of the "Same-name artist dedup" boot-pass row, as string
 * resource + its counter arguments: "No data" without a state, the "N groups · M
 * resolved · K skipped" counters when groups were detected, the "Nothing to dedup"
 * clean sentence on an empty run (the "Nothing to clean" pattern of the cleanup row).
 * Shared by the sheet's chevron row and the settings card's pass table so the two can
 * never drift.
 */
internal fun dedupStatusRes(state: MaintenanceDedupState?): Pair<Int, IntArray?> =
    when {
        state == null -> R.string.maintenance_no_data to null
        state.groups > 0 ->
            R.string.maintenance_dedup_summary to intArrayOf(state.groups, state.resolved, state.skipped)
        else -> R.string.maintenance_dedup_clean to null
    }

/**
 * The trailing status of the "Name convergence" boot-pass row (see [dedupStatusRes]):
 * the "N copies rewritten" counter on a changed run, the already-converged sentence
 * otherwise.
 */
internal fun convergenceStatusRes(state: MaintenanceConvergenceState?): Pair<Int, IntArray?> =
    when {
        state == null -> R.string.maintenance_no_data to null
        state.summary.changed > 0 ->
            R.string.maintenance_convergence_changed to intArrayOf(state.summary.changed)
        else -> R.string.maintenance_convergence_no_change to null
    }

/**
 * The trailing status of the "Artist link cleanup" boot-pass row (see [dedupStatusRes]):
 * the "N stale link(s) removed" counter on a removing run, the clean sentence otherwise.
 */
internal fun dbCleanupStatusRes(state: MaintenanceDbCleanupState?): Pair<Int, IntArray?> =
    when {
        state == null -> R.string.maintenance_no_data to null
        state.removed > 0 -> R.string.maintenance_db_cleanup_removed to intArrayOf(state.removed)
        else -> R.string.maintenance_db_cleanup_clean to null
    }

/**
 * Resolves a pass status cell (string resource + optional args) to its display text —
 * the single formatting point shared by the sheet's chevron rows and the card's table.
 */
@Composable
internal fun passStatusText(status: Pair<Int, IntArray?>): String {
    val args = status.second
    return if (args != null) stringResource(status.first, *args.toTypedArray()) else stringResource(status.first)
}

/**
 * The overall app health shown by the settings card: [NoData] when no snapshot could be
 * read at all, [Operational] when nothing is broken, [NeedsAttention] with the broken
 * count otherwise (the card renders "N systems need attention" in RED).
 */
internal sealed interface MaintenanceHealth {
    data object NoData : MaintenanceHealth
    data object Operational : MaintenanceHealth
    data class NeedsAttention(val count: Int) : MaintenanceHealth
}

/**
 * The card's health line from the full sheet snapshot + the live states the sheet
 * observes (the Discord error, the update download state, the failed-download count).
 * Only the sheet's RED rows are counted as broken systems, with the sheet's exact
 * rules; neutral states (not connected services, battery warnings, ...) are NOT counted.
 */
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

/**
 * The number of broken systems (the sheet's RED rows) in a readable snapshot: abnormal
 * jobs (FAILED/CANCELLED), a last crash block, an INVALID/EXPIRED YouTube session, a
 * Discord RPC error, a Listen Together error state, a failed update download, a
 * rate-limited MusicBrainz circuit, a failed download.
 */
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

/**
 * One convergence detail line of the expanded convergence row: the label string
 * resource plus its count argument (null for the "No data" / "No change" fallbacks).
 */
internal data class ConvergenceLine(
    val resId: Int,
    val count: Int? = null,
)

/**
 * The expanded convergence detail lines, one per NON-ZERO sweep counter, in the
 * [ConvergenceSummary] field order (the "N copies rewritten" row stays the main status —
 * this is the detail track). A null summary (no readable state) answers the shared
 * "No data" line; an all-zero summary answers the shared "Already converged" line.
 */
internal fun convergenceDetailLines(summary: ConvergenceSummary?): List<ConvergenceLine> {
    if (summary == null) return listOf(ConvergenceLine(R.string.maintenance_no_data))
    val lines = ArrayList<ConvergenceLine>(8)
    if (summary.changed > 0) lines.add(ConvergenceLine(R.string.maintenance_convergence_copies_rewritten, summary.changed))
    if (summary.unbackedSongs > 0) lines.add(ConvergenceLine(R.string.maintenance_convergence_unbacked_songs, summary.unbackedSongs))
    if (summary.unlinkedSongs > 0) lines.add(ConvergenceLine(R.string.maintenance_convergence_unlinked_songs, summary.unlinkedSongs))
    if (summary.customSongs > 0) lines.add(ConvergenceLine(R.string.maintenance_convergence_custom_songs, summary.customSongs))
    if (summary.unlinkedAlbums > 0) lines.add(ConvergenceLine(R.string.maintenance_convergence_unlinked_albums, summary.unlinkedAlbums))
    if (summary.inconsistentAlbums > 0) lines.add(ConvergenceLine(R.string.maintenance_convergence_inconsistent_albums, summary.inconsistentAlbums))
    if (summary.countMismatchAlbums > 0) lines.add(ConvergenceLine(R.string.maintenance_convergence_count_mismatch_albums, summary.countMismatchAlbums))
    if (summary.customAlbums > 0) lines.add(ConvergenceLine(R.string.maintenance_convergence_custom_albums, summary.customAlbums))
    if (lines.isEmpty()) lines.add(ConvergenceLine(R.string.maintenance_convergence_no_change))
    return lines
}

/**
 * One expanded jobs detail line: the job name, the string resource of its WorkManager
 * state (the "Never run" fallback when the job was never enqueued) and whether the job
 * needs attention (FAILED / CANCELLED — the sheet's abnormal predicate).
 */
internal data class JobDetailLine(
    val name: String,
    val stateResId: Int?,
    val abnormal: Boolean,
)

/**
 * The expanded jobs detail lines: ALL the scheduled jobs, alphabetically by name (the
 * dedup group order); the abnormal ones (FAILED/CANCELLED) are rendered RED by the
 * caller, the rest neutral. An absent or empty job list answers an empty list — the
 * row then shows its muted "No jobs" line.
 */
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

/**
 * A row color key: the pure display mappings return keys instead of Compose colors so
 * they stay unit-testable; [rowColor] resolves a key to the palette color.
 */
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

/**
 * One expanded downloads detail line (state mapping): the title (or the "Unknown
 * Title" fallback when the download carries no readable title) plus its state label
 * and row color — waiting and in-progress in accent, failed in red, paused in blue,
 * completed in the neutral gray.
 */
internal data class DownloadDetailLine(
    val titleResId: Int?,
    val stateResId: Int,
    val color: RowColorKey,
)

/**
 * The state mapping of one expanded downloads detail line. [title] is the decoded
 * download title (the "artist - title" stored in the download request); blank or
 * absent answers the "Unknown Title" fallback.
 */
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
        titleResId = if (title.isNullOrBlank()) R.string.unknown_title else null,
        stateResId = stateResId,
        color = color,
    )
}

/**
 * The sync row, aligned with the accounts card: running keeps the sheet's own wording
 * ("Sync in progress", accent — the sheet snapshot has no operation name); the idle
 * states reuse the card's resources ("Last sync: Nmin ago" with the relative whole
 * minutes, "Never synced") in the muted color.
 */
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
                UpdateRow(R.string.update_available, color = RowColorKey.Accent)
            } else {
                UpdateRow(R.string.up_to_date, color = RowColorKey.Secondary)
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

/**
 * The Debug logs row display, aligned with the app's actual state: off, on, or
 * on-but-restart-needed (the log file is created at startup when debug is enabled, so an
 * enabled-but-not-restarted app shows "On · restart needed"). The View + Export buttons
 * show whenever the switch is ON (the dialogs handle the missing-file state —
 * "Log unavailable" until the restart creates the log file).
 */
internal data class DebugLogsRow(
    val baseResId: Int,
    val restartNeeded: Boolean = false,
)

internal fun debugLogsRow(debugEnabled: Boolean, logFileAvailable: Boolean): DebugLogsRow = when {
    !debugEnabled -> DebugLogsRow(R.string.maintenance_debug_off)
    logFileAvailable -> DebugLogsRow(R.string.maintenance_debug_on)
    else -> DebugLogsRow(R.string.maintenance_debug_on, restartNeeded = true)
}

/** List detail-line indent: the ListMenu.Entry 32dp icon chip + its 12dp spacing. */
private val detailListIndent = 44.dp

/**
 * The display of one dedup group record: its status plus a short detail (when relevant).
 * When a group both merged and flagged rows, BOTH counters are shown
 * ([detailResId] with [detailArgs] = merged count and [detailArgs2] = flagged count).
 */
internal data class DedupGroupDisplay(
    val statusResId: Int,
    val detailResId: Int? = null,
    val detailArgs: Int? = null,
    val detailArgs2: Int? = null,
)

/**
 * Maps a persisted [DedupGroupRecord] to its display (status + short reason), so the
 * sheet stays a dumb view and the mapping is unit-tested.
 */
internal fun dedupGroupDisplay(record: DedupGroupRecord): DedupGroupDisplay {
    val statusResId = when (record.status) {
        DedupGroupStatus.RESOLVED -> R.string.maintenance_status_resolved
        DedupGroupStatus.SONG_RESOLVED -> R.string.maintenance_status_song_resolved
        DedupGroupStatus.SKIPPED -> R.string.maintenance_status_skipped
        DedupGroupStatus.DEFERRED -> R.string.maintenance_status_deferred
    }
    val detailResId: Int?
    val detailArgs: Int?
    val detailArgs2: Int?
    when {
        record.status == DedupGroupStatus.SKIPPED -> {
            detailResId = when (record.skipReason) {
                DedupSkipReason.CUSTOM_NAME -> R.string.maintenance_skip_custom_name
                DedupSkipReason.OFFLINE -> R.string.maintenance_skip_offline
                DedupSkipReason.NO_UNANIMOUS_CANDIDATE -> R.string.maintenance_skip_no_candidate
                DedupSkipReason.NETWORK_FAILURE -> R.string.maintenance_skip_network_failure
                null -> null
            }
            detailArgs = null
            detailArgs2 = null
        }
        record.mergedRows > 0 && record.flaggedRows > 0 -> {
            detailResId = R.string.maintenance_dedup_merged_flagged
            detailArgs = record.mergedRows
            detailArgs2 = record.flaggedRows
        }
        record.mergedRows > 0 -> {
            detailResId = R.string.maintenance_dedup_merged
            detailArgs = record.mergedRows
            detailArgs2 = null
        }
        record.flaggedRows > 0 -> {
            detailResId = R.string.maintenance_dedup_flagged
            detailArgs = record.flaggedRows
            detailArgs2 = null
        }
        else -> {
            detailResId = null
            detailArgs = null
            detailArgs2 = null
        }
    }
    return DedupGroupDisplay(statusResId, detailResId, detailArgs, detailArgs2)
}

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

/**
 * The number of COMPLETE crash blocks in a `N-Zik_crash_log.txt` content — the same
 * completeness rule as [parseLastCrashBlock] (a timestamp line followed by the
 * `Stacktrace:` marker), so the count never includes a block cut off by a brutal
 * process death.
 */
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

/**
 * The widest realistic cache result line ("9.3 GB used (100%)" — the largest custom cache
 * max with a full usage): the second reference of the shared status column width.
 */
private const val CACHE_STATUS_WIDTH_REF = "9.3 GB used (100%)"

/** Formats a byte count as a human-readable size (always MB, Locale.ROOT decimal). */
internal fun formatBytes(bytes: Long): String =
    String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)

/** Formats an epoch-millis timestamp as `dd/MM/yyyy HH:mm` in [zone] (device zone by default). */
internal fun formatTimestamp(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))

/**
 * The chevron rows of the boot passes share one trailing format: the last run's timestamp
 * in front, its status after ("27/09/2026 14:30 · 3 copies rewritten"). A missing timestamp
 * (the pass never ran) answers the plain [noDataText] status.
 */
internal fun lastRunLine(
    timestamp: Long?,
    statusText: String,
    noDataText: String,
    zone: ZoneId = ZoneId.systemDefault(),
): String =
    if (timestamp == null) noDataText else "${formatTimestamp(timestamp, zone)} · $statusText"

/**
 * The cache result line, aligned with the Data settings cache rows: the short file
 * size followed by " used (<pct>%)" — a zero max (Unlimited) answers
 * " used (Unlimited)" instead of a percentage.
 */
@Composable
private fun cacheUsedStatusText(context: Context, usedBytes: Long, maxBytes: Long): String {
    val used = Formatter.formatShortFileSize(context, usedBytes)
    return if (maxBytes > 0L) {
        "$used${stringResource(R.string.used)} (${usedBytes * 100 / maxBytes}%)"
    } else {
        "$used${stringResource(R.string.used)} (${stringResource(R.string.unlimited)})"
    }
}

/**
 * One Listen Together reading taken at refresh time (the client's StateFlows are NOT
 * observed live in the sheet — the spec reads them on open/refresh only).
 */
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
    val lastSyncTime: Long = 0L,
    val libraryCounts: IntArray? = null, // [songs, artists, albums, playlists]
    /** distinct library songs with at least one playback event, null when the read failed. */
    val listenedSongs: Int? = null,
    val dbSizeBytes: Long? = null,
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
        val imagesCache = runCatching {
            val size = preferences.getEnum(coilDiskCacheMaxSizeKey, CoilDiskCacheMaxSize.`128MB`)
            val max = if (size == CoilDiskCacheMaxSize.Custom) {
                preferences.getInt(coilCustomDiskCacheKey, 128) * 1_000_000L
            } else size.bytes
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
        // Downloads pause flag (persisted by MyDownloadService in the "download_prefs" file)
        val downloadsPaused = runCatching {
            app.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)
                .getBoolean("downloads_paused_state", false)
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
            File(app.filesDir.resolve("logs"), "N-Zik_crash_log.txt")
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
                debugLogsEnabled && File(app.filesDir.resolve("logs"), "N-Zik_log.txt")
                    .let { it.exists() && it.length() > 0L }
            }.getOrDefault(false)
        val lastfmSessionAvailable =
            runCatching { LastFmActions.isSessionAvailable() }.getOrDefault(false)

        MaintenanceSnapshot(
            dedup = dedup,
            convergence = convergence,
            dbCleanup = dbCleanup,
            lastSyncTime = lastSyncTime,
            libraryCounts = libraryCounts,
            listenedSongs = listenedSongs,
            dbSizeBytes = dbSizeBytes,
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

/**
 * The Maintenance sheet (spec "Maintenance — état de l'app en un regard"): a bottom sheet
 * listing the last boot passes (persisted last successful run) and the live state of each
 * subsystem — the whole app state at a glance.
 *
 * Design (per user request): follows the app's own menu design system, exactly like the
 * Listen Together menu — LIST mode ([ListMenu.Menu] + [ListMenu.Entry] rows +
 * right-aligned colored status dot). The sheet is list-only: the grid mode was dropped
 * (this many rows are unreadable in grid cells). The [CustomModalBottomSheet] container
 * stays transparent with an empty drag handle, and the title + refresh header row sits
 * above the menu content.
 *
 * The sheet reads its data at open (and on the "refresh" action) only: no polling, no
 * background refresh; the refresh re-runs the READS, never the boot passes.
 *
 * Opened from two entry points that compose the same composable:
 * - the burger menu "Maintenance" item (short tap) — [app.n_zik.android.components.menu.header.MaintenanceMenuItem]
 * - the Misc settings "Maintenance" card ([app.n_zik.android.components.settings.MaintenanceSettingsCard])
 *
 * @param showSheet whether the sheet is open
 * @param onDismissRequest called when the sheet is dismissed (swipe / outside tap)
 * @param renderLogsDialog whether this composition renders the standard [CopyLogsDialog]
 * for the Export action. Both entry points pass `false`: the single Render() host for
 * the whole app lives in the persistent header (ActionBar.kt, composed for the entire
 * session), which both sheets share — Render() is a singleton and a second Render()
 * would stack a second dialog on top of the first.
 */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun MaintenanceSheet(
    showSheet: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    renderLogsDialog: Boolean = false,
) {
    val context = LocalContext.current
    val palette = colorPalette()

    var refreshTick by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<MaintenanceSnapshot?>(null) }
    var dedupExpanded by remember { mutableStateOf(false) }
    var convergenceExpanded by remember { mutableStateOf(false) }
    var dbCleanupExpanded by remember { mutableStateOf(false) }
    var jobsExpanded by remember { mutableStateOf(false) }
    var downloadsExpanded by remember { mutableStateOf(false) }

    // Live StateFlows observed with lifecycle awareness (spec: collectAsStateWithLifecycle,
    // never collectAsState). The sheet composable lives in the persistent header, so the
    // (passive, no polling) observers are registered for the whole session, not only while
    // the sheet is visible — the values simply update in place between opens. The rest of
    // the state is a snapshot loaded at open/refresh.
    val syncStatus by syncStatus.collectAsStateWithLifecycle()
    val downloads by MyDownloadHelper.downloads.collectAsStateWithLifecycle()
    val discordError by DiscordRpcErrorState.error.collectAsStateWithLifecycle()
    val updateState by UpdateDownloadManager.downloadState.collectAsStateWithLifecycle()

    // Read at open + on refresh only (disk I/O inside runs on NzikDispatchers.DATA).
    LaunchedEffect(showSheet, refreshTick) {
        if (!showSheet) return@LaunchedEffect
        Timber.tag(TAG).d("Loading maintenance state")
        snapshot = runCatching { loadMaintenanceSnapshot(context) }
            .onFailure { Timber.tag(TAG).w(it, "Failed to load the maintenance state") }
            .getOrNull()
    }

    // Cap the content at ~80% of the screen; the menu rows scroll inside.

    // One line per value: every row's single-line status (text + color) is resolved here,
    // in the composable body (stringResource is only callable from a composable context),
    // so the rows render the same resolved model.
    val snap = snapshot
    // Boot passes share one trailing format: the last run's date in front, the status after
    val dedup = snap?.dedup
    // "No data" (never ran / unreadable state) vs "no groups detected" (ran, found none):
    // the expanded detail must not claim a detection when there is no state to detect from
    val dedupNoData = dedup == null
    val dedupSummary = lastRunLine(
        dedup?.timestamp,
        passStatusText(dedupStatusRes(dedup)),
        stringResource(R.string.maintenance_no_data),
    )
    val dedupGroups = ArrayList<GroupLine>()
    if (dedupExpanded) {
        val groups = dedup?.records?.sortedBy { it.name.lowercase(Locale.ROOT) }.orEmpty()
        for (record in groups) {
            val display = dedupGroupDisplay(record)
            val detailArgs = display.detailArgs
            val detailArgs2 = display.detailArgs2
            val detailResId = display.detailResId
            val detail = when {
                detailResId == null -> ""
                detailArgs2 != null && detailArgs != null ->
                    ": ${stringResource(detailResId, detailArgs, detailArgs2)}"
                detailArgs != null -> ": ${stringResource(detailResId, detailArgs)}"
                else -> ": ${stringResource(detailResId)}"
            }
            dedupGroups.add(GroupLine(record.name, stringResource(display.statusResId) + detail))
        }
    }
    val convergence = snap?.convergence
    val convergenceStatus = lastRunLine(
        convergence?.timestamp,
        passStatusText(convergenceStatusRes(convergence)),
        stringResource(R.string.maintenance_no_data),
    )
    // Expanded convergence detail (the "N copies rewritten" row stays the main status)
    val convergenceDetails = convergenceDetailLines(convergence?.summary).map { line ->
        val count = line.count
        if (count != null) {
            ConvergenceDetail(stringResource(line.resId, count))
        } else {
            ConvergenceDetail(stringResource(line.resId))
        }
    }
    // The row carries the "date · status" line; the expansion lists the links removed by
    // the last run (one line per link, like the dedup groups)
    val dbCleanup = snap?.dbCleanup
    val dbCleanupStatus = lastRunLine(
        dbCleanup?.timestamp,
        passStatusText(dbCleanupStatusRes(dbCleanup)),
        stringResource(R.string.maintenance_no_data),
    )
    val dbCleanupLines = dbCleanupLines(dbCleanup)
    val dbCleanupEmpty = dbCleanupEmptyRes(dbCleanup)
    val dbCleanupEmptyArg = dbCleanupEmpty.second
    val dbCleanupEmptyText = if (dbCleanupEmptyArg != null) {
        stringResource(dbCleanupEmpty.first, dbCleanupEmptyArg)
    } else {
        stringResource(dbCleanupEmpty.first)
    }
    // Aligned with the accounts card: running keeps the sheet's own wording (the snapshot
    // has no operation name); idle reuses the card's resources in the muted color
    val syncRow = syncRow(syncStatus.isRunning, System.currentTimeMillis(), snap?.lastSyncTime ?: 0L)
    val syncStatusText = syncRow.arg?.let { stringResource(syncRow.resId, it) } ?: stringResource(syncRow.resId)
    val syncRowColor = palette.rowColor(syncRow.color)
    val counts = snap?.libraryCounts
    val listened = snap?.listenedSongs
    // The DB file size has its own row (content.dbSizeStatus); the library row carries
    // only the counts, the listened subset right after the songs
    val libraryStatus = buildString {
        val parts = ArrayList<String>()
        if (counts != null) {
            parts.add(stringResource(R.string.maintenance_library_songs, counts[0]))
            if (listened != null) parts.add(stringResource(R.string.maintenance_library_listened, listened))
            parts.add(stringResource(R.string.maintenance_library_artists, counts[1]))
            parts.add(stringResource(R.string.maintenance_library_albums, counts[2]))
            parts.add(stringResource(R.string.maintenance_library_playlists, counts[3]))
        }
        if (parts.isEmpty()) parts.add(stringResource(R.string.maintenance_no_data))
        append(parts.joinToString(" · "))
    }
    val dbSizeStatus = snap?.dbSizeBytes?.let { formatBytes(it) }
        ?: stringResource(R.string.maintenance_no_data)
    // Cache rows, aligned with the Data settings cache lines: the settings label is
    // the row title, the "<size> used (<pct>%)" result is the right status (the shared
    // status column); a disabled cache answers "Off", a missing size "No data"
    val imagesCache = snap?.imagesCache
    val imagesStatus = if (imagesCache != null) {
        cacheUsedStatusText(context, imagesCache.first, imagesCache.second)
    } else {
        stringResource(R.string.maintenance_no_data)
    }
    val downloadsCache = snap?.downloadsCache
    val downloadsDisabled = snap?.downloadsCacheDisabled == true
    val downloadsCacheStatus = when {
        downloadsDisabled -> stringResource(R.string.turn_off)
        downloadsCache != null -> cacheUsedStatusText(context, downloadsCache.first, downloadsCache.second)
        else -> stringResource(R.string.maintenance_no_data)
    }
    // Song cache (the streaming playback cache, the DataSettings pattern): the size
    // comes from the bound player service, the max from the song cache setting
    val songCacheMaxPref by rememberPreference(exoPlayerDiskCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`2GB`)
    val songCustomCacheMb by rememberPreference(exoPlayerCustomCacheKey, 32)
    val songCacheMax = if (songCacheMaxPref == ExoPlayerDiskCacheMaxSize.Custom) {
        songCustomCacheMb * 1_000L * 1_000L
    } else {
        songCacheMaxPref.bytes
    }
    val songCacheSize = LocalPlayerServiceBinder.current?.cache?.cacheSpace
    val songCacheStatus = when {
        songCacheMaxPref == ExoPlayerDiskCacheMaxSize.Disabled -> stringResource(R.string.turn_off)
        songCacheSize == null -> stringResource(R.string.maintenance_no_data)
        else -> cacheUsedStatusText(context, songCacheSize, songCacheMax)
    }
    // Downloads (live map; the pause flag comes from the persisted state)
    val downloadStates = downloads.values.map { it.state }
    val downloadsInProgress = downloadStates.count {
        it == Download.STATE_QUEUED || it == Download.STATE_DOWNLOADING || it == Download.STATE_RESTARTING
    }
    val downloadsCompleted = downloadStates.count { it == Download.STATE_COMPLETED }
    val downloadsFailed = downloadStates.count { it == Download.STATE_FAILED }
    val downloadsPaused = snap?.downloadsPaused == true
    val downloadsLiveStatus = when {
        downloads.isEmpty() -> stringResource(R.string.maintenance_downloads_none)
        downloadsInProgress > 0 || downloadsCompleted > 0 || downloadsFailed > 0 || downloadsPaused -> buildString {
            val parts = ArrayList<String>()
            if (downloadsInProgress > 0) parts.add(stringResource(R.string.maintenance_downloads_in_progress, downloadsInProgress))
            if (downloadsCompleted > 0) parts.add(stringResource(R.string.maintenance_downloads_completed, downloadsCompleted))
            if (downloadsFailed > 0) parts.add(stringResource(R.string.maintenance_downloads_failed, downloadsFailed))
            if (downloadsPaused) parts.add(stringResource(R.string.maintenance_downloads_paused))
            append(parts.joinToString(" · "))
        }
        else -> stringResource(R.string.maintenance_no_data)
    }
    val downloadsLiveColor = when {
        downloadsFailed > 0 -> palette.red
        downloadsPaused -> palette.blue
        downloadsInProgress > 0 -> palette.accent
        else -> palette.textSecondary
    }
    // Expanded downloads detail: one line per download (title + state), alphabetical —
    // failed in RED, waiting/in-progress in accent, paused in blue, completed neutral
    val downloadDetails = downloads.values.map { download ->
        val title = download.request.data.decodeToString()
        val line = downloadDetailLine(title, download.state)
        DownloadDetail(
            name = line.titleResId?.let { stringResource(it) } ?: (title.orEmpty()),
            stateLabel = stringResource(line.stateResId),
            color = palette.rowColor(line.color),
        )
    }.sortedBy { it.name.lowercase(Locale.ROOT) }
    val jobs = snap?.jobs
    val abnormal = jobs?.let { abnormalJobs(it) }.orEmpty()
    val anyJobRunning = jobs?.any { it.state == WorkInfo.State.RUNNING } == true
    val jobsStatus = when {
        jobs == null -> stringResource(R.string.maintenance_no_data)
        abnormal.isNotEmpty() -> stringResource(R.string.maintenance_jobs_abnormal, abnormal.size)
        else -> stringResource(R.string.maintenance_jobs_all_ok)
    }
    val jobsColor = when {
        abnormal.isNotEmpty() -> palette.red
        anyJobRunning -> palette.accent
        else -> palette.textSecondary
    }
    // Expanded jobs detail: ALL the jobs alphabetically; the abnormal ones in RED, the rest neutral
    val jobDetails = jobDetailLines(jobs).map { line ->
        val stateLabel = line.stateResId?.let { stringResource(it) }
        JobDetail(
            name = line.name,
            stateLabel = stateLabel,
            color = if (line.abnormal) palette.red else palette.textSecondary,
        )
    }
    val backupJob = snap?.backupJob
    val backupInterval = snap?.backupInterval ?: BackupManager.INTERVAL_NONE
    val backupStatus = buildString {
        val parts = ArrayList<String>()
        parts.add(
            stringResource(
                when (backupInterval) {
                    BackupManager.INTERVAL_HOURLY -> R.string.auto_backup_interval_hourly
                    BackupManager.INTERVAL_DAILY -> R.string.auto_backup_interval_daily
                    BackupManager.INTERVAL_WEEKLY -> R.string.auto_backup_interval_weekly
                    BackupManager.INTERVAL_MONTHLY -> R.string.auto_backup_interval_monthly
                    BackupManager.INTERVAL_CUSTOM -> R.string.auto_backup_interval_custom
                    else -> R.string.maintenance_backup_disabled
                },
            ),
        )
        val backupRunAt = backupJob?.runAtStartTime ?: 0L
        if (backupRunAt > 0L) parts.add(stringResource(R.string.maintenance_backup_next, formatTimestamp(backupRunAt)))
        val backupState = backupJob?.state
        if (backupState != null) parts.add(stringResource(R.string.maintenance_backup_last, stringResource(workStateResId(backupState))))
        append(parts.joinToString(" · "))
    }
    val backupColor = when {
        backupJob?.state == WorkInfo.State.FAILED -> palette.red
        backupJob?.state == WorkInfo.State.RUNNING -> palette.accent
        backupInterval == BackupManager.INTERVAL_NONE -> palette.blue
        else -> palette.textSecondary
    }
    val mbSeconds = snap?.mbRateLimitedSeconds ?: 0L
    // The app card shows the cooldown in minutes: round the seconds up to whole minutes
    val mbStatus = if (mbSeconds > 0L) {
        stringResource(R.string.maintenance_mb_rate_limited, mbCooldownMinutes(mbSeconds))
    } else {
        stringResource(R.string.maintenance_mb_ok)
    }
    val mbColor = if (mbSeconds > 0L) palette.red else palette.textSecondary
    val ytSession = snap?.ytSession
    val ytStatus = when (ytSession) {
        MainApplication.CookieStatus.NOT_LOGGED_IN -> stringResource(R.string.maintenance_not_connected)
        MainApplication.CookieStatus.VALID -> stringResource(R.string.maintenance_yt_valid)
        MainApplication.CookieStatus.INVALID -> stringResource(R.string.maintenance_yt_invalid)
        MainApplication.CookieStatus.EXPIRED -> stringResource(R.string.maintenance_yt_expired)
        null -> stringResource(R.string.maintenance_no_data)
    }
    val ytColor = when (ytSession) {
        MainApplication.CookieStatus.INVALID,
        MainApplication.CookieStatus.EXPIRED,
            -> palette.red
        else -> palette.textSecondary
    }
    val lt = snap?.listenTogether
    val ltStateResId = lt?.stateResId
    val ltStateLine = if (ltStateResId != null) {
        stringResource(ltStateResId)
    } else {
        stringResource(R.string.maintenance_not_connected)
    }
    val ltRoleResId = lt?.roleResId
    val ltRoleLine = if (ltRoleResId != null) {
        stringResource(ltRoleResId)
    } else {
        null
    }
    val ltExtras = listOfNotNull(ltRoleLine, lt?.roomCode)
    val ltStatus = if (ltExtras.isEmpty()) ltStateLine else "$ltStateLine · ${ltExtras.joinToString(" · ")}"
    // Aligned with the ConnectionStatusCard: connecting/reconnecting are softened,
    // the not-connected states are muted, connected keeps the full accent
    val ltColor = palette.rowColor(ltColorKey(ltStateResId))
    val discordTokenConfigured = snap?.discordTokenConfigured == true
    val discordStatus = when (discordError) {
        null -> if (discordTokenConfigured) {
            stringResource(R.string.maintenance_discord_ok)
        } else {
            stringResource(R.string.maintenance_not_connected)
        }
        DiscordRpcError.INVALID_TOKEN -> stringResource(R.string.maintenance_discord_invalid_token)
        DiscordRpcError.RECONNECT_FAILED -> stringResource(R.string.maintenance_discord_reconnect_failed)
    }
    val discordColor = if (discordError == null) palette.textSecondary else palette.red
    // The available version comes from the same live Updater source as the About update
    // card (no snapshot field needed); idle then maps to "Update available" (accent)
    // or "Up to date" (neutral), aligned with that card
    val newVersionAvailable = Updater.githubRelease?.let { release ->
        Updater.isVersionNewer(release.tagName, BuildConfig.VERSION_NAME)
    } ?: false
    val updateRow = updateRow(BuildConfig.IS_AUTOUPDATE, newVersionAvailable, updateState)
    val updateStatus = updateRow.arg?.let { stringResource(updateRow.resId, it) } ?: stringResource(updateRow.resId)
    val updateColor = palette.rowColor(updateRow.color)
    val batteryOk = snap?.batteryOptimizationsIgnored == true
    val batteryStatus = stringResource(if (batteryOk) R.string.maintenance_battery_ok else R.string.maintenance_battery_warn)
    val batteryColor = if (batteryOk) palette.textSecondary else palette.blue
    val crash = snap?.lastCrash
    // The row is always present: "None" without a crash, "A crash occurred (N)" with one
    // (N = the number of complete crash blocks in the log — the details live in the
    // View dialog, not in the one-line status)
    val crashStatus = if (crash == null) {
        stringResource(R.string.maintenance_crash_none)
    } else {
        stringResource(R.string.maintenance_crash_occurred_count, snap?.crashCount ?: 0)
    }
    val crashColor = if (crash == null) palette.textSecondary else palette.red
    // The View + Export buttons show only when a crash block exists (no empty slot)
    val crashViewVisible = crash != null
    val debugRow = debugLogsRow(
        snap?.debugLogsEnabled == true,
        snap?.debugLogFileAvailable == true,
    )
    val debugStatus = if (debugRow.restartNeeded) {
        // "On · restart needed": the log file only appears at the next startup
        "${stringResource(R.string.maintenance_debug_on)} · ${stringResource(R.string.maintenance_debug_restart_needed)}"
    } else {
        stringResource(debugRow.baseResId)
    }
    val lastfmAvailable = snap?.lastfmSessionAvailable == true
    // "Not connected" is neutral: red is reserved for genuinely broken states
    val lastfmStatus = if (lastfmAvailable) {
        stringResource(R.string.maintenance_lastfm_active)
    } else {
        stringResource(R.string.maintenance_not_connected)
    }
    val lastfmColor = palette.textSecondary

    val content = MaintenanceMenuContent(
        dedupSummary = dedupSummary,
        dedupNoData = dedupNoData,
        dedupGroups = dedupGroups,
        convergenceStatus = convergenceStatus,
        convergenceColor = palette.textSecondary,
        convergenceDetails = convergenceDetails,
        dbCleanupStatus = dbCleanupStatus,
        dbCleanupColor = palette.textSecondary,
        dbCleanupLines = dbCleanupLines,
        dbCleanupEmptyText = dbCleanupEmptyText,
        syncStatus = syncStatusText,
        syncColor = syncRowColor,
        libraryStatus = libraryStatus,
        dbSizeStatus = dbSizeStatus,
        imagesStatus = imagesStatus,
        songCacheStatus = songCacheStatus,
        downloadsCacheStatus = downloadsCacheStatus,
        downloadsLiveStatus = downloadsLiveStatus,
        downloadsLiveColor = downloadsLiveColor,
        downloadDetails = downloadDetails,
        jobsStatus = jobsStatus,
        jobsColor = jobsColor,
        jobDetails = jobDetails,
        backupStatus = backupStatus,
        backupColor = backupColor,
        mbStatus = mbStatus,
        mbColor = mbColor,
        ytStatus = ytStatus,
        ytColor = ytColor,
        ltStatus = ltStatus,
        ltColor = ltColor,
        discordStatus = discordStatus,
        discordColor = discordColor,
        updateStatus = updateStatus,
        updateColor = updateColor,
        batteryStatus = batteryStatus,
        batteryColor = batteryColor,
        crashStatus = crashStatus,
        crashColor = crashColor,
        crashViewVisible = crashViewVisible,
        debugStatus = debugStatus,
        debugButtonsVisible = snap?.debugLogsEnabled == true,
        onViewDebug = { DebugLogDialog.showDialog() },
        lastfmStatus = lastfmStatus,
        lastfmColor = lastfmColor,
        onExportLogs = { CopyLogsDialog.showDialog() },
        onViewCrash = { CrashLogDialog.showDialog() },
        // The export dialog opens pre-selected on the crash option (the row's context)
        onExportCrash = { CopyLogsDialog.showDialogFor(1) },
    )

    // Opens and grows exactly like the main menu sheet (SongItemMenu — MainActivity):
    // transparent container, top-only shape, statusBarsPadding,
    // skipPartiallyExpanded = false (opens at its content height, draggable to full
    // screen) — the menu draws its own background (background0), its own top clip and
    // its own drag handle
    CustomModalBottomSheet(
        showSheet = showSheet,
        onDismissRequest = onDismissRequest,
        modifier = modifier.statusBarsPadding(),
        containerColor = Color.Transparent,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        shape = (uiRoundnessShape() as? RoundedCornerShape)?.let {
            RoundedCornerShape(
                topStart = it.topStart,
                topEnd = it.topEnd,
                bottomStart = CornerSize(0.dp),
                bottomEnd = CornerSize(0.dp),
            )
        } ?: uiRoundnessShape(),
        dragHandle = {}
    ) {
        // Standard menu header (title + refresh in the header trailing slot), exactly
        // like the Listen Together menu; refresh re-runs the reads (never the boot
        // passes)
        val refreshTrailing: (@Composable RowScope.() -> Unit)? = {
            IconButton(onClick = { refreshTick++ }) {
                Icon(
                    painter = painterResource(R.drawable.refresh),
                    contentDescription = stringResource(R.string.maintenance_refresh),
                    tint = palette.textSecondary,
                )
            }
        }
        // No height cap on the content — exactly like the main menu sheet (SongItemMenu):
        // with skipPartiallyExpanded = false the sheet opens at its content height and
        // is draggable to full screen. No background here — the menu draws its own.
        ListMenu.Menu(
            title = stringResource(R.string.maintenance),
            headerTrailing = refreshTrailing,
        ) {
            MaintenanceListContent(
                content = content,
                dedupExpanded = dedupExpanded,
                onToggleDedup = { dedupExpanded = !dedupExpanded },
                convergenceExpanded = convergenceExpanded,
                onToggleConvergence = { convergenceExpanded = !convergenceExpanded },
                dbCleanupExpanded = dbCleanupExpanded,
                onToggleDbCleanup = { dbCleanupExpanded = !dbCleanupExpanded },
                jobsExpanded = jobsExpanded,
                onToggleJobs = { jobsExpanded = !jobsExpanded },
                downloadsExpanded = downloadsExpanded,
                onToggleDownloads = { downloadsExpanded = !downloadsExpanded },
            )
        }
        // Both entry points pass renderLogsDialog = false: the single Render() host for
        // the whole app lives in the persistent header (ActionBar.kt) — Render() is a
        // singleton, so a second one anywhere would stack two dialogs on the same
        // showDialog() activation. This branch stays only as a safety valve.
        if (renderLogsDialog) {
            CopyLogsDialog.Render()
        }
    }
}

/**
 * One-line-per-value display model of the Maintenance menu rows, resolved in the sheet
 * body (composable context): each row carries its single-line status and its status
 * color (the LT menu's shared content pattern).
 */
private class MaintenanceMenuContent(
    // Boot passes
    val dedupSummary: String,
    val dedupNoData: Boolean,
    val dedupGroups: List<GroupLine>,
    val convergenceStatus: String,
    val convergenceColor: Color,
    val convergenceDetails: List<ConvergenceDetail>,
    val dbCleanupStatus: String,
    val dbCleanupColor: Color,
    val dbCleanupLines: List<GroupLine>,
    val dbCleanupEmptyText: String,
    // Subsystems
    val syncStatus: String,
    val syncColor: Color,
    val libraryStatus: String,
    val dbSizeStatus: String,
    val imagesStatus: String,
    val songCacheStatus: String,
    val downloadsCacheStatus: String,
    val downloadsLiveStatus: String,
    val downloadsLiveColor: Color,
    val downloadDetails: List<DownloadDetail>,
    val jobsStatus: String,
    val jobsColor: Color,
    val jobDetails: List<JobDetail>,
    val backupStatus: String,
    val backupColor: Color,
    val mbStatus: String,
    val mbColor: Color,
    val ytStatus: String,
    val ytColor: Color,
    val ltStatus: String,
    val ltColor: Color,
    val discordStatus: String,
    val discordColor: Color,
    val updateStatus: String,
    val updateColor: Color,
    val batteryStatus: String,
    val batteryColor: Color,
    val crashStatus: String,
    val crashColor: Color,
    val crashViewVisible: Boolean,
    val onViewCrash: () -> Unit,
    val onExportCrash: () -> Unit,
    val debugStatus: String,
    // The debug row's View + Export buttons show only while the debug switch is ON
    // (off → no buttons; on but not restarted → the dialogs answer "Log unavailable")
    val debugButtonsVisible: Boolean,
    val onViewDebug: () -> Unit,
    val lastfmStatus: String,
    val lastfmColor: Color,
    val onExportLogs: () -> Unit,
)

/**
 * One expanded detail row of a chevron line (name + status): shared by the dedup
 * group list and the artist-link-cleanup removed-link list.
 */
internal data class GroupLine(
    val name: String,
    val status: String,
)

/** One expanded convergence detail line (the resolved text). */
private data class ConvergenceDetail(
    val text: String,
)

/** One expanded jobs detail line (name + state label, the abnormal ones colored RED). */
private data class JobDetail(
    val name: String,
    val stateLabel: String?,
    val color: Color,
)

/** One expanded downloads detail line (title + state label, the abnormal ones colored). */
private data class DownloadDetail(
    val name: String,
    val stateLabel: String,
    val color: Color,
)

/**
 * List rendering: [ListMenu.Menu] + accent section titles + [ListMenu.Entry] rows, each
 * with its 32dp icon chip and its right-aligned colored status (dot + text, the LT
 * ConnectionStatusRow pattern).
 */
@Composable
private fun ColumnScope.MaintenanceListContent(
    content: MaintenanceMenuContent,
    dedupExpanded: Boolean,
    onToggleDedup: () -> Unit,
    convergenceExpanded: Boolean,
    onToggleConvergence: () -> Unit,
    dbCleanupExpanded: Boolean,
    onToggleDbCleanup: () -> Unit,
    jobsExpanded: Boolean,
    onToggleJobs: () -> Unit,
    downloadsExpanded: Boolean,
    onToggleDownloads: () -> Unit,
) {
    val palette = colorPalette()
    val isScrollingTextDisabled by rememberPreference(disableScrollingTextKey, false)
    // One shared status column: the rendered width of the longest status that must
    // stay fully visible — "Not connected" (the longest short status) or a full-width
    // cache result ("9.3 GB used (100%)" — the widest realistic "used (pct%)" line).
    // Every status starts at the same x; anything longer scrolls in a marquee
    val statusColumnWidth = maxOf(
        clampedLabelMaxWidth(stringResource(R.string.maintenance_not_connected), typography().xs),
        clampedLabelMaxWidth(CACHE_STATUS_WIDTH_REF, typography().xs),
    )

    MenuSectionTitle(stringResource(R.string.maintenance_section_database))

    // Dedup: expandable row (chevron trailing, groups sorted alphabetically underneath)
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_dedup),
        icon = { SettingIcon(R.drawable.trash, palette.accent) },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusTrailing(content.dedupSummary, palette.textSecondary, statusColumnWidth)
                Icon(
                    painter = painterResource(if (dedupExpanded) R.drawable.chevron_up else R.drawable.chevron_down),
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        onClick = onToggleDedup,
    )
    // Animated expansion (the settings-card specs): enter + exit motion, the detail
    // lines indented to the parent row's label
    AnimatedVisibility(
        visible = dedupExpanded,
        enter = settingsEntryEnter,
        exit = settingsEntryExit,
    ) {
        if (content.dedupGroups.isEmpty()) {
            Text(
                // No state at all (never ran / unreadable) answers "No data"; a readable
                // state without groups answers "No groups detected"
                text = stringResource(
                    if (content.dedupNoData) R.string.maintenance_no_data
                    else R.string.maintenance_dedup_no_groups
                ),
                style = typography().xs,
                color = palette.textSecondary,
                modifier = Modifier.fillMaxWidth()
                    .padding(start = detailListIndent)
                    .padding(vertical = 6.dp),
            )
        } else {
            Column {
                for (line in content.dedupGroups) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = detailListIndent)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = line.name,
                            style = typography().xs,
                            color = palette.text,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = line.status,
                            style = typography().xs,
                            color = palette.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
    // Convergence: expandable row (chevron trailing, the non-zero sweep counters underneath)
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_convergence),
        icon = { SettingIcon(R.drawable.sync, palette.accent) },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusTrailing(content.convergenceStatus, content.convergenceColor, statusColumnWidth)
                Icon(
                    painter = painterResource(if (convergenceExpanded) R.drawable.chevron_up else R.drawable.chevron_down),
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        onClick = onToggleConvergence,
    )
    AnimatedVisibility(
        visible = convergenceExpanded,
        enter = settingsEntryEnter,
        exit = settingsEntryExit,
    ) {
        Column {
            for (line in content.convergenceDetails) {
                Text(
                    text = line.text,
                    style = typography().xs,
                    color = palette.textSecondary,
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = detailListIndent)
                        .padding(vertical = 2.dp),
                )
            }
        }
    }
    // Artist link cleanup: expandable row (chevron trailing, the removed links listed
    // underneath — same "summary on top, detail list underneath" shape as dedup and
    // convergence, so the three chevron rows open/close consistently)
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_db_cleanup),
        icon = { SettingIcon(R.drawable.data_migration, palette.accent) },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusTrailing(content.dbCleanupStatus, content.dbCleanupColor, statusColumnWidth)
                Icon(
                    painter = painterResource(if (dbCleanupExpanded) R.drawable.chevron_up else R.drawable.chevron_down),
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        onClick = onToggleDbCleanup,
    )
    AnimatedVisibility(
        visible = dbCleanupExpanded,
        enter = settingsEntryEnter,
        exit = settingsEntryExit,
    ) {
        if (content.dbCleanupLines.isEmpty()) {
            Text(
                text = content.dbCleanupEmptyText,
                style = typography().xs,
                color = palette.textSecondary,
                modifier = Modifier.fillMaxWidth()
                    .padding(start = detailListIndent)
                    .padding(vertical = 6.dp),
            )
        } else {
            Column {
                for (line in content.dbCleanupLines) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = detailListIndent)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = line.name,
                            style = typography().xs,
                            color = palette.text,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = line.status,
                            style = typography().xs,
                            color = palette.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
    MenuSectionTitle(stringResource(R.string.maintenance_section_library_sync))
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_sync_ytm),
        icon = { SettingIcon(R.drawable.sync, palette.accent) },
        trailingContent = { StatusTrailing(content.syncStatus, content.syncColor, statusColumnWidth) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_library),
        icon = { SettingIcon(R.drawable.library, palette.accent) },
        trailingContent = { StatusTrailing(content.libraryStatus, palette.textSecondary, statusColumnWidth) },
    )
    // DB file size: its own row (separated from the library counts)
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_db_size),
        icon = { SettingIcon(R.drawable.data_migration, palette.accent) },
        trailingContent = { StatusTrailing(content.dbSizeStatus, palette.textSecondary, statusColumnWidth) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_musicbrainz),
        icon = { SettingIcon(R.drawable.globe, palette.accent) },
        trailingContent = { StatusTrailing(content.mbStatus, content.mbColor, statusColumnWidth) },
    )

    MenuSectionTitle(stringResource(R.string.maintenance_section_caches_downloads))
    // Cache rows (the Data settings order): image, song, song download — each shows
    // "<size> used (<pct>%)" in the shared status column
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_image_cache),
        icon = { SettingIcon(R.drawable.image, palette.accent) },
        trailingContent = { StatusTrailing(content.imagesStatus, palette.textSecondary, statusColumnWidth) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_song_cache),
        icon = { SettingIcon(R.drawable.music_file, palette.accent) },
        trailingContent = { StatusTrailing(content.songCacheStatus, palette.textSecondary, statusColumnWidth) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_song_download_cache),
        icon = { SettingIcon(R.drawable.download, palette.accent) },
        trailingContent = { StatusTrailing(content.downloadsCacheStatus, palette.textSecondary, statusColumnWidth) },
    )
    // Downloads: expandable row (chevron trailing, one line per download underneath)
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_downloads),
        icon = { SettingIcon(R.drawable.download, palette.accent) },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusTrailing(content.downloadsLiveStatus, content.downloadsLiveColor, statusColumnWidth)
                Icon(
                    painter = painterResource(if (downloadsExpanded) R.drawable.chevron_up else R.drawable.chevron_down),
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        onClick = onToggleDownloads,
    )
    AnimatedVisibility(
        visible = downloadsExpanded,
        enter = settingsEntryEnter,
        exit = settingsEntryExit,
    ) {
        if (content.downloadDetails.isEmpty()) {
            Text(
                text = stringResource(R.string.maintenance_downloads_none),
                style = typography().xs,
                color = palette.textSecondary,
                modifier = Modifier.fillMaxWidth()
                    .padding(start = detailListIndent)
                    .padding(vertical = 6.dp),
            )
        } else {
            Column {
                for (line in content.downloadDetails) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = detailListIndent)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = line.name,
                            style = typography().xs,
                            color = line.color,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = line.stateLabel,
                            style = typography().xs,
                            color = line.color,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    MenuSectionTitle(stringResource(R.string.maintenance_section_jobs))
    // Jobs: expandable row (chevron trailing, ALL the jobs underneath — abnormal in RED)
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_jobs),
        icon = { SettingIcon(R.drawable.calendar, palette.accent) },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusTrailing(content.jobsStatus, content.jobsColor, statusColumnWidth)
                Icon(
                    painter = painterResource(if (jobsExpanded) R.drawable.chevron_up else R.drawable.chevron_down),
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        onClick = onToggleJobs,
    )
    AnimatedVisibility(
        visible = jobsExpanded,
        enter = settingsEntryEnter,
        exit = settingsEntryExit,
    ) {
        if (content.jobDetails.isEmpty()) {
            Text(
                text = stringResource(R.string.maintenance_jobs_none),
                style = typography().xs,
                color = palette.textSecondary,
                modifier = Modifier.fillMaxWidth()
                    .padding(start = detailListIndent)
                    .padding(vertical = 6.dp),
            )
        } else {
            Column {
                for (line in content.jobDetails) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = detailListIndent)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = line.name,
                            style = typography().xs,
                            color = line.color,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        line.stateLabel?.let {
                            Text(
                                text = it,
                                style = typography().xs,
                                color = line.color,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    MenuSectionTitle(stringResource(R.string.maintenance_section_services))
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_yt_session),
        icon = { SettingIcon(R.drawable.logo_youtube, palette.accent) },
        trailingContent = { StatusTrailing(content.ytStatus, content.ytColor, statusColumnWidth) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_listen_together),
        icon = { SettingIcon(R.drawable.people, palette.accent) },
        trailingContent = { StatusTrailing(content.ltStatus, content.ltColor, statusColumnWidth) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_discord),
        icon = { SettingIcon(R.drawable.logo_discord, palette.accent) },
        trailingContent = { StatusTrailing(content.discordStatus, content.discordColor, statusColumnWidth) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_lastfm),
        icon = { SettingIcon(R.drawable.logo_lastfm, palette.accent) },
        trailingContent = { StatusTrailing(content.lastfmStatus, content.lastfmColor, statusColumnWidth) },
    )

    MenuSectionTitle(stringResource(R.string.maintenance_section_system))
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_backup),
        icon = { SettingIcon(R.drawable.backup, palette.accent) },
        trailingContent = { StatusTrailing(content.backupStatus, content.backupColor, statusColumnWidth) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_auto_update),
        icon = { SettingIcon(R.drawable.update, palette.accent) },
        trailingContent = { StatusTrailing(content.updateStatus, content.updateColor, statusColumnWidth) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_battery),
        icon = { SettingIcon(R.drawable.battery_opti, palette.accent) },
        trailingContent = { StatusTrailing(content.batteryStatus, content.batteryColor, statusColumnWidth) },
    )
    // Last crash: View + Export live in the entry's trailing slot (the debug-logs
    // pattern) — View opens the crash log dialog (full content + copy + export +
    // clear), Export the shared export dialog pre-selected on the crash option; both
    // show only when a crash block exists (no empty slot otherwise)
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_last_crash),
        icon = { SettingIcon(R.drawable.bugs, palette.accent) },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusTrailing(content.crashStatus, content.crashColor, statusColumnWidth)
                if (content.crashViewVisible) {
                    TextButton(onClick = content.onViewCrash) {
                        Text(
                            text = stringResource(R.string.maintenance_log_view),
                            color = palette.accent,
                        )
                    }
                    TextButton(onClick = content.onExportCrash) {
                        Text(
                            text = stringResource(R.string.export_logs),
                            color = palette.accent,
                        )
                    }
                }
            }
        },
    )
    // Debug logs: View + Export live in the entry's trailing slot (the LT pattern) —
    // shown only while the debug switch is ON
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_debug_logs),
        icon = { SettingIcon(R.drawable.export_outline, palette.accent) },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Status dot like every other row (the status here is always neutral)
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(palette.textSecondary),
                )
                Text(
                    text = content.debugStatus,
                    style = typography().xs,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .width(statusColumnWidth)
                        .conditional(!isScrollingTextDisabled) {
                            basicMarquee(iterations = Int.MAX_VALUE)
                        },
                )
                // View + Export show only while the debug switch is ON (off → no
                // buttons at all; on but not restarted → the dialogs answer "Log
                // unavailable" until the restart creates the log file)
                if (content.debugButtonsVisible) {
                    TextButton(onClick = content.onViewDebug) {
                        Text(
                            text = stringResource(R.string.maintenance_log_view),
                            color = palette.accent,
                        )
                    }
                    TextButton(onClick = content.onExportLogs) {
                        Text(
                            text = stringResource(R.string.export_logs),
                            color = palette.accent,
                        )
                    }
                }
            }
        },
    )
}

/**
 * Accent section title, same styling as the app's own item menus: xxs semiBold accent
 * text with 12dp vertical / 4dp horizontal padding.
 */
@Composable
private fun MenuSectionTitle(title: String) {
    BasicText(
        text = title,
        style = typography().xxs.semiBold.copy(
            color = colorPalette().accent,
            textAlign = TextAlign.Start,
        ),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

/**
 * Right-aligned status of a list row: the status dot + the colored status text (the LT
 * ConnectionStatusRow pattern — the dot and the text share the status color).
 *
 * The text box is the shared status column width (sized for the longest status that must
 * stay fully visible — "Not connected" or a full-width cache result): every row's status
 * (dot included) starts at the same x, and a longer status (last crash, library counts)
 * scrolls within the column, honoring the scrolling-text setting.
 */
@Composable
private fun StatusTrailing(status: String, color: Color, columnWidth: Dp) {
    val isScrollingTextDisabled by rememberPreference(disableScrollingTextKey, false)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Text(
            text = status,
            style = typography().xs,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(columnWidth)
                .conditional(!isScrollingTextDisabled) {
                    basicMarquee(iterations = Int.MAX_VALUE)
                },
        )
    }
}

/**
 * Standard menu icon chip (same pattern as the app's own menus): tinted rounded 32dp
 * square with an 18dp icon.
 */
@Composable
private fun SettingIcon(@DrawableRes icon: Int, color: Color) {
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .background(color.copy(alpha = 0.1f), uiRoundnessShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
    }
}
