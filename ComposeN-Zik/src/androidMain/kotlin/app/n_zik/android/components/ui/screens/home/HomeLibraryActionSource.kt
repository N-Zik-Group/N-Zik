package app.n_zik.android.components.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.models.Song

internal typealias HomeLibrarySongLoader = suspend (List<PlaylistPreview>) -> List<Song>

/** Retained toolbar callbacks read current targets and songs without rebuilding their buttons. */
internal class HomeLibraryActionSource(
    private val currentTargets: State<List<PlaylistPreview>>,
    private val currentSongs: State<List<Song>>,
    private val currentLoader: State<HomeLibrarySongLoader>,
) {
    val targets: List<PlaylistPreview> get() = currentTargets.value
    val cachedSongs: List<Song> get() = currentSongs.value
    suspend fun loadSongs(): List<Song> = currentLoader.value(targets)
}

@Composable
internal fun rememberHomeLibraryActionSource(
    targets: List<PlaylistPreview>,
    cachedSongs: List<Song>,
    loader: HomeLibrarySongLoader = { loadHomeLibraryActionSongs(it) },
): HomeLibraryActionSource {
    val currentTargets = rememberUpdatedState(targets)
    val currentSongs = rememberUpdatedState(cachedSongs)
    val currentLoader = rememberUpdatedState(loader)
    return remember { HomeLibraryActionSource(currentTargets, currentSongs, currentLoader) }
}
