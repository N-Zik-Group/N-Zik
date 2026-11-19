package app.n_zik.android.ui.items

import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.ui.unit.Dp
import app.n_zik.android.cleanPrefix
import app.n_zik.innertube.Innertube

/** Unknown pages reserve a viewport. Once loaded, use the real cards and their metadata. */
internal fun LazyGridScope.albumGridPlaceholders(albums: List<Innertube.AlbumItem>?, thumbnailSize: Dp) {
    items(
        count = albums?.size ?: 8,
        key = { index -> albums?.getOrNull(index)?.key ?: "album-loading-$index" },
        contentType = { "album-placeholder" },
    ) { index ->
        val album = albums?.getOrNull(index)
        AlbumItemPlaceholder(
            thumbnailSize,
            alternative = true,
            title = album?.title?.let(::cleanPrefix),
            metadataLines = if (album != null && album.year.isNullOrBlank()) 0 else 1,
            metadata = album?.year?.let(::listOf),
        )
    }
}
