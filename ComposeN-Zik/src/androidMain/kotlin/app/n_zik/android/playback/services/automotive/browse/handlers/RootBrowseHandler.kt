package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri

class RootBrowseHandler : BrowseHandler {
    override fun handles(parentId: String): Boolean = parentId == PlayerServiceModern.ROOT

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?
    ): List<MediaItem> {
        return buildList {
            if (AutoAccessPolicy(context, database, downloadHelper, binder).online) {
                add(browsableMediaItem(AutoSessionConstants.ID_QUICK_PICKS, context.getString(R.string.quick_picks), null, drawableUri(context, R.drawable.sparkles), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED))
            }
            addAll(listOf(
                browsableMediaItem(PlayerServiceModern.SONG, context.getString(R.string.songs), null, drawableUri(context, R.drawable.musical_notes), MediaMetadata.MEDIA_TYPE_PLAYLIST),
                browsableMediaItem(PlayerServiceModern.ARTIST, context.getString(R.string.artists), null, drawableUri(context, R.drawable.people), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
                browsableMediaItem(PlayerServiceModern.ALBUM, context.getString(R.string.albums), null, drawableUri(context, R.drawable.album), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                browsableMediaItem(PlayerServiceModern.PLAYLIST, context.getString(R.string.library), null, drawableUri(context, R.drawable.library), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
            ))
        }
    }
}
