package app.n_zik.android.components.settings.data

import android.content.Context
import androidx.core.content.edit
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
import app.it.fast4x.rimusic.utils.pauseListenHistoryKey
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.putEnum

/** Restores the original eight Data defaults once for the active profile. */
internal fun applyDataDefaults(context: Context) {
    context.preferences.edit {
        putEnum(coilDiskCacheMaxSizeKey, CoilDiskCacheMaxSize.`128MB`)
        putEnum(exoPlayerDiskCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`2GB`)
        putEnum(exoPlayerDiskDownloadCacheMaxSizeKey, ExoPlayerDiskDownloadCacheMaxSize.`2GB`)
        putEnum(exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System)
        putInt(exoPlayerCustomCacheKey, 32)
        putInt(coilCustomDiskCacheKey, 32)
        putBoolean(pauseSearchHistoryKey, false)
        putBoolean(pauseListenHistoryKey, false)
    }
}
