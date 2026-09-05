package it.fast4x.innertube.requests

import io.ktor.client.call.body
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.GetQueueResponse
import it.fast4x.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.queue(videoIds: List<String>, playlistId: String? = null) = runCatchingNonCancellable {
    val response = getQueue(videoIds = videoIds, playlistId = playlistId).body<GetQueueResponse>()

    response
        .queueDatas
        ?.mapNotNull { queueData ->
            queueData
                .content
                ?.playlistPanelVideoRenderer
                ?.let(Innertube.SongItem::parse)
        }
}

suspend fun Innertube.song(videoId: String): Result<Innertube.SongItem?>? =
    queue(videoIds = listOf(videoId))?.map { it?.firstOrNull() }
