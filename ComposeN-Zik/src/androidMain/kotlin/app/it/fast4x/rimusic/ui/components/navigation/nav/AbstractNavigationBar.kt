package app.it.fast4x.rimusic.ui.components.navigation.nav

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import androidx.compose.runtime.staticCompositionLocalOf

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.components.tab.NavigationButtonSlots
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.ui.components.themed.Button
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.disableNavigationBackStackKey
import app.it.fast4x.rimusic.utils.rememberPreference

val LocalIsManyButtons = staticCompositionLocalOf { false }

@SuppressLint("ComposableNaming")
abstract class AbstractNavigationBar(
    val navController: NavController,
    val modifier: Modifier = Modifier
) {
    internal val buttonList = NavigationButtonSlots<@Composable () -> Unit>()

    @Composable
    internal open fun BackButton(): NavigationButton {
        val disableBackStack by rememberPreference(disableNavigationBackStackKey, false)
        val button = NavigationButton( navController, R.drawable.chevron_back, colorPalette().favoritesIcon )
        button.clickEvent {
            if ( navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED ) {
                if (disableBackStack) {
                    navController.popBackStack(NavRoutes.home.name, inclusive = false)
                } else {
                    navController.popBackStack()
                }
            }
        }
        return button
    }

    @ReadOnlyComposable
    @Composable
    internal open fun SettingsButton(): NavigationButton {
        return NavigationButton(
            navController,
            R.drawable.settings,
            colorPalette().favoritesIcon,
            NavRoutes.settings.name
        )
    }

    @ReadOnlyComposable
    @Composable
    internal open fun StatsButton(): NavigationButton {
        return NavigationButton(
            navController,
            R.drawable.stats_chart,
            colorPalette().textSecondary,
            NavRoutes.statistics.name
        )
    }

    @ReadOnlyComposable
    @Composable
    internal open fun SearchButton(): NavigationButton {
        return NavigationButton(
            navController,
            R.drawable.search,
            colorPalette().textSecondary,
            NavRoutes.search.name
        )
    }

    @Composable
    abstract fun add( buttons: @Composable (@Composable (Int, String, Int) -> Unit) -> Unit )

    @Composable
    abstract fun Draw()
}

internal class NavigationButton(
    val navController: NavController,
    iconId: Int,
    color: Color,
    val destination: String = "",
    padding: Dp = 0.dp,
    size: Dp = 0.dp,
    forceWidth: Dp = Dp.Unspecified,
    modifier: Modifier = Modifier
): Button( iconId, color, padding, size, forceWidth, modifier ) {

    fun clickEvent( event: (NavigationButton) -> Unit ) {
        modifier = modifier.clickable { event(this@NavigationButton) }
    }

    @Composable
    override fun Draw() {
        if( destination.isNotBlank() )
            clickEvent { navController.navigate( destination ) }

        super.Draw()
    }
}
