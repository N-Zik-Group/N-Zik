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
        title = stringResource(R.string.connection_settings),
        icon = R.drawable.network,
        content = {
            if (query.isBlank() || stringResource(R.string.enable_connection_metered).contains(query, true) || stringResource(R.string.info_enable_connection_metered).contains(query, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("network.connection_settings.enable_connection_metered"),
                    title = stringResource(R.string.enable_connection_metered),
                    text = stringResource(R.string.info_enable_connection_metered),
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
