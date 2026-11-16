package app.n_zik.innertube.requests

import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.BrowseResponse
import app.n_zik.innertube.utils.from

/** Maps the original track's clock to the official counterpart's clock. */
class MusicVideoPositionMap internal constructor(private val segments: List<MusicVideoSegment>) {
    fun mapPosition(positionMs: Long): Long {
        val position = positionMs.coerceAtLeast(0)
        for (segment in segments) {
            if (position < segment.sourceStartMs) return segment.targetStartMs
            val offset = position - segment.sourceStartMs
            if (offset < segment.durationMs) return segment.targetStartMs + offset
        }
        return segments.lastOrNull()?.let { it.targetStartMs + it.durationMs } ?: position
    }
}

internal data class MusicVideoSegment(
    val sourceStartMs: Long,
    val targetStartMs: Long,
    val durationMs: Long,
)

data class MusicVideoCounterpart(
    val targetVideoId: String,
    val targetMusicVideoType: String?,
    val positionMap: MusicVideoPositionMap?,
)

/** Reads the authoritative Audio/Video link supplied by YouTube Music's watch-next endpoint. */
suspend fun Innertube.musicVideoCounterpart(videoId: String, params: String? = null): MusicVideoCounterpart? {
    val response = next(videoId = videoId, params = params)
    if (!response.status.isSuccess()) return null
    return parseMusicVideoCounterpart(response.body<JsonObject>(), videoId)
}

/** Reads the direct album shelf, which can contain video releases missing from its playlist. */
suspend fun Innertube.albumVideoItems(albumId: String): List<Innertube.SongItem> {
    val response = browse(browseId = albumId, setLogin = true)
    if (!response.status.isSuccess()) return emptyList()
    val sections = response.body<BrowseResponse>().contents?.twoColumnBrowseResultsRenderer
        ?.secondaryContents?.sectionListRenderer?.contents.orEmpty()
    val shelf = sections.firstNotNullOfOrNull { it.musicShelfRenderer?.contents ?: it.musicPlaylistShelfRenderer?.contents }
    return shelf.orEmpty().mapNotNull { entry ->
        val renderer = entry.musicResponsiveListItemRenderer ?: return@mapNotNull null
        val song = Innertube.SongItem.from(renderer) ?: PlaylistPage.fromMusicResponsiveListItemRenderer(renderer)
        val info = song.info ?: return@mapNotNull null
        val overlayEndpoint = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content
            ?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint
        val endpoints = listOfNotNull(renderer.navigationEndpoint?.watchEndpoint, overlayEndpoint, info.endpoint)
            .filter { it.videoId != null }
        val watchEndpoint = endpoints.firstOrNull { it.type != null } ?: endpoints.firstOrNull()
        song.copy(info = info.copy(endpoint = watchEndpoint))
            .takeIf { it.key.isNotBlank() }
    }
}

// The wrapper and segment semantics follow Metrolist's MusicVideoCounterpart (GPL-3.0).
internal fun parseMusicVideoCounterpart(response: JsonObject, videoId: String): MusicVideoCounterpart? {
    for (wrapper in response.findObjects("playlistPanelVideoWrapperRenderer")) {
        val primary = wrapper.renderer("primaryRenderer") ?: continue
        val primaryId = primary.string("videoId") ?: continue
        for (entry in wrapper.array("counterpart")) {
            val counterpart = entry as? JsonObject ?: continue
            val counterpartRenderer = counterpart.renderer("counterpartRenderer") ?: continue
            val counterpartId = counterpartRenderer.string("videoId") ?: continue
            val forward = primaryId == videoId
            if (!forward && counterpartId != videoId) continue
            val targetId = if (forward) counterpartId else primaryId
            if (targetId == videoId) continue
            val target = if (forward) counterpart else primary
            val videoType = target.findString("musicVideoType")
            val segments = counterpart.objectValue("segmentMap")?.array("segment").orEmpty()
                .mapNotNull { element ->
                    val segment = element as? JsonObject ?: return@mapNotNull null
                    val primaryStart = segment.timestamp("primaryVideoStartTimeMilliseconds") ?: return@mapNotNull null
                    val counterpartStart = segment.timestamp("counterpartVideoStartTimeMilliseconds") ?: return@mapNotNull null
                    val duration = segment.timestamp("durationMilliseconds")?.takeIf { it > 0 } ?: return@mapNotNull null
                    if (forward) MusicVideoSegment(primaryStart, counterpartStart, duration)
                    else MusicVideoSegment(counterpartStart, primaryStart, duration)
                }.sortedBy(MusicVideoSegment::sourceStartMs)
            val positionMap = segments.takeIf { it.isNotEmpty() }?.let(::MusicVideoPositionMap)
            return MusicVideoCounterpart(targetId, videoType, positionMap)
        }
    }
    return null
}

private fun JsonObject.renderer(name: String): JsonObject? =
    objectValue(name)?.objectValue("playlistPanelVideoRenderer")

private fun JsonObject.objectValue(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): List<JsonElement> = (this[name] as? JsonArray).orEmpty()

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)

private fun JsonObject.timestamp(name: String): Long? =
    (this[name] as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 }

private fun JsonElement.findObjects(name: String): Sequence<JsonObject> = sequence {
    when (val element = this@findObjects) {
        is JsonObject -> {
            element.objectValue(name)?.let { yield(it) }
            element.values.forEach { yieldAll(it.findObjects(name)) }
        }
        is JsonArray -> element.forEach { yieldAll(it.findObjects(name)) }
        else -> Unit
    }
}

private fun JsonElement.findString(name: String): String? = when (this) {
    is JsonObject -> string(name) ?: values.firstNotNullOfOrNull { it.findString(name) }
    is JsonArray -> firstNotNullOfOrNull { it.findString(name) }
    else -> null
}
