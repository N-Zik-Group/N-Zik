package app.n_zik.android.components.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.models.Song
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** Observes song metadata even when the provider collection has never been saved locally. */
@Composable
internal fun rememberMenuSongs(fetched: List<Song>?): State<List<Song>?> {
    val ids = remember(fetched) { fetched.orEmpty().map(Song::id).distinct() }
    val saved by remember(ids) { menuSongRows(ids, Database.songTable::findByIds) }
        .collectAsStateWithLifecycle(emptyList(), context = NzikDispatchers.DATA)
    return rememberUpdatedState(overlayMenuSongs(fetched, saved))
}

/** Batches long provider queues below SQLite's bind limit without one subscription per song. */
internal fun menuSongRows(
    ids: List<String>,
    query: (List<String>) -> Flow<List<Song>>
): Flow<List<Song>> {
    val batches = ids.distinct().chunked(900).map(query)
    return when (batches.size) {
        0 -> flowOf(emptyList())
        1 -> batches.first()
        else -> combine(batches) { it.flatMap { rows -> rows } }
    }
}
