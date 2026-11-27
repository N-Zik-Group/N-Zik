package app.n_zik.android.components.menu.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.colorPalette
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.rememberPreference

@Composable
fun SettingIcon(@DrawableRes icon: Int) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .iconBadgeBackground(
                color = colorPalette().accent.copy(alpha = 0.1f),
                shape = uiRoundnessShape()
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            tint = colorPalette().accent,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@ExperimentalTextApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun PlayerMenu(
    navController: NavController,
    binder: PlayerServiceModern.Binder,
    mediaItem: MediaItem,
    onDismiss: () -> Unit,
    onClosePlayer: () -> Unit,
    onMatchingSong: (() -> Unit)? = null,
    onShowSleepTimer: () -> Unit,
    disableScrollingText: Boolean,
    statsMenuControl: State<PlayerStatsMenuControl?>? = null,
    ) {
    
    val menuState = LocalMenuState.current
    val styleState = rememberPreference(menuStyleKey, MenuStyle.List)

    val menu = remember(mediaItem.mediaId, binder, menuState, styleState, statsMenuControl) {
        PlayerItemMenu.create(
            navController = navController,
            binder = binder,
            mediaItem = mediaItem,
            menuState = menuState,
            styleState = styleState,
            onDismiss = onDismiss,
            onClosePlayer = onClosePlayer,
            onShowSleepTimer = onShowSleepTimer,
            statsMenuControl = statsMenuControl,
        )
    }
    
    menu.MenuComponent()
}
