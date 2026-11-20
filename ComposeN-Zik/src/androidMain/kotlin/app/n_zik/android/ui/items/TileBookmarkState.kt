package app.n_zik.android.ui.items

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import app.n_zik.android.utils.coroutines.NzikDispatchers

@Composable
internal fun tileBookmarkState(
    id: String,
    bookmarkState: Boolean?,
    observeBookmarkState: Boolean,
    observe: (String) -> Flow<Boolean?>,
): Boolean? {
    // A batch result can be neutral even when it owns this tile's state.
    if (!observeBookmarkState) return bookmarkState

    val state = remember(id) { observe(id).distinctUntilChanged() }
    return state.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA).value
}
