package app.n_zik.android.components.settings.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.core.backup.ui.AutoBackupSettingsBlock

/** Reuses the existing automatic backup controls. */
@Composable
internal fun DataAutoBackupCard() {
    SettingsSectionCard(
        modifier = Modifier.testTag("data.card.auto-backup"),
        title = stringResource(R.string.auto_backup_title),
        icon = R.drawable.history,
        content = {
            AutoBackupSettingsBlock()
        }
    )
}
