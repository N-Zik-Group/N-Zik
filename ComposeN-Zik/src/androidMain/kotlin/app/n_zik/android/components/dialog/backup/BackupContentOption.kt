package app.n_zik.android.components.dialog.backup

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.semiBold

internal enum class BackupContent(
    @StringRes val labelRes: Int,
    @StringRes val importDescriptionRes: Int,
    @StringRes val exportDescriptionRes: Int,
) {
    Database(
        R.string.backup_target_database_label,
        R.string.backup_import_database_with_covers_description,
        R.string.backup_export_database_with_covers_description,
    ),
    Settings(
        R.string.settings_title,
        R.string.backup_import_settings_description,
        R.string.backup_export_settings_description,
    ),
    Themes(
        R.string.theme_store_results,
        R.string.backup_import_themes_description,
        R.string.backup_export_themes_description,
    ),
    Profiles(
        R.string.profile_title,
        R.string.backup_import_accounts_description,
        R.string.backup_export_accounts_description,
    );

    val flag: Int get() = 1 shl ordinal
    fun isSelected(selection: Int): Boolean = selection and flag != 0

    companion object {
        val all: Int get() = entries.fold(0) { flags, option -> flags or option.flag }
    }
}

@Composable
internal fun BackupContentOption(
    @StringRes label: Int,
    @StringRes description: Int,
    selected: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit,
) {
    val palette = colorPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(uiRoundnessShape())
            .toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (selected) R.drawable.checked_filled else R.drawable.unchecked_outline),
            contentDescription = null,
            tint = if (selected) palette.text else palette.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), style = typography().xs.semiBold, color = palette.text)
            Text(stringResource(description), style = typography().xxs, color = palette.textSecondary)
        }
    }
}

@Composable
internal fun ImportBackupContentOptions(
    selection: Int,
    onSelectionChange: (Int) -> Unit,
    themeContent: @Composable () -> Unit,
) {
    BackupContent.entries.forEach { option ->
        val selected = option.isSelected(selection)
        BackupContentOption(
            label = option.labelRes,
            description = option.importDescriptionRes,
            selected = selected,
            onToggle = { onSelectionChange(selection xor option.flag) },
        )
        if (option == BackupContent.Themes && selected) themeContent()
    }
    BackupContentOption(
        label = R.string.backup_import_all,
        description = R.string.backup_import_select_all_description,
        selected = selection == BackupContent.all,
        onToggle = { onSelectionChange(if (selection == BackupContent.all) 0 else BackupContent.all) },
    )
}
