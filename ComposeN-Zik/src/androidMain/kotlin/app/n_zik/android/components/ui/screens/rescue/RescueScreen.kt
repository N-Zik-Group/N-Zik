package app.n_zik.android.components.ui.screens.rescue

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.getEncryptedSharedPreferencesResult
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.core.rescue.RescueFiles
import app.n_zik.android.core.rescue.RescueProcess
import kotlinx.coroutines.CoroutineStart
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import es.dmoral.toasty.Toasty
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicText

/** Time left to read the confirmation toast before the `:rescue` process is ended. */
private const val PROCESS_EXIT_DELAY_MS = 1_500L

/**
 * Main UI composable for the Rescue Center.
 *
 * Lists the recovery actions by category (data, maintenance, danger zone), with confirmation
 * dialogs for destructive ones.
 * Runs in the `:rescue` process — NO Room, no DI, no player, no `appContext()`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescueScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    // Encrypted prefs Result (safe, no throw). Opened lazily and off the main thread, only when an
    // action awaits it: Keystore work must not run in composition, and the database and log
    // actions never need it.
    val encryptedPrefs = remember(scope) {
        scope.async(NzikDispatchers.DATA, start = CoroutineStart.LAZY) {
            context.getEncryptedSharedPreferencesResult()
        }
    }

    // State for confirmation dialogs
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }

    // State for credential toggles (export settings)
    var includeYtb by remember { mutableStateOf(false) }
    var includeDiscord by remember { mutableStateOf(false) }
    var includeLastfm by remember { mutableStateOf(false) }
    var includeProxy by remember { mutableStateOf(false) }
    var showCredentialToggles by remember { mutableStateOf(false) }

    // Set once restored settings are waiting for this process to end: no other write may run.
    var exitPending by remember { mutableStateOf(false) }

    // Which actions are available depends on files on disk (logs, backups). Read off the main
    // thread and re-read after every action so the cards never stay stale.
    var fileStateVersion by remember { mutableIntStateOf(0) }
    var fileState by remember { mutableStateOf(RescueFileState()) }
    LaunchedEffect(fileStateVersion) {
        fileState = withContext(NzikDispatchers.DATA) {
            RescueFileState(
                hasLogs = RescueFiles.hasLogs(context),
                hasDatabaseBackup = RescueFiles.hasBackup(context),
                hasSettingsBackup = RescueFiles.hasSettingsBackup(context)
            )
        }
    }

    // Status of the main process, which the process guard (guardWrite) watches. Poll every
    // second while it is alive: the kill request sent when the Rescue Center opened usually
    // lands within a few seconds, so the status moves from "stopping" to "stopped" without
    // the user doing anything.
    //
    // When the main process is observed dead BELOW 31 (where the probe is trustworthy), the
    // kill-request flag is discarded: a dead process has no pending kill, and a stale flag
    // would make the next healthy launch end itself at startup. From 31 on,
    // getRunningAppProcesses() is restricted to the calling process, so the probe reports
    // "not running" even while the main process is alive: there the flag is NOT cancelled and
    // the status falls back to the flag itself — it disappears only once the kill receiver
    // consumed it, i.e. once the kill landed.
    // A new kill request (danger zone) restarts the polling via killRequestGeneration.
    var mainProcessRunning by remember { mutableStateOf<Boolean?>(null) }
    var killRequestGeneration by remember { mutableIntStateOf(0) }
    LaunchedEffect(killRequestGeneration) {
        while (true) {
            val running = runCatching {
                withContext(NzikDispatchers.DATA) { RescueFiles.isMainProcessRunning(context) }
            }.getOrNull()
            when {
                // Probe failed: keep the last known state and retry — never cancel on unknown.
                running == null -> Unit
                running -> mainProcessRunning = true
                else -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        // Trustworthy probe: the main process is dead — discard the pending flag.
                        runCatching {
                            withContext(NzikDispatchers.DATA) { RescueProcess.cancelKillRequest(context) }
                        }
                        mainProcessRunning = false
                        break
                    }
                    // 31+: "not running" is not proof of death — the flag is: it is gone only
                    // after the kill receiver consumed it. Unknown flag state → still pending.
                    val killPending = runCatching {
                        withContext(NzikDispatchers.DATA) { RescueProcess.hasKillRequest(context) }
                    }.getOrDefault(true)
                    if (!killPending) {
                        mainProcessRunning = false
                        break
                    }
                    mainProcessRunning = true
                }
            }
            delay(1_000L)
        }
    }

    // Helper to show result
    fun showResult(result: Result<*>, successMsg: String? = null) {
        fileStateVersion++
        result.onSuccess {
            val msg = successMsg ?: context.getString(R.string.rescue_success)
            Toasty.success(context, msg, Toast.LENGTH_SHORT, true).show()
        }.onFailure { e ->
            val msg = if (e is RescueFiles.DatabaseBusyException) {
                context.getString(R.string.rescue_error_database_busy)
            } else {
                context.getString(R.string.rescue_error, e.message ?: "Unknown")
            }
            Toasty.error(context, msg, Toast.LENGTH_LONG, true).show()
            Timber.tag("RescueScreen").e(e, "Action failed")
        }
    }

    // Same as showResult, plus a warning when encrypted credentials could not be processed
    fun showSettingsResult(result: Result<RescueFiles.SettingsOutcome>) {
        showResult(result)
        if (result.getOrNull()?.encryptedSkipped == true) {
            Toasty.warning(
                context,
                context.getString(R.string.rescue_encrypted_unavailable_warning),
                Toast.LENGTH_LONG,
                true
            ).show()
        }
    }

    // Helper to check main process before write actions
    fun guardWrite(action: () -> Unit) {
        // A write now would commit this process's stale in-memory preferences over the restored files.
        if (exitPending) return
        if (RescueFiles.isMainProcessRunning(context)) {
            Toasty.warning(context, context.getString(R.string.rescue_main_process_running), Toast.LENGTH_LONG, true).show()
        } else {
            action()
        }
    }

    // SAF launchers
    val exportDbLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.sqlite3")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(NzikDispatchers.DATA) { RescueFiles.exportDatabase(context, uri) }
            showResult(result)
        }
    }

    val importDbLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        guardWrite {
            scope.launch {
                val result = withContext(NzikDispatchers.DATA) { RescueFiles.importDatabase(context, uri) }
                showResult(result)
            }
        }
    }

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(NzikDispatchers.DATA) {
                val wantsCredentials = includeYtb || includeDiscord || includeLastfm || includeProxy
                RescueFiles.exportSettings(
                    context, uri,
                    if (wantsCredentials) encryptedPrefs.await() else null,
                    includeYtb, includeDiscord, includeLastfm, includeProxy
                )
            }
            showSettingsResult(result)
        }
    }

    val importSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        guardWrite {
            scope.launch {
                val result = withContext(NzikDispatchers.DATA) {
                    RescueFiles.importSettings(context, uri, encryptedPrefs.await())
                }
                showSettingsResult(result)
            }
        }
    }

    val exportLogsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(NzikDispatchers.DATA) { RescueFiles.exportCrashLogs(context, uri) }
            result.onSuccess { found ->
                if (found == true) {
                    showResult(Result.success(Unit))
                } else {
                    Toasty.warning(context, context.getString(R.string.rescue_no_logs), Toast.LENGTH_SHORT, true).show()
                }
            }.onFailure { e ->
                showResult(Result.failure<Unit>(e))
            }
        }
    }

    // Confirmation dialog
    confirmAction?.let { pending ->
        RescueConfirmationDialog(
            text = stringResource(pending.messageRes),
            onDismiss = { confirmAction = null },
            onConfirm = {
                val action = confirmAction
                confirmAction = null
                action?.onConfirm?.invoke()
            }
        )
    }

    // Credential toggles dialog for export settings
    if (showCredentialToggles) {
        Dialog(
            onDismissRequest = { showCredentialToggles = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BasicText(
                            text = stringResource(R.string.rescue_export_settings),
                            style = TextStyle(
                                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = includeYtb, onCheckedChange = { includeYtb = it })
                                Text(stringResource(R.string.rescue_include_youtube_credentials), color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = includeDiscord, onCheckedChange = { includeDiscord = it })
                                Text(stringResource(R.string.rescue_include_discord_credentials), color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = includeLastfm, onCheckedChange = { includeLastfm = it })
                                Text(stringResource(R.string.rescue_include_lastfm_credentials), color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = includeProxy, onCheckedChange = { includeProxy = it })
                                Text(stringResource(R.string.rescue_include_proxy_credentials), color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        RescueDialogButtons(
                            cancelText = stringResource(android.R.string.cancel),
                            confirmText = stringResource(android.R.string.ok),
                            onCancel = { showCredentialToggles = false },
                            onConfirm = {
                                showCredentialToggles = false
                                exportSettingsLauncher.launch("${BuildConfig.APP_NAME} $date Settings.csv")
                            }
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            RescueHeader(title = stringResource(R.string.rescue_center))
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.rescue_center_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Main process status: "stopping" while the kill request is pending, "stopped"
            // once it landed (the process guard is then released).
            mainProcessRunning?.let { running ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(if (running) R.drawable.loader else R.drawable.checkmark),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(
                            if (running) R.string.rescue_status_main_process_stopping
                            else R.string.rescue_status_main_process_stopped
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── DATA & BACKUP ───
            RescueCategoryHeader(stringResource(R.string.rescue_category_data))

            // Export database
            RescueActionCard(
                iconRes = R.drawable.server,
                title = stringResource(R.string.rescue_export_database),
                description = stringResource(R.string.rescue_export_database_description),
                onClick = {
                    exportDbLauncher.launch("${BuildConfig.APP_NAME} $date Database.sqlite")
                }
            )

            // Import database
            RescueActionCard(
                iconRes = R.drawable.server,
                title = stringResource(R.string.rescue_import_database),
                description = stringResource(R.string.rescue_import_database_description),
                onClick = {
                    guardWrite {
                        confirmAction = ConfirmAction(R.string.rescue_confirm_import_database) {
                            importDbLauncher.launch(arrayOf(
                                "application/vnd.sqlite3",
                                "application/x-sqlite3",
                                "application/octet-stream"
                            ))
                        }
                    }
                }
            )

            // Export settings
            RescueActionCard(
                iconRes = R.drawable.settings,
                title = stringResource(R.string.rescue_export_settings),
                description = stringResource(R.string.rescue_export_settings_description),
                onClick = { showCredentialToggles = true }
            )

            // Import settings
            RescueActionCard(
                iconRes = R.drawable.settings,
                title = stringResource(R.string.rescue_import_settings),
                description = stringResource(R.string.rescue_import_settings_description),
                onClick = {
                    guardWrite {
                        confirmAction = ConfirmAction(R.string.rescue_confirm_import_settings) {
                            importSettingsLauncher.launch(arrayOf("text/csv", "text/plain"))
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ─── MAINTENANCE ───
            RescueCategoryHeader(stringResource(R.string.rescue_category_maintenance))

            // Export logs
            RescueActionCard(
                iconRes = R.drawable.bugs,
                title = stringResource(R.string.rescue_export_logs),
                description = stringResource(R.string.rescue_export_logs_description),
                enabled = fileState.hasLogs,
                disabledReason = stringResource(R.string.rescue_no_logs),
                onClick = {
                    exportLogsLauncher.launch("${BuildConfig.APP_NAME} $date Logs.txt")
                }
            )

            // Delete logs
            RescueActionCard(
                iconRes = R.drawable.trash,
                title = stringResource(R.string.rescue_delete_logs),
                description = stringResource(R.string.rescue_delete_logs_description),
                enabled = fileState.hasLogs,
                disabledReason = stringResource(R.string.rescue_no_logs),
                onClick = {
                    guardWrite {
                        confirmAction = ConfirmAction(R.string.rescue_confirm_delete_logs) {
                            scope.launch {
                                val result = withContext(NzikDispatchers.DATA) {
                                    RescueFiles.deleteLogs(context)
                                }
                                showResult(result)
                            }
                        }
                    }
                }
            )

            // Clear cache
            RescueActionCard(
                iconRes = R.drawable.trash,
                title = stringResource(R.string.rescue_clear_cache),
                description = stringResource(R.string.rescue_clear_cache_description),
                onClick = {
                    guardWrite {
                        confirmAction = ConfirmAction(R.string.rescue_confirm_clear_cache) {
                            scope.launch {
                                val result = withContext(NzikDispatchers.DATA) {
                                    RescueFiles.clearCache(context)
                                }
                                showResult(result)
                            }
                        }
                    }
                }
            )

            // Delete downloads
            RescueActionCard(
                iconRes = R.drawable.trash,
                title = stringResource(R.string.rescue_delete_downloads),
                description = stringResource(R.string.rescue_delete_downloads_description),
                onClick = {
                    guardWrite {
                        confirmAction = ConfirmAction(R.string.rescue_confirm_delete_downloads) {
                            scope.launch {
                                val result = withContext(NzikDispatchers.DATA) {
                                    RescueFiles.deleteDownloads(context)
                                }
                                showResult(result)
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ─── DANGER ZONE ───
            RescueCategoryHeader(stringResource(R.string.rescue_category_danger))

            // Kill the app: re-sends the kill request when the automatic one (sent when this
            // screen opened) failed — broadcast lost, main thread fully frozen. Not gated by
            // guardWrite: its whole purpose is to release the guard, and it writes nothing.
            RescueActionCard(
                iconRes = R.drawable.logout,
                title = stringResource(R.string.rescue_kill_app),
                description = stringResource(R.string.rescue_kill_app_description),
                onClick = {
                    confirmAction = ConfirmAction(R.string.rescue_confirm_kill_app) {
                        scope.launch {
                            withContext(NzikDispatchers.DATA) {
                                RescueProcess.requestKillMain(context)
                            }
                        }
                        // Restart the status polling so "stopping" → "stopped" is visible again.
                        killRequestGeneration++
                    }
                }
            )

            // Reset database
            RescueActionCard(
                iconRes = R.drawable.server,
                title = stringResource(R.string.rescue_reset_database),
                description = stringResource(R.string.rescue_reset_database_description),
                onClick = {
                    guardWrite {
                        // A second reset overwrites the only backup: say so before it happens.
                        val message = if (fileState.hasDatabaseBackup) {
                            R.string.rescue_confirm_reset_database_replace_backup
                        } else {
                            R.string.rescue_confirm_reset_database
                        }
                        confirmAction = ConfirmAction(message) {
                            scope.launch {
                                val result = withContext(NzikDispatchers.DATA) {
                                    RescueFiles.resetDatabase(context)
                                }
                                showResult(result)
                            }
                        }
                    }
                }
            )

            // Restore database
            RescueActionCard(
                iconRes = R.drawable.server,
                title = stringResource(R.string.rescue_restore_database),
                description = stringResource(R.string.rescue_restore_database_description),
                enabled = fileState.hasDatabaseBackup,
                disabledReason = stringResource(R.string.rescue_no_backup),
                onClick = {
                    guardWrite {
                        confirmAction = ConfirmAction(R.string.rescue_confirm_restore_database) {
                            scope.launch {
                                val result = withContext(NzikDispatchers.DATA) {
                                    RescueFiles.restoreDatabase(context)
                                }
                                showResult(result)
                            }
                        }
                    }
                }
            )

            // Reset settings
            RescueActionCard(
                iconRes = R.drawable.settings,
                title = stringResource(R.string.rescue_reset_settings),
                description = stringResource(R.string.rescue_reset_settings_description),
                onClick = {
                    guardWrite {
                        // A second reset would overwrite the backup with already-cleared settings.
                        val message = if (fileState.hasSettingsBackup) {
                            R.string.rescue_confirm_reset_settings_replace_backup
                        } else {
                            R.string.rescue_confirm_reset_settings
                        }
                        confirmAction = ConfirmAction(message) {
                            scope.launch {
                                val result = withContext(NzikDispatchers.DATA) {
                                    RescueFiles.resetSettings(context, encryptedPrefs.await())
                                }
                                showResult(result)
                            }
                        }
                    }
                }
            )

            // Restore settings
            RescueActionCard(
                iconRes = R.drawable.settings,
                title = stringResource(R.string.rescue_restore_settings),
                description = stringResource(R.string.rescue_restore_settings_description),
                enabled = fileState.hasSettingsBackup,
                disabledReason = stringResource(R.string.rescue_no_settings_backup),
                onClick = {
                    guardWrite {
                        confirmAction = ConfirmAction(R.string.rescue_confirm_restore_settings) {
                            scope.launch {
                                val result = withContext(NzikDispatchers.DATA) {
                                    RescueFiles.restoreSettings(context)
                                }
                                showResult(result)
                                if (result.isSuccess) {
                                    // The XML files were swapped behind this process's in-memory
                                    // SharedPreferences: a later commit() would write the stale map
                                    // back over them. End the :rescue process so nothing does. Armed
                                    // on the main looper, not in the composition scope: leaving the
                                    // screen or recreating the activity cannot cancel it.
                                    exitPending = true
                                    NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                                        delay(PROCESS_EXIT_DELAY_MS)
                                        // The kill must run even if finishing the task throws: the
                                        // old Handler runnable ended the process on any exception.
                                        try {
                                            (context as? Activity)?.finishAndRemoveTask()
                                        } finally {
                                            Process.killProcess(Process.myPid())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )

            // Delete backups: removes the only safety copy, so it lives in the danger zone
            RescueActionCard(
                iconRes = R.drawable.trash,
                title = stringResource(R.string.rescue_delete_backups),
                description = stringResource(R.string.rescue_delete_backups_description),
                enabled = fileState.hasDatabaseBackup || fileState.hasSettingsBackup,
                disabledReason = stringResource(R.string.rescue_no_backups_to_delete),
                onClick = {
                    guardWrite {
                        confirmAction = ConfirmAction(R.string.rescue_confirm_delete_backups) {
                            scope.launch {
                                val result = withContext(NzikDispatchers.DATA) {
                                    RescueFiles.deleteBackups(context)
                                }
                                showResult(result)
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RescueCategoryHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun RescueActionCard(
    iconRes: Int,
    title: String,
    description: String,
    enabled: Boolean = true,
    disabledReason: String? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        // Still tappable (it explains why it is unavailable), but announced as disabled.
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                if (!enabled) {
                    disabled()
                    // The reason is otherwise only shown by a toast when the card is tapped.
                    disabledReason?.let { stateDescription = it }
                }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        onClick = {
            if (enabled) {
                onClick()
            } else if (disabledReason != null) {
                Toasty.warning(context, disabledReason, Toast.LENGTH_SHORT, true).show()
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (enabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
    }
}

private data class ConfirmAction(
    val messageRes: Int,
    val onConfirm: () -> Unit
)

/** Availability of the actions that depend on files present on disk. */
private data class RescueFileState(
    val hasLogs: Boolean = false,
    val hasDatabaseBackup: Boolean = false,
    val hasSettingsBackup: Boolean = false
)

@Composable
fun RescueHeader(title: String) {
    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        BasicText(
            text = title,
            style = TextStyle(
                fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RescueConfirmationDialog(
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    cancelText: String = stringResource(android.R.string.cancel),
    confirmText: String = stringResource(android.R.string.ok)
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Message text
                    BasicText(
                        text = text,
                        style = TextStyle(
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    )

                    RescueDialogButtons(
                        cancelText = cancelText,
                        confirmText = confirmText,
                        onCancel = onDismiss,
                        onConfirm = {
                            onConfirm()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Cancel / confirm buttons shared by the Rescue dialogs. Material3 buttons keep the button role
 * for TalkBack and a 48 dp minimum touch target.
 */
@Composable
private fun RescueDialogButtons(
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        ) {
            Text(text = cancelText, fontWeight = FontWeight.Medium)
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier.weight(1f)
        ) {
            Text(text = confirmText, fontWeight = FontWeight.Medium)
        }
    }
}
