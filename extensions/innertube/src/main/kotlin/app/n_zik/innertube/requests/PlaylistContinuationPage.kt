package app.n_zik.innertube.requests

import app.n_zik.innertube.Innertube


data class PlaylistContinuationPage(
    val songs: List<Innertube.SongItem>,
    val continuation: String?,
)
