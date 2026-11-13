package app.n_zik.innertube.models

data class VideoOrSongInfo(
    val videoId: String,
    val title: String? = null,
    val author: String? = null,
    val authorId: String? = null,
    val authorThumbnail: String? = null,
    val description: String? = null,
    val uploadDate: String? = null,
    val subscribers: String? = null,
    val viewCount: String? = null,
    val like: String? = null,
    val dislike: String? = null,
)
