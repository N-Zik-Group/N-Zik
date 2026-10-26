package app.n_zik.android.components.settings.data

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.components.dialog.backup.ImportBackupDialog
import app.n_zik.android.components.dialog.export.ExportBackupDialog
import app.n_zik.android.components.import.ImportMigration
import app.n_zik.android.components.settings.common.ImportantSettingsDescription
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search

/** Owns backup and migration launchers without changing their contracts. */
@UnstableApi
@Composable
internal fun DataBackupCard(search: Search) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    SettingsSectionCard(
        modifier = Modifier.testTag("data.card.backup"),
        title = stringResource(R.string.title_backup_and_restore),
        icon = R.drawable.server,
        content = {

            if (search.inputValue.isBlank() || stringResource(R.string.export_backup).contains(search.inputValue, true) || stringResource(R.string.export_backup_description).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("data.title_backup_and_restore.export_backup"),
                    title = stringResource(R.string.export_backup),
                    text = stringResource(R.string.export_backup_description),
                    icon = R.drawable.export_outline,
                    onClick = { ExportBackupDialog.showDialog() }
                )
            }
            ImportantSettingsDescription(text = stringResource(
                R.string.personal_preference
            ))

            if (search.inputValue.isBlank() || stringResource(R.string.import_backup).contains(search.inputValue, true) || stringResource(R.string.import_backup_description).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("data.title_backup_and_restore.import_backup"),
                    title = stringResource(R.string.import_backup),
                    text = stringResource(R.string.import_backup_description),
                    icon = R.drawable.import_outline,
                    onClick = { ImportBackupDialog.showDialog() }
                )
            }
            ImportantSettingsDescription(text = stringResource(
                R.string.existing_data_will_be_overwritten,
                context.applicationInfo.nonLocalizedLabel
            ))

            val importMigration = ImportMigration(context, binder)

            if (search.inputValue.isBlank() || stringResource(R.string.title_import_settings_migration).contains(search.inputValue, true) || stringResource(R.string.description_import_settings_migration).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("data.title_backup_and_restore.title_import_settings_migration"),
                    title = stringResource(R.string.title_import_settings_migration),
                    text = stringResource(R.string.description_import_settings_migration),
                    icon = R.drawable.data_migration,
                    onClick = importMigration::onShortClick
                )
            }
        }
    )
}
