package app.n_zik.android.components.dialog.song

import io.ktor.client.call.body
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.PlayerResponse
import kotlinx.coroutines.CancellationException
import timber.log.Timber

private val VIDEO_AUTHOR_TYPES = setOf("MUSIC_VIDEO_TYPE_UGC", "MUSIC_VIDEO_TYPE_ATV", "MUSIC_VIDEO_TYPE_OMV")
private val VIDEO_AUTHOR_CHANNEL_ID = Regex("UC[A-Za-z0-9_-]{22}")

/** Fills missing video authors from fresh player metadata without replacing music credits. */
internal suspend fun withVideoAuthorFallback(
    videoId: String,
    fetchedItem: Innertube.SongItem?,
    authorsSelected: Boolean,
    onFailure: (SongUpdateIssue) -> Unit = {},
    fetchDetails: suspend (String) -> PlayerResponse.VideoDetails? = { id ->
        Innertube.player(videoId = id).body<PlayerResponse>().videoDetails
    },
): Innertube.SongItem? {
    if (!authorsSelected || fetchedItem == null || fetchedItem.key != videoId) return fetchedItem
    if (fetchedItem.authors.orEmpty().any { !it.name.isNullOrBlank() }) return fetchedItem
    val queueType = fetchedItem.info?.endpoint?.type
    if (queueType != null && queueType !in VIDEO_AUTHOR_TYPES) return fetchedItem

    val details = try {
        fetchDetails(videoId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        val issue = classifySongUpdateFetchFailure(error)
        Timber.tag("SongUpdate").w(error, "Video author fallback failed")
        onFailure(issue)
        null
    } ?: return fetchedItem
    if (details.videoId != videoId) return fetchedItem
    val playerType = details.musicVideoType
    if (playerType != null && playerType !in VIDEO_AUTHOR_TYPES) return fetchedItem
    if (queueType == null && playerType == null) return fetchedItem
    if (queueType != null && playerType != null && queueType != playerType) return fetchedItem
    val author = details.author?.trim()?.takeIf { it.isNotBlank() } ?: return fetchedItem
    val channelId = details.channelId?.takeIf(VIDEO_AUTHOR_CHANNEL_ID::matches) ?: return fetchedItem

    return fetchedItem.copy(
        authors = listOf(
            Innertube.Info(
                name = author,
                endpoint = NavigationEndpoint.Endpoint.Browse(browseId = channelId),
            ),
        ),
    )
}
