package app.it.fast4x.rimusic.ui.screens.settings

import android.os.Build
import android.text.TextUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.LocalPlayerAwareWindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.sp
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.DurationInMilliseconds
import app.it.fast4x.rimusic.enums.DurationInMinutes
import app.it.fast4x.rimusic.enums.ExoPlayerMinTimeForEvent
import app.it.fast4x.rimusic.enums.Languages
import app.it.fast4x.rimusic.enums.MaxSongs
import app.it.fast4x.rimusic.enums.MusicAnimationType
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.enums.NotificationType
import app.it.fast4x.rimusic.enums.PauseBetweenSongs
import app.it.fast4x.rimusic.enums.PipModule
import app.it.fast4x.rimusic.enums.PresetsReverb
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.RestartActivity
import app.it.fast4x.rimusic.utils.RestartPlayerService
import app.it.fast4x.rimusic.utils.audioReverbPresetKey
import app.it.fast4x.rimusic.utils.autoLoadSongsInQueueKey
import app.it.fast4x.rimusic.utils.bassboostEnabledKey
import app.it.fast4x.rimusic.utils.bassboostLevelKey
import app.it.fast4x.rimusic.utils.closeWithBackButtonKey
import app.it.fast4x.rimusic.utils.closebackgroundPlayerKey
import app.it.fast4x.rimusic.utils.disableClosingPlayerSwipingDownKey
import app.it.fast4x.rimusic.utils.discoverKey
import app.it.fast4x.rimusic.utils.enablePictureInPictureAutoKey
import app.it.fast4x.rimusic.utils.enablePictureInPictureKey
import app.it.fast4x.rimusic.utils.excludeSongsWithDurationLimitKey
import app.it.fast4x.rimusic.utils.exoPlayerMinTimeForEventKey
import app.it.fast4x.rimusic.utils.handleAudioFocusEnabledKey
import app.it.fast4x.rimusic.utils.isAtLeastAndroid12
import app.it.fast4x.rimusic.utils.isAtLeastAndroid6
import app.it.fast4x.rimusic.utils.isPauseOnVolumeZeroEnabledKey
import app.it.fast4x.rimusic.utils.pauseOnHeadphoneDisconnectKey
import app.it.fast4x.rimusic.utils.jumpPreviousKey
import app.it.fast4x.rimusic.utils.keepPlayerMinimizedKey
import app.it.fast4x.rimusic.utils.languageAppKey
import app.it.fast4x.rimusic.utils.loudnessBaseGainKey
import app.it.fast4x.rimusic.utils.maxSongsInQueueKey
import app.it.fast4x.rimusic.utils.maxSongsInQueueAndroidAutoKey
import app.it.fast4x.rimusic.utils.minimumSilenceDurationKey
import app.it.fast4x.rimusic.utils.notificationTypeKey
import app.it.fast4x.rimusic.utils.nowPlayingIndicatorKey
import app.it.fast4x.rimusic.utils.pauseBetweenSongsKey
import app.it.fast4x.rimusic.utils.persistentQueueKey
import app.it.fast4x.rimusic.utils.pipModuleKey
import app.it.fast4x.rimusic.utils.playbackFadeAudioDurationKey
import app.it.fast4x.rimusic.utils.playlistindicatorKey
import app.it.fast4x.rimusic.utils.rememberEqualizerLauncher
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.resumePlaybackOnStartKey
import app.it.fast4x.rimusic.utils.resumePlaybackWhenDeviceConnectedKey
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.skipMediaOnErrorKey
import app.it.fast4x.rimusic.utils.skipSilenceKey
import app.it.fast4x.rimusic.utils.useVolumeKeysToChangeSongKey
import app.it.fast4x.rimusic.utils.volumeNormalizationKey
import app.it.fast4x.rimusic.utils.volumeBoostLevelKey
import app.it.fast4x.rimusic.ui.components.themed.ValueSelectorDialog
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.tab.Search
import app.it.fast4x.rimusic.utils.crossfadeDurationKey
import androidx.compose.animation.core.tween
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import app.n_zik.android.uiRoundnessShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.LaunchedEffect
import app.it.fast4x.rimusic.utils.crossfadeEnabledKey
import app.it.fast4x.rimusic.utils.crossfadeGaplessKey
import androidx.compose.animation.scaleIn
import android.content.Context

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
    var notificationType by rememberPreference(notificationTypeKey, NotificationType.Default)
    notificationType = NotificationType.Default
}

@ExperimentalAnimationApi
@UnstableApi
@Composable
fun GeneralSettings(
    navController: NavController
) {
    val binder = LocalPlayerServiceBinder.current

    var languageApp  by rememberPreference(languageAppKey, Languages.System)
    val systemLocale = LocaleListCompat.getDefault().get(0).toString()

    var exoPlayerMinTimeForEvent by rememberPreference(
        exoPlayerMinTimeForEventKey,
        ExoPlayerMinTimeForEvent.`20s`
    )
    var persistentQueue by rememberPreference(persistentQueueKey, false)
    var resumePlaybackOnStart by rememberPreference(resumePlaybackOnStartKey, false)
    var closebackgroundPlayer by rememberPreference(closebackgroundPlayerKey, false)
    var closeWithBackButton by rememberPreference(closeWithBackButtonKey, true)
    var resumePlaybackWhenDeviceConnected by rememberPreference(
        resumePlaybackWhenDeviceConnectedKey,
        false
    )

    var skipSilence by rememberPreference(skipSilenceKey, false)
    var skipMediaOnError by rememberPreference(skipMediaOnErrorKey, false)
    var volumeNormalization by rememberPreference(volumeNormalizationKey, false)
    var volumeBoostLevel by rememberPreference(volumeBoostLevelKey, 0f)

    var keepPlayerMinimized by rememberPreference(keepPlayerMinimizedKey,   true)

    var disableClosingPlayerSwipingDown by rememberPreference(disableClosingPlayerSwipingDownKey, false)
    var pauseBetweenSongs  by rememberPreference(pauseBetweenSongsKey, PauseBetweenSongs.`0`)
    var maxSongsInQueue  by rememberPreference(maxSongsInQueueKey, MaxSongs.Unlimited)
    var maxSongsInQueueAndroidAuto by rememberPreference(maxSongsInQueueAndroidAutoKey, MaxSongs.Unlimited)
    var crossfadeEnabled by rememberPreference(crossfadeEnabledKey, false)
    var crossfadeDuration by rememberPreference(crossfadeDurationKey, 3000)
    var crossfadeGapless by rememberPreference(crossfadeGaplessKey, false)

    val search = Search()

    var useVolumeKeysToChangeSong by rememberPreference(useVolumeKeysToChangeSongKey, false)
    var excludeSongWithDurationLimit by rememberPreference(excludeSongsWithDurationLimitKey, DurationInMinutes.Disabled)
    var playlistindicator by rememberPreference(playlistindicatorKey, false)
    var nowPlayingIndicator by rememberPreference(nowPlayingIndicatorKey, MusicAnimationType.Bubbles)
    var discoverIsEnabled by rememberPreference(discoverKey, false)
    var isPauseOnVolumeZeroEnabled by rememberPreference(isPauseOnVolumeZeroEnabledKey, false)
    var pauseOnHeadphoneDisconnect by rememberPreference(pauseOnHeadphoneDisconnectKey, true)

    val launchEqualizer by rememberEqualizerLauncher(audioSessionId = { binder?.player?.audioSessionId })

    var minimumSilenceDuration by rememberPreference(minimumSilenceDurationKey, 2_000_000L)

    var restartService by rememberSaveable { mutableStateOf(false) }
    var restartActivity by rememberSaveable { mutableStateOf(false) }

    var loudnessBaseGain by rememberPreference(loudnessBaseGainKey, 5.00f)
    var autoLoadSongsInQueue by rememberPreference(autoLoadSongsInQueueKey, true)

    var bassboostEnabled by rememberPreference(bassboostEnabledKey,false)
    var bassboostLevel by rememberPreference(bassboostLevelKey, 0f)
    var audioReverb by rememberPreference(audioReverbPresetKey,   PresetsReverb.NONE)
    var audioFocusEnabled by rememberPreference(handleAudioFocusEnabledKey, true)

    var enablePictureInPicture by rememberPreference(enablePictureInPictureKey, false)
    var enablePictureInPictureAuto by rememberPreference(enablePictureInPictureAutoKey, false)
    var pipModule by rememberPreference(pipModuleKey, PipModule.Cover)
    var jumpPrevious by rememberPreference(jumpPreviousKey,"3")
    var notificationType by rememberPreference(notificationTypeKey, NotificationType.Default)

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {

        HeaderWithIcon(
            title = stringResource(R.string.tab_general),
            iconId = R.drawable.ic_launcher_monochrome,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {}
        )

                 SettingsDescription(
             text = stringResource(R.string.general_settings_description),
             modifier = Modifier.fillMaxWidth(),
             textAlign = TextAlign.Center
         ) 

        /* Removed Spacer */

        // Search Section
        search.ToolBarButton()
        search.SearchBar( this )

                 // Language Section
         val searchCtx_0 = search.inputValue.isBlank() || stringResource(R.string.languages).contains(search.inputValue, true) || stringResource(R.string.app_language).contains(search.inputValue, true)
         AnimatedVisibility(
             visible = searchCtx_0,
             enter = fadeIn(animationSpec = tween(600)) + scaleIn(
                 animationSpec = tween(600),
                 initialScale = 0.9f
             )
         ) {
             SettingsSectionCard(
                 title = stringResource(R.string.languages),
                 icon = R.drawable.discover,
                 content = {

                                           var showLanguageDialog by remember { mutableStateOf(false) }
                      if (search.inputValue.isBlank() || stringResource(R.string.app_language).contains(search.inputValue,true)) {
                          OtherSettingsEntry(
                              title = stringResource(R.string.app_language),
                              text = languageApp.text,
                              onClick = { showLanguageDialog = true },
                              icon = R.drawable.translate
                          )
                      }
                      
                      if (showLanguageDialog) {
                          ValueSelectorDialog(
                title = stringResource(R.string.app_language)+": $systemLocale",
                selectedValue = languageApp,
                onValueSelected = {
                    languageApp = it
                    RestartAppDialog.showDialog()
                },
                              valueText = { it.text },
                              values = Languages.values().toList(),
                              onDismiss = { showLanguageDialog = false }
                          )
                      }
                 }
             )
         }

         /* Removed Spacer */

         // Notifications Section
         val searchCtx_1 = search.inputValue.isBlank() || stringResource(R.string.notifications).contains(search.inputValue, true) || stringResource(R.string.notification_type).contains(search.inputValue, true) || stringResource(R.string.restarting_rimusic_is_required).contains(search.inputValue, true) || stringResource(R.string.notification_type_info).contains(search.inputValue, true)
         AnimatedVisibility(
             visible = searchCtx_1,
             enter = fadeIn(animationSpec = tween(700)) + scaleIn(
                 animationSpec = tween(700),
                 initialScale = 0.9f
             )
         ) {
                                                       SettingsSectionCard(
                   title = stringResource(R.string.notifications),
                   icon = R.drawable.notification2,
                   content = {
                     var showNotificationTypeDialog by remember { mutableStateOf(false) }
        if (search.inputValue.isBlank() || stringResource(R.string.notification_type).contains(search.inputValue,true)) {
                                                                                                       OtherSettingsEntry(
                               title = stringResource(R.string.notification_type),
                               text = notificationType.textName,
                               onClick = { showNotificationTypeDialog = true },
                               icon = R.drawable.notification1
                           )
                         ImportantSettingsDescription(text = stringResource(R.string.restarting_rimusic_is_required))
                     }
                     
                                           if (showNotificationTypeDialog) {
                          ValueSelectorDialog(
                title = stringResource(R.string.notification_type_info),
                selectedValue = notificationType,
                onValueSelected = {
                    notificationType = it
                                  RestartAppDialog.showDialog()
                              },
                              valueText = { it.textName },
                              values = NotificationType.values().toList(),
                              onDismiss = { showNotificationTypeDialog = false }
                          )
                      }
                 }
             )
         }

         /* Removed Spacer */

         // Playback Section
         val searchCtx_2 = search.inputValue.isBlank() || stringResource(R.string.playback).contains(search.inputValue, true) || stringResource(R.string.jump_previous).contains(search.inputValue, true) || stringResource(R.string.jump_previous_blank).contains(search.inputValue, true) || stringResource(R.string.min_listening_time).contains(search.inputValue, true) || stringResource(R.string.is_min_list_time_for_tips_or_quick_pics).contains(search.inputValue, true) || stringResource(R.string.exclude_songs_with_duration_limit).contains(search.inputValue, true) || stringResource(R.string.vt_disabled).contains(search.inputValue, true) || stringResource(R.string.exclude_songs_with_duration_limit_description).contains(search.inputValue, true) || stringResource(R.string.pause_between_songs).contains(search.inputValue, true) || stringResource(R.string.pause_between_songs_description).contains(search.inputValue, true)
         AnimatedVisibility(
             visible = searchCtx_2,
             enter = fadeIn(animationSpec = tween(800)) + scaleIn(
                 animationSpec = tween(800),
                 initialScale = 0.9f
             )
         ) {
             SettingsSectionCard(
                 title = stringResource(R.string.playback),
                 icon = R.drawable.play_forward,
                 content = {

                                         var showJumpPreviousDialog by remember { mutableStateOf(false) }
        if (search.inputValue.isBlank() || stringResource(R.string.jump_previous).contains(search.inputValue,true)) {
                         OtherSettingsEntry(
                             title = stringResource(R.string.jump_previous),
                text = jumpPrevious,
                             onClick = { showJumpPreviousDialog = true },
                             icon = R.drawable.play_skip_back
                         )
                     }
                     
                     if (showJumpPreviousDialog) {
                         SettingsInputDialog(
                             title = stringResource(R.string.jump_previous_blank),
                             initialValue = jumpPrevious,
                             placeholder = stringResource(R.string.jump_previous_blank),
                             onDismiss = { showJumpPreviousDialog = false },
                             onSetValue = { 
                                 if (TextUtils.isDigitsOnly(it))
                                     jumpPrevious = it
                             }
                         ).apply {
                             showDialog()
                             Render()
                         }
                     }

                                         var showMinListeningTimeDialog by remember { mutableStateOf(false) }
        if (search.inputValue.isBlank() || stringResource(R.string.min_listening_time).contains(search.inputValue,true)) {
                         OtherSettingsEntry(
                             title = stringResource(R.string.min_listening_time),
                             text = when (exoPlayerMinTimeForEvent) {
                                 ExoPlayerMinTimeForEvent.`10s` -> "10s"
                                 ExoPlayerMinTimeForEvent.`15s` -> "15s"
                                 ExoPlayerMinTimeForEvent.`20s` -> "20s"
                                 ExoPlayerMinTimeForEvent.`30s` -> "30s"
                                 ExoPlayerMinTimeForEvent.`40s` -> "40s"
                                 ExoPlayerMinTimeForEvent.`60s` -> "60s"
                             },
                             onClick = { showMinListeningTimeDialog = true },
                             icon = R.drawable.time
                         )
                     }
                     
                                           if (showMinListeningTimeDialog) {
                          ValueSelectorDialog(
                 title = stringResource(R.string.is_min_list_time_for_tips_or_quick_pics),
                 selectedValue = exoPlayerMinTimeForEvent,
                 onValueSelected = { exoPlayerMinTimeForEvent = it },
                 valueText = {
                     when (it) {
                         ExoPlayerMinTimeForEvent.`10s` -> "10s"
                         ExoPlayerMinTimeForEvent.`15s` -> "15s"
                         ExoPlayerMinTimeForEvent.`20s` -> "20s"
                         ExoPlayerMinTimeForEvent.`30s` -> "30s"
                         ExoPlayerMinTimeForEvent.`40s` -> "40s"
                         ExoPlayerMinTimeForEvent.`60s` -> "60s"
                     }
                              },
                              values = ExoPlayerMinTimeForEvent.values().toList(),
                              onDismiss = { showMinListeningTimeDialog = false }
                          )
                      }

                                         var showExcludeSongsDialog by remember { mutableStateOf(false) }
                     if (search.inputValue.isBlank() || stringResource(R.string.exclude_songs_with_duration_limit).contains(search.inputValue,true)) {
                         OtherSettingsEntry(
                             title = stringResource(R.string.exclude_songs_with_duration_limit),
                             text = when (excludeSongWithDurationLimit) {
                                 DurationInMinutes.Disabled -> stringResource(R.string.vt_disabled)
                                 DurationInMinutes.`3` -> "3m"
                                 DurationInMinutes.`5` -> "5m"
                                 DurationInMinutes.`10` -> "10m"
                                 DurationInMinutes.`15` -> "15m"
                                 DurationInMinutes.`20` -> "20m"
                                 DurationInMinutes.`25` -> "25m"
                                 DurationInMinutes.`30` -> "30m"
                                 DurationInMinutes.`60` -> "60m"
                             },
                             onClick = { showExcludeSongsDialog = true },
                             icon = R.drawable.playbackduration
                         )
                     }
                     
                                           if (showExcludeSongsDialog) {
                          ValueSelectorDialog(
                 title = stringResource(R.string.exclude_songs_with_duration_limit_description),
                 selectedValue = excludeSongWithDurationLimit,
                 onValueSelected = { excludeSongWithDurationLimit = it },
                 valueText = {
                     when (it) {
                         DurationInMinutes.Disabled -> stringResource(R.string.vt_disabled)
                         DurationInMinutes.`3` -> "3m"
                         DurationInMinutes.`5` -> "5m"
                         DurationInMinutes.`10` -> "10m"
                         DurationInMinutes.`15` -> "15m"
                         DurationInMinutes.`20` -> "20m"
                         DurationInMinutes.`25` -> "25m"
                         DurationInMinutes.`30` -> "30m"
                         DurationInMinutes.`60` -> "60m"
                     }
                              },
                              values = DurationInMinutes.values().toList(),
                              onDismiss = { showExcludeSongsDialog = false }
                          )
                      }

                                         var showPauseBetweenSongsDialog by remember { mutableStateOf(false) }
                     if (search.inputValue.isBlank() || stringResource(R.string.pause_between_songs).contains(search.inputValue,true)) {
                         OtherSettingsEntry(
                             title = stringResource(R.string.pause_between_songs),
                             text = when (pauseBetweenSongs) {
                                 PauseBetweenSongs.`0` -> "0s"
                                 PauseBetweenSongs.`5` -> "5s"
                                 PauseBetweenSongs.`10` -> "10s"
                                 PauseBetweenSongs.`15` -> "15s"
                                 PauseBetweenSongs.`20` -> "20s"
                                 PauseBetweenSongs.`30` -> "30s"
                                 PauseBetweenSongs.`40` -> "40s"
                                 PauseBetweenSongs.`50` -> "50s"
                                 PauseBetweenSongs.`60` -> "60s"
                             },
                             onClick = { showPauseBetweenSongsDialog = true },
                             icon = R.drawable.pause
                         )
                     }
                     
                                           if (showPauseBetweenSongsDialog) {
                          ValueSelectorDialog(
                 title = stringResource(R.string.pause_between_songs_description),
                 selectedValue = pauseBetweenSongs,
                 onValueSelected = { pauseBetweenSongs = it },
                 valueText = {
                     when (it) {
                         PauseBetweenSongs.`0` -> "0s"
                         PauseBetweenSongs.`5` -> "5s"
                         PauseBetweenSongs.`10` -> "10s"
                         PauseBetweenSongs.`15` -> "15s"
                         PauseBetweenSongs.`20` -> "20s"
                         PauseBetweenSongs.`30` -> "30s"
                         PauseBetweenSongs.`40` -> "40s"
                         PauseBetweenSongs.`50` -> "50s"
                         PauseBetweenSongs.`60` -> "60s"
                     }
                              },
                              values = PauseBetweenSongs.values().toList(),
                              onDismiss = { showPauseBetweenSongsDialog = false }
                          )
                                           }

                     if (search.inputValue.isBlank() || stringResource(R.string.crossfade).contains(search.inputValue, true)) {
                         OtherSwitchSettingEntry(
                             title = stringResource(R.string.crossfade),
                             text = stringResource(R.string.crossfade_description),
                             isChecked = crossfadeEnabled,
                             onCheckedChange = { crossfadeEnabled = it },
                             icon = R.drawable.shuffle
                         )
                     }

                     AnimatedVisibility(visible = crossfadeEnabled) {
                         Column(
                             modifier = Modifier.padding(start = 25.dp)
                         ) {
                             var showCrossfadeDurationDialog by remember { mutableStateOf(false) }
                             if (search.inputValue.isBlank() || stringResource(R.string.crossfade_duration).contains(search.inputValue,true)) {
                                 OtherSettingsEntry(
                                     title = stringResource(R.string.crossfade_duration),
                                     text = "${crossfadeDuration / 1000}s",
                                     onClick = { showCrossfadeDurationDialog = true },
                                     icon = R.drawable.time
                                 )
                             }
                             
                             if (showCrossfadeDurationDialog) {
                                 ValueSelectorDialog(
                                     title = stringResource(R.string.crossfade_duration),
                                     selectedValue = crossfadeDuration,
                                     onValueSelected = { crossfadeDuration = it },
                                     valueText = { "${it / 1000}s" },
                                     values = listOf(1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000, 12000, 15000, 20000),
                                     onDismiss = { showCrossfadeDurationDialog = false }
                                 )
                             }

                             if (search.inputValue.isBlank() || stringResource(R.string.crossfade_gapless).contains(search.inputValue, true)) {
                                 OtherSwitchSettingEntry(
                                     title = stringResource(R.string.crossfade_gapless),
                                     text = stringResource(R.string.crossfade_gapless_description),
                                     isChecked = crossfadeGapless,
                                     onCheckedChange = { crossfadeGapless = it },
                                     icon = R.drawable.album
                                 )
                             }
                         }
                     }
                 }
             )
         }

         /* Removed Spacer */

         // Player Controls Section
         val searchCtx_3 = search.inputValue.isBlank() || stringResource(R.string.player_controls).contains(search.inputValue, true) || stringResource(R.string.player_pause_on_volume_zero).contains(search.inputValue, true) || stringResource(R.string.info_pauses_player_when_volume_zero).contains(search.inputValue, true) || stringResource(R.string.player_keep_minimized).contains(search.inputValue, true) || stringResource(R.string.when_click_on_a_song_player_start_minimized).contains(search.inputValue, true) || stringResource(R.string.player_collapsed_disable_swiping_down).contains(search.inputValue, true) || stringResource(R.string.avoid_closing_the_player_cleaning_queue_by_swiping_down).contains(search.inputValue, true) || stringResource(R.string.player_auto_load_songs_in_queue).contains(search.inputValue, true) || stringResource(R.string.player_auto_load_songs_in_queue_description).contains(search.inputValue, true)
         AnimatedVisibility(
             visible = searchCtx_3,
             enter = fadeIn(animationSpec = tween(900)) + scaleIn(
                 animationSpec = tween(900),
                 initialScale = 0.9f
             )
         ) {
             SettingsSectionCard(
                 title = stringResource(R.string.player_controls),
                 icon = R.drawable.player_control,
                 content = {
        if (search.inputValue.isBlank() || stringResource(R.string.player_pause_on_volume_zero).contains(search.inputValue,true))
                          OtherSwitchSettingEntry(
                title = stringResource(R.string.player_pause_on_volume_zero),
                text = stringResource(R.string.info_pauses_player_when_volume_zero),
                isChecked = isPauseOnVolumeZeroEnabled,
                onCheckedChange = {
                    isPauseOnVolumeZeroEnabled = it
                             },
                             icon = R.drawable.volume_up
                         )

            if (search.inputValue.isBlank() || stringResource(R.string.pause_on_headphone_disconnect).contains(search.inputValue,true))
                         OtherSwitchSettingEntry(
                title = stringResource(R.string.pause_on_headphone_disconnect),
                text = stringResource(R.string.pause_on_headphone_disconnect_description),
                isChecked = pauseOnHeadphoneDisconnect,
                onCheckedChange = {
                    pauseOnHeadphoneDisconnect = it
                             },
                             icon = R.drawable.headphones
                         )

            if (search.inputValue.isBlank() || stringResource(R.string.player_keep_minimized).contains(search.inputValue,true))
                         OtherSwitchSettingEntry(
                    title = stringResource(R.string.player_keep_minimized),
                    text = stringResource(R.string.when_click_on_a_song_player_start_minimized),
                    isChecked = keepPlayerMinimized,
                    onCheckedChange = {
                        keepPlayerMinimized = it
                             },
                             icon = R.drawable.maximize
                )

        if (search.inputValue.isBlank() || stringResource(R.string.player_collapsed_disable_swiping_down).contains(search.inputValue,true))
                         OtherSwitchSettingEntry(
                title = stringResource(R.string.player_collapsed_disable_swiping_down),
                text = stringResource(R.string.avoid_closing_the_player_cleaning_queue_by_swiping_down),
                isChecked = disableClosingPlayerSwipingDown,
                onCheckedChange = {
                    disableClosingPlayerSwipingDown = it
                             },
                             icon = R.drawable.reorder
            )

        if (search.inputValue.isBlank() || stringResource(R.string.player_auto_load_songs_in_queue).contains(search.inputValue,true) || (stringResource(R.string.player_auto_load_songs_in_queue_description)).contains(search.inputValue, true)) {
                         OtherSwitchSettingEntry(
                title = stringResource(R.string.player_auto_load_songs_in_queue),
                text = stringResource(R.string.player_auto_load_songs_in_queue_description),
                isChecked = autoLoadSongsInQueue,
                onCheckedChange = {
                    autoLoadSongsInQueue = it
                    restartService = true
                             },
                             icon = R.drawable.playlist
            )
            RestartPlayerService(restartService, onRestart = { restartService = false })
                     }
                 }
             )
         }

         /* Removed Spacer */

         // Queue Management Section
         val searchCtx_4 = search.inputValue.isBlank() || stringResource(R.string.queue_management).contains(search.inputValue, true) || stringResource(R.string.max_songs_in_queue).contains(search.inputValue, true) || stringResource(R.string.unlimited).contains(search.inputValue, true) || stringResource(R.string.discover).contains(search.inputValue, true) || stringResource(R.string.discoverinfo).contains(search.inputValue, true) || stringResource(R.string.playlistindicator).contains(search.inputValue, true) || stringResource(R.string.playlistindicatorinfo).contains(search.inputValue, true) || stringResource(R.string.now_playing_indicator).contains(search.inputValue, true)
         AnimatedVisibility(
             visible = searchCtx_4,
             enter = fadeIn(animationSpec = tween(1000)) + scaleIn(
                 animationSpec = tween(1000),
                 initialScale = 0.9f
             )
         ) {
             SettingsSectionCard(
                 title = stringResource(R.string.queue_management),
                 icon = R.drawable.playlist,
                 content = {
                     var showMaxSongsDialog by remember { mutableStateOf(false) }
                     var showMaxSongsAndroidAutoDialog by remember { mutableStateOf(false) }
                     if (search.inputValue.isBlank() || stringResource(R.string.max_songs_in_queue).contains(search.inputValue,true)) {
                         OtherSettingsEntry(
                             title = stringResource(R.string.max_songs_in_queue),
                             text = when (maxSongsInQueue) {
                                 MaxSongs.Unlimited -> stringResource(R.string.unlimited)
                                 MaxSongs.`50` -> MaxSongs.`50`.name
                                 MaxSongs.`100` -> MaxSongs.`100`.name
                                 MaxSongs.`200` -> MaxSongs.`200`.name
                                 MaxSongs.`300` -> MaxSongs.`300`.name
                                 MaxSongs.`500` -> MaxSongs.`500`.name
                                 MaxSongs.`1000` -> MaxSongs.`1000`.name
                                 MaxSongs.`2000` -> MaxSongs.`2000`.name
                                 MaxSongs.`3000` -> MaxSongs.`3000`.name
                             },
                             onClick = { showMaxSongsDialog = true },
                             icon = R.drawable.music_file
                         )
if (search.inputValue.isBlank() || stringResource(R.string.max_songs_in_queue_android_auto).contains(search.inputValue, true) || stringResource(R.string.queue_management).contains(search.inputValue, true) || stringResource(R.string.unlimited).contains(search.inputValue, true)) {
                                 OtherSettingsEntry(
                                 title = stringResource(R.string.max_songs_in_queue_android_auto),
                                 text = when (maxSongsInQueueAndroidAuto) {
                                     MaxSongs.Unlimited -> stringResource(R.string.unlimited)
                                     MaxSongs.`50` -> MaxSongs.`50`.name
                                     MaxSongs.`100` -> MaxSongs.`100`.name
                                     MaxSongs.`200` -> MaxSongs.`200`.name
                                     MaxSongs.`300` -> MaxSongs.`300`.name
                                     MaxSongs.`500` -> MaxSongs.`500`.name
                                     MaxSongs.`1000` -> MaxSongs.`1000`.name
                                     MaxSongs.`2000` -> MaxSongs.`2000`.name
                                     MaxSongs.`3000` -> MaxSongs.`3000`.name
                                 },
                                 onClick = { showMaxSongsAndroidAutoDialog = true },
                                 icon = R.drawable.music_file
                             )
    }
                     }
                     
                     if (showMaxSongsDialog) {
                         ValueSelectorDialog(
                title = stringResource(R.string.max_songs_in_queue),
                selectedValue = maxSongsInQueue,
                onValueSelected = { maxSongsInQueue = it },
                valueText = {
                    when (it) {
                        MaxSongs.Unlimited -> stringResource(R.string.unlimited)
                        MaxSongs.`50` -> MaxSongs.`50`.name
                        MaxSongs.`100` -> MaxSongs.`100`.name
                        MaxSongs.`200` -> MaxSongs.`200`.name
                        MaxSongs.`300` -> MaxSongs.`300`.name
                        MaxSongs.`500` -> MaxSongs.`500`.name
                        MaxSongs.`1000` -> MaxSongs.`1000`.name
                        MaxSongs.`2000` -> MaxSongs.`2000`.name
                        MaxSongs.`3000` -> MaxSongs.`3000`.name
                    }
                             },
                             values = MaxSongs.values().toList(),
                             onDismiss = { showMaxSongsDialog = false }
                         )
                     }

                     if (showMaxSongsAndroidAutoDialog) {
                         ValueSelectorDialog(
                title = stringResource(R.string.max_songs_in_queue_android_auto),
                selectedValue = maxSongsInQueueAndroidAuto,
                onValueSelected = { maxSongsInQueueAndroidAuto = it },
                valueText = {
                    when (it) {
                        MaxSongs.Unlimited -> stringResource(R.string.unlimited)
                        MaxSongs.`50` -> MaxSongs.`50`.name
                        MaxSongs.`100` -> MaxSongs.`100`.name
                        MaxSongs.`200` -> MaxSongs.`200`.name
                        MaxSongs.`300` -> MaxSongs.`300`.name
                        MaxSongs.`500` -> MaxSongs.`500`.name
                        MaxSongs.`1000` -> MaxSongs.`1000`.name
                        MaxSongs.`2000` -> MaxSongs.`2000`.name
                        MaxSongs.`3000` -> MaxSongs.`3000`.name
                    }
                             },
                             values = MaxSongs.values().toList(),
                             onDismiss = { showMaxSongsAndroidAutoDialog = false }
                         )
                     }

                     if (search.inputValue.isBlank() || stringResource(R.string.discover).contains(search.inputValue, true))
                         OtherSwitchSettingEntry(
                title = stringResource(R.string.discover),
                text = stringResource(R.string.discoverinfo),
                isChecked = discoverIsEnabled,
                             onCheckedChange = { discoverIsEnabled = it },
                             icon = R.drawable.search
            )

        if (search.inputValue.isBlank() || stringResource(R.string.playlistindicator).contains(search.inputValue,true))
                         OtherSwitchSettingEntry(
                title = stringResource(R.string.playlistindicator),
                text = stringResource(R.string.playlistindicatorinfo),
                isChecked = playlistindicator,
                onCheckedChange = {
                    playlistindicator = it
                             },
                             icon = R.drawable.playlist
                         )

                     var showNowPlayingIndicatorDialog by remember { mutableStateOf(false) }
                     if (search.inputValue.isBlank() || stringResource(R.string.now_playing_indicator).contains(search.inputValue,true)) {
                         OtherSettingsEntry(
                             title = stringResource(R.string.now_playing_indicator),
                             text = nowPlayingIndicator.text,
                             onClick = { showNowPlayingIndicatorDialog = true },
                             icon = R.drawable.playing_indicator
                         )
                     }
                     
                     if (showNowPlayingIndicatorDialog) {
                         ValueSelectorDialog(
                title = stringResource(R.string.now_playing_indicator),
                selectedValue = nowPlayingIndicator,
                onValueSelected = { nowPlayingIndicator = it },
                             valueText = { it.text },
                             values = MusicAnimationType.values().toList(),
                             onDismiss = { showNowPlayingIndicatorDialog = false }
                         )
                     }
                 }
             )
         }

         /* Removed Spacer */

         // App Behavior Section
         val searchCtx_5 = search.inputValue.isBlank() || stringResource(R.string.app_behavior).contains(search.inputValue, true) || stringResource(R.string.resume_playback).contains(search.inputValue, true) || stringResource(R.string.when_device_is_connected).contains(search.inputValue, true) || stringResource(R.string.persistent_queue).contains(search.inputValue, true) || stringResource(R.string.save_and_restore_playing_songs).contains(search.inputValue, true) || stringResource(R.string.resume_playback_on_start).contains(search.inputValue, true) || stringResource(R.string.resume_automatically_when_app_opens).contains(search.inputValue, true) || stringResource(R.string.close_app_with_back_button).contains(search.inputValue, true) || stringResource(R.string.when_you_use_the_back_button_from_the_home_page).contains(search.inputValue, true) || stringResource(R.string.close_background_player).contains(search.inputValue, true) || stringResource(R.string.when_app_swipe_out_from_task_manager).contains(search.inputValue, true) || stringResource(R.string.skip_media_on_error).contains(search.inputValue, true) || stringResource(R.string.skip_media_on_error_description).contains(search.inputValue, true)
         AnimatedVisibility(
             visible = searchCtx_5,
             enter = fadeIn(animationSpec = tween(1100)) + scaleIn(
                 animationSpec = tween(1100),
                 initialScale = 0.9f
             )
         ) {
             SettingsSectionCard(
                 title = stringResource(R.string.app_behavior),
                 icon = R.drawable.settings,
                 content = {
        if (search.inputValue.isBlank() || stringResource(R.string.resume_playback).contains(search.inputValue,true) || (stringResource(R.string.when_device_is_connected)).contains(search.inputValue, true)) {
            if (isAtLeastAndroid6) {
                             if (search.inputValue.isBlank() || stringResource(R.string.resume_playback).contains(search.inputValue, true) || (stringResource(R.string.when_device_is_connected)).contains(search.inputValue, true)) {
                                 OtherSwitchSettingEntry(
                        title = stringResource(R.string.resume_playback),
                        text = stringResource(R.string.when_device_is_connected),
                        isChecked = resumePlaybackWhenDeviceConnected,
                        onCheckedChange = {
                            resumePlaybackWhenDeviceConnected = it
                            restartService = true
                                     },
                                     icon = R.drawable.play
                    )
                             }
                RestartPlayerService(restartService, onRestart = { restartService = false })
            }
        }

        if (search.inputValue.isBlank() || stringResource(R.string.persistent_queue).contains(search.inputValue,true) || (stringResource(R.string.save_and_restore_playing_songs)).contains(search.inputValue, true)) {
                         OtherSwitchSettingEntry(
                title = stringResource(R.string.persistent_queue),
                text = stringResource(R.string.save_and_restore_playing_songs),
                isChecked = persistentQueue,
                onCheckedChange = {
                    persistentQueue = it
                    restartService = true
                             },
                             icon = R.drawable.download
            )
            RestartPlayerService(restartService, onRestart = { restartService = false })

            AnimatedVisibility(visible = persistentQueue) {
                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                                 if (search.inputValue.isBlank() ||  stringResource(R.string.resume_playback_on_start).contains(search.inputValue, true) || (stringResource(R.string.resume_automatically_when_app_opens)).contains(search.inputValue, true)) {
                                     OtherSwitchSettingEntry(
                            title =  stringResource(R.string.resume_playback_on_start),
                            text = stringResource(R.string.resume_automatically_when_app_opens),
                            isChecked = resumePlaybackOnStart,
                            onCheckedChange = {
                                resumePlaybackOnStart = it
                                restartService = true
                                         },
                                         icon = R.drawable.play
                        )
                                 }
                    RestartPlayerService(restartService, onRestart = { restartService = false } )
                }
            }
        }

        if (search.inputValue.isBlank() || stringResource(R.string.close_app_with_back_button).contains(search.inputValue,true) || (stringResource(R.string.when_you_use_the_back_button_from_the_home_page)).contains(search.inputValue, true)) {
                         if (Build.VERSION.SDK_INT >= 33) {
                             if (search.inputValue.isBlank() || stringResource(R.string.close_app_with_back_button).contains(search.inputValue, true) || (stringResource(R.string.when_you_use_the_back_button_from_the_home_page)).contains(search.inputValue, true)) {
                                 OtherSwitchSettingEntry(
                    title = stringResource(R.string.close_app_with_back_button),
                    text = stringResource(R.string.when_you_use_the_back_button_from_the_home_page),
                    isChecked = closeWithBackButton,
                    onCheckedChange = {
                        closeWithBackButton = it
                        restartActivity = true
                                     },
                                     icon = R.drawable.close
                )
                             }
                         }
            RestartActivity(restartActivity, onRestart = { restartActivity = false })
        }

        if (search.inputValue.isBlank() || stringResource(R.string.close_background_player).contains(search.inputValue,true) || (stringResource(R.string.when_app_swipe_out_from_task_manager)).contains(search.inputValue, true)) {
                         OtherSwitchSettingEntry(
                title = stringResource(R.string.close_background_player),
                text = stringResource(R.string.when_app_swipe_out_from_task_manager),
                isChecked = closebackgroundPlayer,
                onCheckedChange = {
                    closebackgroundPlayer = it
                    restartService = true
                             },
                             icon = R.drawable.close
            )
            RestartPlayerService(restartService, onRestart = { restartService = false } )
        }

        if (search.inputValue.isBlank() || stringResource(R.string.skip_media_on_error).contains(search.inputValue,true) || (stringResource(R.string.skip_media_on_error_description)).contains(search.inputValue, true)) {
                         OtherSwitchSettingEntry(
                title = stringResource(R.string.skip_media_on_error),
                text = stringResource(R.string.skip_media_on_error_description),
                isChecked = skipMediaOnError,
                onCheckedChange = {
                    skipMediaOnError = it
                    restartService = true
                             },
                             icon = R.drawable.alert_circle
            )
            RestartPlayerService(restartService, onRestart = { restartService = false } )
                     }
                 }
             )
         }

         /* Removed Spacer */

         // Audio Effects Section
         val searchCtx_6 = search.inputValue.isBlank() || stringResource(R.string.audio_effects).contains(search.inputValue, true) || stringResource(R.string.skip_silence).contains(search.inputValue, true) || stringResource(R.string.skip_silent_parts_during_playback).contains(search.inputValue, true) || stringResource(R.string.minimum_silence_length).contains(search.inputValue, true) || stringResource(R.string.minimum_silence_length_description).contains(search.inputValue, true) || stringResource(R.string.loudness_normalization).contains(search.inputValue, true) || stringResource(R.string.autoadjust_the_volume).contains(search.inputValue, true) || stringResource(R.string.settings_loudness_base_gain).contains(search.inputValue, true) || stringResource(R.string.settings_target_gain_loudness_info).contains(search.inputValue, true) || stringResource(R.string.settings_audio_bass_boost).contains(search.inputValue, true) || stringResource(R.string.settings_bass_boost_level).contains(search.inputValue, true) || stringResource(R.string.settings_audio_reverb).contains(search.inputValue, true) || stringResource(R.string.settings_audio_reverb_info_apply_a_depth_effect_to_the_audio).contains(search.inputValue, true) || stringResource(R.string.settings_audio_focus).contains(search.inputValue, true) || stringResource(R.string.settings_audio_focus_info).contains(search.inputValue, true) || stringResource(R.string.equalizer).contains(search.inputValue, true) || stringResource(R.string.interact_with_the_system_equalizer).contains(search.inputValue, true)
         AnimatedVisibility(
             visible = searchCtx_6,
             enter = fadeIn(animationSpec = tween(1200)) + scaleIn(
                 animationSpec = tween(1200),
                 initialScale = 0.9f
             )
         ) {
             SettingsSectionCard(
                 title = stringResource(R.string.audio_effects),
                 icon = R.drawable.sound_effect,
                 content = {
        if (search.inputValue.isBlank() || stringResource(R.string.skip_silence).contains(search.inputValue,true) || (stringResource(R.string.skip_silent_parts_during_playback)).contains(search.inputValue, true)) {
                         OtherSwitchSettingEntry(
                title = stringResource(R.string.skip_silence),
                text = stringResource(R.string.skip_silent_parts_during_playback),
                isChecked = skipSilence,
                onCheckedChange = {
                    skipSilence = it
                             },
                             icon = R.drawable.pause
            )

            AnimatedVisibility(visible = skipSilence) {
                val initialValue by remember { derivedStateOf { minimumSilenceDuration.toFloat() / 1000L } }
                var newValue by remember(initialValue) { mutableFloatStateOf(initialValue) }

                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                    if (search.inputValue.isBlank() || stringResource(R.string.minimum_silence_length).contains(search.inputValue, true) || (stringResource(R.string.minimum_silence_length_description)).contains(search.inputValue, true)) {
                        SliderSettingsEntry(
                            title = stringResource(R.string.minimum_silence_length),
                            text = stringResource(R.string.minimum_silence_length_description),
                            state = newValue,
                            onSlide = { newValue = it },
                            onSlideComplete = {
                                minimumSilenceDuration = newValue.toLong() * 1000L
                                restartService = true
                            },
                            isIntegerOnly = true,
                            toDisplay = { stringResource(R.string.format_ms, it.toLong()) },
                            range = 1.00f..2000.000f,
                            stepSize = 0f,
                            defaultValue = 2000f,
                            drawValuePoints = true,
                            icon = R.drawable.time
                        )
                    }

                    RestartPlayerService(restartService, onRestart = { restartService = false } )
                }
            }
        }

        if (search.inputValue.isBlank() || stringResource(R.string.loudness_normalization).contains(search.inputValue,true) || (stringResource(R.string.autoadjust_the_volume)).contains(search.inputValue, true)) {
                         OtherSwitchSettingEntry(
                title = stringResource(R.string.loudness_normalization),
                text = stringResource(R.string.autoadjust_the_volume),
                isChecked = volumeNormalization,
                onCheckedChange = {
                    volumeNormalization = it
                             },
                             icon = R.drawable.volume_up
            )
            AnimatedVisibility(visible = volumeNormalization) {
                val initialValue by remember { derivedStateOf { loudnessBaseGain } }
                var newValue by remember(initialValue) { mutableFloatStateOf(initialValue) }

                val initialValueVolume by remember { derivedStateOf { volumeBoostLevel } }
                var newValueVolume by remember(initialValueVolume) { mutableFloatStateOf(initialValueVolume) }

                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_loudness_base_gain).contains(search.inputValue, true) || (stringResource(R.string.settings_target_gain_loudness_info)).contains(search.inputValue, true)) {
                        SliderSettingsEntry(
                            title = stringResource(R.string.settings_loudness_base_gain),
                            text = stringResource(R.string.settings_target_gain_loudness_info),
                            state = newValue,
                            onSlide = { newValue = kotlin.math.round(it * 100f) / 100f },
                            onSlideComplete = {
                                loudnessBaseGain = newValue
                            },
                            toDisplay = { "%.2f dB".format(it) },
                            range = -20f..20f,
                            stepSize = 0f,
                            defaultValue = 0f,
                            drawValuePoints = true,
                            icon = R.drawable.volume_up
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(-20f to "-20", -10f to "-10", 0f to "0", 10f to "10", 20f to "20").forEach { (v, label) ->
                                val isSelected = loudnessBaseGain == v
                                TextButton(
                                    onClick = { loudnessBaseGain = v; newValue = v },
                                    shape = uiRoundnessShape(),
                                    colors = ButtonDefaults.textButtonColors(containerColor = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        color = if (isSelected) colorPalette().accent else colorPalette().text,
                                        fontWeight = if (isSelected) FontWeight.Bold else null
                                    )
                                }
                            }
                        }

                    }

                    if (search.inputValue.isBlank() || stringResource(R.string.loudness_boost_level).contains(search.inputValue, true) || (stringResource(R.string.loudness_boost_level_info)).contains(search.inputValue, true)) {
                        SliderSettingsEntry(
                            title = stringResource(R.string.loudness_boost_level),
                            text = stringResource(R.string.loudness_boost_level_info),
                            state = newValueVolume,
                            onSlide = { newValueVolume = kotlin.math.round(it * 100f) / 100f },
                            onSlideComplete = {
                                volumeBoostLevel = newValueVolume
                            },
                            toDisplay = { "%.2f dB".format(it).replace(",", ".") },
                            range = -30f..30f,
                            stepSize = 0f,
                            defaultValue = 0f,
                            drawValuePoints = true,
                            icon = R.drawable.volume_up
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(-30f to "-30", -15f to "-15", 0f to "0", 15f to "15", 30f to "30").forEach { (v, label) ->
                                val isSelected = volumeBoostLevel == v
                                TextButton(
                                    onClick = { volumeBoostLevel = v; newValueVolume = v },
                                    shape = uiRoundnessShape(),
                                    colors = ButtonDefaults.textButtonColors(containerColor = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        color = if (isSelected) colorPalette().accent else colorPalette().text,
                                        fontWeight = if (isSelected) FontWeight.Bold else null
                                    )
                                }
                            }
                        }

                    }
                }
            }
        }

        if (search.inputValue.isBlank() || stringResource(R.string.settings_audio_bass_boost).contains(search.inputValue,true)) {
                         OtherSwitchSettingEntry(
                title = stringResource(R.string.settings_audio_bass_boost),
                text = "",
                isChecked = bassboostEnabled,
                onCheckedChange = {
                    bassboostEnabled = it
                             },
                             icon = R.drawable.equalizer
            )
            AnimatedVisibility(visible = bassboostEnabled) {
                val initialValue by remember { derivedStateOf { bassboostLevel } }
                var newValue by remember(initialValue) { mutableFloatStateOf(initialValue) }

                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_bass_boost_level).contains(search.inputValue, true)) {
                        SliderSettingsEntry(
                            title = stringResource(R.string.settings_bass_boost_level),
                            text = "",
                            state = newValue,
                            onSlide = { newValue = kotlin.math.round(it * 10000f) / 10000f },
                            onSlideComplete = {
                                bassboostLevel = newValue
                            },
                            toDisplay = { "%.2f dB".format(it * 15f).replace(",", ".") },
                            range = 0f..1f,
                            stepSize = 0f,
                            defaultValue = 0f,
                            drawValuePoints = true,
                            icon = R.drawable.equalizer
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(0f to "0", (4f/15f) to "4", (8f/15f) to "8", (11f/15f) to "11", 1f to "15").forEach { (v, label) ->
                                val isSelected = bassboostLevel == v
                                TextButton(
                                    onClick = { bassboostLevel = v; newValue = v },
                                    shape = uiRoundnessShape(),
                                    colors = ButtonDefaults.textButtonColors(containerColor = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        color = if (isSelected) colorPalette().accent else colorPalette().text,
                                        fontWeight = if (isSelected) FontWeight.Bold else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

                     var showAudioReverbDialog by remember { mutableStateOf(false) }
        if (search.inputValue.isBlank() || stringResource(R.string.settings_audio_reverb).contains(search.inputValue,true)) {
                         OtherSettingsEntry(
                title = stringResource(R.string.settings_audio_reverb),
                text = stringResource(audioReverb.textRes),
                             onClick = { showAudioReverbDialog = true },
                             icon = R.drawable.reverb
                         )
                         RestartPlayerService(restartService, onRestart = { restartService = false } )
                     }
                     
                                           if (showAudioReverbDialog) {
                          ValueSelectorDialog(
                              title = stringResource(R.string.settings_audio_reverb_info_apply_a_depth_effect_to_the_audio),
                 selectedValue = audioReverb,
                 onValueSelected = {
                     audioReverb = it
                     restartService = true
                 },
                              valueText = { stringResource(it.textRes) },
                              values = PresetsReverb.values().toList(),
                              onDismiss = { showAudioReverbDialog = false }
                          )
         }

        if (search.inputValue.isBlank() || stringResource(R.string.settings_audio_focus).contains(search.inputValue,true) || (stringResource(R.string.settings_audio_focus_info)).contains(search.inputValue, true)) {
                         OtherSwitchSettingEntry(
                title = stringResource(R.string.settings_audio_focus),
                text = stringResource(R.string.settings_audio_focus_info),
                isChecked = audioFocusEnabled,
                onCheckedChange = {
                    audioFocusEnabled = it
                             },
                             icon = R.drawable.focus_audio
                         )
                     }

                     if (search.inputValue.isBlank() || stringResource(R.string.equalizer).contains(search.inputValue,true))
                         OtherSettingsEntry(
                             title = stringResource(R.string.equalizer),
                             text = stringResource(R.string.interact_with_the_system_equalizer),
                             onClick = launchEqualizer,
                             icon = R.drawable.equalizer
                         )
                 }
             )
         }

         /* Removed Spacer */

         // Gestures & Events Section
          val searchCtx_7 = search.inputValue.isBlank() || stringResource(R.string.gestures_events).contains(search.inputValue, true) || stringResource(R.string.event_volumekeys).contains(search.inputValue, true) || stringResource(R.string.event_volumekeysinfo).contains(search.inputValue, true)
         AnimatedVisibility(
             visible = searchCtx_7,
             enter = fadeIn(animationSpec = tween(1300)) + scaleIn(
                 animationSpec = tween(1300),
                 initialScale = 0.9f
             )
         ) {
             SettingsSectionCard(
                 title = stringResource(R.string.gestures_events),
                 icon = R.drawable.gesture,
                 content = {
        if (search.inputValue.isBlank() || stringResource(R.string.event_volumekeys).contains(search.inputValue,true) || (stringResource(R.string.event_volumekeysinfo)).contains(search.inputValue, true)) {
                         OtherSwitchSettingEntry(
                title = stringResource(R.string.event_volumekeys),
                text = stringResource(R.string.event_volumekeysinfo),
                isChecked = useVolumeKeysToChangeSong,
                onCheckedChange = {
                    useVolumeKeysToChangeSong = it
                    restartService = true
                             },
                             icon = R.drawable.volume_control
            )
            RestartPlayerService(restartService, onRestart = { restartService = false } )
                     }
                 }
             )
         }

         /* Removed Spacer */

         // Picture in Picture Section
         val searchCtx_8 = search.inputValue.isBlank() || stringResource(R.string.picture_in_picture).contains(search.inputValue, true) || stringResource(R.string.settings_enable_pip).contains(search.inputValue, true) || stringResource(R.string.settings_pip_module).contains(search.inputValue, true) || stringResource(R.string.pipmodule_cover).contains(search.inputValue, true) || stringResource(R.string.settings_enable_pip_auto).contains(search.inputValue, true) || stringResource(R.string.pip_info_from_android_12_pip_can_be_automatically_enabled).contains(search.inputValue, true)
         AnimatedVisibility(
             visible = searchCtx_8,
             enter = fadeIn(animationSpec = tween(1400)) + scaleIn(
                 animationSpec = tween(1400),
                 initialScale = 0.9f
             )
         ) {
             SettingsSectionCard(
                 title = stringResource(R.string.picture_in_picture),
                 icon = R.drawable.video,
                 content = {
        if (search.inputValue.isBlank() || stringResource(R.string.settings_enable_pip).contains(search.inputValue,true)) {
                         OtherSwitchSettingEntry(
                title = stringResource(R.string.settings_enable_pip),
                text = "",
                isChecked = enablePictureInPicture,
                onCheckedChange = {
                    enablePictureInPicture = it
                    restartActivity = true
                             },
                             icon = R.drawable.logo_youtube
            )
            RestartActivity(restartActivity, onRestart = { restartActivity = false })
            AnimatedVisibility(visible = enablePictureInPicture) {
                Column(
                    modifier = Modifier.padding(start = 25.dp)
                ) {
                                 var showPipModuleDialog by remember { mutableStateOf(false) }
                                 if (search.inputValue.isBlank() || stringResource(R.string.settings_pip_module).contains(search.inputValue, true)) {
                                     OtherSettingsEntry(
                                         title = stringResource(R.string.settings_pip_module),
                                         text = when (pipModule) {
                                             PipModule.Cover -> stringResource(R.string.pipmodule_cover)
                                         },
                                         onClick = { showPipModuleDialog = true },
                                         icon = R.drawable.logo_youtube
                                     )
                                 }
                                 
                                 if (showPipModuleDialog) {
                                     ValueSelectorDialog(
                        title = stringResource(R.string.settings_pip_module),
                        selectedValue = pipModule,
                        onValueSelected = {
                            pipModule = it
                            restartActivity = true
                        },
                        valueText = {
                            when (it) {
                                PipModule.Cover -> stringResource(R.string.pipmodule_cover)
                            }
                                         },
                                         values = PipModule.values().toList(),
                                         onDismiss = { showPipModuleDialog = false }
                    )
                                 }

                                 if (isAtLeastAndroid12) {
                                     if (search.inputValue.isBlank() || stringResource(R.string.settings_enable_pip_auto).contains(search.inputValue, true) || (stringResource(R.string.pip_info_from_android_12_pip_can_be_automatically_enabled)).contains(search.inputValue, true)) {
                                         OtherSwitchSettingEntry(
                            title = stringResource(R.string.settings_enable_pip_auto),
                            text = stringResource(R.string.pip_info_from_android_12_pip_can_be_automatically_enabled),
                            isChecked = enablePictureInPictureAuto,
                            onCheckedChange = {
                                enablePictureInPictureAuto = it
                                restartActivity = true
                                             },
                                             icon = R.drawable.logo_youtube
                        )
                                     }
                                 }
                    RestartActivity(restartActivity, onRestart = { restartActivity = false })
                }
                         }
                     }
                 }
             )
         }

        // Settings Reset Section
        val searchCtx_Reset = search.inputValue.isBlank() || stringResource(R.string.settings_reset).contains(search.inputValue, true) || stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true)
        AnimatedVisibility(
            visible = searchCtx_Reset,
            enter = fadeIn(animationSpec = tween(1100)) + scaleIn(animationSpec = tween(1100), initialScale = 0.9f)
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.settings_reset),
                icon = R.drawable.refresh,
                content = {
                    var resetToDefault by remember { mutableStateOf(false) }
                    val context = LocalContext.current
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true) || stringResource(R.string.settings_reset).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.settings_reset),
                            text = stringResource(R.string.settings_restore_default_settings),
                            icon = R.drawable.refresh,
                            onClick = { 
                                resetToDefault = true
                                restartService = true
                                Toaster.done()
                            }
                        )
                    }

                    if (resetToDefault) {
                        DefaultGeneralSettings(context)
                        LaunchedEffect(Unit) {
                            resetToDefault = false
                        }
                    }
                }
            )
        }

        RestartPlayerService(restartService, onRestart = { restartService = false })

        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )

    }
}   

