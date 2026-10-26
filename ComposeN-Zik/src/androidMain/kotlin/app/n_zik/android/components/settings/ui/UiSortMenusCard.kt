package app.n_zik.android.components.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.n_zik.android.components.dialog.settings.HistorySortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeAlbumsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeArtistsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeLibrarySortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeSongsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.LocalPlaylistSortSettingsDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.R

/** Owns the sort menu customization actions. */
@Composable
internal fun UiSortMenusCard(search: Search) {
    val sortMenuSearchContextMatch = search.inputValue.isBlank() ||
        "sort".contains(search.inputValue, true) ||
        stringResource(R.string.songs).contains(search.inputValue, true) ||
        stringResource(R.string.artists).contains(search.inputValue, true) ||
        stringResource(R.string.albums).contains(search.inputValue, true) ||
        stringResource(R.string.playlists).contains(search.inputValue, true) ||
        stringResource(R.string.history).contains(search.inputValue, true)

    AnimatedVisibility(
        visible = sortMenuSearchContextMatch,
        enter = settingsCardEnter,
        exit = settingsCardExit
    ) {
        SettingsSectionCard(
            modifier = Modifier.testTag("ui.card.sort-menus"),
            title = stringResource(R.string.sort_menu),
            icon = R.drawable.arrow_up,
            content = {
                if (search.inputValue.isBlank() || stringResource(R.string.songs).contains(search.inputValue, true) || stringResource(R.string.sort_menu).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        title = stringResource(R.string.songs),
                        text = stringResource(R.string.customize_sort_options),
                        onClick = { HomeSongsSortSettingsDialog.showDialog() },
                        icon = R.drawable.musical_notes
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.artists).contains(search.inputValue, true) || stringResource(R.string.sort_menu).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        title = stringResource(R.string.artists),
                        text = stringResource(R.string.customize_sort_options),
                        onClick = { HomeArtistsSortSettingsDialog.showDialog() },
                        icon = R.drawable.people
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.albums).contains(search.inputValue, true) || stringResource(R.string.sort_menu).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        title = stringResource(R.string.albums),
                        text = stringResource(R.string.customize_sort_options),
                        onClick = { HomeAlbumsSortSettingsDialog.showDialog() },
                        icon = R.drawable.album
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.playlists).contains(search.inputValue, true) || stringResource(R.string.sort_menu).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        title = stringResource(R.string.playlists),
                        text = stringResource(R.string.customize_sort_options),
                        onClick = { HomeLibrarySortSettingsDialog.showDialog() },
                        icon = R.drawable.library
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.playlists).contains(search.inputValue, true) || stringResource(R.string.sort_menu).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        title = stringResource(R.string.local_playlist_sort),
                        text = stringResource(R.string.customize_sort_options),
                        onClick = { LocalPlaylistSortSettingsDialog.showDialog() },
                        icon = R.drawable.playlist
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.history).contains(search.inputValue, true) || stringResource(R.string.sort_menu).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        title = stringResource(R.string.history),
                        text = stringResource(R.string.customize_sort_options),
                        onClick = { HistorySortSettingsDialog.showDialog() },
                        icon = R.drawable.time
                    )
                }
            }
        )
    }
}
