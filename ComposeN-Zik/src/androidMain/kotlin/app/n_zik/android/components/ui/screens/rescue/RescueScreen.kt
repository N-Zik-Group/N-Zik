package app.n_zik.android.components.ui.screens.rescue

import android.net.Uri
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.getEncryptedSharedPreferencesResult
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.core.rescue.RescueFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import es.dmoral.toasty.Toasty
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicText

/**
 * Main UI composable for the Rescue Center.
 *
 * Lists all 8 recovery actions with confirmation dialogs for destructive ones.
 * Runs in the `:rescue` process — NO Room, no DI, no player, no `appContext()`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescueScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    // Encrypted prefs Result (safe, no throw)
    val encryptedPrefsResult = remember {
        context.getEncryptedSharedPreferencesResult()
    }

    // State for confirmation dialogs
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }

    // State for credential toggles (export settings)
    var includeYtb by remember { mutableStateOf(false) }
    var includeDiscord by remember { mutableStateOf(false) }
    var includeLastfm by remember { mutableStateOf(false) }
    var showCredentialToggles by remember { mutableStateOf(false) }

    // Status message
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Helper to show result
    fun showResult(result: Result<*>, successMsg: String? = null) {
        result.onSuccess {
            val msg = successMsg ?: context.getString(R.string.rescue_success)
            statusMessage = msg
            Toasty.success(context, msg, Toast.LENGTH_SHORT, true).show()
        }.onFailure { e ->
            val msg = context.getString(R.string.rescue_error, e.message ?: "Unknown")
            statusMessage = msg
            Toasty.error(context, msg, Toast.LENGTH_LONG, true).show()
            Timber.tag("RescueScreen").e(e, "Action failed")
        }
    }

    // Helper to check main process before write actions
    fun guardWrite(action: () -> Unit) {
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
            val result = withContext(Dispatchers.IO) { RescueFiles.exportDatabase(context, uri) }
            showResult(result)
        }
    }

    val importDbLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        guardWrite {
            scope.launch {
                val result = withContext(Dispatchers.IO) { RescueFiles.importDatabase(context, uri) }
                showResult(result)
            }
        }
    }

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                RescueFiles.exportSettings(
                    context, uri, encryptedPrefsResult,
                    includeYtb, includeDiscord, includeLastfm
                )
            }
            showResult(result)
        }
    }

    val importSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        guardWrite {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    RescueFiles.importSettings(context, uri, encryptedPrefsResult)
                }
                showResult(result)
            }
        }
    }

    val exportLogsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { RescueFiles.exportCrashLogs(context, uri) }
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
    if (confirmAction != null) {
        RescueConfirmationDialog(
            text = stringResource(confirmAction!!.messageRes),
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
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = { showCredentialToggles = false })
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    text = stringResource(android.R.string.cancel),
                                    style = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        showCredentialToggles = false
                                        exportSettingsLauncher.launch("${BuildConfig.APP_NAME} $date Settings.csv")
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    text = stringResource(android.R.string.ok),
                                    style = TextStyle(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
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

            Spacer(modifier = Modifier.height(16.dp))

            // ─── DATA & BACKUP ───
            RescueCategoryHeader(stringResource(R.string.rescue_category_data))

            // 1. Export database
            RescueActionCard(
                iconRes = R.drawable.server,
                title = stringResource(R.string.rescue_export_database),
                description = stringResource(R.string.rescue_export_database_description),
                onClick = {
                    exportDbLauncher.launch("${BuildConfig.APP_NAME} $date Database.sqlite")
                }
            )

            // 2. Import database
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

            // 3. Export settings
            RescueActionCard(
                iconRes = R.drawable.settings,
                title = stringResource(R.string.rescue_export_settings),
                description = stringResource(R.string.rescue_export_settings_description),
                onClick = { showCredentialToggles = true }
            )

            // 4. Import settings
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

            // 5. Clear cache
            RescueActionCard(
                iconRes = R.drawable.trash,
                title = stringResource(R.string.rescue_clear_cache),
                description = stringResource(R.string.rescue_clear_cache_description),
                onClick = {
                    guardWrite {
                        confirmAction = ConfirmAction(R.string.rescue_confirm_clear_cache) {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    RescueFiles.clearCache(context)
                                }
                                showResult(result)
                            }
                        }
                    }
                }
            )

            // 6. Delete downloads
            RescueActionCard(
                iconRes = R.drawable.trash,
                title = stringResource(R.string.rescue_delete_downloads),
                description = stringResource(R.string.rescue_delete_downloads_description),
                onClick = {
                    guardWrite {
                        confirmAction = ConfirmAction(R.string.rescue_confirm_delete_downloads) {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    RescueFiles.deleteDownloads(context)
                                }
                                showResult(result)
                            }
                        }
                    }
                }
            )

            // 6. Export logs
            RescueActionCard(
                iconRes = R.drawable.bugs,
                title = stringResource(R.string.rescue_export_logs),
                description = stringResource(R.string.rescue_export_logs_description),
                enabled = RescueFiles.hasLogs(context),
                disabledReason = stringResource(R.string.rescue_no_logs),
                onClick = {
                    exportLogsLauncher.launch("${BuildConfig.APP_NAME} $date Logs.txt")
                }
            )

            // 7. Delete logs
            RescueActionCard(
                iconRes = R.drawable.trash,
                title = stringResource(R.string.rescue_delete_logs),
                description = stringResource(R.string.rescue_delete_logs_description),
                enabled = RescueFiles.hasLogs(context),
                disabledReason = stringResource(R.string.rescue_no_logs),
                onClick = {
                    guardWrite {
                        confirmAction = ConfirmAction(R.string.rescue_confirm_delete_logs) {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    RescueFiles.deleteLogs(context)
                                }
                                showResult(result)
                            }
                        }
                    }
                }
            )

            // 8. Delete backups (corbeille)
            RescueActionCard(
                iconRes = R.drawable.trash,
                title = stringResource(R.string.rescue_delete_backups),
                description = stringResource(R.string.rescue_delete_backups_description),
                enabled = RescueFiles.hasBackup(context) || RescueFiles.hasSettingsBackup(context),
                disabledReason = stringResource(R.string.rescue_no_backups_to_delete),
                onClick = {
                    guardWrite {
                        confirmAction = ConfirmAction(R.string.rescue_confirm_delete_backups) {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    RescueFiles.deleteBackups(context)
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

            // 9. Reset database
            RescueActionCard(
                iconRes = R.drawable.server,
                title = stringResource(R.string.rescue_reset_database),
                description = stringResource(R.string.rescue_reset_database_description),
                onClick = {
                    guardWrite {
                        confirmAction = ConfirmAction(R.string.rescue_confirm_reset_database) {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    RescueFiles.resetDatabase(context)
                                }
                                showResult(result)
                            }
                        }
                    }
                }
            )

            // 10. Restore database
            RescueActionCard(
                iconRes = R.drawable.server,
                title = stringResource(R.string.rescue_restore_database),
                description = stringResource(R.string.rescue_restore_database_description),
                enabled = RescueFiles.hasBackup(context),
                disabledReason = stringResource(R.string.rescue_no_backup),
                onClick = {
                    guardWrite {
                        confirmAction = ConfirmAction(R.string.rescue_confirm_restore_database) {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    RescueFiles.restoreDatabase(context)
                                }
                                showResult(result)
                            }
                        }
                    }
                }
            )

            // 11. Reset settings
            RescueActionCard(
                iconRes = R.drawable.settings,
                title = stringResource(R.string.rescue_reset_settings),
                description = stringResource(R.string.rescue_reset_settings_description),
                onClick = {
                    guardWrite {
                        confirmAction = ConfirmAction(R.string.rescue_confirm_reset_settings) {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    RescueFiles.resetSettings(context, encryptedPrefsResult)
                                }
                                showResult(result)
                            }
                        }
                    }
                }
            )

            // 12. Restore settings
            RescueActionCard(
                iconRes = R.drawable.settings,
                title = stringResource(R.string.rescue_restore_settings),
                description = stringResource(R.string.rescue_restore_settings_description),
                enabled = RescueFiles.hasSettingsBackup(context),
                disabledReason = stringResource(R.string.rescue_no_settings_backup),
                onClick = {
                    guardWrite {
                        confirmAction = ConfirmAction(R.string.rescue_confirm_restore_settings) {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    RescueFiles.restoreSettings(context)
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
        modifier = Modifier.fillMaxWidth(),
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

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onDismiss)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = cancelText,
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onConfirm()
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = confirmText,
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
