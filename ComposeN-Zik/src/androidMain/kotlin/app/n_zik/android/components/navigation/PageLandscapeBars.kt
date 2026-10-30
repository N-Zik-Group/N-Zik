package app.n_zik.android.components.navigation

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.it.fast4x.rimusic.utils.LandscapeBars
import app.it.fast4x.rimusic.utils.hidesBarsInLandscape
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.n_zik.android.core.navigation.currentPageNavigationEntry
import app.n_zik.android.extensions.discord.DiscordUiState
import app.n_zik.android.utils.coroutines.NzikDispatchers

/** Keeps outgoing page geometry tied to its own entry during navigation transitions. */
@Composable
internal fun hidePageBarsInLandscapeMobile(): Boolean {
    if (sideNavigationOwnsHeader(NavigationBarPosition.current())) return false
    val configuration = LocalConfiguration.current
    val entry = currentPageNavigationEntry()
    val route = if (entry != null) entry.destination.route else {
        val currentRoute by DiscordUiState.currentRoute.collectAsStateWithLifecycle(context = NzikDispatchers.DATA)
        currentRoute
    }
    val homeTab by DiscordUiState.currentHomeTab.collectAsStateWithLifecycle(context = NzikDispatchers.DATA)
    return hidesBarsInLandscape(
        isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        smallestScreenWidthDp = configuration.smallestScreenWidthDp,
        route = route,
        homeTab = homeTab,
    ) && !LandscapeBars.revealed
}
