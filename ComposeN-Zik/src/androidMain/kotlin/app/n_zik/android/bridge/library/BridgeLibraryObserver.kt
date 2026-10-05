package app.n_zik.android.bridge.library

import android.content.Context
import android.content.SharedPreferences
import app.n_zik.android.bridge.state.BridgeStateHub
import app.n_zik.android.core.database.Database

/**
 * Contract §10.3 (since 1.7.3, feature `library.live`): pushes a `libraryChanged` delta for
 * every write to the phone's library database and for every edit of its songs sort menu.
 *
 * The library DAOs (songs, albums, artists, playlists, formats, playlist maps, play events)
 * are wrapped by [Database] so every write reaches [Database.libraryWriteNotifier], whatever
 * its origin: the phone's own UI (a like, a bookmark, a download, a deletion, …), the
 * client's §10.2 writes (which go through the same DAOs), the imports, the play counters. A
 * write to an unwrapped DAO (queue, lyrics, search) emits nothing: the queue already has its
 * `queueChanged` delta and the rest is not library listings (§10.3).
 *
 * Sort menu edits (the reordering or the option visibility of the phone's songs sort menus,
 * its `HomeSongsSortSettingsDialog`) are preferences, not database writes: a listener on the
 * `preferences` SharedPreferences watches their keys and emits `songs` — the client recovers
 * the new menu with its reload (`sortMenu` rides on the songs pages, §10.1).
 */
internal class BridgeLibraryObserver(
    context: Context,
    private val hub: BridgeStateHub,
) {
    private val prefs = context.applicationContext.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    // `start` (the server run, a background thread) and `stop` (`onDestroy`, main) can race: the
    // check-then-act is atomic under one lock, or a late `start` leaves a dangling listener
    // registered while `started` is back to `false` (the next `stop` then unregisters nothing)
    private val lock = Any()
    private var started = false
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Registers the library write notifier and the sort-menu preference listener; a no-op while already started. */
    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
            Database.libraryWriteNotifier = { kinds ->
                for (kind in kinds) {
                    hub.submitLibraryChanged(kind)
                }
            }
            val menuListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key != null && isSongsSortMenuKey(key)) {
                    hub.submitLibraryChanged("songs")
                }
            }
            prefsListener = menuListener
            prefs.registerOnSharedPreferenceChangeListener(menuListener)
        }
    }

    /** Unregisters both listeners; a no-op when never started. */
    fun stop() {
        synchronized(lock) {
            if (!started) return
            started = false
            Database.libraryWriteNotifier = null
            prefsListener?.let { prefs.unregisterOnSharedPreferenceChangeListener(it) }
            prefsListener = null
        }
    }

    companion object {
        /**
         * A preference key one of the phone's songs sort menus reads (a chip's order, an
         * option's visibility) — [SongsSortMenu.sortMenuKeys], the single source of truth
         * shared with the contract §10.1 `sortMenu` read.
         */
        fun isSongsSortMenuKey(key: String): Boolean = key in SongsSortMenu.sortMenuKeys
    }
}
