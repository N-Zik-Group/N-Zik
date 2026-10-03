@file:OptIn(UnstableApi::class)

package app.it.fast4x.rimusic.utils

import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.enums.PlayerBackgroundColors
import app.it.fast4x.rimusic.enums.PlayerControlsType
import app.it.fast4x.rimusic.enums.PlayerPlayButtonType
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.components.menu.player.PlaybackSettingsMenu
import app.n_zik.android.ui.saveable.TolerantBoolStateSaver
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.screens.player.components.controls.ControlsEssential
import app.it.fast4x.rimusic.ui.screens.player.components.controls.ControlsModern
import kotlin.math.roundToInt
import app.it.fast4x.rimusic.ui.styling.ColorPalette

@Composable
fun GetControls(
    binder: PlayerServiceModern.Binder,
    position: () -> Long,
    shouldBePlaying: Boolean,
    isBuffering: Boolean,
    likedAt: Long?,
    mediaId: String,
    onBlurScaleChange: (Float) -> Unit,
    dynamicColorPalette: ColorPalette
) {
    val playerControlsType by rememberPreference(playerControlsTypeKey, PlayerControlsType.Essential)
    val playerPlayButtonType by rememberPreference(
        playerPlayButtonTypeKey,
        PlayerPlayButtonType.CircularRibbed
    )
    // Issue #881 (gh-881): tolerant saver (spec M5).
    var isRotated by rememberSaveable(saver = TolerantBoolStateSaver("getControls.isRotated")) { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isRotated) 360F else 0f,
        animationSpec = tween(durationMillis = 200), label = ""
    )
    val playerBackgroundColors by rememberPreference(
        playerBackgroundColorsKey,
        PlayerBackgroundColors.AnimatedGradient
    )

    val isGradientBackgroundEnabled = playerBackgroundColors == PlayerBackgroundColors.ThemeColorGradient ||
            playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient

    var playbackSpeed by rememberPreference(playbackSpeedKey, 1f)
    var playbackDuration by rememberPreference(playbackDurationKey, 0f)
    var setPlaybackDuration by remember { mutableStateOf(false) }

    val menuState = LocalMenuState.current
    val menuStyle = rememberPreference(menuStyleKey, MenuStyle.List)

    val playbackSettingsMenu = remember(binder, menuState, menuStyle) {
        PlaybackSettingsMenu.create(
            binder = binder,
            menuState = menuState,
            styleState = menuStyle,
            onDismiss = { menuState.hide() },
            onBlurScaleChange = onBlurScaleChange
        )
    }


        MedleyMode(
            binder = binder,
            seconds = if (playbackDuration < 1f) 0 else playbackDuration.roundToInt()
        )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
    ) {

        if (playerControlsType == PlayerControlsType.Essential)
            ControlsEssential(
                binder = binder,
                position = position,
                playbackSpeed = playbackSpeed,
                shouldBePlaying = shouldBePlaying,
                isBuffering = isBuffering,
                likedAt = likedAt,
                mediaId = mediaId,
                playerPlayButtonType = playerPlayButtonType,
                isGradientBackgroundEnabled = isGradientBackgroundEnabled,
                onShowSpeedPlayerDialog = { menuState.display { playbackSettingsMenu.MenuComponent() } },
                dynamicColorPalette = dynamicColorPalette
            )

        if (playerControlsType == PlayerControlsType.Modern)
            ControlsModern(
                binder = binder,
                position = position,
                playbackSpeed = playbackSpeed,
                shouldBePlaying = shouldBePlaying,
                isBuffering = isBuffering,
                playerPlayButtonType = playerPlayButtonType,
                isGradientBackgroundEnabled = isGradientBackgroundEnabled,
                onShowSpeedPlayerDialog = { menuState.display { playbackSettingsMenu.MenuComponent() } },
                dynamicColorPalette = dynamicColorPalette
            )
    }
}


