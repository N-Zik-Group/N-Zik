package app.n_zik.android.components.maintenance

import java.util.Locale

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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.work.WorkInfo
import kotlinx.coroutines.CancellationException
import timber.log.Timber

import app.n_zik.android.BuildConfig
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.logs.CopyLogsDialog
import app.n_zik.android.components.dialog.logs.CrashLogDialog
import app.n_zik.android.components.dialog.logs.DebugLogDialog
import app.n_zik.android.components.glass.SampledMenuSheet
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.menuSectionHeading
import app.n_zik.android.components.settings.common.settingsEntryEnter
import app.n_zik.android.components.settings.common.settingsEntryExit
import app.n_zik.android.components.ui.UiIconButton as IconButton
import app.n_zik.android.components.ui.UiTextButton as TextButton
import app.n_zik.android.components.ui.header.clampedLabelMaxWidth
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.core.backup.BackupManager
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.download.utils.downloadDisplayName
import app.n_zik.android.enums.ExoPlayerDiskCacheMaxSize
import app.n_zik.android.extensions.discord.DiscordRpcError
import app.n_zik.android.extensions.discord.DiscordRpcErrorState
import app.n_zik.android.typography
import app.n_zik.android.ui.components.themed.ItemTextPlaceholder
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.updater.services.UpdateDownloadManager
import app.n_zik.android.updater.services.Updater
import app.n_zik.android.utils.conditional
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.exoPlayerCustomCacheKey
import app.n_zik.android.utils.exoPlayerDiskCacheMaxSizeKey
import app.n_zik.android.utils.percentLabel
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.shimmerEffect
import app.n_zik.android.utils.syncStatus

private const val TAG = "MaintenanceSheet"

/** List detail-line indent: the ListMenu.Entry 32dp icon chip + its 12dp spacing. */
internal val detailListIndent = 44.dp

/** The widest realistic cache result line ("9.3 GB used (100%)" - the largest custom cache max with a full usage): the second reference of the shared status column width. */
private const val CACHE_STATUS_WIDTH_REF = "9.3 GB used (100%)"

@Composable
internal fun formatBytes(bytes: Long): String =
    Formatter.formatShortFileSize(LocalContext.current, bytes)

/** The cache result line, aligned with the Data settings cache rows: the short file size followed by " used (<pct>%)" - a zero max (Unlimited) answers " used (Unlimited)" instead of a percentage. */
@Composable
private fun cacheUsedStatusText(context: Context, usedBytes: Long, maxBytes: Long): String {
    val used = Formatter.formatShortFileSize(context, usedBytes)
    val limit = if (maxBytes > 0L) {
        percentLabel((usedBytes * 100 / maxBytes).toInt())
    } else {
        stringResource(R.string.common_limit_unlimited_label)
    }
    return stringResource(
        R.string.maintenance_cache_usage_status_format,
        used,
        stringResource(R.string.common_storage_used_suffix),
        limit,
    )
}

/**
 * Shows boot passes and subsystem statuses from the header and Settings.
 * @param renderLogsDialog Render the export dialog here when there is no persistent header.
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
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val palette = colorPalette()

    var refreshTick by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<MaintenanceSnapshot?>(null) }
    var snapshotReadFailed by remember { mutableStateOf(false) }
    var dedupExpanded by remember { mutableStateOf(false) }
    var convergenceExpanded by remember { mutableStateOf(false) }
    var dbCleanupExpanded by remember { mutableStateOf(false) }
    var downloadRevalidationExpanded by remember { mutableStateOf(false) }
    var downloadSelfHealExpanded by remember { mutableStateOf(false) }
    var jobsExpanded by remember { mutableStateOf(false) }
    var downloadsExpanded by remember { mutableStateOf(false) }

    // The persistent header keeps these live observations active between sheet opens.
    val syncStatus by syncStatus.collectAsStateWithLifecycle()
    // Only downloads from this process session belong in the maintenance row.
    val downloads by MyDownloadHelper.sessionDownloads.collectAsStateWithLifecycle()
    val discordError by DiscordRpcErrorState.error.collectAsStateWithLifecycle()
    val updateState by UpdateDownloadManager.downloadState.collectAsStateWithLifecycle()

    // Read at open + on refresh only (disk I/O inside runs on NzikDispatchers.DATA).
    LaunchedEffect(showSheet, refreshTick) {
        if (!showSheet) return@LaunchedEffect
        Timber.tag(TAG).d("Loading maintenance state")
        snapshotReadFailed = false
        try {
            snapshot = loadMaintenanceSnapshot(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Failed to load the maintenance state")
            snapshotReadFailed = true
        }
    }

    val snap = snapshot
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
    // The active profile's settings size, the profile sheet's Settings row value
    val settingsStatus = snap?.settingsSizeBytes?.let { formatBytes(it) }
        ?: stringResource(R.string.maintenance_no_data)
    // Cache results use the same format as Data settings.
    val imagesCache = snap?.imagesCache
    val imagesStatus = if (imagesCache != null) {
        cacheUsedStatusText(context, imagesCache.first, imagesCache.second)
    } else {
        stringResource(R.string.maintenance_no_data)
    }
    val downloadsCache = snap?.downloadsCache
    val downloadsDisabled = snap?.downloadsCacheDisabled == true
    val downloadsCacheStatus = when {
        downloadsDisabled -> stringResource(R.string.common_action_turn_off)
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
        songCacheMaxPref == ExoPlayerDiskCacheMaxSize.Disabled -> stringResource(R.string.common_action_turn_off)
        songCacheSize == null -> stringResource(R.string.maintenance_no_data)
        else -> cacheUsedStatusText(context, songCacheSize, songCacheMax)
    }
    // Downloads (session map - empty at process start; the pause flag comes from the persisted state)
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
    // Expanded downloads detail: one line per download (title + state), alphabetical -
    // failed in RED, waiting/in-progress in accent, paused in blue, completed neutral
    val downloadDetails = downloads.values.map { download ->
        val title = downloadDisplayName(download.request.data.decodeToString())
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
        abnormal.isNotEmpty() -> pluralStringResource(R.plurals.maintenance_abnormal_job_count, abnormal.size, abnormal.size)
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
                    BackupManager.INTERVAL_HOURLY -> R.string.backup_auto_interval_hourly
                    BackupManager.INTERVAL_DAILY -> R.string.backup_auto_interval_daily
                    BackupManager.INTERVAL_WEEKLY -> R.string.backup_auto_interval_weekly
                    BackupManager.INTERVAL_MONTHLY -> R.string.backup_auto_interval_monthly
                    BackupManager.INTERVAL_CUSTOM -> R.string.backup_auto_interval_custom
                    else -> R.string.maintenance_backup_disabled
                },
            ),
        )
        val backupRunAt = backupJob?.runAtStartTime ?: 0L
        if (backupRunAt > 0L) parts.add(stringResource(R.string.maintenance_backup_next, formatTimestamp(backupRunAt, locale)))
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
    // Use the same live release information as the About update card.
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
    // Only complete crash blocks contribute to the count.
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
        readPhase = when {
            snap != null -> MaintenanceReadPhase.Ready
            snapshotReadFailed -> MaintenanceReadPhase.Unavailable
            else -> MaintenanceReadPhase.Pending
        },
        syncStatus = syncStatusText,
        syncColor = syncRowColor,
        libraryStatus = libraryStatus,
        dbSizeStatus = dbSizeStatus,
        settingsStatus = settingsStatus,
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

    // The menu handles its own background and opens partially expanded.
    SampledMenuSheet(
        showSheet = showSheet,
        onDismissRequest = onDismissRequest,
        modifier = modifier.statusBarsPadding(),
        containerColor = Color.Transparent,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        partiallyExpanded = true,
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
        // Refresh rereads the snapshot without rerunning the boot passes.
        val refreshTrailing: (@Composable RowScope.() -> Unit)? = {
            IconButton(onClick = { refreshTick++ }) {
                Icon(
                    painter = painterResource(R.drawable.refresh),
                    contentDescription = stringResource(R.string.maintenance_refresh),
                    tint = palette.textSecondary,
                )
            }
        }
        ListMenu.Menu(
            title = stringResource(R.string.maintenance_title),
            headerTrailing = refreshTrailing,
        ) {
            if (snapshotReadFailed) {
                ListMenu.Entry(
                    text = stringResource(R.string.common_load_failed),
                    subtitle = stringResource(R.string.common_action_retry),
                    icon = { SettingIcon(R.drawable.refresh, palette.accent) },
                    onClick = { refreshTick++ },
                )
            }
            MaintenanceListContent(
                content = content,
                databaseSection = { statusColumnWidth ->
                    MaintenanceDatabaseSection(
                        snapshot = snap,
                        readPhase = content.readPhase,
                        statusColumnWidth = statusColumnWidth,
                        locale = locale,
                        sheetStatusColor = palette.textSecondary,
                        dedupExpanded = dedupExpanded,
                        onToggleDedup = { dedupExpanded = !dedupExpanded },
                        convergenceExpanded = convergenceExpanded,
                        onToggleConvergence = { convergenceExpanded = !convergenceExpanded },
                        dbCleanupExpanded = dbCleanupExpanded,
                        onToggleDbCleanup = { dbCleanupExpanded = !dbCleanupExpanded },
                        downloadRevalidationExpanded = downloadRevalidationExpanded,
                        onToggleDownloadRevalidation = { downloadRevalidationExpanded = !downloadRevalidationExpanded },
                        downloadSelfHealExpanded = downloadSelfHealExpanded,
                        onToggleDownloadSelfHeal = { downloadSelfHealExpanded = !downloadSelfHealExpanded },
                    )
                },
                jobsExpanded = jobsExpanded,
                onToggleJobs = { jobsExpanded = !jobsExpanded },
                downloadsExpanded = downloadsExpanded,
                onToggleDownloads = { downloadsExpanded = !downloadsExpanded },
            )
        }
        // The persistent header normally renders the singleton export dialog.
        if (renderLogsDialog) {
            CopyLogsDialog.Render()
        }
    }
}

internal enum class MaintenanceReadPhase { Ready, Pending, Unavailable }

/** One-line-per-value display model of the Maintenance menu rows, resolved in the sheet body (composable context): each row carries its single-line status and its status color (the LT menu's shared content pattern). */
private class MaintenanceMenuContent(
    val readPhase: MaintenanceReadPhase,
    val syncStatus: String,
    val syncColor: Color,
    val libraryStatus: String,
    val dbSizeStatus: String,
    val settingsStatus: String,
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

/** List rendering: [ListMenu.Menu] + accent section titles + [ListMenu.Entry] rows, each with its 32dp icon chip and its right-aligned colored status (dot + text, the LT ConnectionStatusRow pattern). */
@Composable
private fun ColumnScope.MaintenanceListContent(
    content: MaintenanceMenuContent,
    databaseSection: @Composable ColumnScope.(statusColumnWidth: Dp) -> Unit,
    jobsExpanded: Boolean,
    onToggleJobs: () -> Unit,
    downloadsExpanded: Boolean,
    onToggleDownloads: () -> Unit,
) {
    val palette = colorPalette()
    val isScrollingTextDisabled by rememberPreference(disableScrollingTextKey, false)
    // Size the shared column for the longest short status or a full cache result.
    val statusColumnWidth = maxOf(
        clampedLabelMaxWidth(stringResource(R.string.maintenance_not_connected), typography().xs),
        clampedLabelMaxWidth(CACHE_STATUS_WIDTH_REF, typography().xs),
    )

    databaseSection(statusColumnWidth)

    MenuSectionTitle(stringResource(R.string.maintenance_section_library_sync))
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_sync_ytm),
        icon = { SettingIcon(R.drawable.sync, palette.accent) },
        trailingContent = { StatusTrailing(content.syncStatus, content.syncColor, statusColumnWidth, if (content.syncColor == palette.accent) MaintenanceReadPhase.Ready else content.readPhase) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_library),
        icon = { SettingIcon(R.drawable.library, palette.accent) },
        trailingContent = { StatusTrailing(content.libraryStatus, palette.textSecondary, statusColumnWidth, content.readPhase) },
    )
    // Active profile's settings size (the profile sheet's Settings row source, labeled
    // "Settings size" like the "Database size" row)
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_settings_size),
        icon = { SettingIcon(R.drawable.settings, palette.accent) },
        trailingContent = { StatusTrailing(content.settingsStatus, palette.textSecondary, statusColumnWidth, content.readPhase) },
    )
    // DB file size: its own row (separated from the library counts)
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_db_size),
        icon = { SettingIcon(R.drawable.data_migration, palette.accent) },
        trailingContent = { StatusTrailing(content.dbSizeStatus, palette.textSecondary, statusColumnWidth, content.readPhase) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.metadata_musicbrainz_title),
        icon = { SettingIcon(R.drawable.globe, palette.accent) },
        trailingContent = { StatusTrailing(content.mbStatus, content.mbColor, statusColumnWidth, content.readPhase) },
    )

    MenuSectionTitle(stringResource(R.string.maintenance_section_caches_downloads))
    // Cache rows (the Data settings order): image, song, song download - each shows
    // "<size> used (<pct>%)" in the shared status column
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_image_cache),
        icon = { SettingIcon(R.drawable.image, palette.accent) },
        trailingContent = { StatusTrailing(content.imagesStatus, palette.textSecondary, statusColumnWidth, content.readPhase) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_song_cache),
        icon = { SettingIcon(R.drawable.music_file, palette.accent) },
        trailingContent = { StatusTrailing(content.songCacheStatus, palette.textSecondary, statusColumnWidth, content.readPhase) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_song_download_cache),
        icon = { SettingIcon(R.drawable.download, palette.accent) },
        trailingContent = { StatusTrailing(content.downloadsCacheStatus, palette.textSecondary, statusColumnWidth, content.readPhase) },
    )
    // Downloads: expandable row (chevron trailing, one line per download underneath)
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_downloads),
        icon = { SettingIcon(R.drawable.download, palette.accent) },
        modifier = maintenanceExpansionSemantics(downloadsExpanded),
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
    // Jobs: expandable row (chevron trailing, ALL the jobs underneath - abnormal in RED)
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_jobs),
        icon = { SettingIcon(R.drawable.calendar, palette.accent) },
        modifier = maintenanceExpansionSemantics(jobsExpanded),
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusTrailing(content.jobsStatus, content.jobsColor, statusColumnWidth, content.readPhase)
                Icon(
                    painter = painterResource(if (jobsExpanded) R.drawable.chevron_up else R.drawable.chevron_down),
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        enabled = content.readPhase == MaintenanceReadPhase.Ready,
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
        trailingContent = { StatusTrailing(content.ytStatus, content.ytColor, statusColumnWidth, content.readPhase) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.listen_together_title),
        icon = { SettingIcon(R.drawable.people, palette.accent) },
        trailingContent = { StatusTrailing(content.ltStatus, content.ltColor, statusColumnWidth, content.readPhase) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.settings_discord_title),
        icon = { SettingIcon(R.drawable.logo_discord, palette.accent) },
        trailingContent = { StatusTrailing(content.discordStatus, content.discordColor, statusColumnWidth, if (content.discordColor == palette.red) MaintenanceReadPhase.Ready else content.readPhase) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.settings_lastfm_title),
        icon = { SettingIcon(R.drawable.logo_lastfm, palette.accent) },
        trailingContent = { StatusTrailing(content.lastfmStatus, content.lastfmColor, statusColumnWidth, content.readPhase) },
    )

    MenuSectionTitle(stringResource(R.string.maintenance_section_system))
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_backup),
        icon = { SettingIcon(R.drawable.backup, palette.accent) },
        trailingContent = { StatusTrailing(content.backupStatus, content.backupColor, statusColumnWidth, content.readPhase) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_auto_update),
        icon = { SettingIcon(R.drawable.update, palette.accent) },
        trailingContent = { StatusTrailing(content.updateStatus, content.updateColor, statusColumnWidth) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_battery),
        icon = { SettingIcon(R.drawable.battery_opti, palette.accent) },
        trailingContent = { StatusTrailing(content.batteryStatus, content.batteryColor, statusColumnWidth, content.readPhase) },
    )
    ListMenu.Entry(
        text = stringResource(R.string.maintenance_last_crash),
        icon = { SettingIcon(R.drawable.bugs, palette.accent) },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusTrailing(content.crashStatus, content.crashColor, statusColumnWidth, content.readPhase)
                if (content.crashViewVisible) {
                    TextButton(onClick = content.onViewCrash) {
                        Text(
                            text = stringResource(R.string.maintenance_log_view),
                            color = palette.accent,
                        )
                    }
                    TextButton(onClick = content.onExportCrash) {
                        Text(
                            text = stringResource(R.string.logs_action_export),
                            color = palette.accent,
                        )
                    }
                }
            }
        },
    )
    // Debug logs: View + Export live in the entry's trailing slot (the LT pattern) -
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
                if (content.readPhase == MaintenanceReadPhase.Pending) {
                    ItemTextPlaceholder(null, typography().xs, modifier = Modifier.width(statusColumnWidth), width = 0.68f)
                } else Text(
                    text = if (content.readPhase == MaintenanceReadPhase.Unavailable) {
                        stringResource(R.string.common_load_failed)
                    } else content.debugStatus,
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
                // A missing log file is handled by the dialogs.
                if (content.debugButtonsVisible) {
                    TextButton(onClick = content.onViewDebug) {
                        Text(
                            text = stringResource(R.string.maintenance_log_view),
                            color = palette.accent,
                        )
                    }
                    TextButton(onClick = content.onExportLogs) {
                        Text(
                            text = stringResource(R.string.logs_action_export),
                            color = palette.accent,
                        )
                    }
                }
            }
        },
    )
}

@Composable
internal fun MenuSectionTitle(title: String) {
    BasicText(
        text = title,
        style = typography().xxs.semiBold.copy(
            color = colorPalette().accent,
            textAlign = TextAlign.Start,
        ),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .menuSectionHeading(),
    )
}

/** Shared status width and marquee for rows with a trailing status dot. */
@Composable
internal fun StatusTrailing(
    status: String,
    color: Color,
    columnWidth: Dp,
    readPhase: MaintenanceReadPhase = MaintenanceReadPhase.Ready,
) {
    val isScrollingTextDisabled by rememberPreference(disableScrollingTextKey, false)
    val statusColor = if (readPhase == MaintenanceReadPhase.Unavailable) colorPalette().textSecondary else color
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val dot = Modifier.size(8.dp).clip(CircleShape)
        if (readPhase == MaintenanceReadPhase.Pending) {
            Spacer(dot.shimmerEffect())
            Box(Modifier.width(columnWidth)) {
                ItemTextPlaceholder(null, typography().xs, width = 0.68f)
            }
        } else {
            Box(dot.background(statusColor))
            Text(
                text = if (readPhase == MaintenanceReadPhase.Unavailable) stringResource(R.string.common_load_failed) else status,
                style = typography().xs,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(columnWidth).conditional(!isScrollingTextDisabled) {
                    basicMarquee(iterations = Int.MAX_VALUE)
                },
            )
        }
    }
}

/** Standard menu icon chip (same pattern as the app's own menus): tinted rounded 32dp square with an 18dp icon. */
@Composable
internal fun SettingIcon(@DrawableRes icon: Int, color: Color) {
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .iconBadgeBackground(color.copy(alpha = 0.1f), uiRoundnessShape()),
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
