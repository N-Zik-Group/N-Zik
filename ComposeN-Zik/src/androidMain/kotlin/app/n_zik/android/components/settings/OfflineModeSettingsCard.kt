package app.n_zik.android.components.settings

import app.n_zik.android.components.settings.common.settingsSearchTarget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.core.network.fullOfflineModeKey

/**
 * Offline mode card (Network settings tab, spec-customizable-burger-menu goal B): the live
 * per-profile "full offline mode" toggle the burger menu item also flips (same key, same
 * effect - the flag already feeds the effective online predicate, no new logic).
 */
@Composable
fun OfflineModeSettingsCard(modifier: Modifier = Modifier) {
    var offlineMode by rememberPreference(fullOfflineModeKey, false)

    SettingsSectionCard(
        title = stringResource(R.string.offline_mode),
        icon = R.drawable.airplane,
        modifier = modifier,
        content = {
            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("embedded.offline.offline_mode"),
                title = stringResource(R.string.offline_mode),
                text = stringResource(R.string.offline_mode_description),
                isChecked = offlineMode,
                onCheckedChange = { offlineMode = it },
                icon = R.drawable.airplane
            )
        },
    )
}
