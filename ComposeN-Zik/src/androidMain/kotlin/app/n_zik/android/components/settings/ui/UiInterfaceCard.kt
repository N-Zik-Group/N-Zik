package app.n_zik.android.components.settings.ui

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.common.SearchableSettingsSectionHeading
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.enums.HomeScreenTabs
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.MusicAnimationType
import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.enums.NavigationBarType
import app.n_zik.android.enums.PlayerPosition
import app.n_zik.android.enums.SearchDisplayOrder
import app.n_zik.android.enums.TransitionEffect
import app.n_zik.android.utils.artistThumbnailRoundnessDpKey
import app.n_zik.android.utils.thumbnailRoundnessDpKey
import app.n_zik.android.utils.disableNavigationBackStackKey
import app.n_zik.android.utils.hideStatusBarKey
import app.n_zik.android.utils.indexNavigationTabKey
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.navigationBarPositionKey
import app.n_zik.android.utils.navigationBarTypeKey
import app.n_zik.android.utils.nowPlayingIndicatorKey
import app.n_zik.android.utils.playerPositionKey
import app.n_zik.android.utils.playlistindicatorKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.rightToLeftInterfaceKey
import app.n_zik.android.utils.searchDisplayOrderKey
import app.n_zik.android.utils.showFloatingIconKey
import app.n_zik.android.utils.transitionEffectKey
import app.n_zik.android.utils.uiRoundnessDpKey
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.dialog.settings.HomeTabsSettingsDialog
import app.n_zik.android.components.ui.screens.home.activeHomeTabIds
import app.n_zik.android.components.ui.screens.home.tabIdForIndex
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.utils.enableQuickPicksPageKey
import app.n_zik.android.utils.homeTabsOrderKey
import app.n_zik.android.components.settings.common.ArtistRoundnessSettingsTarget
import app.n_zik.android.components.settings.common.ThumbnailRoundnessSettingsTarget
import app.n_zik.android.components.settings.common.InterfaceRoundnessSettingsTarget
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.SliderSettingsEntry
import app.n_zik.android.components.tab.Search
import app.n_zik.android.ui.styling.BoundedCornerSize
import app.n_zik.android.R

/** Owns the interface settings and their local dialogs. */
@Composable
internal fun UiInterfaceCard(search: Search) {
    var hideStatusBar by rememberPreference(hideStatusBarKey, false)
    var rightToLeft by rememberPreference(rightToLeftInterfaceKey, false)
    val layoutDirectionTitle = stringResource(R.string.settings_interface_right_to_left_label)
    val layoutDirectionDescription = stringResource(R.string.settings_interface_right_to_left_description)
    val layoutDirectionMatches = search.inputValue.isBlank() ||
        layoutDirectionTitle.contains(search.inputValue, ignoreCase = true) ||
        layoutDirectionDescription.contains(search.inputValue, ignoreCase = true)
    var indexNavigationTab by rememberPreference(
        indexNavigationTabKey,
        HomeScreenTabs.Default
    )
    var navigationBarPosition by rememberPreference(navigationBarPositionKey, NavigationBarPosition.BottomFloating)
    var navigationBarType by rememberPreference(navigationBarTypeKey, NavigationBarType.IconOnly)
    var showFloatingIcon by rememberPreference(showFloatingIconKey, false)
    var menuStyle by rememberPreference(menuStyleKey, MenuStyle.List)
    var transitionEffect by rememberPreference(transitionEffectKey, TransitionEffect.Fade)
    var disableBackStack by rememberPreference(disableNavigationBackStackKey, false)
    var playerPosition by rememberPreference(playerPositionKey, PlayerPosition.Bottom)
    var searchDisplayOrder by rememberPreference(searchDisplayOrderKey, SearchDisplayOrder.SuggestionsFirst)
    var playlistIndicator by rememberPreference(playlistindicatorKey, false)
    var nowPlayingIndicator by rememberPreference(nowPlayingIndicatorKey, MusicAnimationType.Bubbles)
    var showNowPlayingIndicatorDialog by remember { mutableStateOf(false) }

    val query = search.inputValue
    var interfaceRoundness by rememberPreference(uiRoundnessDpKey, 25f)
    var thumbnailRoundness by rememberPreference(thumbnailRoundnessDpKey, 12f)
    var artistRoundness by rememberPreference(artistThumbnailRoundnessDpKey, 48f)
    val interfaceRoundnessTitle = stringResource(R.string.settings_interface_corner_radius_label)
    val interfaceRoundnessDescription = stringResource(R.string.interface_corner_roundness_description)
    val thumbnailRoundnessTitle = stringResource(R.string.settings_player_artwork_corner_radius_label)
    val thumbnailRoundnessDescription = stringResource(R.string.player_artwork_corner_roundness_description)
    val artistRoundnessTitle = stringResource(R.string.artist_roundness)
    val artistRoundnessDescription = stringResource(R.string.interface_artist_image_roundness_description)
    val interfaceRoundnessMatches = interfaceRoundnessTitle.contains(query, ignoreCase = true) ||
        interfaceRoundnessDescription.contains(query, ignoreCase = true)
    val thumbnailRoundnessMatches = thumbnailRoundnessTitle.contains(query, ignoreCase = true) ||
        thumbnailRoundnessDescription.contains(query, ignoreCase = true)
    val artistRoundnessMatches = artistRoundnessTitle.contains(query, ignoreCase = true) ||
        artistRoundnessDescription.contains(query, ignoreCase = true)
    val playlistIndicatorTitle = stringResource(R.string.settings_library_playlist_indicator_label)
    val playlistIndicatorDescription = stringResource(R.string.settings_library_playlist_indicator_description)
    val nowPlayingIndicatorTitle = stringResource(R.string.settings_library_now_playing_indicator_label)
    val nowPlayingIndicatorDescription = stringResource(R.string.queue_now_playing_indicator_description)
    val playlistIndicatorMatches = playlistIndicatorTitle.contains(query, true) ||
        playlistIndicatorDescription.contains(query, true)
    val nowPlayingIndicatorMatches = nowPlayingIndicatorTitle.contains(query, true) ||
        nowPlayingIndicatorDescription.contains(query, true) ||
        MusicAnimationType.entries.any { it.text.contains(query, ignoreCase = true) }

    DialogPresence(visible = showNowPlayingIndicatorDialog) {
        ValueSelectorDialog(
            title = nowPlayingIndicatorTitle,
            selectedValue = nowPlayingIndicator,
            onValueSelected = { nowPlayingIndicator = it },
            valueText = { it.text },
            values = MusicAnimationType.entries,
            onDismiss = { showNowPlayingIndicatorDialog = false },
        )
    }

    val navSearchContextMatch = layoutDirectionMatches ||
        playlistIndicatorMatches || nowPlayingIndicatorMatches || interfaceRoundnessMatches ||
        thumbnailRoundnessMatches || artistRoundnessMatches ||
        stringResource(R.string.settings_interface_hide_status_bar_label).contains(search.inputValue, true) ||
        stringResource(R.string.search_display_order).contains(search.inputValue, true) ||
        stringResource(R.string.settings_navigation_position_label).contains(search.inputValue, true) ||
        stringResource(R.string.settings_navigation_style_label).contains(search.inputValue, true) ||
        stringResource(R.string.player_position).contains(search.inputValue, true) ||
        stringResource(R.string.settings_menu_style_label).contains(search.inputValue, true) ||
        stringResource(R.string.home_tabs_settings).contains(search.inputValue, true) ||
        stringResource(R.string.settings_home_default_page_label).contains(search.inputValue, true) ||
        stringResource(R.string.settings_navigation_transition_effect_label).contains(search.inputValue, true)

    AnimatedVisibility(
        visible = navSearchContextMatch,
        enter = settingsCardEnter,
        exit = settingsCardExit
    ) {
        SettingsSectionCard(
            modifier = Modifier.testTag("ui.card.interface"),
            title = stringResource(R.string.settings_interface_title),
            icon = R.drawable.ui,
            content = {
                if (search.inputValue.isBlank()) {
                    SearchableSettingsSectionHeading(R.string.common_navigation_section_title, "ui.user_interface.section.navigation", showDivider = false)
                }

                var showNavPosDialog by remember { mutableStateOf(false) }
                if (search.inputValue.isBlank() || stringResource(R.string.settings_navigation_position_label).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.navigation_bar_position"),
                        title = stringResource(R.string.settings_navigation_position_label),
                        text = stringResource(R.string.interface_navigation_bar_position_description),
                        selectedValueText = navigationBarPosition.text,
                        icon = R.drawable.locate,
                        onClick = { showNavPosDialog = true }
                    )
                }
                DialogPresence(visible = showNavPosDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.settings_navigation_position_label),
                        selectedValue = navigationBarPosition,
                        onValueSelected = { navigationBarPosition = it },
                        valueText = { it.text },
                        values = NavigationBarPosition.values().toList(),
                        onDismiss = { showNavPosDialog = false }
                    )
                }

                var showNavTypeDialog by remember { mutableStateOf(false) }
                if (search.inputValue.isBlank() || stringResource(R.string.settings_navigation_style_label).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.navigation_bar_type"),
                        title = stringResource(R.string.settings_navigation_style_label),
                        text = stringResource(R.string.interface_navigation_bar_type_description),
                        selectedValueText = navigationBarType.text,
                        icon = R.drawable.burger,
                        onClick = { showNavTypeDialog = true }
                    )
                }
                DialogPresence(visible = showNavTypeDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.settings_navigation_style_label),
                        selectedValue = navigationBarType,
                        onValueSelected = { navigationBarType = it },
                        valueText = { it.text },
                        values = NavigationBarType.values().toList(),
                        onDismiss = { showNavTypeDialog = false }
                    )
                }

                var showPlayerPosDialog by remember { mutableStateOf(false) }
                if (search.inputValue.isBlank() || stringResource(R.string.player_position).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.player_position"),
                        title = stringResource(R.string.player_position),
                        text = stringResource(R.string.interface_player_position_description),
                        selectedValueText = playerPosition.text,
                        icon = R.drawable.position,
                        onClick = { showPlayerPosDialog = true }
                    )
                }
                DialogPresence(visible = showPlayerPosDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.player_position),
                        selectedValue = playerPosition,
                        onValueSelected = { playerPosition = it },
                        valueText = { it.text },
                        values = PlayerPosition.values().toList(),
                        onDismiss = { showPlayerPosDialog = false }
                    )
                }

                var showMenuStyleDialog by remember { mutableStateOf(false) }
                if (search.inputValue.isBlank() || stringResource(R.string.settings_menu_style_label).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.menu_style"),
                        title = stringResource(R.string.settings_menu_style_label),
                        text = stringResource(R.string.interface_menu_style_description),
                        selectedValueText = menuStyle.text,
                        icon = R.drawable.reorder,
                        onClick = { showMenuStyleDialog = true }
                    )
                }
                DialogPresence(visible = showMenuStyleDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.settings_menu_style_label),
                        selectedValue = menuStyle,
                        onValueSelected = { menuStyle = it },
                        valueText = { it.text },
                        values = MenuStyle.values().toList(),
                        onDismiss = { showMenuStyleDialog = false }
                    )
                }

                var showTransDialog by remember { mutableStateOf(false) }
                if (search.inputValue.isBlank() || stringResource(R.string.settings_navigation_transition_effect_label).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.transition_effect"),
                        title = stringResource(R.string.settings_navigation_transition_effect_label),
                        text = stringResource(R.string.interface_transition_effect_description),
                        selectedValueText = transitionEffect.text,
                        icon = R.drawable.images_sharp,
                        onClick = { showTransDialog = true }
                    )
                }
                DialogPresence(visible = showTransDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.settings_navigation_transition_effect_label),
                        selectedValue = transitionEffect,
                        onValueSelected = { transitionEffect = it },
                        valueText = { it.text },
                        values = TransitionEffect.values().toList(),
                        onDismiss = { showTransDialog = false }
                    )
                }

                if (search.inputValue.isBlank() || stringResource(R.string.settings_navigation_back_to_home_label).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.disable_navigation_back_stack"),
                        title = stringResource(R.string.settings_navigation_back_to_home_label),
                        text = stringResource(R.string.settings_navigation_back_to_home_description),
                        icon = R.drawable.chevron_back,
                        isChecked = disableBackStack,
                        onCheckedChange = { disableBackStack = it }
                    )
                }

                if (search.inputValue.isBlank()) {
                    SearchableSettingsSectionHeading(R.string.home_title, "ui.user_interface.section.home")
                }

                val homeOrder by rememberPreference(homeTabsOrderKey, "")
                val quickPicks by rememberPreference(enableQuickPicksPageKey, true)
                val songs by rememberPreference("hometab_songs_enabled", true)
                val artists by rememberPreference("hometab_artists_enabled", true)
                val albums by rememberPreference("hometab_albums_enabled", true)
                val playlists by rememberPreference("hometab_playlists_enabled", true)
                val offline by OfflineMode.rememberEnabled()
                val activeTabs = activeHomeTabIds(HomeTabsSettingsDialog.parseOrder(homeOrder), quickPicks, songs, artists, albums, playlists, offline)
                val defaultPageChoices = HomeScreenTabs.entries.filter {
                    it == HomeScreenTabs.Default || it == HomeScreenTabs.Search || tabIdForIndex(it.index) in activeTabs
                }
                val effectiveDefaultPage = indexNavigationTab.takeIf { it in defaultPageChoices } ?: HomeScreenTabs.Default
                var showDefPageDialog by remember { mutableStateOf(false) }
                if (search.inputValue.isBlank() || stringResource(R.string.settings_home_default_page_label).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.default_page"),
                        title = stringResource(R.string.settings_home_default_page_label),
                        text = stringResource(R.string.interface_default_page_description),
                        selectedValueText = effectiveDefaultPage.text,
                        icon = R.drawable.star_brilliant,
                        onClick = { showDefPageDialog = true }
                    )
                }
                DialogPresence(visible = showDefPageDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.settings_home_default_page_label),
                        selectedValue = effectiveDefaultPage,
                        onValueSelected = { indexNavigationTab = it },
                        valueText = { it.text },
                        values = defaultPageChoices,
                        onDismiss = { showDefPageDialog = false }
                    )
                }

                if (search.inputValue.isBlank() || stringResource(R.string.home_tabs_settings).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.home_tabs_settings"),
                        title = stringResource(R.string.home_tabs_settings),
                        text = stringResource(R.string.home_tabs_settings_description),
                        onClick = { HomeTabsSettingsDialog.showDialog() },
                        icon = R.drawable.sparkles
                    )
                }

                if (search.inputValue.isBlank()) {
                    SearchableSettingsSectionHeading(R.string.settings_interface_group_display, "ui.user_interface.section.display")
                }

                if (search.inputValue.isBlank() || stringResource(R.string.settings_interface_hide_status_bar_label).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("ui.settings_use_font_type.hide_status_bar"),
                        title = stringResource(R.string.settings_interface_hide_status_bar_label),
                        text = stringResource(R.string.interface_hide_status_bar_description),
                        isChecked = hideStatusBar,
                        onCheckedChange = { hideStatusBar = it },
                        icon = R.drawable.eye_off
                    )
                }

                if (layoutDirectionMatches) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.right_to_left"),
                        title = layoutDirectionTitle,
                        text = layoutDirectionDescription,
                        icon = R.drawable.reorder,
                        isChecked = rightToLeft,
                        onCheckedChange = { rightToLeft = it },
                    )
                }

                if (interfaceRoundnessMatches) {
                    InterfaceRoundnessSlider(
                        target = InterfaceRoundnessSettingsTarget,
                        icon = R.drawable.ui,
                        title = interfaceRoundnessTitle,
                        description = interfaceRoundnessDescription,
                        value = interfaceRoundness,
                        defaultValue = 25f,
                        shape = RoundedCornerShape(BoundedCornerSize(interfaceRoundness.dp, 0.4f)),
                        onSlide = { interfaceRoundness = it },
                    )
                }
                if (thumbnailRoundnessMatches) {
                    InterfaceRoundnessSlider(
                        target = ThumbnailRoundnessSettingsTarget,
                        icon = R.drawable.image,
                        title = thumbnailRoundnessTitle,
                        description = thumbnailRoundnessDescription,
                        value = thumbnailRoundness,
                        defaultValue = 12f,
                        shape = if (thumbnailRoundness >= 48f) CircleShape else
                            RoundedCornerShape(BoundedCornerSize(thumbnailRoundness.dp, 0.25f)),
                        onSlide = { thumbnailRoundness = it },
                    )
                }
                if (artistRoundnessMatches) {
                    InterfaceRoundnessSlider(
                        target = ArtistRoundnessSettingsTarget,
                        icon = R.drawable.image,
                        title = artistRoundnessTitle,
                        description = artistRoundnessDescription,
                        value = artistRoundness,
                        defaultValue = 48f,
                        shape = if (artistRoundness >= 48f) CircleShape else
                            RoundedCornerShape(BoundedCornerSize(artistRoundness.dp, 0.25f)),
                        onSlide = { artistRoundness = it },
                    )
                }

                if (search.inputValue.isBlank()) {
                    SearchableSettingsSectionHeading(R.string.search_title, "ui.user_interface.section.search")
                }

                var showSearchOrderDialog by remember { mutableStateOf(false) }
                if (search.inputValue.isBlank() || stringResource(R.string.search_display_order).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.search_display_order"),
                        title = stringResource(R.string.search_display_order),
                        text = stringResource(R.string.search_display_order_description),
                        icon = R.drawable.search,
                        onClick = { showSearchOrderDialog = true }
                    )
                }
                DialogPresence(visible = showSearchOrderDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.search_display_order),
                        selectedValue = searchDisplayOrder,
                        onValueSelected = { searchDisplayOrder = it },
                        valueText = {
                            when (it) {
                                SearchDisplayOrder.SuggestionsFirst -> stringResource(R.string.search_display_order_suggestions_first)
                                SearchDisplayOrder.SavedSearchesFirst -> stringResource(R.string.search_display_order_saved_searches_first)
                            }
                        },
                        values = SearchDisplayOrder.values().toList(),
                        onDismiss = { showSearchOrderDialog = false }
                    )
                }

                if (search.inputValue.isBlank() || stringResource(R.string.settings_search_floating_button_visible_label).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.show_floating_icon"),
                        title = stringResource(R.string.settings_search_floating_button_visible_label),
                        text = stringResource(R.string.interface_show_floating_icon_description),
                        isChecked = showFloatingIcon,
                        onCheckedChange = { showFloatingIcon = it },
                        icon = R.drawable.maximize
                    )
                }

                if (search.inputValue.isBlank()) {
                    SearchableSettingsSectionHeading(R.string.settings_interface_group_indicators, "ui.user_interface.section.indicators")
                }

                if (playlistIndicatorMatches) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("general.queue_management.playlistindicator"),
                        title = playlistIndicatorTitle,
                        text = playlistIndicatorDescription,
                        isChecked = playlistIndicator,
                        onCheckedChange = { playlistIndicator = it },
                        icon = R.drawable.playlist,
                    )
                }
                if (nowPlayingIndicatorMatches) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("general.queue_management.now_playing_indicator"),
                        title = nowPlayingIndicatorTitle,
                        text = nowPlayingIndicatorDescription,
                        selectedValueText = nowPlayingIndicator.text,
                        onClick = { showNowPlayingIndicatorDialog = true },
                        icon = R.drawable.playing_indicator,
                    )
                }
            }
        )
    }
}

@Composable
private fun InterfaceRoundnessSlider(
    target: String,
    icon: Int,
    title: String,
    description: String,
    value: Float,
    defaultValue: Float,
    shape: Shape,
    onSlide: (Float) -> Unit,
) {
    val accent = colorPalette().accent
    SliderSettingsEntry(
        modifier = Modifier.settingsSearchTarget(target),
        icon = icon,
        title = title,
        text = description,
        state = value,
        range = 0f..48f,
        stepSize = 0f,
        defaultValue = defaultValue,
        drawValuePoints = true,
        isIntegerOnly = true,
        onSlide = { onSlide(kotlin.math.round(it)) },
        toDisplay = { it.toInt().toString() },
        trailingContent = {
            Spacer(Modifier.size(36.dp).background(accent.copy(alpha = 0.5f), shape).border(1.dp, accent, shape))
        },
    )
}
