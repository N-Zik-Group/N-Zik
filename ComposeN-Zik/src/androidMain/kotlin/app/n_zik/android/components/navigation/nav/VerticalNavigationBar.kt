package app.n_zik.android.components.navigation.nav

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.forArea
import androidx.annotation.OptIn
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.navigation.header.SideAppHeaderControls
import app.n_zik.android.components.navigation.sideRailScrollOrigin

/** Keeps utility buttons reachable when the fixed-height glass tabs exceed the viewport. */
internal fun glassRailNeedsScroll(
    availableHeight: Dp,
    tabCount: Int,
    hasBack: Boolean,
    hasUtilities: Boolean,
    hasSearch: Boolean,
    headerHeight: Dp = 0.dp,
): Boolean {
    val required = (tabCount * 64 + 8).dp +
        (if (hasBack) 48.dp else 0.dp) +
        (if (hasUtilities) Dimensions.navigationRailWidth * (if (hasSearch) 3 else 2) else 0.dp) + headerHeight
    return required > availableHeight
}

// Shown when "Navigation bar position" is set to "left" or "right"
class VerticalNavigationBar(
    override val tabIndex: Int,
    val onTabChanged: (Int) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier
): AbstractNavigationBar( navController, modifier ) {

    @OptIn(UnstableApi::class)
    @Composable
    override fun add(buttons: @Composable (@Composable (Int, String, Int) -> Unit) -> Unit) {
        val transition = updateTransition(targetState = tabIndex, label = "sideTabSelection")
        buttons { index, text, iconId ->
            tabIndices.register(index, index)
            buttonList.register(index) {
                val textColor by transition.animateColor(label = "sideTabInk") {
                    if (it == index) colorPalette().text else colorPalette().textDisabled
                }
                GlassNavigationTab(text, iconId, index == tabIndex,
                    NavigationBarType.IconAndText.isCurrent(), textColor,
                    sidePosition = NavigationBarPosition.current()) { onTabChanged(index) }
            }
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
        val glassSettings = rememberGlassSettings()


        Box(modifier.width(Dimensions.navigationRailWidth).sideRailScrollOrigin()) {

            val railSurfaceBackdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                SideNavigationSurface(glassSettings, Modifier.matchParentSize(), railSurfaceBackdrop)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp, bottom = 8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SideAppHeaderControls(navController)

                    DrawTabs(railSurfaceBackdrop)
                }
            }
        }
    }

    /** Draws only the page-owned tabs below the root-owned side header. */
    @Composable
    internal fun DrawTabs(railSurfaceBackdrop: LayerBackdrop) {
        if (buttonList.size < 2) return
        val glassSettings = rememberGlassSettings()
        Spacer(Modifier.height(20.dp))
        GlassNavigationTabs(
            selectedIndex = tabIndex,
            indices = tabIndices.toList(),
            onTabChanged = onTabChanged,
            settings = glassSettings.forArea(GlassArea.Nav),
            vertical = true,
            drawContainer = false,
            externalSurfaceBackdrop = railSurfaceBackdrop,
            modifier = Modifier.fillMaxWidth().height((buttonList.size * 64 + 8).dp),
        ) { DrawButtons() }
    }
}
