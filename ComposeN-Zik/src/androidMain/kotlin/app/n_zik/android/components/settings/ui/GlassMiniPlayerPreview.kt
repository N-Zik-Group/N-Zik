package app.n_zik.android.components.settings.ui

import app.n_zik.android.components.glass.glassMiniPlayerBackground
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.miniPlayerButtonOrderKey
import app.it.fast4x.rimusic.utils.playerControlsColorsKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.showMiniPlayerAddToPlaylistKey
import app.it.fast4x.rimusic.utils.showMiniPlayerAudioOutputKey
import app.it.fast4x.rimusic.utils.showMiniPlayerDiscoverKey
import app.it.fast4x.rimusic.utils.showMiniPlayerDownloadKey
import app.it.fast4x.rimusic.utils.showMiniPlayerLikeKey
import app.it.fast4x.rimusic.utils.showMiniPlayerLyricsKey
import app.it.fast4x.rimusic.utils.showMiniPlayerPlayPauseKey
import app.it.fast4x.rimusic.utils.showMiniPlayerQueueKey
import app.it.fast4x.rimusic.utils.showMiniPlayerRadioKey
import app.it.fast4x.rimusic.utils.showMiniPlayerRepeatKey
import app.it.fast4x.rimusic.utils.showMiniPlayerShareKey
import app.it.fast4x.rimusic.utils.showMiniPlayerShuffleKey
import app.it.fast4x.rimusic.utils.showMiniPlayerSkipBackKey
import app.it.fast4x.rimusic.utils.showMiniPlayerSkipForwardKey
import app.it.fast4x.rimusic.utils.showMiniPlayerSleepTimerKey
import app.it.fast4x.rimusic.utils.showMiniPlayerVideoKey
import app.it.fast4x.rimusic.utils.showMiniPlayerVisualizerKey
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassArtworkColor
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.player.GlassMiniPlayerIconButton
import app.n_zik.android.components.player.GlassMiniPlayerPlayPauseButton
import app.n_zik.android.components.player.monochromeControlsColor
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.enums.MiniPlayerButton
import app.n_zik.android.enums.PlayerControlsColors
import app.n_zik.android.components.glass.glassImageRim
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import org.json.JSONArray
import org.json.JSONException
import timber.log.Timber

private data class PreviewButton(val id: String, val button: MiniPlayerButton, val preference: String)

// Keep the same order and defaults as MiniPlayer and its button settings dialog.
private val previewButtons = listOf(
    PreviewButton("skip_back", MiniPlayerButton.SkipBack, showMiniPlayerSkipBackKey),
    PreviewButton("play_pause", MiniPlayerButton.PlayPause, showMiniPlayerPlayPauseKey),
    PreviewButton("skip_forward", MiniPlayerButton.SkipForward, showMiniPlayerSkipForwardKey),
    PreviewButton("shuffle", MiniPlayerButton.Shuffle, showMiniPlayerShuffleKey),
    PreviewButton("repeat", MiniPlayerButton.Repeat, showMiniPlayerRepeatKey),
    PreviewButton("queue", MiniPlayerButton.Queue, showMiniPlayerQueueKey),
    PreviewButton("audio_output", MiniPlayerButton.AudioOutput, showMiniPlayerAudioOutputKey),
    PreviewButton("sleep_timer", MiniPlayerButton.SleepTimer, showMiniPlayerSleepTimerKey),
    PreviewButton("like", MiniPlayerButton.Like, showMiniPlayerLikeKey),
    PreviewButton("add_to_playlist", MiniPlayerButton.AddToPlaylist, showMiniPlayerAddToPlaylistKey),
    PreviewButton("download", MiniPlayerButton.Download, showMiniPlayerDownloadKey),
    PreviewButton("share", MiniPlayerButton.Share, showMiniPlayerShareKey),
    PreviewButton("radio", MiniPlayerButton.Radio, showMiniPlayerRadioKey),
    PreviewButton("discover", MiniPlayerButton.Discover, showMiniPlayerDiscoverKey),
    PreviewButton("lyrics", MiniPlayerButton.Lyrics, showMiniPlayerLyricsKey),
    PreviewButton("visualizer", MiniPlayerButton.Visualizer, showMiniPlayerVisualizerKey),
    PreviewButton("video", MiniPlayerButton.Video, showMiniPlayerVideoKey),
)
private val defaultPreviewButtons = setOf(
    MiniPlayerButton.SkipBack, MiniPlayerButton.PlayPause,
    MiniPlayerButton.SkipForward, MiniPlayerButton.AudioOutput,
)

private fun previewButtonOrder(serialized: String): List<PreviewButton> {
    if (serialized.isBlank()) return previewButtons
    return try {
        val array = JSONArray(serialized)
        val ids = List(array.length()) { array.getString(it) }
        val ordered = ids.mapNotNull { id -> previewButtons.find { it.id == id } }
        ordered + previewButtons.filter { it !in ordered }
    } catch (error: JSONException) {
        Timber.tag("GlassMiniPlayerPreview").w(error, "Invalid mini-player button order")
        previewButtons
    }
}

@Composable
private fun rememberPreviewMetadata(player: Player?): MediaMetadata {
    var metadata by remember(player) { mutableStateOf(player?.mediaMetadata ?: MediaMetadata.EMPTY) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                metadata = player.mediaMetadata
            }
        }
        player?.addListener(listener)
        metadata = player?.mediaMetadata ?: MediaMetadata.EMPTY
        onDispose { player?.removeListener(listener) }
    }
    return metadata
}

@Composable
private fun rememberPreviewPlayer(): Player? {
    val binder = LocalPlayerServiceBinder.current
    val generation by binder?.playerUpdateTrigger?.collectAsStateWithLifecycle(0)
        ?: remember { mutableStateOf(0) }
    return remember(binder, generation) { binder?.player }
}

/** Renders NZik controls without invoking playback, navigation or settings mutations. */
@Composable
internal fun GlassMiniPlayerPreview(
    config: GlassSettings,
    modifier: Modifier = Modifier,
    player: Player? = rememberPreviewPlayer(),
) {
    val serialized by rememberPreference(miniPlayerButtonOrderKey, "")
    val order = remember(serialized) { previewButtonOrder(serialized) }
    val enabled = previewButtons.associate { definition ->
        val visible by rememberPreference(definition.preference, definition.button in defaultPreviewButtons)
        definition.button to visible
    }
    val buttons = order.filter { enabled[it.button] == true }.map { it.button }
    val metadata = rememberPreviewMetadata(player)
    val title = cleanPrefix(metadata.title?.toString().orEmpty())
        .ifBlank { stringResource(R.string.unknown_title) }
    val artist = cleanPrefix(metadata.artist?.toString().orEmpty())
        .ifBlank { stringResource(R.string.unknown_artist) }
    var playing by rememberSaveable { mutableStateOf(false) }
    val settings = config
    val colors = colorPalette()
    val controlsColors by rememberPreference(playerControlsColorsKey, PlayerControlsColors.Monochrome)
    val controlsColor = when (controlsColors) {
        PlayerControlsColors.Monochrome -> monochromeControlsColor(colors)
        PlayerControlsColors.Cover -> LocalGlassArtworkColor.current ?: colors.accent
        else -> colors.accent
    }
    val backdrop = LocalGlassBackdrop.current
    val shape = uiRoundnessShape()
    val height = Dimensions.miniPlayerHeight

    BoxWithConstraints(modifier.fillMaxWidth().testTag("settings.glass.miniPlayer")) {
        // Scale the native row together so a narrow preview still shows the song and all configured controls.
        val nativeWidth = maxOf(maxWidth, (164 + buttons.size * 58).dp)
        val scale = (maxWidth / nativeWidth).coerceAtMost(1f)
        Box(Modifier.fillMaxWidth().height(height * scale), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.requiredWidth(nativeWidth).height(height)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(shape)
                    .glassMiniPlayerBackground(settings, shape, backdrop, colors.background2)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(thumbnailShape())) {
                    if (metadata.artworkUri != null) {
                        ImageCacheFactory.Thumbnail(
                            thumbnailUrl = metadata.artworkUri.toString(),
                            contentDescription = title,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Image(painterResource(R.drawable.ic_launcher_box), title,
                            Modifier.fillMaxSize().glassImageRim(thumbnailShape()))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    BasicText(title, style = typography().xxs.semiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(artist, style = typography().xxs.semiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    buttons.forEach { button ->
                        when (button) {
                            MiniPlayerButton.PlayPause -> GlassMiniPlayerPlayPauseButton(
                                onClick = { playing = !playing },
                                backgroundColor = colors.background2,
                                contentDescription = stringResource(if (playing) R.string.notification_pause else R.string.play),
                                settings = settings,
                                backdrop = backdrop,
                            ) {
                                Image(
                                    painterResource(if (playing) R.drawable.pause else R.drawable.play),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(controlsColor),
                                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                                )
                            }
                            MiniPlayerButton.SkipBack, MiniPlayerButton.SkipForward, MiniPlayerButton.AudioOutput ->
                                GlassMiniPlayerIconButton(
                                    icon = button.iconRes,
                                    color = controlsColor,
                                    onClick = {},
                                    contentDescription = stringResource(button.labelRes),
                                    settings = settings,
                                    backdrop = backdrop,
                                )
                            else -> {
                                val description = stringResource(button.labelRes)
                                IconButton(
                                    icon = button.iconRes,
                                    color = controlsColor,
                                    onClick = {},
                                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp)
                                        .size(24.dp).semantics { contentDescription = description },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
