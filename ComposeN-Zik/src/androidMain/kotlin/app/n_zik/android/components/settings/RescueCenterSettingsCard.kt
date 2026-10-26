package app.n_zik.android.components.settings

import app.n_zik.android.components.settings.common.settingsSearchTarget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rescue.RescueActivity
import app.n_zik.android.shortcuts.ACTION_RESCUE

/**
 * Rescue Center entry in the Misc settings section (spec-customizable-burger-menu goal B):
 * the same shape as the Maintenance / Extensions cards - a compact card with an entry that
 * launches the Recovery Center (same intent as the burger item and the launcher shortcut).
 */
@Composable
fun RescueCenterSettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    SettingsSectionCard(
        title = stringResource(R.string.rescue_center),
        icon = R.drawable.shortcut_rescue,
        modifier = modifier,
        content = {
            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("embedded.rescue.rescue_center"),
                title = stringResource(R.string.rescue_center),
                text = stringResource(R.string.rescue_center_description),
                icon = R.drawable.chevron_forward,
                onClick = {
                    context.startActivity(
                        Intent(context, RescueActivity::class.java).setAction(ACTION_RESCUE)
                    )
                },
            )
        },
    )
}
