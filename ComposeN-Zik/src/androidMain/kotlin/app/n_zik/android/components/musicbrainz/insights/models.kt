package app.n_zik.android.components.musicbrainz.insights

import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import app.n_zik.android.musicbrainz.models.ExternalLink

data class ArtistDetailUiState(
    val isLoading: Boolean = true,
    val artist: Artist? = null,
    val albums: List<Album> = emptyList(),
    val topTracks: List<Song> = emptyList(),
    val topAlbums: List<Album> = emptyList(),
    val relations: List<ArtistRelationInfo> = emptyList(),
    val externalLinks: List<ExternalLink> = emptyList(),
    val stats: ArtistStats? = null
)

data class ArtistRelationInfo(
    val artist: Artist,
    val relationType: String,
    val direction: String,
    val localArtistId: String? = null
)

data class ArtistStats(
    val totalPlayTimeMs: Long,
    val playCount: Int,
    val likedSongsCount: Int,
    val distinctAlbumsCount: Int,
    val bookmarkedAlbumsCount: Int
)

data class AlbumDetailUiState(
    val isLoading: Boolean = true,
    val album: Album? = null,
    val tracks: List<Song> = emptyList(),
    val topTracks: List<Song> = emptyList(),
    val artist: Artist? = null,
    val otherAlbums: List<Album> = emptyList(),
    val singlesAndEps: List<Album> = emptyList(),
    val externalLinks: List<ExternalLink> = emptyList(),
    val stats: AlbumStats? = null
)

data class AlbumStats(
    val totalPlayTimeMs: Long,
    val playCount: Int,
    val likedSongsCount: Int,
    val tracksCount: Int
)
