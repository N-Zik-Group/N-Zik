package app.n_zik.android.components.dialog.backup

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.import.ImportChainRunner
import app.n_zik.android.components.import.ImportDatabase
import app.n_zik.android.components.import.ImportProfileState
import app.n_zik.android.components.import.ImportSettings
import app.n_zik.android.components.import.buildProfileTargetOptions
import app.n_zik.android.components.settings.appearance.preview.ThemeCollectionBackupState
import app.n_zik.android.components.settings.appearance.preview.ThemeCollectionImport
import app.n_zik.android.components.settings.appearance.preview.rememberThemeCollectionBackup
import app.n_zik.android.core.rescue.RescueFiles
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.profileDisplayName
import app.n_zik.android.utils.resolveProfileDisplayName
import kotlinx.coroutines.withContext

@Stable
internal class ImportBackupControls(
    val selectedOptions: Int,
    val selectOptions: (Int) -> Unit,
    val themeBackup: ThemeCollectionBackupState,
    val submit: () -> Unit,
    val clear: () -> Unit,
    val dialogs: @Composable () -> Unit,
) {
    val includeThemes get() = BackupContent.Themes.isSelected(selectedOptions)
    val canSubmit get() = selectedOptions != 0 && (!includeThemes || themeBackup.canSubmit)
}

@Composable
internal fun ImportBackupFlow(
    initialOptions: Int,
    onComplete: () -> Unit,
    onProfilesImported: () -> Unit = onComplete,
    content: @Composable (ImportBackupControls) -> Unit,
) {
    val context = LocalContext.current
    var selectedOptions by rememberSaveable { mutableIntStateOf(initialOptions) }
    var regularOptions by rememberSaveable { mutableIntStateOf(0) }
    var pendingThemeRestore by rememberSaveable(stateSaver = ThemeCollectionImport.Saver) {
        mutableStateOf<ThemeCollectionImport?>(null)
    }

    // Retain the pending consent and picked files across recreation.
    var crossProfilePending by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }

    // This picker runs on confirmation, so a recreated screen requires a fresh pick.
    var stateCrossProfilePending by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }

    // Deferred per-profile chain: the picked files wait for the target-profile
    // choice before anything is written (ImportChainRunner).
    var choosingTarget by rememberSaveable { mutableStateOf(false) }
    var targetSelection by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDb by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingSettings by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingState by rememberSaveable { mutableStateOf<Uri?>(null) }

    // The profile tag carried by a picked file name (null when untagged / unreadable).
    fun profileTagOf(uri: Uri): String? =
        RescueFiles.documentDisplayName(context, uri)
            ?.let { RescueFiles.backupProfileTagOf(it, BuildConfig.APP_NAME) }

    fun abortChain() {
        // Nothing was written yet: drop every pick so the flow can start over.
        pendingDb = null
        pendingSettings = null
        pendingState = null
        pendingThemeRestore = null
        choosingTarget = false
        targetSelection = null
    }

    fun startImportChain(target: String) {
        val themes = pendingThemeRestore
        val db = pendingDb
        val settings = pendingSettings
        val state = pendingState
        abortChain()
        ImportChainRunner.start(
            context = context,
            target = target,
            databaseUri = db,
            settingsUri = settings,
            stateUri = state,
            onCompleted = onComplete,
            themes = themes,
        )
    }

    fun showTargetDialog(preselect: String?, imported: List<Pair<String, String>>) {
        ImportTargetProfileDialog.show(
            options = buildProfileTargetOptions(
                context,
                imported,
                context.getString(R.string.profile_base_name)
            ),
            preselectedId = targetSelection ?: preselect,
            onSelectionChanged = { targetSelection = it },
            onTarget = { target ->
                choosingTarget = false
                targetSelection = null
                // The all chain's state file must carry a tag matching the chosen
                // target, since the profile state is shared and requires consent for a mismatch.

                // it in is deliberate).
                val stateTag = pendingState?.let { profileTagOf(it) }
                if (stateTag != null && stateTag != target) {
                    crossProfilePending = stateTag to target
                } else {
                    startImportChain(target)
                }
            },
            onDismiss = { abortChain() }
        )
    }

    // Rebuild the target dialog from the saved picks after recreation.
    DisposableEffect(Unit) {
        onDispose { ImportTargetProfileDialog.clear() }
    }
    LaunchedEffect(choosingTarget) {
        if (!choosingTarget) return@LaunchedEffect
        val stateUri = pendingState
        val taggedUri = pendingDb ?: pendingSettings
        val (preselect, imported) = withContext(NzikDispatchers.DATA) {
            taggedUri?.let(::profileTagOf) to
                stateUri?.let { RescueFiles.readProfileStateEntries(context, it) }.orEmpty()
        }
        val withTag = if (preselect != null && imported.none { it.first == preselect }) {
            listOf(preselect to preselect) + imported
        } else {
            imported
        }
        showTargetDialog(preselect, withTag)
    }

    // Profiles-only import restores the shared list and faces without a target-profile
    // question (the state is shared by every profile) and no restart on its own.
    val importProfileState = ImportProfileState(
        context,
        onImportComplete = onProfilesImported
    ) { tag, proceed ->
        stateCrossProfilePending = Triple(tag, getActiveProfile(context), proceed)
    }

    // A combined import captures the profile state file for the selected target.
    // ImportChainRunner writes it after confirmation.
    val importProfileStateDeferred = ImportProfileState(
        context,
        onFilePicked = { uri ->
            pendingState = uri
            choosingTarget = true
        },
        onCancelled = {
            // The state pick was skipped: nothing was written, the chain restarts.
            abortChain()
        }
    ) { _, _ -> }

    val importSettings = ImportSettings(context, onCancelled = ::abortChain, onFilePicked = { uri ->
        pendingSettings = uri
        if (BackupContent.Profiles.isSelected(regularOptions)) {
            importProfileStateDeferred.onShortClick()
        } else {
            choosingTarget = true
        }
    })

    val importDatabase = ImportDatabase(context, onCancelled = ::abortChain, onFilePicked = { uri ->
        pendingDb = uri
        when {
            BackupContent.Settings.isSelected(regularOptions) -> importSettings.onShortClick()
            BackupContent.Profiles.isSelected(regularOptions) -> importProfileStateDeferred.onShortClick()
            else -> choosingTarget = true
        }
    })

    fun startRegularImport() {
        when {
            BackupContent.Database.isSelected(regularOptions) -> importDatabase.onShortClick()
            BackupContent.Settings.isSelected(regularOptions) -> importSettings.onShortClick()
            BackupContent.Profiles.isSelected(regularOptions) -> {
                if (pendingThemeRestore != null) importProfileStateDeferred.onShortClick()
                else importProfileState.onShortClick()
            }
            else -> onComplete()
        }
    }

    val includeThemes = BackupContent.Themes.isSelected(selectedOptions)
    val themeBackup = rememberThemeCollectionBackup(
        export = false,
        selected = includeThemes,
        onComplete = onComplete,
        onImportPrepared = if (selectedOptions and BackupContent.Themes.flag.inv() != 0) {
            { restore ->
                pendingThemeRestore = restore
                startRegularImport()
            }
        } else null,
    )

    content(
        ImportBackupControls(
            selectedOptions = selectedOptions,
            selectOptions = { selectedOptions = it },
            themeBackup = themeBackup,
            submit = {
                abortChain()
                regularOptions = selectedOptions and BackupContent.Themes.flag.inv()
                if (includeThemes) themeBackup.submit() else startRegularImport()
            },
            clear = {
                abortChain()
                selectedOptions = 0
            },
        ) {
            ImportTargetProfileDialog.Render()
            DialogPresence(target = crossProfilePending) { (tag, targetId) ->
                ConfirmCrossProfileImport(
                    tag = tag,
                    targetId = targetId,
                    onDismiss = {
                        crossProfilePending = null
                        abortChain()
                    },
                    onConfirm = {
                        crossProfilePending = null
                        startImportChain(targetId)
                    },
                )
            }
            DialogPresence(target = stateCrossProfilePending) { (tag, targetId, proceed) ->
                ConfirmCrossProfileImport(
                    tag = tag,
                    targetId = targetId,
                    onDismiss = { stateCrossProfilePending = null },
                    onConfirm = {
                        stateCrossProfilePending = null
                        proceed()
                    },
                )
            }
        }
    )
}

@Composable
private fun ConfirmCrossProfileImport(
    tag: String,
    targetId: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    ConfirmationDialog(
        title = stringResource(R.string.backup_action_import),
        text = stringResource(
            R.string.recovery_cross_profile_confirm,
            tag,
            resolveProfileDisplayName(
                targetId,
                context.profileDisplayName(targetId),
                stringResource(R.string.profile_base_name),
            ),
        ),
        confirmText = stringResource(R.string.common_action_import),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        dismissOnConfirm = false,
    )
}
