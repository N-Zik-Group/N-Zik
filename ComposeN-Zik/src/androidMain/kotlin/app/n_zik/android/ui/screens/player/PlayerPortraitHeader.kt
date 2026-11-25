package app.n_zik.android.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.player.PlayerActionSlot
import app.n_zik.android.components.player.PlayerControlsWidthFraction
import app.n_zik.android.components.player.PlayerPlacedTopAction
import app.n_zik.android.components.player.PlayerTopAction
import app.n_zik.android.components.settings.ui.ToolbarAction
import app.n_zik.android.ui.screens.player.components.PlayerAudioVideoToggle
import app.n_zik.android.ui.screens.player.components.PlayerMusicVideo
import app.n_zik.android.ui.styling.collapsedPlayerProgressBar

@Composable
internal fun PlayerPortraitHeader(
    showTopActionsBar: Boolean,
    actions: List<String>,
    placedActions: Map<String, PlayerActionSlot>,
    fixedSlots: Boolean,
    topPadding: Boolean,
    showMenu: Boolean,
    windowInsets: WindowInsets,
    glassSettings: GlassSettings,
    rotationAngle: Float,
    showVideoMode: Boolean,
    toggleSession: PlayerMusicVideo?,
    videoInteractive: Boolean,
    openManualVideoSearch: () -> Unit,
    onDismiss: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    if (showTopActionsBar) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .padding(
                    windowInsets
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        .asPaddingValues()
                )
                .fillMaxWidth(PlayerControlsWidthFraction)
                .height(44.dp)
        ) {

            @Composable
            fun Control(action: String) {
                when (action) {
                    ToolbarAction.COLLAPSE_PLAYER.id -> PlayerTopAction(
                        icon = R.drawable.chevron_down,
                        color = colorPalette().collapsedPlayerProgressBar,
                        rotation = rotationAngle,
                        settings = glassSettings,
                        contentDescription = stringResource(R.string.common_action_close),
                        onClick = onDismiss,
                    )
                    ToolbarAction.AUDIO_VIDEO.id -> if (showVideoMode && toggleSession != null) {
                        PlayerAudioVideoToggle(
                            toggleSession,
                            onSearchManually = openManualVideoSearch,
                            interactive = videoInteractive,
                        )
                    }
                    ToolbarAction.MENU.id -> if (showMenu) {
                        PlayerTopAction(
                            icon = R.drawable.ellipsis_vertical,
                            color = colorPalette().collapsedPlayerProgressBar,
                            rotation = rotationAngle,
                            settings = glassSettings,
                            contentDescription = stringResource(R.string.common_action_show_more),
                            onClick = onOpenMenu,
                        )
                    } else Spacer(Modifier.size(44.dp))
                    else -> placedActions[action]?.let { PlayerPlacedTopAction(it, glassSettings) }
                }
            }

            actions.forEachIndexed { index, action ->
                key(index, action) {
                    if (fixedSlots) {
                        val alignment = when (index) {
                            0 -> Alignment.CenterStart
                            1 -> Alignment.Center
                            else -> Alignment.CenterEnd
                        }
                        Box(Modifier.weight(1f), contentAlignment = alignment) { Control(action) }
                    } else {
                        Control(action)
                    }
                }
            }
        }
        Spacer(
            modifier = Modifier
                .height(5.dp)
                .padding(
                    windowInsets
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        .asPaddingValues()
                )
        )
    }

    if (topPadding && !showTopActionsBar) {
        Spacer(
            modifier = Modifier
                .padding(
                    windowInsets
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        .asPaddingValues()
                )
                .height(35.dp)
        )
    }
}
