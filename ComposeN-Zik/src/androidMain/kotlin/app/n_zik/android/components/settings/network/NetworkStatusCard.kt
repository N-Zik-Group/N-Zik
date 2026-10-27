package app.n_zik.android.components.settings.network

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.it.fast4x.rimusic.utils.imageQualityFormatKey
import app.it.fast4x.rimusic.enums.ImageQualityFormat
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.n_zik.android.enums.DownloadQualityFormat
import app.n_zik.android.enums.downloadQualityFormatKey
import app.it.fast4x.rimusic.utils.audioQualityFormatKey
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.OtherInfoSettingsEntry
import androidx.compose.runtime.getValue

@Composable
internal fun automaticQualityStatusText(networkQuality: ImageCacheFactory.NetworkQuality): String {
    val detectedText = stringResource(when (networkQuality) {
        ImageCacheFactory.NetworkQuality.LOW -> R.string.network_quality_low
        ImageCacheFactory.NetworkQuality.MEDIUM -> R.string.network_quality_medium
        ImageCacheFactory.NetworkQuality.HIGH -> R.string.network_quality_high
    })
    return stringResource(R.string.audio_quality_auto_fmt, detectedText)
}

@Composable
internal fun NetworkStatusCard(
    networkQuality: ImageCacheFactory.NetworkQuality,
    titleRes: Int = R.string.network_status,
    iconRes: Int = R.drawable.network,
) {
    val imageQualityFormat by rememberPreference(imageQualityFormatKey, ImageQualityFormat.Auto)
    val audioQualityFormat by rememberPreference(audioQualityFormatKey, AudioQualityFormat.Auto)
    val downloadQualityFormat by rememberPreference(downloadQualityFormatKey, DownloadQualityFormat.Auto)

    SettingsSectionCard(
        title = stringResource(titleRes),
        icon = iconRes,
        content = {
            val detectedText = when(networkQuality) {
                ImageCacheFactory.NetworkQuality.LOW -> stringResource(R.string.network_quality_low)
                ImageCacheFactory.NetworkQuality.MEDIUM -> stringResource(R.string.network_quality_medium)
                ImageCacheFactory.NetworkQuality.HIGH -> stringResource(R.string.network_quality_high)
            }

            val imageStatusText = if (imageQualityFormat == ImageQualityFormat.Auto) {
                stringResource(R.string.audio_quality_auto_fmt, detectedText)
            } else {
                when(imageQualityFormat) {
                    ImageQualityFormat.High -> stringResource(R.string.audio_quality_format_high)
                    ImageQualityFormat.Medium -> stringResource(R.string.audio_quality_format_medium)
                    ImageQualityFormat.Low -> stringResource(R.string.audio_quality_format_low)
                    else -> detectedText
                }
            }

            OtherInfoSettingsEntry(
                modifier = Modifier.settingsSearchTarget("network.network_status.image_quality_format"),
                title = stringResource(R.string.image_quality_format),
                text = stringResource(R.string.settings_desc_network_network_status_image_quality_format),
                selectedValueText = imageStatusText,
                icon = R.drawable.image
            )

            val audioStatusText = if (audioQualityFormat == AudioQualityFormat.Auto) {
                stringResource(R.string.audio_quality_auto_fmt, detectedText)
            } else {
                when(audioQualityFormat) {
                    AudioQualityFormat.High -> stringResource(R.string.audio_quality_format_high)
                    AudioQualityFormat.Low -> stringResource(R.string.audio_quality_format_low)
                    else -> detectedText
                }
            }

            OtherInfoSettingsEntry(
                modifier = Modifier.settingsSearchTarget("network.network_status.audio_quality_format"),
                title = stringResource(R.string.audio_quality_format),
                text = stringResource(R.string.settings_desc_network_network_status_audio_quality_format),
                selectedValueText = audioStatusText,
                icon = R.drawable.audio_quality
            )

            val downloadStatusText = if (downloadQualityFormat == DownloadQualityFormat.Auto) {
                stringResource(R.string.audio_quality_auto_fmt, detectedText)
            } else {
                when(downloadQualityFormat) {
                    DownloadQualityFormat.High -> stringResource(R.string.audio_quality_format_high)
                    DownloadQualityFormat.Low -> stringResource(R.string.audio_quality_format_low)
                    else -> detectedText
                }
            }

            OtherInfoSettingsEntry(
                modifier = Modifier.settingsSearchTarget("network.network_status.download_quality_format"),
                title = stringResource(R.string.download_quality_format),
                text = stringResource(R.string.settings_desc_network_network_status_download_quality_format),
                selectedValueText = downloadStatusText,
                icon = R.drawable.download
            )
        }
    )

}
