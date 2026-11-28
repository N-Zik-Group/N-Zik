package app.n_zik.android.core.database
import app.n_zik.android.core.ondevice.isDeviceAlbumId
import app.n_zik.android.core.ondevice.isDeviceArtistId
import app.n_zik.android.models.Info
import app.n_zik.android.utils.usableTrackArtistName
import app.n_zik.android.utils.resolveTrackArtistName
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.utils.playlist.PlaylistInsertionPreferences
import app.n_zik.android.utils.playlist.PlaylistInsertionPosition
import app.n_zik.android.utils.playlist.supportsInsertionPosition
import app.n_zik.android.appContext
import app.n_zik.android.utils.coroutines.NzikDispatchers

import app.n_zik.android.Dependencies

import androidx.annotation.OptIn
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMapNotNull
import androidx.compose.ui.util.fastZip
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.room.AutoMigration
import androidx.room.Room
import app.n_zik.android.requireLibraryAccess
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.albumPage
import app.n_zik.android.cleanPrefix
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Event
import app.n_zik.android.models.Format
import app.n_zik.android.models.Lyrics
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.QueuedMediaItem
import app.n_zik.android.models.Song
import app.n_zik.android.models.SongAlbumMap
import app.n_zik.android.models.SongArtistMap
import app.n_zik.android.models.SongPlaylistMap
import app.n_zik.android.models.SortedSongPlaylistMap
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.durationTextToMillis
import app.n_zik.android.utils.ARTIST_LINKED_NAMES_EXTRA
import app.n_zik.android.utils.readMediaItemArtistLinks
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.core.database.AlbumTable
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Converters
import app.n_zik.android.core.database.EventTable
import app.n_zik.android.core.database.FormatTable
import app.n_zik.android.core.database.LyricsTable
import app.n_zik.android.core.database.PlaylistTable
import app.n_zik.android.core.database.QueuedMediaItemTable
import app.n_zik.android.core.database.SongAlbumMapTable
import app.n_zik.android.core.database.SongArtistMapTable
import app.n_zik.android.core.database.SongPlaylistMapTable
import app.n_zik.android.core.database.SongTable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
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
import app.n_zik.android.core.database.migration.From42To43Migration
import app.n_zik.android.core.database.migration.From43To44Migration
import app.n_zik.android.core.utils.PropUtils
import app.n_zik.android.core.backup.BackupManager
import app.n_zik.android.playback.services.PersistentQueueArtwork
import androidx.room.InvalidationTracker
import timber.log.Timber
import android.database.sqlite.SQLiteDatabaseLockedException
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.isUserEditedPlaybackAlbumId

object Database {
    /**
     * Database file name for the active profile.
     *
     * Resolved on first access and cached for the process: when no application
     * is initialized (unit tests that don't boot it, non-main processes) there
     * is no profile preference to read, so the name resolves to the default
     * profile file.
     */
    val FILE_NAME: String by lazy {
        fileNameForProfile(if (Dependencies.isInitialized) getActiveProfile(appContext()) else "default")
    }

    /** Database file name for [profile]: `data.db` for the default profile, `data_<profile>.db` otherwise. */
    internal fun fileNameForProfile(profile: String): String =
        "data${if (profile != "default") "_$profile" else ""}.db"

    private val _internal: DatabaseInitializer
        get() = DatabaseInitializer.Instance

    internal fun observeSavedPlaylistChanges(): Flow<Set<String>> =
        _internal.invalidationTracker.createFlow("Playlist", "SongPlaylistMap", emitInitialState = false)

    // Contract §10.3 (bridge, since 1.7.3, feature `library.live`): the library DAOs
    // below are transparent proxies that notify [libraryWriteNotifier] with the changed
    // entity per write, whatever the origin (phone UI, client §10.2 writes, imports,
    // play counters). The album/artist map tables are wrapped too: the client-visible
    // `trackCount` is derived from their rows, so a standalone map write (match,
    // unmatch, redirect during playback) changes it without touching the main table.
    // The other DAOs (queue, lyrics, search, import) are left unwrapped: the queue
    // already has its `queueChanged` delta; the rest is not library listings (§10.3).

    /**
     * Contract §10.3 (since 1.7.3, feature `library.live`): invoked with the changed entity —
     * `"songs"`, `"albums"`, `"artists"` or `"playlists"` — per write to a library table.
     * `null` while no bridge listener is active; the proxies only perform this null check.
     */
    @Volatile
    var libraryWriteNotifier: ((kinds: List<String>) -> Unit)? = null

    /** The changed entity per wrapped library DAO (contract §10.3 `kind`); exposed for tests. */
    internal val libraryTableKinds: Map<Class<*>, List<String>> = mapOf(
        SongTable::class.java to listOf("songs"),
        AlbumTable::class.java to listOf("albums"),
        ArtistTable::class.java to listOf("artists"),
        // "songs" too: a playlist delete cascades its SongPlaylistMap rows (`Track.inPlaylist`, since 1.12.0)
        PlaylistTable::class.java to listOf("playlists", "songs"),
        FormatTable::class.java to listOf("songs"),
        // "songs" too: the open playlist's track list is a songs-family detail list
        SongPlaylistMapTable::class.java to listOf("playlists", "songs"),
        SongAlbumMapTable::class.java to listOf("albums"),
        SongArtistMapTable::class.java to listOf("artists"),
        EventTable::class.java to listOf("songs", "artists", "albums"),
    )

    /**
     * The write methods per library DAO: a call to a name in the set emits, a Flow-returning
     * method never does (writes don't return Flows; the `likeState(id)`-style Flow reads share
     * a name with their write overloads). Exposed for tests.
     */
    internal val libraryTableWrites: Map<Class<*>, Set<String>> = mapOf(
        SongTable::class.java to setOf(
            "insertIgnore", "updateReplace", "updateId", "upsert", "delete",
            "rotateLikeState", "clearAllDisliked", "toggleDislike", "toggleLike", "likeState",
            "updateTitle", "updateCover", "updateCoverForAlbum", "updatePosition",
            "updateArtists", "updateTotalPlayTime", "deleteByIds", "deleteAll",
            "unlikeByIds", "unlikeAll", "resetPlayTimeByIds", "clearHiddenSongs",
        ),
        AlbumTable::class.java to setOf(
            "insertIgnore", "updateReplace", "upsert", "updateMetadata", "insertMetadata",
            "toggleBookmark", "bookmarkState", "likeState", "rotateLikeState", "resetFetchTtl",
            "toggleDislike", "clearAllDisliked", "updateCover", "fillMissingCover", "updateAuthors", "updateTitle",
            "updatePosition", "deleteOrphaned", "deleteById",
        ),
        ArtistTable::class.java to setOf(
            "insertIgnore", "upsert", "updateMetadata", "insertMetadata", "update", "fillMissingCover",
            "toggleFollow", "followState", "rotateLikeState", "resetFetchTtl", "toggleBookmark",
            "toggleDislike", "clearAllDisliked", "updatePosition", "deleteOrphaned", "deleteById",
            "clearYouTubeArtistProvenance", "markYouTubeArtistImported",
        ),
        PlaylistTable::class.java to setOf(
            "insert", "insertIgnore", "upsert", "update", "delete",
            "togglePin", "pinState", "toggleAutoSync", "updatePosition", "updateBrowseId",
        ),
        FormatTable::class.java to setOf(
            "deleteBySongId", "insertIgnore", "upsert", "updateContentLengthOf", "updateDownloadQuality",
        ),
        SongPlaylistMapTable::class.java to setOf(
            "deleteBySongId", "clear", "clearGhostMaps", "updateReplace", "shufflePositions",
            "move", "updatePosition", "map", "mapAtPosition", "updateSongId",
        ),
        SongAlbumMapTable::class.java to setOf(
            "upsert", "clear", "clearGhostMaps", "map",
            "clearConflictingPairs", "updateSongId", "updateAlbumId", "deleteBySongId", "deletePairDirect",
        ),
        SongArtistMapTable::class.java to setOf(
            "insertIgnore", "clearGhostMaps", "clearConflictingPairs", "updateSongId",
            "updateArtistId", "dropLinksAlreadyOn", "deleteBySongId", "deletePairDirect",
        ),
        EventTable::class.java to setOf(
            "insertIgnore", "deleteAll", "updateSongId", "deleteBySongIds", "deleteBySongId",
        ),
    )

    private class LibraryTransactionWrites : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<LibraryTransactionWrites>

        private val kinds = linkedSetOf<String>()

        fun append(values: List<String>) = synchronized(kinds) { kinds.addAll(values) }
        fun snapshot(): List<String> = synchronized(kinds) { kinds.toList() }
    }

    private fun notifyLibraryWrites(kinds: List<String>, context: CoroutineContext? = null) {
        if (kinds.isEmpty()) return
        val pending = context?.get(LibraryTransactionWrites)
        if (pending != null) {
            pending.append(kinds)
        } else {
            runCatching { libraryWriteNotifier?.invoke(kinds) }.onFailure {
                Timber.tag("Database").w("Unable to publish library write (%s)", it.javaClass.simpleName)
            }
        }
    }

    /**
     * Delegates DAO calls and publishes library writes once their SQL succeeds. Inside [transaction]
     * the changes are accumulated and published after the outer transaction commits.
     */
    internal fun <T : Any> libraryWrites(
        dao: T,
        daoClass: Class<T>,
        kinds: List<String> = libraryTableKinds[daoClass].orEmpty(),
        writes: Set<String> = libraryTableWrites[daoClass].orEmpty(),
    ): T {
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(daoClass.classLoader, arrayOf(daoClass)) { _, method, args ->
            val isWrite = method.name in writes && method.returnType != Flow::class.java
            val continuation = if (isWrite) args?.lastOrNull() as? Continuation<Any?> else null
            try {
                if (continuation != null) {
                    val suspendArgs = checkNotNull(args)
                    val observingContinuation = object : Continuation<Any?> {
                        override val context: CoroutineContext get() = continuation.context

                        override fun resumeWith(result: Result<Any?>) {
                            if (result.isSuccess) notifyLibraryWrites(kinds, context)
                            continuation.resumeWith(result)
                        }
                    }
                    val result = method.invoke(dao, *suspendArgs.copyOf(suspendArgs.size - 1), observingContinuation)
                    if (result !== COROUTINE_SUSPENDED) notifyLibraryWrites(kinds, continuation.context)
                    result
                } else {
                    val result = if (args == null) method.invoke(dao) else method.invoke(dao, *args)
                    if (isWrite) notifyLibraryWrites(kinds)
                    result
                }
            } catch (error: InvocationTargetException) {
                throw error.targetException
            }
        } as T
    }

    /** One library DAO behind its write proxy, following the underlying instance: in production the Room DAO is process-stable (one proxy), and a test that swaps [DatabaseInitializer.Instance] for a fresh in-memory database gets a fresh proxy — a stale DAO (of a closed database) is never called. */
    private class TrackedDao<T : Any>(
        private val dao: () -> T,
        private val daoClass: Class<T>,
    ) {
        private var underlying: Any? = null
        private var wrapped: Any? = null

        @Suppress("UNCHECKED_CAST")
        fun get(): T {
            val current = dao()
            val cached = wrapped
            if (cached !== null && underlying === current) return cached as T
            val proxy = libraryWrites(current, daoClass)
            underlying = current
            wrapped = proxy
            return proxy
        }
    }

    private val songTableDao = TrackedDao({ _internal.songTable }, SongTable::class.java)
    val songTable: SongTable
        get() = songTableDao.get()
    private val albumTableDao = TrackedDao({ _internal.albumTable }, AlbumTable::class.java)
    val albumTable: AlbumTable
        get() = albumTableDao.get()
    private val artistTableDao = TrackedDao({ _internal.artistTable }, ArtistTable::class.java)
    val artistTable: ArtistTable
        get() = artistTableDao.get()
    private val eventTableDao = TrackedDao({ _internal.eventTable }, EventTable::class.java)
    val eventTable: EventTable
        get() = eventTableDao.get()
    private val formatTableDao = TrackedDao({ _internal.formatTable }, FormatTable::class.java)
    val formatTable: FormatTable
        get() = formatTableDao.get()
    val lyricsTable: LyricsTable
        get() = _internal.lyricsTable
    private val playlistTableDao = TrackedDao({ _internal.playlistTable }, PlaylistTable::class.java)
    val playlistTable: PlaylistTable
        get() = playlistTableDao.get()
    val queueTable: QueuedMediaItemTable
        get() = _internal.queueTable
    private val songAlbumMapTableDao = TrackedDao({ _internal.songAlbumMapTable }, SongAlbumMapTable::class.java)
    val songAlbumMapTable: SongAlbumMapTable
        get() = songAlbumMapTableDao.get()
    private val songArtistMapTableDao = TrackedDao({ _internal.songArtistMapTable }, SongArtistMapTable::class.java)
    val songArtistMapTable: SongArtistMapTable
        get() = songArtistMapTableDao.get()
    private val songPlaylistMapTableDao = TrackedDao({ _internal.songPlaylistMapTable }, SongPlaylistMapTable::class.java)
    val songPlaylistMapTable: SongPlaylistMapTable
        get() = songPlaylistMapTableDao.get()
    val importSongTable: ImportSongTable
        get() = _internal.importSongTable

    suspend fun upsert( songItem: Innertube.SongItem ) {
        val song = songItem.asSong
        if (song.id.startsWith(LOCAL_KEY_PREFIX)) return

        val authorEntries = ArtistMappingReconcile.parseAuthorEntries(songItem.authors, includeIdOnly = true)
        val artistNames = authorEntries.mapNotNull { usableTrackArtistName(it.first) }

        transaction {
            val dbSong = songTable.findByIdDirect(song.id)
            val storedArtists = if (dbSong == null) emptyList() else artistTable.findBySongIdDirect(song.id)
            val customLinks = storedArtists.filter(::isManuallyEditedArtistLink)
            val credits = authorEntries.map { (name, browseId) ->
                val artist = browseId?.takeUnless { it.isBlank() || isDeviceArtistId(it) }?.let { id ->
                    manualArtistOverrideFor(song.id, id, customLinks) ?: artistTable.findByIdDirect(id)
                        ?: usableTrackArtistName(name)?.let { Artist(id = id, name = it) }
                }
                (usableTrackArtistName(artist?.name) ?: usableTrackArtistName(name)) to artist
            }
            val incomingArtistText = credits.mapNotNull { it.first }.joinToString(", ").takeIf(String::isNotBlank)
            val incomingArtists = credits.mapNotNull { it.second }
            val complete = ArtistMappingReconcile.isCompleteAuthorList(artistNames, songItem.authors) &&
                credits.all { it.second != null }
            val finalTitle = when {
                song.title.isNullOrBlank() && !dbSong?.title.isNullOrBlank() -> dbSong.title
                else -> PropUtils.retainIfModified(dbSong?.title, song.title)
            }
            val storedText = dbSong?.artistsText
            val resolvedArtistsText = if (complete) {
                incomingArtistText
            } else {
                val linked = (storedArtists + incomingArtists).distinctBy(Artist::id)
                    .filterNot { isDeviceArtistId(it.id) }.map { Info(it.id, it.name) }
                resolveTrackArtistName(storedText, incomingArtistText, linked, "Unknown Artist")
            }
            val finalArtistsText = PropUtils.retainIfModified(storedText, resolvedArtistsText ?: storedText)
            val finalSong = Song(
                id = song.id,
                title = finalTitle.orEmpty(),
                artistsText = finalArtistsText ?: "",
                durationText = PropUtils.retainIfModified(
                    dbSong?.durationText,
                    song.durationText?.takeIf { durationTextToMillis(it) > 0 } ?: dbSong?.durationText,
                ),
                thumbnailUrl = PropUtils.retainIfModified(dbSong?.thumbnailUrl, song.thumbnailUrl),
                likedAt = dbSong?.likedAt,
                totalPlayTimeMs = dbSong?.totalPlayTimeMs ?: 0,
                position = dbSong?.position ?: -1,
                isYoutubeSong = dbSong?.isYoutubeSong ?: song.isYoutubeSong,
            ).apply { playCount = dbSong?.playCount ?: song.playCount }
            if (dbSong != finalSong) {
                songTable.upsert(finalSong)
            }

            if (complete) {
                logReconcileDrops(songArtistMapTable, artistTable, song.id, finalSong.title)
                val dropped = reconcileArtistLinks(song.id, incomingArtists)
                Timber.tag("Database").d("Artist credits reconciled for %s (%d previous links)", song.id, dropped)
            } else {
                incomingArtists.distinctBy(Artist::id).forEach { mapIgnore(it, finalSong) }
            }

            // Upsert album
            songItem.album?.let {
                val browseId = it.endpoint?.browseId?.takeUnless(::isDeviceAlbumId) ?: return@let
                if (findEditedAlbumLink(song.id, browseId) != null) return@let
                val dbAlbum = albumTable.findByIdDirect(browseId)
                val trackArtists = artistNames.joinToString(", ").takeIf { it.isNotBlank() }
                val fetchedAlbum = if (dbAlbum != null) {
                    dbAlbum.copy(
                        title = PropUtils.retainIfModified(dbAlbum.title, it.name),
                        thumbnailUrl = PropUtils.retainIfModified(dbAlbum.thumbnailUrl, song.thumbnailUrl),
                        authorsText = albumCreditWithTrackFallback(dbAlbum.authorsText, trackArtists)
                    )
                } else {
                    Album(
                        id = browseId,
                        title = it.name,
                        thumbnailUrl = song.thumbnailUrl,
                        authorsText = trackArtists
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
     * **Contract — complete list only:** only invoke this when the fresh author
     * list is complete (every parsed name backed by a browse ID —
     * [ArtistMappingReconcile.isCompleteAuthorList]); a names-only/partial list
     * must never erase the mapping it cannot rewrite.
     *
     * Transaction semantics depend on the caller: [upsert] calls it inside a
     * Room transaction (atomic); the song update dialog calls it from
     * `asyncTransaction` with NO surrounding transaction, so each statement
     * autocommits — the delete + re-insert is best-effort there, and a failure
     * in between briefly leaves the song without artist links until the next
     * reconcile heals it. No logging in here — the caller logs.
     *
     * @param songId song side of the replaced links
     * @param artists the complete fresh artist list the links are rebuilt from
     * @return number of stale links deleted before the re-insert
     */
    suspend fun reconcileArtistLinks( songId: String, artists: List<Artist> ): Int {
        if (songId.startsWith(LOCAL_KEY_PREFIX)) return 0
        val onlineArtists = artists.filterNot { isDeviceArtistId(it.id) }
        if (artists.isNotEmpty() && onlineArtists.isEmpty()) return 0
        val customLinks = artistTable.findBySongIdDirect(songId).filter(::isManuallyEditedArtistLink)
        val applyArtists = onlineArtists.filter { candidate ->
            manualArtistOverrideFor(songId, candidate.id, customLinks) == null
        }
        val dropped = songArtistMapTable.deleteBySongId( songId )
        artistTable.upsert(applyArtists)
        (applyArtists + customLinks).distinctBy(Artist::id).forEach { artist ->
            songArtistMapTable.insertIgnore( SongArtistMap( songId, artist.id ) )
        }
        return dropped
    }

    /** Validates incoming and existing cover sources ahead of a batched insert. */
    @OptIn(UnstableApi::class)
    internal suspend fun prepareArtworkForInsert(mediaItem: MediaItem): Map<String?, String?> {
        val songId = mediaItem.mediaId.substringAfterLast('/')
        val albumId = mediaItem.mediaMetadata.extras?.getString("albumId")
        return listOf(
            mediaItem.asSong.thumbnailUrl,
            PersistentQueueArtwork.originalSource(mediaItem),
            songTable.findByIdDirect(songId)?.thumbnailUrl,
            albumId?.let { albumTable.findByIdDirect(it)?.thumbnailUrl },
        ).distinct().associateWith { validateLibraryArtwork(it, songId) }
    }

    internal suspend fun findEditedAlbumLink(songId: String, incomingAlbumId: String): Album? {
        if (incomingAlbumId.isBlank() || isDeviceAlbumId(incomingAlbumId) || isUserEditedPlaybackAlbumId(incomingAlbumId)) return null
        return songAlbumMapTable.findAlbumsOfDirect(songId).singleOrNull { album ->
            isUserEditedPlaybackAlbumId(album.id) && (
                album.id == "$MODIFIED_PREFIX$incomingAlbumId" ||
                    trackBrowseIdOrigin(songId, album.id) == incomingAlbumId
                )
        }
    }

    // A track's artist can fill missing album credits until that album's own page is read.
    private fun albumCreditWithTrackFallback(albumCredit: String?, trackCredit: String?): String? =
        albumCredit?.takeIf { it.isNotBlank() } ?: trackCredit?.takeIf { it.isNotBlank() }

    /** Inserts a media item and reconciles its album and artist links. */
    suspend fun insertIgnore(mediaItem: MediaItem, autoFix: Boolean = true) =
        insertIgnore(mediaItem, autoFix, null)

    @OptIn(UnstableApi::class)
    internal suspend fun insertIgnore(
        mediaItem: MediaItem,
        autoFix: Boolean,
        preparedArtwork: Map<String?, String?>?,
        reconcileExistingIdentity: Boolean = true,
    ) {
        val cleanSongId = mediaItem.mediaId.split("/").lastOrNull() ?: mediaItem.mediaId
        val newSong = mediaItem.asSong
        val dbSong = songTable.findByIdDirect(cleanSongId)
        val artwork = LibraryInsertArtwork(mediaItem, cleanSongId, newSong.thumbnailUrl, preparedArtwork)
        val mergedSong = if (dbSong != null) {
            val newTitle = newSong.title
            val finalTitle = when {
                !reconcileExistingIdentity && dbSong.title.isNotBlank() -> dbSong.title
                newTitle.isNullOrBlank() && !dbSong.title.isNullOrBlank() -> dbSong.title
                else -> PropUtils.retainIfModified(dbSong.title, newTitle)
            }
            val newArtistsText = newSong.artistsText
            val finalArtistsText = when {
                usableTrackArtistName(dbSong.artistsText) != null -> dbSong.artistsText
                else -> PropUtils.retainIfModified(dbSong.artistsText, usableTrackArtistName(newArtistsText))
            }
            val newDurationText = newSong.durationText
            val storedDuration = dbSong.durationText?.takeIf { durationTextToMillis(it) > 0 }
            val finalDurationText = when {
                storedDuration != null && (!reconcileExistingIdentity ||
                    newDurationText == null || durationTextToMillis(newDurationText) <= 0) -> storedDuration
                else -> newDurationText
            }

            Song(
                id = cleanSongId,
                title = finalTitle.orEmpty(),
                artistsText = finalArtistsText ?: "",
                durationText = finalDurationText,
                thumbnailUrl = artwork.songThumbnailWithExisting(dbSong.thumbnailUrl),
                likedAt = dbSong.likedAt,
                totalPlayTimeMs = dbSong.totalPlayTimeMs,
                position = dbSong.position,
                isYoutubeSong = dbSong.isYoutubeSong,
            ).apply { playCount = dbSong.playCount }
        } else newSong.copy(thumbnailUrl = artwork.songThumbnail)
        songTable.upsert(mergedSong)
        if (cleanSongId.startsWith(LOCAL_KEY_PREFIX)) return
        // Playback snapshots may have been queued before a committed relink.
        if (!reconcileExistingIdentity && dbSong != null && dbSong.title.isNotBlank()) return

        val albumId = mediaItem.mediaMetadata.extras?.getString("albumId")?.takeUnless(::isDeviceAlbumId)
        if (albumId != null && findEditedAlbumLink(cleanSongId, albumId) == null) {
            val albumArtwork = artwork.albumThumbnail()
            val albumTitle = mediaItem.mediaMetadata.albumTitle?.toString()
            val artist = mediaItem.mediaMetadata.artist?.toString()
            val year = mediaItem.mediaMetadata.releaseYear?.toString()
            
            val dbAlbum = albumTable.findByIdDirect(albumId)
            val mergedAlbum = if (dbAlbum != null) {
                dbAlbum.copy(
                    title = PropUtils.retainIfModified(dbAlbum.title, albumTitle?.takeIf { it.isNotBlank() }) ?: dbAlbum.title,
                    thumbnailUrl = artwork.albumThumbnailWithExisting(albumArtwork, dbAlbum.thumbnailUrl),
                    year = year.takeIf { !it.isNullOrBlank() } ?: dbAlbum.year,
                    authorsText = albumCreditWithTrackFallback(dbAlbum.authorsText, artist)
                )
            } else {
                Album(
                    id = albumId,
                    title = albumTitle,
                    thumbnailUrl = albumArtwork,
                    year = year,
                    authorsText = artist?.takeIf { it.isNotBlank() }
                )
            }
            if (dbAlbum != mergedAlbum) albumTable.upsert(mergedAlbum)

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
        val extras = mediaItem.mediaMetadata.extras
        val artistsNames = extras?.getStringArrayList("artistNames").orEmpty()
        val parsedCredits = readMediaItemArtistLinks(
            extras?.getStringArrayList("artistIds").orEmpty(),
            artistsNames,
            extras?.getStringArrayList(ARTIST_LINKED_NAMES_EXTRA),
        )
        val artistLinks = parsedCredits.copy(
            complete = parsedCredits.complete && parsedCredits.entries.all { usableTrackArtistName(it.name) != null },
        )

        // Names of the artist rows the mapping below actually points to - the alignment
        // target for artistsText (see the alignment block at the end of this function).
        val resolvedArtistNames = mutableListOf<String>()
        val customLinks = if (dbSong == null || (artistLinks.entries.isEmpty() && artistsNames.isEmpty())) emptyList() else {
            artistTable.findBySongIdDirect(cleanSongId).filter(::isManuallyEditedArtistLink)
        }

        if (artistLinks.entries.isNotEmpty()) {
            if (dbSong != null && artistLinks.complete) {
                logReconcileDrops(songArtistMapTable, artistTable, cleanSongId, mergedSong.title)
                val dropped = songArtistMapTable.deleteBySongId(cleanSongId)
                Timber.tag("Database").d(
                    "insertIgnore RECONCILE ids song=%s dropped=%d latestList=%s",
                    cleanSongId, dropped, artistLinks.entries.map { it.id }
                )
            }
            val mappedIds = mutableSetOf<String>()
            for ((id, name) in artistLinks.entries) {
                if (isDeviceArtistId(id)) continue
                val artist = manualArtistOverrideFor(cleanSongId, id, customLinks)
                    ?: artistTable.findByIdDirect(id)
                    ?: usableTrackArtistName(name)?.let {
                        Artist(id, it).also { newArtist -> artistTable.insertIgnore(newArtist) }
                    }
                    ?: continue
                if (mappedIds.add(artist.id)) {
                    songArtistMapTable.insertIgnore(SongArtistMap(cleanSongId, artist.id))
                    if (artistLinks.complete) {
                        (usableTrackArtistName(artist.name) ?: usableTrackArtistName(name))?.let(resolvedArtistNames::add)
                    }
                }
            }
            if (artistLinks.complete) {
                for (artist in customLinks) {
                    if (mappedIds.add(artist.id)) {
                        songArtistMapTable.insertIgnore(SongArtistMap(cleanSongId, artist.id))
                        usableTrackArtistName(artist.name)?.let(resolvedArtistNames::add)
                    }
                }
            }
        }

        // Complete endpoint credits can replace a stale copy. Partial entries only
        // repair a missing name, retaining the byline and the surviving associations.
        if (resolvedArtistNames.isEmpty() && usableTrackArtistName(mergedSong.artistsText) == null) {
            songArtistMapTable.findArtistsOfDirect(cleanSongId)
                .filterNot { isDeviceArtistId(it.id) }
                .mapNotNullTo(resolvedArtistNames) { usableTrackArtistName(it.name) }
        }
        if (resolvedArtistNames.isNotEmpty()) {
            val alignedJoin = resolvedArtistNames
                .map { cleanPrefix(it).trim() }
                .filter { it.isNotBlank() }
                .joinToString(", ")
            val alignedArtistsText = alignedJoin
                .takeIf { it.isNotBlank() }
                ?.let { PropUtils.retainIfModified(mergedSong.artistsText, it) }
            if (alignedArtistsText != null && alignedArtistsText != mergedSong.artistsText) {
                songTable.upsert(mergedSong.copy(artistsText = alignedArtistsText).apply { playCount = mergedSong.playCount })
            }
        }
    }

    private fun manualArtistOverrideFor(songId: String, id: String, linkedOverrides: List<Artist>): Artist? =
        linkedOverrides.singleOrNull { artist ->
            (artist.id != id && artist.youtubeChannelId == id) || ArtistMappingReconcile.isOverrideOf(artist.id, id) {
                trackBrowseIdOrigin(songId, artist.id)
            }
        }

    private fun isManuallyEditedArtistLink(artist: Artist): Boolean =
        !isDeviceArtistId(artist.id) && (
            artist.id.startsWith(MODIFIED_PREFIX) ||
                artist.id.startsWith("LOCAL_ARTIST_") ||
                artist.name?.startsWith(MODIFIED_PREFIX) == true
            )

    /**
     * Diagnostic helper for reconcile cleanup: right before a reconciliation
     * drops a song's stale artist links, log one line per about-to-be-dropped
     * pair (song id + title, artist name) so the drop can be read back from
     * logcat. Pure logging — no behavior change. Runs only on the delete
     * paths, never on the add-only one. A missing artist row (orphan link)
     * is logged as `artist="null"`.
     *
     * @param mapTable source of the stale pairs (targeted by [songId])
     * @param artistTable name lookup for each dropped pair
     * @param songId song side of the dropped pairs
     */
    internal suspend fun logReconcileDrops(
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

    /** Ensures [album] and [song] exist, then maps them; [position] -1 (default) maps to the next free position. */
    suspend fun mapIgnore( album: Album, song: Song, position: Int = -1 ) {
        if (isDeviceAlbumId(album.id) != song.id.startsWith(LOCAL_KEY_PREFIX)) return
        albumTable.insertIgnore( album )
        songTable.insertIgnore( song )
        songAlbumMapTable.map( song.id, album.id, position )
    }

    /** [mapIgnore] overload for [MediaItem]. */
    suspend fun mapIgnore( album: Album, mediaItem: MediaItem, position: Int = -1 ) =
        mapIgnore( album, mediaItem.asSong, position )


    /** Ensures [artist] and each [songs] entry exist, then maps them. */
    suspend fun mapIgnore( artist: Artist, vararg songs: Song ) {
        val compatibleSongs = songs.filter { it.id.startsWith(LOCAL_KEY_PREFIX) == isDeviceArtistId(artist.id) }
        if (compatibleSongs.isEmpty()) return

        artistTable.insertIgnore( artist )
        compatibleSongs.forEach {
            if (!isManuallyEditedArtistLink(artist) && !isDeviceArtistId(artist.id)) {
                val editedLinks = artistTable.findBySongIdDirect(it.id).filter(::isManuallyEditedArtistLink)
                if (manualArtistOverrideFor(it.id, artist.id, editedLinks) != null) return@forEach
            }
            songTable.insertIgnore( it )
            songArtistMapTable.insertIgnore(
                SongArtistMap(it.id, artist.id)
            )
        }
    }

    /** [mapIgnore] overload for [MediaItem]s. */
    suspend fun mapIgnore( artist: Artist, vararg mediaItems: MediaItem ) =
        mapIgnore( artist, *mediaItems.map( MediaItem::asSong ).toTypedArray() )

    /** Ensures [playlist] and each [songs] entry exist, then maps them. */
    suspend fun mapIgnore( playlist: Playlist, vararg songs: Song ) {
        mapMissingPlaylistSongs(playlist, songs.asList())
    }

    /** Returns the number of mappings created by the normal playlist insertion path. */
    internal suspend fun mapMissingPlaylistSongs(playlist: Playlist, songs: List<Song>): Int {
        if (songs.isEmpty()) return 0

        val position = if (playlist.supportsInsertionPosition())
            PlaylistInsertionPreferences(appContext()).get(playlist.id)
        else PlaylistInsertionPosition.End

        var insertedCount = 0
        transaction {
            // Autogenerated IDs become available only after inserting the playlist.
            val pId = if (playlist.id > 0) {
                playlistTable.insertIgnore(playlist)
                playlist.id
            } else playlistTable.insert(playlist)
            val existingIds = songPlaylistMapTable.songIdsInPlaylist(pId).toHashSet()
            val remotePlaylist = isYouTubePlaylistSource(playlist.browseId, playlist.isYoutubePlaylist)
            val newSongs = songs.distinctBy(Song::id).filter {
                it.id !in existingIds && (!remotePlaylist || !it.id.startsWith(LOCAL_KEY_PREFIX))
            }
            if (newSongs.isEmpty()) return@transaction

            if (position == PlaylistInsertionPosition.Beginning) {
                songPlaylistMapTable.shiftPositions(pId, newSongs.size)
                importSongTable.alignPlaylistPositions(pId)
            }
            val firstPosition = if (position == PlaylistInsertionPosition.Beginning) 0
                else songPlaylistMapTable.getMaxPosition(pId) + 1
            newSongs.forEachIndexed { index, song ->
                songTable.insertIgnore(song)
                songPlaylistMapTable.mapAtPosition(song.id, pId, firstPosition + index)
            }
            insertedCount = newSongs.size
        }
        return insertedCount
    }

    /** [mapIgnore] overload for [MediaItem]s. */
    suspend fun mapIgnore( playlist: Playlist, vararg mediaItems: MediaItem ) =
        mapIgnore( playlist, *mediaItems.map( MediaItem::asSong ).toTypedArray() )

    private class QueuedWriteContext : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<QueuedWriteContext>
    }

    private val databaseWorkScope by lazy { NzikDispatchers.fireAndForget(NzikDispatchers.DATA) }

    // Separate from Room's executors: a batch can suspend on a Room DAO without holding
    // the executor needed to run its SQL. Keep submitted batches FIFO, including cleanup.
    private val writeQueue by lazy {
        Channel<suspend () -> Unit>(Channel.UNLIMITED).also { queue ->
            databaseWorkScope.launch(QueuedWriteContext()) {
                for (work in queue) work()
            }
        }
    }

    /**
     * Schedules a write batch in submission order. Individual statements still autocommit
     * unless the caller explicitly uses [transaction].
     */
    fun asyncTransaction(retries: Int = 3, block: suspend Database.() -> Unit) {
        writeQueue.trySend {
            runCatching { runWriteBatch(retries, block) }.onFailure { error ->
                if (error !is CancellationException) {
                    Timber.tag("Database").e(error, "Database write batch failed")
                }
            }
        }.getOrThrow()
    }

    /**
     * Waits for the batch and any subsequent cleanup in it. A canceled waiter does not
     * interrupt a submitted batch between SQL and its associated filesystem operations.
     */
    internal suspend fun <T> awaitWriteBatch(retries: Int = 3, block: suspend Database.() -> T): T {
        if (coroutineContext[QueuedWriteContext] != null) {
            return runWriteBatch(retries, block)
        }
        val completed = CompletableDeferred<T>()
        writeQueue.trySend {
            runCatching { runWriteBatch(retries, block) }
                .fold(completed::complete, completed::completeExceptionally)
        }.getOrThrow()
        return completed.await()
    }

    private suspend fun <T> runWriteBatch(retries: Int, block: suspend Database.() -> T): T {
        require(retries > 0) { "A database write batch requires at least one attempt" }
        var attempt = 1
        while (true) {
            check(!isClosed) { "The database is closed" }
            try {
                return block()
            } catch (error: SQLiteDatabaseLockedException) {
                if (attempt == retries) throw error
                delay(200L * attempt)
                attempt++
            }
        }
    }

    /** Updates existing format metadata and publishes the change after the transaction commits. */
    internal suspend fun updateExistingFormat(songId: String, update: (Format) -> Format?) {
        transaction {
            val current = formatTable.findBySongIdDirect(songId) ?: return@transaction
            val updated = update(current) ?: return@transaction
            formatTable.upsert(updated)
        }
    }

    // Startup and automotive resumption must see earlier submitted snapshots, even
    // while ordinary write batches perform library work or file cleanup.
    private val savedQueueWork by lazy {
        Channel<suspend () -> Unit>(Channel.UNLIMITED).also { queue ->
            databaseWorkScope.launch {
                for (work in queue) work()
            }
        }
    }

    internal fun saveQueueSnapshot(snapshot: () -> List<QueuedMediaItem>) {
        savedQueueWork.trySend {
            runCatching {
                runWriteBatch(retries = 3) { queueTable.replaceAll(snapshot()) }
            }.onFailure { error ->
                if (error !is CancellationException) {
                    Timber.tag("Database").e(error, "Saving persistent queue failed")
                }
            }
        }.getOrThrow()
    }

    internal suspend fun readSavedQueue(): List<QueuedMediaItem> = awaitSavedQueueWork {
        check(!isClosed) { "The database is closed" }
        queueTable.allDirect()
    }

    /** Active database import joins this queue from the ordered write batch before closing Room. */
    internal suspend fun <T> awaitSavedQueueWork(block: suspend () -> T): T {
        val completed = CompletableDeferred<T>()
        savedQueueWork.trySend {
            if (completed.isActive) {
                runCatching { block() }
                    .fold(completed::complete, completed::completeExceptionally)
            }
        }.getOrThrow()
        try {
            return completed.await()
        } finally {
            completed.cancel()
        }
    }

    /** Runs an atomic Room transaction and publishes any library changes after its outer commit. */
    suspend fun transaction(block: suspend Database.() -> Unit) {
        val database = _internal
        if (coroutineContext[LibraryTransactionWrites] != null) {
            database.withTransaction { block() }
        } else {
            val caller = coroutineContext
            caller.ensureActive()
            val pending = LibraryTransactionWrites()
            // A committed write must still notify Bridge if the caller cancels during
            // the dispatch back from Room. Keep the SQL body cancellable by the caller.
            withContext(NonCancellable + pending) {
                database.withTransaction {
                    withContext(caller[Job] ?: NonCancellable) { block() }
                    caller.ensureActive()
                }
                notifyLibraryWrites(pending.snapshot())
            }
            caller.ensureActive()
        }
    }

    /** Runs a read callback off the main thread without occupying Room's query executor. */
    fun asyncQuery(block: suspend Database.() -> Unit) {
        databaseWorkScope.launch { block() }
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

    internal suspend fun updateArtistId(
        oldId: String,
        newId: String,
        fallbackArtist: Artist? = null,
        requiredTrackId: String? = null,
    ): Boolean = awaitWriteBatch {
        var saved = false
        transaction {
            if (requiredTrackId != null && artistTable.findBySongIdDirect(requiredTrackId).none { it.id == oldId }) {
                return@transaction
            }
            val stored = artistTable.findByIdDirect(oldId)
            val source = stored ?: fallbackArtist?.takeIf { it.id == oldId } ?: return@transaction
            if (oldId != newId) {
                if (newId.startsWith(MODIFIED_PREFIX) && artistTable.findByIdDirect(newId) != null) return@transaction
                artistTable.insertIgnore(source.copy(id = newId, isYoutubeArtist = false))
                songArtistMapTable.updateArtistId(oldId, newId)
                artistTable.deleteById(oldId)
            }
            saved = oldId != newId || stored != null
        }
        saved
    }

    internal suspend fun updateAlbumId(
        oldId: String,
        newId: String,
        fallbackAlbum: Album? = null,
        requiredTrackId: String? = null,
    ): Boolean = awaitWriteBatch {
        var saved = false
        transaction {
            if (requiredTrackId != null && songAlbumMapTable.findAlbumsOfDirect(requiredTrackId).none { it.id == oldId }) {
                return@transaction
            }
            val stored = albumTable.findByIdDirect(oldId)
            val source = stored ?: fallbackAlbum?.takeIf { it.id == oldId } ?: return@transaction
            if (oldId != newId) {
                if (newId.startsWith(MODIFIED_PREFIX) && albumTable.findByIdDirect(newId) != null) return@transaction
                albumTable.insertIgnore(source.copy(id = newId))
                songAlbumMapTable.updateAlbumId(oldId, newId)
                albumTable.deleteById(oldId)
            }
            saved = oldId != newId || stored != null
        }
        saved
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
        QueuedMediaItem::class,
        Format::class,
        Event::class,
        Lyrics::class,
        ImportSong::class,
    ],
    views = [
        SortedSongPlaylistMap::class
    ],
    version = LIBRARY_SCHEMA_VERSION,
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
    abstract val songAlbumMapTable: SongAlbumMapTable
    abstract val songArtistMapTable: SongArtistMapTable
    abstract val songPlaylistMapTable: SongPlaylistMapTable
    abstract val songTable: SongTable
    abstract val importSongTable: ImportSongTable

    companion object {
        val Instance: DatabaseInitializer by lazy {
            val context = appContext()
            context.requireLibraryAccess()
            val db = Room.databaseBuilder(
                    context = context,
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
                    From41To42Migration,
                    From42To43Migration,
                    From43To44Migration,
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





