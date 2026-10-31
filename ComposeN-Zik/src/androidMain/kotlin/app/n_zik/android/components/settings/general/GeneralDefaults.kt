package app.n_zik.android.components.settings.general

import android.content.Context
import app.it.fast4x.rimusic.enums.DislikeMode
import app.it.fast4x.rimusic.enums.DurationInMinutes
import app.it.fast4x.rimusic.enums.ExoPlayerMinTimeForEvent
import app.it.fast4x.rimusic.enums.Languages
import app.it.fast4x.rimusic.enums.MaxSongs
import app.it.fast4x.rimusic.enums.MusicAnimationType
import app.it.fast4x.rimusic.enums.PauseBetweenSongs
import app.it.fast4x.rimusic.enums.PipModule
import app.it.fast4x.rimusic.enums.PresetsReverb
import app.it.fast4x.rimusic.utils.audioReverbPresetKey
import app.it.fast4x.rimusic.utils.autoLoadSongsInQueueKey
import app.it.fast4x.rimusic.utils.bassboostEnabledKey
import app.it.fast4x.rimusic.utils.bassboostLevelKey
import app.it.fast4x.rimusic.utils.closeWithBackButtonKey
import app.it.fast4x.rimusic.utils.closebackgroundPlayerKey
import app.it.fast4x.rimusic.utils.crossfadeDurationKey
import app.it.fast4x.rimusic.utils.crossfadeEnabledKey
import app.it.fast4x.rimusic.utils.crossfadeGaplessKey
import app.it.fast4x.rimusic.utils.disableClosingPlayerSwipingDownKey
import app.it.fast4x.rimusic.utils.discoverKey
import app.it.fast4x.rimusic.utils.enablePictureInPictureAutoKey
import app.it.fast4x.rimusic.utils.enablePictureInPictureKey
import app.it.fast4x.rimusic.utils.excludeDislikedAlbumsKey
import app.it.fast4x.rimusic.utils.excludeDislikedArtistsKey
import app.it.fast4x.rimusic.utils.excludeDislikedSongsKey
import app.it.fast4x.rimusic.utils.excludeSongsWithDurationLimitKey
import app.it.fast4x.rimusic.utils.exoPlayerMinTimeForEventKey
import app.it.fast4x.rimusic.utils.handleAudioFocusEnabledKey
import app.it.fast4x.rimusic.utils.isPauseOnVolumeZeroEnabledKey
import app.it.fast4x.rimusic.utils.jumpPreviousKey
import app.it.fast4x.rimusic.utils.keepPlayerMinimizedKey
import app.it.fast4x.rimusic.utils.languageAppKey
import app.it.fast4x.rimusic.utils.loudnessBaseGainKey
import app.it.fast4x.rimusic.utils.maxSongsInQueueAndroidAutoKey
import app.it.fast4x.rimusic.utils.maxSongsInQueueKey
import app.it.fast4x.rimusic.utils.minimumSilenceDurationKey
import app.it.fast4x.rimusic.utils.nowPlayingIndicatorKey
import app.it.fast4x.rimusic.utils.pauseBetweenSongsKey
import app.it.fast4x.rimusic.utils.pauseOnHeadphoneDisconnectKey
import app.it.fast4x.rimusic.utils.persistentQueueKey
import app.it.fast4x.rimusic.utils.pipModuleKey
import app.it.fast4x.rimusic.utils.playlistindicatorKey
import app.it.fast4x.rimusic.utils.resumePlaybackOnStartKey
import app.it.fast4x.rimusic.utils.resumePlaybackWhenDeviceConnectedKey
import app.it.fast4x.rimusic.utils.skipMediaOnErrorKey
import app.it.fast4x.rimusic.utils.skipSilenceKey
import app.it.fast4x.rimusic.utils.useVolumeKeysToChangeSongKey
import app.it.fast4x.rimusic.utils.volumeBoostLevelKey
import app.it.fast4x.rimusic.utils.volumeNormalizationKey
import app.n_zik.android.components.dialog.settings.AppShortcutsSettingsDialog

import androidx.compose.runtime.Composable
import androidx.core.content.edit
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.putEnum

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
    AppShortcutsSettingsDialog.reset(context)
}

/** Compatibility entry for the category reset, retaining its synchronous writes. */
@Composable
fun DefaultGeneralSettings(context: Context) {
    applyGeneralDefaults(context)
}
