package app.n_zik.android.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.player.PlayerControlsWidthFraction
import app.n_zik.android.components.player.PlayerTopAction
import app.n_zik.android.ui.screens.player.components.PlayerAudioVideoToggle
import app.n_zik.android.ui.screens.player.components.PlayerMusicVideo
import app.n_zik.android.ui.styling.collapsedPlayerProgressBar

@Composable
internal fun PlayerPortraitHeader(
    showTopActionsBar: Boolean,
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
                //.padding(top = 5.dp)
                .fillMaxWidth(PlayerControlsWidthFraction)
                .height(44.dp)
        ) {

            PlayerTopAction(
                icon = R.drawable.chevron_down,
                color = colorPalette().collapsedPlayerProgressBar,
                rotation = rotationAngle,
                settings = glassSettings,
                onClick = onDismiss,
            )

            if (showVideoMode && toggleSession != null) {
                PlayerAudioVideoToggle(
                    toggleSession,
                    onSearchManually = openManualVideoSearch,
                    interactive = videoInteractive,
                )
            }

            if (showMenu) {
                PlayerTopAction(
                    icon = R.drawable.ellipsis_vertical,
                    color = colorPalette().collapsedPlayerProgressBar,
                    rotation = rotationAngle,
                    settings = glassSettings,
                    onClick = onOpenMenu,
                )
            } else {
                Spacer(Modifier.size(44.dp))
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
