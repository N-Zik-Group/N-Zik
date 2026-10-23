package app.n_zik.android.bridge.library

import app.it.fast4x.rimusic.enums.BuiltInPlaylist
import app.it.fast4x.rimusic.models.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One song the phone's smart shuffle inserted in its Songs tab, at [position] of the tab's list. */
internal data class RecommendedSong(val position: Int, val song: Song)

/** What the phone's Songs tab shows of its smart shuffle (contract §10.1, since 1.12.0): the tab's [filter] (`null` when the tab has no wire twin) and its recommended songs, in insertion order. */
internal data class SongRecommendationsState(
    val filter: SongFilter?,
    val items: List<RecommendedSong>,
) {
    companion object {
        /** Smart shuffle off, or the Songs tab not shown: nothing recommended. */
        val EMPTY = SongRecommendationsState(filter = null, items = emptyList())
    }
}

/**
 * The phone's Songs-tab smart shuffle, shared with the PC bridge (contract §10.1
 * `GET /library/songs/recommendations`, feature `library.recommendations`, since 1.12.0).
 *
 * The phone's `HomeSongs` publishes its own recommendations here once it has computed their positions,
 * and clears them when the smart shuffle is switched off or the screen leaves; the algorithm stays the
 * phone's own. Every change of [state] calls [changeListener] — the bridge's `libraryChanged` `songs`.
 */
internal object SongRecommendations {

    private val _state = MutableStateFlow(SongRecommendationsState.EMPTY)
    val state: StateFlow<SongRecommendationsState> = _state.asStateFlow()

    /** Called after every change of [state]; `null` while no bridge listens. */
    @Volatile
    var changeListener: (() -> Unit)? = null

    /** The phone's [tab] shows [positions] (each recommended song at its position in the tab's list). */
    fun publish(tab: BuiltInPlaylist, positions: Map<Song, Int>) {
        set(
            SongRecommendationsState(
                filter = filterOf(tab),
                items = positions.map { (song, position) -> RecommendedSong(position, song) },
            ),
        )
    }

    /** The smart shuffle is off, or the Songs screen left: nothing recommended any more. */
    fun clear() = set(SongRecommendationsState.EMPTY)

    private fun set(value: SongRecommendationsState) {
        val previous = _state.value
        _state.value = value
        if (previous != value) changeListener?.invoke()
    }

    /** The wire `filter` of a phone tab (contract §10.1 `/library/songs`); `null` for the phone's "On device" tab, which the bridge does not expose as a chip of its own. */
    fun filterOf(tab: BuiltInPlaylist): SongFilter? = when (tab) {
        BuiltInPlaylist.All -> SongFilter.ALL
        BuiltInPlaylist.Favorites -> SongFilter.LIKED
        BuiltInPlaylist.Offline -> SongFilter.OFFLINE
        BuiltInPlaylist.Downloaded -> SongFilter.DOWNLOADED
        BuiltInPlaylist.Top -> SongFilter.TOP
        BuiltInPlaylist.Disliked -> SongFilter.DISLIKED
        BuiltInPlaylist.OnDevice -> null
    }
}
