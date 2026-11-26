package app.n_zik.android.components.menu.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.SearchEmptySpace
import app.n_zik.android.components.search.SearchNoResults
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.PlaylistLinkKind
import app.n_zik.android.core.database.playlistLinkKind
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.Preference
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.shimmerEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber

internal fun Playlist.matchesPickerQuery(query: String): Boolean =
    cleanName().contains(query, ignoreCase = true)

@Composable
internal fun PlaylistPreview.menuItemCount(): String {
    val countResource = if (playlistLinkKind(playlist.browseId) == PlaylistLinkKind.YouTubePodcast) {
        R.plurals.sync_detail_episodes
    } else {
        R.plurals.library_song_count
    }
    return pluralStringResource(countResource, songCount, songCount)
}

internal fun playlistPickerResults(source: Flow<List<PlaylistPreview>>): Flow<Result<List<PlaylistPreview>>> =
    source.map { Result.success(it) }.catch { error ->
        if (error is CancellationException) throw error
        Timber.tag("PlaylistPicker").w(error, "Could not read playlist previews")
        emit(Result.failure(error))
    }

internal class PlaylistPickerRead(
    val result: Result<List<PlaylistPreview>>?,
    val retry: () -> Unit,
) {
    val previews: List<PlaylistPreview> get() = result?.getOrNull().orEmpty()
}

@Composable
internal fun rememberPlaylistPickerRead(): PlaylistPickerRead {
    val sortBy by Preference.remember(Preference.HOME_LIBRARY_PLAYLIST_SORT_BY)
    val sortOrder by Preference.remember(Preference.HOME_LIBRARY_PLAYLIST_SORT_ORDER)
    var retry by remember { mutableIntStateOf(0) }
    val results = remember(sortBy, sortOrder, retry) {
        playlistPickerResults(Database.playlistTable.sortPreviews(sortBy, sortOrder))
    }
    return key(retry) {
        val result by results.collectAsStateWithLifecycle<Result<List<PlaylistPreview>>?>(
            initialValue = null,
            context = NzikDispatchers.DATA,
        )
        PlaylistPickerRead(result) { retry++ }
    }
}

@Composable
internal fun PlaylistPickerFeedback(read: PlaylistPickerRead, query: String, empty: Boolean) {
    val result = read.result
    when {
        result == null -> repeat(3) {
            ListMenu.Entry(
                text = "",
                subtitle = "",
                loading = true,
                icon = { Spacer(Modifier.size(32.dp).clip(uiRoundnessShape()).shimmerEffect()) },
                trailingContent = { Spacer(Modifier.size(24.dp).clip(uiRoundnessShape()).shimmerEffect()) },
            )
        }
        result.isFailure -> ListMenu.Entry(
            text = stringResource(R.string.common_load_failed),
            subtitle = stringResource(R.string.common_action_retry),
            icon = { PlaylistPickerRetryIcon() },
            onClick = read.retry,
        )
        else -> {
            PlaylistPickerEmptyFeedback(query, empty)
        }
    }
}

internal fun LazyGridScope.playlistPickerGridFeedback(read: PlaylistPickerRead, query: String, empty: Boolean) {
    val result = read.result
    when {
        result == null -> items(3) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.size(32.dp).clip(uiRoundnessShape()).shimmerEffect())
                Spacer(Modifier.height(4.dp))
                Spacer(Modifier.fillMaxWidth(.65f).height(12.dp).clip(uiRoundnessShape()).shimmerEffect())
                Spacer(Modifier.height(2.dp))
                Spacer(Modifier.fillMaxWidth(.4f).height(10.dp).clip(uiRoundnessShape()).shimmerEffect())
            }
        }
        result.isFailure -> item {
            GridMenu.Entry(
                text = stringResource(R.string.common_load_failed),
                subtitle = stringResource(R.string.common_action_retry),
                icon = { PlaylistPickerRetryIcon() },
                onClick = read.retry,
            )
        }
        empty -> item(span = { GridItemSpan(maxLineSpan) }) {
            Column { PlaylistPickerEmptyFeedback(query, empty) }
        }
    }
}

@Composable
private fun PlaylistPickerRetryIcon() {
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        Icon(
            painterResource(R.drawable.refresh),
            contentDescription = null,
            tint = colorPalette().accent,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PlaylistPickerEmptyFeedback(query: String, empty: Boolean) {
    if (!empty) return
    if (query.isBlank()) {
        SearchNoResults(
            text = stringResource(R.string.common_empty_items),
            modifier = Modifier.height(72.dp),
        )
    } else {
        SearchEmptyFeedback(query = query, empty = true)
        SearchEmptySpace(Modifier.height(160.dp))
    }
}
