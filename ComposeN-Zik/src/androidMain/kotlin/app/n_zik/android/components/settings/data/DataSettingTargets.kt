package app.n_zik.android.components.settings.data

import app.it.fast4x.rimusic.utils.coilCustomDiskCacheKey
import app.it.fast4x.rimusic.utils.coilDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerCacheLocationKey
import app.it.fast4x.rimusic.utils.exoPlayerCustomCacheKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.pauseListenHistoryKey
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey

/** Stable local card, preference and action targets for the Data page. */
internal object DataSettingTargets {
    const val HISTORY_FOCUS = "history"
    val cards = listOf("data.cache", "data.backup", "data.auto-backup", "data.history", "data.reset")
    val preferenceKeys = listOf(coilDiskCacheMaxSizeKey, exoPlayerDiskCacheMaxSizeKey, exoPlayerDiskDownloadCacheMaxSizeKey, exoPlayerCacheLocationKey, exoPlayerCustomCacheKey, coilCustomDiskCacheKey, pauseSearchHistoryKey, pauseListenHistoryKey)
    val actions = listOf("data.cache.clear-images", "data.cache.clear-songs", "data.cache.clear-downloads", "data.backup.export", "data.backup.import", "data.backup.migrate", "data.history.open", "data.history.clear-search", "data.history.clear-listen", "data.reset.apply")
}
