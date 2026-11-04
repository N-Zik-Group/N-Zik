package app.n_zik.android.components.settings.common

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.ui.styling.primaryButton
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.restartActivityKey
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassCustomChip
import app.n_zik.android.typography

@OptIn(UnstableApi::class)
@Composable
internal fun RestartPlayerService(restartService: Boolean, onRestart: () -> Unit) {
    val binder = LocalPlayerServiceBinder.current
    SettingsRestartAction(restartService) {
        binder?.restartForegroundOrStop()
        onRestart()
        Toaster.done()
    }
}

@Composable
internal fun RestartActivity(restart: Boolean, onRestart: () -> Unit) {
    var restartRequested by rememberPreference(restartActivityKey, false)
    SettingsRestartAction(restart) {
        restartRequested = !restartRequested
        onRestart()
        Toaster.done()
    }
}

@Composable
private fun SettingsRestartAction(visible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(visible = visible) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingsDescription(
                text = stringResource(R.string.player_restart_required_message),
                important = true,
                modifier = Modifier.weight(2f),
            )
            GlassCustomChip(
                color = colorPalette().primaryButton,
                onClick = onClick,
                modifier = Modifier.weight(1f).padding(end = 24.dp),
            ) { button ->
                BasicText(
                    text = stringResource(R.string.settings_player_action_restart_service),
                    style = typography().xxs.medium,
                    modifier = button.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
