package app.n_zik.android.components.onboarding

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.BuildConfig
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.backup.BackupContent
import app.n_zik.android.components.dialog.backup.ImportBackupContentOptions
import app.n_zik.android.components.dialog.backup.ImportTargetProfileDialog
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.import.ImportChainRunner
import app.n_zik.android.components.import.ImportDatabase
import app.n_zik.android.components.import.ImportProfileState
import app.n_zik.android.components.import.ImportSettings
import app.n_zik.android.components.import.buildProfileTargetOptions
import app.n_zik.android.components.settings.appearance.preview.rememberThemeCollectionBackup
import app.n_zik.android.core.rescue.RescueFiles
import app.n_zik.android.typography
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.profileDisplayName
import app.n_zik.android.utils.resolveProfileDisplayName
import timber.log.Timber

/**
 * Second step of the first-launch onboarding flow (after permissions, before the
 * accounts step): an optional restore of the database and/or the settings from a
 * backup file (created by the app's Backup & Restore feature or by a previous version).
 *
 * Reuses the existing import pipeline ([ImportDatabase] / [ImportSettings], the same
 * components behind Settings → Backup and Restore). Every per-profile restore first asks
 * which profile the data lands into ([ImportTargetProfileDialog]); a successful restore
 * into the active profile ends with the restart prompt ([RestartAppDialog]) — its
 * `Render()` lives in the activity's onboarding container (it must survive the step
 * transition, which a screen-local composition would not).
 *
 * Two exits: "skip" calls [onComplete] (the flow moves on to the accounts step); a
 * successful restore advances the flow and triggers the restart prompt — the complete
 * flag is left unwritten, so the restart lands on the next step and the user stays
 * inside onboarding, with the restored data live once the app re-reads its preferences.
 */
@Composable
fun OnboardingImportScreen(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current

    // Preserve the default first-launch database and settings restore.
    var selectedOptions by rememberSaveable {
        mutableIntStateOf(BackupContent.Database.flag or BackupContent.Settings.flag)
    }
    var regularOptions by rememberSaveable { mutableIntStateOf(0) }
    var pendingThemeRestore by remember { mutableStateOf<((() -> Unit) -> Unit)?>(null) }
    var pendingDb by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingSettings by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingState by rememberSaveable { mutableStateOf<Uri?>(null) }

    // A picked file tagged with a profile other than its import target: confirm
    // before importing it. Saveable DATA (tag + target id), not a lambda: the
//
    // pending URIs are saveable, so a rotation must re-offer the half-finished
    // chain instead of dropping the warning while the stale picks stay pending
//
    // (the proceed action is rebuilt from the target id).
//
    var crossProfilePending by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }

    // The state-only import's cross-profile warning: the import runs straight on
    // the confirm (nothing is pending), so a rotation just means re-picking the
//
    // file — no saveable state needed. Triple: (tag, target profile ID, proceed).
    var stateCrossProfilePending by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }

    // Flips off when the screen leaves the composition: a target dialog must never be
    // flipped on by a read that finishes after the user left the step (its Render()
//
    // would not be composed, and the stale state would surface on the next entry).
    var screenActive by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { screenActive = false } }

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
    }

    fun startImportChain(target: String) {
        val restoreThemes = pendingThemeRestore
        if (BackupContent.Themes.isSelected(selectedOptions) && restoreThemes == null) {
            abortChain()
            return
        }
        val db = pendingDb
        val settings = pendingSettings
        val state = pendingState
        pendingDb = null
        pendingSettings = null
        pendingState = null
        pendingThemeRestore = null
        val runImport = {
            ImportChainRunner.start(
                context = context,
                target = target,
                databaseUri = db,
                settingsUri = settings,
                stateUri = state,
                onCompleted = { onComplete() }
            )
        }
        if (restoreThemes == null) runImport() else restoreThemes(runImport)
    }

    fun showTargetDialog(preselect: String?, imported: List<Pair<String, String>>) {
        ImportTargetProfileDialog.show(
            options = buildProfileTargetOptions(
                context,
                imported,
                context.getString(R.string.profile_base_name)
            ),
            preselectedId = preselect,
            onTarget = { target ->
                // The all chain's state file must carry a tag matching the chosen
                // target — otherwise confirm first (the state is global, so mixing
//
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

    // Every per-profile restore (database / settings / both / all) asks where it
    // lands before anything is written — the restart only applies when the chosen
//
    // target is the active profile. In all mode the state file's profile list is
    // read first so the dialog offers every profile the bundle carries.
    fun openTargetDialog() {
        val stateUri = pendingState
        if (stateUri == null) {
            // Single / both: the database (else settings) file tag is the only
            // imported profile — show the question right away.
            val preselect = (pendingDb ?: pendingSettings)?.let { profileTagOf(it) }
            showTargetDialog(preselect, preselect?.let { listOf(it to it) } ?: emptyList())
        } else {
            // All: read the state file to offer the profiles it carries (plus the
            // database tag when the list omits it).
            NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                val preselect = (pendingDb ?: pendingSettings)?.let { profileTagOf(it) }
                val imported = RescueFiles.readProfileStateEntries(context, stateUri)
                val withTag = if (preselect != null && imported.none { it.first == preselect }) {
                    listOf(preselect to preselect) + imported
                } else {
                    imported
                }
                withContext(NzikDispatchers.UI) {
                    if (screenActive) {
                        showTargetDialog(preselect, withTag)
                    }
                }
            }
        }
    }

    // Accounts only — the global profile state (list + faces): no target-profile
    // question (the state is shared by every profile) and no restart on its own —
//
    // the list, the names and the face are re-read on recomposition.
    val importProfileState = ImportProfileState(
        context,
        onImportComplete = {
            Timber.tag("Onboarding").i("Profile state restored, advancing the onboarding")
            onComplete()
        }
    ) { tag, proceed ->
        stateCrossProfilePending = Triple(tag, getActiveProfile(context), proceed)
    }

    // Deferred state picker of the all chain: the file is captured, not imported —
    // the import runs later, into the chosen profile (ImportChainRunner).
    val importProfileStateDeferred = ImportProfileState(
        context,
        onFilePicked = { uri ->
            pendingState = uri
            openTargetDialog()
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
            openTargetDialog()
        }
    })

    val importDatabase = ImportDatabase(context, onCancelled = ::abortChain, onFilePicked = { uri ->
        pendingDb = uri
        when {
            BackupContent.Settings.isSelected(regularOptions) -> importSettings.onShortClick()
            BackupContent.Profiles.isSelected(regularOptions) -> importProfileStateDeferred.onShortClick()
            else -> openTargetDialog()
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
        onComplete = { onComplete() },
        onImportPrepared = if (selectedOptions and BackupContent.Themes.flag.inv() != 0) {
            { restore ->
                pendingThemeRestore = restore
                startRegularImport()
            }
        } else null,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorPalette().background0)
            // The flow replaces AppNavigation here: keep the app's edge-to-edge behavior
            // - background full-bleed, content extends under the nav bar, the top bar is
            // still cleared; the nav inset is re-applied to the last child (the restart
            // note), so the final text never lands on the system nav buttons
            .statusBarsPadding()
            // Small screens / enlarged fonts: header + both cards + the note can exceed
            // the viewport — the whole step scrolls instead of clipping
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            // drawable (PNG) instead of mipmap: the adaptive-icon XML wins on device and
            // Compose painterResource only supports vectors/rasters
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = null,
            tint = null,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_restore_title),
            style = typography().l,
            fontWeight = FontWeight.SemiBold,
            color = colorPalette().text
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_restore_description),
            style = typography().s,
            color = colorPalette().textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OnboardingActionCard(
            icon = R.drawable.server,
            title = stringResource(R.string.backup_action_import),
            description = stringResource(R.string.backup_import_with_themes_description),
            extraContent = {
                Spacer(modifier = Modifier.height(12.dp))

                ImportBackupContentOptions(selectedOptions, { selectedOptions = it }) { themeBackup.Content() }

                Spacer(modifier = Modifier.height(12.dp))
                ButtonRow(listOf(
                    DialogButton(
                        text = stringResource(R.string.common_action_cancel),
                        onClick = {
                            abortChain()
                            selectedOptions = 0
                        },
                        danger = true,
                    ),
                    DialogButton(
                        text = stringResource(if (includeThemes) themeBackup.primaryLabelRes else R.string.common_action_import),
                        onClick = {
                            abortChain()
                            regularOptions = selectedOptions and BackupContent.Themes.flag.inv()
                            if (includeThemes) themeBackup.submit() else startRegularImport()
                        },
                        primary = true,
                        enabled = selectedOptions != 0 && (!includeThemes || themeBackup.canSubmit),
                    ),
                ))
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OnboardingActionCard(
            icon = R.drawable.settings,
            title = stringResource(R.string.onboarding_restore_skip),
            description = stringResource(R.string.onboarding_restore_skip_description),
            action = {
                OnboardingPrimaryButton(
                    onClick = {
                        Timber.tag("Onboarding").i("Restore step skipped, onboarding continues")
                        onComplete()
                    },
                ) {
                    // Bounded label: never pushes the card off-screen on small screens / large fonts
                    OnboardingActionLabel(stringResource(R.string.onboarding_restore_skip_button))
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // The note is the last child of the step: it re-applies the nav inset the column
        // dropped, so the final text stays clear of the system nav buttons
        Text(
            text = stringResource(R.string.onboarding_restore_restart_note),
            style = typography().xxs,
            color = colorPalette().textSecondary,
            modifier = Modifier.navigationBarsPadding()
        )
    }

    // Target-profile question (NZik dialog frame): where the picked per-profile data
    // lands — self-gates on its own isActive
    ImportTargetProfileDialog.Render()

    // Cross-profile warning of the chain: the picked file was created for a
    // profile other than its import target. Dismissing it abandons the half
//
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

    // Cross-profile warning of the state-only import: the file was created for a
    // profile other than the active one. The import runs on the confirm (nothing
//
    // is pending), so dismissing just drops the question.
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
