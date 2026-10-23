package app.n_zik.android.bridge.library

import android.content.Context
import android.content.SharedPreferences
import app.n_zik.android.bridge.state.BridgeStateHub
import app.n_zik.android.core.database.Database

/**
 * Contract §10.3 (since 1.7.3, `library.live`): emits a `libraryChanged` delta for every write
 * to the phone's library database and every edit of its songs sort menu.
 *
 * [Database] wraps the library DAOs (songs, albums, artists, playlists, formats, playlist
 * maps, play events) so every write — the phone's own UI, the client's §10.2 writes, imports,
 * play counters — reaches [Database.libraryWriteNotifier]. Unwrapped DAOs (queue, lyrics,
 * search) emit nothing: the queue has its own `queueChanged` delta, the rest is not
 * library listings.
 *
 * Sort menu edits are preferences, not writes: a listener on the `preferences` SharedPreferences
 * watches their keys and emits `songs` (since 1.11.0 the client no longer receives the menu
 * itself on the wire). Since 1.12.0 (`library.recommendations`), smart-shuffle changes
 * ([SongRecommendations]) emit `songs` too — the client re-reads `GET /library/songs/recommendations`.
 */
internal class BridgeLibraryObserver(
    context: Context,
    private val hub: BridgeStateHub,
    /** A `songs` write happened (a like among them): the player source re-checks the queue's likes. */
    private val onSongsChanged: () -> Unit = {},
) {
    private val prefs = context.applicationContext.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    // `start` (the server run, a background thread) and `stop` (`onDestroy`, main) can race: the
    // check-then-act is atomic under one lock,
    //
    // or a late `start` leaves a dangling listener registered while `started` is back to
    // `false` (the next `stop` then unregisters nothing)
    private val lock = Any()
    private var started = false
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Registers the library write notifier, the sort-menu preference listener and the smart-shuffle listener; a no-op while already started. */
    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
            Database.libraryWriteNotifier = { kinds ->
                for (kind in kinds) {
                    hub.submitLibraryChanged(kind)
                }
                if ("songs" in kinds) onSongsChanged()
            }
            val menuListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key != null && isSongsSortMenuKey(key)) {
                    hub.submitLibraryChanged("songs")
                }
            }
            prefsListener = menuListener
            prefs.registerOnSharedPreferenceChangeListener(menuListener)
            SongRecommendations.changeListener = { hub.submitLibraryChanged("songs") }
        }
    }

    /** Unregisters every listener; a no-op when never started. */
    fun stop() {
        synchronized(lock) {
            if (!started) return
            started = false
            Database.libraryWriteNotifier = null
            prefsListener?.let { prefs.unregisterOnSharedPreferenceChangeListener(it) }
            prefsListener = null
            SongRecommendations.changeListener = null
        }
    }

    companion object {
        /** A preference key one of the phone's songs sort menus reads (a chip's order, an option's visibility) — [SongsSortMenu.sortMenuKeys], the single source of truth of those keys (the §10.1 `sortMenu` read that shared it was removed in 1.11.0). */
        fun isSongsSortMenuKey(key: String): Boolean = key in SongsSortMenu.sortMenuKeys
    }
}
