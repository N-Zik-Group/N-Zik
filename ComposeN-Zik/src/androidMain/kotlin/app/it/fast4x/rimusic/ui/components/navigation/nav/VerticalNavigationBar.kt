package app.it.fast4x.rimusic.ui.components.navigation.nav

import androidx.annotation.OptIn
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.enums.UiType
import app.n_zik.android.showSearchIconInNav
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.themed.Button
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.vertical
import app.n_zik.android.uiRoundnessShape

// Shown when "Navigation bar position" is set to "left" or "right"
class VerticalNavigationBar(
    val tabIndex: Int,
    val onTabChanged: (Int) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier
): AbstractNavigationBar( navController, modifier ) {

    @Composable
    private fun addButton( component: @Composable () -> Unit ) =
        super.buttonList.add( component )

    @OptIn(UnstableApi::class)
    @Composable
    override fun add(buttons: @Composable (@Composable (Int, String, Int) -> Unit) -> Unit ) {
        buttonList.clear()
        val transition = updateTransition( targetState = tabIndex, label = null )
        val isLandscape: Boolean = isLandscape

        buttons { index, text, iconId ->
            val textColor by transition.animateColor(label = "") {
                if (it == index)
                    colorPalette().text
                else
                    colorPalette().textDisabled
            }
            val dothAlpha by transition.animateFloat(label = "") {
                if (it == index)
                    1f
                else
                    0f
            }

            val textContent: @Composable () -> Unit = {
                if ( NavigationBarType.IconAndText.isCurrent() )
                    BasicText(
                        text = text,
                        style = TextStyle(
                            fontSize = typography().xs.semiBold.fontSize,
                            fontWeight = typography().xs.semiBold.fontWeight,
                            color = colorPalette().text,
                        ),
                        modifier = Modifier.vertical( enabled = !isLandscape )
                                           .rotate(if (isLandscape) 0f else -90f)
                                           .padding(horizontal = 16.dp)
                    )
            }

            val buttonModifier: Modifier =
                if ( NavigationBarType.IconOnly.isCurrent() ) {
                    Modifier
                        .padding( top = 12.dp, bottom = 12.dp )
                            .size(24.dp)
                } else {
                    Modifier.vertical( enabled = !isLandscape )
                            .size( Dimensions.navigationRailIconOffset * 3 )
                            .graphicsLayer {
                                alpha = dothAlpha
                                translationX = (1f - dothAlpha) * -48.dp.toPx()
                                rotationZ = if (isLandscape) 0f else -90f
                            }
                }
            val button = Button( iconId, textColor, 0.dp, 0.dp, Dp.Unspecified, buttonModifier )
            val contentModifier = Modifier.clip( uiRoundnessShape() )
                                          .clip(uiRoundnessShape()).clickable( onClick = { onTabChanged(index) } )
                                          .padding( vertical = 8.dp )
            val result: @Composable () -> Unit = {
                if( isLandscape )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = contentModifier
                    ) {
                        button.Draw()
                        textContent()
                    }
                else
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = contentModifier
                    ) {
                        button.Draw()
                        textContent()
                    }
            }

            addButton( result )
        }
    }

    @Composable
    override fun BackButton(): NavigationButton {
        val button = super.BackButton()
        val shape = uiRoundnessShape()
        button.modifier {
            it.offset( 0.dp, 7.dp )
              .clip( shape )
              .padding( top = 12.dp, bottom = 12.dp )
              .size( 24.dp )
        }
        return button
    }

    @Composable
    override fun SettingsButton(): NavigationButton {
        val button = super.SettingsButton()
        val shape = uiRoundnessShape()
        button.modifier {
            it.offset( 0.dp, 7.dp )
              .clip( shape )
              .padding( top = 12.dp, bottom = 12.dp )
              .size( 24.dp )
        }
        return button
    }

    @Composable
    override fun StatsButton(): NavigationButton {
        val button = super.StatsButton()
        val shape = uiRoundnessShape()
        button.modifier {
            it.offset( 0.dp, 7.dp )
              .clip( shape )
              .padding( top = 12.dp, bottom = 12.dp )
              .size( 24.dp )
        }
        return button
    }

    @Composable
    override fun SearchButton(): NavigationButton {
        val button = super.SearchButton()
        val shape = uiRoundnessShape()
        button.modifier {
            it.offset( 0.dp, 7.dp )
                .clip( shape )
                .padding( top = 12.dp, bottom = 12.dp )
                .size( 24.dp )
        }
        return button
    }

    @Composable
    override fun Draw() {
        if( buttonList.size < 2 ) return

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .width(Dimensions.navigationRailWidth)
                .verticalScroll( rememberScrollState() )
        ) {
            Box(
                contentAlignment = Alignment.TopCenter
            ) {
                // Show settings and statistics buttons in homepage
                // Show back button in other screens
                if(navController.currentBackStackEntry?.destination?.route != NavRoutes.home.name
                    && UiType.ViMusic.isCurrent())
                    BackButton().Draw()
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                content = { buttonList.forEach { it() } }
            )

            // Only show search icon when UI is ViMusic and
            // setting is turned on

            if( UiType.ViMusic.isCurrent() ) {
                val iconSize: Dp = Dimensions.navigationRailWidth
                //val iconHeight: Dp = Dimensions.halfheaderHeight
                if ( showSearchIconInNav() )
                    Box(
                        contentAlignment = Alignment.TopCenter,
                        modifier = Modifier.size(iconSize),
                        content = {
                            SearchButton().Draw()
                        }
                    )

                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.size(iconSize),
                    content = {
                        StatsButton().Draw()
                    }
                )
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.size(iconSize),
                    content = {
                        SettingsButton().Draw()
                    }
                )
            }
        }
    }
}








