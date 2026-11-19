package app.n_zik.android.components.menu.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.SearchEmptySpace
import app.n_zik.android.core.database.Database
import app.n_zik.android.enums.PlaylistSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.shimmerEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber

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
internal fun rememberPlaylistPickerRead(sortBy: PlaylistSortBy, sortOrder: SortOrder): PlaylistPickerRead {
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
            icon = {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.refresh), contentDescription = null, tint = colorPalette().accent,
                        modifier = Modifier.size(18.dp))
                }
            },
            onClick = read.retry,
        )
        else -> {
            SearchEmptyFeedback(query = query, empty = empty)
            if (query.isNotBlank() && empty) SearchEmptySpace(Modifier.height(160.dp))
        }
    }
}
