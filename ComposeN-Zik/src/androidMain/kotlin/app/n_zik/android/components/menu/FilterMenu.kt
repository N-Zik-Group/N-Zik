package app.n_zik.android.components.menu

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.enums.FilterBy

@Composable
internal fun FilterMenu(
    selected: FilterBy,
    onSelected: (FilterBy) -> Unit,
) {
    SelectionMenu(
        title = stringResource(R.string.library_filter_by_title),
        choices = listOf(FilterBy.All, FilterBy.YoutubeLibrary, FilterBy.Local),
        selectedChoice = selected,
        label = { filter ->
            stringResource(when (filter) {
                FilterBy.All -> R.string.common_option_all
                FilterBy.YoutubeLibrary -> R.string.library_youtube_music_title
                FilterBy.Local -> R.string.library_on_device_title
            })
        },
        icon = { filter ->
            when (filter) {
                FilterBy.All -> R.drawable.musical_notes
                FilterBy.YoutubeLibrary -> R.drawable.ytmusic
                FilterBy.Local -> R.drawable.folder
            }
        },
        onSelected = onSelected,
    )
}
