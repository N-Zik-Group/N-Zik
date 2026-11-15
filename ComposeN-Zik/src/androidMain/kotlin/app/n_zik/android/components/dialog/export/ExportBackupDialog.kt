package app.n_zik.android.components.dialog.export

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.core.backup.DatabaseBackup
import timber.log.Timber
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.backup.BackupContent
import app.n_zik.android.components.dialog.backup.BackupContentOption
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.components.settings.appearance.preview.rememberThemeCollectionBackup
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape

object ExportBackupDialog : ContentFramedDialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.backup_action_export)

    override var isActive: Boolean by mutableStateOf(false)

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun DialogBody() {
        val context = LocalContext.current

        // The flow state survives a rotation mid-dialog (same as the import
        // counterpart): a rotation must keep the chosen option and the credential

        // toggles — plain remember would reset them and silently change what the
        // export button exports.
        var selectedOptions by rememberSaveable { mutableIntStateOf(BackupContent.Database.flag) }

        var includeYtbCredentials by rememberSaveable { mutableStateOf(false) }
        var includeDiscordCredentials by rememberSaveable { mutableStateOf(false) }
        var includeLastfmCredentials by rememberSaveable { mutableStateOf(false) }
        var includeGitHubCredentials by rememberSaveable { mutableStateOf(false) }
        var includeProxyCredentials by rememberSaveable { mutableStateOf(false) }

        var remaining by rememberSaveable { mutableIntStateOf(0) }
        var regularInProgress by remember { mutableStateOf(false) }
        var themeStep by rememberSaveable { mutableStateOf(false) }

        fun finishRegularExport() {
            regularInProgress = false
            themeStep = BackupContent.Themes.isSelected(remaining)
            remaining = 0
        }

        val exportProfileStateDialog = ExportProfileStateDialog(context, onPickerFinished = ::finishRegularExport)
        fun exportProfilesOrFinish() {
            if (BackupContent.Profiles.isSelected(remaining)) {
                remaining = remaining and BackupContent.Profiles.flag.inv()
                exportProfileStateDialog.export()
            } else {
                finishRegularExport()
            }
        }
        val exportSettingsDialog = ExportSettingsDialog(context, onPickerFinished = ::exportProfilesOrFinish)
        val exportSettings = {
            exportSettingsDialog.export(
                includeYtbCredentials, includeDiscordCredentials, includeLastfmCredentials, includeProxyCredentials, includeGitHubCredentials
            )
        }
        val exportDatabase = rememberDatabaseExport(
            prepare = { DatabaseBackup.prepareExport(context, getActiveProfile(context)) },
            context = context,
            onResult = { result ->
                result.onFailure { error ->
                    Timber.tag("ExportDatabaseDialog").e(error, "Database export failed")
                    Toaster.e(R.string.backup_export_database_failed)
                }
            },
            onPickerFinished = {
                if (BackupContent.Settings.isSelected(remaining)) {
                    remaining = remaining and BackupContent.Settings.flag.inv()
                    exportSettings()
                } else {
                    exportProfilesOrFinish()
                }
            }
        )

        val includeThemes = BackupContent.Themes.isSelected(selectedOptions)
        val submitThemes = themeStep || selectedOptions == BackupContent.Themes.flag
        val themeBackup = rememberThemeCollectionBackup(export = true, selected = includeThemes || themeStep, onComplete = {
            if (isActive) hideDialog()
        })

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
                                if (submitThemes) themeBackup.primaryLabelRes else R.string.common_action_export,
                            ),
                            primary = true,
                            enabled = if (submitThemes) themeBackup.canSubmit
                                else selectedOptions != 0 && !regularInProgress && !exportDatabase.inProgress,
                            textColor = colorPalette().textSecondary,
                            onClick = {
                                if (submitThemes) {
                                    themeBackup.submit()
                                } else {
                                    regularInProgress = true
                                    remaining = selectedOptions
                                    when {
                                        BackupContent.Database.isSelected(remaining) -> exportDatabase.launch()
                                        BackupContent.Settings.isSelected(remaining) -> {
                                            remaining = remaining and BackupContent.Settings.flag.inv()
                                            exportSettings()
                                        }
                                        else -> exportProfilesOrFinish()
                                    }
                                }
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
                if (themeStep) {
                    themeBackup.Content()
                } else {
                    BackupContent.entries.forEach { option ->
                        val selected = option.isSelected(selectedOptions)
                        BackupContentOption(
                            label = option.labelRes,
                            description = option.exportDescriptionRes,
                            selected = selected,
                            enabled = !regularInProgress,
                            onToggle = { selectedOptions = selectedOptions xor option.flag },
                        )
                        if (option == BackupContent.Themes && selected) themeBackup.Content()

                        AnimatedVisibility(visible = option == BackupContent.Settings && selected) {
                            Column(modifier = Modifier.padding(start = 44.dp, bottom = 8.dp)) {
                                BackupDetailOption(stringResource(R.string.backup_include_youtube_credentials), includeYtbCredentials, !regularInProgress) {
                                    includeYtbCredentials = it
                                }
                                BackupDetailOption(stringResource(R.string.backup_include_discord_credentials), includeDiscordCredentials, !regularInProgress) {
                                    includeDiscordCredentials = it
                                }
                                BackupDetailOption(stringResource(R.string.backup_include_github_identity_credentials), includeGitHubCredentials, !regularInProgress) {
                                    includeGitHubCredentials = it
                                }
                                BackupDetailOption(stringResource(R.string.backup_include_lastfm_credentials), includeLastfmCredentials, !regularInProgress) {
                                    includeLastfmCredentials = it
                                }
                                BackupDetailOption(stringResource(R.string.backup_include_proxy_password), includeProxyCredentials, !regularInProgress) {
                                    includeProxyCredentials = it
                                }
                            }
                        }
                    }
                    BackupContentOption(
                        label = R.string.backup_export_all,
                        description = R.string.backup_export_select_all_description,
                        selected = selectedOptions == BackupContent.all,
                        enabled = !regularInProgress,
                        onToggle = { selectedOptions = if (selectedOptions == BackupContent.all) 0 else BackupContent.all },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BackupDetailOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val palette = colorPalette()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(uiRoundnessShape())
            .toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = onToggle)
            .padding(vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(if (selected) R.drawable.checked_filled else R.drawable.unchecked_outline),
            contentDescription = null,
            tint = if (selected) palette.text else palette.textSecondary,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            label,
            style = typography().xxs,
            color = palette.text,
            maxLines = 1,
            modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE),
        )
    }
}
