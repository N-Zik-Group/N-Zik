package app.n_zik.android.components.onboarding

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.backup.ImportTargetProfileDialog
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.import.ImportChainRunner
import app.n_zik.android.components.import.ImportDatabase
import app.n_zik.android.components.import.ImportProfileState
import app.n_zik.android.components.import.ImportSettings
import app.n_zik.android.components.import.buildProfileTargetOptions
import app.n_zik.android.core.rescue.RescueFiles
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.profileDisplayName
import app.it.fast4x.rimusic.utils.resolveProfileDisplayName
import timber.log.Timber

/**
 * Second step of the first-launch onboarding flow (after permissions, before the name
 * step): an optional restore of the database and/or the settings from a backup file
 * (created by the app's Backup & Restore feature or by a previous version).
 *
 * Reuses the existing import pipeline ([ImportDatabase] / [ImportSettings], the same
 * components behind Settings -> Backup and restore). Every per-profile restore
 * (database / settings / both / all) first asks which profile the data lands into
 * ([ImportTargetProfileDialog]); a successful restore into the active profile ends
 * with the restart prompt ([RestartAppDialog]) — its `Render()` is composed here
 * because it is normally only composed inside the settings screen, which is not alive
 * during onboarding.
 *
 * Two exits: "skip" calls [onComplete] (the flow moves on to the name step); a
 * successful restore advances the flow to the next step and triggers the restart
 * prompt — the complete flag is left unwritten, so the restart lands on the next step
 * and the user stays inside onboarding, with the restored data live once the app
 * re-reads its preferences.
 */
@Composable
fun OnboardingImportScreen(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current

    // Default to restoring both — the most useful outcome for a first-launch restore.
    // rememberSaveable: a rotation mid-flow must keep the chosen option, the import
    // mode and the picked files of the deferred chain (a late picker result is
    // evaluated against them)
    var selectedOption by rememberSaveable { mutableIntStateOf(2) }
    // 0 = single option, 1 = both (database -> settings), 2 = all (database -> settings -> profile state)
    var importMode by rememberSaveable { mutableIntStateOf(0) }
    var pendingDb by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingSettings by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingState by rememberSaveable { mutableStateOf<Uri?>(null) }

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

    // Flips off when the screen leaves the composition: a target dialog must never be
    // flipped on by a read that finishes after the user left the step (its Render()
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
    }

    fun startImportChain(target: String) {
        val db = pendingDb
        val settings = pendingSettings
        val state = pendingState
        pendingDb = null
        pendingSettings = null
        pendingState = null
        ImportChainRunner.start(
            context = context,
            target = target,
            databaseUri = db,
            settingsUri = settings,
            stateUri = state,
            onCompleted = { onComplete() }
        )
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

    // Settings pick: in the all chain the state picker runs next, otherwise the
    // target-profile question.
    val importSettings = ImportSettings(context, onFilePicked = { uri ->
        pendingSettings = uri
        if (importMode == 2) {
            Timber.tag("Onboarding").d("Chaining the profile state pick (all mode)")
            importProfileStateDeferred.onShortClick()
        } else {
            openTargetDialog()
        }
    })

    // Database pick: in the both/all chain the settings picker runs next,
    // otherwise the target-profile question.
    val importDatabase = ImportDatabase(context, onFilePicked = { uri ->
        pendingDb = uri
        if (importMode == 1 || importMode == 2) {
            Timber.tag("Onboarding").d("Chaining the settings pick (mode: $importMode)")
            importSettings.onShortClick()
        } else {
            openTargetDialog()
        }
    })

    val options = listOf(
        Triple(0, stringResource(R.string.database), stringResource(R.string.import_database_description)),
        Triple(1, stringResource(R.string.settings), stringResource(R.string.import_settings_description)),
        Triple(2, stringResource(R.string.database_and_settings), stringResource(R.string.import_both_description)),
        // The profile state option (the list + every face) — "Profiles", not the login accounts
        Triple(3, stringResource(R.string.profiles), stringResource(R.string.import_accounts_description)),
        Triple(4, stringResource(R.string.import_all), stringResource(R.string.import_all_description))
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorPalette().background0)
            // The flow replaces AppNavigation here: keep the app's edge-to-edge
            // behavior — background full-bleed, content clear of the system bars
            .statusBarsPadding()
            .navigationBarsPadding()
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
            text = stringResource(R.string.onboard_restore_title),
            style = typography().l,
            fontWeight = FontWeight.SemiBold,
            color = colorPalette().text
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboard_restore_desc),
            style = typography().s,
            color = colorPalette().textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OnboardingActionCard(
            icon = R.drawable.server,
            title = stringResource(R.string.import_backup),
            description = stringResource(R.string.import_backup_description),
            extraContent = {
                Spacer(modifier = Modifier.height(12.dp))

                // The restore options — same layout as ImportBackupDialog
                options.forEach { (index, optionTitle, optionDescription) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(uiRoundnessShape())
                            .clickable { selectedOption = index }
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == index,
                            onClick = { selectedOption = index },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorPalette().text,
                                unselectedColor = colorPalette().textSecondary
                            ),
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = optionTitle,
                                style = typography().xs,
                                fontWeight = FontWeight.SemiBold,
                                color = colorPalette().text
                            )
                            Text(
                                text = optionDescription,
                                style = typography().xxs,
                                color = colorPalette().textSecondary
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        importMode = when (selectedOption) {
                            2 -> 1
                            4 -> 2
                            else -> 0
                        }
                        Timber.tag("Onboarding").d("Restore started, option: $selectedOption (importMode: $importMode)")
                        when (selectedOption) {
                            0 -> importDatabase.onShortClick()
                            1 -> importSettings.onShortClick()
                            2 -> importDatabase.onShortClick()
                            3 -> importProfileState.onShortClick()
                            4 -> importDatabase.onShortClick()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorPalette().accent,
                        contentColor = colorPalette().textSecondary
                    ),
                    shape = uiRoundnessShape(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text(stringResource(R.string.import_button))
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OnboardingActionCard(
            icon = R.drawable.settings,
            title = stringResource(R.string.onboard_restore_skip),
            description = stringResource(R.string.onboard_restore_skip_desc),
            action = {
                Button(
                    onClick = {
                        Timber.tag("Onboarding").i("Restore step skipped, onboarding continues")
                        onComplete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorPalette().accent,
                        contentColor = colorPalette().textSecondary
                    ),
                    shape = uiRoundnessShape()
                ) {
                    // Bounded label: never pushes the card off-screen on small screens / large fonts
                    OnboardingActionLabel(stringResource(R.string.onboard_restore_skip_button))
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onboard_restore_restart_note),
            style = typography().xxs,
            color = colorPalette().textSecondary
        )
    }

    // Target-profile question (NZik dialog frame): where the picked per-profile data
    // lands — self-gates on its own isActive
    ImportTargetProfileDialog.Render()

    // Cross-profile warning of the chain: the picked file was created for a
    // profile other than its import target. Dismissing it abandons the half
    // chain — the pending picks are dropped, so they cannot leak into the
    // next attempt.
    crossProfilePending?.let { (tag, targetId) ->
        AlertDialog(
            onDismissRequest = {
                crossProfilePending = null
                abortChain()
            },
            text = {
                Text(
                    stringResource(
                        R.string.rescue_cross_profile_confirm,
                        tag,
                        resolveProfileDisplayName(
                            targetId,
                            context.profileDisplayName(targetId),
                            stringResource(R.string.profile_base_name)
                        )
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        crossProfilePending = null
                        startImportChain(targetId)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorPalette().accent,
                        contentColor = colorPalette().textSecondary
                    )
                ) {
                    Text(stringResource(R.string.import_button))
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        crossProfilePending = null
                        abortChain()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorPalette().background0,
                        contentColor = colorPalette().textSecondary
                    )
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Cross-profile warning of the state-only import: the file was created for a
    // profile other than the active one. The import runs on the confirm (nothing
    // is pending), so dismissing just drops the question.
    stateCrossProfilePending?.let { (tag, targetId, proceed) ->
        AlertDialog(
            onDismissRequest = { stateCrossProfilePending = null },
            text = {
                Text(
                    stringResource(
                        R.string.rescue_cross_profile_confirm,
                        tag,
                        resolveProfileDisplayName(
                            targetId,
                            context.profileDisplayName(targetId),
                            stringResource(R.string.profile_base_name)
                        )
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        stateCrossProfilePending = null
                        proceed()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorPalette().accent,
                        contentColor = colorPalette().textSecondary
                    )
                ) {
                    Text(stringResource(R.string.import_button))
                }
            },
            dismissButton = {
                Button(
                    onClick = { stateCrossProfilePending = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorPalette().background0,
                        contentColor = colorPalette().textSecondary
                    )
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // The restart prompt is normally composed inside the settings screen only —
    // onboarding is the other context that triggers an import, so it must be
    // composed here for the post-import restart to be visible
    RestartAppDialog.Render()
}
