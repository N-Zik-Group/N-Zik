package app.n_zik.android.components.menu.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.cleanPrefix
import app.n_zik.android.hasExplicitPrefix
import app.n_zik.android.models.Song
import app.n_zik.android.utils.EXPLICIT_BUNDLE_TAG
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper

@UnstableApi
internal fun playerMenuSongId(item: MediaItem): String =
    item.localConfiguration?.customCacheKey?.takeIf(String::isNotBlank)
        ?: item.mediaId.substringAfterLast('/')

/** Refreshes persisted display fields while retaining playback configuration and provider extras. */
@UnstableApi
internal fun playerMenuMediaItem(item: MediaItem, song: Song): MediaItem {
    val source = song.thumbnailUrl?.let(::cleanPrefix)
    val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply {
        putBoolean("isTitleModified", song.title.startsWith(MODIFIED_PREFIX, true))
        putBoolean("isArtistModified", song.artistsText?.startsWith(MODIFIED_PREFIX, true) == true)
        putBoolean("isCoverModified", song.thumbnailUrl?.startsWith(MODIFIED_PREFIX, true) == true)
        putBoolean(EXPLICIT_BUNDLE_TAG, song.title.hasExplicitPrefix())
        putBoolean("androidx.media3.session.EXTRAS_KEY_IS_EXPLICIT", song.title.hasExplicitPrefix())
        putString("durationText", song.durationText)
        putString(SessionMediaItemMapper.ORIGINAL_ARTWORK_SOURCE_EXTRA, source)
    }
    return item.buildUpon().setMediaId(song.id).setMediaMetadata(
        item.mediaMetadata.buildUpon()
            .setTitle(song.cleanTitle())
            .setArtist(song.cleanArtistsText())
            .setArtworkUri(source?.let(Uri::parse))
            .setArtworkData(null, null)
            .setExtras(extras)
            .build()
    ).build()
}
