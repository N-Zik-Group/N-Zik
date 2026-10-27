package app.n_zik.android.components.settings.ui

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.AlbumSwipeAction
import app.it.fast4x.rimusic.enums.PlaylistSwipeAction
import app.it.fast4x.rimusic.enums.QueueSwipeAction
import app.it.fast4x.rimusic.utils.albumSwipeLeftActionKey
import app.it.fast4x.rimusic.utils.albumSwipeRightActionKey
import app.it.fast4x.rimusic.utils.isSwipeToActionEnabledKey
import app.it.fast4x.rimusic.utils.playlistSwipeLeftActionKey
import app.it.fast4x.rimusic.utils.playlistSwipeRightActionKey
import app.it.fast4x.rimusic.utils.queueSwipeLeftActionKey
import app.it.fast4x.rimusic.utils.queueSwipeRightActionKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.R

/** Owns the swipe settings and their local dialogs. */
@Composable
internal fun UiSwipeCard(search: Search) {
    var isSwipeToActionEnabled by rememberPreference(isSwipeToActionEnabledKey, true)
    var queueSwipeLeftAction by rememberPreference(
        queueSwipeLeftActionKey,
        QueueSwipeAction.RemoveFromQueue
    )
    var queueSwipeRightAction by rememberPreference(
        queueSwipeRightActionKey,
        QueueSwipeAction.PlayNext
    )
    var playlistSwipeLeftAction by rememberPreference(
        playlistSwipeLeftActionKey,
        PlaylistSwipeAction.Favourite
    )
    var playlistSwipeRightAction by rememberPreference(
        playlistSwipeRightActionKey,
        PlaylistSwipeAction.PlayNext
    )
    var albumSwipeLeftAction by rememberPreference(
        albumSwipeLeftActionKey,
        AlbumSwipeAction.PlayNext
    )
    var albumSwipeRightAction by rememberPreference(
        albumSwipeRightActionKey,
        AlbumSwipeAction.Bookmark
    )

    val swipeSearchContextMatch = search.inputValue.isBlank() ||
        stringResource(R.string.swipe_to_action).contains(search.inputValue, true)

    AnimatedVisibility(
        visible = swipeSearchContextMatch,
        enter = settingsCardEnter,
        exit = settingsCardExit
    ) {
        SettingsSectionCard(
            modifier = Modifier.testTag("ui.card.swipe"),
            title = stringResource(R.string.swipe_to_action),
            icon = R.drawable.arrow_forward,
            content = {
                if (search.inputValue.isBlank() || stringResource(R.string.swipe_to_action).contains(search.inputValue, true)) {
                    OtherSwitchSettingEntry(
                        modifier = Modifier.settingsSearchTarget("ui.swipe_to_action.swipe_to_action"),
                        title = stringResource(R.string.swipe_to_action),
                        text = stringResource(R.string.activate_the_action_menu_by_swiping_the_song_left_or_right),
                        isChecked = isSwipeToActionEnabled,
                        onCheckedChange = { isSwipeToActionEnabled = it },
                        icon = R.drawable.arrow_forward
                    )
                }

                AnimatedVisibility(visible = isSwipeToActionEnabled) {
                    Column(modifier = Modifier.padding(start = 25.dp)) {
                        var showQSLeftDialog by remember { mutableStateOf(false) }
                        if (search.inputValue.isBlank() || stringResource(R.string.queue_and_local_playlists_left_swipe).contains(search.inputValue, true) || stringResource(R.string.swipe_to_action).contains(search.inputValue, true)) {
                            OtherSettingsEntry(
                                modifier = Modifier.settingsSearchTarget("ui.swipe_to_action.queue_and_local_playlists_left_swipe"),
                                title = stringResource(R.string.queue_and_local_playlists_left_swipe),
                                text = stringResource(R.string.settings_desc_ui_swipe_to_action_queue_and_local_playlists_left_swipe),
                                selectedValueText = queueSwipeLeftAction.text,
                                icon = R.drawable.arrow_left,
                                onClick = { showQSLeftDialog = true }
                            )
                        }
                        if (showQSLeftDialog) {
                            ValueSelectorDialog(
                                title = stringResource(R.string.queue_and_local_playlists_left_swipe),
                                selectedValue = queueSwipeLeftAction,
                                onValueSelected = { queueSwipeLeftAction = it },
                                valueText = { it.text },
                                values = QueueSwipeAction.values().toList(),
                                onDismiss = { showQSLeftDialog = false }
                            )
                        }

                        var showPLLeftDialog by remember { mutableStateOf(false) }
                        if (search.inputValue.isBlank() || stringResource(R.string.playlist_left_swipe).contains(search.inputValue, true) || stringResource(R.string.swipe_to_action).contains(search.inputValue, true)) {
                            OtherSettingsEntry(
                                modifier = Modifier.settingsSearchTarget("ui.swipe_to_action.playlist_left_swipe"),
                                title = stringResource(R.string.playlist_left_swipe),
                                text = stringResource(R.string.settings_desc_ui_swipe_to_action_playlist_left_swipe),
                                selectedValueText = playlistSwipeLeftAction.text,
                                icon = R.drawable.arrow_left,
                                onClick = { showPLLeftDialog = true }
                            )
                        }
                        if (showPLLeftDialog) {
                            ValueSelectorDialog(
                                title = stringResource(R.string.playlist_left_swipe),
                                selectedValue = playlistSwipeLeftAction,
                                onValueSelected = { playlistSwipeLeftAction = it },
                                valueText = { it.text },
                                values = PlaylistSwipeAction.values().toList(),
                                onDismiss = { showPLLeftDialog = false }
                            )
                        }

                        var showPLRightDialog by remember { mutableStateOf(false) }
                        if (search.inputValue.isBlank() || stringResource(R.string.playlist_right_swipe).contains(search.inputValue, true) || stringResource(R.string.swipe_to_action).contains(search.inputValue, true)) {
                            OtherSettingsEntry(
                                modifier = Modifier.settingsSearchTarget("ui.swipe_to_action.playlist_right_swipe"),
                                title = stringResource(R.string.playlist_right_swipe),
                                text = stringResource(R.string.settings_desc_ui_swipe_to_action_playlist_right_swipe),
                                selectedValueText = playlistSwipeRightAction.text,
                                icon = R.drawable.arrow_right,
                                onClick = { showPLRightDialog = true }
                            )
                        }
                        if (showPLRightDialog) {
                            ValueSelectorDialog(
                                title = stringResource(R.string.playlist_right_swipe),
                                selectedValue = playlistSwipeRightAction,
                                onValueSelected = { playlistSwipeRightAction = it },
                                valueText = { it.text },
                                values = PlaylistSwipeAction.values().toList(),
                                onDismiss = { showPLRightDialog = false }
                            )
                        }

                        var showALeftDialog by remember { mutableStateOf(false) }
                        if (search.inputValue.isBlank() || stringResource(R.string.album_left_swipe).contains(search.inputValue, true) || stringResource(R.string.swipe_to_action).contains(search.inputValue, true)) {
                            OtherSettingsEntry(
                                modifier = Modifier.settingsSearchTarget("ui.swipe_to_action.album_left_swipe"),
                                title = stringResource(R.string.album_left_swipe),
                                text = stringResource(R.string.settings_desc_ui_swipe_to_action_album_left_swipe),
                                selectedValueText = albumSwipeLeftAction.text,
                                icon = R.drawable.arrow_left,
                                onClick = { showALeftDialog = true }
                            )
                        }
                        if (showALeftDialog) {
                            ValueSelectorDialog(
                                title = stringResource(R.string.album_left_swipe),
                                selectedValue = albumSwipeLeftAction,
                                onValueSelected = { albumSwipeLeftAction = it },
                                valueText = { it.text },
                                values = AlbumSwipeAction.values().toList(),
                                onDismiss = { showALeftDialog = false }
                            )
                        }

                        var showARightDialog by remember { mutableStateOf(false) }
                        if (search.inputValue.isBlank() || stringResource(R.string.album_right_swipe).contains(search.inputValue, true) || stringResource(R.string.swipe_to_action).contains(search.inputValue, true)) {
                            OtherSettingsEntry(
                                modifier = Modifier.settingsSearchTarget("ui.swipe_to_action.album_right_swipe"),
                                title = stringResource(R.string.album_right_swipe),
                                text = stringResource(R.string.settings_desc_ui_swipe_to_action_album_right_swipe),
                                selectedValueText = albumSwipeRightAction.text,
                                icon = R.drawable.arrow_right,
                                onClick = { showARightDialog = true }
                            )
                        }
                        if (showARightDialog) {
                            ValueSelectorDialog(
                                title = stringResource(R.string.album_right_swipe),
                                selectedValue = albumSwipeRightAction,
                                onValueSelected = { albumSwipeRightAction = it },
                                valueText = { it.text },
                                values = AlbumSwipeAction.values().toList(),
                                onDismiss = { showARightDialog = false }
                            )
                        }

                        var showQSRightDialog by remember { mutableStateOf(false) }
                        if (search.inputValue.isBlank() || stringResource(R.string.queue_and_local_playlists_right_swipe).contains(search.inputValue, true) || stringResource(R.string.swipe_to_action).contains(search.inputValue, true)) {
                            OtherSettingsEntry(
                                modifier = Modifier.settingsSearchTarget("ui.swipe_to_action.queue_and_local_playlists_right_swipe"),
                                title = stringResource(R.string.queue_and_local_playlists_right_swipe),
                                text = stringResource(R.string.settings_desc_ui_swipe_to_action_queue_and_local_playlists_right_swipe),
                                selectedValueText = queueSwipeRightAction.text,
                                icon = R.drawable.arrow_right,
                                onClick = { showQSRightDialog = true }
                            )
                        }
                        if (showQSRightDialog) {
                            ValueSelectorDialog(
                                title = stringResource(R.string.queue_and_local_playlists_right_swipe),
                                selectedValue = queueSwipeRightAction,
                                onValueSelected = { queueSwipeRightAction = it },
                                valueText = { it.text },
                                values = QueueSwipeAction.values().toList(),
                                onDismiss = { showQSRightDialog = false }
                            )
                        }
                    }
                }
            }
        )
    }
}
