package app.n_zik.android.components.settings.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.it.fast4x.rimusic.utils.imageQualityFormatKey
import app.it.fast4x.rimusic.enums.ImageQualityFormat
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.n_zik.android.components.settings.DownloadQualityEntry
import app.n_zik.android.components.settings.UpdateDownloadsButton
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.it.fast4x.rimusic.utils.audioQualityFormatKey
import app.it.fast4x.rimusic.utils.RestartPlayerService
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@UnstableApi
@Composable
internal fun NetworkQualityCard(query: String, restartService: Boolean, audioDialog: MutableState<Boolean>, imageDialog: MutableState<Boolean>, onRestartRequired: (Boolean) -> Unit) {
    var audioQualityFormat by rememberPreference(audioQualityFormatKey, AudioQualityFormat.Auto)
    var imageQualityFormat by rememberPreference(imageQualityFormatKey, ImageQualityFormat.Auto)
    var showAudioQualityDialog by audioDialog
    var showImageQualityDialog by imageDialog

    SettingsSectionCard(
        title = stringResource(R.string.quality),
        icon = R.drawable.audio_quality,
        content = {

            if (query.isBlank() || stringResource(R.string.audio_quality_format).contains(query, true)) {
                OtherSettingsEntry(
                    title = stringResource(R.string.audio_quality_format),
                    text = when (audioQualityFormat) {
                        AudioQualityFormat.Auto -> stringResource(R.string.audio_quality_automatic)
                        AudioQualityFormat.High -> stringResource(R.string.audio_quality_format_high)
                        AudioQualityFormat.Low -> stringResource(R.string.audio_quality_format_low)
                    },
                    icon = R.drawable.speaker,
                    onClick = { showAudioQualityDialog = true }
                )
            }

            if (query.isBlank() || stringResource(R.string.image_quality_format).contains(query, true)) {
                OtherSettingsEntry(
                    title = stringResource(R.string.image_quality_format),
                    text = when (imageQualityFormat) {
                        ImageQualityFormat.Auto -> stringResource(R.string.audio_quality_automatic)
                        ImageQualityFormat.High -> stringResource(R.string.audio_quality_format_high)
                        ImageQualityFormat.Medium -> stringResource(R.string.audio_quality_format_medium)
                        ImageQualityFormat.Low -> stringResource(R.string.audio_quality_format_low)
                    },
                    icon = R.drawable.image,
                    onClick = { showImageQualityDialog = true }
                )
            }

            if (query.isBlank() || stringResource(R.string.download_quality_format).contains(query, true) || stringResource(R.string.update_downloads).contains(query, true)) {
                DownloadQualityEntry(onQualityChanged = { onRestartRequired(true) })
                UpdateDownloadsButton()
            }

            if (query.isBlank() || stringResource(R.string.audio_quality_format).contains(query, true) || stringResource(R.string.download_quality_format).contains(query, true)) {
                RestartPlayerService(restartService, onRestart = { onRestartRequired(false) })
            }
        }
    )

    if (showAudioQualityDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.audio_quality_format),
            values = AudioQualityFormat.values().toList(),
            selectedValue = audioQualityFormat,
            onValueSelected = {
                audioQualityFormat = it
                onRestartRequired(true)
                showAudioQualityDialog = false
            },
            onDismiss = { showAudioQualityDialog = false },
            valueText = {
                when (it) {
                    AudioQualityFormat.Auto -> stringResource(R.string.audio_quality_automatic)
                    AudioQualityFormat.High -> stringResource(R.string.audio_quality_format_high)
                    AudioQualityFormat.Low -> stringResource(R.string.audio_quality_format_low)
                }
            }
        )
    }

    if (showImageQualityDialog) {
        ValueSelectorDialog(
            title = stringResource(R.string.image_quality_format),
            values = ImageQualityFormat.values().toList(),
            selectedValue = imageQualityFormat,
            onValueSelected = {
                imageQualityFormat = it
                showImageQualityDialog = false
            },
            onDismiss = { showImageQualityDialog = false },
            valueText = {
                when (it) {
                    ImageQualityFormat.Auto -> stringResource(R.string.audio_quality_automatic)
                    ImageQualityFormat.High -> stringResource(R.string.audio_quality_format_high)
                    ImageQualityFormat.Medium -> stringResource(R.string.audio_quality_format_medium)
                    ImageQualityFormat.Low -> stringResource(R.string.audio_quality_format_low)
                }
            }
        )
    }

}
