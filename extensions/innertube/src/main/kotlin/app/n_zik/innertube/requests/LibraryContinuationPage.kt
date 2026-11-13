package app.n_zik.innertube.requests

import app.n_zik.innertube.Innertube

data class LibraryContinuationPage(
    val items: List<Innertube.Item>,
    val continuation: String?,
)