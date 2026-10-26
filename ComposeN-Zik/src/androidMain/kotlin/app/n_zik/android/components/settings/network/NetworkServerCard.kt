package app.n_zik.android.components.settings.network

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import app.n_zik.android.R
import app.it.fast4x.rimusic.enums.NavRoutes
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.OtherSettingsEntry

@Composable
internal fun NetworkServerCard(query: String, navController: NavController, modifier: Modifier = Modifier) {

    if (query.isBlank() || stringResource(R.string.bridge_server).contains(query, true)) {
        SettingsSectionCard(
            title = stringResource(R.string.bridge_server),
            icon = R.drawable.server,
            modifier = modifier,
            content = {
                OtherSettingsEntry(
                    title = stringResource(R.string.bridge_server),
                    text = stringResource(R.string.bridge_server_description),
                    icon = R.drawable.devices,
                    onClick = { navController.navigate(NavRoutes.bridgeServer.name) }
                )
            }
        )
    }

}
