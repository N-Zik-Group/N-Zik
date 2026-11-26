package app.n_zik.android.ui.screens.searchresult
import app.n_zik.android.components.navigation.rememberTopContentInsetProvider

import app.n_zik.android.components.search.SearchResultHeaderActions
import app.n_zik.android.components.search.SearchResultHeaderIcon
import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.components.navigation.Skeleton
import app.n_zik.android.components.navigation.dock.searchResultDockTabs
import app.n_zik.android.ui.components.themed.Title

import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.searchResultScreenTabIndexKey
import androidx.compose.foundation.text.BasicText
import app.n_zik.android.typography
import androidx.compose.material3.Icon
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.GlassDropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import app.n_zik.android.components.menu.menuSelectionSemantics
import app.n_zik.android.enums.ContentType
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import app.n_zik.android.utils.Preference
import app.n_zik.android.utils.filterContentTypeKey
import app.n_zik.android.uiRoundnessShape


fun String.toBooleanArray(): BooleanArray = this.map { it == '1' }.toBooleanArray()
fun BooleanArray.toPrefString(): String = joinToString(separator = "") { if (it) "1" else "0" }

@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun SearchResultScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {},
    query: String,
    initialTab: Int? = null,
    onSearchAgain: () -> Unit
) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val saveableStateHolder = rememberSaveableStateHolder()
    val (savedTab, saveTab) = rememberPreference(searchResultScreenTabIndexKey, 0)
    val routeTab = rememberSaveable(query, initialTab) { mutableStateOf(initialTab) }
    val tabIndex = routeTab.value ?: savedTab
    val onTabIndexChanges: (Int) -> Unit = { tab ->
        if (initialTab == null) saveTab(tab) else routeTab.value = tab
    }

    val hapticFeedback = LocalHapticFeedback.current

    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val (filterContentType, onFilterContentTypeChanged) = rememberPreference(filterContentTypeKey, ContentType.All)
    val showFilterMenu = remember { mutableStateOf(false) }

    val (gridStatesPref, onGridStatesPrefChanged) = rememberPreference(Preference.SEARCH_RESULT_GRID_STATES.key, Preference.SEARCH_RESULT_GRID_STATES.default)
    val gridStates = remember(gridStatesPref) { gridStatesPref.toBooleanArray() }
    val useGrid = gridStates[tabIndex]
    val setUseGrid: (Boolean) -> Unit = { newValue ->
        val arr = gridStates.copyOf()
        arr[tabIndex] = newValue
        onGridStatesPrefChanged(arr.toPrefString())
    }

    val editQuery: () -> Unit = {
        navController.navigate("${NavRoutes.search.name}?text=${Uri.encode(query)}")
    }
    val showContentTypeFilter = tabIndex == 0 || tabIndex == 3
    val showLayoutToggle = tabIndex in listOf(1, 4, 5, 6)

    val headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit = {
        Title(
            title = stringResource(R.string.search_results_for),
            verticalPadding = 4.dp
        )
        Title(
            title = query,
            icon = R.drawable.pencil,
            onClick = editQuery,
            enableClick = false,
            verticalPadding = 4.dp,
            trailingIcon = {
                SearchResultHeaderActions(
                    showLayoutToggle = showLayoutToggle,
                    onEditQuery = editQuery,
                ) {
                    if (showContentTypeFilter) Box {
                        SearchResultHeaderIcon(
                            icon = R.drawable.discover,
                            contentDescription = stringResource(R.string.accessibility_filter),
                            onClick = { showFilterMenu.value = true },
                        )
                        GlassDropdownMenu(
                            expanded = showFilterMenu.value,
                            containerColor = colorPalette().background0,
                            onDismissRequest = { showFilterMenu.value = false },
                            shape = uiRoundnessShape()
                        ) {
                            ContentType.entries.forEach { type ->
                                val selected = type == filterContentType
                                DropdownMenuItem(
                                    modifier = menuSelectionSemantics(selected),
                                    text = { Text(type.textName, color = colorPalette().text) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(id = type.icon),
                                            contentDescription = null,
                                            tint = colorPalette().text
                                        )
                                    },
                                    trailingIcon = {
                                        RadioButton(
                                            selected = selected,
                                            onClick = null,
                                            modifier = Modifier.size(20.dp),
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = colorPalette().accent,
                                                unselectedColor = colorPalette().textSecondary,
                                            ),
                                        )
                                    },
                                    onClick = {
                                        onFilterContentTypeChanged(type)
                                        showFilterMenu.value = false
                                    }
                                )
                            }
                        }
                    }
                    if (showLayoutToggle) {
                        SearchResultHeaderIcon(
                            icon = if (useGrid) R.drawable.sort_vertical else R.drawable.sort_grid,
                            contentDescription = stringResource(R.string.accessibility_switch_mode),
                            onClick = { setUseGrid(!useGrid) },
                        )
                    }
                }
            }
        )
        if (showContentTypeFilter) Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = filterContentType.icon),
                contentDescription = null,
                tint = colorPalette().textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicText(
                text = filterContentType.textName,
                style = typography().xs.copy(color = colorPalette().textSecondary)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }


    val topContentInset = rememberTopContentInsetProvider(hasNavBar = !app.n_zik.android.utils.hideBarsInLandscapeMobile())

    Skeleton(
        navController,
        tabIndex,
        onTabIndexChanges,
        miniPlayer,
        topInsetHandledByContent = true,
        topContentInsetProvider = topContentInset,
        navBarContent = { item ->
            searchResultDockTabs.forEachIndexed { index, tab ->
                item(index, stringResource(tab.labelRes), tab.iconRes)
            }
        }
    ) { currentTabIndex ->
        saveableStateHolder.SaveableStateProvider(currentTabIndex) {
            when (currentTabIndex) {
                0, 2, 3 -> {
                    OnlineSearchList(
                        topContentInsetProvider = topContentInset,
                        query = query,
                        tabIndex = currentTabIndex,
                        filterContentType = if (showContentTypeFilter) filterContentType else ContentType.All,
                        navController = navController,
                        disableScrollingText = disableScrollingText,
                        headerContent = headerContent,
                    )
                }

                1, 4, 5, 6 -> {
                    if (useGrid) {
                        OnlineSearchGrid(
                            topContentInsetProvider = topContentInset,
                            query = query,
                            tabIndex = currentTabIndex,
                            filterContentType = if (showContentTypeFilter) filterContentType else ContentType.All,
                            navController = navController,
                            disableScrollingText = disableScrollingText,
                            headerContent = headerContent,
                        )
                    } else {
                        OnlineSearchList(
                            topContentInsetProvider = topContentInset,
                            query = query,
                            tabIndex = currentTabIndex,
                            filterContentType = if (showContentTypeFilter) filterContentType else ContentType.All,
                            navController = navController,
                            disableScrollingText = disableScrollingText,
                            headerContent = headerContent,
                        )
                    }
                }
            }
        }
    }
}




