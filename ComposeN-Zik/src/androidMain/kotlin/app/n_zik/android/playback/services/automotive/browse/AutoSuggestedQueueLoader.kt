package app.n_zik.android.playback.services.automotive.browse

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.enums.MaxSongs
import app.it.fast4x.rimusic.hasExplicitPrefix
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.maxSongsInQueueAndroidAutoKey
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.playback.services.automotive.DislikedExclusion
import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import app.n_zik.android.playback.services.automotive.models.CancellableAutoMediaItemMapper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.widget.PlaylistQuickPickSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Adapts the widget's shared selection to playable queues or existing browse destinations. */
object AutoSuggestedQueueLoader {
    const val ROOT_ID = "AA_SUGGESTED"

    data class SuggestedQueue(
        val parentId: String,
        val title: String,
        val artworkUri: Uri?,
        val mediaType: Int,
        val items: List<MediaItem>,
        val browseId: String? = null,
    )

    /** The browse tree captures these queues only after checking its publication generation. */
    suspend fun load(
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?,
    ): List<SuggestedQueue> = withContext(NzikDispatchers.DATA) {
        val access = AutoAccessPolicy(context, database, downloadHelper, binder)
        val excluded = DislikedExclusion.excludedSongIds(context, database)
        val parental = context.preferences.getBoolean(parentalControlEnabledKey, false)
        val limit = context.preferences.getEnum(maxSongsInQueueAndroidAutoKey, MaxSongs.Unlimited).toInt()
        PlaylistQuickPickSource.shared.load(context, database).mapNotNull { entry ->
            val (parentId, browseId, mediaType) = when (val target = entry.target) {
                PlaylistQuickPickSource.Target.Favorites -> Triple(AutoSessionConstants.ID_SONGS_FAVORITES,
                    AutoSessionConstants.ID_SONGS_FAVORITES, MediaMetadata.MEDIA_TYPE_PLAYLIST)
                is PlaylistQuickPickSource.Target.Playlist -> Triple("${AutoSessionConstants.ID_PLAYLISTS_LOCAL}/${target.id}",
                    "${PlayerServiceModern.PLAYLIST}/${target.id}", MediaMetadata.MEDIA_TYPE_PLAYLIST)
                is PlaylistQuickPickSource.Target.Artist -> Triple("${AutoSessionConstants.ID_ARTISTS_LIBRARY}/${target.id}",
                    "${PlayerServiceModern.ARTIST}/${target.id}", MediaMetadata.MEDIA_TYPE_ARTIST)
                is PlaylistQuickPickSource.Target.Album -> Triple("${AutoSessionConstants.ID_ALBUMS_LIBRARY}/${target.id}",
                    "${PlayerServiceModern.ALBUM}/${target.id}", MediaMetadata.MEDIA_TYPE_ALBUM)
                is PlaylistQuickPickSource.Target.Track -> Triple("${AutoSessionConstants.ID_SONGS_ALL}/${target.song.id}",
                    null, MediaMetadata.MEDIA_TYPE_MUSIC)
            }
            val sourceSongs = when (val target = entry.target) {
                PlaylistQuickPickSource.Target.Favorites -> database.songTable.allFavorites().first()
                is PlaylistQuickPickSource.Target.Playlist -> database.songPlaylistMapTable.sortSongsByPosition(target.id).first()
                is PlaylistQuickPickSource.Target.Artist -> database.songArtistMapTable.allSongsBy(target.id).first()
                is PlaylistQuickPickSource.Target.Album -> database.songAlbumMapTable.allSongsOf(target.id).first()
                is PlaylistQuickPickSource.Target.Track -> listOf(target.song)
            }
            val songs = sourceSongs.filter { it.id !in excluded && (!parental || !it.title.hasExplicitPrefix()) }
            val identities = songs.map { MediaItem.Builder().setMediaId(it.id).build() }
            val eligible = access.eligible(identities).take(limit).map { songs[it.index] }
            if (eligible.isEmpty() && browseId == null) return@mapNotNull null
            val firstSong = eligible.firstOrNull()
            val artwork = entry.artworkSource
            val uri = when {
                entry.target == PlaylistQuickPickSource.Target.Favorites -> drawableUri(context, R.drawable.ic_widget_heart_nav)
                !artwork.isNullOrBlank() || firstSong?.isLocal == true ->
                    CancellableAutoMediaItemMapper.mapSongToMediaItem(
                        (firstSong ?: Song.makePlaceholder(parentId.substringAfterLast('/'))).copy(thumbnailUrl = artwork),
                    ).mediaMetadata.artworkUri
                else -> drawableUri(context, R.drawable.ic_launcher_box)
            }
            SuggestedQueue(parentId, entry.title, uri, mediaType,
                eligible.map { CancellableAutoMediaItemMapper.mapSongToMediaItem(it) }, browseId)
        }
    }
}
