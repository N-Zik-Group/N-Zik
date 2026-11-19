package app.n_zik.android.ui.screens.searchresult

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.core.database.artistEntryNames
import app.n_zik.android.typography
import app.n_zik.android.ui.components.themed.ItemTextPlaceholder
import app.n_zik.android.utils.conditional
import app.n_zik.innertube.Innertube

@Composable
internal fun SearchResultCardText(
    item: Innertube.Item?,
    tabIndex: Int,
    centered: Boolean,
    disableScrollingText: Boolean,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val titleStyle = typography().s.copy(textAlign = if (centered) TextAlign.Center else TextAlign.Start)
    val metadataStyle = typography().xs.copy(color = Color.Gray, textAlign = titleStyle.textAlign)
    val metadata: List<String?> = when (item) {
        is Innertube.AlbumItem -> buildList<String> {
            add(item.authors.artistEntryNames().joinToString(", "))
            item.year?.let(::add)
            item.description?.let(::add)
        }.filter(String::isNotBlank)
        is Innertube.ArtistItem -> listOfNotNull(item.description?.takeIf(String::isNotBlank))
        is Innertube.PlaylistItem -> buildList<String> {
            item.channel?.name?.takeIf(String::isNotBlank)?.let(::add)
            if (tabIndex != 5) item.description?.let(::add)
        }.filter(String::isNotBlank)
        null -> List(if (tabIndex == 1 || tabIndex == 4) 2 else 1) { null }
        else -> emptyList()
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchResultCardLine(
            text = item?.title ?: if (item == null) null else "",
            style = titleStyle,
            centered = centered,
            disableScrollingText = disableScrollingText,
            loading = loading,
            maxLines = if (centered) 2 else 1,
            placeholderWidth = 0.75f,
        )
        metadata.forEach { line ->
            SearchResultCardLine(
                text = line,
                style = metadataStyle,
                centered = centered,
                disableScrollingText = disableScrollingText,
                loading = loading,
                maxLines = 1,
                placeholderWidth = 0.58f,
            )
        }
    }
}

@Composable
private fun SearchResultCardLine(
    text: String?,
    style: TextStyle,
    centered: Boolean,
    disableScrollingText: Boolean,
    loading: Boolean,
    maxLines: Int,
    placeholderWidth: Float,
) {
    if (loading) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
        ) {
            ItemTextPlaceholder(
                text = text,
                style = style,
                width = placeholderWidth,
                maxLines = if (disableScrollingText) maxLines else 1,
            )
        }
    } else {
        BasicText(
            text = text.orEmpty(),
            style = style,
            maxLines = maxLines,
            overflow = if (centered) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) },
        )
    }
}
