package app.n_zik.android.components.settings.data

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.common.RestartPlayerService
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.enums.CoilDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import app.it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerDiskDownloadCacheMaxSize
import app.it.fast4x.rimusic.utils.coilCustomDiskCacheKey
import app.it.fast4x.rimusic.utils.coilDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerCacheLocationKey
import app.it.fast4x.rimusic.utils.exoPlayerCustomCacheKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.InputNumericDialog
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search

/** Owns cache limits and location preferences. */
@UnstableApi
@Composable
internal fun DataCacheCard(
    search: Search,
    restartState: MutableState<Boolean>,
) {
    var restartService by restartState

    var coilDiskCacheMaxSize by rememberPreference(
        coilDiskCacheMaxSizeKey,
        CoilDiskCacheMaxSize.`128MB`
    )
    var exoPlayerDiskCacheMaxSize by rememberPreference(
        exoPlayerDiskCacheMaxSizeKey,
        ExoPlayerDiskCacheMaxSize.`2GB`
    )

    var exoPlayerDiskDownloadCacheMaxSize by rememberPreference(
        exoPlayerDiskDownloadCacheMaxSizeKey,
        ExoPlayerDiskDownloadCacheMaxSize.`2GB`
    )

    var exoPlayerCacheLocation by rememberPreference(
        exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System
    )

    var showExoPlayerCustomCacheDialog by remember { mutableStateOf(false) }
    var exoPlayerCustomCache by rememberPreference(
        exoPlayerCustomCacheKey,32
    )

    var showCoilCustomDiskCacheDialog by remember { mutableStateOf(false) }
    var coilCustomDiskCache by rememberPreference(
        coilCustomDiskCacheKey,32
    )

    SettingsSectionCard(
        modifier = Modifier.testTag("data.card.cache"),
        title = stringResource(R.string.settings_cache_title),
        icon = R.drawable.server,
        description = stringResource(R.string.settings_cache_eviction_description),
        content = {
            var showImageCacheDialog by remember { mutableStateOf(false) }

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("data.cache.image_cache_max_size"),
                title = stringResource(R.string.settings_cache_image_size_limit_label),
                text = stringResource(R.string.cache_image_limit_description),
                selectedValueText = when (coilDiskCacheMaxSize) {
                    CoilDiskCacheMaxSize.Custom -> "${stringResource(R.string.common_option_custom)}: ${coilCustomDiskCache}MB"
                    else -> coilDiskCacheMaxSize.text
                },
                icon = R.drawable.image,
                onClick = { showImageCacheDialog = true },
            )

            DialogPresence(visible = showImageCacheDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.settings_cache_image_size_limit_label),
                    selectedValue = coilDiskCacheMaxSize,
                    values = CoilDiskCacheMaxSize.values().toList(),
                    onValueSelected = {
                        coilDiskCacheMaxSize = it
                        if (coilDiskCacheMaxSize == CoilDiskCacheMaxSize.Custom)
                            showCoilCustomDiskCacheDialog = true
                        restartService = true
                    },
                    valueText = { it.text },
                    onDismiss = { showImageCacheDialog = false }
                )
            }

            RestartPlayerService(restartService, onRestart = { restartService = false })

            DialogPresence(visible = showCoilCustomDiskCacheDialog) {
                InputNumericDialog(
                    title = stringResource(R.string.settings_cache_action_set_custom_size),
                    placeholder = stringResource(R.string.settings_cache_custom_size_input_hint),
                    value = coilCustomDiskCache.toString(),
                    valueMin = "32",
                    valueMax = "10000",
                    onDismiss = { showCoilCustomDiskCacheDialog = false },
                    setValue = {
                        coilCustomDiskCache = it.toInt()
                        showCoilCustomDiskCacheDialog = false
                        restartService = true
                    }
                )
            }

            var showSongCacheDialog by remember { mutableStateOf(false) }

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("data.cache.song_cache_max_size"),
                title = stringResource(R.string.settings_cache_song_size_limit_label),
                text = stringResource(R.string.cache_streaming_limit_description),
                selectedValueText = when (exoPlayerDiskCacheMaxSize) {
                    ExoPlayerDiskCacheMaxSize.Custom -> "${stringResource(R.string.common_option_custom)}: ${exoPlayerCustomCache}MB"
                    ExoPlayerDiskCacheMaxSize.Disabled -> stringResource(R.string.common_action_turn_off)
                    else -> exoPlayerDiskCacheMaxSize.text
                },
                icon = R.drawable.music_file,
                onClick = { showSongCacheDialog = true },
            )

            DialogPresence(visible = showSongCacheDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.settings_cache_song_size_limit_label),
                    selectedValue = exoPlayerDiskCacheMaxSize,
                    values = ExoPlayerDiskCacheMaxSize.values().toList(),
                    onValueSelected = {
                        exoPlayerDiskCacheMaxSize = it
                        if (exoPlayerDiskCacheMaxSize == ExoPlayerDiskCacheMaxSize.Custom)
                            showExoPlayerCustomCacheDialog = true
                        restartService = true
                    },
                    valueText = { it.text },
                    onDismiss = { showSongCacheDialog = false }
                )
            }

            RestartPlayerService(restartService, onRestart = { restartService = false })

            DialogPresence(visible = showExoPlayerCustomCacheDialog) {
                InputNumericDialog(
                    title = stringResource(R.string.settings_cache_action_set_custom_size),
                    placeholder = stringResource(R.string.settings_cache_custom_size_input_hint),
                    value = exoPlayerCustomCache.toString(),
                    valueMin = "32",
                    valueMax = "10000",
                    onDismiss = { showExoPlayerCustomCacheDialog = false },
                    setValue = {
                        exoPlayerCustomCache = it.toInt()
                        showExoPlayerCustomCacheDialog = false
                        restartService = true
                    }
                )
            }

            var showDownloadCacheDialog by remember { mutableStateOf(false) }

            OtherSettingsEntry(
                modifier = Modifier.settingsSearchTarget("data.cache.song_download_max_size"),
                title = stringResource(R.string.settings_downloads_storage_limit_label),
                text = stringResource(R.string.cache_downloads_limit_description),
                selectedValueText = exoPlayerDiskDownloadCacheMaxSize.text,
                icon = R.drawable.download,
                onClick = { showDownloadCacheDialog = true },
            )

            RestartPlayerService(restartService, onRestart = { restartService = false })

            DialogPresence(visible = showDownloadCacheDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.settings_downloads_storage_limit_label),
                    selectedValue = exoPlayerDiskDownloadCacheMaxSize,
                    values = ExoPlayerDiskDownloadCacheMaxSize.values().toList(),
                    onValueSelected = {
                        exoPlayerDiskDownloadCacheMaxSize = it
                        restartService = true
                    },
                    valueText = { it.text },
                    onDismiss = { showDownloadCacheDialog = false }
                )
            }

            var showCacheLocationDialog by remember { mutableStateOf(false) }
            if (search.inputValue.isBlank() || stringResource(R.string.settings_cache_location_label).contains(search.inputValue, true)) {
                OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("data.cache.set_cache_location"),
                    title = stringResource(R.string.settings_cache_location_label),
                    text = stringResource(R.string.cache_location_description),
                    selectedValueText = exoPlayerCacheLocation.text,
                    icon = R.drawable.folder,
                    onClick = { showCacheLocationDialog = true }
                )
            }

            SettingsDescription(stringResource(R.string.settings_cache_private_location_description))

            DialogPresence(visible = showCacheLocationDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.settings_cache_location_label),
                    selectedValue = exoPlayerCacheLocation,
                    values = ExoPlayerCacheLocation.values().toList(),
                    onValueSelected = {
                        exoPlayerCacheLocation = it
                        restartService = true
                    },
                    valueText = { it.text },
                    onDismiss = { showCacheLocationDialog = false }
                )
            }

            RestartPlayerService(restartService, onRestart = { restartService = false })

        }
    )
}
