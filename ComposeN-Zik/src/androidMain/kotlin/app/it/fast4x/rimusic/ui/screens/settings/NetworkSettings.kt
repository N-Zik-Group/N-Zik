package app.it.fast4x.rimusic.ui.screens.settings

import app.n_zik.android.components.tab.Search
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.autoDownloadSongKey
import app.it.fast4x.rimusic.utils.autoDownloadSongWhenAlbumBookmarkedKey
import app.it.fast4x.rimusic.utils.autoDownloadSongWhenLikedKey
import app.it.fast4x.rimusic.utils.isConnectionMeteredEnabledKey
import app.it.fast4x.rimusic.utils.imageQualityFormatKey
import app.it.fast4x.rimusic.enums.ImageQualityFormat
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.it.fast4x.rimusic.utils.streamClientWebRemixEnabledKey
import app.it.fast4x.rimusic.utils.streamClientAndroidVrEnabledKey
import app.it.fast4x.rimusic.utils.streamClientRestartNeededKey
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.components.dialog.settings.StreamClientsSettingsDialog
import app.n_zik.android.components.dialog.settings.PreferredStreamClientDialog
import app.it.fast4x.rimusic.ui.components.themed.ValueSelectorDialog
import app.it.fast4x.rimusic.utils.audioQualityFormatKey
import app.it.fast4x.rimusic.utils.RestartPlayerService
import androidx.compose.runtime.produceState
import app.n_zik.android.core.coil.ImageCacheFactory
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import app.kreate.android.me.knighthat.utils.Toaster

@Composable
fun DefaultNetworkSettings() {

    var isConnectionMeteredEnabled by rememberPreference(isConnectionMeteredEnabledKey, false)
    isConnectionMeteredEnabled = false

    var autoDownloadSong by rememberPreference(autoDownloadSongKey, false)
    autoDownloadSong = false

    var autoDownloadSongWhenLiked by rememberPreference(autoDownloadSongWhenLikedKey, false)
    autoDownloadSongWhenLiked = false

    var autoDownloadSongWhenAlbumBookmarked by rememberPreference(autoDownloadSongWhenAlbumBookmarkedKey, false)
    autoDownloadSongWhenAlbumBookmarked = false

    var audioQualityFormat by rememberPreference(audioQualityFormatKey, AudioQualityFormat.Auto)
    audioQualityFormat = AudioQualityFormat.Auto

    var imageQualityFormat by rememberPreference(imageQualityFormatKey, ImageQualityFormat.Auto)
    imageQualityFormat = ImageQualityFormat.Auto

    var navigationBarPosition by rememberPreference(navigationBarPositionKey, NavigationBarPosition.BottomFloating)
    navigationBarPosition = NavigationBarPosition.BottomFloating

    var isWebRemixEnabled by rememberPreference(streamClientWebRemixEnabledKey, true)
    isWebRemixEnabled = true

    var isAndroidVrEnabled by rememberPreference(streamClientAndroidVrEnabledKey, true)
    isAndroidVrEnabled = true

    var isStreamRestartNeeded by rememberPreference(streamClientRestartNeededKey, false)
    isStreamRestartNeeded = false
}

@ExperimentalAnimationApi
@UnstableApi
@Composable
fun NetworkSettings(
    navController: NavController
) {
    val search = Search()
    val context = LocalContext.current


    var isConnectionMeteredEnabled by rememberPreference(isConnectionMeteredEnabledKey, false)
    var autoDownloadSong by rememberPreference(autoDownloadSongKey, false)
    var autoDownloadSongWhenLiked by rememberPreference(autoDownloadSongWhenLikedKey, false)
    var autoDownloadSongWhenAlbumBookmarked by rememberPreference(autoDownloadSongWhenAlbumBookmarkedKey, false)
    var audioQualityFormat by rememberPreference(audioQualityFormatKey, AudioQualityFormat.Auto)
    var imageQualityFormat by rememberPreference(imageQualityFormatKey, ImageQualityFormat.Auto)
    var restartService by rememberSaveable { mutableStateOf(false) }
    var showAudioQualityDialog by rememberSaveable { mutableStateOf(false) }
    var showImageQualityDialog by rememberSaveable { mutableStateOf(false) }
    val isWebRemixEnabled by rememberPreference(streamClientWebRemixEnabledKey, true)
    val isAndroidVrEnabled by rememberPreference(streamClientAndroidVrEnabledKey, true)
    val isStreamRestartNeeded by rememberPreference(streamClientRestartNeededKey, false)
    
    var navigationBarPosition by rememberPreference(navigationBarPositionKey, NavigationBarPosition.BottomFloating)

    val networkQuality by produceState(initialValue = ImageCacheFactory.getCurrentNetworkQuality()) {
        while (true) {
            value = ImageCacheFactory.getCurrentNetworkQuality()
            delay(5000) // Refresh every 5 seconds
        }
    }

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        HeaderWithIcon(
            title = stringResource(R.string.tab_network),
            iconId = R.drawable.network,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {}
        )

        SettingsDescription(
            text = stringResource(R.string.network_settings_description),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        ) 

        search.ToolBarButton()
        search.SearchBar( this )

        /* Removed Spacer */

        // Network Status Section (Informative, non-clickable)
        SettingsSectionCard(
            title = stringResource(R.string.network_status),
            icon = R.drawable.network,
            content = {
                val detectedText = when(networkQuality) {
                    ImageCacheFactory.NetworkQuality.LOW -> stringResource(R.string.network_quality_low)
                    ImageCacheFactory.NetworkQuality.MEDIUM -> stringResource(R.string.network_quality_medium)
                    ImageCacheFactory.NetworkQuality.HIGH -> stringResource(R.string.network_quality_high)
                }
                
                // Image Status
                val imageStatusText = if (imageQualityFormat == ImageQualityFormat.Auto) {
                    stringResource(R.string.audio_quality_auto_fmt, detectedText)
                } else {
                    when(imageQualityFormat) {
                        ImageQualityFormat.High -> stringResource(R.string.audio_quality_format_high)
                        ImageQualityFormat.Medium -> stringResource(R.string.audio_quality_format_medium)
                        ImageQualityFormat.Low -> stringResource(R.string.audio_quality_format_low)
                        else -> detectedText
                    }
                }

                OtherInfoSettingsEntry(
                    title = stringResource(R.string.image_quality_format),
                    text = imageStatusText,
                    icon = R.drawable.image
                )
                
                // Audio Status
                val audioStatusText = if (audioQualityFormat == AudioQualityFormat.Auto) {
                    stringResource(R.string.audio_quality_auto_fmt, detectedText)
                } else {
                    when(audioQualityFormat) {
                        AudioQualityFormat.High -> stringResource(R.string.audio_quality_format_high)
                        AudioQualityFormat.Medium -> stringResource(R.string.audio_quality_format_medium)
                        AudioQualityFormat.Low -> stringResource(R.string.audio_quality_format_low)
                        else -> detectedText
                    }
                }
                
                OtherInfoSettingsEntry(
                    title = stringResource(R.string.audio_quality_format),
                    text = audioStatusText,
                    icon = R.drawable.audio_quality
                )
            }
        )

        /* Removed Spacer */

        // Quality Settings Section (Clickable override)
        SettingsSectionCard(
            title = stringResource(R.string.quality),
            icon = R.drawable.audio_quality,
            content = {
                // Audio Quality Entry
                if (search.inputValue.isBlank() || stringResource(R.string.audio_quality_format).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        title = stringResource(R.string.audio_quality_format),
                        text = when (audioQualityFormat) {
                            AudioQualityFormat.Auto -> stringResource(R.string.audio_quality_automatic)
                            AudioQualityFormat.High -> stringResource(R.string.audio_quality_format_high)
                            AudioQualityFormat.Medium -> stringResource(R.string.audio_quality_format_medium)
                            AudioQualityFormat.Low -> stringResource(R.string.audio_quality_format_low)
                        },
                        icon = R.drawable.speaker,
                        onClick = { showAudioQualityDialog = true }
                    )
                }
                
                // Image Quality Entry
                if (search.inputValue.isBlank() || stringResource(R.string.image_quality_format).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        title = stringResource(R.string.image_quality_format),
                        text = when (imageQualityFormat) {
                            ImageQualityFormat.Auto -> stringResource(R.string.audio_quality_automatic)
                            ImageQualityFormat.High -> stringResource(R.string.audio_quality_format_high)
                            ImageQualityFormat.Medium -> stringResource(R.string.audio_quality_format_medium)
                            ImageQualityFormat.Low -> stringResource(R.string.audio_quality_format_low)
                        },
                        icon = R.drawable.image,
                        onClick = { showImageQualityDialog = true }
                    )
                }
                
                if (search.inputValue.isBlank() || stringResource(R.string.audio_quality_format).contains(search.inputValue, true)) {
                    RestartPlayerService(restartService, onRestart = { restartService = false })
                }
            }
        )

        if (showAudioQualityDialog) {
            ValueSelectorDialog(
                title = stringResource(R.string.audio_quality_format),
                values = AudioQualityFormat.values().toList(),
                selectedValue = audioQualityFormat,
                onValueSelected = {
                    audioQualityFormat = it
                    restartService = true
                    showAudioQualityDialog = false
                },
                onDismiss = { showAudioQualityDialog = false },
                valueText = {
                    when (it) {
                        AudioQualityFormat.Auto -> stringResource(R.string.audio_quality_automatic)
                        AudioQualityFormat.High -> stringResource(R.string.audio_quality_format_high)
                        AudioQualityFormat.Medium -> stringResource(R.string.audio_quality_format_medium)
                        AudioQualityFormat.Low -> stringResource(R.string.audio_quality_format_low)
                    }
                }
            )
        }
        
        if (showImageQualityDialog) {
            ValueSelectorDialog(
                title = stringResource(R.string.image_quality_format),
                values = ImageQualityFormat.values().toList(),
                selectedValue = imageQualityFormat,
                onValueSelected = {
                    imageQualityFormat = it
                    showImageQualityDialog = false
                },
                onDismiss = { showImageQualityDialog = false },
                valueText = {
                    when (it) {
                        ImageQualityFormat.Auto -> stringResource(R.string.audio_quality_automatic)
                        ImageQualityFormat.High -> stringResource(R.string.audio_quality_format_high)
                        ImageQualityFormat.Medium -> stringResource(R.string.audio_quality_format_medium)
                        ImageQualityFormat.Low -> stringResource(R.string.audio_quality_format_low)
                    }
                }
            )
        }

        /* Removed Spacer */

        // Connection Settings Section
        SettingsSectionCard(
            title = stringResource(R.string.connection_settings),
            icon = R.drawable.network,
            content = {
                if (search.inputValue.isBlank() || stringResource(R.string.enable_connection_metered).contains(search.inputValue, true) || stringResource(R.string.info_enable_connection_metered).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        title = stringResource(R.string.enable_connection_metered),
                        text = stringResource(R.string.info_enable_connection_metered),
                        isChecked = isConnectionMeteredEnabled,
                        onCheckedChange = {
                            isConnectionMeteredEnabled = it
                        },
                        icon = R.drawable.wifi
                    )
                }
            }
        )

        /* Removed Spacer */

        // Stream Clients Section — DISABLED: InnerTubeX handles client selection automatically
        // Settings below are kept for reference but have no effect on playback
        /*
        SettingsSectionCard(
            title = stringResource(R.string.stream_clients),
            icon = R.drawable.musical_notes,
            content = {
                if (isWebRemixEnabled && isAndroidVrEnabled) {
                    if (search.inputValue.isBlank() || stringResource(R.string.preferred_stream_client).contains(search.inputValue, true) || stringResource(R.string.preferred_stream_client_description).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.preferred_stream_client),
                            text = stringResource(R.string.preferred_stream_client_description),
                            icon = R.drawable.musical_notes,
                            onClick = { PreferredStreamClientDialog.showDialog() }
                        )
                    }
                }
                if (search.inputValue.isBlank() || stringResource(R.string.disabled_stream_clients).contains(search.inputValue, true) || stringResource(R.string.configure_which_stream_clients_are_enabled).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        title = stringResource(R.string.disabled_stream_clients),
                        text = stringResource(R.string.configure_which_stream_clients_are_enabled),
                        icon = R.drawable.musical_notes,
                        onClick = { StreamClientsSettingsDialog.showDialog() }
                    )
                }
                if (search.inputValue.isBlank() || true) {
                    RestartPlayerService(
                        restartService = isStreamRestartNeeded,
                        onRestart = {
                            appContext().preferences.edit().putBoolean(streamClientRestartNeededKey, false).apply()
                        }
                    )
                }
            }
        )

        if (isWebRemixEnabled && isAndroidVrEnabled) {
            PreferredStreamClientDialog.Render()
        }
        StreamClientsSettingsDialog.Render()
        */

        /* Removed Spacer */

        // Auto Download Settings Section
        SettingsSectionCard(
            title = stringResource(R.string.download),
            icon = R.drawable.arrow_down,
            content = {
                if (search.inputValue.isBlank() || stringResource(R.string.settings_enable_autodownload_song).contains(search.inputValue, true) || stringResource(R.string.auto_download_song_description).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        title = stringResource(R.string.settings_enable_autodownload_song),
                        text = stringResource(R.string.auto_download_song_description),
                        isChecked = autoDownloadSong,
                        onCheckedChange = {
                            autoDownloadSong = it
                        },
                        icon = R.drawable.download
                    )
                }

                AnimatedVisibility(
                    visible = autoDownloadSong,
                    enter = fadeIn(animationSpec = tween(400)) + scaleIn(
                        animationSpec = tween(400),
                        initialScale = 0.9f
                    ),
                    exit = fadeOut(animationSpec = tween(200)) + scaleOut(
                        animationSpec = tween(200),
                        targetScale = 0.9f
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        if (search.inputValue.isBlank() || stringResource(R.string.settings_enable_autodownload_song_when_liked).contains(search.inputValue, true) || stringResource(R.string.auto_download_when_liked_description).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                                title = stringResource(R.string.settings_enable_autodownload_song_when_liked),
                                text = stringResource(R.string.auto_download_when_liked_description),
                                isChecked = autoDownloadSongWhenLiked,
                                onCheckedChange = {
                                    autoDownloadSongWhenLiked = it
                                },
                                icon = R.drawable.heart
                            )
                        }

                        if (search.inputValue.isBlank() || stringResource(R.string.settings_enable_autodownload_song_when_album_bookmarked).contains(search.inputValue, true) || stringResource(R.string.auto_download_when_album_bookmarked_description).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                                title = stringResource(R.string.settings_enable_autodownload_song_when_album_bookmarked),
                                text = stringResource(R.string.auto_download_when_album_bookmarked_description),
                                isChecked = autoDownloadSongWhenAlbumBookmarked,
                                onCheckedChange = {
                                    autoDownloadSongWhenAlbumBookmarked = it
                                },
                                icon = R.drawable.bookmark
                            )
                        }
                    }
                }
            }
        )

        
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
                    
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_restore_default_settings).contains(search.inputValue, true) || stringResource(R.string.settings_reset).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.settings_reset),
                            text = stringResource(R.string.settings_restore_default_settings),
                            icon = R.drawable.refresh,
                            onClick = { 
                                resetToDefault = true
                                restartService = true
                                PreferredStreamClientDialog.reset(context)
                                StreamClientsSettingsDialog.reset(context)
                                Toaster.done()
                            }
                        )
                    }

                    if (resetToDefault) {
                        DefaultNetworkSettings()
                        LaunchedEffect(Unit) {
                            resetToDefault = false
                        }
                    }
                }
            )
        }
        
        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )
        
    }
}



