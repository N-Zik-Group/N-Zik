package app.n_zik.android.components.dialog.backup

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.profileDisplayName
import app.it.fast4x.rimusic.utils.resolveProfileDisplayName
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.components.dialog.backup.ImportTargetProfileDialog
import app.n_zik.android.components.import.ImportChainRunner
import app.n_zik.android.components.import.ImportDatabase
import app.n_zik.android.components.import.ImportProfileState
import app.n_zik.android.components.import.ImportSettings
import app.n_zik.android.components.import.buildProfileTargetOptions
import app.n_zik.android.core.rescue.RescueFiles
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.n_zik.android.components.dialog.common.Dialog

object ImportBackupDialog : Dialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.import_backup)

    override var isActive: Boolean by mutableStateOf(false)

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        // The flow state survives a rotation mid-flow (same as the onboarding import
        // screen): a rotation must keep the chosen option, the chain mode and the
        // already-picked files — plain remember would reset them and silently drop
        // the user's selections.
        var selectedOption by rememberSaveable { mutableIntStateOf(0) }
        // 0 = single option, 1 = both (database -> settings), 2 = all (database -> settings -> profile state)
        var importMode by rememberSaveable { mutableIntStateOf(0) }

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
                onCompleted = { hideDialog() }
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

        // Every per-profile import (database / settings / both / all) asks where it
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
                        // The host may have been dismissed while the state file was
                        // being read: its Render() is no longer composed, so the target
                        // dialog must not be flipped on behind its back (a stale
                        // isActive would surface on the next composition).
                        if (isActive) {
                            showTargetDialog(preselect, withTag)
                        }
                    }
                }
            }
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
                Timber.tag("ImportBackupDialog").d("Chaining the profile state pick (all mode)")
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
                Timber.tag("ImportBackupDialog").d("Chaining the settings pick (mode: $importMode)")
                importSettings.onShortClick()
            } else {
                openTargetDialog()
            }
        })

        val databaseLabel = stringResource(R.string.database)
        val databaseDescription = stringResource(R.string.import_database_description)
        val settingsLabel = stringResource(R.string.settings)
        val settingsDescription = stringResource(R.string.import_settings_description)
        val bothLabel = stringResource(R.string.database_and_settings)
        val bothDescription = stringResource(R.string.import_both_description)
        // The option is the profile state (the list + every face), not the login
        // accounts: labeled "Profiles" like everywhere else in the app.
        val profilesLabel = stringResource(R.string.profiles)
        val profilesDescription = stringResource(R.string.import_accounts_description)
        val allLabel = stringResource(R.string.import_all)
        val allDescription = stringResource(R.string.import_all_description)

        val options = listOf(
            Triple(R.drawable.server, databaseLabel, databaseDescription),
            Triple(R.drawable.settings, settingsLabel, settingsDescription),
            Triple(R.drawable.server, bothLabel, bothDescription),
            Triple(R.drawable.person, profilesLabel, profilesDescription),
            Triple(R.drawable.server, allLabel, allDescription)
        )

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                options.forEachIndexed { index, (_, title, description) ->
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
                                text = title,
                                style = typography().xs.semiBold,
                                color = colorPalette().text
                            )
                            Text(
                                text = description,
                                style = typography().xxs,
                                color = colorPalette().textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Target-profile question (NZik dialog frame): where the picked per-profile
            // data lands — self-gates on its own isActive
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

            // Cross-profile warning of the state-only import: the file was created for
            // a profile other than the active one. The import runs on the confirm
            // (nothing is pending), so dismissing just drops the question.
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

            Button(
                onClick = {
                    Timber.tag("ImportBackupDialog").d("Selected option: $selectedOption")
                    when (selectedOption) {
                        0 -> {
                            importMode = 0
                            importDatabase.onShortClick()
                        }
                        1 -> {
                            importMode = 0
                            importSettings.onShortClick()
                        }
                        2 -> {
                            importMode = 1
                            importDatabase.onShortClick()
                        }
                        3 -> {
                            // Global state only — no target-profile question, no restart
                            importProfileState.onShortClick()
                        }
                        4 -> {
                            importMode = 2
                            importDatabase.onShortClick()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorPalette().accent,
                    contentColor = colorPalette().textSecondary
                ),
                shape = uiRoundnessShape()
            ) {
                Text(
                    text = stringResource(R.string.import_button),
                    style = typography().s.medium
                )
            }
        }
    }
}
