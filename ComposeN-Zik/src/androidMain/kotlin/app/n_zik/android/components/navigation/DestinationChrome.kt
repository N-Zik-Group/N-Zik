package app.n_zik.android.components.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.components.glass.LocalGlassScope
import app.n_zik.android.components.glass.glassScopeForRoute

/** Each animated destination retains its own inset policy until it leaves composition. */
internal fun NavGraphBuilder.chromeComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(route = route, arguments = arguments, deepLinks = deepLinks) { entry ->
        DestinationChrome(entry.destination.route) { content(entry) }
    }
}

/** Reads live geometry but selects ownership from the rendered entry, never the navigation target. */
@Composable
internal fun DestinationChrome(route: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassScope provides glassScopeForRoute(route)) {
        DestinationChromeInsets(route, content)
    }
}

@Composable
private fun DestinationChromeInsets(route: String?, content: @Composable () -> Unit) {
    // The Rewind deck owns its safe areas and must remain full screen during header motion.
    if (route == "rewind" || route?.startsWith("rewind?") == true) {
        Box(Modifier.fillMaxSize()) { content() }
        return
    }
    val position = NavigationBarPosition.current()
    val ownsInsets = pageOwnsChromeInsets(route)
    val hasSideHeader = sideNavigationOwnsHeader(position)
    val ownsTopInset = ownsInsets || (hasSideHeader && sidePageOwnsTopInset(route))
    val headerInset = LocalAppHeaderContentInset.current
    val headerOverlayInset = LocalAppHeaderOverlayInset.current
    val playerInsets = LocalPlayerAwareWindowInsets.current
    val density = LocalDensity.current
    val left = if (hasSideHeader && position == NavigationBarPosition.Left) Dimensions.navigationRailWidth else 0.dp
    val right = if (hasSideHeader && position == NavigationBarPosition.Right) Dimensions.navigationRailWidth else 0.dp
    val railInsets = if (ownsInsets) WindowInsets(left = left, right = right) else WindowInsets(0, 0, 0, 0)

    CompositionLocalProvider(
        LocalSideRailContentInsets provides railInsets,
        LocalPlayerAwareWindowInsets provides playerInsets.add(railInsets),
        LocalAppHeaderContentInset provides { if (ownsTopInset) headerInset() else 0.dp },
        LocalAppHeaderOverlayInset provides { if (ownsInsets) headerOverlayInset() else 0.dp },
    ) {
        Box(
            Modifier.fillMaxSize()
                .absolutePadding(left = if (ownsInsets) 0.dp else left, right = if (ownsInsets) 0.dp else right)
                .then(if (ownsTopInset) Modifier else Modifier.appHeaderViewport {
                    with(density) { headerInset().roundToPx() }
                })
        ) { content() }
    }
}
