package app.n_zik.android.components.search

import app.n_zik.android.cleanPrefix
import app.n_zik.android.core.database.artistEntryNames
import app.n_zik.android.models.Song
import app.n_zik.innertube.Innertube

internal fun Song.matchesCollectionSearch(query: String): Boolean {
    val text = query.trim()
    return text.isEmpty() || cleanTitle().contains(text, ignoreCase = true) ||
        cleanArtistsText().contains(text, ignoreCase = true)
}

internal fun Innertube.Item.matchesCollectionSearch(query: String): Boolean {
    val text = query.trim()
    if (text.isEmpty() || cleanPrefix(title.orEmpty()).contains(text, ignoreCase = true)) return true
    val metadata = when (this) {
        is Innertube.AlbumItem -> listOf(cleanPrefix(authors.artistEntryNames().joinToString(", ")), year)
        is Innertube.VideoItem -> listOf(authors.artistEntryNames().joinToString(", "), viewsText, durationText)
        is Innertube.PlaylistItem -> listOf(channel?.name)
        is Innertube.ArtistItem -> listOf(subscribersCountText)
        is Innertube.SongItem -> listOf(authors.artistEntryNames().joinToString(", "))
    }
    return metadata.any { it?.contains(text, ignoreCase = true) == true }
}
