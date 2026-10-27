package app.n_zik.android.components.settings.network

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.components.settings.DownloadQualityEntry
import app.n_zik.android.components.settings.UpdateDownloadsButton
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.core.coil.ImageCacheFactory

@UnstableApi
@Composable
internal fun NetworkDownloadQualityCard(
    query: String,
    networkQuality: ImageCacheFactory.NetworkQuality,
    onRestartRequired: (Boolean) -> Unit,
) {
    SettingsSectionCard(
        title = stringResource(R.string.settings_download_quality),
        icon = R.drawable.network,
        content = {
            if (query.isBlank() || stringResource(R.string.download_quality_format).contains(query, true) || stringResource(R.string.update_downloads).contains(query, true)) {
                DownloadQualityEntry(automaticQualityText = automaticQualityStatusText(networkQuality), onQualityChanged = { onRestartRequired(true) })
                UpdateDownloadsButton()
            }
        },
    )
}
