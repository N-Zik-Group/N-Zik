package app.n_zik.android.components.settings.ui

import app.n_zik.android.components.settings.common.settingsSearchTarget
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
        stringResource(R.string.settings_library_toolbar_title).contains(search.inputValue, true) ||
        stringResource(R.string.library_songs_title).contains(search.inputValue, true) ||
        stringResource(R.string.library_artists_title).contains(search.inputValue, true) ||
        stringResource(R.string.library_albums_title).contains(search.inputValue, true) ||
        stringResource(R.string.library_playlists_title).contains(search.inputValue, true)

    AnimatedVisibility(
        visible = toolbarSearchContextMatch,
        enter = settingsCardEnter,
        exit = settingsCardExit
    ) {
        SettingsSectionCard(
            modifier = Modifier.testTag("ui.card.toolbars"),
            title = stringResource(R.string.settings_library_toolbar_title),
            icon = R.drawable.ellipsis_horizontal,
            content = {
                if (search.inputValue.isBlank() || stringResource(R.string.library_songs_title).contains(search.inputValue, true) || stringResource(R.string.settings_library_toolbar_title).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.toolbar.songs"),
                        title = stringResource(R.string.library_songs_title),
                        text = stringResource(R.string.settings_library_toolbar_customization_description),
                        onClick = { HomeSongsToolbarSettingsDialog.showDialog() },
                        icon = R.drawable.musical_notes
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.library_artists_title).contains(search.inputValue, true) || stringResource(R.string.settings_library_toolbar_title).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.toolbar.artists"),
                        title = stringResource(R.string.library_artists_title),
                        text = stringResource(R.string.settings_library_toolbar_customization_description),
                        onClick = { HomeArtistsToolbarSettingsDialog.showDialog() },
                        icon = R.drawable.people
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.library_albums_title).contains(search.inputValue, true) || stringResource(R.string.settings_library_toolbar_title).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.toolbar.albums"),
                        title = stringResource(R.string.library_albums_title),
                        text = stringResource(R.string.settings_library_toolbar_customization_description),
                        onClick = { HomeAlbumsToolbarSettingsDialog.showDialog() },
                        icon = R.drawable.album
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.library_playlists_title).contains(search.inputValue, true) || stringResource(R.string.settings_library_toolbar_title).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.toolbar.playlists"),
                        title = stringResource(R.string.library_playlists_title),
                        text = stringResource(R.string.settings_library_toolbar_customization_description),
                        onClick = { HomeLibraryToolbarSettingsDialog.showDialog() },
                        icon = R.drawable.library
                    )
                }
                if (search.inputValue.isBlank() || stringResource(R.string.library_playlists_title).contains(search.inputValue, true) || stringResource(R.string.settings_library_toolbar_title).contains(search.inputValue, true)) {
                    OtherSettingsEntry(
                        modifier = Modifier.settingsSearchTarget("ui.toolbar.local_playlist_sort"),
                        title = stringResource(R.string.settings_local_playlist_title),
                        text = stringResource(R.string.settings_library_toolbar_customization_description),
                        onClick = { LocalPlaylistToolbarSettingsDialog.showDialog() },
                        icon = R.drawable.playlist
                    )
                }
            }
        )
    }
}
