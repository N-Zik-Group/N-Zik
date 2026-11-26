package app.n_zik.android.components.settings.network

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.components.settings.DownloadQualityEntry
import app.n_zik.android.components.settings.UpdateDownloadsButton
import app.n_zik.android.components.settings.common.SettingsSectionCard

@UnstableApi
@Composable
internal fun NetworkDownloadQualityCard(
    query: String,
    connectionMetered: Boolean,
    onRestartRequired: (Boolean) -> Unit,
) {
    SettingsSectionCard(
        title = stringResource(R.string.downloads_quality),
        icon = R.drawable.audio_quality,
        content = {
            if (query.isBlank() || stringResource(R.string.downloads_quality_format).contains(query, true) || stringResource(R.string.downloads_action_refresh_quality).contains(query, true)) {
                DownloadQualityEntry(automaticQualityText = automaticAudioQualityStatusText(connectionMetered), onQualityChanged = { onRestartRequired(true) })
                UpdateDownloadsButton()
            }
        },
    )
}
