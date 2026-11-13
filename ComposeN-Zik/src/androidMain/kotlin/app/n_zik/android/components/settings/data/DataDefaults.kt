package app.n_zik.android.components.settings.data

import android.content.Context
import androidx.core.content.edit
import app.it.fast4x.rimusic.enums.CoilDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import app.it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerDiskDownloadCacheMaxSize
import app.n_zik.android.core.coil.PROFILE_IMAGE_CACHE_CUSTOM_MB
import app.it.fast4x.rimusic.utils.coilDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerCustomCacheKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.pauseListenHistoryKey
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.putEnum
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.core.profiles.ProfileCacheLocationMigration
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.profiles.profileDownloadSpanBytes
import app.n_zik.android.core.profiles.profileStorageIdentity

/** Restores the original eight Data defaults once for the active profile. */
internal fun applyDataDefaults(context: Context) = DatabaseRestoreJournal.withProfileStorageMutation(context) {
    val target = checkNotNull(profileStorageIdentity(context, getActiveProfile(context)))
    val preferences = profileDataPrefs(context, target.id)
    preferences.edit {
        putEnum(coilDiskCacheMaxSizeKey, CoilDiskCacheMaxSize.`128MB`)
        putEnum(exoPlayerDiskCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`2GB`)
        if (runCatching { profileDownloadSpanBytes(context, target.id) <= ExoPlayerDiskDownloadCacheMaxSize.`2GB`.bytes }
                .getOrDefault(false)
        ) {
            putEnum(exoPlayerDiskDownloadCacheMaxSizeKey, ExoPlayerDiskDownloadCacheMaxSize.`2GB`)
        }
        ProfileCacheLocationMigration.stageRequest(preferences, this, target, ExoPlayerCacheLocation.System)
        putInt(exoPlayerCustomCacheKey, 32)
        putInt(PROFILE_IMAGE_CACHE_CUSTOM_MB, 32)
        putBoolean(pauseSearchHistoryKey, false)
        putBoolean(pauseListenHistoryKey, false)
        SearchHistoryStore.pages.forEach { page -> remove(SearchHistoryStore.enabledKey(page)) }
    }
}
