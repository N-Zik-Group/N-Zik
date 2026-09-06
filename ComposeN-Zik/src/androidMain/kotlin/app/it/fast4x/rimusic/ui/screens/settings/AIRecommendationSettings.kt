package app.it.fast4x.rimusic.ui.screens.settings

import app.n_zik.android.core.database.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.PlayEventsType
import app.it.fast4x.rimusic.enums.LocalRecommandationsNumber
import app.it.fast4x.rimusic.enums.RecommendationsNumber
import app.it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.components.themed.ValueSelectorDialog
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.enableQuickPicksPageKey
import app.it.fast4x.rimusic.utils.playEventsTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showChartsKey
import app.it.fast4x.rimusic.utils.showMonthlyPlaylistInQuickPicksKey
import app.it.fast4x.rimusic.utils.showMoodsAndGenresKey
import app.it.fast4x.rimusic.utils.showNewAlbumsArtistsKey
import app.it.fast4x.rimusic.utils.showNewAlbumsKey
import app.it.fast4x.rimusic.utils.showPlaylistMightLikeKey
import app.it.fast4x.rimusic.utils.showRelatedAlbumsKey
import app.it.fast4x.rimusic.utils.showSimilarArtistsKey
import app.it.fast4x.rimusic.utils.showTipsKey
import app.it.fast4x.rimusic.utils.recommendationsNumberKey
import app.it.fast4x.rimusic.utils.enableCreateMonthlyPlaylistsKey
import app.it.fast4x.rimusic.utils.showMonthlyPlaylistsKey
import app.it.fast4x.rimusic.utils.showMyTopPlaylistKey
import app.it.fast4x.rimusic.utils.showStatsListeningTimeKey
import app.it.fast4x.rimusic.utils.maxStatisticsItemsKey
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsKey
import app.it.fast4x.rimusic.enums.MaxStatisticsItems
import app.it.fast4x.rimusic.enums.MaxTopPlaylistItems
import kotlinx.coroutines.Dispatchers
import app.kreate.android.me.knighthat.utils.Toaster
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.dialog.settings.QuickPicksContentSettingsDialog
import androidx.compose.runtime.LaunchedEffect

@Composable
fun DefaultAIRecommendationSettings() {
    var playEventType by rememberPreference(
        playEventsTypeKey,
        PlayEventsType.MostPlayed
    )
    playEventType = PlayEventsType.MostPlayed
    var showTips by rememberPreference(showTipsKey, true)
    showTips = true
    var showRelatedAlbums by rememberPreference(showRelatedAlbumsKey, true)
    showRelatedAlbums = true
    var showSimilarArtists by rememberPreference(showSimilarArtistsKey, true)
    showSimilarArtists = true
    var showNewAlbumsArtists by rememberPreference(showNewAlbumsArtistsKey, true)
    showNewAlbumsArtists = true
    var showNewAlbums by rememberPreference(showNewAlbumsKey, true)
    showNewAlbums = true
    var showPlaylistMightLike by rememberPreference(showPlaylistMightLikeKey, true)
    showPlaylistMightLike = true
    var showMoodsAndGenres by rememberPreference(showMoodsAndGenresKey, true)
    showMoodsAndGenres = true
    var showMonthlyPlaylistInQuickPicks by rememberPreference(showMonthlyPlaylistInQuickPicksKey, true)
    showMonthlyPlaylistInQuickPicks = true
    var showCharts by rememberPreference(showChartsKey, true)
    showCharts = true
    var enableQuickPicksPage by rememberPreference(enableQuickPicksPageKey, true)
    enableQuickPicksPage = true
    var localRecommandationsNumber by rememberPreference(
        key = "LocalRecommandationsNumber",
        defaultValue = LocalRecommandationsNumber.SixQ
    )
    localRecommandationsNumber = LocalRecommandationsNumber.SixQ
    var recommendationsNumber by rememberPreference(recommendationsNumberKey, RecommendationsNumber.Adaptive)
    recommendationsNumber = RecommendationsNumber.Adaptive
    
    // Monthly Playlists Settings
    var enableCreateMonthlyPlaylists by rememberPreference(enableCreateMonthlyPlaylistsKey, true)
    enableCreateMonthlyPlaylists = true
    var showMonthlyPlaylists by rememberPreference(showMonthlyPlaylistsKey, true)
    showMonthlyPlaylists = true
    
    // Statistics Settings
    var showMyTopPlaylist by rememberPreference(showMyTopPlaylistKey, true)
    showMyTopPlaylist = true
    var showStatsListeningTime by rememberPreference(showStatsListeningTimeKey, true)
    showStatsListeningTime = true
    var maxStatisticsItems by rememberPreference(maxStatisticsItemsKey, MaxStatisticsItems.`10`)
    maxStatisticsItems = MaxStatisticsItems.`10`
    
    // Top Playlists Settings
    var maxTopPlaylistItems by rememberPreference(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`)
    maxTopPlaylistItems = MaxTopPlaylistItems.`10`
}

@ExperimentalAnimationApi
@UnstableApi
@Composable
fun AIRecommendationSettings(
    navController: NavController,
    ytLoggedIn: Boolean = false
) {
    var playEventType by rememberPreference(
        playEventsTypeKey,
        PlayEventsType.MostPlayed
    )
    var showTips by rememberPreference(showTipsKey, true)
    var showRelatedAlbums by rememberPreference(showRelatedAlbumsKey, true)
    var showSimilarArtists by rememberPreference(showSimilarArtistsKey, true)
    var showNewAlbumsArtists by rememberPreference(showNewAlbumsArtistsKey, true)
    var showNewAlbums by rememberPreference(showNewAlbumsKey, true)
    var showPlaylistMightLike by rememberPreference(showPlaylistMightLikeKey, true)
    var showMoodsAndGenres by rememberPreference(showMoodsAndGenresKey, true)
    var showMonthlyPlaylistInQuickPicks by rememberPreference(showMonthlyPlaylistInQuickPicksKey, true)
    var showCharts by rememberPreference(showChartsKey, true)
    var enableQuickPicksPage by rememberPreference(enableQuickPicksPageKey, true)
    var clearEvents by remember { mutableStateOf(false) }
    var localRecommandationsNumber by rememberPreference(
        key = "LocalRecommandationsNumber",
        defaultValue = LocalRecommandationsNumber.SixQ
    )
    var recommendationsNumber by rememberPreference(recommendationsNumberKey, RecommendationsNumber.Adaptive)
    
    // Monthly Playlists Settings
    var enableCreateMonthlyPlaylists by rememberPreference(enableCreateMonthlyPlaylistsKey, true)
    var showMonthlyPlaylists by rememberPreference(showMonthlyPlaylistsKey, true)
    
    // Statistics Settings
    var showMyTopPlaylist by rememberPreference(showMyTopPlaylistKey, true)
    var showStatsListeningTime by rememberPreference(showStatsListeningTimeKey, true)
    var maxStatisticsItems by rememberPreference(maxStatisticsItemsKey, MaxStatisticsItems.`10`)
    
    // Top Playlists Settings
    var maxTopPlaylistItems by rememberPreference(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`)
    
    val search = Search()
    
    val searchCtx_0 = search.inputValue.isBlank() || stringResource(R.string.tab_general).contains(search.inputValue, true) || stringResource(R.string.enable_quick_picks_page).contains(search.inputValue, true) || stringResource(R.string.disable_if_you_do_not_want_to_see).contains(search.inputValue, true)
    val searchCtx_1 = search.inputValue.isBlank() || stringResource(R.string.quick_picks).contains(search.inputValue, true) || stringResource(R.string.quick_picks_content).contains(search.inputValue, true) || stringResource(R.string.show).contains(search.inputValue, true) || stringResource(R.string.tips).contains(search.inputValue, true) || stringResource(R.string.charts).contains(search.inputValue, true) || stringResource(R.string.related_albums).contains(search.inputValue, true) || stringResource(R.string.similar_artists).contains(search.inputValue, true) || stringResource(R.string.new_albums_of_your_artists).contains(search.inputValue, true) || stringResource(R.string.new_albums).contains(search.inputValue, true) || stringResource(R.string.playlists_you_might_like).contains(search.inputValue, true) || stringResource(R.string.moods_and_genres).contains(search.inputValue, true) || stringResource(R.string.show_monthly_playlists_in_quick_picks).contains(search.inputValue, true) || stringResource(R.string.disable_if_you_do_not_want_to_see).contains(search.inputValue, true)
    val searchCtx_2 = search.inputValue.isBlank() || stringResource(R.string.tips).contains(search.inputValue, true) || stringResource(R.string.quick_selection_type).contains(search.inputValue, true)
    val searchCtx_3 = search.inputValue.isBlank() || stringResource(R.string.monthly_playlists).contains(search.inputValue, true) || stringResource(R.string.show_monthly_playlists_in_library).contains(search.inputValue, true) || stringResource(R.string.enable_monthly_playlists_creation).contains(search.inputValue, true)
    val searchCtx_4 = search.inputValue.isBlank() || stringResource(R.string.smart_recommendations).contains(search.inputValue, true) || stringResource(R.string.smart_recommendations_number).contains(search.inputValue, true)
    val searchCtx_5 = search.inputValue.isBlank() || stringResource(R.string.statistics).contains(search.inputValue, true) || stringResource(R.string.statistics_max_number_of_items).contains(search.inputValue, true) || stringResource(R.string.listening_time).contains(search.inputValue, true)
    val searchCtx_6 = search.inputValue.isBlank() || stringResource(R.string.playlist_top).contains(search.inputValue, true) || stringResource(R.string.statistics_max_number_of_items).contains(search.inputValue, true) || stringResource(R.string.my_playlist_top1).contains(search.inputValue, true)
    val searchCtx_7 = search.inputValue.isBlank() || stringResource(R.string.tab_data).contains(search.inputValue, true) || stringResource(R.string.reset_quick_picks).contains(search.inputValue, true)
    val searchCtx_8 = search.inputValue.isBlank() || stringResource(R.string.settings_reset).contains(search.inputValue, true)

    if (clearEvents) {
        ConfirmationDialog(
            text = stringResource(R.string.do_you_really_want_to_delete_all_playback_events),
            onDismiss = { clearEvents = false },
            onConfirm = {
                Database.asyncTransaction {
                    eventTable.deleteAll()
                    Toaster.done()
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {

        QuickPicksContentSettingsDialog.Render()

        HeaderWithIcon(
            title = if (!ytLoggedIn) stringResource(R.string.ai_recommendations) else stringResource(R.string.home),
            iconId = if (!ytLoggedIn) R.drawable.sparkles else R.drawable.ytmusic,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {}
        )

        SettingsDescription(
            text = stringResource(R.string.quick_picks_description),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        ) 

        /* Removed Spacer */
        
        search.ToolBarButton()
        search.SearchBar( this )

        // General Settings Section
        AnimatedVisibility(
            visible = searchCtx_0,
            enter = fadeIn(animationSpec = tween(600)) + scaleIn(
                animationSpec = tween(600),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.tab_general),
                icon = R.drawable.settings,
                content = {
                    if (search.inputValue.isBlank() || stringResource(R.string.enable_quick_picks_page).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            title = stringResource(R.string.enable_quick_picks_page),
                            text = "",
                            isChecked = enableQuickPicksPage,
                            onCheckedChange = {
                                enableQuickPicksPage = it
                            },
                            icon = R.drawable.sparkles
                        )
                    }
                }
            )
        }

        /* Removed Spacer */

        // Content Sections
        AnimatedVisibility(
            visible = enableQuickPicksPage && searchCtx_1,
            enter = fadeIn(animationSpec = tween(800)) + scaleIn(
                animationSpec = tween(800),
                initialScale = 0.9f
            ),
            exit = fadeOut(animationSpec = tween(200)) + scaleOut(
                animationSpec = tween(200),
                targetScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.quick_picks),
                icon = R.drawable.star_brilliant,
                content = {
                    if (search.inputValue.isBlank() || stringResource(R.string.quick_picks_content).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.quick_picks_content),
                            text = stringResource(R.string.quick_picks_content_description),
                            onClick = { QuickPicksContentSettingsDialog.showDialog() },
                            icon = R.drawable.star_brilliant
                        )
                    }
                }
            )
        }

        /* Removed Spacer */

        // Tips Configuration Section
        AnimatedVisibility(
            visible = enableQuickPicksPage && showTips && searchCtx_2,
            enter = fadeIn(animationSpec = tween(1000)) + scaleIn(
                animationSpec = tween(1000),
                initialScale = 0.9f
            ),
            exit = fadeOut(animationSpec = tween(200)) + scaleOut(
                animationSpec = tween(200),
                targetScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.tips),
                icon = R.drawable.person,
                content = {
                    var showTipsDialog by remember { mutableStateOf(false) }
                    if (search.inputValue.isBlank() || stringResource(R.string.tips).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.tips),
                            text = playEventType.text,
                            icon = R.drawable.sort_vertical,
                            onClick = { showTipsDialog = true }
                        )
                    }

                    if (showTipsDialog) {
                        ValueSelectorDialog(
                            title = stringResource(R.string.tips),
                            selectedValue = playEventType,
                            values = PlayEventsType.values().toList(),
                            onValueSelected = { playEventType = it },
                            valueText = { it.text },
                            onDismiss = { showTipsDialog = false }
                        )
                    }

                    var showQuickSelectionDialog by remember { mutableStateOf(false) }
                    if (search.inputValue.isBlank() || stringResource(R.string.quick_selection_type).contains(search.inputValue, true) || (stringResource(R.string.quick_selection, localRecommandationsNumber.value)).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.quick_selection_type),
                            text = stringResource(R.string.quick_selection, localRecommandationsNumber.value),
                            icon = R.drawable.sparkles,
                            onClick = { showQuickSelectionDialog = true }
                        )
                    }

                    if (showQuickSelectionDialog) {
                        ValueSelectorDialog(
                            title = stringResource(R.string.quick_selection_type),
                            selectedValue = localRecommandationsNumber,
                            values = LocalRecommandationsNumber.values().toList(),
                            onValueSelected = { localRecommandationsNumber = it },
                            valueText = { option ->
                                stringResource(R.string.quick_selection, option.value)
                            },
                            onDismiss = { showQuickSelectionDialog = false }
                        )
                    }
                }
            )
        }

        /* Removed Spacer */

        // Monthly Playlists Section
        AnimatedVisibility(
            visible = searchCtx_3,
            enter = fadeIn(animationSpec = tween(1400)) + scaleIn(
                animationSpec = tween(1400),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.monthly_playlists),
                icon = R.drawable.calendar,
                content = {
					if (search.inputValue.isBlank() || stringResource(R.string.show_monthly_playlists_in_library).contains(search.inputValue, true) || (stringResource(R.string.disable_if_you_do_not_want_to_see) + " " + stringResource(R.string.monthly_playlists) + " " + stringResource(R.string.in_txt) + " " + stringResource(R.string.library)).contains(search.inputValue, true)) {
    					OtherSwitchSettingEntry(
    						title = stringResource(R.string.show_monthly_playlists_in_library),
    						text = stringResource(R.string.disable_if_you_do_not_want_to_see) + " " + stringResource(R.string.monthly_playlists) + " " + stringResource(R.string.in_txt) + " " + stringResource(R.string.library),
    						isChecked = showMonthlyPlaylists,
    						onCheckedChange = {
    							showMonthlyPlaylists = it
    						},
    						icon = R.drawable.eye
    					)
					}

                    if (search.inputValue.isBlank() || stringResource(R.string.enable_monthly_playlists_creation).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            title = stringResource(R.string.enable_monthly_playlists_creation),
                            text = "",
                            isChecked = enableCreateMonthlyPlaylists,
                            onCheckedChange = {
                                enableCreateMonthlyPlaylists = it
                            },
                            icon = R.drawable.calendar_clear
                        )
                    }
                }
            )
        }

        /* Removed Spacer */

        // Smart Recommendations Section
        AnimatedVisibility(
            visible = searchCtx_4,
            enter = fadeIn(animationSpec = tween(1400)) + scaleIn(
                animationSpec = tween(1400),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.smart_recommendations),
                icon = R.drawable.smart_shuffle,
                content = {
                    var showRecommendationsDialog by remember { mutableStateOf(false) }
                    if (search.inputValue.isBlank() || stringResource(R.string.smart_recommendations_number).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.smart_recommendations_number),
                            text = if (recommendationsNumber == RecommendationsNumber.Adaptive) 
                                stringResource(R.string.smart_recommendations_adaptive_description) else recommendationsNumber.text,
                            icon = R.drawable.shuffle,
                            onClick = { showRecommendationsDialog = true }
                        )
                    }

                    if (showRecommendationsDialog) {
                        ValueSelectorDialog(
                            title = stringResource(R.string.smart_recommendations_number),
                            selectedValue = recommendationsNumber,
                            values = RecommendationsNumber.values().toList(),
                            onValueSelected = { recommendationsNumber = it },
                            valueText = { 
                                when (it) {
                                    RecommendationsNumber.Adaptive -> stringResource(R.string.smart_recommendations_adaptive)
                                    else -> it.text
                                }
                            },
                            onDismiss = { showRecommendationsDialog = false }
                        )
                    }
                }
            )
        }

        /* Removed Spacer */

        // Statistics Section
        AnimatedVisibility(
            visible = searchCtx_5,
            enter = fadeIn(animationSpec = tween(1600)) + scaleIn(
                animationSpec = tween(1600),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.statistics),
                icon = R.drawable.trending,
                content = {
                    var showStatisticsDialog by remember { mutableStateOf(false) }
                    if (search.inputValue.isBlank() || stringResource(R.string.statistics_max_number_of_items).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.statistics_max_number_of_items),
                            text = maxStatisticsItems.name,
                            icon = R.drawable.musical_notes,
                            onClick = { showStatisticsDialog = true }
                        )
                    }

                    if (showStatisticsDialog) {
                        ValueSelectorDialog(
                            title = stringResource(R.string.statistics_max_number_of_items),
                            selectedValue = maxStatisticsItems,
                            values = MaxStatisticsItems.values().toList(),
                            onValueSelected = { maxStatisticsItems = it },
                            valueText = { it.name },
                            onDismiss = { showStatisticsDialog = false }
                        )
                    }

                    if (search.inputValue.isBlank() || stringResource(R.string.listening_time).contains(search.inputValue, true) || (stringResource(R.string.shows_the_number_of_songs_heard_and_their_listening_time)).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            title = stringResource(R.string.listening_time),
                            text = stringResource(R.string.shows_the_number_of_songs_heard_and_their_listening_time),
                            isChecked = showStatsListeningTime,
                            onCheckedChange = {
                                showStatsListeningTime = it
                            },
                            icon = R.drawable.time
                        )
                    }
                }
            )
        }

        /* Removed Spacer */

        // Top Playlists Section
        AnimatedVisibility(
            visible = searchCtx_6,
            enter = fadeIn(animationSpec = tween(1800)) + scaleIn(
                animationSpec = tween(1800),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.playlist_top),
                icon = R.drawable.playlist,
                content = {
                    var showTopPlaylistsDialog by remember { mutableStateOf(false) }
                    if (search.inputValue.isBlank() || stringResource(R.string.statistics_max_number_of_items).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.statistics_max_number_of_items),
                            text = maxTopPlaylistItems.name,
                            icon = R.drawable.musical_notes,
                            onClick = { showTopPlaylistsDialog = true }
                        )
                    }

                    if (showTopPlaylistsDialog) {
                        ValueSelectorDialog(
                            title = stringResource(R.string.statistics_max_number_of_items),
                            selectedValue = maxTopPlaylistItems,
                            values = MaxTopPlaylistItems.values().toList(),
                            onValueSelected = { maxTopPlaylistItems = it },
                            valueText = { it.name },
                            onDismiss = { showTopPlaylistsDialog = false }
                        )
                    }

                    if (search.inputValue.isBlank() || "${stringResource(R.string.show)} ${stringResource(R.string.my_playlist_top1)}".contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                            title = "${stringResource(R.string.show)} ${stringResource(R.string.my_playlist_top1)}",
                            text = "",
                            isChecked = showMyTopPlaylist,
                            onCheckedChange = {
                                showMyTopPlaylist = it
                            },
                            icon = R.drawable.trending
                        )
                    }
                }

            )
        }

        /* Removed Spacer */

        // Data Management Section
        AnimatedVisibility(
            visible = searchCtx_7,
            enter = fadeIn(animationSpec = tween(2000)) + scaleIn(
                animationSpec = tween(2000),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.tab_data),
                icon = R.drawable.server,
                content = {
                    val eventsCount by remember {
                        Database.eventTable
                                .countAll()
                    }.collectAsState( 0L, Dispatchers.IO )

                    if (search.inputValue.isBlank() || stringResource(R.string.reset_quick_picks).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.reset_quick_picks),
                            text = if (eventsCount > 0) {
                                stringResource(R.string.delete_playback_events, eventsCount)
                            } else {
                                stringResource(R.string.quick_picks_are_cleared)
                            },
                            icon = R.drawable.trash,
                            onClick = { clearEvents = true }
                        )
                    }
                }
            )
        }

        /* Removed Spacer */

        // Reset to Default Section
        AnimatedVisibility(
            visible = searchCtx_8,
            enter = fadeIn(animationSpec = tween(2200)) + scaleIn(
                animationSpec = tween(2200),
                initialScale = 0.9f
            )
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.settings_reset),
                icon = R.drawable.refresh,
                content = {
                    var resetToDefault by remember { mutableStateOf(false) }
                    val context = LocalContext.current
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_reset).contains(search.inputValue, true) || (stringResource(R.string.settings_restore_default_settings)).contains(search.inputValue, true)) {
                        OtherSettingsEntry(
                            title = stringResource(R.string.settings_reset),
                            text = stringResource(R.string.settings_restore_default_settings),
                            icon = R.drawable.refresh,
                            onClick = { resetToDefault = true
                                QuickPicksContentSettingsDialog.reset(context) }
                        )
                    }
                    if (resetToDefault) {
                        DefaultAIRecommendationSettings()
                        LaunchedEffect(Unit) {
                            resetToDefault = false
                        }
                        Toaster.done()
                    }
                }
            )
        }

        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )

    }
}




