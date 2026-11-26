package app.n_zik.android.components.settings.common

import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.ui.styling.primaryButton
import app.n_zik.android.utils.medium
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.restartActivityKey
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassCustomChip
import app.n_zik.android.typography

@OptIn(UnstableApi::class)
@Composable
internal fun RestartPlayerService(restartService: Boolean, onRestart: () -> Unit) {
    val binder = LocalPlayerServiceBinder.current
    SettingsRestartAction(
        visible = restartService,
        description = if (binder == null) R.string.player_restart_service_unavailable_message
        else R.string.player_restart_required_message,
        enabled = binder != null,
    ) {
        val connectedBinder = binder ?: return@SettingsRestartAction
        connectedBinder.restartForegroundOrStop()
        onRestart()
        Toaster.done()
    }
}

@Composable
internal fun RestartActivity(restart: Boolean, onRestart: () -> Unit) {
    var restartRequested by rememberPreference(restartActivityKey, false)
    SettingsRestartAction(
        visible = restart,
        description = R.string.settings_restart_required_message,
        action = R.string.settings_app_action_restart,
    ) {
        restartRequested = !restartRequested
        onRestart()
        Toaster.done()
    }
}

@Composable
private fun SettingsRestartAction(
    visible: Boolean,
    @StringRes description: Int = R.string.player_restart_required_message,
    @StringRes action: Int = R.string.settings_player_action_restart_service,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    AnimatedVisibility(visible = visible) {
        val palette = colorPalette()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsDescription(
                text = stringResource(description),
                important = true,
                modifier = Modifier.weight(2f),
            )
            GlassCustomChip(
                color = palette.primaryButton,
                enabled = enabled,
                onClick = onClick,
                modifier = Modifier.weight(1f),
            ) { button ->
                BasicText(
                    text = stringResource(action),
                    style = typography().xxs.medium,
                    modifier = button.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
