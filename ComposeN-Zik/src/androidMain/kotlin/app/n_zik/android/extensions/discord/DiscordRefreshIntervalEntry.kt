package app.n_zik.android.extensions.discord

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.ui.screens.settings.SliderSettingsEntry
import app.it.fast4x.rimusic.utils.rememberEncryptedPreference
import app.n_zik.android.R

/**
 * Discord advanced options: the refresh tick interval while playing (slider in seconds,
 * 2 to 60, default 5 s). Persisted in milliseconds in the encrypted prefs — the presence
 * manager re-reads it at every refresh tick (hot apply), so a change takes effect on the
 * next tick without re-creating the manager.
 */
@Composable
fun DiscordRefreshIntervalEntry(modifier: Modifier = Modifier) {
    var refreshIntervalMs by rememberEncryptedPreference(
        discordAdvancedRefreshIntervalMsKey,
        DiscordAdvancedSettings.DEFAULTS.refreshIntervalMs.toInt(),
    )
    // The slider works in seconds; the stored ms value is clamped first so a corrupted
    // value renders on the range instead of below its start.
    val initialSeconds by remember {
        derivedStateOf {
            (refreshIntervalMs.toLong()
                .coerceIn(
                    DiscordAdvancedSettings.MIN_REFRESH_INTERVAL_MS,
                    DiscordAdvancedSettings.MAX_REFRESH_INTERVAL_MS,
                ) / 1000L).toFloat()
        }
    }
    var secondsUi by remember(initialSeconds) { mutableFloatStateOf(initialSeconds) }

    SliderSettingsEntry(
        title = stringResource(R.string.discord_advanced_refresh_interval),
        text = stringResource(R.string.discord_advanced_refresh_interval_text),
        state = secondsUi,
        range = DiscordAdvancedSettings.MIN_REFRESH_INTERVAL_MS / 1000f
            ..DiscordAdvancedSettings.MAX_REFRESH_INTERVAL_MS / 1000f,
        stepSize = 1f,
        onSlide = { secondsUi = it },
        onSlideComplete = { refreshIntervalMs = secondsUi.toInt() * 1000 },
        toDisplay = { "${it.toInt()} s" },
        isIntegerOnly = true,
        icon = R.drawable.time,
        modifier = modifier,
    )
}
