package app.n_zik.android.core.database
import app.n_zik.android.appContext
import app.n_zik.android.utils.coroutines.NzikDispatchers

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
import app.n_zik.android.core.database.migration.From36To37Migration
import app.n_zik.android.core.database.migration.From37To38Migration
import app.n_zik.android.core.database.migration.From38To39Migration
import app.n_zik.android.core.database.migration.From39To40Migration
import app.n_zik.android.core.database.migration.From40To41Migration
import app.n_zik.android.core.database.migration.From41To42Migration
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

        // One author entry = one artist: the channel's browseId is the identity and
        // the row name converges to the channel page name (background fetch,
        // retainIfModified). The previous split of a single entry's display name
        // (parseArtists: "Bigflo & OLi" -> "Bigflo" + "OLi") lost the browseId,
        // failed the completeness check below (split names no longer equal the
        // entry names) and fell back to the add-only path with one online search
        // + junk row per split part on every playback (device captures
        // 2026-09-26 12:11 / 12:18: added=2 -> dropped=2 loop). Entry names are
        // kept whole; the channel page stays the table of truth for the name.
        val artistEntries = ArtistMappingReconcile.parseAuthorEntries( songItem.authors )
        val artistNames = artistEntries.map { it.first }
        val artistDataList = mutableListOf<Triple<String, String?, Artist?>>() // name -> browseId -> existing DB artist

        for ((artistName, browseId) in artistEntries) {
            if (browseId != null) {
                // The row name is owned by the channel page: an existing row keeps
                // its stored name (page name or user-modified), a new row starts
                // from the entry name and converges to the page name at the next
                // background fetch.
                val artist = artistTable.findByIdDirect(browseId) ?: Artist(id = browseId, name = artistName)
                artistDataList.add(Triple(artistName, browseId, artist))
            } else {
                val dbArtistByName = artistTable.findByNameDirect(artistName)
                artistDataList.add(Triple(artistName, null, dbArtistByName))
            }
        }

        val dbAlbum = songItem.album?.endpoint?.browseId?.let { albumTable.findByIdDirect(it) }

        // Phase 2: Network calls (NO lock held)
        val artistsToUpsert = mutableListOf<Artist>()
        val artistsToMap = mutableListOf<Artist>()
        for ((artistName, browseId, existingArtist) in artistDataList) {
            if (existingArtist != null) {
                if (browseId == null) {
                    // No channel identity: keep syncing the row name from the fresh
                    // YTM response (previous behavior for name-only rows).
                    val retainedName = PropUtils.retainIfModified(existingArtist.name, artistName)
                    if (existingArtist.name != retainedName) {
                        val updatedArtist = existingArtist.copy(name = retainedName)
                        artistsToUpsert.add(updatedArtist)
                        artistsToMap.add(updatedArtist)
                    } else {
                        artistsToMap.add(existingArtist)
                    }
                } else {
                    // Channel row: the stored name is the page name (or a
                    // user-modified name) - the raw entry name must not clobber it.
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
            // The stored artist list follows the resolved names: the row name when
            // the channel row already exists (page name / user-modified - the table
            // of truth), the entry name for rows that don't exist yet (they
            // converge to the page name at the next background fetch). The split
            // join ("Bigflo, OLi") used to feed the in-app "two artists" view.
            val fetchedArtistsText = artistDataList
                .map { entry -> entry.third?.name ?: entry.first }
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
            val finalArtistsText = when {
                fetchedArtistsText.isNullOrBlank() && !dbSong?.artistsText.isNullOrBlank() -> dbSong.artistsText
                else -> PropUtils.retainIfModified(dbSong?.artistsText, fetchedArtistsText)
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
            // Reconcile the artist mapping from this fresh YTM response when it is
            // complete (every parsed artist is backed by a browse ID): the map must
            // reflect the latest playback context, not the union of every context
            // ever seen. A names-only/partial list keeps the legacy add-only path.
            if (ArtistMappingReconcile.isCompleteAuthorList(artistNames, songItem.authors)) {
                logReconcileDrops(songArtistMapTable, artistTable, song.id, finalSong.title)
                val reconcileArtists = artistDataList.mapNotNull { it.third }
                val dropped = reconcileArtistLinks( song.id, reconcileArtists )
                Timber.tag("Database").d(
                    "upsert RECONCILE song=%s dropped=%d latestList=%d artists",
                    song.id, dropped, artistNames.size
                )
            } else {
                // Only path that leaves stale links behind: an incomplete author list must
                // never erase the mapping it cannot rewrite, so legacy add-only keeps the
                // previous context's rows — the startup sweep used to catch them silently.
                Timber.tag("Database").d(
                    "upsert ADD-ONLY song=%s names=%d authors=%d added=%d (stale links may remain until the next reconcile)",
                    song.id, artistNames.size, songItem.authors?.size ?: 0, artistsToMap.size
                )
                artistsToMap.forEach { mapIgnore(it, song) }
            }

            // Upsert album
            songItem.album?.let {
                val browseId = it.endpoint?.browseId ?: return@let
                val fetchedAlbum = if (dbAlbum != null) {
                    dbAlbum.copy(
                        title = PropUtils.retainIfModified(dbAlbum.title, it.name),
                        thumbnailUrl = PropUtils.retainIfModified(dbAlbum.thumbnailUrl, song.thumbnailUrl),
                        authorsText = PropUtils.retainIfModified(dbAlbum.authorsText, artistNames.joinToString(", ").takeIf { it.isNotBlank() })
                    )
                } else {
                    Album(
                        id = browseId,
                        title = it.name,
                        thumbnailUrl = song.thumbnailUrl,
                        authorsText = artistNames.joinToString(", ").takeIf { it.isNotBlank() }
                    )
                }
                if (dbAlbum != fetchedAlbum) {
                    albumTable.upsert(fetchedAlbum)
                }

                if (fetchedAlbum.year.isNullOrBlank()) {
                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                        try {
                            Innertube.albumPage(browseId = browseId)
                                ?.getOrNull()
                                ?.let { albumPage ->
                                    if (!albumPage.year.isNullOrBlank()) {
                                        val updatedAlbum = fetchedAlbum.copy(
                                            title = PropUtils.retainIfModified(fetchedAlbum.title, albumPage.title.takeIf { !it.isNullOrBlank() }) ?: fetchedAlbum.title,
                                            thumbnailUrl = PropUtils.retainIfModified(fetchedAlbum.thumbnailUrl, albumPage.thumbnail?.url.takeIf { !it.isNullOrBlank() }) ?: fetchedAlbum.thumbnailUrl,
                                            year = albumPage.year,
                                            authorsText = PropUtils.retainIfModified(fetchedAlbum.authorsText, albumPage.authors.artistEntryNames().joinToString(", ").takeIf { it.isNotBlank() }) ?: fetchedAlbum.authorsText,
                                            shareUrl = PropUtils.retainIfModified(fetchedAlbum.shareUrl, albumPage.url) ?: fetchedAlbum.shareUrl,
                                            timestamp = System.currentTimeMillis()
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
     * Replaces a song's artist links with [artists]: every existing
     * [SongArtistMap] row of [songId] is deleted, then the given artists are
     * upserted and re-mapped to the song.
     *
     * **Contract - complete list only:** the caller must only invoke this when the
     * fresh author list is complete, i.e. it passes
     * [ArtistMappingReconcile.isCompleteAuthorList] (every parsed name backed by a
     * browse ID). A names-only/partial list must never erase the mapping it cannot
     * rewrite - gate on [ArtistMappingReconcile.isCompleteAuthorList] before calling.
     *
     * Transaction semantics depend on the caller: [upsert] calls it from
     * inside a Room transaction, so the delete + re-insert is atomic there.
     * The song update dialog calls it from `asyncTransaction` (an executor
     * thread with NO surrounding Room transaction): each statement autocommits
     * on its own, so the delete + re-insert is best-effort there - a failure
     * between the delete and the re-insert would briefly leave the song
     * without artist links until the next reconcile heals it. No logging in
     * here - the caller logs.
     *
     * @param songId song side of the replaced links
     * @param artists the complete fresh artist list the links are rebuilt from
     * @return number of stale links deleted before the re-insert
     */
    fun reconcileArtistLinks( songId: String, artists: List<Artist> ): Int {
        val dropped = songArtistMapTable.deleteBySongId( songId )
        artistTable.upsert( artists )
        artists.forEach { artist ->
            songArtistMapTable.insertIgnore( SongArtistMap( songId, artist.id ) )
        }
        return dropped
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
                dbAlbum.copy(
                    title = albumTitle.takeIf { !it.isNullOrBlank() } ?: dbAlbum.title,
                    thumbnailUrl = artworkUri.takeIf { !it.isNullOrBlank() } ?: dbAlbum.thumbnailUrl,
                    year = year.takeIf { !it.isNullOrBlank() } ?: dbAlbum.year,
                    authorsText = artist.takeIf { !it.isNullOrBlank() } ?: dbAlbum.authorsText
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
                NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                    try {
                        Innertube.albumPage(browseId = albumId)
                            ?.getOrNull()
                            ?.let { albumPage ->
                                if (!albumPage.year.isNullOrBlank()) {
                                    val updatedAlbum = mergedAlbum.copy(
                                        title = PropUtils.retainIfModified(mergedAlbum.title, albumPage.title.takeIf { !it.isNullOrBlank() }) ?: mergedAlbum.title,
                                        thumbnailUrl = PropUtils.retainIfModified(mergedAlbum.thumbnailUrl, albumPage.thumbnail?.url.takeIf { !it.isNullOrBlank() }) ?: mergedAlbum.thumbnailUrl,
                                        year = albumPage.year,
                                        authorsText = PropUtils.retainIfModified(mergedAlbum.authorsText, albumPage.authors.artistEntryNames().joinToString(", ").takeIf { it.isNotBlank() }) ?: mergedAlbum.authorsText,
                                        shareUrl = PropUtils.retainIfModified(mergedAlbum.shareUrl, albumPage.url) ?: mergedAlbum.shareUrl,
                                        timestamp = System.currentTimeMillis()
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

        // Names of the artist rows the mapping below actually points to - the alignment
        // target for artistsText (see the alignment block at the end of this function).
        val resolvedArtistNames = mutableListOf<String>()

        if (artistsIds.isNotEmpty()) {
            // Reconcile before re-mapping: the mapping must reflect the latest YTM author
            // list, not the union of every list ever seen. YTM author lists vary by
            // playback context (a channel playlist/radio credits the channel as an
            // author), so insert-only accumulation pollutes SongArtistMap over time
            // (bug: a channel-derived artist row collected hundreds of plays of
            // unrelated songs and ranked as a top artist in the Rewind deck).
            // Guarded: only an existing song has stale rows to drop, and only the
            // id-bearing path reconciles — a names-only/local re-insert must never
            // wipe existing mappings.
            if (dbSong != null) {
                logReconcileDrops(songArtistMapTable, artistTable, cleanSongId, mergedSong.title)
                val dropped = songArtistMapTable.deleteBySongId(cleanSongId)
                Timber.tag("Database").d(
                    "insertIgnore RECONCILE ids song=%s dropped=%d latestList=%s",
                    cleanSongId, dropped, artistsIds
                )
            }
            // Normal case: zip names with IDs. `resolvedArtistNames` tracks the name of
            // the row each link actually points to, because the startup sweep (DbCleanup)
            // compares that stored name against artistsText - not the raw context name,
            // which can be a different spelling (JP vs romaji) of the same row.
            artistsNames.zip(artistsIds).forEach { (name, id) ->
                val existingArtist = artistTable.findByNameDirect(name)
                val targetArtistId = if (existingArtist != null) {
                    // Row resolved by name: its stored name IS the context name.
                    resolvedArtistNames += name
                    existingArtist.id
                } else {
                    // insertIgnore is a no-op when the row already exists under another
                    // spelling - it does NOT rename it - so read the stored name back:
                    // the link points to that existing row.
                    artistTable.insertIgnore(Artist(id, name))
                    resolvedArtistNames += artistTable.findByIdDirect(id)?.name ?: name
                    id
                }
                songArtistMapTable.insertIgnore(SongArtistMap(cleanSongId, targetArtistId))
            }
        } else if (artistsNames.isNotEmpty()) {
            // A names-only author list (search results often carry names without
            // browse IDs) is still the latest authoritative answer for this
            // context, so the same reconcile rule applies: stale rows from other
            // contexts must not survive it. Guarded like above: existing song only.
            // Rows are resolved by name here, so the stored row name equals the
            // context name for every link written synchronously (the async background
            // search prefers an exact-name match) - the context names are the target.
            resolvedArtistNames += artistsNames
            if (dbSong != null) {
                logReconcileDrops(songArtistMapTable, artistTable, cleanSongId, mergedSong.title)
                val dropped = songArtistMapTable.deleteBySongId(cleanSongId)
                Timber.tag("Database").d(
                    "insertIgnore RECONCILE names song=%s dropped=%d latestList=%s",
                    cleanSongId, dropped, artistsNames
                )
            }
            // No browse IDs but we have names: try database by name first
            artistsNames.forEach { name ->
                val existingArtist = artistTable.findByNameDirect(name)
                if (existingArtist != null) {
                    songArtistMapTable.insertIgnore(SongArtistMap(cleanSongId, existingArtist.id))
                } else if (autoFix) {
                    // Search online for the artist in background (non-blocking)
                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
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

        // Single source of truth for the artist display: once the mapping has been
        // (re)written from this context's author list, align artistsText to the names of
        // the rows those links actually point to. The startup sweep (DbCleanup) judges
        // links by name equality against artistsText, so the two must carry the same
        // names - aligning to the stored row names (not the raw context names, which can
        // be a different spelling of the same row) is what breaks the
        // "map on play, drop on restart" loop. `modified:` values are never overwritten,
        // and an already-aligned value skips the write.
        if (resolvedArtistNames.isNotEmpty()) {
            val alignedArtistsText =
                PropUtils.retainIfModified(mergedSong.artistsText, resolvedArtistNames.joinToString(", "))
            if (alignedArtistsText != mergedSong.artistsText) {
                songTable.upsert(mergedSong.copy(artistsText = alignedArtistsText))
            }
        }
    }

    /**
     * Diagnostic helper for reconcile cleanup: right before a reconciliation
     * drops a song's stale artist links, log one line per about-to-be-dropped
     * pair (song id + title, artist name) so the drop can be read back from
     * logcat. Pure logging — no behavior change. Runs only on the delete
     * paths, never on the add-only one.
     *
     * @param mapTable source of the stale pairs (targeted by [songId])
     * @param artistTable name lookup for each dropped pair
     * @param songId song side of the dropped pairs
     * @param songTitle title of the song for the log line
     *
     * A missing artist row (orphan link) is logged as `artist="null"`.
     */
    internal fun logReconcileDrops(
        mapTable: SongArtistMapTable,
        artistTable: ArtistTable,
        songId: String,
        songTitle: String,
    ) {
        for (pair in mapTable.pairsBySongIdDirect(songId)) {
            Timber.tag("Database").d(
                "reconcile dropped link: song=%s \"%s\" artist=\"%s\"",
                songId, songTitle, artistTable.findByIdDirect(pair.artistId)?.name
            )
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

    /**
     * True once [close] has run. The database cannot be used again until the process restarts,
     * so UI that would be recomposed over it (e.g. an activity recreation) must check this first.
     */
    @Volatile
    var isClosed: Boolean = false
        private set

    fun close() {
        isClosed = true
        _internal.close()
    }

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
    version = 42,
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
                .setQueryExecutor(NzikDispatchers.ROOM_QUERY_EXECUTOR)
                .setTransactionExecutor(NzikDispatchers.ROOM_TX_EXECUTOR)
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
                    From35To36Migration,
                    From36To37Migration,
                    From37To38Migration,
                    From38To39Migration,
                    From39To40Migration,
                    From40To41Migration,
                    From41To42Migration
                )
                .fallbackToDestructiveMigration()
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





