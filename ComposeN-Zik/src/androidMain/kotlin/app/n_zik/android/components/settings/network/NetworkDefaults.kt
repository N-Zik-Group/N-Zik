package app.n_zik.android.components.settings.network

import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.utils.autoDownloadSongKey
import app.it.fast4x.rimusic.utils.autoDownloadSongWhenAlbumBookmarkedKey
import app.it.fast4x.rimusic.utils.autoDownloadSongWhenLikedKey
import app.it.fast4x.rimusic.utils.isConnectionMeteredEnabledKey
import app.it.fast4x.rimusic.utils.imageQualityFormatKey
import app.it.fast4x.rimusic.enums.ImageQualityFormat
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.it.fast4x.rimusic.utils.streamClientWebRemixEnabledKey
import app.it.fast4x.rimusic.utils.streamClientAndroidVrEnabledKey
import app.it.fast4x.rimusic.utils.streamClientRestartNeededKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.components.dialog.settings.StreamClientsSettingsDialog
import app.n_zik.android.components.dialog.settings.PreferredStreamClientDialog
import app.n_zik.android.enums.DownloadQualityFormat
import app.n_zik.android.enums.downloadQualityFormatKey
import app.it.fast4x.rimusic.utils.audioQualityFormatKey
import android.content.Context
import androidx.core.content.edit
import app.it.fast4x.rimusic.utils.putEnum

internal fun applyNetworkDefaults(context: Context) {
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
        putEnum(navigationBarPositionKey, NavigationBarPosition.BottomFloating)
        putBoolean(streamClientWebRemixEnabledKey, true)
        putBoolean(streamClientAndroidVrEnabledKey, true)
        putBoolean(streamClientRestartNeededKey, false)
    }
}
