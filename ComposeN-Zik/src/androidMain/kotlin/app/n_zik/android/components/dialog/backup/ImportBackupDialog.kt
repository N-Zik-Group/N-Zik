package app.n_zik.android.components.dialog.backup

import androidx.compose.foundation.layout.PaddingValues
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withContext
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.profileDisplayName
import app.n_zik.android.utils.resolveProfileDisplayName
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.BuildConfig
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.components.import.ImportChainRunner
import app.n_zik.android.components.import.ImportDatabase
import app.n_zik.android.components.import.ImportProfileState
import app.n_zik.android.components.import.ImportSettings
import app.n_zik.android.components.import.buildProfileTargetOptions
import app.n_zik.android.components.settings.appearance.preview.ThemeCollectionImport
import app.n_zik.android.components.settings.appearance.preview.rememberThemeCollectionBackup
import app.n_zik.android.core.rescue.RescueFiles
import app.n_zik.android.utils.coroutines.NzikDispatchers

object ImportBackupDialog : ContentFramedDialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.backup_action_import)

    override var isActive: Boolean by mutableStateOf(false)

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        // The flow state survives a rotation mid-flow (same as the onboarding import
        // screen): a rotation must keep the chosen option, the chain mode and the

        // already-picked files — plain remember would reset them and silently drop
        // the user's selections.
        var selectedOptions by rememberSaveable { mutableIntStateOf(BackupContent.Database.flag) }
        var regularOptions by rememberSaveable { mutableIntStateOf(0) }
        var pendingThemeRestore by rememberSaveable(stateSaver = ThemeCollectionImport.Saver) {
            mutableStateOf<ThemeCollectionImport?>(null)
        }

        // A picked file tagged with a profile other than its import target: confirm
        // before importing it. Saveable DATA (tag + target id), not a lambda: the

        // pending URIs are saveable, so a rotation must re-offer the half-finished
        // chain instead of dropping the warning while the stale picks stay pending

        // (the proceed action is rebuilt from the target id).
        var crossProfilePending by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }

        // The state-only import's cross-profile warning: the import runs straight on
        // the confirm (nothing is pending), so a rotation just means re-picking the

        // file — no saveable state needed. Triple: (tag, target profile ID, proceed).
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
                onCompleted = { hideDialog() },
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
                    // target — otherwise confirm first (the state is global, so mixing

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

        // Profiles only — the global profile state (list + faces): no target-profile
        // question (the state is shared by every profile) and no restart on its own.
        val importProfileState = ImportProfileState(
            context,
            onImportComplete = {
                hideDialog()
            }
        ) { tag, proceed ->
            stateCrossProfilePending = Triple(tag, getActiveProfile(context), proceed)
        }

        // Deferred profile picker of a combined import: the file is captured, not imported —
        // the import runs later, into the chosen profile (ImportChainRunner).
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
                else -> hideDialog()
            }
        }

        val includeThemes = BackupContent.Themes.isSelected(selectedOptions)
        val themeBackup = rememberThemeCollectionBackup(
            export = false,
            selected = includeThemes,
            onComplete = { hideDialog() },
            onImportPrepared = if (selectedOptions and BackupContent.Themes.flag.inv() != 0) {
                { restore ->
                    pendingThemeRestore = restore
                    startRegularImport()
                }
            } else null,
        )

        DialogFrame(
            onDismiss = ::hideDialog,
            title = dialogTitle,
            bodyPadding = PaddingValues(0.dp),
            footer = {
                ButtonRow(
                    buttons = listOf(
                        DialogButton(
                            text = stringResource(R.string.common_action_cancel),
                            onClick = ::hideDialog,
                            danger = true,
                        ),
                        DialogButton(
                            text = stringResource(
                                if (includeThemes) themeBackup.primaryLabelRes else R.string.common_action_import,
                            ),
                            primary = true,
                            enabled = selectedOptions != 0 && (!includeThemes || themeBackup.canSubmit),
                            textColor = colorPalette().textSecondary,
                            onClick = {
                                abortChain()
                                regularOptions = selectedOptions and BackupContent.Themes.flag.inv()
                                if (includeThemes) themeBackup.submit() else startRegularImport()
                            },
                        )
                    )
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()).padding(24.dp)
            ) {
                ImportBackupContentOptions(selectedOptions, { selectedOptions = it }) { themeBackup.Content() }
            }

            // Target-profile question (NZik dialog frame): where the picked per-profile
            // data lands — self-gates on its own isActive
            ImportTargetProfileDialog.Render()

            // Cross-profile warning of the chain: the picked file was created for a
            // profile other than its import target. Dismissing it abandons the half

            // chain — the pending picks are dropped, so they cannot leak into the
            // next attempt.
            DialogPresence(target = crossProfilePending) { (tag, targetId) ->
                ConfirmationDialog(
                    title = stringResource(R.string.backup_action_import),
                    text = stringResource(
                        R.string.recovery_cross_profile_confirm,
                        tag,
                        resolveProfileDisplayName(
                            targetId,
                            context.profileDisplayName(targetId),
                            stringResource(R.string.profile_base_name)
                        )
                    ),
                    confirmText = stringResource(R.string.common_action_import),
                    onDismiss = {
                        crossProfilePending = null
                        abortChain()
                    },
                    onConfirm = {
                        crossProfilePending = null
                        startImportChain(targetId)
                    },
                    // Dismiss aborts an import chain; confirmation must only start it.
                    dismissOnConfirm = false
                )
            }

            // Cross-profile warning of the state-only import: the file was created for
            // a profile other than the active one. The import runs on the confirm

            // (nothing is pending), so dismissing just drops the question.
            DialogPresence(target = stateCrossProfilePending) { (tag, targetId, proceed) ->
                ConfirmationDialog(
                    title = stringResource(R.string.backup_action_import),
                    text = stringResource(
                        R.string.recovery_cross_profile_confirm,
                        tag,
                        resolveProfileDisplayName(
                            targetId,
                            context.profileDisplayName(targetId),
                            stringResource(R.string.profile_base_name)
                        )
                    ),
                    confirmText = stringResource(R.string.common_action_import),
                    onDismiss = {
                        stateCrossProfilePending = null
                    },
                    onConfirm = {
                        stateCrossProfilePending = null
                        proceed()
                    },
                    // Dismiss aborts an import chain; confirmation must only start it.
                    dismissOnConfirm = false
                )
            }
        }
    }
}
