package app.n_zik.android.components.settings.network

import app.n_zik.android.components.settings.common.settingsSearchTarget
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.it.fast4x.rimusic.utils.autoDownloadSongKey
import app.it.fast4x.rimusic.utils.autoDownloadSongWhenAlbumBookmarkedKey
import app.it.fast4x.rimusic.utils.autoDownloadSongWhenLikedKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun NetworkDownloadCard(query: String) {
    var autoDownloadSong by rememberPreference(autoDownloadSongKey, false)
    var autoDownloadSongWhenLiked by rememberPreference(autoDownloadSongWhenLikedKey, false)
    var autoDownloadSongWhenAlbumBookmarked by rememberPreference(autoDownloadSongWhenAlbumBookmarkedKey, false)

    SettingsSectionCard(
        title = stringResource(R.string.download),
        icon = R.drawable.download,
        content = {
            if (query.isBlank() || stringResource(R.string.settings_enable_autodownload_song).contains(query, true) || stringResource(R.string.auto_download_song_description).contains(query, true)) {
                OtherSwitchSettingEntry(
                    modifier = Modifier.settingsSearchTarget("network.download.settings_enable_autodownload_song"),
                    title = stringResource(R.string.settings_enable_autodownload_song),
                    text = stringResource(R.string.auto_download_song_description),
                    isChecked = autoDownloadSong,
                    onCheckedChange = {
                        autoDownloadSong = it
                    },
                    icon = R.drawable.download
                )
            }

            AnimatedVisibility(
                visible = autoDownloadSong,
                enter = fadeIn(animationSpec = tween(400)) + scaleIn(
                    animationSpec = tween(400),
                    initialScale = 0.9f
                ),
                exit = fadeOut(animationSpec = tween(200)) + scaleOut(
                    animationSpec = tween(200),
                    targetScale = 0.9f
                )
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    if (query.isBlank() || stringResource(R.string.settings_enable_autodownload_song_when_liked).contains(query, true) || stringResource(R.string.auto_download_when_liked_description).contains(query, true)) {
                        OtherSwitchSettingEntry(
                            modifier = Modifier.settingsSearchTarget("network.download.settings_enable_autodownload_song_when_liked"),
                            title = stringResource(R.string.settings_enable_autodownload_song_when_liked),
                            text = stringResource(R.string.auto_download_when_liked_description),
                            isChecked = autoDownloadSongWhenLiked,
                            onCheckedChange = {
                                autoDownloadSongWhenLiked = it
                            },
                            icon = R.drawable.heart
                        )
                    }

                    if (query.isBlank() || stringResource(R.string.settings_enable_autodownload_song_when_album_bookmarked).contains(query, true) || stringResource(R.string.auto_download_when_album_bookmarked_description).contains(query, true)) {
                        OtherSwitchSettingEntry(
                            modifier = Modifier.settingsSearchTarget("network.download.settings_enable_autodownload_song_when_album_bookmarked"),
                            title = stringResource(R.string.settings_enable_autodownload_song_when_album_bookmarked),
                            text = stringResource(R.string.auto_download_when_album_bookmarked_description),
                            isChecked = autoDownloadSongWhenAlbumBookmarked,
                            onCheckedChange = {
                                autoDownloadSongWhenAlbumBookmarked = it
                            },
                            icon = R.drawable.bookmark
                        )
                    }
                }
            }
        }
    )

}
