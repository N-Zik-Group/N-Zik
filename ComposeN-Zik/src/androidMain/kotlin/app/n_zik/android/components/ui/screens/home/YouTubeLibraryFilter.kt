package app.n_zik.android.components.ui.screens.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.n_zik.android.R
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.core.database.PlaylistLinkKind
import app.n_zik.android.core.database.playlistLinkKind

internal const val YOUTUBE_LIBRARY_FILTER_KEY = "youtubeLibraryContentFilter"

internal enum class YouTubeLibraryFilter(val labelRes: Int, val iconRes: Int) {
    All(R.string.all, R.drawable.ytmusic),
    Playlists(R.string.playlists, R.drawable.playlist),
    Podcasts(R.string.podcasts, R.drawable.podcast),
}

/** The YouTube library groups saved content by source, independently of account sync flags. */
internal fun matchesYouTubeLibraryFilter(playlist: Playlist, filter: YouTubeLibraryFilter): Boolean =
    when (playlistLinkKind(playlist.browseId)) {
        PlaylistLinkKind.Local -> false
        PlaylistLinkKind.YouTubePlaylist -> filter != YouTubeLibraryFilter.Podcasts
        PlaylistLinkKind.YouTubePodcast -> filter != YouTubeLibraryFilter.Playlists
    }

@Composable
internal fun YouTubeLibraryFilterChip(
    selected: YouTubeLibraryFilter,
    onSelected: (YouTubeLibraryFilter) -> Unit,
) {
    val menuState = LocalMenuState.current
    GlassHomeFilterChip(
        selected = true,
        label = { Text(stringResource(selected.labelRes)) },
        onClick = {
            menuState.display {
                ListMenu.Menu(title = stringResource(R.string.filter_by)) {
                    YouTubeLibraryFilter.entries.forEach { filter ->
                        ListMenu.Entry(
                            text = stringResource(filter.labelRes),
                            icon = { HomeLibraryFilterIcon(filter.iconRes) },
                            onClick = {
                                menuState.hide()
                                onSelected(filter)
                            },
                        )
                    }
                }
            }
        },
    )
}
