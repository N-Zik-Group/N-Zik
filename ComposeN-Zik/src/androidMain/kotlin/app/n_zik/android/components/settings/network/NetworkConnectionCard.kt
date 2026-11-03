package app.n_zik.android.components.settings.network

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.it.fast4x.rimusic.utils.isConnectionMeteredEnabledKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun NetworkConnectionCard(query: String) {
    var isConnectionMeteredEnabled by rememberPreference(isConnectionMeteredEnabledKey, false)

    SettingsSectionCard(
        title = stringResource(R.string.network_settings),
        icon = R.drawable.network,
        content = {
            if (query.isBlank() || stringResource(R.string.settings_network_data_saver_label).contains(query, true) || stringResource(R.string.settings_network_data_saver_description).contains(query, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("network.connection_settings.enable_connection_metered"),
                    title = stringResource(R.string.settings_network_data_saver_label),
                    text = stringResource(R.string.settings_network_data_saver_description),
                    isChecked = isConnectionMeteredEnabled,
                    onCheckedChange = {
                        isConnectionMeteredEnabled = it
                    },
                    icon = R.drawable.wifi
                )
            }
        }
    )

}
