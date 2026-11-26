package app.n_zik.android.components.settings.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.components.dialog.settings.PlayerActionBarSettingsDialog
import app.n_zik.android.components.dialog.settings.appearanceToolbarSettingsDialogs
import app.n_zik.android.components.settings.common.OtherEnumValueSelectorSettingsEntry
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.CardCategoryLabel
import app.n_zik.android.components.settings.common.SearchableSettingsSectionHeading
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.settingsSearchTarget
import app.n_zik.android.components.tab.Search
import app.n_zik.android.enums.PlayerOpeningMode
import app.n_zik.android.enums.SongsNumber
import app.n_zik.android.utils.actionspacedevenlyKey
import app.n_zik.android.utils.isLandscape
import app.n_zik.android.utils.playerOpeningModeKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.rememberQueueScrollPositionKey
import app.n_zik.android.utils.showNextSongsInPlayerKey
import app.n_zik.android.utils.showalbumcoverKey
import app.n_zik.android.utils.showsongsKey
import app.n_zik.android.utils.swipeUpQueueKey
import app.n_zik.android.utils.tapqueueKey
import app.n_zik.android.utils.topPaddingKey
import app.n_zik.android.utils.transparentBackgroundPlayerActionBarKey

@UnstableApi
@Composable
internal fun AppearanceActionBarCard(search: Search) {
    var transparentBackground by rememberPreference(transparentBackgroundPlayerActionBarKey, true)
    var evenlySpaced by rememberPreference(actionspacedevenlyKey, true)
    var queuePreviewEnabled by rememberPreference(showNextSongsInPlayerKey, false)
    var queuePreviewSongCount by rememberPreference(showsongsKey, SongsNumber.`2`)
    var queuePreviewArtwork by rememberPreference(showalbumcoverKey, true)
    var tapQueue by rememberPreference(tapqueueKey, true)
    var swipeUpQueue by rememberPreference(swipeUpQueueKey, true)
    var rememberQueueScroll by rememberPreference(rememberQueueScrollPositionKey, true)
    var openingMode by rememberPreference(playerOpeningModeKey, PlayerOpeningMode.Audio)
    var topPadding by rememberPreference(topPaddingKey, true)
    val openingModeLabels = mapOf(
        PlayerOpeningMode.Audio to R.string.player_audio_mode,
        PlayerOpeningMode.Video to R.string.player_video_mode,
        PlayerOpeningMode.RememberLast to R.string.settings_player_opening_mode_remember_last,
    )
    val context = LocalContext.current
    val query = search.inputValue
    val title = stringResource(R.string.settings_section_player_action_bar_title)
    val showAll = query.isBlank() || title.contains(query, ignoreCase = true)
    fun matches(vararg labels: Int) = showAll || labels.any { context.getString(it).contains(query, ignoreCase = true) }

    val appearanceMatches = matches(R.string.settings_group_action_appearance)
    val customizationMatches = matches(R.string.settings_group_action_customization)
    val queueMatches = matches(R.string.settings_group_queue_preview)
    val showBackground = appearanceMatches || matches(R.string.settings_player_action_bar_transparent_background_label)
    val showSpacing = appearanceMatches || matches(R.string.settings_player_actions_spaced_evenly_label)
    val showQueuePreview = queueMatches || matches(R.string.settings_player_next_queue_visible_label)
    val showQueuePreviewSongCount = queueMatches || matches(R.string.settings_player_queue_visible_count_label)
    val showQueuePreviewArtwork = queueMatches || matches(R.string.settings_player_album_cover_visible_label)
    val showTap = queueMatches || matches(R.string.settings_player_queue_tap_to_open_label)
    val showSwipe = queueMatches || matches(R.string.settings_player_queue_swipe_up_label)
    val showQueueScroll = queueMatches || matches(R.string.settings_player_queue_remember_scroll_label,
        R.string.settings_player_queue_remember_scroll_description)
    val showPlayerActions = customizationMatches || matches(R.string.settings_toolbar_player_action_bar_configure,
        R.string.settings_toolbar_player_action_bar_description,
        R.string.player_position_top_label, R.string.player_position_bottom_label) ||
        PlayerActionBarSettingsDialog.searchOptions().any { matches(it.labelRes) }
    val showOpeningMode = appearanceMatches || matches(R.string.settings_player_opening_mode_label,
        R.string.settings_player_opening_mode_description) || openingModeLabels.values.any { matches(it) }
    val showTopPadding = !isLandscape &&
        (appearanceMatches || matches(R.string.settings_lyrics_top_spacing_label, R.string.player_top_spacing_description))
    val toolbarDialogs = appearanceToolbarSettingsDialogs.filter { dialog ->
        customizationMatches || matches(dialog.label) || dialog.searchOptions().any { matches(it.labelRes) }
    }

    val showCustomization = showPlayerActions || toolbarDialogs.isNotEmpty()
    val showAppearance = showBackground || showSpacing || showOpeningMode || showTopPadding
    val showQueue = showQueuePreview || (queuePreviewEnabled && (showQueuePreviewSongCount || showQueuePreviewArtwork)) ||
        showTap || showSwipe || showQueueScroll

    AnimatedVisibility(
        visible = showCustomization || showAppearance || showQueue,
        enter = settingsCardEnter,
        exit = settingsCardExit,
    ) {
        SettingsSectionCard(
            modifier = Modifier.testTag("appearance.card.actionBar"),
            title = title,
            icon = R.drawable.ellipsis_vertical,
            content = {
                if (search.inputValue.isBlank()) CardCategoryLabel(stringResource(R.string.settings_group_action_customization))
                if (showPlayerActions) OtherSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.player_action_bar_buttons"),
                    icon = R.drawable.ellipsis_vertical,
                    title = stringResource(R.string.settings_toolbar_player_action_bar_configure),
                    text = stringResource(R.string.settings_toolbar_player_action_bar_description),
                    onClick = PlayerActionBarSettingsDialog::showDialog,
                )

                toolbarDialogs.forEach { dialog ->
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget(dialog.entryId),
                        icon = dialog.icon,
                        title = stringResource(dialog.label),
                        text = stringResource(R.string.settings_library_toolbar_customization_description),
                        onClick = dialog::showDialog,
                    )
                }

                if (showCustomization && showAppearance) SearchableSettingsSectionHeading(R.string.settings_group_action_appearance, "appearance.player_action_bar.section.appearance")

                if (showBackground) OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.action_bar_transparent_background"),
                    icon = R.drawable.eye,
                    title = stringResource(R.string.settings_player_action_bar_transparent_background_label),
                    text = stringResource(R.string.player_action_bar_transparent_background_description),
                    isChecked = transparentBackground,
                    onCheckedChange = { transparentBackground = it },
                )
                if (showSpacing) OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.actionspacedevenly"),
                    icon = R.drawable.horizontal_bold_line,
                    title = stringResource(R.string.settings_player_actions_spaced_evenly_label),
                    text = stringResource(R.string.player_action_bar_even_button_spacing_description),
                    isChecked = evenlySpaced,
                    onCheckedChange = { evenlySpaced = it },
                )

                if (showOpeningMode) OtherEnumValueSelectorSettingsEntry(
                    modifier = Modifier.settingsSearchTarget("appearance.player_behavior_and_visuals.opening_mode"),
                    icon = R.drawable.video,
                    title = stringResource(R.string.settings_player_opening_mode_label),
                    text = stringResource(
                        R.string.settings_description_with_value,
                        stringResource(R.string.settings_player_opening_mode_description),
                        stringResource(openingModeLabels.getValue(openingMode)),
                    ),
                    selectedValue = openingMode,
                    onValueSelected = { openingMode = it },
                    valueText = { stringResource(openingModeLabels.getValue(it)) },
                )
                if (showTopPadding) OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("appearance.player_behavior_and_visuals.blankspace"),
                    icon = R.drawable.space,
                    title = stringResource(R.string.settings_lyrics_top_spacing_label),
                    text = stringResource(R.string.player_top_spacing_description),
                    isChecked = topPadding,
                    onCheckedChange = { topPadding = it },
                )

                if (showQueue && (showCustomization || showAppearance)) SearchableSettingsSectionHeading(R.string.settings_group_queue_preview, "appearance.player_action_bar.section.queue_preview")

                if (showQueuePreview) OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("appearance.player_behavior_and_visuals.show_next_songs_in_player"),
                    icon = R.drawable.arrow_forward,
                    title = stringResource(R.string.settings_player_next_queue_visible_label),
                    text = stringResource(R.string.player_show_next_songs_in_player_description),
                    isChecked = queuePreviewEnabled,
                    onCheckedChange = { queuePreviewEnabled = it },
                )
                AnimatedVisibility(visible = queuePreviewEnabled && (showQueuePreviewSongCount || showQueuePreviewArtwork)) {
                    Column {
                        if (showQueuePreviewSongCount) OtherEnumValueSelectorSettingsEntry(
                            modifier = Modifier.padding(start = 25.dp)
                                .settingsSearchTarget("appearance.player_behavior_and_visuals.songs_number_to_show"),
                            icon = R.drawable.information,
                            title = stringResource(R.string.settings_player_queue_visible_count_label),
                            text = stringResource(
                                R.string.settings_description_with_value,
                                stringResource(R.string.player_queue_preview_song_count_description),
                                queuePreviewSongCount.name,
                            ),
                            selectedValue = queuePreviewSongCount,
                            onValueSelected = { queuePreviewSongCount = it },
                            valueText = { it.name },
                        )
                        if (showQueuePreviewArtwork) OtherSwitchSettingEntry(
                            modifier = Modifier.padding(start = 25.dp)
                                .settingsSearchTarget("appearance.player_behavior_and_visuals.showalbumcover"),
                            icon = R.drawable.album,
                            title = stringResource(R.string.settings_player_album_cover_visible_label),
                            text = stringResource(R.string.player_queue_preview_artwork_description),
                            isChecked = queuePreviewArtwork,
                            onCheckedChange = { queuePreviewArtwork = it },
                        )
                    }
                }
                if (showTap) OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.tapqueue"),
                    icon = R.drawable.gesture,
                    title = stringResource(R.string.settings_player_queue_tap_to_open_label),
                    text = stringResource(R.string.player_action_bar_queue_preview_tap_description),
                    isChecked = tapQueue,
                    onCheckedChange = { tapQueue = it },
                )
                if (showSwipe) OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("appearance.player_action_bar.swipe_up_to_open_the_queue"),
                    icon = R.drawable.gesture,
                    title = stringResource(R.string.settings_player_queue_swipe_up_label),
                    text = stringResource(R.string.player_action_bar_swipe_up_to_open_the_queue_description),
                    isChecked = swipeUpQueue,
                    onCheckedChange = { swipeUpQueue = it },
                )
                if (showQueueScroll) OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("ui.user_interface.remember_queue_scroll_position"),
                    icon = R.drawable.playlist,
                    title = stringResource(R.string.settings_player_queue_remember_scroll_label),
                    text = stringResource(R.string.settings_player_queue_remember_scroll_description),
                    isChecked = rememberQueueScroll,
                    onCheckedChange = { rememberQueueScroll = it },
                )
            },
        )
    }
}
