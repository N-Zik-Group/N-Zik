package app.n_zik.android.components.settings.general

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.resumePlaybackOnStartKey
import app.it.fast4x.rimusic.utils.resumePlaybackWhenDeviceConnectedKey
import app.it.fast4x.rimusic.utils.skipMediaOnErrorKey
import app.it.fast4x.rimusic.utils.skipSilenceKey
import app.it.fast4x.rimusic.utils.useVolumeKeysToChangeSongKey
import app.it.fast4x.rimusic.utils.volumeBoostLevelKey
import app.it.fast4x.rimusic.utils.volumeNormalizationKey
import app.n_zik.android.components.dialog.settings.AppShortcutsSettingsDialog

/** Restores every General-tab pref to its default value, then resets the app shortcuts. */
@Composable
fun DefaultGeneralSettings(context: Context) {
    var languageApp by rememberPreference(languageAppKey, Languages.System)
    languageApp = Languages.System
    var exoPlayerMinTimeForEvent by rememberPreference(exoPlayerMinTimeForEventKey, ExoPlayerMinTimeForEvent.`20s`)
    exoPlayerMinTimeForEvent = ExoPlayerMinTimeForEvent.`20s`
    var persistentQueue by rememberPreference(persistentQueueKey, false)
    persistentQueue = false
    var resumePlaybackOnStart by rememberPreference(resumePlaybackOnStartKey, false)
    resumePlaybackOnStart = false
    var closebackgroundPlayer by rememberPreference(closebackgroundPlayerKey, false)
    closebackgroundPlayer = false
    var closeWithBackButton by rememberPreference(closeWithBackButtonKey, true)
    closeWithBackButton = true
    var resumePlaybackWhenDeviceConnected by rememberPreference(resumePlaybackWhenDeviceConnectedKey, false)
    resumePlaybackWhenDeviceConnected = false
    var skipSilence by rememberPreference(skipSilenceKey, false)
    skipSilence = false
    var skipMediaOnError by rememberPreference(skipMediaOnErrorKey, false)
    skipMediaOnError = false
    var volumeNormalization by rememberPreference(volumeNormalizationKey, false)
    volumeNormalization = false
    var volumeBoostLevel by rememberPreference(volumeBoostLevelKey, 0f)
    volumeBoostLevel = 0f
    var keepPlayerMinimized by rememberPreference(keepPlayerMinimizedKey, true)
    keepPlayerMinimized = true
    var disableClosingPlayerSwipingDown by rememberPreference(disableClosingPlayerSwipingDownKey, false)
    disableClosingPlayerSwipingDown = false
    var pauseBetweenSongs by rememberPreference(pauseBetweenSongsKey, PauseBetweenSongs.`0`)
    pauseBetweenSongs = PauseBetweenSongs.`0`
    var maxSongsInQueue by rememberPreference(maxSongsInQueueKey, MaxSongs.Unlimited)
    maxSongsInQueue = MaxSongs.Unlimited
    var maxSongsInQueueAndroidAuto by rememberPreference(maxSongsInQueueAndroidAutoKey, MaxSongs.Unlimited)
    maxSongsInQueueAndroidAuto = MaxSongs.Unlimited
    var crossfadeEnabled by rememberPreference(crossfadeEnabledKey, false)
    crossfadeEnabled = false
    var crossfadeDuration by rememberPreference(crossfadeDurationKey, 3000)
    crossfadeDuration = 3000
    var crossfadeGapless by rememberPreference(crossfadeGaplessKey, false)
    crossfadeGapless = false
    var useVolumeKeysToChangeSong by rememberPreference(useVolumeKeysToChangeSongKey, false)
    useVolumeKeysToChangeSong = false
    var excludeSongWithDurationLimit by rememberPreference(excludeSongsWithDurationLimitKey, DurationInMinutes.Disabled)
    excludeSongWithDurationLimit = DurationInMinutes.Disabled
    var playlistindicator by rememberPreference(playlistindicatorKey, false)
    playlistindicator = false
    var nowPlayingIndicator by rememberPreference(nowPlayingIndicatorKey, MusicAnimationType.Bubbles)
    nowPlayingIndicator = MusicAnimationType.Bubbles
    var discoverIsEnabled by rememberPreference(discoverKey, false)
    discoverIsEnabled = false
    var isPauseOnVolumeZeroEnabled by rememberPreference(isPauseOnVolumeZeroEnabledKey, false)
    isPauseOnVolumeZeroEnabled = false
    var pauseOnHeadphoneDisconnect by rememberPreference(pauseOnHeadphoneDisconnectKey, true)
    var minimumSilenceDuration by rememberPreference(minimumSilenceDurationKey, 2_000_000L)
    minimumSilenceDuration = 2_000_000L
    var loudnessBaseGain by rememberPreference(loudnessBaseGainKey, 5.00f)
    loudnessBaseGain = 5.00f
    var autoLoadSongsInQueue by rememberPreference(autoLoadSongsInQueueKey, true)
    autoLoadSongsInQueue = true
    var bassboostEnabled by rememberPreference(bassboostEnabledKey, false)
    bassboostEnabled = false
    var bassboostLevel by rememberPreference(bassboostLevelKey, 0f)
    bassboostLevel = 0f
    var audioReverb by rememberPreference(audioReverbPresetKey, PresetsReverb.NONE)
    audioReverb = PresetsReverb.NONE
    var audioFocusEnabled by rememberPreference(handleAudioFocusEnabledKey, true)
    audioFocusEnabled = true
    var enablePictureInPicture by rememberPreference(enablePictureInPictureKey, false)
    enablePictureInPicture = false
    var enablePictureInPictureAuto by rememberPreference(enablePictureInPictureAutoKey, false)
    enablePictureInPictureAuto = false
    var pipModule by rememberPreference(pipModuleKey, PipModule.Cover)
    pipModule = PipModule.Cover
    var jumpPrevious by rememberPreference(jumpPreviousKey, "3")
    jumpPrevious = "3"
    AppShortcutsSettingsDialog.reset(context)
}
