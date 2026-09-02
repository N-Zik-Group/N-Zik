package app.n_zik.android.playback.services.automotive.session

import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import app.n_zik.android.R

object AutoSessionConstants {
    const val ID_QUICK_PICKS = "QUICK_PICKS"
    const val ID_LUCKY_SHUFFLE = "LUCKY_SHUFFLE"
    const val ID_SONG_SHUFFLE = "SONG_SHUFFLE"
    const val ID_ALBUM_SHUFFLE = "ALBUM_SHUFFLE"
    const val ID_ARTIST_SHUFFLE = "ARTIST_SHUFFLE"
    const val ID_SEARCH_SONGS = "SEARCH_SONGS"
    const val ID_SEARCH_ARTISTS = "SEARCH_ARTISTS"
    const val ID_SEARCH_ALBUMS = "SEARCH_ALBUMS"
    const val ID_SEARCH_VIDEOS = "SEARCH_VIDEOS"
    const val ID_SEARCH_PLAYLISTS = "SEARCH_PLAYLISTS"
    const val ID_SEARCH_FEATURED = "SEARCH_FEATURED"
    const val ID_SEARCH_PODCASTS = "SEARCH_PODCASTS"
    const val ID_FAVORITES = "FAVORITES"

    const val ID_CACHED = "CACHED"
    const val ID_DOWNLOADED = "DOWNLOADED"
    const val ID_TOP = "TOP"
    const val ID_ONDEVICE = "ONDEVICE"
    const val ID_ALBUMS_LIBRARY = "ALBUMS_LIBRARY"
    const val ID_ALBUMS_FAVORITES = "ALBUMS_FAVORITES"
    const val ID_ALBUMS_LIBRARY_SHUFFLE = "ALBUMS_LIBRARY_SHUFFLE"
    const val ID_ALBUMS_FAVORITES_SHUFFLE = "ALBUMS_FAVORITES_SHUFFLE"
    const val ID_SONGS_ALL = "SONGS_ALL"
    const val ID_SONGS_FAVORITES = "SONGS_FAVORITES"
    const val ID_SONGS_DOWNLOADED = "SONGS_DOWNLOADED"
    const val ID_SONGS_ONDEVICE = "SONGS_ONDEVICE"
    const val ID_SONGS_CACHED = "SONGS_CACHED"
    const val ID_SONGS_TOP = "SONGS_TOP"
    const val ID_SONGS_ALL_SHUFFLE = "SONGS_ALL_SHUFFLE"
    const val ID_SONGS_FAVORITES_SHUFFLE = "SONGS_FAVORITES_SHUFFLE"
    const val ID_SONGS_DOWNLOADED_SHUFFLE = "SONGS_DOWNLOADED_SHUFFLE"
    const val ID_SONGS_ONDEVICE_SHUFFLE = "SONGS_ONDEVICE_SHUFFLE"
    const val ID_SONGS_CACHED_SHUFFLE = "SONGS_CACHED_SHUFFLE"
    const val ID_SONGS_TOP_SHUFFLE = "SONGS_TOP_SHUFFLE"
    const val ID_PLAYLISTS_LOCAL = "PLAYLISTS_LOCAL"
    const val ID_PLAYLISTS_YT = "PLAYLISTS_YT"
    const val ID_PLAYLISTS_PINNED = "PLAYLISTS_PINNED"
    const val ID_PLAYLISTS_MONTHLY = "PLAYLISTS_MONTHLY"
    const val ID_PLAYLISTS_LOCAL_SHUFFLE = "PLAYLISTS_LOCAL_SHUFFLE"
    const val ID_PLAYLISTS_YT_SHUFFLE = "PLAYLISTS_YT_SHUFFLE"
    const val ID_PLAYLISTS_PINNED_SHUFFLE = "PLAYLISTS_PINNED_SHUFFLE"
    const val ID_PLAYLISTS_MONTHLY_SHUFFLE = "PLAYLISTS_MONTHLY_SHUFFLE"
    const val ID_PLAYLIST_SHUFFLE = "PLAYLIST_SHUFFLE"
    const val ID_SONGS_OTHERS = "SONGS_OTHERS"
    const val ID_ARTISTS_LIBRARY = "ARTISTS_LIBRARY"
    const val ID_ARTISTS_FAVORITES = "ID_ARTISTS_FAVORITES"
    const val ID_ARTISTS_LIBRARY_SHUFFLE = "ARTISTS_LIBRARY_SHUFFLE"
    const val ID_ARTISTS_FAVORITES_SHUFFLE = "ARTISTS_FAVORITES_SHUFFLE"

    @OptIn(UnstableApi::class)
    fun shuffleItem(context: Context, id: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(context.getString(R.string.shuffle))
                .setArtworkUri(AutoMediaItemMapper.drawableUri(context, R.drawable.random))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        )
        .build()
        
    @OptIn(UnstableApi::class)
    fun playAllItem(context: Context, id: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(context.getString(R.string.play_all))
                .setArtworkUri(AutoMediaItemMapper.drawableUri(context, R.drawable.play))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        )
        .build()
    const val ACTION_TOGGLE_DOWNLOAD = "TOGGLE_DOWNLOAD"
    const val ACTION_TOGGLE_LIKE = "TOGGLE_LIKE"
    const val ACTION_TOGGLE_SHUFFLE = "TOGGLE_SHUFFLE"
    const val ACTION_TOGGLE_REPEAT_MODE = "TOGGLE_REPEAT_MODE"
    const val ACTION_START_RADIO = "START_RADIO"
    const val ACTION_SEARCH = "ACTION_SEARCH"
    val CommandToggleDownload = SessionCommand(ACTION_TOGGLE_DOWNLOAD, Bundle.EMPTY)
    val CommandToggleLike = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
    val CommandToggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    val CommandToggleRepeatMode = SessionCommand(ACTION_TOGGLE_REPEAT_MODE, Bundle.EMPTY)
    val CommandStartRadio = SessionCommand(ACTION_START_RADIO, Bundle.EMPTY)
    val CommandSearch = SessionCommand(ACTION_SEARCH, Bundle.EMPTY)
}
