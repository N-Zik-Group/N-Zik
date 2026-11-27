package app.n_zik.android.components.podcast

import app.n_zik.android.components.playlist.PodcastContinuationFailure
import app.n_zik.android.components.playlist.loadCompletePodcastPage
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.innertube.Innertube
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
    val loadedEpisodeCount: Int = 0,
    val partialEpisodes: List<Innertube.Podcast.EpisodeItem> = emptyList(),
)

/** Keeps failed requests retryable without turning coroutine cancellation into a UI error. */
internal class PodcastPageLoader(
    initialPage: Innertube.Podcast? = null,
    private val fetchPage: (suspend (String) -> Innertube.Podcast)? = null,
) {
    private val mutableState = MutableStateFlow(initialPage?.loadedState() ?: PodcastPageLoadState())
    val state = mutableState.asStateFlow()
    private val requestLock = Any()
    private var currentRequest: Any? = null

    suspend fun load(browseId: String) {
        currentCoroutineContext().ensureActive()
        val request = Any()
        synchronized(requestLock) {
            currentRequest = request
            mutableState.update { PodcastPageLoadState() }
        }
        try {
            val page = if (fetchPage != null) {
                fetchPage(browseId)
            } else {
                withContext(NzikDispatchers.DATA) {
                    loadCompletePodcastPage(browseId, onEpisodeCount = { count ->
                        publish(request) { it.copy(loadedEpisodeCount = count) }
                    })
                }
            }
            currentCoroutineContext().ensureActive()
            publish(request) { page.loadedState() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            Timber.tag("PodcastPage").e(error, "Unable to load complete podcast page")
            publish(request) {
                PodcastPageLoadState(
                    status = PodcastPageStatus.Error,
                    partialEpisodes = (error as? PodcastContinuationFailure)?.availableEpisodes.orEmpty(),
                )
            }
        }
    }

    private fun publish(request: Any, next: (PodcastPageLoadState) -> PodcastPageLoadState) {
        synchronized(requestLock) {
            if (currentRequest === request) mutableState.update(next)
        }
    }

    private fun Innertube.Podcast.loadedState() = PodcastPageLoadState(
        status = if (listEpisode.isEmpty()) PodcastPageStatus.Empty else PodcastPageStatus.Content,
        page = this,
    )
}
