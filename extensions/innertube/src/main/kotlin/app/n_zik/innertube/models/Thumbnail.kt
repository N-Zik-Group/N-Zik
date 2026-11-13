package app.n_zik.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Thumbnail(
    val url: String,
    val height: Int?,
    val width: Int?
)

