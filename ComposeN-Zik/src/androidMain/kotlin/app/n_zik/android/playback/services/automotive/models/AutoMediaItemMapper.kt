package app.n_zik.android.playback.services.automotive.models

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.cleanPrefix

@UnstableApi
object AutoMediaItemMapper {

    fun browsableMediaItem(
        id: String,
        title: String,
        subtitle: String?,
        iconUri: Uri?,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_MUSIC,
        path: String = ""
    ): MediaItem {
        val cleanTitle = cleanPrefix(title)
        val cleanSubtitle = subtitle?.let(::cleanPrefix)
        
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(cleanTitle)
                    .setSubtitle(cleanSubtitle)
                    .setArtist(cleanSubtitle)
                    .setArtworkUri(iconUri)
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(mediaType)
                    .build()
            )
            .build()
    }

    fun drawableUri(context: Context, @DrawableRes id: Int): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.resources.getResourcePackageName(id))
        .appendPath(context.resources.getResourceTypeName(id))
        .appendPath(context.resources.getResourceEntryName(id))
        .build()
}
