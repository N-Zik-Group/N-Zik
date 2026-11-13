package app.n_zik.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Badges(
    val musicInlineBadgeRenderer: MusicInlineBadgeRenderer?,
) {
    @Serializable
    data class MusicInlineBadgeRenderer(
        val icon: Icon,
    ) {
        @Serializable
        data class Icon(
            val iconType: String,
        )
    }

}
