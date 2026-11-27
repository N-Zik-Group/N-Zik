@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)
package app.n_zik.android.ui.screens.player

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.LocalIsShowingLyrics
import app.n_zik.android.LocalIsShowingVisualizer
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.discoverButtonIconRes
import app.n_zik.android.ui.components.themed.PlaylistsMenu
import app.n_zik.android.components.player.GlassMiniPlayerIconButton
import app.n_zik.android.components.player.rememberPlayerSkipAvailability
import app.n_zik.android.components.radioButtonIcon
import app.n_zik.android.components.shuffleButtonIcon
import app.n_zik.android.core.navigation.LocalMiniPlayerQueueRequest
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.enums.MiniPlayerButton
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.PendingMiniPlayerAction
import app.n_zik.android.enums.QueueLoopType
import app.n_zik.android.listentogether.guestLockedAlpha
import app.n_zik.android.playback.services.AudioOutputManager
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.playback.utils.playNextIfAvailable
import app.n_zik.android.playback.utils.seekPreviousIfAvailable
import app.n_zik.android.ui.styling.favoritesIcon
import app.n_zik.android.utils.autoLoadSongsInQueueKey
import app.n_zik.android.utils.discoverKey
import app.n_zik.android.utils.getDislikedIcon
import app.n_zik.android.utils.getLikedIcon
import app.n_zik.android.utils.getUnlikedIcon
import app.n_zik.android.utils.intent
import app.n_zik.android.utils.isDownloadedSong
import app.n_zik.android.utils.manageDownload
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.saveLyricsStateKey
import app.n_zik.android.utils.saveVisualizerStateKey
import app.n_zik.android.utils.showLyricsStateKey
import app.n_zik.android.utils.showVisualizerStateKey
import app.n_zik.android.utils.shuffleQueue

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalTextApi::class)
@Composable
internal fun MiniPlayerSlotButton(
    button: MiniPlayerButton?,
    likeState: Boolean?,
    controlsColorTextState: State<Color>,
    rotationAngle: Float,
    onLikeClick: () -> Unit,
    onAudioOutputClick: () -> Unit,
    onShowPlayer: () -> Unit,
    mediaItem: MediaItem?,
    context: Context,
    player: Player,
    binder: PlayerServiceModern.Binder?,
    effectRotationEnabled: Boolean,
    isRotated: Boolean,
    onRotatedChange: (Boolean) -> Unit,
    pendingAction: MutableState<PendingMiniPlayerAction?>?,
    navController: NavController?,
    onClosePlayer: () -> Unit
) {
    if (button == null) return
    val controlsColorText = controlsColorTextState.value
    val requestQueue = LocalMiniPlayerQueueRequest.current

    // Listen Together guest lock (spec-listen-together): guests in a room cannot
    // skip tracks or shuffle; play/pause stays available
    val listenTogetherGuestLock by app.n_zik.android.listentogether.listenTogetherGuestLock
    // Locked controls are also visibly disabled (spec-listen-together-guest-lock-hardening)
    val guestLockAlpha = guestLockedAlpha(listenTogetherGuestLock)
    val skipAvailability by rememberPlayerSkipAvailability(player)


    when (button) {
        MiniPlayerButton.SkipBack -> {
            GlassMiniPlayerIconButton(
                icon = R.drawable.play_skip_back,
                color = controlsColorText,
                contentDescription = stringResource(button.labelRes),
                enabled = !listenTogetherGuestLock && skipAvailability.previous,
                onClick = {
                    if (!listenTogetherGuestLock && player.seekPreviousIfAvailable()) {
                        if (effectRotationEnabled) onRotatedChange(!isRotated)
                    }
                },
                rotationAngle = rotationAngle,
                alpha = if (!listenTogetherGuestLock && skipAvailability.previous) 1f else 0.38f,
            )
        }
        MiniPlayerButton.SkipForward -> {
            GlassMiniPlayerIconButton(
                icon = R.drawable.play_skip_forward,
                color = controlsColorText,
                contentDescription = stringResource(button.labelRes),
                enabled = !listenTogetherGuestLock && skipAvailability.next,
                onClick = {
                    if (!listenTogetherGuestLock) {
                        player.playNextIfAvailable()
                        if (effectRotationEnabled) onRotatedChange(!isRotated)
                    }
                },
                rotationAngle = rotationAngle,
                alpha = if (!listenTogetherGuestLock && skipAvailability.next) 1f else 0.38f,
            )
        }
        MiniPlayerButton.Shuffle -> {
            GlassMiniPlayerIconButton(
                contentDescription = stringResource(button.labelRes),
                preserveNativeGeometry = true,
                // Issue #866 (gh-866): app-wide shuffle confirmation flash (shuffle_ok ~1 s).
                icon = shuffleButtonIcon(),
                color = controlsColorText,
                enabled = !listenTogetherGuestLock,
                onClick = {
                    if (!listenTogetherGuestLock) {
                        player.shuffleQueue()
                        binder?.triggerShuffleOkFlash()
                        if (effectRotationEnabled) onRotatedChange(!isRotated)
                    }
                },
                rotationAngle = rotationAngle,
                alpha = guestLockAlpha
            )
        }
        MiniPlayerButton.Repeat -> {
            var currentRepeatMode by remember { mutableIntStateOf(player.repeatMode) }
            val queueLoopType = QueueLoopType.from(currentRepeatMode)
            GlassMiniPlayerIconButton(
                contentDescription = stringResource(button.labelRes),
                preserveNativeGeometry = true,
                icon = queueLoopType.iconId,
                color = if (queueLoopType != QueueLoopType.Default) colorPalette().accent else controlsColorText,
                enabled = !listenTogetherGuestLock,
                onClick = {
                    if (!listenTogetherGuestLock) {
                        val next = queueLoopType.next()
                        player.repeatMode = next.type
                        currentRepeatMode = next.type
                        if (effectRotationEnabled) onRotatedChange(!isRotated)
                    }
                },
                rotationAngle = rotationAngle,
                alpha = guestLockAlpha
            )
        }
        MiniPlayerButton.Like -> {
            GlassMiniPlayerIconButton(
                contentDescription = stringResource(button.labelRes),
                preserveNativeGeometry = true,
                icon = when(likeState) {
                    false -> getDislikedIcon()
                    null -> getUnlikedIcon()
                    else -> getLikedIcon()
                },
                color = when(likeState) {
                    false -> colorPalette().red
                    else -> colorPalette().favoritesIcon
                },
                onClick = onLikeClick,
                rotationAngle = rotationAngle
            )
        }
        MiniPlayerButton.AddToPlaylist -> {
            val picker = mediaItem?.let { PlaylistsMenu.forSong(it, navController, onClosePlayer) }
            GlassMiniPlayerIconButton(
                contentDescription = stringResource(button.labelRes),
                preserveNativeGeometry = true,
                icon = button.iconRes,
                color = controlsColorText,
                onClick = {
                    picker?.openMenu()
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                rotationAngle = rotationAngle
            )
        }
        MiniPlayerButton.Download -> {
            val isDownloaded = mediaItem?.let { isDownloadedSong(it.mediaId) } ?: false
            GlassMiniPlayerIconButton(
                contentDescription = stringResource(button.labelRes),
                preserveNativeGeometry = true,
                icon = if (isDownloaded) R.drawable.downloaded else R.drawable.download,
                color = if (isDownloaded) colorPalette().accent else controlsColorText,
                onClick = {
                    mediaItem?.let { manageDownload(context, it, isDownloaded) }
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                rotationAngle = rotationAngle
            )
        }
        MiniPlayerButton.Share -> {
            val shareItem = mediaItem?.takeUnless { it.isLocal }
            GlassMiniPlayerIconButton(
                contentDescription = stringResource(button.labelRes),
                preserveNativeGeometry = true,
                icon = R.drawable.share_social,
                color = controlsColorText,
                enabled = shareItem != null,
                onClick = {
                    shareItem?.let { item ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${item.mediaId}")
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                rotationAngle = rotationAngle,
                alpha = if (shareItem != null) 1f else 0.38f,
            )
        }
        MiniPlayerButton.Radio -> {
            val isRadioActive = binder?.isRadioActive == true
            GlassMiniPlayerIconButton(
                contentDescription = stringResource(button.labelRes),
                preserveNativeGeometry = true,
                // Issue #866 (gh-866): state icon — radio_stop while the radio is active
                // (the state change is the press feedback).
                icon = radioButtonIcon(),
                color = if (isRadioActive) colorPalette().accent else controlsColorText,
                // Guest lock: Radio is a stateful ON/OFF control owned by the host
                // (blocked centrally in NZikRadio.startRadio + grayed here).
                enabled = !listenTogetherGuestLock,
                onClick = {
                    if (!listenTogetherGuestLock) mediaItem?.let { binder?.startRadio(it, false, null, true) }
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                rotationAngle = rotationAngle,
                alpha = guestLockAlpha
            )
        }
        MiniPlayerButton.AudioOutput -> {
            val context = LocalContext.current
            val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
            val audioOutputManager = remember { AudioOutputManager(context, audioManager) }
            var activeDevice by remember { mutableStateOf(audioOutputManager.getAvailableDevices().firstOrNull { it.isCurrentlyActive }) }

            DisposableEffect(Unit) {
                audioOutputManager.registerDeviceChanges { devices ->
                    activeDevice = devices.firstOrNull { it.isCurrentlyActive }
                }
                onDispose {
                    audioOutputManager.unregisterDeviceChanges()
                }
            }

            val isExternal = (activeDevice != null && activeDevice?.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER && activeDevice?.type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) || activeDevice?.isCar == true

            val iconVal = activeDevice?.icon ?: R.drawable.devices

            val isAudioOutputEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            val finalColor = if (!isAudioOutputEnabled) {
                controlsColorText.copy(alpha = 0.3f)
            } else if (isExternal) {
                colorPalette().accent
            } else {
                controlsColorText
            }

            val finalIcon = if (isExternal) iconVal else R.drawable.devices
            if (finalIcon is ImageVector) {
                GlassMiniPlayerIconButton(
                    icon = finalIcon,
                    color = finalColor,
                    contentDescription = stringResource(button.labelRes),
                    enabled = true,
                    onClick = onAudioOutputClick,
                    rotationAngle = rotationAngle,
                )
            } else {
                GlassMiniPlayerIconButton(
                    icon = finalIcon as Int,
                    color = finalColor,
                    contentDescription = stringResource(button.labelRes),
                    enabled = true,
                    onClick = onAudioOutputClick,
                    rotationAngle = rotationAngle,
                )
            }
        }
        MiniPlayerButton.SleepTimer -> {
            GlassMiniPlayerIconButton(
                contentDescription = stringResource(button.labelRes),
                preserveNativeGeometry = true,
                icon = R.drawable.sleep,
                color = if (listenTogetherGuestLock) colorPalette().textDisabled else controlsColorText,
                // Guest lock: the sleep timer auto-stops the app — a session-level control,
                // grayed + disabled for a guest (spec-listen-together-guest-lock-hardening).
                enabled = !listenTogetherGuestLock,
                onClick = {
                    if (listenTogetherGuestLock) return@GlassMiniPlayerIconButton
                    pendingAction?.value = PendingMiniPlayerAction.SleepTimer
                    onShowPlayer()
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                rotationAngle = rotationAngle,
                alpha = guestLockAlpha
            )
        }
        MiniPlayerButton.Lyrics -> {
            var isLyricsActive by LocalIsShowingLyrics.current
            var savedLyricsState by rememberPreference(saveLyricsStateKey, false)
            val shouldRememberLyricsState by rememberPreference(showLyricsStateKey, false)

            GlassMiniPlayerIconButton(
                contentDescription = stringResource(button.labelRes),
                preserveNativeGeometry = true,
                icon = R.drawable.song_lyrics,
                color = if (isLyricsActive) colorPalette().accent else controlsColorText,
                onClick = {
                    if (isLyricsActive) {
                        isLyricsActive = false
                        if (shouldRememberLyricsState) savedLyricsState = false
                    } else {
                        pendingAction?.value = PendingMiniPlayerAction.Lyrics
                        onShowPlayer()
                    }
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                rotationAngle = rotationAngle
            )
        }
        MiniPlayerButton.Visualizer -> {
            var isVisualizerActive by LocalIsShowingVisualizer.current
            var savedVisualizerState by rememberPreference(saveVisualizerStateKey, false)
            val shouldRememberVisualizerState by rememberPreference(showVisualizerStateKey, false)

            GlassMiniPlayerIconButton(
                contentDescription = stringResource(button.labelRes),
                preserveNativeGeometry = true,
                icon = R.drawable.sound_effect,
                color = if (isVisualizerActive) colorPalette().accent else controlsColorText,
                onClick = {
                    if (isVisualizerActive) {
                        isVisualizerActive = false
                        if (shouldRememberVisualizerState) savedVisualizerState = false
                    } else {
                        pendingAction?.value = PendingMiniPlayerAction.Visualizer
                        onShowPlayer()
                    }
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                rotationAngle = rotationAngle
            )
        }
        MiniPlayerButton.Queue -> {
            GlassMiniPlayerIconButton(
                contentDescription = stringResource(button.labelRes),
                preserveNativeGeometry = true,
                icon = R.drawable.reorder,
                color = controlsColorText,
                onClick = {
                    requestQueue?.invoke() ?: navController?.navigate(NavRoutes.queue.name)
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                rotationAngle = rotationAngle
            )
        }
        MiniPlayerButton.Discover -> {
            val discoverIsEnabled by rememberPreference(discoverKey, false)
            val isAutoFillEnabled by rememberPreference(autoLoadSongsInQueueKey, true)
            val isDiscoverClickable = binder?.service?.nzikRadio?.isRadioActive == true || isAutoFillEnabled

            GlassMiniPlayerIconButton(
                contentDescription = stringResource(button.labelRes),
                preserveNativeGeometry = true,
                // Issue #866 (gh-866): state icon — discover_stop while discover mode is ON
                // (the state change is the feedback).
                icon = discoverButtonIconRes(discoverIsEnabled),
                color = if (discoverIsEnabled && isDiscoverClickable) colorPalette().accent else controlsColorText,
                // Guest lock: Discover is a stateful ON/OFF control owned by the host
                // (blocked centrally in NZikRadio.toggleDiscover + grayed here).
                enabled = !listenTogetherGuestLock,
                onClick = {
                    if (!listenTogetherGuestLock) binder?.service?.nzikRadio?.toggleDiscover()
                    if (effectRotationEnabled) onRotatedChange(!isRotated)
                },
                onLongClick = { Toaster.i(R.string.queue_discover_description) },
                rotationAngle = rotationAngle,
                alpha = (if (isDiscoverClickable) 1f else 0.4f) * guestLockAlpha
            )
        }
        else -> {}
    }
}
