package app.it.fast4x.rimusic.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.input.key.Key
import app.it.fast4x.rimusic.enums.AlbumSortBy
import app.it.fast4x.rimusic.enums.ArtistSortBy
import app.it.fast4x.rimusic.enums.HomeItemSize
import app.it.fast4x.rimusic.enums.OnDeviceSongSortBy
import app.it.fast4x.rimusic.enums.PlaylistSongSortBy
import app.it.fast4x.rimusic.enums.PlaylistSortBy
import app.it.fast4x.rimusic.enums.SongSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.enums.StatisticsType
import app.n_zik.android.enums.HistorySortOrder

object Preference {

    /****  ENUMS  ****/
    val HOME_ARTIST_ITEM_SIZE = Key( "AristItemSizeEnum", HomeItemSize.SMALL )
    val HOME_ALBUM_ITEM_SIZE = Key( "AlbumItemSizeEnum", HomeItemSize.SMALL )
    val HOME_LIBRARY_ITEM_SIZE = Key( "LibraryItemSizeEnum", HomeItemSize.SMALL )
    val HOME_SONGS_TOP_PLAYLIST_PERIOD = Key( "HomeSongsTopPlaylistPeriod", StatisticsType.All )

    //<editor-fold defaultstate="collapsed" desc="Sort by">
    val HOME_SONGS_SORT_BY = Key( "HomeSongsSortBy", SongSortBy.Title )
    val HOME_SONGS_FAVORITES_SORT_BY = Key( "HomeSongsFavoritesSortBy", SongSortBy.DateLiked )
    val HOME_SONGS_OFFLINE_SORT_BY = Key( "HomeSongsOfflineSortBy", SongSortBy.Title )
    val HOME_SONGS_DOWNLOADED_SORT_BY = Key( "HomeSongsDownloadedSortBy", SongSortBy.Title )
    val HOME_SONGS_TOP_SORT_BY = Key( "HomeSongsTopSortBy", SongSortBy.Title )
    val HOME_SONGS_DISLIKED_SORT_BY = Key( "HomeSongsDislikedSortBy", SongSortBy.DateLiked )
    val HOME_ON_DEVICE_SONGS_SORT_BY = Key( "HomeOnDeviceSongsSortBy", OnDeviceSongSortBy.Title )
    val HOME_ARTISTS_SORT_BY = Key( "HomeArtistsSortBy", ArtistSortBy.Name )
    val HOME_ARTISTS_FAVORITES_SORT_BY = Key( "HomeArtistsFavoritesSortBy", ArtistSortBy.Name )
    val HOME_ARTISTS_LIBRARY_SORT_BY = Key( "HomeArtistsLibrarySortBy", ArtistSortBy.Name )
    val HOME_ALBUMS_SORT_BY = Key( "HomeAlbumsSortBy", AlbumSortBy.Title )
    val HOME_ALBUMS_FAVORITES_SORT_BY = Key( "HomeAlbumsFavoritesSortBy", AlbumSortBy.Title )
    val HOME_ALBUMS_LIBRARY_SORT_BY = Key( "HomeAlbumsLibrarySortBy", AlbumSortBy.Title )
    val HOME_LIBRARY_SORT_BY = Key( "HomeLibrarySortBy", PlaylistSortBy.Name )
    val HOME_LIBRARY_PLAYLIST_SORT_BY = Key( "HomeLibraryPlaylistSortBy", PlaylistSortBy.Name )
    val HOME_LIBRARY_YT_PLAYLIST_SORT_BY = Key( "HomeLibraryYTPlaylistSortBy", PlaylistSortBy.Name )

    val HOME_LIBRARY_PINNED_PLAYLIST_SORT_BY = Key( "HomeLibraryPinnedPlaylistSortBy", PlaylistSortBy.Name )
    val HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_BY = Key( "HomeLibraryMonthlyPlaylistSortBy", PlaylistSortBy.Name )
    val PLAYLIST_SONGS_SORT_BY = Key( "PlaylistSongsSortBy", PlaylistSongSortBy.Title )
    val HISTORY_SORT_BY = Key( "HistorySortBy", HistorySortOrder.DATE )
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Sort order">
    val HOME_SONGS_SORT_ORDER = Key( "HomeSongsSortOrder", SortOrder.Ascending )
    val HOME_SONGS_FAVORITES_SORT_ORDER = Key( "HomeSongsFavoritesSortOrder", SortOrder.Ascending )
    val HOME_SONGS_OFFLINE_SORT_ORDER = Key( "HomeSongsOfflineSortOrder", SortOrder.Ascending )
    val HOME_SONGS_DOWNLOADED_SORT_ORDER = Key( "HomeSongsDownloadedSortOrder", SortOrder.Ascending )
    val HOME_SONGS_TOP_SORT_ORDER = Key( "HomeSongsTopSortOrder", SortOrder.Ascending )
    val HOME_SONGS_DISLIKED_SORT_ORDER = Key( "HomeSongsDislikedSortOrder", SortOrder.Ascending )
    val HOME_ON_DEVICE_SONGS_SORT_ORDER = Key( "HomeOnDeviceSongsSortOrder", SortOrder.Ascending )
    val HOME_ARTISTS_SORT_ORDER = Key( "HomeArtistsSortOrder", SortOrder.Ascending )
    val HOME_ARTISTS_FAVORITES_SORT_ORDER = Key( "HomeArtistsFavoritesSortOrder", SortOrder.Ascending )
    val HOME_ARTISTS_LIBRARY_SORT_ORDER = Key( "HomeArtistsLibrarySortOrder", SortOrder.Ascending )
    val HOME_ALBUM_SORT_ORDER = Key( "HomeAlbumSortOrder", SortOrder.Ascending )
    val HOME_ALBUMS_FAVORITES_SORT_ORDER = Key( "HomeAlbumsFavoritesSortOrder", SortOrder.Ascending )
    val HOME_ALBUMS_LIBRARY_SORT_ORDER = Key( "HomeAlbumsLibrarySortOrder", SortOrder.Ascending )
    val HOME_LIBRARY_SORT_ORDER = Key( "HomeLibrarySortOrder", SortOrder.Ascending )
    val HOME_LIBRARY_PLAYLIST_SORT_ORDER = Key( "HomeLibraryPlaylistSortOrder", SortOrder.Ascending )
    val HOME_LIBRARY_YT_PLAYLIST_SORT_ORDER = Key( "HomeLibraryYTPlaylistSortOrder", SortOrder.Ascending )

    val HOME_LIBRARY_PINNED_PLAYLIST_SORT_ORDER = Key( "HomeLibraryPinnedPlaylistSortOrder", SortOrder.Ascending )
    val HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_ORDER = Key( "HomeLibraryMonthlyPlaylistSortOrder", SortOrder.Ascending )
    val PLAYLIST_SONGS_SORT_ORDER = Key( "PlaylistSongsSortOrder", SortOrder.Ascending )
    val HISTORY_SORT_ORDER = Key( "HistorySortOrder", SortOrder.Ascending )
    //</editor-fold>

    val SEARCH_RESULT_GRID_STATES = Key( "searchResultGridStates", "1111111" )

    @Composable
    inline fun <reified T: Enum<T>> remember( key: Key<T>): MutableState<T> =
        rememberPreference( key.key, key.default )

    /**
     * In order to ensure consistent between input key and output value.
     * The provided key must bear a potential return value.
     */
    data class Key<T>( val key: String, val default: T )
}


