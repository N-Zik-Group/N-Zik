package app.n_zik.android.components.navigation.nav

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import app.n_zik.android.components.tab.NavigationButtonSlots
import app.n_zik.android.components.navigation.dock.DockToolbarCatalogue
import app.n_zik.android.components.navigation.dock.DockToolbarPreferences
import app.n_zik.android.components.navigation.dock.DockToolbarTab
import app.n_zik.android.components.navigation.dock.ClassicBottomDockNavigation
import app.n_zik.android.components.player.TOP_NAV_BAR_HEIGHT
import androidx.compose.runtime.CompositionLocalProvider
import app.it.fast4x.rimusic.ui.components.navigation.nav.LocalIsManyButtons
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.n_zik.android.core.navigation.currentSearchShortcutRoute
import app.n_zik.android.core.navigation.shouldShowSearchShortcut
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.n_zik.android.showSearchIconInNav
import app.n_zik.android.showStatsIconInNav
import app.it.fast4x.rimusic.ui.components.themed.Button
import app.it.fast4x.rimusic.ui.components.themed.TextIconButton
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.n_zik.android.utils.toast.trackBottomNavigationForToasts
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.navigation.header.LocalUnifiedTopGlassSurface
import app.n_zik.android.components.ui.screens.home.LocalSharedHomeHeaderGlass
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.semiBold

/** Native Top keeps the native type scale and icon size while mirroring Bottom vertically. */
@Composable
internal fun NativeTopNavigationTabContent(text: String, iconId: Int, color: Color) {
    val many = LocalIsManyButtons.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround,
        modifier = Modifier.padding(5.dp * if (many) .75f else 1f).fillMaxSize(),
    ) {
        BasicText(
            text,
            style = TextStyle(
                fontSize = if (many) typography().xxxs.semiBold.fontSize else typography().xs.semiBold.fontSize,
                fontWeight = typography().xs.semiBold.fontWeight,
                fontFamily = typography().xs.semiBold.fontFamily,
                color = color,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Box(Modifier.testTag("horizontalNavigationTabIcon")) {
            Button(iconId, color, 0.dp, 20.dp).Draw()
        }
    }
}

/** Fits the floating rail's inset slot without spending the label's height on icon spacing. */
@Composable
internal fun NativeFloatingNavigationTabContent(text: String, iconId: Int, color: Color) {
    val many = LocalIsManyButtons.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        modifier = Modifier.padding(6.dp * if (many) .75f else 1f).fillMaxSize(),
    ) {
        Box(Modifier.testTag("horizontalNavigationTabIcon")) {
            Button(iconId, color, 0.dp, 20.dp).Draw()
        }
        BasicText(
            text,
            style = TextStyle(
                fontSize = if (many) typography().xxxs.semiBold.fontSize else typography().xxs.semiBold.fontSize,
                lineHeight = if (many) 12.sp else 14.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                fontWeight = typography().xs.semiBold.fontWeight,
                fontFamily = typography().xs.semiBold.fontFamily,
                color = color,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Shown when "Navigation bar position" is set to "top" or "bottom"
class HorizontalNavigationBar(
    override val tabIndex: Int,
    val onTabChanged: (Int) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier
): AbstractNavigationBar( navController, modifier ) {
    private val dockTabs = NavigationButtonSlots<Pair<Int, DockToolbarTab>>()

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

        val resources = LocalContext.current.resources
        buttons { index, text, iconId ->
            val descriptor = DockToolbarTab(resources.getResourceEntryName(iconId), text, iconId)
            dockTabs.register(index, index to descriptor)
            SideEffect { DockToolbarCatalogue.register(listOf(descriptor)) }
            tabIndices.register(index, index)
            buttonList.register(index) {
                val color by transition.animateColor(label = "") {
                    if (it == index) colorPalette().text else colorPalette().textDisabled
                }
                val button: Button =
                    if (NavigationBarType.IconOnly.isCurrent())
                        Button(iconId, color, 12.dp, if (isFloating) 24.dp else 20.dp)
                    else
                        TextIconButton(text, iconId, color, 0.dp, 20.dp, textSpacing = if (isFloating) 12.dp else 5.dp, isCompact = isFloating)

                val glass = rememberGlassSettings()
                if (glass.usesGlassMaterial(GlassArea.Nav)) {
                    GlassNavigationTab(text, iconId, index == tabIndex,
                        NavigationBarType.IconAndText.isCurrent(), color,
                        labelAbove = NavigationBarPosition.Top.isCurrent()) { onTabChanged(index) }
                } else Box(Modifier.semantics {
                    contentDescription = text; selected = index == tabIndex; role = Role.Tab
                }.clip(uiRoundnessShape()).clickable { onTabChanged(index) }, contentAlignment = Alignment.Center) {
                    if (NavigationBarPosition.Top.isCurrent() && NavigationBarType.IconAndText.isCurrent())
                        NativeTopNavigationTabContent(text, iconId, color)
                    else if (isFloating && NavigationBarType.IconAndText.isCurrent())
                        NativeFloatingNavigationTabContent(text, iconId, color)
                    else button.Draw()
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
        val glassSettings = rememberGlassSettings()
        val useGlass = glassSettings.usesGlassMaterial(GlassArea.Nav)
        val unifiedTopSurface = (LocalSharedHomeHeaderGlass.current
            ?.takeIf { it.topNavigation && it.owner != null }?.surface
            ?: LocalUnifiedTopGlassSurface.current).takeIf { NavigationBarPosition.Top.isCurrent() }

        val isFloating = NavigationBarPosition.BottomFloating.isCurrent()
        val isIconOnly = NavigationBarType.IconOnly.isCurrent()
        val dock = app.n_zik.android.components.navigation.dock.LocalFloatingDock.current
        if (isFloating && dock?.enabled == true) {
            val dockOrder by app.it.fast4x.rimusic.utils.rememberPreference(DockToolbarPreferences.orderKey, "")
            val metadata = dockTabs.toList().toMap()
            val orderedIds = DockToolbarPreferences.orderedIds(metadata.values.map { it.id }, dockOrder)
            val orderedIndices = tabIndices.toList().sortedBy { index ->
                orderedIds.indexOf(metadata[index]?.id).let { if (it < 0) Int.MAX_VALUE else it }
            }
            val allIndices = orderedIndices
            val openSearch = { navController.navigate(NavRoutes.search.name) }
            app.n_zik.android.components.navigation.dock.FloatingDockNavigation(
                dock = dock,
                expandedHeight = app.n_zik.android.components.navigation.dock.FloatingDockDimensions.expandedHeight.dp,
                bottomPadding = bottomPadding(),
                onSearch = openSearch,
                showSearch = false,
                tabsCount = allIndices.size,
                selectedIndex = allIndices.indexOf(tabIndex).coerceAtLeast(0),
            ) { tabsModifier, surfaceBackdrop ->
                GlassNavigationTabs(
                    selectedIndex = tabIndex, indices = allIndices,
                    onTabChanged = onTabChanged,
                    settings = glassSettings.forArea(GlassArea.Nav), vertical = false, modifier = tabsModifier,
                    drawContainer = false, externalSurfaceBackdrop = surfaceBackdrop,
                ) {
                    CompositionLocalProvider(LocalIsManyButtons provides (buttonList.size > 5)) {
                        DrawButtons(orderedIndices)
                    }

                }
            }
            return
        }
        
        val isTop = NavigationBarPosition.Top.isCurrent()
        if ((NavigationBarPosition.Bottom.isCurrent() || isTop) && dock?.enabled == true) {
            val indices = tabIndices.toList()
            ClassicBottomDockNavigation(
                dock = dock,
                rowHeight = if (useGlass) GlassNavigationBarHeight else if (isTop) TOP_NAV_BAR_HEIGHT
                    else Dimensions.navigationBarHeight - 10.dp,
                bottomPadding = if (isTop) 0.dp else bottomPadding(),
                tabsCount = indices.size,
                selectedIndex = indices.indexOf(tabIndex).coerceAtLeast(0),
                modifier = modifier,
                top = isTop,
            ) { tabsModifier, backdrop ->
                Row(tabsModifier, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        if (useGlass) GlassNavigationTabs(
                            selectedIndex = tabIndex, indices = indices, onTabChanged = onTabChanged,
                            settings = glassSettings.forArea(GlassArea.Nav), vertical = false,
                            modifier = Modifier.fillMaxSize(), drawContainer = false,
                            externalSurfaceBackdrop = backdrop,
                        ) {
                            CompositionLocalProvider(LocalIsManyButtons provides (buttonList.size > 5)) {
                                DrawButtons()
                            }
                        } else Row(
                            Modifier.fillMaxSize().padding(horizontal = 16.dp)
                                .padding(bottom = if (isTop) 5.dp else 0.dp)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            CompositionLocalProvider(LocalIsManyButtons provides (buttonList.size > 5)) {
                                DrawButtons()
                            }
                        }
                    }
                }
            }
            return
        }

        val floatingHeight = if (isIconOnly) Dimensions.floatingNavBarIconOnlyHeight else Dimensions.floatingNavBarHeight
        val widthFraction = if (isIconOnly) {
            if (buttonList.size > 5) 0.95f else 0.85f
        } else {
            0.95f
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            // Glass keeps its full control height above Android's navigation buttons.
            modifier = modifier.padding(
                top = topPadding(),
                bottom = if (isFloating || (useGlass && NavigationBarPosition.Bottom.isCurrent())) bottomPadding() else 0.dp,
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround,
                modifier = Modifier
                    .then(if (isFloating) Modifier.fillMaxWidth(widthFraction) else Modifier.fillMaxWidth())
                    .height(if (useGlass) GlassNavigationBarHeight else if (isFloating) floatingHeight else (Dimensions.navigationBarHeight - 10.dp) + bottomPadding())
            ) {

                val scrollState = rememberScrollState()
                // Top navigation uses the same user-selected roundness as the dock.
                val roundedCornerShape = when {
                    NavigationBarPosition.Top.isCurrent() -> topNavigationBarShape(uiRoundnessShape())
                    isFloating -> uiRoundnessShape()
                    else -> bottomNavigationBarShape(uiRoundnessShape())
                }


                val backgroundColor = surfaceColor(colorPalette().background1)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .trackBottomNavigationForToasts(NavigationBarPosition.current().isBottomType, Dimensions.navigationBarHeight - 10.dp)
                        .padding(bottom = if (isFloating) 2.dp else 0.dp)
                        .shadow(elevation = if (isFloating && !useGlass) 8.dp else 0.dp, shape = roundedCornerShape)
                        .then(if (NavigationBarPosition.Top.isCurrent() && !useGlass)
                            Modifier.background(colorPalette().background0) else Modifier)
                        .then(if (useGlass) Modifier else Modifier.clip(roundedCornerShape))
                        .then(if (useGlass) Modifier
                              else Modifier.background(if (isFloating) backgroundColor.copy(alpha = 0.95f) else backgroundColor)
                                  .glassAreaContour(glassSettings, GlassArea.Nav, roundedCornerShape))
                ) {


                    if (useGlass) {
                        GlassNavigationTabs(
                            selectedIndex = tabIndex,
                            indices = tabIndices.toList(),
                            onTabChanged = onTabChanged,
                            settings = glassSettings.forArea(GlassArea.Nav),
                            vertical = false,
                            modifier = Modifier.fillMaxWidth().height(GlassNavigationBarHeight),
                            containerShape = roundedCornerShape,
                            drawContainer = unifiedTopSurface == null,
                            externalSurfaceBackdrop = unifiedTopSurface,
                        ) {
                            CompositionLocalProvider(LocalIsManyButtons provides (buttonList.size > 5)) {
                                DrawButtons()
                            }
                        }
                    } else Row(
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
                                DrawButtons()
                            }
                        }
                    )
                }


            }
        }
    }
}


