package app.n_zik.android.components.settings.accounts

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.syncImportEpisodesKey
import app.it.fast4x.rimusic.utils.syncImportHistoryKey
import app.it.fast4x.rimusic.utils.syncImportLibrarySongsKey
import app.it.fast4x.rimusic.utils.syncImportUploadedAlbumsKey
import app.it.fast4x.rimusic.utils.syncImportUploadedSongsKey
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard

/** Owns the SyncImportCard controls. */
@Composable
internal fun AccountsSyncImportCard() {
    SettingsSectionCard(
        title = stringResource(R.string.sync_import_from_youtube),
        icon = R.drawable.download,
        content = {
            var syncImportHistory by rememberPreference(syncImportHistoryKey, false)
            var syncImportLibrarySongs by rememberPreference(syncImportLibrarySongsKey, false)
            var syncImportUploadedSongs by rememberPreference(syncImportUploadedSongsKey, false)
            var syncImportUploadedAlbums by rememberPreference(syncImportUploadedAlbumsKey, false)
            var syncImportEpisodes by rememberPreference(syncImportEpisodesKey, false)

            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("accounts.sync_import_from_youtube.sync_import_history"),
                title = stringResource(R.string.sync_import_history),
                text = stringResource(R.string.sync_import_history_description),
                isChecked = syncImportHistory,
                onCheckedChange = { syncImportHistory = it },
                icon = R.drawable.history
            )
            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("accounts.sync_import_from_youtube.sync_import_library_songs"),
                title = stringResource(R.string.sync_import_library_songs),
                text = stringResource(R.string.sync_import_library_songs_description),
                isChecked = syncImportLibrarySongs,
                onCheckedChange = { syncImportLibrarySongs = it },
                icon = R.drawable.heart
            )
            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("accounts.sync_import_from_youtube.sync_import_uploaded_songs"),
                title = stringResource(R.string.sync_import_uploaded_songs),
                text = stringResource(R.string.sync_import_uploaded_songs_description),
                isChecked = syncImportUploadedSongs,
                onCheckedChange = { syncImportUploadedSongs = it },
                icon = R.drawable.download
            )
            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("accounts.sync_import_from_youtube.sync_import_uploaded_albums"),
                title = stringResource(R.string.sync_import_uploaded_albums),
                text = stringResource(R.string.sync_import_uploaded_albums_description),
                isChecked = syncImportUploadedAlbums,
                onCheckedChange = { syncImportUploadedAlbums = it },
                icon = R.drawable.album
            )
            OtherSwitchSettingEntry(
                modifier = Modifier.settingsSearchTarget("accounts.sync_import_from_youtube.sync_import_episodes"),
                title = stringResource(R.string.sync_import_episodes),
                text = stringResource(R.string.sync_import_episodes_description),
                isChecked = syncImportEpisodes,
                onCheckedChange = { syncImportEpisodes = it },
                icon = R.drawable.podcast
            )
        }
    )
}
