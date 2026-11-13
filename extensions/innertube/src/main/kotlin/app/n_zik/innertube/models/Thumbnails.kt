package app.n_zik.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Thumbnails(
    val thumbnails: List<Thumbnail>,
)