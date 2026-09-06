package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.asSong
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.relatedPage
import kotlinx.coroutines.flow.first
import timber.log.Timber
import it.fast4x.innertube.YtMusic
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn

class QuickPicksBrowseHandler : BrowseHandler {
    override fun handles(parentId: String): Boolean = parentId == AutoSessionConstants.ID_QUICK_PICKS

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?
    ): List<MediaItem> {
        val luckyItem = MediaItem.Builder()
            .setMediaId(AutoSessionConstants.ID_LUCKY_SHUFFLE)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(context.getString(R.string.lucky_shuffle))
                    .setArtworkUri(drawableUri(context, R.drawable.smart_shuffle))
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
            
        val trending = database.eventTable.findSongsMostPlayedBetween(from = 0, limit = 500).first()
        val relatedSongs = if (trending.isNotEmpty()) {
            Innertube.relatedPage(videoId = trending.first().id, setLogin = isYouTubeLoggedIn() && Innertube.useLoginForBrowse)?.getOrNull()?.songs?.map { it.asSong } ?: emptyList()
        } else emptyList()
        val ytmQuickPicks = if (isYouTubeLoggedIn()) {
            YtMusic.getQuickPicks(setLogin = true).getOrNull()?.map { it.asSong } ?: emptyList()
        } else emptyList()
        
        Timber.tag("QuickPicksBrowseHandler").d("Quick picks loaded -> trending: ${trending.size}, related: ${relatedSongs.size}, ytb: ${ytmQuickPicks.size}")
        
        val trendingItems = trending.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, parentId) }
        val relatedItems = relatedSongs.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, parentId) }
        val ytmItems = ytmQuickPicks.map { song -> SessionMediaItemMapper.mapSongToMediaItem(song, parentId) }
        
        return (listOf(luckyItem) + (ytmItems + trendingItems + relatedItems).distinctBy { it.mediaId })
    }
}
