package app.n_zik.android.components.settings.network

import android.content.Context
import androidx.core.content.edit
import app.n_zik.android.enums.AudioQualityFormat
import app.n_zik.android.enums.ImageQualityFormat
import app.n_zik.android.utils.audioQualityFormatKey
import app.n_zik.android.utils.autoDownloadSongKey
import app.n_zik.android.utils.autoDownloadSongWhenAlbumBookmarkedKey
import app.n_zik.android.utils.autoDownloadSongWhenLikedKey
import app.n_zik.android.utils.imageQualityFormatKey
import app.n_zik.android.utils.isConnectionMeteredEnabledKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.putEnum
import app.n_zik.android.utils.streamClientAndroidVrEnabledKey
import app.n_zik.android.utils.streamClientRestartNeededKey
import app.n_zik.android.utils.streamClientWebRemixEnabledKey
import app.n_zik.android.bridge.AutoStopSettings
import app.n_zik.android.bridge.BridgeServerController
import app.n_zik.android.components.dialog.settings.PreferredStreamClientDialog
import app.n_zik.android.components.dialog.settings.StreamClientsSettingsDialog
import app.n_zik.android.core.network.fullOfflineModeKey
import app.n_zik.android.enums.DownloadQualityFormat
import app.n_zik.android.enums.downloadQualityFormatKey

internal fun applyNetworkDefaults(context: Context) {
    BridgeServerController.setAutoStopNoClient(context, AutoStopSettings.DEFAULT_NO_CLIENT_MINUTES)
    BridgeServerController.setAutoStopInactivity(context, AutoStopSettings.DEFAULT_INACTIVITY_MINUTES)
    PreferredStreamClientDialog.reset(context)
    StreamClientsSettingsDialog.reset(context)
    context.preferences.edit {
        putBoolean(isConnectionMeteredEnabledKey, false)
        putBoolean(autoDownloadSongKey, false)
        putBoolean(autoDownloadSongWhenLikedKey, false)
        putBoolean(autoDownloadSongWhenAlbumBookmarkedKey, false)
        putEnum(audioQualityFormatKey, AudioQualityFormat.Auto)
        putEnum(downloadQualityFormatKey, DownloadQualityFormat.Auto)
        putEnum(imageQualityFormatKey, ImageQualityFormat.Auto)
        putBoolean(fullOfflineModeKey, false)
        putBoolean(streamClientWebRemixEnabledKey, true)
        putBoolean(streamClientAndroidVrEnabledKey, true)
        putBoolean(streamClientRestartNeededKey, false)
    }
}
