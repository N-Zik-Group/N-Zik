package app.n_zik.android.components.navigation.header

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import app.n_zik.android.core.navigation.navigationChromeTween

/** Glass fades at its final anchor while the title's reserved space changes. */
@Composable
internal fun HeaderBackSlot(visible: Boolean, glass: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(navigationChromeTween()) +
            (if (glass) EnterTransition.None else slideInHorizontally(navigationChromeTween()) { -it }) +
            expandHorizontally(navigationChromeTween(), expandFrom = if (glass) Alignment.Start else Alignment.End,
                clip = !glass),
        exit = fadeOut(navigationChromeTween()) +
            (if (glass) ExitTransition.None else slideOutHorizontally(navigationChromeTween()) { -it }) +
            shrinkHorizontally(navigationChromeTween(), shrinkTowards = if (glass) Alignment.Start else Alignment.End,
                clip = !glass),
    ) { content() }
}
