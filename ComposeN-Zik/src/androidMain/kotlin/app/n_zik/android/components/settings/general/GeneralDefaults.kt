package app.n_zik.android.components.settings.general

import android.content.Context
import app.n_zik.android.enums.DislikeMode
import app.n_zik.android.enums.DurationInMinutes
import app.n_zik.android.enums.ExoPlayerMinTimeForEvent
import app.n_zik.android.enums.Languages
import app.n_zik.android.enums.MaxSongs
import app.n_zik.android.enums.MusicAnimationType
import app.n_zik.android.enums.PauseBetweenSongs
import app.n_zik.android.enums.PipModule
import app.n_zik.android.enums.PresetsReverb
import app.n_zik.android.utils.audioReverbPresetKey
import app.n_zik.android.utils.autoLoadSongsInQueueKey
import app.n_zik.android.utils.bassboostEnabledKey
import app.n_zik.android.utils.bassboostLevelKey
import app.n_zik.android.utils.closeWithBackButtonKey
import app.n_zik.android.utils.closebackgroundPlayerKey
import app.n_zik.android.utils.crossfadeDurationKey
import app.n_zik.android.utils.crossfadeEnabledKey
import app.n_zik.android.utils.crossfadeGaplessKey
import app.n_zik.android.utils.disableClosingPlayerSwipingDownKey
import app.n_zik.android.utils.discoverKey
import app.n_zik.android.utils.enablePictureInPictureAutoKey
import app.n_zik.android.utils.enablePictureInPictureKey
import app.n_zik.android.utils.excludeDislikedAlbumsKey
import app.n_zik.android.utils.excludeDislikedArtistsKey
import app.n_zik.android.utils.excludeDislikedSongsKey
import app.n_zik.android.utils.excludeSongsWithDurationLimitKey
import app.n_zik.android.utils.exoPlayerMinTimeForEventKey
import app.n_zik.android.utils.handleAudioFocusEnabledKey
import app.n_zik.android.utils.isPauseOnVolumeZeroEnabledKey
import app.n_zik.android.utils.jumpPreviousKey
import app.n_zik.android.utils.keepPlayerMinimizedKey
import app.n_zik.android.utils.languageAppKey
import app.n_zik.android.utils.loudnessBaseGainKey
import app.n_zik.android.utils.maxSongsInQueueAndroidAutoKey
import app.n_zik.android.utils.maxSongsInQueueKey
import app.n_zik.android.utils.minimumSilenceDurationKey
import app.n_zik.android.utils.nowPlayingIndicatorKey
import app.n_zik.android.utils.pauseBetweenSongsKey
import app.n_zik.android.utils.pauseOnHeadphoneDisconnectKey
import app.n_zik.android.utils.persistentQueueKey
import app.n_zik.android.utils.pipModuleKey
import app.n_zik.android.utils.playlistindicatorKey
import app.n_zik.android.utils.resumePlaybackOnStartKey
import app.n_zik.android.utils.resumePlaybackWhenDeviceConnectedKey
import app.n_zik.android.utils.skipMediaOnErrorKey
import app.n_zik.android.utils.skipSilenceKey
import app.n_zik.android.utils.useVolumeKeysToChangeSongKey
import app.n_zik.android.utils.volumeBoostLevelKey
import app.n_zik.android.utils.volumeNormalizationKey
import app.n_zik.android.components.dialog.settings.AppShortcutsSettingsDialog

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.core.os.LocaleListCompat
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.shouldRecreateActivity
import androidx.core.content.edit
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.putEnum

/** Restores General settings without touching saved queue contents or account data. */
internal fun applyGeneralDefaults(context: Context) {
    context.preferences.edit {
        putEnum(languageAppKey, Languages.System)
        putEnum(exoPlayerMinTimeForEventKey, ExoPlayerMinTimeForEvent.`20s`)
        putBoolean(persistentQueueKey, false)
        putBoolean(resumePlaybackOnStartKey, false)
        putBoolean(closebackgroundPlayerKey, false)
        putBoolean(closeWithBackButtonKey, true)
        putBoolean(resumePlaybackWhenDeviceConnectedKey, false)
        putBoolean(skipSilenceKey, false)
        putBoolean(skipMediaOnErrorKey, false)
        putBoolean(volumeNormalizationKey, false)
        putFloat(volumeBoostLevelKey, 0f)
        putBoolean(keepPlayerMinimizedKey, true)
        putBoolean(disableClosingPlayerSwipingDownKey, false)
        putEnum(pauseBetweenSongsKey, PauseBetweenSongs.`0`)
        putEnum(maxSongsInQueueKey, MaxSongs.Unlimited)
        putEnum(maxSongsInQueueAndroidAutoKey, MaxSongs.Unlimited)
        putBoolean(crossfadeEnabledKey, false)
        putInt(crossfadeDurationKey, 3000)
        putBoolean(crossfadeGaplessKey, false)
        putBoolean(useVolumeKeysToChangeSongKey, false)
        putEnum(excludeSongsWithDurationLimitKey, DurationInMinutes.Disabled)
        putBoolean(playlistindicatorKey, false)
        putEnum(nowPlayingIndicatorKey, MusicAnimationType.Bubbles)
        putBoolean(discoverKey, false)
        putBoolean(isPauseOnVolumeZeroEnabledKey, false)
        putBoolean(pauseOnHeadphoneDisconnectKey, true)
        putEnum(excludeDislikedSongsKey, DislikeMode.Enabled)
        putEnum(excludeDislikedArtistsKey, DislikeMode.Enabled)
        putEnum(excludeDislikedAlbumsKey, DislikeMode.Enabled)
        putLong(minimumSilenceDurationKey, 2_000_000L)
        putFloat(loudnessBaseGainKey, 5.00f)
        putBoolean(autoLoadSongsInQueueKey, true)
        putBoolean(bassboostEnabledKey, false)
        putFloat(bassboostLevelKey, 0f)
        putEnum(audioReverbPresetKey, PresetsReverb.NONE)
        putBoolean(handleAudioFocusEnabledKey, true)
        putBoolean(enablePictureInPictureKey, false)
        putBoolean(enablePictureInPictureAutoKey, false)
        putEnum(pipModuleKey, PipModule.Cover)
        putString(jumpPreviousKey, "3")
    }
    if (shouldRecreateActivity(Database.isClosed) && !AppCompatDelegate.getApplicationLocales().isEmpty) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }
    AppShortcutsSettingsDialog.reset(context)
}

/** Compatibility entry for the category reset, retaining its synchronous writes. */
@Composable
fun DefaultGeneralSettings(context: Context) {
    applyGeneralDefaults(context)
}
