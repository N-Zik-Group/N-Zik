package app.n_zik.android.listentogether

import androidx.navigation.NavController
import app.it.fast4x.rimusic.enums.NavRoutes

/** Opens the room screen and delivers an invitation to its existing saved-state handle. */
internal fun openListenTogetherInvite(navController: NavController, roomCode: String) {
    if (navController.currentDestination?.route != NavRoutes.listenTogether.name) {
        navController.navigate(NavRoutes.listenTogether.name) { launchSingleTop = true }
    }
    navController.currentBackStackEntry?.savedStateHandle?.set(INVITE_ROOM_CODE_KEY, roomCode)
}
