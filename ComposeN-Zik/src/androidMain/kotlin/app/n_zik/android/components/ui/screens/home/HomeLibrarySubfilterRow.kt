package app.n_zik.android.components.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.n_zik.android.R
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.core.rewind.RewindPlaylists

internal enum class HomeLibrarySubfilter { YouTube, Rewind }

/** Both library submenus share the YouTube row's layout and animation lifetime. */
@Composable
internal fun HomeLibrarySubfilterRow(
    kind: HomeLibrarySubfilter?,
    modifier: Modifier = Modifier,
    content: @Composable (HomeLibrarySubfilter) -> Unit,
) {
    var lastKind by remember { mutableStateOf(HomeLibrarySubfilter.YouTube) }
    val displayedKind = kind ?: lastKind
    SideEffect { if (kind != null) lastKind = kind }
    AnimatedVisibility(
        visible = kind != null,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(200)) + expandVertically(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200)),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp).fillMaxWidth(),
        ) {
            content(displayedKind)
        }
    }
}

@Composable
internal fun RewindLibraryFilterChip(
    selected: RewindPlaylists.Filter,
    onSelected: (RewindPlaylists.Filter) -> Unit,
) {
    val menuState = LocalMenuState.current
    val labels = mapOf(
        RewindPlaylists.Filter.Month to stringResource(R.string.rewind_filter_month),
        RewindPlaylists.Filter.Year to stringResource(R.string.rewind_filter_year),
        RewindPlaylists.Filter.All to stringResource(R.string.all),
    )
    val icons = mapOf(RewindPlaylists.Filter.Month to R.drawable.stat_month,
        RewindPlaylists.Filter.Year to R.drawable.stat_year, RewindPlaylists.Filter.All to R.drawable.musical_notes)
    GlassHomeFilterChip(
        selected = true,
        label = { Text(labels.getValue(selected)) },
        onClick = {
            menuState.display {
                ListMenu.Menu(title = stringResource(R.string.filter_by)) {
                    RewindPlaylists.Filter.entries.forEach { filter ->
                        ListMenu.Entry(
                            text = labels.getValue(filter),
                            icon = { HomeLibraryFilterIcon(icons.getValue(filter)) },
                            onClick = { menuState.hide(); onSelected(filter) },
                        )
                    }
                }
            }
        },
    )
}
