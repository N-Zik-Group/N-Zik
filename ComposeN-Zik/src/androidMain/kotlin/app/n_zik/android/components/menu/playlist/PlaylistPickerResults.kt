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
import app.n_zik.android.enums.PlaylistSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.Preference
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.shimmerEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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

internal fun <T> playlistPickerResults(source: Flow<T>): Flow<Result<T>> =
    source.map { Result.success(it) }.catch { error ->
        if (error is CancellationException) throw error
        Timber.tag("PlaylistPicker").w(error, "Could not read playlist previews")
        emit(Result.failure(error))
    }

internal data class PlaylistPickerPreviews(
    val library: List<PlaylistPreview>,
    val pinnedRank: Map<Long, Int> = emptyMap(),
    val youtubeRank: Map<Long, Int> = emptyMap(),
    val deviceRank: Map<Long, Int> = emptyMap(),
)

internal class PlaylistPickerRead(
    previewResult: Result<PlaylistPickerPreviews>?,
    val devicePlaylistIds: Set<Long>,
    deviceScanPending: Boolean = false,
    val retry: () -> Unit,
) {
    val result = previewResult.takeUnless { deviceScanPending && it?.isSuccess == true }
    val libraryPreviews: List<PlaylistPreview> get() = result?.getOrNull()?.library.orEmpty()
}

@Composable
internal fun rememberPlaylistPickerRead(includeDeviceSort: Boolean, deviceScanPending: Boolean): PlaylistPickerRead {
    val libraryBy by Preference.remember(Preference.HOME_LIBRARY_PLAYLIST_SORT_BY)
    val libraryOrder by Preference.remember(Preference.HOME_LIBRARY_PLAYLIST_SORT_ORDER)
    val pinnedBy by Preference.remember(Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_BY)
    val pinnedOrder by Preference.remember(Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_ORDER)
    val youtubeBy by Preference.remember(Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_BY)
    val youtubeOrder by Preference.remember(Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_ORDER)
    val deviceBy by Preference.remember(Preference.HOME_ON_DEVICE_PLAYLISTS_SORT_BY)
    val deviceOrder by Preference.remember(Preference.HOME_ON_DEVICE_PLAYLISTS_SORT_ORDER)
    val devicePlaylistIds by remember {
        Database.songPlaylistMapTable.onDeviceOnlyPlaylistIds().map { it.toSet() }
    }.collectAsStateWithLifecycle(emptySet(), context = NzikDispatchers.DATA)
    val librarySort = libraryBy to libraryOrder
    val pinnedSort = pinnedBy to pinnedOrder
    val youtubeSort = youtubeBy to youtubeOrder
    val deviceSort = (deviceBy to deviceOrder).takeIf { includeDeviceSort }
    var retry by remember { mutableIntStateOf(0) }
    val results = remember(librarySort, pinnedSort, youtubeSort, deviceSort, retry) {
        playlistPickerResults(sortedPlaylistPickerPreviews(librarySort, pinnedSort, youtubeSort, deviceSort))
    }
    return key(retry) {
        val result by results.collectAsStateWithLifecycle<Result<PlaylistPickerPreviews>?>(
            initialValue = null,
            context = NzikDispatchers.DATA,
        )
        PlaylistPickerRead(result, devicePlaylistIds, deviceScanPending) { retry++ }
    }
}

private fun sortedPlaylistPickerPreviews(
    librarySort: Pair<PlaylistSortBy, SortOrder>,
    pinnedSort: Pair<PlaylistSortBy, SortOrder>,
    youtubeSort: Pair<PlaylistSortBy, SortOrder>,
    deviceSort: Pair<PlaylistSortBy, SortOrder>?,
): Flow<PlaylistPickerPreviews> {
    val sorts = (listOf(librarySort, pinnedSort, youtubeSort) + listOfNotNull(deviceSort)).distinct()
    val flows = sorts.map { (sortBy, sortOrder) -> Database.playlistTable.sortPreviews(sortBy, sortOrder) }
    if (flows.size == 1) return flows.single().map { PlaylistPickerPreviews(it) }
    return combine(flows) { previews ->
        val library = previews[sorts.indexOf(librarySort)]
        // Other Room views provide ordering only; membership comes from the same library snapshot.
        fun rank(sort: Pair<PlaylistSortBy, SortOrder>): Map<Long, Int> {
            val index = sorts.indexOf(sort)
            if (sort == librarySort || index < 0) return emptyMap()
            return previews[index].mapIndexed { rank, preview -> preview.playlist.id to rank }.toMap()
        }
        PlaylistPickerPreviews(library, rank(pinnedSort), rank(youtubeSort), deviceSort?.let(::rank).orEmpty())
    }
}

internal fun playlistPickerEmptyBody(
    read: PlaylistPickerRead,
    query: String,
    empty: Boolean,
): (@Composable () -> Unit)? =
    if (read.result?.isSuccess == true && empty && query.isBlank()) {
        { SearchNoResults(text = stringResource(R.string.common_empty_items)) }
    } else null

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
    if (!empty || query.isBlank()) return
    SearchEmptyFeedback(query = query, empty = true)
    SearchEmptySpace(Modifier.height(160.dp))
}
