package app.n_zik.android.components.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import kotlinx.coroutines.delay

/** Announces a completed empty search once typing has settled. */
@Composable
fun SearchEmptyFeedback(
    query: String,
    empty: Boolean,
    loading: Boolean = false,
    showMessage: (String) -> Unit = { Toaster.i(it) }
) {
    val message = stringResource(R.string.no_results_found)
    val currentMessage = rememberUpdatedState(message)
    val currentShowMessage = rememberUpdatedState(showMessage)
    LaunchedEffect(query, empty, loading) {
        if (query.isNotBlank() && empty && !loading) {
            delay(500)
            currentShowMessage.value(currentMessage.value)
        }
    }
}
