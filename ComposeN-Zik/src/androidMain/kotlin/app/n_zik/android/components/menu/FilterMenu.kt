package app.n_zik.android.components.menu

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.enums.FilterBy

@get:StringRes
internal val FilterBy.originLabelRes: Int
    get() = when (this) {
        FilterBy.All -> R.string.common_option_all
        FilterBy.YoutubeLibrary -> R.string.library_youtube_music_title
        FilterBy.Local -> R.string.library_filter_nzik_origin_label
    }

internal val libraryOriginFilters = listOf(FilterBy.All, FilterBy.YoutubeLibrary, FilterBy.Local)

internal val FilterBy.originIconRes: Int
    get() = when (this) {
        FilterBy.All -> R.drawable.musical_notes
        FilterBy.YoutubeLibrary -> R.drawable.ytmusic
        FilterBy.Local -> R.drawable.library
    }

@Composable
internal fun FilterMenu(
    selected: FilterBy,
    onSelected: (FilterBy) -> Unit,
) {
    SelectionMenu(
        title = stringResource(R.string.library_filter_by_title),
        choices = libraryOriginFilters,
        selectedChoice = selected,
        label = { filter -> stringResource(filter.originLabelRes) },
        icon = { filter -> filter.originIconRes },
        onSelected = onSelected,
    )
}
