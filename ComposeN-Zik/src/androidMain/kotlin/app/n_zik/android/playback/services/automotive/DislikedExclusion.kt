package app.n_zik.android.playback.services.automotive

import android.content.Context
import app.it.fast4x.rimusic.enums.DislikeMode
import app.it.fast4x.rimusic.enums.SongSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.Preference
import app.it.fast4x.rimusic.utils.excludeDislikedAlbumsKey
import app.it.fast4x.rimusic.utils.excludeDislikedArtistsKey
import app.it.fast4x.rimusic.utils.excludeDislikedSongsKey
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.core.database.Database
import kotlinx.coroutines.flow.first

/**
 * Android Auto mirror of the phone's dislike exclusions. The phone shuffler
 * ([app.n_zik.android.playback.utils.Shuffler]) drops, from every queue it
 * builds: disliked songs (gated by the songs [DislikeMode]), songs by
 * disliked artists (artists [DislikeMode]) and songs from disliked albums
 * (albums [DislikeMode]) — all three settings default to [DislikeMode.Enabled].
 * AA reuses the same settings and the same DB queries so its browse lists and
 * playback queues can never disagree with the phone's playback. This is the
 * single source of truth of the exclusion in Android Auto.
 */
object DislikedExclusion {

    /**
     * Reads one phone DislikeMode setting with the shuffler read pattern
     * (corrupt value or missing key -> default [DislikeMode.Enabled]).
     */
    private fun dislikeMode(context: Context, key: String): DislikeMode =
        context.preferences.getString(key, DislikeMode.Enabled.name)
            ?.let { runCatching { DislikeMode.valueOf(it) }.getOrNull() }
            ?: DislikeMode.Enabled

    /**
     * Song ids the phone shuffler excludes from a queue for the current phone
     * settings: disliked songs + songs by disliked artists + songs from
     * disliked albums, each part only while its DislikeMode is enabled.
     * An empty set means nothing is excluded (every mode disabled, or no
     * dislikes at all).
     */
    suspend fun excludedSongIds(context: Context, database: Database): Set<String> {
        val excluded = mutableSetOf<String>()
        if (dislikeMode(context, excludeDislikedSongsKey).isEnabled) {
            excluded += database.songTable.getAllDislikedIds()
        }
        if (dislikeMode(context, excludeDislikedArtistsKey).isEnabled) {
            excluded += database.songTable.getSongsByDislikedArtists()
        }
        if (dislikeMode(context, excludeDislikedAlbumsKey).isEnabled) {
            excluded += database.songTable.getSongsByDislikedAlbums()
        }
        return excluded
    }

    /**
     * Songs of the Disliked category — only the songs flagged as disliked
     * (likedAt == -1L, the exact flag written by the dislike toggle), sorted
     * with the dedicated HOME_SONGS_DISLIKED_SORT_* keys (default DateLiked).
     * Shared by the browse branch and the shuffle/selection resolvers so the
     * three can never drift apart. Never filtered itself.
     */
    suspend fun dislikedSongs(context: Context, database: Database): List<Song> {
        val sortBy = try { context.preferences.getEnum(Preference.HOME_SONGS_DISLIKED_SORT_BY.key, SongSortBy.DateLiked) } catch (e: Exception) { SongSortBy.DateLiked }
        val sortOrder = try { context.preferences.getEnum(Preference.HOME_SONGS_DISLIKED_SORT_ORDER.key, SortOrder.Ascending) } catch (e: Exception) { SortOrder.Ascending }
        return database.songTable.sortAll(sortBy, sortOrder, excludeHidden = false).first().filter { it.likedAt == -1L }
    }
}
