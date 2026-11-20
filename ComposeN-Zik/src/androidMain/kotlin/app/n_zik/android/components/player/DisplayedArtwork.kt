package app.n_zik.android.components.player

import android.graphics.Bitmap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

internal data class ArtworkSource(
    val profile: String,
    val uri: String,
    val localRevision: Long,
    val remoteRevision: Long,
)

internal data class DisplayedArtwork(val bitmap: Bitmap, val fileRevision: String)

internal sealed interface DisplayedArtworkResult {
    data class Ready(val artwork: DisplayedArtwork) : DisplayedArtworkResult
    data object Unavailable : DisplayedArtworkResult
}

/** Only elected player covers are registered; the painter continues to own the image. */
internal class DisplayedArtworkRegistry(private val waitMillis: Long = 180L) {
    private class Scene(val profile: String)
    private class Entry(val source: ArtworkSource, val result: Flow<DisplayedArtworkResult>)
    private data class State(val scenes: List<Scene> = emptyList(), val entries: List<Entry> = emptyList())
    private data class Selection(val visible: Boolean, val entry: Entry?)

    private val state = MutableStateFlow(State())

    fun showPlayer(profile: String): () -> Unit {
        val scene = Scene(profile)
        state.update { it.copy(scenes = it.scenes + scene) }
        return { state.update { it.copy(scenes = it.scenes - scene) } }
    }

    fun register(source: ArtworkSource, result: Flow<DisplayedArtworkResult>): () -> Unit {
        val entry = Entry(source, result)
        state.update { it.copy(entries = it.entries + entry) }
        return { state.update { it.copy(entries = it.entries - entry) } }
    }

    fun invalidateSources(profile: String, matches: (String) -> Boolean) {
        state.update { current ->
            current.copy(entries = current.entries.filterNot { it.source.profile == profile && matches(it.source.uri) })
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun await(source: ArtworkSource, fileRevision: String): Bitmap? {
        fun State.selection() = Selection(
            scenes.any { it.profile == source.profile },
            entries.lastOrNull { it.source == source },
        )
        return withTimeoutOrNull(waitMillis) {
            state.map { it.selection() }.distinctUntilChanged().flatMapLatest { selected ->
                val entry = selected.entry
                when {
                    !selected.visible -> flowOf(ArtworkResult(selected, null))
                    entry == null -> emptyFlow()
                    else -> entry.result.map { result ->
                        val artwork = (result as? DisplayedArtworkResult.Ready)?.artwork
                        ArtworkResult(selected, artwork?.takeIf { it.fileRevision == fileRevision }?.bitmap)
                    }
                }
            }.filter { it.selection == state.value.selection() }.first().bitmap
        }
    }

    private data class ArtworkResult(val selection: Selection, val bitmap: Bitmap?)
}
