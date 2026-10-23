package app.n_zik.android.extensions.musicbrainz

import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.n_zik.android.core.database.AlbumTable
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.musicbrainz.MusicBrainz
import timber.log.Timber

/**
 * Fetches and stores MusicBrainz metadata for artists and albums.
 *
 * Intended to be called from a background coroutine (NzikDispatchers.DATA)
 * when the user views an artist/album page, and from the backfill worker.
 */
class MBMetadataHelper(
    private val mbClient: MusicBrainz = MusicBrainz(),
    private val artistTable: ArtistTable = Database.artistTable,
    private val albumTable: AlbumTable = Database.albumTable
) {

    companion object {
        val Default by lazy { MBMetadataHelper() }

        // MusicBrainz aggressively rate-limits (503 "server busy"); without a cooldown,
        // every view of an artist/album whose last fetch failed retries immediately,
//
        // spamming the API and the logs. Manual resync (force = true) bypasses this.
        private val RETRY_COOLDOWN_MS = java.util.concurrent.TimeUnit.MINUTES.toMillis(5)

        // A successful fetch is otherwise never retried (genres/tags being non-null is
        // the "fetched" sentinel). Expire it after 14 days so ratings/tags/bio pick up
//
        // MusicBrainz-side changes periodically instead of being fetched once forever.
        private val REFRESH_EXPIRY_MS = java.util.concurrent.TimeUnit.DAYS.toMillis(14)
    }

    private val TAG = "MBMetadataHelper"

    /** Seconds left before the shared MusicBrainz client's circuit breaker allows requests again, or 0 if closed. */
    fun circuitOpenRemainingSeconds(): Long = mbClient.circuitOpenRemainingMs() / 1000

    /**
     * @return true if the artist is up to date or was successfully (re)synced,
     * false if the MusicBrainz fetch failed.
     */
    suspend fun onArtistViewed(artistId: String, force: Boolean = false): Boolean {
        val artist = artistTable.findByIdDirect(artistId) ?: return false
        val now = System.currentTimeMillis()

        // genres/tags are always written after a fetch cycle (even when the
        // MusicBrainz search finds no match), so they act as the fetched sentinel;
//
        // expires after REFRESH_EXPIRY_MS so a stale successful sync gets refreshed
        val alreadyFetched = artist.genres != null && artist.tags != null
        val isExpired = artist.mbLastFetch != null && now - artist.mbLastFetch >= REFRESH_EXPIRY_MS
        if (!force && alreadyFetched && !isExpired) return true

        if (!force && artist.mbLastFetch != null && now - artist.mbLastFetch < RETRY_COOLDOWN_MS) return true

        val artistName = artist.name?.takeIf { it.isNotBlank() } ?: run {
            artistTable.update(artist.copy(mbLastFetch = now))
            return false
        }

        return try {
            var metadata = mbClient.fetchArtistMetadata(artistName)

            // No Wikipedia link on MusicBrainz -> try a direct Wikipedia search
            if (metadata.wikipediaUrl == null && metadata.wikipediaBio == null) {
                val searchTerm = when (artist.artistType ?: metadata.artistType) {
                    "Group" -> "$artistName band"
                    "Person" -> "$artistName singer"
                    else -> artistName
                }
                mbClient.fetchWikipediaExtractByArtist(searchTerm)?.let { wiki ->
                    metadata = metadata.copy(wikipediaBio = wiki.info, wikipediaUrl = wiki.url)
                }
            }

            // Re-read: the fetch above can take several seconds under rate-limiting, and
            // writing on the snapshot taken before it would clobber anything the user did
//
            // in the meantime (like/follow, bookmark, etc.) since updateReplace overwrites
            // the whole row.
            val current = artistTable.findByIdDirect(artistId) ?: artist
            artistTable.update(
                current.copy(
                    genres = metadata.genres.ifEmpty { emptyList() },
                    artistType = metadata.artistType ?: current.artistType,
                    countryCode = metadata.countryCode ?: current.countryCode,
                    beginYear = metadata.beginYear ?: current.beginYear,
                    tags = metadata.topTags.ifEmpty { emptyList() },
                    rating = metadata.ratingValue ?: current.rating,
                    ratingVotes = metadata.ratingVotes ?: current.ratingVotes,
                    wikipediaUrl = metadata.wikipediaUrl ?: current.wikipediaUrl,
                    wikipediaBio = metadata.wikipediaBio ?: current.wikipediaBio,
                    disambiguation = metadata.disambiguation ?: current.disambiguation,
                    links = metadata.links ?: current.links,
                    mbId = metadata.mbId ?: current.mbId,
                    mbLastFetch = now
                )
            )
            Timber.tag(TAG).d("Artist metadata stored for ${artist.name}")
            true
        } catch (e: Exception) {
            // Network error: keep genres/tags null so a later view can retry, but record
            // the attempt so we don't hammer MusicBrainz again before RETRY_COOLDOWN_MS
            val current = artistTable.findByIdDirect(artistId) ?: artist
            artistTable.update(current.copy(mbLastFetch = now))
            Timber.tag(TAG).e(e, "Failed to fetch artist metadata for ${artist.name}")
            false
        }
    }

    /**
     * @return true if the album is up to date or was successfully (re)synced,
     * false if the MusicBrainz fetch failed.
     */
    suspend fun onAlbumViewed(albumId: String, force: Boolean = false): Boolean {
        val album = albumTable.findByIdDirect(albumId) ?: return false
        val now = System.currentTimeMillis()

        // genres/tags are always written after a fetch cycle (even when the
        // MusicBrainz search finds no match), so they act as the fetched sentinel;
//
        // expires after REFRESH_EXPIRY_MS so a stale successful sync gets refreshed
        val alreadyFetched = album.genres != null && album.tags != null
        val isExpired = album.mbLastFetch != null && now - album.mbLastFetch >= REFRESH_EXPIRY_MS
        if (!force && alreadyFetched && !isExpired) return true

        if (!force && album.mbLastFetch != null && now - album.mbLastFetch < RETRY_COOLDOWN_MS) return true

        val albumTitle = album.title?.takeIf { it.isNotBlank() } ?: run {
            albumTable.updateReplace(album.copy(mbLastFetch = now))
            return false
        }
        val albumAuthor = album.authorsText?.takeIf { it.isNotBlank() }

        // Name matching is fragile (casing, "feat.", collab text); fall back to the
        // actual SongArtistMap relation of the album's own tracks, same source used
//
        // by "Go to artist" and the Insights "other albums" enrichment.
        val artist = albumAuthor?.let { artistTable.findByNameDirect(it) }
            ?: Database.songAlbumMapTable.allSongsOfDirect(albumId, limit = 1).firstOrNull()
                ?.let { song -> artistTable.findBySongIdDirect(song.id).firstOrNull() }
            ?: run {
                albumTable.updateReplace(album.copy(mbLastFetch = now))
                return false
            }

        return try {
            var metadata = mbClient.fetchAlbumMetadata(albumTitle, artist.name.orEmpty())

            if (metadata.wikipediaUrl == null && metadata.wikipediaInfo == null) {
                mbClient.fetchWikipediaExtractByArtist("$albumTitle ${artist.name.orEmpty()} album")
                    ?.let { wiki ->
                        metadata = metadata.copy(wikipediaInfo = wiki.info, wikipediaUrl = wiki.url)
                    }
            }

            // Fallback: inherit the artist's genres when the album has none
            val finalGenres = metadata.genres.ifEmpty {
                artist.genres ?: run {
                    artist.name?.let { mbClient.fetchArtistMetadata(it).genres } ?: emptyList()
                }
            }

            // Re-read: the fetches above can take several seconds under rate-limiting, and
            // writing on the snapshot taken before them would clobber anything the user did
//
            // in the meantime (bookmark, dislike, etc.) since updateReplace overwrites the
            // whole row.
            val current = albumTable.findByIdDirect(albumId) ?: album
            albumTable.updateReplace(
                current.copy(
                    genres = finalGenres.ifEmpty { emptyList() },
                    albumType = metadata.albumType ?: current.albumType,
                    originalYear = metadata.originalYear ?: current.originalYear,
                    tags = metadata.topTags.ifEmpty { emptyList() },
                    rating = metadata.ratingValue ?: current.rating,
                    ratingVotes = metadata.ratingVotes ?: current.ratingVotes,
                    wikipediaUrl = metadata.wikipediaUrl ?: current.wikipediaUrl,
                    wikipediaInfo = metadata.wikipediaInfo ?: current.wikipediaInfo,
                    links = metadata.links ?: current.links,
                    mbId = metadata.mbId ?: current.mbId,
                    mbLastFetch = now
                )
            )
            Timber.tag(TAG).d("Album metadata stored for ${album.title}")
            true
        } catch (e: Exception) {
            val current = albumTable.findByIdDirect(albumId) ?: album
            albumTable.updateReplace(current.copy(mbLastFetch = now))
            Timber.tag(TAG).e(e, "Failed to fetch album metadata for ${album.title}")
            false
        }
    }
}
