package app.n_zik.android.components.settings.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import app.n_zik.android.cleanPrefix
import app.n_zik.android.ui.components.themed.IconButton
import app.n_zik.android.ui.screens.player.rememberMiniPlayerButtons
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.utils.playerControlsColorsKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.glassArtworkColor
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.glassMiniPlayerBackground
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.player.miniPlayerButtonSpacing
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
    val buttons = rememberMiniPlayerButtons()
    val metadata = rememberPreviewMetadata(player)
    val title = cleanPrefix(metadata.title?.toString().orEmpty())
        .ifBlank { stringResource(R.string.metadata_unknown_title) }
    val artist = cleanPrefix(metadata.artist?.toString().orEmpty())
        .ifBlank { stringResource(R.string.metadata_unknown_artist) }
    var playing by rememberSaveable { mutableStateOf(false) }
    val settings = config
    val colors = colorPalette()
    val controlsColors by rememberPreference(playerControlsColorsKey, PlayerControlsColors.Monochrome)
    val controlsColor = when (controlsColors) {
        PlayerControlsColors.Monochrome -> monochromeControlsColor(colors)
        PlayerControlsColors.Cover -> glassArtworkColor() ?: colors.accent
        else -> colors.accent
    }
    val backdrop = LocalGlassBackdrop.current
    val stageBackdrop = rememberPreviewStageBackdrop(backdrop)
    val material = rememberLayerBackdrop()
    val exportedMaterial = material.takeIf { settings.usesGlassMaterial(GlassArea.MiniPlayer) }
    val controlsBackdrop = exportedMaterial ?: backdrop
    val shape = uiRoundnessShape()
    val height = Dimensions.miniPlayerHeight

    BoxWithConstraints(modifier.fillMaxWidth().testTag("settings.glass.miniPlayer")) {
        // Scale the native row together so a narrow preview still shows the song and all configured controls.
        val nativeWidth = maxOf(maxWidth, (164 + buttons.size * 58).dp)
        val scale = (maxWidth / nativeWidth).coerceAtMost(1f)
        Box(Modifier.fillMaxWidth().height(height * scale), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.requiredSize(nativeWidth, height)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(shape)
                    .glassMiniPlayerBackground(settings, shape, stageBackdrop, colors.background2,
                        exportedBackdrop = exportedMaterial)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Export only the row material; its own input remains the preview stage.
                CompositionLocalProvider(LocalGlassBackdrop provides controlsBackdrop) {
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
                    val buttonSpacing = miniPlayerButtonSpacing(settings.usesGlassMaterial(GlassArea.MiniPlayer))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        buttons.forEach { button ->
                            when (button) {
                                MiniPlayerButton.PlayPause -> GlassMiniPlayerPlayPauseButton(
                                    onClick = { playing = !playing },
                                    backgroundColor = colors.background2,
                                    contentDescription = stringResource(if (playing) R.string.player_notification_pause else R.string.player_action_play),
                                    settings = settings,
                                    backdrop = controlsBackdrop,
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
                                        backdrop = controlsBackdrop,
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
}
