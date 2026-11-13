package app.n_zik.android.components.settings.data

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.dialog.backup.ImportBackupDialog
import app.n_zik.android.components.dialog.export.ExportBackupDialog
import app.n_zik.android.components.settings.common.ImportantSettingsDescription
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search

/** Opens the backup export and restore dialogs. */
@Composable
internal fun DataBackupCard(search: Search) {
    val context = LocalContext.current
    SettingsSectionCard(
        modifier = Modifier.testTag("data.card.backup"),
        title = stringResource(R.string.settings_section_backup_and_restore_title),
        icon = R.drawable.server,
        content = {

            if (search.inputValue.isBlank() || stringResource(R.string.backup_action_import).contains(search.inputValue, true) || stringResource(R.string.backup_import_library_options_description).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("data.title_backup_and_restore.import_backup"),
                    title = stringResource(R.string.backup_action_import),
                    text = stringResource(R.string.backup_import_library_options_description),
                    icon = R.drawable.import_outline,
                    onClick = { ImportBackupDialog.showDialog() }
                )
            }
            ImportantSettingsDescription(text = stringResource(
                R.string.backup_restore_overwrite_and_restart_warning,
                context.applicationInfo.nonLocalizedLabel
            ))

            if (search.inputValue.isBlank() || stringResource(R.string.backup_action_export).contains(search.inputValue, true) || stringResource(R.string.backup_export_library_options_description).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("data.title_backup_and_restore.export_backup"),
                    title = stringResource(R.string.backup_action_export),
                    text = stringResource(R.string.backup_export_library_options_description),
                    icon = R.drawable.export_outline,
                    onClick = { ExportBackupDialog.showDialog() }
                )
            }
            ImportantSettingsDescription(text = stringResource(
                R.string.backup_excluded_preferences_and_cache_description
            ))

        }
    )
}
