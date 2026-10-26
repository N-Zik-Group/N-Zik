package app.n_zik.android.components.settings

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.R
import app.n_zik.android.components.extensions.ExtensionsSheet

/**
 * Extensions entry in the Misc settings section (spec-ffmpeg-extension-module),
 * same shape as the Maintenance card: a compact card with the module description
 * and a generic "Open" entry that opens the Extensions sheet. The same sheet as
 * the burger item is composed here so the card is self-contained.
 */
@Composable
fun ExtensionsSettingsCard(modifier: Modifier = Modifier) {
    var showSheet by remember { mutableStateOf(false) }

    SettingsSectionCard(
        title = stringResource(R.string.modules_title),
        icon = R.drawable.extension,
        modifier = modifier,
        content = {
            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("embedded.extensions.module_open"),
                title = stringResource(R.string.module_open),
                text = stringResource(R.string.module_open_desc),
                icon = R.drawable.chevron_forward,
                onClick = { showSheet = true },
            )
            ExtensionsSheet(
                showSheet = showSheet,
                onDismissRequest = { showSheet = false },
            )
        },
    )
}
