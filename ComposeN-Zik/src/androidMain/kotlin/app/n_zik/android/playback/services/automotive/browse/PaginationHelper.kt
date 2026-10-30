package app.n_zik.android.playback.services.automotive.browse

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.R
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import app.n_zik.android.playback.services.automotive.session.AutoMediaIdContract
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants

/** Pages content without counting playback actions as songs. */
object PaginationHelper {
    private const val ITEMS_PER_PAGE = 200

    fun paginate(context: Context, filteredList: List<MediaItem>, actualParentId: String, pageIndex: Int): List<MediaItem> {
        val shuffle = filteredList.firstOrNull { AutoMediaIdContract.isShuffle(it.mediaId) }
        val navigation = filteredList.filter { AutoMediaIdContract.parseSearchContinuation(it.mediaId) != null }
        val content = filteredList.filterNot { AutoMediaIdContract.isQueueAction(it.mediaId) || AutoMediaIdContract.parseSearchContinuation(it.mediaId) != null }
        val playAll = shuffle?.takeUnless {
            it.mediaId == AutoSessionConstants.ID_LUCKY_SHUFFLE || actualParentId == AutoSessionConstants.ID_QUICK_PICKS
        }
            ?.let { AutoSessionConstants.playAllItem(context, AutoMediaIdContract.playAllFor(it.mediaId)) }
        val actions = listOfNotNull(playAll, shuffle)
        if (pageIndex >= 0) {
            val offset = pageIndex.toLong() * ITEMS_PER_PAGE
            if (offset >= content.size) return emptyList()
            return actions + content.drop(offset.toInt()).take(ITEMS_PER_PAGE) + navigation
        }
        if (content.size <= ITEMS_PER_PAGE) return actions + content + navigation
        val folders = content.chunked(ITEMS_PER_PAGE).mapIndexed { index, chunk ->
            val start = index * ITEMS_PER_PAGE + 1
            val end = start + chunk.size - 1
            browsableMediaItem(
                AutoMediaIdContract.page(actualParentId, index),
                "Page ${index + 1} ($start - $end)",
                null,
                drawableUri(context, R.drawable.library),
                MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
            )
        }
        return actions + folders + navigation
    }
}
