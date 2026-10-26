package app.n_zik.android.components.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.n_zik.android.components.dialog.settings.HomeAlbumsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeArtistsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeLibraryToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeSongsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.LocalPlaylistToolbarSettingsDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.R

/** Owns the toolbar customization actions. */
@Composable
internal fun UiToolbarsCard(search: Search) {
    val toolbarSearchContextMatch = search.inputValue.isBlank() ||
        stringResource(R.string.toolbar).contains(search.inputValue, true) ||
        stringResource(R.string.songs).contains(search.inputValue, true) ||
        stringResource(R.string.artists).contains(search.inputValue, true) ||
        stringResource(R.string.albums).contains(search.inputValue, true) ||
        stringResource(R.string.playlists).contains(search.inputValue, true)

    AnimatedVisibility(
        visible = toolbarSearchContextMatch,
        enter = settingsCardEnter,
        exit = settingsCardExit
    ) {
        SettingsSectionCard(
            modifier = Modifier.testTag("ui.card.toolbars"),
            title = stringResource(R.string.toolbar),
            icon = R.drawable.ellipsis_horizontal,
            content = {
                if (search.inputValue.isBlank() || stringResource(R.string.songs).contains(search.inputValue, true) || stringResource(R.string.toolbar).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        title = stringResource(R.string.songs),
                        text = stringResource(R.string.customize_toolbar),
                        onClick = { HomeSongsToolbarSettingsDialog.showDialog() },
                        icon = R.drawable.musical_notes
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.artists).contains(search.inputValue, true) || stringResource(R.string.toolbar).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        title = stringResource(R.string.artists),
                        text = stringResource(R.string.customize_toolbar),
                        onClick = { HomeArtistsToolbarSettingsDialog.showDialog() },
                        icon = R.drawable.people
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.albums).contains(search.inputValue, true) || stringResource(R.string.toolbar).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        title = stringResource(R.string.albums),
                        text = stringResource(R.string.customize_toolbar),
                        onClick = { HomeAlbumsToolbarSettingsDialog.showDialog() },
                        icon = R.drawable.album
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.playlists).contains(search.inputValue, true) || stringResource(R.string.toolbar).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        title = stringResource(R.string.playlists),
                        text = stringResource(R.string.customize_toolbar),
                        onClick = { HomeLibraryToolbarSettingsDialog.showDialog() },
                        icon = R.drawable.library
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.playlists).contains(search.inputValue, true) || stringResource(R.string.toolbar).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        title = stringResource(R.string.local_playlist_sort),
                        text = stringResource(R.string.customize_toolbar),
                        onClick = { LocalPlaylistToolbarSettingsDialog.showDialog() },
                        icon = R.drawable.playlist
                    )
                }
            }
        )
    }
}
