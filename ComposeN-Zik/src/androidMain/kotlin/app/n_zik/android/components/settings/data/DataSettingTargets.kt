package app.n_zik.android.components.settings.data

import app.n_zik.android.core.coil.PROFILE_IMAGE_CACHE_CUSTOM_MB
import app.n_zik.android.utils.coilDiskCacheMaxSizeKey
import app.n_zik.android.utils.exoPlayerCacheLocationKey
import app.n_zik.android.utils.exoPlayerCustomCacheKey
import app.n_zik.android.utils.exoPlayerDiskCacheMaxSizeKey
import app.n_zik.android.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.n_zik.android.utils.pauseListenHistoryKey
import app.n_zik.android.utils.pauseSearchHistoryKey

/** Stable local card, preference and action targets for the Data page. */
internal object DataSettingTargets {
    const val HISTORY_FOCUS = "history"
    val cards = listOf("data.cache", "data.backup", "data.auto-backup", "data.history", "data.reset")
    val preferenceKeys = listOf(coilDiskCacheMaxSizeKey, exoPlayerDiskCacheMaxSizeKey, exoPlayerDiskDownloadCacheMaxSizeKey, exoPlayerCacheLocationKey, exoPlayerCustomCacheKey, PROFILE_IMAGE_CACHE_CUSTOM_MB, pauseSearchHistoryKey, pauseListenHistoryKey)
    val actions = listOf("data.cache.clear-images", "data.cache.clear-songs", "data.cache.clear-downloads", "data.backup.export", "data.backup.import", "data.backup.migrate", "data.history.open", "data.history.clear-search", "data.history.clear-listen", "data.reset.apply")
}
