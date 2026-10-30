package app.n_zik.android.components.navigation.header

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.it.fast4x.rimusic.enums.NavRoutes
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.ui.header.VerticalAppBrand
import app.n_zik.android.core.navigation.currentSearchShortcutRoute
import app.n_zik.android.core.navigation.rememberAppBackAction
import app.n_zik.android.core.navigation.navigationChromeTween

/** Matches the horizontal header's home/queue back-button policy. */
internal fun sideHeaderShowsBack(route: String?): Boolean =
    route != null && !route.startsWith(NavRoutes.home.name) && route != NavRoutes.queue.name

/** Vertical branding followed by the back action and connected search/profile controls. */
@Composable
fun SideAppHeaderControls(navController: NavController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val onAppBack = rememberAppBackAction(navController)
    val route = currentSearchShortcutRoute(navController)
    CompositionLocalProvider(LocalContentColor provides colorPalette().text) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            VerticalAppBrand(navController, context)
            Spacer(Modifier.height(16.dp))
            SideHeaderBackSlot(visible = sideHeaderShowsBack(route)) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    HeaderIcon(R.drawable.chevron_back, onClick = onAppBack)
                }
            }
            ActionBar(navController, vertical = true)
        }
    }
}

/** Expanding the slot moves the persistent action group continuously with Back. */
@Composable
internal fun SideHeaderBackSlot(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(navigationChromeTween()) +
            expandVertically(navigationChromeTween(), expandFrom = Alignment.Top),
        exit = fadeOut(navigationChromeTween()) +
            shrinkVertically(navigationChromeTween(), shrinkTowards = Alignment.Top),
    ) { content() }
}
