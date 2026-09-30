package app.n_zik.android.components.ui.screens.rescue

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.components.ui.screens.profiles.profileSecurePrefs
import app.it.fast4x.rimusic.utils.profileDisplayName
import app.it.fast4x.rimusic.utils.readProfileIds
import app.it.fast4x.rimusic.utils.resolveProfileDisplayName
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.core.backup.ProfileStateArchive
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
import java.time.LocalDateTime
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
import androidx.compose.material3.Scaffold

/** Time left to read the confirmation toast before the `:rescue` process is ended. */
private const val PROCESS_EXIT_DELAY_MS = 1_500L

/**
 * MIME types accepted by the Rescue "Import settings" picker.
 *
 * A rescue-local copy of the app's `ImportSettings.supportedMimes` — the `:rescue` process must
 * stay independent of `components.import` (this screen exists for when the app is in a bad
 * state), so the list is duplicated here instead of referenced. The previous text/csv +
 * text/plain list missed text/comma-separated-values, so a settings file reported under that
 * MIME was hidden in the picker and could not be selected.
 */
private val IMPORT_SETTINGS_MIMES: Array<String> = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
)

/**
 * MIME types accepted by the Rescue "Import database" picker.
 *
 * A rescue-local copy of the app's `ImportDatabase.supportedMimes` (same independence rationale
 * as [IMPORT_SETTINGS_MIMES]).
 */
private val IMPORT_DATABASE_MIMES: Array<String> = arrayOf(
    "application/vnd.sqlite3",
    "application/x-sqlite3",
    "application/octet-stream"
)

/** MIME types accepted by the Rescue "Import accounts" (profile state) picker. */
private val IMPORT_PROFILE_STATE_MIMES: Array<String> = arrayOf(
    "text/plain",
    "application/octet-stream"
)

/**
 * Main UI composable for the Rescue Center.
 *
 * Lists the recovery actions behind two scope categories (chips): "Per profile" (the
 * profile target is the subcategory) and "All" (app-wide actions), each grouped into
 * section cards by sub-scope, with confirmation dialogs for destructive ones.
 * Runs in the `:rescue` process — NO Room, no DI, no player, no `appContext()`, and no
 * app-wide UI helpers (the screens stay on MaterialTheme so nothing app-state can crash
 * here).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescueScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    // Full timestamp for the profile-state export names (same shape as the auto
    // backup names, so the cross-profile tag stays parseable on import).
    val fullDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))
    val defaultName = stringResource(R.string.profile_base_name)

    RescueFiles.initialize(context)

    // Target profile for the data actions — the selector below the section header
    // lets the rescue operate a profile other than the active one (its database is
    // not open, so writing its files directly is safe). In-memory only: the app's
    // active profile is never switched from here.
    var targetProfile by remember { mutableStateOf(getActiveProfile(context)) }
    var profileOptions by remember { mutableStateOf<List<ProfileOption>>(emptyList()) }

    // The per-profile secure prefs of the rescue TARGET — not the active profile's:
    // the per-profile settings actions (export / import / reset) operate on the
    // selected profile, and its credentials live in its own encrypted store
    // (the active profile's store must never be read, cleared or overwritten by
    // an action aimed at another profile). Opened lazily and off the main
    // thread, only when an action awaits it: keystore work must not run in
    // composition, and the database and log actions never need it.
    val encryptedPrefs = remember(scope, targetProfile) {
        scope.async(NzikDispatchers.DATA, start = CoroutineStart.LAZY) {
            runCatching { profileSecurePrefs(context, targetProfile) }
        }
    }

    // State for confirmation dialogs
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }

    // A picked file tagged with a different profile: confirm before importing it.
    var crossProfilePending by remember { mutableStateOf<CrossProfilePending?>(null) }

    // State for credential toggles (export settings)
    var includeYtb by remember { mutableStateOf(false) }
    var includeDiscord by remember { mutableStateOf(false) }
    var includeLastfm by remember { mutableStateOf(false) }
    var includeProxy by remember { mutableStateOf(false) }
    var showCredentialToggles by remember { mutableStateOf(false) }

    // Delete profiles: the selection dialog (a checkbox per user profile — the base is
    // never listed, it has its own reset action) and the checked IDs.
    var deleteProfilesDialog by remember { mutableStateOf(false) }
    var deleteSelection by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Reset profiles: the same selection pattern as delete, but the chosen profiles are
    // factory-reset (kept in the list) instead of removed.
    var resetProfilesDialog by remember { mutableStateOf(false) }
    var resetSelection by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Restore profiles: the same selection pattern, but the candidates are only the
    // profiles that carry a reset backup (the data a reset set aside before wiping).
    var restoreProfilesDialog by remember { mutableStateOf(false) }
    var restoreSelection by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Category filter: which scope of actions is shown — per-profile (with the profile
    // target as subcategory) or the entire app. All is the default.
    var rescueCategory by remember { mutableStateOf(RescueCategory.ALL) }

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
                hasSettingsBackup = RescueFiles.hasSettingsBackup(context),
                hasProfileState = ProfileStateArchive.hasState(context),
                profileBackups = RescueFiles.profileBackupIds(context)
            )
        }
        // The selector options: the active profile first, then the user profiles —
        // labels resolved against the stored display names.
        profileOptions = withContext(NzikDispatchers.DATA) {
            val activeId = getActiveProfile(context)
            (listOf(activeId) + context.readProfileIds()).distinct().map { id ->
                ProfileOption(
                    id = id,
                    label = resolveProfileDisplayName(id, context.profileDisplayName(id), defaultName),
                    isActive = id == activeId
                )
            }
        }
    }

    /** Points the rescue at [id] (in-memory target; the active profile is not switched). */
    fun selectTargetProfile(id: String) {
        if (id == targetProfile) return
        targetProfile = id
        RescueFiles.setRescueProfile(id)
        // The rescue backups and the face file are per-profile: re-read availability.
        fileStateVersion++
    }

    /**
     * The base row of the reset/restore selection dialogs: the base is in
     * [profileOptions] only while active (the names file never lists it), so when it
     * is not active it is built from its stored display name (falling back to the
     * app default name).
     */
    fun baseProfileOption(): ProfileOption =
        profileOptions.firstOrNull { it.id == DEFAULT_PROFILE_ID } ?: ProfileOption(
            id = DEFAULT_PROFILE_ID,
            label = resolveProfileDisplayName(DEFAULT_PROFILE_ID, context.profileDisplayName(DEFAULT_PROFILE_ID), defaultName),
            isActive = false,
        )

    /**
     * Runs [onProceed] — behind a cross-profile confirmation when the picked file's
     * name carries the tag of another profile (a backup taken under a different
     * profile). Unprofiled names proceed straight away.
     */
    fun withCrossProfileGuard(uri: Uri, onProceed: () -> Unit) {
        val tag = RescueFiles.documentDisplayName(context, uri)
            ?.let { RescueFiles.backupProfileTagOf(it, BuildConfig.APP_NAME) }
        if (tag != null && tag != targetProfile) {
            crossProfilePending = CrossProfilePending(tag, onProceed)
        } else {
            onProceed()
        }
    }

    // Status of the main process, which the process guard (guardWrite) watches. Poll every
    // second while it is alive: the kill request sent when the Rescue Center opened usually
    // lands within a few seconds, so the status moves from "stopping" to "stopped" without
    // the user doing anything.
    //
    // Liveness comes from RescueProcess.isMainProcessLikelyAlive: below 31 the
    // getRunningAppProcesses() probe is trustworthy; from 31 on it is restricted to the
    // calling process, so the alive marker (refreshed by the main process itself every ~5 s)
    // decides. When the process is observed dead — trustworthy probe below 31, stale marker
    // from 31 on — the pending kill request is discarded: a dead process has no pending kill,
    // and a stale flag would make the next healthy launch end itself at startup. From 31 on
    // there is one more "stopped" signal: the flag consumed by the kill receiver (the kill
    // landed) — the status turns to "stopped" at that instant, even while the marker is
    // still fresh (the marker stays fresh for up to 15 s after the process dies).
    // A new kill request (danger zone) restarts the polling via killRequestGeneration.
    var mainProcessRunning by remember { mutableStateOf<Boolean?>(null) }
    var killRequestGeneration by remember { mutableIntStateOf(0) }
    LaunchedEffect(killRequestGeneration) {
        while (true) {
            val likelyAlive = runCatching {
                withContext(NzikDispatchers.DATA) { RescueProcess.isMainProcessLikelyAlive(context) }
            }.getOrNull()
            when {
                // Detection failed: keep the last known state and retry — never cancel on unknown.
                likelyAlive == null -> Unit
                likelyAlive -> {
                    // From 31 on the marker alone is not enough: the kill may have landed
                    // already — the flag is gone once the kill receiver consumed it, while the
                    // marker is still fresh for up to 15 s. Unknown flag state → still pending.
                    val killPending =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching {
                                withContext(NzikDispatchers.DATA) { RescueProcess.hasKillRequest(context) }
                            }.getOrDefault(true)
                        } else {
                            true
                        }
                    if (killPending) {
                        mainProcessRunning = true
                    } else {
                        // The kill request was consumed: the kill landed — nothing left to stop.
                        mainProcessRunning = false
                        break
                    }
                }
                else -> {
                    // The main process is observed dead: discard the pending kill request so it
                    // never leaks into the next launch and makes a healthy app end itself at
                    // startup (below 31 the trustworthy probe says dead; from 31 on the stale
                    // marker does — covering a process that died without consuming the flag).
                    runCatching {
                        withContext(NzikDispatchers.DATA) { RescueProcess.cancelKillRequest(context) }
                    }
                    mainProcessRunning = false
                    break
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
        // Likely-alive, not raw probe: from 31 on the probe is restricted to the calling
        // process and would always say "not running", letting a live main process through. The
        // DB-busy guard (checkpointWal) stays the last resort when the marker misleads.
        if (RescueProcess.isMainProcessLikelyAlive(context)) {
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
        withCrossProfileGuard(uri) {
            guardWrite {
                scope.launch {
                    val result = withContext(NzikDispatchers.DATA) { RescueFiles.importDatabase(context, uri) }
                    showResult(result)
                }
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
        withCrossProfileGuard(uri) {
            guardWrite {
                scope.launch {
                    val result = withContext(NzikDispatchers.DATA) {
                        RescueFiles.importSettings(context, uri, encryptedPrefs.await())
                    }
                    showSettingsResult(result)
                }
            }
        }
    }

    // Profile state (list + faces) — the archive the auto backup writes
    val exportProfileStateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(NzikDispatchers.DATA) { RescueFiles.exportProfileState(context, uri) }
            showResult(result, context.getString(R.string.rescue_accounts_exported))
        }
    }

    val importProfileStateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        withCrossProfileGuard(uri) {
            guardWrite {
                scope.launch {
                    val result = withContext(NzikDispatchers.DATA) { RescueFiles.importProfileState(context, uri) }
                    showResult(
                        result,
                        result.getOrNull()?.let { applied ->
                            when {
                                applied.profiles > 0 ->
                                    context.getString(R.string.rescue_accounts_imported_count, applied.profiles)
                                // A base-only archive restores the base face and/or name, not a profile count.
                                applied.faces > 0 || applied.names > 0 ->
                                    context.getString(R.string.rescue_accounts_imported_base)
                                else ->
                                    context.getString(R.string.rescue_accounts_imported_count, 0)
                            }
                        }
                    )
                }
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

    // Cross-profile warning: the picked file was created under a different profile
    crossProfilePending?.let { pending ->
        RescueConfirmationDialog(
            text = stringResource(
                R.string.rescue_cross_profile_confirm,
                pending.tag,
                profileOptions.firstOrNull { it.id == targetProfile }?.label ?: targetProfile
            ),
            onDismiss = { crossProfilePending = null },
            onConfirm = {
                val action = crossProfilePending
                crossProfilePending = null
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

    // Delete profiles selection: every user profile as a checkbox row (the base is
    // never listed — it is reset by its own action, never deleted). The confirm button
    // is disabled until a profile is checked; the actual deletion is confirmed by the
    // standard confirmation dialog (same pattern as the other destructive actions).
    if (deleteProfilesDialog) {
        val userProfiles = profileOptions.filter { it.id != DEFAULT_PROFILE_ID }
        Dialog(
            onDismissRequest = { deleteProfilesDialog = false },
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
                            text = stringResource(R.string.rescue_profiles_to_delete),
                            style = TextStyle(
                                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            userProfiles.forEach { option ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = deleteSelection.contains(option.id),
                                        onCheckedChange = { checked ->
                                            deleteSelection = if (checked)
                                                deleteSelection + option.id
                                            else
                                                deleteSelection - option.id
                                        }
                                    )
                                    Text(
                                        text = option.label,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (option.isActive) {
                                        Text(
                                            text = stringResource(R.string.rescue_profile_active),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        RescueDialogButtons(
                            cancelText = stringResource(android.R.string.cancel),
                            confirmText = stringResource(android.R.string.ok),
                            confirmEnabled = deleteSelection.isNotEmpty(),
                            onCancel = { deleteProfilesDialog = false },
                            onConfirm = {
                                val selected = deleteSelection
                                deleteProfilesDialog = false
                                deleteSelection = emptySet()
                                guardWrite {
                                    // The selection is the informed choice; the standard
                                    // confirmation states the permanent consequences.
                                    confirmAction = ConfirmAction(R.string.rescue_confirm_delete_profiles) {
                                        scope.launch {
                                            val activeId = getActiveProfile(context)
                                            val result = withContext(NzikDispatchers.DATA) {
                                                RescueFiles.deleteProfiles(context, selected.toList())
                                            }
                                            showResult(
                                                result,
                                                result.getOrNull()?.let {
                                                    context.getString(R.string.rescue_profiles_deleted_count, it)
                                                }
                                            )
                                            // Deleting the CURRENT profile switches the active slot
                                            // to the base: the app must be relaunched to pick the
                                            // change up (the base itself can never be deleted).
                                            if (result.isSuccess && activeId in selected &&
                                                activeId != DEFAULT_PROFILE_ID
                                            ) {
                                                Toasty.warning(
                                                    context,
                                                    context.getString(R.string.rescue_profiles_deleted_active_restart),
                                                    Toast.LENGTH_LONG,
                                                    true
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (resetProfilesDialog) {
        // The base is offered like any other profile (first row, badged): the unified
        // reset covers it too, and the result message already asks for the relaunch
        // it requires.
        val resettableProfiles = listOf(baseProfileOption()) +
            profileOptions.filter { it.id != DEFAULT_PROFILE_ID }
        Dialog(
            onDismissRequest = { resetProfilesDialog = false },
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
                            text = stringResource(R.string.rescue_profiles_to_reset),
                            style = TextStyle(
                                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            resettableProfiles.forEach { option ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = resetSelection.contains(option.id),
                                        onCheckedChange = { checked ->
                                            resetSelection = if (checked)
                                                resetSelection + option.id
                                            else
                                                resetSelection - option.id
                                        }
                                    )
                                    Text(
                                        text = option.label,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (option.isActive) {
                                        Text(
                                            text = stringResource(R.string.rescue_profile_active),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (option.id == DEFAULT_PROFILE_ID) {
                                        Text(
                                            text = stringResource(R.string.rescue_profile_base),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        RescueDialogButtons(
                            cancelText = stringResource(android.R.string.cancel),
                            confirmText = stringResource(android.R.string.ok),
                            confirmEnabled = resetSelection.isNotEmpty(),
                            onCancel = { resetProfilesDialog = false },
                            onConfirm = {
                                val selected = resetSelection
                                resetProfilesDialog = false
                                resetSelection = emptySet()
                                guardWrite {
                                    // The selection is the informed choice; the standard
                                    // confirmation states the permanent consequences.
                                    confirmAction = ConfirmAction(R.string.rescue_confirm_reset_profiles) {
                                        scope.launch {
                                            val result = withContext(NzikDispatchers.DATA) {
                                                RescueFiles.resetProfiles(context, selected.toList())
                                            }
                                            showResult(
                                                result,
                                                result.getOrNull()?.let {
                                                    context.getString(R.string.rescue_profiles_reset_count, it)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (restoreProfilesDialog) {
        // Only the profiles that carry a reset backup can be restored — the base
        // included (first row, badged) when it was reset at least once.
        val restorableProfiles = (
            if (fileState.profileBackups.contains(DEFAULT_PROFILE_ID))
                listOf(baseProfileOption())
            else emptyList()
        ) + profileOptions.filter {
            it.id != DEFAULT_PROFILE_ID && fileState.profileBackups.contains(it.id)
        }
        Dialog(
            onDismissRequest = { restoreProfilesDialog = false },
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
                            text = stringResource(R.string.rescue_profiles_to_restore),
                            style = TextStyle(
                                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            restorableProfiles.forEach { option ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = restoreSelection.contains(option.id),
                                        onCheckedChange = { checked ->
                                            restoreSelection = if (checked)
                                                restoreSelection + option.id
                                            else
                                                restoreSelection - option.id
                                        }
                                    )
                                    Text(
                                        text = option.label,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (option.isActive) {
                                        Text(
                                            text = stringResource(R.string.rescue_profile_active),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (option.id == DEFAULT_PROFILE_ID) {
                                        Text(
                                            text = stringResource(R.string.rescue_profile_base),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        RescueDialogButtons(
                            cancelText = stringResource(android.R.string.cancel),
                            confirmText = stringResource(android.R.string.ok),
                            confirmEnabled = restoreSelection.isNotEmpty(),
                            onCancel = { restoreProfilesDialog = false },
                            onConfirm = {
                                val selected = restoreSelection
                                restoreProfilesDialog = false
                                restoreSelection = emptySet()
                                guardWrite {
                                    // The selection is the informed choice; the standard
                                    // confirmation states what the restore replaces.
                                    confirmAction = ConfirmAction(R.string.rescue_confirm_restore_profiles) {
                                        scope.launch {
                                            val result = withContext(NzikDispatchers.DATA) {
                                                RescueFiles.restoreProfiles(context, selected.toList())
                                            }
                                            showResult(
                                                result,
                                                result.getOrNull()?.let {
                                                    context.getString(R.string.rescue_profiles_restored_count, it)
                                                }
                                            )
                                        }
                                    }
                                }
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
        containerColor = MaterialTheme.colorScheme.background,
        // Like the app's screens: the scaffold background stretches behind the transparent
        // system bars (full height, no band under the nav buttons) and the content scrolls
        // behind them — the header owns its status-bar padding.
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        // Fake bottom padding: the room the nav buttons take, appended INSIDE the scroll so
        // the last card can settle above the transparent buttons instead of ending hidden
        // behind them (0 on devices with a side nav bar).
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // The 16dp margin goes INSIDE the scroll: applied before verticalScroll it shrinks the
        // viewport itself, so the scrolling content is clipped 16dp above the screen bottom
        // instead of running behind the transparent nav buttons.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
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

            // Category chips: the scope of the actions. Only the selected category's cards
            // are shown; "Per profile" adds a profile subcategory to pick the target.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                RescueCategoryChip(
                    label = stringResource(R.string.rescue_category_all),
                    selected = rescueCategory == RescueCategory.ALL,
                    onSelect = { rescueCategory = RescueCategory.ALL }
                )
                Spacer(modifier = Modifier.width(8.dp))
                RescueCategoryChip(
                    label = stringResource(R.string.rescue_category_profile),
                    selected = rescueCategory == RescueCategory.PER_PROFILE,
                    onSelect = { rescueCategory = RescueCategory.PER_PROFILE }
                )
            }

            // ─── PER PROFILE ─── (selected category; the profile target selector below is
            // the subcategory: it picks which profile the profile-scoped actions run on)
            AnimatedVisibility(
                visible = rescueCategory == RescueCategory.PER_PROFILE,
                enter = rescueCategoryEnter,
                exit = rescueCategoryExit,
                label = "Per profile category"
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                // Profile target selector — the profile-scoped actions below operate on the
                // chosen profile (its database, its settings, its face); switching is not implied.
                Text(
                    text = stringResource(R.string.rescue_profile_target_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        profileOptions.forEach { option ->
                            val selected = option.id == targetProfile
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (selected)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { selectTargetProfile(option.id) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selected)
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (option.isActive) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.rescue_profile_active),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = (if (selected)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        ).copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }

                // Database — the target profile's database (export / import / reset / restore)
                RescueSectionCard(
                title = stringResource(R.string.rescue_group_database),
                iconRes = R.drawable.server,
                content = {
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
                                    importDbLauncher.launch(IMPORT_DATABASE_MIMES)
                                }
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
                        iconRes = R.drawable.restore,
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
                }
            )

            // Settings — the target profile's settings (export / import / reset / restore)
            RescueSectionCard(
                title = stringResource(R.string.rescue_group_settings),
                iconRes = R.drawable.settings,
                content = {
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
                                    importSettingsLauncher.launch(IMPORT_SETTINGS_MIMES)
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
                        iconRes = R.drawable.restore,
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
                }
            )
                }
            }

            // ─── ALL ─── (selected category; the app-wide actions — no target profile)
            AnimatedVisibility(
                visible = rescueCategory == RescueCategory.ALL,
                enter = rescueCategoryEnter,
                exit = rescueCategoryExit,
                label = "All category"
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                // Profiles — the profile state of the whole app (the list + every face) and
            // the multi-select operations on the user profiles (the selection is made in
            // the dialog, not in the per-profile target subcategory)
            RescueSectionCard(
                title = stringResource(R.string.rescue_group_profiles),
                iconRes = R.drawable.person,
                content = {
                    // Export profiles (profile state: the list + every face, one .txt file)
                    RescueActionCard(
                        iconRes = R.drawable.person,
                        title = stringResource(R.string.rescue_export_profiles),
                        description = stringResource(R.string.rescue_export_profiles_description),
                        enabled = fileState.hasProfileState,
                        disabledReason = stringResource(R.string.rescue_no_profile_state),
                        onClick = {
                            exportProfileStateLauncher.launch(
                                "${BuildConfig.APP_NAME}_${targetProfile}_${fullDate}_Profiles_Export.txt"
                            )
                        }
                    )

                    // Import profiles (profile state)
                    RescueActionCard(
                        iconRes = R.drawable.person,
                        title = stringResource(R.string.rescue_import_profiles),
                        description = stringResource(R.string.rescue_import_profiles_description),
                        onClick = {
                            guardWrite {
                                confirmAction = ConfirmAction(R.string.rescue_confirm_import_accounts) {
                                    importProfileStateLauncher.launch(IMPORT_PROFILE_STATE_MIMES)
                                }
                            }
                        }
                    )

                    // Delete profiles: every user profile is offered as a checkbox in the
                    // selection dialog (the base is never listed — it is reset by its own
                    // action, never deleted). The active profile is NOT skipped: deleting it
                    // switches the active slot to the base, and the app must be relaunched.
                    RescueActionCard(
                        iconRes = R.drawable.trash,
                        title = stringResource(R.string.rescue_delete_profiles),
                        description = stringResource(R.string.rescue_delete_profiles_description),
                        enabled = profileOptions.any { it.id != DEFAULT_PROFILE_ID },
                        disabledReason = stringResource(R.string.rescue_no_profiles_to_delete),
                        onClick = {
                            guardWrite {
                                deleteSelection = emptySet()
                                deleteProfilesDialog = true
                            }
                        }
                    )

                    // Reset profiles: every profile is offered as a checkbox in the selection
                    // dialog (the base included — first row, badged). Unlike deletion, the
                    // profiles are kept in the list: they are simply factory-reset (database,
                    // settings, name and photo deleted), so the active slot is untouched and
                    // the app must be relaunched — the result message says so.
                    RescueActionCard(
                        iconRes = R.drawable.person,
                        title = stringResource(R.string.rescue_reset_profiles),
                        description = stringResource(R.string.rescue_reset_profiles_description),
                        onClick = {
                            guardWrite {
                                resetSelection = emptySet()
                                resetProfilesDialog = true
                            }
                        }
                    )

                    // Restore profiles: brings back the profiles reset earlier — their
                    // last reset backup (database, settings, credentials, face and name),
                    // the base included. The selection dialog offers only the profiles
                    // that carry a backup.
                    RescueActionCard(
                        iconRes = R.drawable.restore,
                        title = stringResource(R.string.rescue_restore_profiles),
                        description = stringResource(R.string.rescue_restore_profiles_description),
                        enabled = fileState.profileBackups.isNotEmpty(),
                        disabledReason = stringResource(R.string.rescue_no_profiles_to_restore),
                        onClick = {
                            guardWrite {
                                restoreSelection = emptySet()
                                restoreProfilesDialog = true
                            }
                        }
                    )
                }
            )

            // Logs — export / delete the app log
            RescueSectionCard(
                title = stringResource(R.string.rescue_group_logs),
                iconRes = R.drawable.bugs,
                content = {
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
                }
            )

            // Storage — the shared (non-profiled) caches, downloads and the safety copies
            RescueSectionCard(
                title = stringResource(R.string.rescue_group_storage),
                iconRes = R.drawable.trash,
                content = {
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

                    // Delete backups: removes the only safety copy
                    RescueActionCard(
                        iconRes = R.drawable.trash,
                        title = stringResource(R.string.rescue_delete_backups),
                        description = stringResource(R.string.rescue_delete_backups_description),
                        // The action wipes the whole rescue_backups tree — database, settings
                        // AND the profile reset backups — so any of them keeps the card on.
                        enabled = fileState.hasDatabaseBackup ||
                            fileState.hasSettingsBackup ||
                            fileState.profileBackups.isNotEmpty(),
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
                }
            )

            // App — end the main process
            RescueSectionCard(
                title = stringResource(R.string.rescue_group_app),
                iconRes = R.drawable.logout,
                content = {

            // Kill the app: re-sends the kill request when the automatic one (sent when this
            // screen opened) failed — broadcast lost, main thread fully frozen. Gated on the
            // process being (still) alive: once it is stopped there is nothing to kill, and a
            // request sent to a dead process would only record a stale flag that the next
            // healthy launch consumes as a self-kill. Not gated by guardWrite: its whole
            // purpose is to release the guard, and it writes nothing (while the process is
            // alive — which is exactly when this button is enabled).
                    RescueActionCard(
                        iconRes = R.drawable.logout,
                        title = stringResource(R.string.rescue_kill_app),
                        description = stringResource(R.string.rescue_kill_app_description),
                        enabled = mainProcessRunning == true,
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
                }
            )
                }
            }

            // Fake bottom padding: the nav-buttons room (the 16dp bottom margin comes from the
            // column padding), so the last card settles above the transparent buttons.
            Spacer(modifier = Modifier.height(navBarBottom))
        }
    }
}

/**
 * The scope categories of the Rescue Center. The selected category decides which action
 * cards are shown; [PER_PROFILE] additionally exposes the profile target as a subcategory.
 */
private enum class RescueCategory {
    ALL,
    PER_PROFILE
}

/**
 * Category block transitions: the incoming category expands + fades in (same motion as
 * the settings cards' entry transitions), and the outgoing one is removed instantly —
 * an animated exit would keep both categories on screen at the same time (the old one
 * collapsing below the new one), which reads as overlapping cards.
 */
private val rescueCategoryEnter: EnterTransition =
    expandVertically(animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
private val rescueCategoryExit: ExitTransition =
    shrinkVertically(animationSpec = tween(0)) + fadeOut(animationSpec = tween(0))

@Composable
private fun RescueCategoryChip(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A self-contained section card (NZik-style frame: rounded card, icon + accent title
 * header) that groups the action cards of one sub-scope (database / settings / …).
 * Deliberately built on MaterialTheme alone — the :rescue process must not depend on
 * app-wide helpers (theme prefs, …) or it risks crashing there.
 */
@Composable
private fun RescueSectionCard(
    title: String,
    iconRes: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Section header: icon tile + accent title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
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

/** A picked file whose name carries the tag of another profile: confirm before importing it. */
private data class CrossProfilePending(
    val tag: String,
    val onConfirm: () -> Unit
)

/** A selectable rescue target profile (display label + active marker). */
private data class ProfileOption(
    val id: String,
    val label: String,
    val isActive: Boolean
)

/** Availability of the actions that depend on files present on disk. */
private data class RescueFileState(
    val hasLogs: Boolean = false,
    val hasDatabaseBackup: Boolean = false,
    val hasSettingsBackup: Boolean = false,
    val hasProfileState: Boolean = false,
    val profileBackups: Set<String> = emptySet()
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
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true
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
            enabled = confirmEnabled,
            modifier = Modifier.weight(1f)
        ) {
            Text(text = confirmText, fontWeight = FontWeight.Medium)
        }
    }
}
