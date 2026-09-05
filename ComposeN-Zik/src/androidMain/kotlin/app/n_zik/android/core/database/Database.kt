package app.n_zik.android.core.database
import app.n_zik.android.appContext

import app.n_zik.android.core.database.*
import app.n_zik.android.*

import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMapNotNull
import androidx.compose.ui.util.fastZip
import androidx.media3.common.MediaItem
import androidx.room.AutoMigration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.requests.albumPage
import it.fast4x.innertube.utils.from
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Event
import app.it.fast4x.rimusic.models.Format
import app.n_zik.android.models.Lyrics
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.QueuedMediaItem
import app.it.fast4x.rimusic.models.SearchQuery
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongAlbumMap
import app.it.fast4x.rimusic.models.SongArtistMap
import app.it.fast4x.rimusic.models.SongPlaylistMap
import app.it.fast4x.rimusic.models.SortedSongPlaylistMap
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.parseArtists
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import app.n_zik.android.core.database.AlbumTable
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Converters
import app.n_zik.android.core.database.EventTable
import app.n_zik.android.core.database.FormatTable
import app.n_zik.android.core.database.LyricsTable
import app.n_zik.android.core.database.PlaylistTable
import app.n_zik.android.core.database.QueuedMediaItemTable
import app.n_zik.android.core.database.SearchQueryTable
import app.n_zik.android.core.database.SongAlbumMapTable
import app.n_zik.android.core.database.SongArtistMapTable
import app.n_zik.android.core.database.SongPlaylistMapTable
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.core.database.migration.From10To11Migration
import app.n_zik.android.core.database.migration.From11To12Migration
import app.n_zik.android.core.database.migration.From14To15Migration
import app.n_zik.android.core.database.migration.From20To21Migration
import app.n_zik.android.core.database.migration.From21To22Migration
import app.n_zik.android.core.database.migration.From22To23Migration
import app.n_zik.android.core.database.migration.From23To24Migration
import app.n_zik.android.core.database.migration.From24To25Migration
import app.n_zik.android.core.database.migration.From25To26Migration
import app.n_zik.android.core.database.migration.From26To27Migration
import app.n_zik.android.core.database.migration.From3To4Migration
import app.n_zik.android.core.database.migration.From7To8Migration
import app.n_zik.android.core.database.migration.From8To9Migration
import app.n_zik.android.core.database.migration.From27To28Migration
import app.n_zik.android.core.database.migration.From29To30Migration
import app.n_zik.android.core.database.migration.From30To31Migration
import app.n_zik.android.core.database.migration.From31To32Migration
import app.n_zik.android.core.database.migration.From32To33Migration
import app.n_zik.android.core.database.migration.From33To34Migration
import app.n_zik.android.core.database.migration.From34To35Migration
import app.n_zik.android.core.database.migration.From35To36Migration
import app.kreate.android.me.knighthat.utils.PropUtils
import app.n_zik.android.core.backup.BackupManager
import androidx.room.InvalidationTracker
import timber.log.Timber
import android.database.sqlite.SQLiteDatabaseLockedException

object Database {
    const val FILE_NAME = "data.db"

    private val _internal: DatabaseInitializer
        get() = DatabaseInitializer.Instance

    val songTable: SongTable
        get() = _internal.songTable
    val albumTable: AlbumTable
        get() = _internal.albumTable
    val artistTable: ArtistTable
        get() = _internal.artistTable
    val eventTable: EventTable
        get() = _internal.eventTable
    val formatTable: FormatTable
        get() = _internal.formatTable
    val lyricsTable: LyricsTable
        get() = _internal.lyricsTable
    val playlistTable: PlaylistTable
        get() = _internal.playlistTable
    val queueTable: QueuedMediaItemTable
        get() = _internal.queueTable
    val searchTable: SearchQueryTable
        get() = _internal.searchQueryTable
    val songAlbumMapTable: SongAlbumMapTable
        get() = _internal.songAlbumMapTable
    val songArtistMapTable: SongArtistMapTable
        get() = _internal.songArtistMapTable
    val songPlaylistMapTable: SongPlaylistMapTable
        get() = _internal.songPlaylistMapTable
    val importSongTable: ImportSongTable
        get() = _internal.importSongTable

    //**********************************************

    suspend fun upsert( songItem: Innertube.SongItem ) {
        val song = songItem.asSong

        // Phase 1: Read existing data from DB (no lock held)
        val dbSong = songTable.findByIdDirect(song.id)

        val artistNames = songItem.authors.parseArtists().filter { name ->
            name.isNotBlank() &&
            !name.matches(Regex("^[,&]+$")) &&
            !name.equals("et", ignoreCase = true) &&
            !name.equals("and", ignoreCase = true)
        }
        val artistDataList = mutableListOf<Pair<String, Artist?>>() // name -> existing DB artist

        for (artistName in artistNames) {
            val originalAuthor = songItem.authors?.find { it.name == artistName }
            val browseId = originalAuthor?.endpoint?.browseId

            if (browseId != null) {
                val dbArtist = artistTable.findByIdDirect(browseId)
                artistDataList.add(artistName to Artist(
                    id = browseId,
                    name = PropUtils.retainIfModified(dbArtist?.name, artistName),
                    thumbnailUrl = dbArtist?.thumbnailUrl,
                    timestamp = dbArtist?.timestamp,
                    bookmarkedAt = dbArtist?.bookmarkedAt,
                    isYoutubeArtist = dbArtist?.isYoutubeArtist == true,
                    lastFetch = dbArtist?.lastFetch
                ))
            } else {
                val dbArtistByName = artistTable.findByNameDirect(artistName)
                artistDataList.add(artistName to dbArtistByName)
            }
        }

        val dbAlbum = songItem.album?.endpoint?.browseId?.let { albumTable.findByIdDirect(it) }

        // Phase 2: Network calls (NO lock held)
        val artistsToUpsert = mutableListOf<Artist>()
        val artistsToMap = mutableListOf<Artist>()
        for ((artistName, existingArtist) in artistDataList) {
            if (existingArtist != null) {
                val retainedName = PropUtils.retainIfModified(existingArtist.name, artistName)
                if (existingArtist.name != retainedName) {
                    val updatedArtist = existingArtist.copy(name = retainedName)
                    artistsToUpsert.add(updatedArtist)
                    artistsToMap.add(updatedArtist)
                } else {
                    artistsToMap.add(existingArtist)
                }
            } else {
                try {
                    val searchResult: Innertube.ItemsPage<Innertube.ArtistItem>? =
                        Innertube.searchPage<Innertube.ArtistItem>(
                            query = artistName,
                            params = Innertube.SearchFilter.Artist.value
                        ) { content -> Innertube.ArtistItem.from(content) }?.getOrNull()

                    val foundArtist = searchResult?.items?.firstOrNull { item ->
                        item.info?.name?.equals(artistName, ignoreCase = true) == true
                    } ?: searchResult?.items?.firstOrNull()
                    if (foundArtist != null && foundArtist.key != null) {
                        val newArtist = Artist(
                            id = foundArtist.key,
                            name = foundArtist.info?.name ?: artistName,
                            isYoutubeArtist = true
                        )
                        artistsToUpsert.add(newArtist)
                        artistsToMap.add(newArtist)
                    }
                } catch (_: Exception) { }
            }
        }

        // Phase 3: Write all to DB in a single transaction (brief lock)
        _internal.withTransaction {
            // Upsert song
            val finalTitle = when {
                song.title.isNullOrBlank() && !dbSong?.title.isNullOrBlank() -> dbSong.title
                else -> PropUtils.retainIfModified(dbSong?.title, song.title)
            }
            val finalArtistsText = when {
                song.artistsText.isNullOrBlank() && !dbSong?.artistsText.isNullOrBlank() -> dbSong.artistsText
                else -> PropUtils.retainIfModified(dbSong?.artistsText, song.artistsText)
            }
            val finalSong = Song(
                id = song.id,
                title = finalTitle.orEmpty(),
                artistsText = finalArtistsText ?: "",
                durationText = PropUtils.retainIfModified(dbSong?.durationText, song.durationText),
                thumbnailUrl = PropUtils.retainIfModified(dbSong?.thumbnailUrl, song.thumbnailUrl),
                likedAt = dbSong?.likedAt,
                totalPlayTimeMs = dbSong?.totalPlayTimeMs ?: 0,
                position = dbSong?.position ?: -1
            )
            if (dbSong != finalSong) {
                songTable.upsert(finalSong)
            }

            // Upsert artists
            if (artistsToUpsert.isNotEmpty()) {
                artistTable.upsert(artistsToUpsert)
            }
            artistsToMap.forEach { mapIgnore(it, song) }

            // Upsert album
            songItem.album?.let {
                val browseId = it.endpoint?.browseId ?: return@let
                val fetchedAlbum = Album(
                    id = browseId,
                    title = PropUtils.retainIfModified(dbAlbum?.title, it.name),
                    thumbnailUrl = PropUtils.retainIfModified(dbAlbum?.thumbnailUrl, song.thumbnailUrl),
                    year = dbAlbum?.year,
                    authorsText = PropUtils.retainIfModified(dbAlbum?.authorsText, songItem.authors.parseArtists().joinToString(", ").takeIf { it.isNotBlank() }),
                    shareUrl = dbAlbum?.shareUrl,
                    timestamp = dbAlbum?.timestamp,
                    bookmarkedAt = dbAlbum?.bookmarkedAt,
                    isYoutubeAlbum = dbAlbum?.isYoutubeAlbum == true,
                    lastFetch = dbAlbum?.lastFetch
                )
                if (dbAlbum != fetchedAlbum) {
                    albumTable.upsert(fetchedAlbum)
                }

                if (fetchedAlbum.year.isNullOrBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            Innertube.albumPage(browseId = browseId)
                                ?.getOrNull()
                                ?.let { albumPage ->
                                    if (!albumPage.year.isNullOrBlank()) {
                                        val updatedAlbum = Album(
                                            id = browseId,
                                            title = PropUtils.retainIfModified(fetchedAlbum.title, albumPage.title.takeIf { !it.isNullOrBlank() }) ?: fetchedAlbum.title,
                                            thumbnailUrl = PropUtils.retainIfModified(fetchedAlbum.thumbnailUrl, albumPage.thumbnail?.url.takeIf { !it.isNullOrBlank() }) ?: fetchedAlbum.thumbnailUrl,
                                            year = albumPage.year,
                                            authorsText = PropUtils.retainIfModified(fetchedAlbum.authorsText, albumPage.authors.parseArtists().joinToString(", ").takeIf { it.isNotBlank() }) ?: fetchedAlbum.authorsText,
                                            shareUrl = PropUtils.retainIfModified(fetchedAlbum.shareUrl, albumPage.url) ?: fetchedAlbum.shareUrl,
                                            timestamp = System.currentTimeMillis(),
                                            bookmarkedAt = fetchedAlbum.bookmarkedAt,
                                            isYoutubeAlbum = fetchedAlbum.isYoutubeAlbum,
                                            lastFetch = fetchedAlbum.lastFetch
                                        )
                                        albumTable.upsert(updatedAlbum)
                                    }
                                }
                        } catch (e: Exception) {
                            Timber.tag("Database").e(e, "Failed to fetch album page for year update")
                        }
                    }
                }

                mapIgnore(fetchedAlbum, song)
            }
        }
    }

    /**
     * Attempt to insert a [MediaItem] into `Song` table
     *
     * If [mediaItem] comes with album and artist(s) then
     * this method handles the insertion automatically.
     */
    fun insertIgnore( mediaItem: MediaItem, autoFix: Boolean = true ) {
        val cleanSongId = mediaItem.mediaId.split("/").lastOrNull() ?: mediaItem.mediaId
        val newSong = mediaItem.asSong
        val dbSong = songTable.findByIdDirect(cleanSongId)
        val mergedSong = if (dbSong != null) {
            val newTitle = newSong.title
            val finalTitle = when {
                newTitle.isNullOrBlank() && !dbSong.title.isNullOrBlank() -> dbSong.title
                else -> PropUtils.retainIfModified(dbSong.title, newTitle)
            }
            val newArtistsText = newSong.artistsText
            val finalArtistsText = when {
                newArtistsText.isNullOrBlank() && !dbSong.artistsText.isNullOrBlank() -> dbSong.artistsText
                else -> PropUtils.retainIfModified(dbSong.artistsText, newArtistsText)
            }
            val newDurationText = newSong.durationText
            val finalDurationText = when {
                newDurationText.isNullOrBlank() && !dbSong.durationText.isNullOrBlank() -> dbSong.durationText
                else -> newDurationText
            }
            val newThumbnailUrl = newSong.thumbnailUrl
            val finalThumbnailUrl = when {
                newThumbnailUrl.isNullOrBlank() && !dbSong.thumbnailUrl.isNullOrBlank() -> dbSong.thumbnailUrl
                else -> PropUtils.retainIfModified(dbSong.thumbnailUrl, newThumbnailUrl)
            }

            Song(
                id = cleanSongId,
                title = finalTitle.orEmpty(),
                artistsText = finalArtistsText ?: "",
                durationText = finalDurationText,
                thumbnailUrl = finalThumbnailUrl,
                likedAt = dbSong.likedAt,
                totalPlayTimeMs = dbSong.totalPlayTimeMs,
                position = dbSong.position
            )
        } else newSong
        songTable.upsert(mergedSong)

        val albumId = mediaItem.mediaMetadata.extras?.getString("albumId")
        if (albumId != null) {
            val albumTitle = mediaItem.mediaMetadata.albumTitle?.toString()
            val artworkUri = mediaItem.mediaMetadata.artworkUri?.toString()
            val artist = mediaItem.mediaMetadata.artist?.toString()
            val year = mediaItem.mediaMetadata.releaseYear?.toString()
            
            val dbAlbum = albumTable.findByIdDirect(albumId)
            val mergedAlbum = if (dbAlbum != null) {
                Album(
                    id = albumId,
                    title = albumTitle.takeIf { !it.isNullOrBlank() } ?: dbAlbum.title,
                    thumbnailUrl = artworkUri.takeIf { !it.isNullOrBlank() } ?: dbAlbum.thumbnailUrl,
                    year = year.takeIf { !it.isNullOrBlank() } ?: dbAlbum.year,
                    authorsText = artist.takeIf { !it.isNullOrBlank() } ?: dbAlbum.authorsText,
                    shareUrl = dbAlbum.shareUrl,
                    timestamp = dbAlbum.timestamp,
                    bookmarkedAt = dbAlbum.bookmarkedAt,
                    lastFetch = dbAlbum.lastFetch
                )
            } else {
                Album(
                    id = albumId,
                    title = albumTitle,
                    thumbnailUrl = artworkUri,
                    year = year,
                    authorsText = artist
                )
            }
            albumTable.upsert(mergedAlbum)

            // Background fetch album page metadata if year is missing online
            if (autoFix && mergedAlbum.year.isNullOrBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        Innertube.albumPage(browseId = albumId)
                            ?.getOrNull()
                            ?.let { albumPage ->
                                if (!albumPage.year.isNullOrBlank()) {
                                    val updatedAlbum = Album(
                                        id = albumId,
                                        title = PropUtils.retainIfModified(mergedAlbum.title, albumPage.title.takeIf { !it.isNullOrBlank() }) ?: mergedAlbum.title,
                                        thumbnailUrl = PropUtils.retainIfModified(mergedAlbum.thumbnailUrl, albumPage.thumbnail?.url.takeIf { !it.isNullOrBlank() }) ?: mergedAlbum.thumbnailUrl,
                                        year = albumPage.year,
                                        authorsText = PropUtils.retainIfModified(mergedAlbum.authorsText, albumPage.authors.parseArtists().joinToString(", ").takeIf { it.isNotBlank() }) ?: mergedAlbum.authorsText,
                                        shareUrl = PropUtils.retainIfModified(mergedAlbum.shareUrl, albumPage.url) ?: mergedAlbum.shareUrl,
                                        timestamp = System.currentTimeMillis(),
                                        bookmarkedAt = mergedAlbum.bookmarkedAt
                                    )
                                    albumTable.upsert(updatedAlbum)
                                }
                            }
                    } catch (e: Exception) {
                        Timber.tag("Database").e(e, "Failed to fetch album page for year update")
                    }
                }
            }

            songAlbumMapTable.map( cleanSongId, albumId )
        }

        // Insert artist
        val artistsNames = mediaItem.mediaMetadata.extras?.getStringArrayList("artistNames").orEmpty()
        val artistsIds = mediaItem.mediaMetadata.extras?.getStringArrayList("artistIds").orEmpty()
        
        if (artistsIds.isNotEmpty()) {
            // Normal case: zip names with IDs
            artistsNames.zip(artistsIds).forEach { (name, id) ->
                val existingArtist = artistTable.findByNameDirect(name)
                val targetArtistId = if (existingArtist != null) {
                    existingArtist.id
                } else {
                    artistTable.insertIgnore(Artist(id, name))
                    id
                }
                songArtistMapTable.insertIgnore(SongArtistMap(cleanSongId, targetArtistId))
            }
        } else if (artistsNames.isNotEmpty()) {
            // No browse IDs but we have names: try database by name first
            artistsNames.forEach { name ->
                val existingArtist = artistTable.findByNameDirect(name)
                if (existingArtist != null) {
                    songArtistMapTable.insertIgnore(SongArtistMap(cleanSongId, existingArtist.id))
                } else if (autoFix) {
                    // Search online for the artist in background (non-blocking)
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val searchResult: Innertube.ItemsPage<Innertube.ArtistItem>? =
                                Innertube.searchPage<Innertube.ArtistItem>(
                                    query = name,
                                    params = Innertube.SearchFilter.Artist.value
                                ) { content -> Innertube.ArtistItem.from(content) }?.getOrNull()

                            val foundArtist = searchResult?.items?.firstOrNull { item ->
                                item.info?.name?.equals(name, ignoreCase = true) == true
                            } ?: searchResult?.items?.firstOrNull()
                            if (foundArtist != null && foundArtist.key != null) {
                                artistTable.insertIgnore(Artist(id = foundArtist.key, name = foundArtist.info?.name ?: name, isYoutubeArtist = true))
                                songArtistMapTable.insertIgnore(SongArtistMap(cleanSongId, foundArtist.key))
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        }
    }

    /**
     * Attempt to map [Song] to [Album].
     *
     * [song] and [album] are ensured to be existed
     * in the database before attempting to map two together.
     *
     * @param album to map
     * @param song to map
     * @param position of song in album, **default** or `-1` results in
     * database puts song to next available position in map
     */
    fun mapIgnore( album: Album, song: Song, position: Int = -1 ) {
        albumTable.insertIgnore( album )
        songTable.insertIgnore( song )
        songAlbumMapTable.map( song.id, album.id, position )
    }

    /**
     * Attempt to put [mediaItem] into `Song` table and map it to [Album].
     *
     * [mediaItem] is first inserted to database with [insertIgnore]
     * then [album] to ensure to be existed in the database  before
     * attempting to map two together.
     *
     * @param album to map
     * @param mediaItem song to map
     * @param position of song in album, **default** or `-1` results in
     * database puts song to next available position in map
     */
    fun mapIgnore( album: Album, mediaItem: MediaItem, position: Int = -1 ) =
        mapIgnore( album, mediaItem.asSong, position )


    /**
     * Attempt to map [Song] to [Artist].
     *
     * [songs] and [artist] are ensured to be existed in
     * the database before attempting to map two together.
     *
     * @param artist to map
     * @param songs to map
     */
    fun mapIgnore( artist: Artist, vararg songs: Song ) {
        if( songs.isEmpty() ) return

        artistTable.insertIgnore( artist )
        songs.forEach {
            songTable.insertIgnore( it )
            songArtistMapTable.insertIgnore(
                SongArtistMap(it.id, artist.id)
            )
        }
    }

    /**
     * Attempt to put [mediaItems] into `Song` table and map it to [Album].
     *
     * [mediaItems] are first inserted to database with [insertIgnore]
     * then [artist] to ensure to be existed in the database  before
     * attempting to map two together.
     *
     * @param artist to map
     * @param mediaItems list of songs to map
     */
    fun mapIgnore( artist: Artist, vararg mediaItems: MediaItem ) =
        mapIgnore( artist, *mediaItems.map( MediaItem::asSong ).toTypedArray() )

    /**
     * Attempt to map [Song] to [Playlist].
     *
     * [songs] and [playlist] are ensured to be existed in
     * the database before attempting to map two together.
     *
     * @param playlist to map
     * @param songs to map
     */
    fun mapIgnore( playlist: Playlist, vararg songs: Song ) {
        if( songs.isEmpty() ) return

        /**
         * [Playlist] has its [Playlist.id] `autogenerated`, therefore,
         * it's unknown until it's inserted into database with [PlaylistTable.insert].
         *
         *
         */
        val pId =
            if( playlist.id > 0 ) {
                playlistTable.insertIgnore( playlist )
                playlist.id
            } else
                playlistTable.insert( playlist )

        songs.forEach {
            songTable.insertIgnore( it )
            songPlaylistMapTable.map( it.id, pId )
        }
    }

    /**
     * Attempt to put [mediaItems] into `Song` table and map it to [Playlist].
     *
     * [mediaItems] are first inserted to database with [insertIgnore]
     * then [playlist] to ensure to be existed in the database  before
     * attempting to map two together.
     *
     * @param playlist to map
     * @param mediaItems list of songs to map
     */
    fun mapIgnore( playlist: Playlist, vararg mediaItems: MediaItem ) =
        mapIgnore( playlist, *mediaItems.map( MediaItem::asSong ).toTypedArray() )

    /**
     * Commit statements in BULK. If anything goes wrong during the transaction,
     * other statements will be cancelled and reversed to preserve database's integrity.
     * [Read more](https://sqlite.org/lang_transaction.html)
     *
     * [asyncTransaction] runs all statements on non-blocking
     * thread to prevent UI from going unresponsive.
     *
     * ## Best use cases:
     * - Commit multiple write statements that require data integrity
     * - Processes that take longer time to complete
     *
     * > Do NOT use this to retrieve data from the database.
     * > Use [asyncQuery] to retrieve records.
     *
     * @param block of statements to write to database
     */
    fun asyncTransaction( retries: Int = 3, block: Database.() -> Unit ) =
        _internal.transactionExecutor.execute {
            var attempt = 0
            while (attempt < retries) {
                try {
                    this.block()
                    return@execute
                } catch (e: SQLiteDatabaseLockedException) {
                    attempt++
                    if (attempt >= retries) {
                        Timber.tag("Database").e(e, "Transaction FAILED after $retries attempts")
                        return@execute
                    }
                    Thread.sleep(200L * attempt)
                } catch (e: Exception) {
                    Timber.tag("Database").e(e, "asyncTransaction unexpected exception, aborting")
                    return@execute
                }
            }
        }

    /**
     * Suspending transaction that waits for the block to complete.
     * Use this when subsequent code depends on the transaction being committed.
     */
    suspend fun transaction( block: suspend Database.() -> Unit ) {
        val db = this
        _internal.withTransaction {
            db.block()
        }
    }


    /**
     * Access and retrieve records from database.
     *
     * [asyncQuery] runs all statements asynchronously to
     * prevent blocking UI thread from going unresponsive.
     *
     * ## Best use cases:
     * - Background data retrieval
     * - Non-immediate UI component update (i.e. count number of songs)
     *
     * > Do NOT use this method to write data to database
     * > because it offers no fail-safe during write.
     * > Use [asyncTransaction] to modify database.
     *
     * @param block of statements to retrieve data from database
     */
    fun asyncQuery( block: Database.() -> Unit ) =
        _internal.queryExecutor.execute {
            this.block()
        }

    fun checkpoint() = _internal.query( SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)") )
                                     .use {
                                         if( it.moveToFirst() ) it.getInt( 0 ) else -1
                                     }

    fun close() = _internal.close()
    fun artistSongs(browseId: String): Flow<List<Song>> {
        return songTable.artistSongs(browseId)
    }

    fun updateArtistId(oldId: String, newId: String) = asyncTransaction {
        val oldEntity = artistTable.findByIdDirect(oldId) ?: return@asyncTransaction
        artistTable.insertIgnore(oldEntity.copy(id = newId))
        songArtistMapTable.updateArtistId(oldId, newId)
        artistTable.deleteById(oldId)
    }

    fun updateAlbumId(oldId: String, newId: String) = asyncTransaction {
        val oldEntity = albumTable.findByIdDirect(oldId) ?: return@asyncTransaction
        albumTable.insertIgnore(oldEntity.copy(id = newId))
        songAlbumMapTable.updateAlbumId(oldId, newId)
        albumTable.deleteById(oldId)
    }
}

@androidx.room.Database(
    entities = [
        Song::class,
        SongPlaylistMap::class,
        Playlist::class,
        Artist::class,
        SongArtistMap::class,
        Album::class,
        SongAlbumMap::class,
        SearchQuery::class,
        QueuedMediaItem::class,
        Format::class,
        Event::class,
        Lyrics::class,
        ImportSong::class,
    ],
    views = [
        SortedSongPlaylistMap::class
    ],
    version = 35,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = From3To4Migration::class),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = From7To8Migration::class),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 11, to = 12, spec = From11To12Migration::class),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21, spec = From20To21Migration::class),
        AutoMigration(from = 21, to = 22, spec = From21To22Migration::class),
        AutoMigration(from = 28, to = 29),
    ],
)
@TypeConverters(Converters::class)
abstract class DatabaseInitializer protected constructor() : RoomDatabase() {
    abstract val albumTable: AlbumTable
    abstract val artistTable: ArtistTable
    abstract val eventTable: EventTable
    abstract val formatTable: FormatTable
    abstract val lyricsTable: LyricsTable
    abstract val playlistTable: PlaylistTable
    abstract val queueTable: QueuedMediaItemTable
    abstract val searchQueryTable: SearchQueryTable
    abstract val songAlbumMapTable: SongAlbumMapTable
    abstract val songArtistMapTable: SongArtistMapTable
    abstract val songPlaylistMapTable: SongPlaylistMapTable
    abstract val songTable: SongTable
    abstract val importSongTable: ImportSongTable

    companion object {
        val Instance: DatabaseInitializer by lazy {
            val db = Room.databaseBuilder(
                    context = appContext(),
                    klass = DatabaseInitializer::class.java,
                    name = Database.FILE_NAME
                )
                .addMigrations(
                    From8To9Migration(),
                    From10To11Migration(),
                    From14To15Migration(),
                    From22To23Migration(),
                    From23To24Migration(),
                    From24To25Migration(),
                    From25To26Migration(),
                    From26To27Migration(),
                    From27To28Migration(),
                    From29To30Migration(),
                    From30To31Migration(),
                    From31To32Migration(),
                    From32To33Migration(),
                    From33To34Migration(),
                    From34To35Migration(),
                    From35To36Migration
                )
                .build()
            
            db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(
                "Song", "Playlist", "SongPlaylistMap", "Album", "Artist", "SongArtistMap", "SongAlbumMap"
            ) {
                override fun onInvalidated(tables: Set<String>) {
                    BackupManager.triggerOnChangeBackup(appContext())
                }
            })
            
            db
        }
    }
}





