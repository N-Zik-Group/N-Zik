package app.n_zik.android.components.podcast

import app.n_zik.android.components.playlist.loadCompletePodcastPage
import app.n_zik.android.utils.coroutines.NzikDispatchers
import it.fast4x.innertube.Innertube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber

internal enum class PodcastPageStatus { Loading, Error, Empty, Content }

internal data class PodcastPageLoadState(
    val status: PodcastPageStatus = PodcastPageStatus.Loading,
    val page: Innertube.Podcast? = null,
)

/** Keeps failed requests retryable without turning coroutine cancellation into a UI error. */
internal class PodcastPageLoader(
    initialPage: Innertube.Podcast? = null,
    private val fetchPage: suspend (String) -> Innertube.Podcast = { id ->
        withContext(NzikDispatchers.DATA) { loadCompletePodcastPage(id) }
    },
) {
    private val mutableState = MutableStateFlow(initialPage?.loadedState() ?: PodcastPageLoadState())
    val state = mutableState.asStateFlow()

    suspend fun load(browseId: String) {
        currentCoroutineContext().ensureActive()
        mutableState.update { PodcastPageLoadState() }
        try {
            val page = fetchPage(browseId)
            currentCoroutineContext().ensureActive()
            mutableState.update { page.loadedState() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            Timber.tag("PodcastPage").e(error, "Unable to load complete podcast page")
            mutableState.update { PodcastPageLoadState(status = PodcastPageStatus.Error) }
        }
    }

    private fun Innertube.Podcast.loadedState() = PodcastPageLoadState(
        status = if (listEpisode.isEmpty()) PodcastPageStatus.Empty else PodcastPageStatus.Content,
        page = this,
    )
}
