package app.n_zik.android.core.migration

import android.content.Context
import android.content.SharedPreferences
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.PlaylistTable
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * One-shot cleanup of the legacy `monthly:YYYYMM` playlists (spec "Retrait du mécanisme
 * legacy « monthly playlists » + catégorie « Rewind playlists »"): the playlists that the
 * abandoned on-the-fly generation (`CheckMonthlyPlaylist()`) created on the device are
 * deleted once, playlists AND their `SongPlaylistMap` mappings (FK cascade on
 * `SongPlaylistMap.playlistId`), and can never come back.
 *
 * Idempotent and cheap: guarded by a SharedPreferences flag — a second run, or a run with
 * nothing to delete, writes nothing to the database. If the read fails, the flag is NOT
 * set, so the cleanup retries on the next launch.
 *
 * Wired in [MainApplication] onCreate, right after [RemovedSettingsMigration.run].
 */
object MonthlyPlaylistCleanup {
    private const val TAG = "MonthlyPlaylistCleanup"

    /** SharedPreferences flag key, set once the cleanup has run (same "preferences" file as [RemovedSettingsMigration]). */
    const val FLAG_KEY = "monthlyPlaylistsCleanupDone"

    /** Legacy prefix written by the deleted `CheckMonthlyPlaylist()` mechanism. */
    const val LEGACY_MONTHLY_PREFIX = "monthly:"

    /**
     * Schedules the one-shot cleanup on [NzikDispatchers.DATA] and logs failures without
     * setting the flag, so the next launch retries.
     */
    fun run(context: Context) {
        val prefs = context.preferences
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            runCatching {
                runClean(prefs, Database.playlistTable)
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "Legacy monthly playlists cleanup failed (will retry on next launch)")
            }
        }
    }

    /**
     * The one-shot core, parameterized by the flag store and the DAO so it can be
     * exercised in unit tests with an in-memory database.
     *
     * @return the number of deleted legacy playlists (0 when the flag was already set or
     * nothing matched)
     */
    internal suspend fun runClean(prefs: SharedPreferences, playlistTable: PlaylistTable): Int {
        if (prefs.getBoolean(FLAG_KEY, false)) return 0

        val legacy = playlistTable.allAsPreview().first()
            .filter { it.playlist.name.startsWith(LEGACY_MONTHLY_PREFIX, ignoreCase = true) }

        if (legacy.isNotEmpty()) {
            // delete() cascades to the SongPlaylistMap rows (FK on playlistId)
            legacy.forEach { preview -> playlistTable.delete(preview.playlist) }
        }
        prefs.edit().putBoolean(FLAG_KEY, true).apply()
        Timber.tag(TAG).i("Legacy monthly playlists cleanup: deleted %d playlist(s)", legacy.size)
        return legacy.size
    }
}
