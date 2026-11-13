package app.n_zik.android.components.dialog.song

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.cleanPrefix
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.hasExplicitPrefix
import app.n_zik.android.models.Song
import app.n_zik.android.utils.EXPLICIT_BUNDLE_TAG
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.appContext
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext

/** Updates player/menu snapshots only after the corresponding database commit. */
@UnstableApi
internal object SongUpdatePlaybackRefresh {
    suspend fun publish(
        binder: PlayerServiceModern.Binder?,
        song: Song,
        selection: SongUpdateSelection,
        profile: String,
    ) {
        if (binder == null || !selection.needsFetch) return
        withContext(NzikDispatchers.UI) {
            if (getActiveProfile(appContext()) != profile) return@withContext
            refresh(binder.player, song, selection)
        }
    }

    internal fun refresh(player: Player, song: Song, selection: SongUpdateSelection) {
        for (index in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            if (item.mediaId != song.id) continue
            val extras = Bundle(item.mediaMetadata.extras ?: Bundle())
            val metadata = item.mediaMetadata.buildUpon()
            if (selection.title) {
                metadata.setTitle(cleanPrefix(song.title))
                extras.putBoolean("isTitleModified", song.title.startsWith(MODIFIED_PREFIX, true))
                extras.putBoolean(EXPLICIT_BUNDLE_TAG, song.title.hasExplicitPrefix())
                extras.putBoolean("androidx.media3.session.EXTRAS_KEY_IS_EXPLICIT", song.title.hasExplicitPrefix())
            }
            if (selection.authors) {
                metadata.setArtist(song.artistsText?.let(::cleanPrefix))
                extras.putBoolean("isArtistModified", song.artistsText?.startsWith(MODIFIED_PREFIX, true) == true)
            }
            if (selection.thumbnail) {
                val source = song.thumbnailUrl?.let(::cleanPrefix)
                metadata.setArtworkUri(source?.let(Uri::parse)).setArtworkData(null, null)
                extras.putString(SessionMediaItemMapper.ORIGINAL_ARTWORK_SOURCE_EXTRA, source)
                extras.putBoolean("isCoverModified", song.thumbnailUrl?.startsWith(MODIFIED_PREFIX, true) == true)
            }
            val replacement = item.buildUpon().setMediaMetadata(metadata.setExtras(extras).build()).build()
            if (replacement != item) player.replaceMediaItem(index, replacement)
        }
    }
}
