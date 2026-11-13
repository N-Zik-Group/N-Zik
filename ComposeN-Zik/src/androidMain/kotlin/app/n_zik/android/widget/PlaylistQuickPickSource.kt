package app.n_zik.android.widget

import android.content.Context
import android.content.res.Resources
import androidx.core.content.ContextCompat
import android.os.SystemClock
import app.n_zik.android.models.Song
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Shared widget and automotive selection, including the always-present Favorites fallback. */
internal class PlaylistQuickPickSource(private val now: () -> Long = { SystemClock.elapsedRealtime() }) {
    sealed interface Target {
        data object Favorites : Target
        data class Playlist(val id: Long) : Target
        data class Artist(val id: String) : Target
        data class Album(val id: String) : Target
        data class Track(val song: Song) : Target
    }

    data class Entry(val target: Target, val title: String, val artworkSource: String?)

    private data class Snapshot(val profile: String, val language: String, val createdAt: Long, val entries: List<Entry>)
    private val mutex = Mutex()
    private var cached: Snapshot? = null

    suspend fun load(context: Context, database: Database = Database): List<Entry> = withContext(NzikDispatchers.DATA) {
        mutex.withLock {
            val profile = getActiveProfile(context)
            val resources = ContextCompat.getContextForLanguage(context).resources
            val language = resources.configuration.locales.toLanguageTags()
            val timestamp = now()
            cached?.takeIf { it.profile == profile && it.language == language && timestamp - it.createdAt in 0L until 60_000L }
                ?.let { return@withLock it.entries }
            val entries = build(resources, database)
            if (profile != getActiveProfile(context)) return@withLock emptyList()
            cached = Snapshot(profile, language, timestamp, entries)
            entries
        }
    }

    private suspend fun build(resources: Resources, database: Database): List<Entry> {
        val favorites = Entry(Target.Favorites, resources.getString(R.string.library_favorites_title), null)
        val items = mutableListOf<Entry>()
        database.playlistTable.sortPreviewsByPlayCount(8).firstOrNull().orEmpty().forEach { preview ->
            val playlist = preview.playlist
            val firstSong = database.songPlaylistMapTable.sortSongsByPosition(playlist.id, 1).firstOrNull()?.firstOrNull()
            items += Entry(Target.Playlist(playlist.id), playlist.name, firstSong?.thumbnailUrl)
        }
        val artists = database.artistTable.sortFollowingByPlayCount(8).firstOrNull().orEmpty() +
            database.artistTable.sortInLibraryByPlayCount(8).firstOrNull().orEmpty()
        artists.distinctBy { it.id }.forEach {
            items += Entry(Target.Artist(it.id), it.name ?: resources.getString(R.string.common_unknown), it.thumbnailUrl)
        }
        val albums = database.albumTable.sortBookmarkedByPlayCount(8).firstOrNull().orEmpty() +
            database.albumTable.sortInLibraryByPlayCount(8).firstOrNull().orEmpty()
        albums.distinctBy { it.id }.forEach {
            items += Entry(Target.Album(it.id), it.title ?: resources.getString(R.string.common_unknown), it.thumbnailUrl)
        }
        database.songTable.all().firstOrNull().orEmpty().shuffled().take(8).forEach {
            items += Entry(Target.Track(it), it.title, it.thumbnailUrl)
        }
        return listOf(favorites) + items.shuffled().take(7)
    }

    companion object {
        val shared = PlaylistQuickPickSource()
    }
}
