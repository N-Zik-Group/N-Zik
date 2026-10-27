package app.it.fast4x.rimusic.ui.components.navigation.nav

import androidx.compose.runtime.CompositionLocalProvider
import app.n_zik.android.uiRoundnessShape

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.enums.UiType
import app.n_zik.android.showSearchIconInNav
import app.n_zik.android.showStatsIconInNav
import app.it.fast4x.rimusic.ui.components.themed.Button
import app.it.fast4x.rimusic.ui.components.themed.TextIconButton
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.n_zik.android.utils.toast.trackBottomNavigationForToasts
import app.n_zik.android.topUiRoundnessShape

// Shown when "Navigation bar position" is set to "top" or "bottom"
class HorizontalNavigationBar(
    val tabIndex: Int,
    val onTabChanged: (Int) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier
): AbstractNavigationBar( navController, modifier ) {

    @Composable
    private fun navButtonProperties(): Modifier {
        val padding: Dp = 4.dp
        val size: Dp = 24.dp
        val border: Shape = uiRoundnessShape()

        return Modifier.padding( all = padding )
                       .size( size )
                       .clip( shape = border )
    }

    @Composable
    private fun bottomPadding(): Dp {
        return if ( NavigationBarPosition.BottomFloating.isCurrent() )
            Dimensions.navBarBottomPadding(true)
        else if ( NavigationBarPosition.Bottom.isCurrent() )
            Dimensions.navBarBottomPadding(false)
        else
            5.dp
    }

    private fun topPadding(): Dp = 0.dp

    @Composable
    override fun add(buttons: @Composable (@Composable (Int, String, Int) -> Unit) -> Unit) {
        val transition = updateTransition(targetState = tabIndex, label = null)
        val isFloating = NavigationBarPosition.BottomFloating.isCurrent()

        buttons { index, text, iconId ->
            buttonList.register(index) {
                val color by transition.animateColor(label = "") {
                    if (it == index) colorPalette().text else colorPalette().textDisabled
                }
                val button: Button =
                    if (NavigationBarType.IconOnly.isCurrent())
                        Button(iconId, color, 12.dp, if (isFloating) 24.dp else 20.dp)
                    else
                        TextIconButton(text, iconId, color, 0.dp, 20.dp, textSpacing = if (isFloating) 12.dp else 5.dp, isCompact = isFloating)

                Box(Modifier.clip(uiRoundnessShape()).clickable { onTabChanged(index) }) {
                    button.Draw()
                }
            }
        }
    }

    @Composable
    override fun BackButton(): NavigationButton {
        val button = super.BackButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun SettingsButton(): NavigationButton {
        val button = super.SettingsButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun StatsButton(): NavigationButton {
        val button = super.StatsButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun SearchButton(): NavigationButton {
        val button = super.SearchButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun Draw() {
        if( buttonList.size < 2 ) return

        val isFloating = NavigationBarPosition.BottomFloating.isCurrent()
        val isIconOnly = NavigationBarType.IconOnly.isCurrent()
        
        val floatingHeight = if (isIconOnly) Dimensions.floatingNavBarIconOnlyHeight else Dimensions.floatingNavBarHeight
        val widthFraction = if (isIconOnly) {
            if (buttonList.size > 5) 0.95f else 0.85f
        } else {
            0.95f
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = modifier.padding( top = topPadding(), bottom = if (isFloating) bottomPadding() else 0.dp )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround,
                modifier = Modifier
                    .then(if (isFloating) Modifier.fillMaxWidth(widthFraction) else Modifier.fillMaxWidth())
                    .height(if (isFloating) floatingHeight else (Dimensions.navigationBarHeight - 10.dp) + bottomPadding())
            ) {

                val scrollState = rememberScrollState()
                // A bar at the top of the screen sits flush under the header: no rounded corners
                val roundedCornerShape = when {
                    isFloating -> uiRoundnessShape()
                    NavigationBarPosition.Top.isCurrent() -> RectangleShape
                    else -> topUiRoundnessShape()
                }

                // Settings button only visible when
                // UI is not RiMusic and current location isn't home screen
                if( UiType.ViMusic.isCurrent() && NavRoutes.home.isNotHere( navController ) )
                    BackButton().Draw()

                val backgroundColor = surfaceColor(colorPalette().background1)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .trackBottomNavigationForToasts(NavigationBarPosition.current().isBottomType, Dimensions.navigationBarHeight - 10.dp)
                        .padding(bottom = if (isFloating) 2.dp else 0.dp)
                        .shadow(elevation = if (isFloating) 8.dp else 0.dp, shape = roundedCornerShape)
                        .clip(roundedCornerShape)
                        .background(if (isFloating) backgroundColor.copy(alpha = 0.95f) else backgroundColor)
                ) {


                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxSize()
                            .padding(horizontal = if (isFloating && buttonList.size > 5) 8.dp else 16.dp)
                            .padding(vertical = if (isFloating) 6.dp else 0.dp)
                            .padding(bottom = if (!isFloating) bottomPadding() else 0.dp)
                            .then(if (!isFloating) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)) else Modifier)
                            .horizontalScroll(scrollState),
                        content = { 
                            val isManyButtons = buttonList.size > 5
                            CompositionLocalProvider(LocalIsManyButtons provides isManyButtons) {
                                buttonList.forEach { it() }
                            }
                        }
                    )
                }

                // Search button only visible when
                // UI is not RiMusic and must be explicitly turned on
                if( UiType.ViMusic.isCurrent() && showSearchIconInNav() )
                    SearchButton()

                // Settings button only visible when
                // UI is not RiMusic
                if( UiType.ViMusic.isCurrent() )
                    SettingsButton().Draw()

                // Statistics button only visible when
                // UI is not RiMusic and must be explicitly turned on
                if( UiType.ViMusic.isCurrent() && showStatsIconInNav() )
                    StatsButton()
            }
        }
    }
}










