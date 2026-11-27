package app.n_zik.android.components.settings.data

import android.content.Context
import androidx.core.content.edit
import app.n_zik.android.enums.CoilDiskCacheMaxSize
import app.n_zik.android.enums.ExoPlayerCacheLocation
import app.n_zik.android.enums.ExoPlayerDiskCacheMaxSize
import app.n_zik.android.enums.ExoPlayerDiskDownloadCacheMaxSize
import app.n_zik.android.core.coil.PROFILE_IMAGE_CACHE_CUSTOM_MB
import app.n_zik.android.utils.coilDiskCacheMaxSizeKey
import app.n_zik.android.utils.exoPlayerCustomCacheKey
import app.n_zik.android.utils.exoPlayerDiskCacheMaxSizeKey
import app.n_zik.android.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.putEnum
import app.n_zik.android.components.image.ORIGINAL_IMAGE_QUALITY_KEY
import app.n_zik.android.components.image.readOriginalImageQuality
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.backup.BackupManager
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.core.ondevice.applyDeviceLibraryDefaults
import app.n_zik.android.core.profiles.ProfileCacheLocationMigration
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.profiles.profileDownloadSpanBytes
import app.n_zik.android.core.profiles.profileStorageIdentity

// Keep the selected destination and its persisted SAF grant when resetting the schedule.
private val autoBackupSettingKeys = setOf(
    BackupManager.PREF_ENABLED, BackupManager.PREF_INTERVAL, BackupManager.PREF_DB_CHANGE, BackupManager.PREF_MAX_BACKUPS,
    BackupManager.PREF_CUSTOM_INTERVAL_VALUE, BackupManager.PREF_CUSTOM_INTERVAL_UNIT,
    BackupManager.PREF_SCHEDULE_HOUR, BackupManager.PREF_SCHEDULE_DAY_OF_WEEK,
    BackupManager.PREF_SCHEDULE_DAY_OF_MONTH, BackupManager.PREF_TARGET,
    BackupManager.PREF_INCLUDE_YTB, BackupManager.PREF_INCLUDE_DISCORD,
    BackupManager.PREF_INCLUDE_LASTFM, BackupManager.PREF_INCLUDE_PROXY, BackupManager.PREF_INCLUDE_GITHUB, BackupManager.PREF_PRE_INSTALL,
)

/** Reports whether restoring the storage location still requires an app restart. */
internal fun hasPendingDataResetCacheMove(context: Context, profileId: String): Boolean {
    val target = profileStorageIdentity(context, profileId) ?: return false
    return ProfileCacheLocationMigration.requested(context, target) == ExoPlayerCacheLocation.System
}

internal data class StorageCacheLimits(
    val streaming: ExoPlayerDiskCacheMaxSize,
    val downloads: ExoPlayerDiskDownloadCacheMaxSize,
    val artwork: CoilDiskCacheMaxSize,
) {
    fun requiresAppRestart(previous: StorageCacheLimits): Boolean =
        downloads != previous.downloads || artwork != previous.artwork
}

internal fun readStorageCacheLimits(context: Context, profileId: String): StorageCacheLimits {
    val prefs = profileDataPrefs(context, profileId)
    return StorageCacheLimits(
        prefs.getEnum(exoPlayerDiskCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`2GB`),
        prefs.getEnum(exoPlayerDiskDownloadCacheMaxSizeKey, ExoPlayerDiskDownloadCacheMaxSize.`2GB`),
        prefs.getEnum(coilDiskCacheMaxSizeKey, CoilDiskCacheMaxSize.`128MB`),
    )
}

/** Restores storage, automatic backup and local music defaults for the active profile. */
internal fun applyDataDefaults(
    context: Context,
    profileId: String = getActiveProfile(context),
    onDownloadLimitRetained: () -> Unit = {},
    cancelAutoBackup: (ProfileStorageIdentity) -> Unit = { target ->
        BackupManager.scheduleBackup(context, BackupManager.INTERVAL_NONE, target)
    },
): Boolean =
    DatabaseRestoreJournal.withProfileStorageMutation(context) {
        if (getActiveProfile(context) != profileId) return@withProfileStorageMutation false
        val target = profileStorageIdentity(context, profileId) ?: return@withProfileStorageMutation false
        val preferences = profileDataPrefs(context, target.id)
        val usedOriginalArtwork = readOriginalImageQuality(preferences)
        val backupWasEnabled = BackupManager.isEnabled(preferences)
        val defaultDownloadLimit = ExoPlayerDiskDownloadCacheMaxSize.`2GB`
        val currentDownloadLimit = preferences.getEnum(exoPlayerDiskDownloadCacheMaxSizeKey, defaultDownloadLimit)
        val canResetDownloadLimit = currentDownloadLimit == defaultDownloadLimit ||
            runCatching { profileDownloadSpanBytes(context, target.id) <= defaultDownloadLimit.bytes }.getOrDefault(false)
        preferences.edit {
            applyDeviceLibraryDefaults()
            autoBackupSettingKeys.forEach(::remove)
            remove(ORIGINAL_IMAGE_QUALITY_KEY)
            putEnum(coilDiskCacheMaxSizeKey, CoilDiskCacheMaxSize.`128MB`)
            putEnum(exoPlayerDiskCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`2GB`)
            if (canResetDownloadLimit) {
                putEnum(exoPlayerDiskDownloadCacheMaxSizeKey, defaultDownloadLimit)
            }
            ProfileCacheLocationMigration.stageRequest(preferences, this, target, ExoPlayerCacheLocation.System)
            putInt(exoPlayerCustomCacheKey, 32)
            putInt(PROFILE_IMAGE_CACHE_CUSTOM_MB, 32)
        }
        if (backupWasEnabled) cancelAutoBackup(target)
        if (usedOriginalArtwork != readOriginalImageQuality(preferences)) ImageCacheFactory.refreshArtworkQuality()
        if (!canResetDownloadLimit) onDownloadLimitRetained()
        true
    }
