package app.n_zik.android.components.menu

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import app.n_zik.android.components.navigation.COLLECTION_SOURCE_ONLY_ARGUMENT
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon

/** Opens the exact entity already represented by a menu, without duplicating its detail page. */
internal class OpenEntityMenuAction(
    private val navController: NavController,
    private val menuState: MenuState,
    private val destination: NavRoutes,
    private val entityId: String,
    @param:DrawableRes override val iconId: Int,
    @param:StringRes override val messageId: Int,
    private val route: String = "${destination.name}/${Uri.encode(entityId)}",
    private val onNavigate: () -> Unit = {},
) : MenuIcon, Descriptive {
    @get:Composable
    override val menuIconTitle: String
        get() = stringResource(messageId)

    override fun onShortClick() {
        navigateToMenuEntity(navController, menuState, destination, entityId, route, onNavigate)
    }
}

/** Publish the destination before dismissing the menu so the origin is never exposed while resolving a link. */
internal fun navigateToMenuEntity(
    navController: NavController,
    menuState: MenuState,
    destination: NavRoutes,
    entityId: String,
    route: String = "${destination.name}/${Uri.encode(entityId)}",
    onNavigate: () -> Unit = {},
    reopenCurrent: Boolean = false,
) {
    if (entityId.isBlank()) return

    val current = navController.currentBackStackEntry
    val currentRoute = current?.destination?.route
    val arguments = current?.arguments?.takeIf { currentRoute?.startsWith("${destination.name}/") == true }
    val isLocalPlaylist = destination == NavRoutes.localPlaylist
    val currentId = if (isLocalPlaylist) arguments?.getLong("id")?.toString() else arguments?.getString("id")
    val sameSource = !isLocalPlaylist ||
        (arguments?.getBoolean("onDevice") == true) == Uri.parse(route).getBooleanQueryParameter("onDevice", false)
    if ((reopenCurrent && arguments?.getBoolean(COLLECTION_SOURCE_ONLY_ARGUMENT) != true) || !sameSource || !isCurrentMenuEntity(destination, entityId, currentRoute, currentId)) {
        navController.navigate(route)
    }
    onNavigate()
    menuState.hide()
}

internal fun MenuState.isCurrentMenu(key: Int): Boolean = isDisplayed && transitionKey == key

internal fun isCurrentMenuEntity(
    destination: NavRoutes,
    entityId: String,
    currentRoute: String?,
    currentEntityId: String?,
): Boolean = currentEntityId == entityId && currentRoute?.startsWith("${destination.name}/") == true
