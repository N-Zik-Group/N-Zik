package app.n_zik.android.components.settings.appearance

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.utils.actionExpandedKey
import app.it.fast4x.rimusic.utils.controlsExpandedKey
import app.it.fast4x.rimusic.utils.expandedplayertoggleKey
import app.it.fast4x.rimusic.utils.miniQueueExpandedKey
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.it.fast4x.rimusic.utils.queueDurationExpandedKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showButtonPlayerAddToPlaylistKey
import app.it.fast4x.rimusic.utils.showButtonPlayerArrowKey
import app.it.fast4x.rimusic.utils.showButtonPlayerDiscoverKey
import app.it.fast4x.rimusic.utils.showButtonPlayerDownloadKey
import app.it.fast4x.rimusic.utils.showButtonPlayerLoopKey
import app.it.fast4x.rimusic.utils.showButtonPlayerLyricsKey
import app.it.fast4x.rimusic.utils.showButtonPlayerMenuKey
import app.it.fast4x.rimusic.utils.showButtonPlayerShuffleKey
import app.it.fast4x.rimusic.utils.showButtonPlayerSleepTimerKey
import app.it.fast4x.rimusic.utils.showButtonPlayerSystemEqualizerKey
import app.it.fast4x.rimusic.utils.showButtonPlayerVideoKey
import app.it.fast4x.rimusic.utils.showNextSongsInPlayerKey
import app.it.fast4x.rimusic.utils.showTotalTimeQueueKey
import app.it.fast4x.rimusic.utils.showlyricsthumbnailKey
import app.it.fast4x.rimusic.utils.showthumbnailKey
import app.it.fast4x.rimusic.utils.statsExpandedKey
import app.it.fast4x.rimusic.utils.statsfornerdsKey
import app.it.fast4x.rimusic.utils.timelineExpandedKey
import app.it.fast4x.rimusic.utils.titleExpandedKey
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import androidx.compose.ui.platform.testTag
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit

@OptIn(ExperimentalAnimationApi::class)
@UnstableApi
@Composable
internal fun AppearanceFullscreenLyricsCard(search: Search) {
    var showthumbnail by rememberPreference(showthumbnailKey, true)
    var showButtonPlayerAddToPlaylist by rememberPreference(showButtonPlayerAddToPlaylistKey, true)
    var showButtonPlayerArrow by rememberPreference(showButtonPlayerArrowKey, true)
    var showButtonPlayerDownload by rememberPreference(showButtonPlayerDownloadKey, true)
    var showButtonPlayerLoop by rememberPreference(showButtonPlayerLoopKey, false)
    var showButtonPlayerLyrics by rememberPreference(showButtonPlayerLyricsKey, true)
    var expandedplayertoggle by rememberPreference(expandedplayertoggleKey, false)
    var showButtonPlayerShuffle by rememberPreference(showButtonPlayerShuffleKey, true)
    var showButtonPlayerSleepTimer by rememberPreference(showButtonPlayerSleepTimerKey, false)
    var showButtonPlayerMenu by rememberPreference(showButtonPlayerMenuKey, false)
    var showButtonPlayerSystemEqualizer by rememberPreference(
        showButtonPlayerSystemEqualizerKey,
        false
    )
    var showButtonPlayerDiscover by rememberPreference(showButtonPlayerDiscoverKey, false)
    var showButtonPlayerVideo by rememberPreference(showButtonPlayerVideoKey, true)
    var showTotalTimeQueue by rememberPreference(showTotalTimeQueueKey, true)
    var showNextSongsInPlayer by rememberPreference(showNextSongsInPlayerKey, false)
    var statsfornerds by rememberPreference(statsfornerdsKey, false)
    var playerType by rememberPreference(playerTypeKey, PlayerType.Essential)
    var queueDurationExpanded by rememberPreference(queueDurationExpandedKey, true)
    var titleExpanded by rememberPreference(titleExpandedKey, true)
    var timelineExpanded by rememberPreference(timelineExpandedKey, true)
    var controlsExpanded by rememberPreference(controlsExpandedKey, true)
    var miniQueueExpanded by rememberPreference(miniQueueExpandedKey, true)
    var statsExpanded by rememberPreference(statsExpandedKey, true)
    var actionExpanded by rememberPreference(actionExpandedKey, true)
    var showlyricsthumbnail by rememberPreference(showlyricsthumbnailKey, true)
    val searchCtx_2 = search.inputValue.isBlank() || stringResource(R.string.settings_section_fullscreen_lyrics_title).contains(search.inputValue, true) || stringResource(R.string.settings_player_queue_duration_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_lyrics_title_artist_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_player_timeline_label).contains(search.inputValue, true) || stringResource(R.string.settings_lyrics_controls_visible_label).contains(search.inputValue, true) || stringResource(R.string.lyrics_fullscreen_technical_info_title).contains(search.inputValue, true) || stringResource(R.string.settings_lyrics_action_bar_visible_label).contains(search.inputValue, true) || stringResource(R.string.settings_lyrics_next_queue_visible_label).contains(search.inputValue, true)
    if (!showlyricsthumbnail) {
AnimatedVisibility(
                    visible = searchCtx_2,
                    enter = settingsCardEnter,
                exit = settingsCardExit
                ) {
                    SettingsSectionCard(
                    modifier = Modifier.testTag("appearance.card.fullscreenLyrics"),
                        title = stringResource(R.string.settings_section_fullscreen_lyrics_title),
                        icon = R.drawable.song_lyrics,
                        content = {

                if (showTotalTimeQueue) {
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_player_queue_duration_visible_label).contains(
                            search.inputValue,
                            true
                        )
                    )
                        if (search.inputValue.isBlank() || stringResource(R.string.settings_player_queue_duration_visible_label).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.full_screen_lyrics_components.show_total_time_of_queue"),
                        icon = R.drawable.time,
                                title = stringResource(R.string.settings_player_queue_duration_visible_label),
                                text = stringResource(R.string.lyrics_fullscreen_show_total_time_of_queue_description),
                                isChecked = queueDurationExpanded,
                                onCheckedChange = { queueDurationExpanded = it }
                            )
                        }
                }

                if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_title_artist_visible_label).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_title_artist_visible_label).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.full_screen_lyrics_components.titleartist"),
                        icon = R.drawable.title_edit,
                            title = stringResource(R.string.settings_lyrics_title_artist_visible_label),
                            text = stringResource(R.string.lyrics_fullscreen_title_and_artist_description),
                            isChecked = titleExpanded,
                            onCheckedChange = { titleExpanded = it }
                        )
                    }

                if (search.inputValue.isBlank() || stringResource(R.string.settings_player_timeline_label).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_player_timeline_label).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.full_screen_lyrics_components.timeline"),
                        icon = R.drawable.ellipsis_horizontal,
                            title = stringResource(R.string.settings_player_timeline_label),
                            text = stringResource(R.string.lyrics_fullscreen_timeline_description),
                            isChecked = timelineExpanded,
                            onCheckedChange = { timelineExpanded = it }
                        )
                    }

                if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_controls_visible_label).contains(
                        search.inputValue,
                        true
                    )
                )
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_controls_visible_label).contains(search.inputValue, true)) {
                        OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.full_screen_lyrics_components.controls"),
                        icon = R.drawable.ellipsis_horizontal,
                            title = stringResource(R.string.settings_lyrics_controls_visible_label),
                            text = stringResource(R.string.lyrics_fullscreen_controls_description),
                            isChecked = controlsExpanded,
                            onCheckedChange = { controlsExpanded = it }
                        )
                    }


                if (statsfornerds && (!(showthumbnail && playerType == PlayerType.Essential))){
                    if (search.inputValue.isBlank() || stringResource(R.string.lyrics_fullscreen_technical_info_title).contains(
                            search.inputValue,
                            true
                        )
                    )
                        if (search.inputValue.isBlank() || stringResource(R.string.lyrics_fullscreen_technical_info_title).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.full_screen_lyrics_components.statsfornerds"),
                        icon = R.drawable.stats_chart,
                                title = stringResource(R.string.lyrics_fullscreen_technical_info_title),
                                text = stringResource(R.string.lyrics_fullscreen_technical_info_description),
                                isChecked = statsExpanded,
                                onCheckedChange = { statsExpanded = it }
                            )
                        }
                }

                if (
                    showButtonPlayerDownload ||
                    showButtonPlayerAddToPlaylist ||
                    showButtonPlayerLoop ||
                    showButtonPlayerShuffle ||
                    showButtonPlayerLyrics ||
                    showButtonPlayerSleepTimer ||
                    showButtonPlayerSystemEqualizer ||
                    showButtonPlayerArrow ||
                    showButtonPlayerMenu ||
                    expandedplayertoggle ||
                    showButtonPlayerDiscover ||
                    showButtonPlayerVideo
                ){
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_action_bar_visible_label).contains(
                            search.inputValue,
                            true
                        )
                    )
                        if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_action_bar_visible_label).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.full_screen_lyrics_components.actionbar"),
                        icon = R.drawable.menu,
                                title = stringResource(R.string.settings_lyrics_action_bar_visible_label),
                                text = stringResource(R.string.lyrics_fullscreen_action_bar_description),
                                isChecked = actionExpanded,
                                onCheckedChange = {
                                    actionExpanded = it
                                }
                            )
                        }
                }
                if (showNextSongsInPlayer && actionExpanded) {
                    if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_next_queue_visible_label).contains(
                            search.inputValue,
                            true
                        )
                    )
                        if (search.inputValue.isBlank() || stringResource(R.string.settings_lyrics_next_queue_visible_label).contains(search.inputValue, true)) {
                            OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("appearance.full_screen_lyrics_components.miniqueue"),
                        icon = R.drawable.sort_vertical,
                                title = stringResource(R.string.settings_lyrics_next_queue_visible_label),
                                text = stringResource(R.string.lyrics_fullscreen_queue_preview_description),
                                isChecked = miniQueueExpanded,
                                onCheckedChange = { miniQueueExpanded = it }
                            )
                        }
                }



                        }
                    )
                }
}
}
