package app.n_zik.android.components.settings.network

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
internal fun NetworkStatusCard(networkQuality: ImageCacheFactory.NetworkQuality) {
    val imageQualityFormat by rememberPreference(imageQualityFormatKey, ImageQualityFormat.Auto)
    val audioQualityFormat by rememberPreference(audioQualityFormatKey, AudioQualityFormat.Auto)
    val downloadQualityFormat by rememberPreference(downloadQualityFormatKey, DownloadQualityFormat.Auto)

    SettingsSectionCard(
        title = stringResource(R.string.network_status),
        icon = R.drawable.network,
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
                title = stringResource(R.string.image_quality_format),
                text = imageStatusText,
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
                title = stringResource(R.string.audio_quality_format),
                text = audioStatusText,
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
                title = stringResource(R.string.download_quality_format),
                text = downloadStatusText,
                icon = R.drawable.download
            )
        }
    )

}
