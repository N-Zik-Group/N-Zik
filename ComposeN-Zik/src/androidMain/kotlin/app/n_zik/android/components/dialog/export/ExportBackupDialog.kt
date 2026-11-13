package app.n_zik.android.components.dialog.export

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.core.backup.DatabaseBackup
import timber.log.Timber
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogFrame
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
        val exportProfileStateDialog = ExportProfileStateDialog(context)

        // The flow state survives a rotation mid-dialog (same as the import
        // counterpart): a rotation must keep the chosen option and the credential

        // toggles — plain remember would reset them and silently change what the
        // export button exports.
        var selectedOption by rememberSaveable { mutableIntStateOf(0) }

        val databaseLabel = stringResource(R.string.backup_target_database_label)
        val databaseDescription = stringResource(R.string.backup_export_database_with_covers_description)
        val settingsLabel = stringResource(R.string.settings_title)
        val settingsDescription = stringResource(R.string.backup_export_settings_description)
        val bothLabel = stringResource(R.string.backup_target_database_and_settings_label)
        val bothDescription = stringResource(R.string.backup_export_database_with_covers_and_settings_description)
        // The option is the profile state (the list + every face), not the login
        // accounts: labeled "Profiles" like everywhere else in the app.
        val profilesLabel = stringResource(R.string.profile_title)
        val profilesDescription = stringResource(R.string.backup_export_accounts_description)
        val allLabel = stringResource(R.string.backup_export_all)
        val allDescription = stringResource(R.string.backup_export_all_with_covers_description)

        val options = listOf(
            Triple(R.drawable.server, databaseLabel, databaseDescription),
            Triple(R.drawable.settings, settingsLabel, settingsDescription),
            Triple(R.drawable.server, bothLabel, bothDescription),
            Triple(R.drawable.person, profilesLabel, profilesDescription),
            Triple(R.drawable.server, allLabel, allDescription)
        )

        var includeYtbCredentials by rememberSaveable { mutableStateOf(false) }
        var includeDiscordCredentials by rememberSaveable { mutableStateOf(false) }
        var includeLastfmCredentials by rememberSaveable { mutableStateOf(false) }
        var includeProxyCredentials by rememberSaveable { mutableStateOf(false) }

        var pendingSettings by rememberSaveable { mutableStateOf(false) }
        var pendingProfiles by rememberSaveable { mutableStateOf(false) }
        val exportSettingsDialog = ExportSettingsDialog(context, onPickerFinished = {
            if (pendingProfiles) {
                pendingProfiles = false
                exportProfileStateDialog.export()
            }
        })
        val exportSettings = {
            exportSettingsDialog.export(
                includeYtbCredentials, includeDiscordCredentials, includeLastfmCredentials, includeProxyCredentials
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
                if (pendingSettings) {
                    pendingSettings = false
                    exportSettings()
                }
            }
        )

        DialogFrame(
            onDismiss = ::hideDialog,
            title = dialogTitle,
            bodyPadding = PaddingValues(0.dp),
        footer = {
                ButtonRow(
                    buttons = listOf(
                        DialogButton(
                            text = stringResource(R.string.common_action_export),
                            primary = true,
                            enabled = !exportDatabase.inProgress,
                            textColor = colorPalette().textSecondary,
                            onClick = {
                                pendingSettings = false
                                pendingProfiles = false
                                when (selectedOption) {
                                    0 -> exportDatabase.launch()
                                    1 -> exportSettings()
                                    2, 4 -> {
                                        pendingSettings = true
                                        pendingProfiles = selectedOption == 4
                                        exportDatabase.launch()
                                    }
                                    3 -> exportProfileStateDialog.export()
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
                    
                    // The credential toggles belong to the settings CSV, which options
                    // Settings / Database + Settings / All all export.
                    AnimatedVisibility(visible = selectedOption == index && (index == 1 || index == 2 || index == 4)) {
                        Column(modifier = Modifier.padding(start = 44.dp, bottom = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(uiRoundnessShape())
                                    .clickable { includeYtbCredentials = !includeYtbCredentials }
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(if (includeYtbCredentials) R.drawable.checked_filled else R.drawable.unchecked_outline),
                                    contentDescription = null,
                                    tint = if (includeYtbCredentials) colorPalette().text else colorPalette().textSecondary,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                )
                                Text(
                                    stringResource(R.string.backup_include_youtube_credentials),
                                    style = typography().xxs,
                                    color = colorPalette().text,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .weight(1f)
                                        .basicMarquee(iterations = Int.MAX_VALUE)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(uiRoundnessShape())
                                    .clickable { includeDiscordCredentials = !includeDiscordCredentials }
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(if (includeDiscordCredentials) R.drawable.checked_filled else R.drawable.unchecked_outline),
                                    contentDescription = null,
                                    tint = if (includeDiscordCredentials) colorPalette().text else colorPalette().textSecondary,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                )
                                Text(
                                    stringResource(R.string.backup_include_discord_credentials),
                                    style = typography().xxs,
                                    color = colorPalette().text,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .weight(1f)
                                        .basicMarquee(iterations = Int.MAX_VALUE)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(uiRoundnessShape())
                                    .clickable { includeLastfmCredentials = !includeLastfmCredentials }
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(if (includeLastfmCredentials) R.drawable.checked_filled else R.drawable.unchecked_outline),
                                    contentDescription = null,
                                    tint = if (includeLastfmCredentials) colorPalette().text else colorPalette().textSecondary,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                )
                                Text(
                                    stringResource(R.string.backup_include_lastfm_credentials),
                                    style = typography().xxs,
                                    color = colorPalette().text,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .weight(1f)
                                        .basicMarquee(iterations = Int.MAX_VALUE)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(uiRoundnessShape())
                                    .clickable { includeProxyCredentials = !includeProxyCredentials }
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(if (includeProxyCredentials) R.drawable.checked_filled else R.drawable.unchecked_outline),
                                    contentDescription = null,
                                    tint = if (includeProxyCredentials) colorPalette().text else colorPalette().textSecondary,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                )
                                Text(
                                    stringResource(R.string.backup_include_proxy_password),
                                    style = typography().xxs,
                                    color = colorPalette().text,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .weight(1f)
                                        .basicMarquee(iterations = Int.MAX_VALUE)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
