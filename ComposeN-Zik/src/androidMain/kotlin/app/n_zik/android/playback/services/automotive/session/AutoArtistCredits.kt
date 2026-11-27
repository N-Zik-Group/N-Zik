package app.n_zik.android.playback.services.automotive.session

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.playback.services.observePlaybackIdentity
import app.n_zik.android.playback.services.sessionArtistTrackId
import app.n_zik.android.playback.services.withCurrentTrackIdentity
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

@UnstableApi
internal suspend fun refreshAutoArtistCredits(
    context: Context,
    database: Database,
    profile: String,
    items: List<MediaItem>,
): List<MediaItem> {
    if (items.isEmpty()) return items
    if (getActiveProfile(context) != profile || Database.FILE_NAME != Database.fileNameForProfile(profile)) return items
    return try {
        val unknown = ContextCompat.getContextForLanguage(context).getString(R.string.metadata_unknown_artist)
        val preferences = context.preferences
        overlayAutoArtistCredits(database.songTable, items, unknown) { preferences.getString("old_browse_id_$it", null) }
            .takeIf { getActiveProfile(context) == profile } ?: items
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Timber.tag("AutoArtistCredits").w(error, "Unable to refresh automotive track credits")
        items
    }
}

/** Recomputes the track identity for outgoing items and keeps scoped selections intact. */
@UnstableApi
internal suspend fun overlayAutoArtistCredits(
    songs: SongTable,
    items: List<MediaItem>,
    unknownArtist: String,
    originalId: (String) -> String?,
): List<MediaItem> {
    val keys = items.map { item ->
        item.takeUnless { it.mediaMetadata.isBrowsable == true || AutoMediaIdContract.isQueueAction(it.mediaId) }
            ?.sessionArtistTrackId()?.takeIf(String::isNotBlank)
    }
    val rows = songs.observePlaybackIdentity(keys.filterNotNull()).first()
    return items.mapIndexed { index, item ->
        val row = rows[keys[index]] ?: return@mapIndexed item
        item.withCurrentTrackIdentity(row, unknownArtist, originalId)
    }
}
