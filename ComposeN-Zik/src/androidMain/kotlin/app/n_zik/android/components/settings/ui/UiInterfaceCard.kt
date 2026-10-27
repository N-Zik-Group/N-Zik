package app.n_zik.android.components.settings.ui

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.enums.HomeScreenTabs
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.enums.MessageType
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.enums.PlayerPosition
import app.it.fast4x.rimusic.enums.SearchDisplayOrder
import app.it.fast4x.rimusic.enums.TransitionEffect
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.utils.disableNavigationBackStackKey
import app.it.fast4x.rimusic.utils.hideStatusBarKey
import app.it.fast4x.rimusic.utils.indexNavigationTabKey
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.messageTypeKey
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.navigationBarTypeKey
import app.it.fast4x.rimusic.utils.playerPositionKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.rememberQueueScrollPositionKey
import app.it.fast4x.rimusic.utils.searchDisplayOrderKey
import app.it.fast4x.rimusic.utils.showFloatingIconKey
import app.it.fast4x.rimusic.utils.showSearchTabKey
import app.it.fast4x.rimusic.utils.showStatsInNavbarKey
import app.it.fast4x.rimusic.utils.transitionEffectKey
import app.it.fast4x.rimusic.utils.UiTypeKey
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.dialog.settings.HomeTabsSettingsDialog
import app.n_zik.android.components.search.SearchHistoryPageSettings
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.R

/** Owns the interface settings and their local dialogs. */
@Composable
internal fun UiInterfaceCard(search: Search) {
    var hideStatusBar by rememberPreference(hideStatusBarKey, false)
    val uiType by rememberPreference(UiTypeKey, UiType.RiMusic)
    var indexNavigationTab by rememberPreference(
        indexNavigationTabKey,
        HomeScreenTabs.Default
    )
    var showSearchTab by rememberPreference(showSearchTabKey, false)
    var showStatsInNavbar by rememberPreference(showStatsInNavbarKey, false)
    var navigationBarPosition by rememberPreference(navigationBarPositionKey, NavigationBarPosition.BottomFloating)
    var navigationBarType by rememberPreference(navigationBarTypeKey, NavigationBarType.IconOnly)
    var showFloatingIcon by rememberPreference(showFloatingIconKey, false)
    var menuStyle by rememberPreference(menuStyleKey, MenuStyle.List)
    var transitionEffect by rememberPreference(transitionEffectKey, TransitionEffect.Fade)
    var disableBackStack by rememberPreference(disableNavigationBackStackKey, false)
    var playerPosition by rememberPreference(playerPositionKey, PlayerPosition.Bottom)
    var messageType by rememberPreference(messageTypeKey, MessageType.Modern)
    var searchDisplayOrder by rememberPreference(searchDisplayOrderKey, SearchDisplayOrder.SuggestionsFirst)

    val navSearchContextMatch = search.inputValue.isBlank() ||
        stringResource(R.string.hide_status_bar).contains(search.inputValue, true) ||
        stringResource(R.string.search_history_pages).contains(search.inputValue, true) ||
        stringResource(R.string.search_history_pages_description).contains(search.inputValue, true) ||
        stringResource(R.string.search_display_order).contains(search.inputValue, true) ||
        stringResource(R.string.navigation_bar_position).contains(search.inputValue, true) ||
        stringResource(R.string.navigation_bar_type).contains(search.inputValue, true) ||
        stringResource(R.string.player_position).contains(search.inputValue, true) ||
        stringResource(R.string.menu_style).contains(search.inputValue, true) ||
        stringResource(R.string.message_type).contains(search.inputValue, true) ||
        stringResource(R.string.home_tabs_settings).contains(search.inputValue, true) ||
        stringResource(R.string.default_page).contains(search.inputValue, true) ||
        stringResource(R.string.transition_effect).contains(search.inputValue, true)

    AnimatedVisibility(
        visible = navSearchContextMatch,
        enter = settingsCardEnter,
        exit = settingsCardExit
    ) {
        SettingsSectionCard(
            modifier = Modifier.testTag("ui.card.interface"),
            title = stringResource(R.string.user_interface),
            icon = R.drawable.ui,
            content = {
                if (search.inputValue.isBlank() || stringResource(R.string.hide_status_bar).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("ui.settings_use_font_type.hide_status_bar"),
                        title = stringResource(R.string.hide_status_bar),
                        text = stringResource(R.string.settings_desc_ui_settings_use_font_type_hide_status_bar),
                        isChecked = hideStatusBar,
                        onCheckedChange = { hideStatusBar = it },
                        icon = R.drawable.eye_off
                    )
                }

                var showNavPosDialog by remember { mutableStateOf(false) }
                if (search.inputValue.isBlank() || stringResource(R.string.navigation_bar_position).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.navigation_bar_position"),
                        title = stringResource(R.string.navigation_bar_position),
                        text = stringResource(R.string.settings_desc_ui_user_interface_navigation_bar_position),
                        selectedValueText = navigationBarPosition.text,
                        icon = R.drawable.locate,
                        onClick = { if (uiType != UiType.ViMusic) showNavPosDialog = true }
                    )
                }
                DialogPresence(visible = showNavPosDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.navigation_bar_position),
                        selectedValue = navigationBarPosition,
                        onValueSelected = { navigationBarPosition = it },
                        valueText = { it.text },
                        values = NavigationBarPosition.values().toList(),
                        onDismiss = { showNavPosDialog = false }
                    )
                }

                var showNavTypeDialog by remember { mutableStateOf(false) }
                if (search.inputValue.isBlank() || stringResource(R.string.navigation_bar_type).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.navigation_bar_type"),
                        title = stringResource(R.string.navigation_bar_type),
                        text = stringResource(R.string.settings_desc_ui_user_interface_navigation_bar_type),
                        selectedValueText = navigationBarType.text,
                        icon = R.drawable.burger,
                        onClick = { showNavTypeDialog = true }
                    )
                }
                DialogPresence(visible = showNavTypeDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.navigation_bar_type),
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
                        text = stringResource(R.string.settings_desc_ui_user_interface_player_position),
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
                if (search.inputValue.isBlank() || stringResource(R.string.menu_style).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.menu_style"),
                        title = stringResource(R.string.menu_style),
                        text = stringResource(R.string.settings_desc_ui_user_interface_menu_style),
                        selectedValueText = menuStyle.text,
                        icon = R.drawable.reorder,
                        onClick = { showMenuStyleDialog = true }
                    )
                }
                DialogPresence(visible = showMenuStyleDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.menu_style),
                        selectedValue = menuStyle,
                        onValueSelected = { menuStyle = it },
                        valueText = { it.text },
                        values = MenuStyle.values().toList(),
                        onDismiss = { showMenuStyleDialog = false }
                    )
                }

                var showMessageTypeDialog by remember { mutableStateOf(false) }
                if (search.inputValue.isBlank() || stringResource(R.string.message_type).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.message_type"),
                        title = stringResource(R.string.message_type),
                        text = stringResource(R.string.settings_desc_ui_user_interface_message_type),
                        selectedValueText = messageType.text,
                        icon = R.drawable.information,
                        onClick = { showMessageTypeDialog = true }
                    )
                }
                DialogPresence(visible = showMessageTypeDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.message_type),
                        selectedValue = messageType,
                        onValueSelected = { messageType = it },
                        valueText = { it.text },
                        values = MessageType.values().toList(),
                        onDismiss = { showMessageTypeDialog = false }
                    )
                }

                var showDefPageDialog by remember { mutableStateOf(false) }
                if (search.inputValue.isBlank() || stringResource(R.string.default_page).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.default_page"),
                        title = stringResource(R.string.default_page),
                        text = stringResource(R.string.settings_desc_ui_user_interface_default_page),
                        selectedValueText = indexNavigationTab.text,
                        icon = R.drawable.star_brilliant,
                        onClick = { showDefPageDialog = true }
                    )
                }
                DialogPresence(visible = showDefPageDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.default_page),
                        selectedValue = indexNavigationTab,
                        onValueSelected = { indexNavigationTab = it },
                        valueText = { it.text },
                        values = HomeScreenTabs.values().toList(),
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

                var showTransDialog by remember { mutableStateOf(false) }
                if (search.inputValue.isBlank() || stringResource(R.string.transition_effect).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.transition_effect"),
                        title = stringResource(R.string.transition_effect),
                        text = stringResource(R.string.settings_desc_ui_user_interface_transition_effect),
                        selectedValueText = transitionEffect.text,
                        icon = R.drawable.images_sharp,
                        onClick = { showTransDialog = true }
                    )
                }
                DialogPresence(visible = showTransDialog) {
                    ValueSelectorDialog(
                        title = stringResource(R.string.transition_effect),
                        selectedValue = transitionEffect,
                        onValueSelected = { transitionEffect = it },
                        valueText = { it.text },
                        values = TransitionEffect.values().toList(),
                        onDismiss = { showTransDialog = false }
                    )
                }

                if (search.inputValue.isBlank() || stringResource(R.string.disable_navigation_back_stack).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.disable_navigation_back_stack"),
                        title = stringResource(R.string.disable_navigation_back_stack),
                        text = stringResource(R.string.disable_navigation_back_stack_description),
                        icon = R.drawable.chevron_back,
                        isChecked = disableBackStack,
                        onCheckedChange = { disableBackStack = it }
                    )
                }

                if (search.inputValue.isBlank() ||
                    stringResource(R.string.search_history_pages).contains(search.inputValue, true) ||
                    stringResource(R.string.search_history_pages_description).contains(search.inputValue, true)
                ) {
                    SearchHistoryPageSettings()
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

                if (UiType.ViMusic.isCurrent()) {
                    if (search.inputValue.isBlank() || stringResource(R.string.vimusic_show_search_button_in_navigation_bar).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            modifier = Modifier.settingsSearchTarget("ui.user_interface.vimusic_show_search_button_in_navigation_bar"),
                            title = stringResource(R.string.vimusic_show_search_button_in_navigation_bar),
                            text = stringResource(R.string.vismusic_only_in_left_right_navigation_bar),
                            isChecked = showSearchTab,
                            onCheckedChange = { showSearchTab = it },
                            icon = R.drawable.search
                        )
                    }
                    if (search.inputValue.isBlank() || stringResource(R.string.show_statistics_in_navigation_bar).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            modifier = Modifier.settingsSearchTarget("ui.user_interface.show_statistics_in_navigation_bar"),
                            title = stringResource(R.string.show_statistics_in_navigation_bar),
                            text = stringResource(R.string.settings_desc_ui_user_interface_show_statistics_in_navigation_bar),
                            isChecked = showStatsInNavbar,
                            onCheckedChange = { showStatsInNavbar = it },
                            icon = R.drawable.stats_chart
                        )
                    }
                }

                if (search.inputValue.isBlank() || stringResource(R.string.show_floating_icon).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.show_floating_icon"),
                        title = stringResource(R.string.show_floating_icon),
                        text = stringResource(R.string.settings_desc_ui_user_interface_show_floating_icon),
                        isChecked = showFloatingIcon,
                        onCheckedChange = { showFloatingIcon = it },
                        icon = R.drawable.maximize
                    )
                }

                var rememberQueueScrollPosition by rememberPreference(rememberQueueScrollPositionKey, true)
                if (search.inputValue.isBlank() || stringResource(R.string.remember_queue_scroll_position).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("ui.user_interface.remember_queue_scroll_position"),
                        title = stringResource(R.string.remember_queue_scroll_position),
                        text = stringResource(R.string.remember_queue_scroll_position_description),
                        isChecked = rememberQueueScrollPosition,
                        onCheckedChange = { rememberQueueScrollPosition = it },
                        icon = R.drawable.reorder
                    )
                }

            }
        )
    }
}
