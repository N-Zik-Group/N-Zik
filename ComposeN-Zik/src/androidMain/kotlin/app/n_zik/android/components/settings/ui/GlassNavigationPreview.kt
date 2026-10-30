package app.n_zik.android.components.settings.ui

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.forArea
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.utils.enableQuickPicksPageKey
import app.it.fast4x.rimusic.utils.homeTabsOrderKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.settings.HomeTabsSettingsDialog
import app.n_zik.android.components.dialog.settings.buildHomeTabDefs
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.navigation.nav.GlassNavigationBarHeight
import app.n_zik.android.components.navigation.nav.GlassNavigationTab
import app.n_zik.android.components.navigation.nav.GlassNavigationTabs
import app.n_zik.android.components.ui.screens.home.activeHomeTabIds
import app.n_zik.android.core.network.OfflineMode

@Composable
internal fun GlassNavigationPreview(config: GlassSettings, modifier: Modifier = Modifier) {
    val orderSerialized by rememberPreference(homeTabsOrderKey, "")
    val quickPicks by rememberPreference(enableQuickPicksPageKey, true)
    val songs by rememberPreference("hometab_songs_enabled", true)
    val artists by rememberPreference("hometab_artists_enabled", true)
    val albums by rememberPreference("hometab_albums_enabled", true)
    val playlists by rememberPreference("hometab_playlists_enabled", true)
    val offlineMode by OfflineMode.rememberEnabled()
    val order = remember(orderSerialized) { HomeTabsSettingsDialog.parseOrder(orderSerialized) }
    val activeTabs = remember(order, quickPicks, songs, artists, albums, playlists, offlineMode) {
        activeHomeTabIds(order, quickPicks, songs, artists, albums, playlists, offlineMode)
    }
    val definitions = remember { buildHomeTabDefs() }
    // Preview interactions must never write the home screen's selected-tab preference.
    var selectedId by rememberSaveable { mutableStateOf(activeTabs.first()) }
    LaunchedEffect(activeTabs) {
        if (selectedId !in activeTabs) selectedId = activeTabs.first()
    }
    if (activeTabs.size < 2) return

    val selectedIndex = activeTabs.indexOf(selectedId).coerceAtLeast(0)
    val transition = updateTransition(selectedIndex, label = "glassPreviewSelection")
    val showLabel = NavigationBarType.IconAndText.isCurrent()
    val widthFraction = if (NavigationBarPosition.BottomFloating.isCurrent()) {
        if (showLabel || activeTabs.size > 5) .95f else .85f
    } else 1f
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        GlassNavigationTabs(
            selectedIndex = selectedIndex,
            indices = activeTabs.indices.toList(),
            onTabChanged = { selectedId = activeTabs[it] },
            settings = config.forArea(GlassArea.Nav),
            vertical = false,
            modifier = Modifier.fillMaxWidth(widthFraction).height(GlassNavigationBarHeight),
        ) {
            activeTabs.forEachIndexed { index, id ->
                val definition = definitions.getValue(id)
                val color by transition.animateColor(label = "glassPreviewTabColor") {
                    if (it == index) colorPalette().text else colorPalette().textDisabled
                }
                GlassNavigationTab(
                    text = stringResource(definition.labelRes),
                    iconId = definition.iconRes,
                    selected = index == selectedIndex,
                    showLabel = showLabel,
                    color = color,
                    onClick = { selectedId = id },
                )
            }
        }
    }
}
