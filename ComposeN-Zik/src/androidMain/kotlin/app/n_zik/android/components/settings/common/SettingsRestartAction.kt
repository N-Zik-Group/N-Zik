package app.n_zik.android.components.settings.common

import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.ui.styling.primaryButton
import app.n_zik.android.utils.medium
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.restartActivityKey
import app.n_zik.android.utils.streamClientRestartNeededKey
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassCustomChip
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.typography

internal typealias SettingsPlayerRestart = (PlayerServiceModern.Binder, (Boolean) -> Unit) -> Unit
internal val LocalSettingsPlayerRestart = staticCompositionLocalOf<SettingsPlayerRestart?> { null }
internal val LocalSettingsPlayerRestartState = staticCompositionLocalOf<SettingsPlayerRestartState?> { null }

internal class SettingsPlayerRestartState(initialGeneration: Long = 0L) {
    class Request internal constructor(internal val generation: Long)

    private var generation by mutableLongStateOf(initialGeneration)
    private var activeRequest by mutableStateOf<Request?>(null)
    val pending: Boolean get() = generation != 0L
    val restarting: Boolean get() = activeRequest != null

    fun requireRestart() {
        generation++
    }

    fun begin(): Request? {
        if (restarting) return null
        return Request(generation).also { activeRequest = it }
    }

    /** A change made while the service starts remains pending for the next restart. */
    fun complete(request: Request, ready: Boolean): Boolean {
        if (activeRequest !== request) return false
        activeRequest = null
        if (!ready || generation != request.generation) return false
        generation = 0L
        return true
    }

    companion object {
        fun saver(profileId: String): Saver<SettingsPlayerRestartState, Any> = listSaver(
            save = { listOf(profileId, it.generation) },
            restore = { values ->
                (values.getOrNull(1) as? Long)
                    ?.takeIf { values.firstOrNull() == profileId }
                    ?.let(::SettingsPlayerRestartState)
            },
        )
    }
}

@Composable
internal fun settingsPlayerRestartRequest(): () -> Unit {
    val state = LocalSettingsPlayerRestartState.current
    return { state?.requireRestart() }
}

@OptIn(UnstableApi::class)
@Composable
internal fun RestartPlayerService() {
    val state = LocalSettingsPlayerRestartState.current ?: return
    val binder = LocalPlayerServiceBinder.current
    val restartPlayer = LocalSettingsPlayerRestart.current
    val context = LocalContext.current
    val streamRestartPending by rememberPreference(streamClientRestartNeededKey, false)
    val available = binder != null && restartPlayer != null
    SettingsRestartAction(
        visible = state.pending || streamRestartPending,
        description = if (!available && !state.restarting) R.string.player_restart_service_unavailable_message
        else R.string.player_restart_required_message,
        enabled = available && !state.restarting,
    ) {
        val connectedBinder = binder ?: return@SettingsRestartAction
        val requestRestart = restartPlayer ?: return@SettingsRestartAction
        val preferences = context.preferences
        val hadStreamRestart = preferences.getBoolean(streamClientRestartNeededKey, false)
        if (!state.pending && !hadStreamRestart) return@SettingsRestartAction
        val request = state.begin() ?: return@SettingsRestartAction
        requestRestart(connectedBinder) { restarted ->
            val applied = state.complete(request, ready = restarted)
            if (restarted) {
                if (applied && hadStreamRestart) {
                    preferences.edit().putBoolean(streamClientRestartNeededKey, false).apply()
                }
                Toaster.done()
            } else {
                Toaster.e(R.string.common_error_title)
            }
        }
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
    SettingsEntryVisibility(visible = visible) {
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
                enabled = enabled && visible,
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
