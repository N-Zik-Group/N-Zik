package app.n_zik.android.components.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import app.n_zik.android.R
import app.n_zik.android.core.database.HomeParentSong
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import timber.log.Timber

internal suspend fun <Id> loadHomeParentSongs(
    ids: List<Id>,
    load: suspend (List<Id>) -> List<HomeParentSong>,
    include: (Id, Song) -> Boolean = { _, _ -> true },
): List<Song> = loadHomeParentItems(ids, load, include) { _, song -> song }

internal suspend fun <Id, T> loadHomeParentItems(
    ids: List<Id>,
    load: suspend (List<Id>) -> List<HomeParentSong>,
    include: (Id, Song) -> Boolean,
    map: (Id, Song) -> T,
): List<T> = withContext(NzikDispatchers.DATA) {
    val seen = HashSet<String>()
    buildList {
        for (chunk in ids.chunked(500)) {
            val byParent = load(chunk).groupBy(HomeParentSong::parentId)
            for (id in chunk) {
                for (row in byParent[id.toString()].orEmpty()) {
                    if (include(id, row.song) && seen.add(row.song.id)) add(map(id, row.song))
                }
            }
        }
    }
}

internal suspend fun loadHomeLibraryGroupSongs(
    ids: List<String>,
    load: suspend (List<String>) -> List<HomeParentSong>,
    isDeviceGroup: (String) -> Boolean,
    loadDeviceGroups: suspend () -> Map<String, Set<String>>,
): List<Song> {
    val deviceGroups = if (ids.any(isDeviceGroup)) loadDeviceGroups() else emptyMap()
    return loadHomeParentSongs(ids, load) { id, song ->
        if (isDeviceGroup(id)) song.isLocal && song.id in deviceGroups[id].orEmpty()
        else !song.isLocal
    }
}

internal class HomeSongAction<T>(
    private val tag: String,
    private val capture: () -> (suspend () -> List<T>)?,
) {
    fun snapshot(): (suspend () -> List<T>?)? {
        val load = capture() ?: return null
        return {
            try {
                load()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.tag(tag).e(error, "Failed to load songs for Home action")
                Toaster.e(R.string.common_load_failed)
                null
            }
        }
    }
}

@Composable
internal fun <T> rememberHomeSongAction(
    tag: String,
    capture: () -> (suspend () -> List<T>)?,
): HomeSongAction<T> {
    val latest = rememberUpdatedState(capture)
    return remember(tag) { HomeSongAction(tag) { latest.value() } }
}
