package app.n_zik.lrclib.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.abs
import kotlin.time.Duration

@Serializable
data class Track(
    val id: Long,
    val name: String,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    @SerialName("duration") val tDuration: JsonElement,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?
){
    /** Duration in seconds, or 0 when LrcLib sends `null` (it does for some tracks) or an unreadable value. */
    val duration: Long
        get() = when (val d = tDuration) {
            is JsonPrimitive -> d.contentOrNull
            is JsonArray -> (d.firstOrNull() as? JsonPrimitive)?.contentOrNull
            else -> null
        }?.substringBefore(".")?.toLongOrNull() ?: 0L
}


internal fun List<Track>.bestMatchingFor(title: String, duration: Duration) =
    firstOrNull { it.duration == duration.inWholeSeconds }
        ?: minByOrNull { abs(it.trackName.length - title.length) }
