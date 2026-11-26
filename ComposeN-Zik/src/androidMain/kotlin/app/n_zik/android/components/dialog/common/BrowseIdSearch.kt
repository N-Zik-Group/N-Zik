package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.typography
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class BrowseIdSearch<T>(
    initialQuery: String,
    private val scope: CoroutineScope,
    private val isOffline: () -> Boolean,
    private val fetch: suspend (String) -> Result<List<T>>?,
) {
    var query by mutableStateOf(initialQuery)
    private var submittedQuery by mutableStateOf<String?>(null)
    private var result by mutableStateOf<Result<List<T>>?>(null)
    private var request = 0

    private val matchesQuery get() = submittedQuery == query.trim()
    val isSearching get() = matchesQuery && result == null
    val results get() = result?.getOrNull()?.takeIf { matchesQuery }
    val failed get() = matchesQuery && result?.isFailure == true

    fun submit() {
        val term = query.trim()
        if (term.isEmpty()) return
        val currentRequest = ++request
        submittedQuery = term
        result = null
        scope.launch {
            val response = if (isOffline()) null else fetch(term)
            if (request == currentRequest) {
                result = response ?: Result.failure(IllegalStateException("Search was unavailable"))
            }
        }
    }

    @Composable
    fun Feedback() {
        SearchEmptyFeedback(
            query = submittedQuery.orEmpty(),
            empty = results?.isEmpty() == true,
            loading = isSearching,
        )
        if (failed) {
            BasicText(
                text = stringResource(R.string.common_load_failed),
                style = typography().xs.copy(color = colorPalette().text),
            )
        }
    }
}

@Composable
internal fun <T> rememberBrowseIdSearch(
    initialQuery: String,
    fetch: suspend (String) -> Result<List<T>>?,
): BrowseIdSearch<T> {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    return remember {
        BrowseIdSearch(initialQuery, scope, { OfflineMode.enabled(context) }) { query ->
            withContext(NzikDispatchers.DATA) { fetch(query) }
        }
    }
}
