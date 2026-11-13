package app.n_zik.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistPanelVideoRenderer(
    val title: Runs?,
    val longBylineText: Runs?,
    val shortBylineText: Runs?,
    val lengthText: Runs?,
    val badges: List<Badges> = emptyList(),
    val navigationEndpoint: NavigationEndpoint?,
    val thumbnail: ThumbnailRenderer.MusicThumbnailRenderer.Thumbnail?,
)
