package app.n_zik.android.components.settings.accounts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.syncPushAlbumBookmarkKey
import app.it.fast4x.rimusic.utils.syncPushArtistFollowKey
import app.it.fast4x.rimusic.utils.syncPushEpisodeKey
import app.it.fast4x.rimusic.utils.syncPushHistoryKey
import app.it.fast4x.rimusic.utils.syncPushPlaylistKey
import app.it.fast4x.rimusic.utils.syncPushSongLikeKey
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard

/** Owns the SyncPushCard controls. */
@Composable
internal fun AccountsSyncPushCard() {
    SettingsSectionCard(
        title = stringResource(R.string.sync_push_to_youtube),
        icon = R.drawable.arrow_up,
        content = {
            var syncPushSongLike by rememberPreference(syncPushSongLikeKey, false)
            var syncPushAlbumBookmark by rememberPreference(syncPushAlbumBookmarkKey, false)
            var syncPushArtistFollow by rememberPreference(syncPushArtistFollowKey, false)
            var syncPushPlaylist by rememberPreference(syncPushPlaylistKey, false)
            var syncPushHistory by rememberPreference(syncPushHistoryKey, false)

            OtherSwitchSettingEntry(
                title = stringResource(R.string.sync_push_history),
                text = stringResource(R.string.sync_push_history_description),
                isChecked = syncPushHistory,
                onCheckedChange = { syncPushHistory = it },
                icon = R.drawable.history
            )
            OtherSwitchSettingEntry(
                title = stringResource(R.string.sync_push_song_like),
                text = stringResource(R.string.sync_push_song_like_description),
                isChecked = syncPushSongLike,
                onCheckedChange = { syncPushSongLike = it },
                icon = R.drawable.heart
            )
            OtherSwitchSettingEntry(
                title = stringResource(R.string.sync_push_album_bookmark),
                text = stringResource(R.string.sync_push_album_bookmark_description),
                isChecked = syncPushAlbumBookmark,
                onCheckedChange = { syncPushAlbumBookmark = it },
                icon = R.drawable.album
            )
            OtherSwitchSettingEntry(
                title = stringResource(R.string.sync_push_artist_follow),
                text = stringResource(R.string.sync_push_artist_follow_description),
                isChecked = syncPushArtistFollow,
                onCheckedChange = { syncPushArtistFollow = it },
                icon = R.drawable.people
            )
            OtherSwitchSettingEntry(
                title = stringResource(R.string.sync_push_playlist),
                text = stringResource(R.string.sync_push_playlist_description),
                isChecked = syncPushPlaylist,
                onCheckedChange = { syncPushPlaylist = it },
                icon = R.drawable.playlist
            )
            var syncPushEpisode by rememberPreference(syncPushEpisodeKey, false)
            OtherSwitchSettingEntry(
                title = stringResource(R.string.sync_push_episode),
                text = stringResource(R.string.sync_push_episode_description),
                isChecked = syncPushEpisode,
                onCheckedChange = { syncPushEpisode = it },
                icon = R.drawable.podcast
            )
        }
    )
}
