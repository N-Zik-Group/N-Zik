package app.n_zik.android.components.settings.accounts

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.utils.autosyncAlbumsKey
import app.it.fast4x.rimusic.utils.autosyncArtistsKey
import app.it.fast4x.rimusic.utils.autosyncLikesKey
import app.it.fast4x.rimusic.utils.autosyncPlaylistsKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard

/** Owns the AutoSyncCard controls. */
@Composable
internal fun AccountsAutoSyncCard() {
    SettingsSectionCard(
        title = stringResource(R.string.autosync),
        icon = R.drawable.sync,
        content = {
            var autosyncArtists by rememberPreference(autosyncArtistsKey, false)
            var autosyncAlbums by rememberPreference(autosyncAlbumsKey, false)
            var autosyncPlaylists by rememberPreference(autosyncPlaylistsKey, false)
            var autosyncLikes by rememberPreference(autosyncLikesKey, false)

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("accounts.autosync.autosync_channels"),
                title = stringResource(R.string.autosync_channels),
                text = stringResource(R.string.autosync_channels_description),
                isChecked = autosyncArtists,
                onCheckedChange = { autosyncArtists = it },
                icon = R.drawable.people
            )
            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("accounts.autosync.autosync_albums"),
                title = stringResource(R.string.autosync_albums),
                text = stringResource(R.string.autosync_albums_description),
                isChecked = autosyncAlbums,
                onCheckedChange = { autosyncAlbums = it },
                icon = R.drawable.album
            )
            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("accounts.autosync.autosync"),
                title = stringResource(R.string.autosync),
                text = stringResource(R.string.autosync_playlists_description),
                isChecked = autosyncPlaylists,
                onCheckedChange = { autosyncPlaylists = it },
                icon = R.drawable.playlist
            )
            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("accounts.autosync.autosync_likes"),
                title = stringResource(R.string.autosync_likes),
                text = stringResource(R.string.autosync_likes_description),
                isChecked = autosyncLikes,
                onCheckedChange = { autosyncLikes = it },
                icon = R.drawable.heart
            )
        }
    )
}
