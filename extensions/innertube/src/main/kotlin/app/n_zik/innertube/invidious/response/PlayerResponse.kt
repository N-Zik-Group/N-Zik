package app.n_zik.innertube.invidious.response

import kotlinx.serialization.Serializable
import app.n_zik.innertube.common.response.AudioFormat
import app.n_zik.innertube.common.response.MediaFormatContainer
import java.util.SortedSet

@Serializable
data class PlayerResponse(
    private val adaptiveFormats: List<AdaptiveFormat>
): MediaFormatContainer<PlayerResponse.AdaptiveFormat> {

    override val formats: SortedSet<out AdaptiveFormat> =
        sortedSetOf<AdaptiveFormat>().apply {
            // Should filter format starts with "audio" as in "audio/webm"
            addAll( adaptiveFormats.filter { it.mimeType.startsWith("audio") } )
        }

    @Serializable
    data class AdaptiveFormat(
        val type: String,
        override val itag: UShort,
        override val url: String,
        override val bitrate: UInt
    ): AudioFormat {

        override val mimeType: String
            get() = type.split( ";" )[0].trim()
        override val codec: String
            get() = type.split( ";" )[1].trim()
    }
}