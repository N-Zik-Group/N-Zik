package app.n_zik.android.components.dialog.song

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.core.database.ArtistMappingReconcile
import app.n_zik.android.core.database.Database

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.appContext
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.requests.song
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.core.migration.NameConvergence
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.asSong
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.isLocal
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.components.dialog.common.InProgressDialog
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.components.dialog.common.CheckboxDialog
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.download.services.ProfileDownloadCommands
import timber.log.Timber
import java.util.Optional

/**
 * The song « Update » action shared by the player menu, the song menu and the
 * Home Songs toolbar (batch flavor): refetch fields from YouTube Music
 * (title / authors / thumbnail), reset the total play time or like status and wipe the caches
 * - each behind its own checkbox. Ticking « authors » re-links the artist
 * links from the fresh (complete) author list via
 * [Database.reconcileArtistLinks], so orphaned songs heal without playback.
 *
 * The batch flavor replaces the per-song done toast with a single summary
 * toast: N « done » toasts are unusable feedback for a batch run.
 */
@UnstableApi
class UpdateSongDialog private constructor(
    activeState: MutableState<Boolean>,
    private val binder: PlayerServiceModern.Binder?,
    var song: Optional<Song>,
    private val batchTargets: (() -> List<Song>)? = null
) : CheckboxDialog(activeState), MenuIcon, Descriptive {

    companion object {
        private const val TITLE_CHECKBOX_ID = "title"
        private const val AUTHORS_CHECKBOX_ID = "authors"
        private const val THUMBNAIL_CHECKBOX_ID = "thumbnail"
        private const val PLAYTIME_CHECKBOX_ID = "playtime"
        private const val CACHE_CHECKBOX_ID = "cache"
        private const val FAVORITE_CHECKBOX_ID = "favorite"
        private const val OFFLINE_DOWNLOAD_CHECKBOX_ID = "offline_download"

        /** Keeps active checkbox choices while same-song metadata changes. */
        @Composable
        operator fun invoke(song: Song): UpdateSongDialog {
            val binder = LocalPlayerServiceBinder.current
            val dialog = remember(song.id, binder) {
                UpdateSongDialog(mutableStateOf(false), binder, Optional.of(song))
            }
            SideEffect { dialog.song = Optional.of(song) }
            return dialog
        }

        /**
         * Batch flavor for the Home Songs toolbar: the targets are read from [batchTargets] at
         * confirmation time (the selected songs, or every song on display when nothing is
         * selected), never at dialog creation. While the batch runs, the dialog switches to a
         * progress mode (spinner + counter) and closes itself once the summary toast is shown.
         *
         * Memoized on the binder only: the provider is deliberately NOT a remember key — a
         * bound method reference gets a fresh identity on every recomposition and keying on it
         * would recreate the instance and reset the checkbox selection (the same incident the
         * song key of the single dialog guards against). rememberUpdatedState keeps the
         * current provider even when its closure captured a new displayed list.
         */
        @Composable
        operator fun invoke( batchTargets: () -> List<Song> ): UpdateSongDialog {
            val binder = LocalPlayerServiceBinder.current
            val currentTargets = rememberUpdatedState(batchTargets)
            return remember( binder ) {
                UpdateSongDialog(
                    mutableStateOf( false ),
                    binder,
                    Optional.empty(),
                    { currentTargets.value() }
                )
            }
        }
    }

    /*
     * To save memory, these buttons are not init at runtime.
     * They are created upon used, and discard after used.
     */
    init {
        val updateTitle = object : Item() {
            override val id: String = TITLE_CHECKBOX_ID
            override val menuIconTitle: String
                @Composable
                get() = stringResource( R.string.metadata_action_refresh_title_from_youtube )
        }
        items.add( updateTitle )

        val updateAuthors = object : Item() {
            override val id: String = AUTHORS_CHECKBOX_ID
            override val menuIconTitle: String
                @Composable
                get() = stringResource( R.string.metadata_action_refresh_artists_from_youtube )
        }
        items.add( updateAuthors )

        val updateThumbnail = object : Item() {
            override val id: String = THUMBNAIL_CHECKBOX_ID
            override val menuIconTitle: String
                @Composable
                get() = stringResource( R.string.metadata_action_refresh_cover_from_youtube )
        }
        items.add( updateThumbnail )

        val resetPlaytime = object : Item() {
            override val id: String = PLAYTIME_CHECKBOX_ID
            override val menuIconTitle: String
                @Composable
                get() = stringResource( R.string.song_play_time_action_reset )
        }
        items.add( resetPlaytime )

        val resetCache = object : Item() {
            override val id: String = CACHE_CHECKBOX_ID
            override val menuIconTitle: String
                @Composable
                get() = stringResource(R.string.song_update_action_clear_playback_cache)
        }
        items.add( resetCache )

        val resetFavorite = object : Item() {
            override val id: String = FAVORITE_CHECKBOX_ID
            override val menuIconTitle: String
                @Composable
                get() = stringResource(R.string.song_like_status_action_reset)
        }
        items.add(resetFavorite)

        val removeOfflineDownload = object : Item() {
            override val id: String = OFFLINE_DOWNLOAD_CHECKBOX_ID
            override val menuIconTitle: String
                @Composable
                get() = stringResource(R.string.song_update_action_remove_offline_download)
        }
        items.add(removeOfflineDownload)

        items.add( Item.SELECT_ALL )
    }

    override val iconId: Int = R.drawable.refresh
    override val messageId: Int = R.string.song_metadata_update_action_open
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.common_action_update )
    override val dialogTitle: String
        @Composable
        get() = menuIconTitle

    override fun onShortClick() = showDialog()

    /**
     * The (done, total) progress counter of an in-flight batch run; null when
     * the dialog is idle. Written only from the UI dispatcher.
     */
    private var batchProgress by mutableStateOf<Pair<Int, Int>?>(null)

    /** The in-flight batch coroutine, cancelled by the progress cancel button. */
    private var batchJob: Job? = null

    override fun onConfirm() {
        val targets = batchTargets
        val selection = readSelection()

        // Nothing ticked: nothing to fetch, write or announce (« Aucune case
        // cochée » of the spec I/O matrix) -
        //
        // closing without a toast is the only honest feedback for a no-op.
        if( !selection.isAnySelected ) {
            Timber.tag("Database").d( "update skipped: no box ticked" )
            hideDialog()
            return
        }
        val requestProfile = getActiveProfile(appContext())

        if( targets != null ) {
            // Batch flavor: local songs carry no YTM id and are skipped, so
            // compute the remote targets here -
            //
            // with nothing to update the dialog just closes (no progress mode, no toast).
            val remote = remoteTargets( targets() )
            if( remote.isEmpty() ) {
                hideDialog()
                return
            }
            // The dialog stays open in progress mode until the batch is done
            // (or the cancel button stops it between songs).
            batchProgress = 0 to remote.size
            batchJob = NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                try {
                    runSongUpdateBatch( binder, remote, selection, requestProfile = requestProfile ) { done, total ->
                        withContext( NzikDispatchers.UI ) { batchProgress = done to total }
                    }
                } catch( _: CancellationException ) {
                    // A cancel landing mid-song propagates here; the processed
                    // songs are already written, the rest is left for the next run.
                    Timber.tag("Database").d( "update batch cancelled" )
                } catch( e: Exception ) {
                    // Without this catch the job would die before the close
                    // below and the progress dialog would stay open forever.
                    Timber.tag("Database").e( e, "update batch failed" )
                }
                // Close on the UI thread: the Compose state write must not
                // happen from the DATA dispatcher
                //
                // (the cancel path already closed it - the processed songs stay written, no toast).
                if( coroutineContext[ Job ]?.isActive != false ) {
                    withContext( NzikDispatchers.UI ) {
                        batchProgress = null
                        hideDialog()
                    }
                }
            }
            return
        }

        if( song.isEmpty ) {
            hideDialog()
            return
        }
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            runSongUpdate( binder, song.get(), selection, requestProfile )
        }
        hideDialog()
    }

    /** Reads the checkbox states out of the Compose items (wiring layer only). */
    private fun readSelection(): SongUpdateSelection = SongUpdateSelection(
        title = items.first { it.id == TITLE_CHECKBOX_ID }.selected,
        authors = items.first { it.id == AUTHORS_CHECKBOX_ID }.selected,
        thumbnail = items.first { it.id == THUMBNAIL_CHECKBOX_ID }.selected,
        playtime = items.first { it.id == PLAYTIME_CHECKBOX_ID }.selected,
        cache = items.first { it.id == CACHE_CHECKBOX_ID }.selected,
        favorite = items.first { it.id == FAVORITE_CHECKBOX_ID }.selected,
        offlineDownload = items.first { it.id == OFFLINE_DOWNLOAD_CHECKBOX_ID }.selected
    )

    /**
     * Progress mode renders the shared [InProgressDialog] (bold label, 48 dp
     * wavy circular indicator, done/total counter, cancel pill). The standard
     * dialog chrome (checkbox list + confirm buttons) is only rendered while
     * the dialog is idle.
     */
    @Composable
    override fun Render() {
        var presentedProgress by remember { mutableStateOf(batchProgress) }
        if (isActive) presentedProgress = batchProgress
        DialogPresence(visible = isActive, inherit = true) {
            val progress = presentedProgress
            if (progress != null) {
                val (done, total) = progress
                InProgressDialog(
                    total = total,
                    done = done,
                    text = stringResource(R.string.common_action_update),
                    onDismiss = ::onCancelBatch
                )
            } else {
                super.Render()
            }
        }
    }

    /** Cancels the in-flight batch between songs and leaves the progress UI. */
    private fun onCancelBatch() {
        Timber.tag("Database").d( "update batch cancel requested" )
        batchJob?.cancel()
        batchProgress = null
        hideDialog()
    }
}

/** The ticked checkboxes of [UpdateSongDialog], decoupled from the Compose item list so the update logic stays testable without composition. */
internal data class SongUpdateSelection(
    val title: Boolean = false,
    val authors: Boolean = false,
    val thumbnail: Boolean = false,
    val playtime: Boolean = false,
    val cache: Boolean = false,
    val favorite: Boolean = false,
    val offlineDownload: Boolean = false
) {
    /** A YTM fetch is needed when any fetched-field checkbox is ticked. */
    val needsFetch: Boolean get() = title || authors || thumbnail

    /** Whether any action is requested at all (all boxes unticked = no-op). */
    val isAnySelected: Boolean get() = title || authors || thumbnail || playtime || cache || favorite || offlineDownload
}

/**
 * Applies the checked update actions of [UpdateSongDialog] to [storedRow].
 *
 * Checked fields explicitly reset custom metadata to non-blank provider values.
 * Missing fetched data preserves the stored value; play count is never reset.
 */
internal fun applySongUpdate(
    storedRow: Song,
    fetchedSong: Song?,
    selection: SongUpdateSelection
): Song {
    var updated = storedRow
    val fetched = fetchedSong
    if( fetched != null ) {
        updated = updated.copy(
            title = if( selection.title ) fetched.title.takeIf { hasUsableSongUpdateTitle(it) } ?: storedRow.title else storedRow.title,
            artistsText = if( selection.authors ) mergeFetchedArtistsText( storedRow.id, storedRow.artistsText, fetched.artistsText ) else storedRow.artistsText,
            thumbnailUrl = if( selection.thumbnail ) fetched.thumbnailUrl?.takeUnless { it.isBlank() } ?: storedRow.thumbnailUrl else storedRow.thumbnailUrl
        )
    }
    if( selection.playtime ) {
        updated = updated.copy( totalPlayTimeMs = 0L )
    }
    if (selection.favorite) {
        updated = updated.copy(likedAt = null)
    }
    // Song.copy() only carries constructor parameters: the body property
    // playCount would silently reset to 0 on every update. Restore the
    //
    // stored value - the dialog never touches the per-song play count.
    if( updated !== storedRow ) {
        updated.playCount = storedRow.playCount
    }
    return updated
}

/** Prefixes are metadata flags, not a usable provider title. */
internal fun hasUsableSongUpdateTitle(title: String?): Boolean =
    title != null && cleanPrefix(title).isNotBlank()

/**
 * An empty author list from a video fetch means missing data, so preserve stored artists.
 */
internal fun mergeFetchedArtistsText(
    songId: String,
    storedArtistsText: String?,
    fetchedArtistsText: String?
): String? {
    if( fetchedArtistsText.isNullOrBlank() && !storedArtistsText.isNullOrBlank() ) {
        Timber.tag( "Database" ).d(
            "update keep stored artistsText song=%s value=%s (blank fetch)",
            songId, storedArtistsText
        )
        return storedArtistsText
    }
    return fetchedArtistsText ?: storedArtistsText
}

/** Reset artist links only when requested and backed by a complete provider list. */
internal fun shouldReconcileAuthors(
    authorsChecked: Boolean,
    parsedNames: List<String>,
    authors: List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>>?
): Boolean =
    authorsChecked &&
        ArtistMappingReconcile.isCompleteAuthorList( parsedNames, authors )

/**
 * The whole per-song update of [UpdateSongDialog]: fetch gate, channel-anchored artist
 * resolution, conditional artist-link reconcile, stored-row rewrite and the conditional
 * cache wipe — everything except the done toast, so the batch action of
 * [runSongUpdateBatch] can reuse it without emitting N toasts. Each step is logged
 * (tag `Database`, level `d`).
 *
 * The stored row is re-read inside the transaction before the merge is applied: the
 * dialog entry may be a 5-field `MediaItem.asSong` snapshot (player menu) whose
 * `likedAt`/`totalPlayTimeMs`/`position`/`isYoutubeSong` carry model defaults, so
 * out-of-snapshot fields always come from the stored row, never from the snapshot.
 */
internal suspend fun runSongUpdateCore(
    binder: PlayerServiceModern.Binder?,
    entry: Song,
    selection: SongUpdateSelection,
    requestProfile: String = getActiveProfile(appContext()),
    progress: SongUpdateProgress = SongUpdateProgress(),
) : Boolean {
    val context = appContext()
    if (getActiveProfile(context) != requestProfile) return progress.profileChanged()
    val downloadOwner = if (selection.offlineDownload && !entry.isLocal) {
        ProfileDownloadCommands.capture(context) ?: return progress.profileChanged()
    } else null
    if (selection.cache && binder == null) {
        Timber.tag("Database").w("Cache reset unavailable: playback service is not connected")
        progress.issues += SongUpdateIssue.CACHE_UNAVAILABLE
        return false
    }
    Timber.tag("Database").d(
        "update start song=%s fetch=%b playtime=%b cache=%b favoriteReset=%b",
        entry.id, selection.needsFetch, selection.playtime, selection.cache, selection.favorite
    )

    // Fetch gate: only touch YTM when a fetch checkbox is ticked.
    val videoId = entry.id.takeIf { YOUTUBE_VIDEO_ID.matches(it) }
    val providerItem: Innertube.SongItem? = if (selection.needsFetch && videoId != null) {
        try {
            val result = Innertube.song(videoId)
            val failure = result?.exceptionOrNull()
            if (failure != null) {
                progress.issues += classifySongUpdateFetchFailure(failure)
                null
            } else {
                result?.getOrNull()?.let { item ->
                    if (item.key == videoId) item else {
                        progress.issues += SongUpdateIssue.WRONG_SONG
                        null
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            progress.issues += classifySongUpdateFetchFailure(error)
            null
        }
    } else null
    val fetchedItem = if (videoId != null) {
        withVideoAuthorFallback(videoId, providerItem, selection.authors, onFailure = { progress.issues += it })
    } else providerItem
    val fetchedSong = fetchedItem?.asSong
    if (selection.needsFetch) {
        if (fetchedSong == null) {
            if (progress.issues.isEmpty()) progress.issues += SongUpdateIssue.METADATA_UNAVAILABLE
        } else {
            if (selection.title && !hasUsableSongUpdateTitle(fetchedSong.title)) progress.issues += SongUpdateIssue.MISSING_TITLE
            if (selection.authors && fetchedSong.artistsText.isNullOrBlank()) progress.issues += SongUpdateIssue.MISSING_ARTISTS
            if (selection.thumbnail && fetchedSong.thumbnailUrl.isNullOrBlank()) progress.issues += SongUpdateIssue.MISSING_COVER
        }
    }
    if( selection.needsFetch ) {
        Timber.tag("Database").d(
            "update fetch song=%s ok=%b title=%s",
            entry.id, fetchedItem != null, fetchedSong?.title
        )
    }

    // Resolve artist rows in the commit so a user override cannot be restored
    // from a stale pre-transaction snapshot.
    val parsedEntries = if( selection.authors && fetchedItem != null ) {
        ArtistMappingReconcile.parseAuthorEntries( fetchedItem.authors )
    } else {
        emptyList()
    }
    if (selection.authors && !fetchedSong?.artistsText.isNullOrBlank() &&
        !shouldReconcileAuthors(true, parsedEntries.map { it.first }, fetchedItem?.authors)
    ) {
        progress.issues += SongUpdateIssue.INCOMPLETE_ARTISTS
    }
    if (getActiveProfile(context) != requestProfile) return progress.profileChanged()

    progress.failureIssue = SongUpdateIssue.STORAGE
    val committedSong = awaitSongUpdateCommit {
        if (getActiveProfile(context) != requestProfile) return@awaitSongUpdateCommit null
        var previousThumbnail: String? = null
        var committedRow: Song? = null
        syncTransaction {
            // Re-read the stored row before writing: the entry may be a 5-field
            // MediaItem snapshot, so out-of-snapshot fields come from the DB.
            val storedRow = songTable.findByIdDirect( entry.id ) ?: entry
            previousThumbnail = storedRow.thumbnailUrl
            val updatedRow = applySongUpdate( storedRow, fetchedSong, selection )
            songTable.insertIgnore(storedRow)
            songTable.updateReplace( updatedRow )
            committedRow = updatedRow
            Timber.tag("Database").d(
                "update applied song=%s title=%b artistsText=%b thumbnail=%b playtimeReset=%b cacheWiped=%b favoriteReset=%b",
                entry.id,
                updatedRow.title != storedRow.title,
                updatedRow.artistsText != storedRow.artistsText,
                updatedRow.thumbnailUrl != storedRow.thumbnailUrl,
                selection.playtime,
                selection.cache,
                selection.favorite
            )

            if( shouldReconcileAuthors( selection.authors, parsedEntries.map { it.first }, fetchedItem?.authors ) ) {
                val linkedIds = songArtistMapTable.findArtistsOfDirect(entry.id).map { it.id }.toSet()
                val artists = parsedEntries.mapNotNull { (name, browseId) ->
                    if (browseId == null) return@mapNotNull null
                    val existing = artistTable.findByIdDirect(browseId)
                    if (existing != null && browseId in linkedIds && existing.name?.startsWith(MODIFIED_PREFIX, true) == true) {
                        // A song author edit also renames its linked artist. Undo that
                        // override or the next playback would reapply the custom name.
                        NameConvergence.propagateRename(this, browseId, existing.name.orEmpty(), name)
                        existing.copy(name = name)
                    } else {
                        existing ?: Artist(id = browseId, name = name)
                    }
                }
                val dropped = reconcileArtistLinks( entry.id, artists )
                Timber.tag("Database").d(
                    "update RECONCILE song=%s dropped=%d latestList=%d artists",
                    entry.id, dropped, parsedEntries.size
                )
            }

            // Only a successful fetch refreshed this song's metadata: reset the
            // 30-day fetch TTLs of its artist pages, album pages and lyrics so
            //
            // the next playback re-fetches them with the fresh data (lastFetch → NULL
            // = TTL expired for the StreamResolver / lyrics decision makers).
            //
            // A skipped (playtime/cache/favorite-only) or failed fetch must NOT invalidate the TTLs:
            //
            // nothing was re-fetched, so the cached pages are still as fresh as before.
            if( fetchedItem != null ) {
                val ttlArtistIds = songArtistMapTable.findArtistsOfDirect( entry.id ).map { it.id }
                ttlArtistIds.forEach { artistTable.resetFetchTtl( it ) }
                // A song can be mapped to several albums: reset every mapped
                // album's TTL, not just one arbitrary row.
                val ttlAlbums = songAlbumMapTable.findAlbumsOfDirect( entry.id )
                    .filter { !it.id.isNullOrBlank() }
                ttlAlbums.forEach { albumTable.resetFetchTtl( it.id ) }
                val lyricsTtlRows = lyricsTable.resetFetchTtlBySongId( entry.id )
                Timber.tag( "Database" ).d(
                    "update TTL reset song=%s artists=%d album=%d lyricsRows=%d",
                    entry.id, ttlArtistIds.size, ttlAlbums.size, lyricsTtlRows
                )
            }
        }
        // Count successful requested actions only after Room commits, even if values were already original.
        progress.anyCompleted = selection.playtime || selection.favorite ||
            (selection.title && hasUsableSongUpdateTitle(fetchedSong?.title)) ||
            (selection.authors && !fetchedSong?.artistsText.isNullOrBlank()) ||
            (selection.thumbnail && !fetchedSong?.thumbnailUrl.isNullOrBlank())
        // Files and caches cannot roll back with Room; remove them only after commit.
        if (getActiveProfile(context) != requestProfile) return@awaitSongUpdateCommit null
        if (selection.cache) {
            progress.failureIssue = SongUpdateIssue.CACHE_CLEAR
            binder?.cache?.removeResource(entry.id)
            progress.anyCompleted = true
            WaveformExtractor.deleteWaveformOrThrow(context, entry.id)
        }
        if (downloadOwner != null) {
            if (downloadOwner != ProfileDownloadCommands.capture(context)) return@awaitSongUpdateCommit null
            // Use the service/manager removal path so its index and observed download
            // state follow the file deletion. Shared Format metadata still describes
            // the source and must survive either independent storage action.
            progress.failureIssue = SongUpdateIssue.DOWNLOAD_REMOVE
            downloadOwner.sendRemove(context, entry.id).getOrThrow()
            progress.anyCompleted = true
        }
        if (selection.thumbnail && !fetchedSong?.thumbnailUrl.isNullOrBlank()) {
            progress.failureIssue = SongUpdateIssue.COVER_RESET
            SongUpdateCoverReset.reset(context, entry.id, previousThumbnail, requestProfile)
        }
        committedRow
    }
    if (committedSong == null || getActiveProfile(context) != requestProfile) return progress.profileChanged()
    val refreshedFields = selection.copy(
        title = selection.title && hasUsableSongUpdateTitle(fetchedSong?.title),
        authors = selection.authors && !fetchedSong?.artistsText.isNullOrBlank(),
        thumbnail = selection.thumbnail && !fetchedSong?.thumbnailUrl.isNullOrBlank(),
    )
    if (refreshedFields.needsFetch) {
        progress.failureIssue = SongUpdateIssue.PLAYBACK_REFRESH
        SongUpdatePlaybackRefresh.publish(binder, committedSong, refreshedFields, requestProfile)
    }
    return progress.issues.isEmpty()
}

private val YOUTUBE_VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

/** The single song « Update » action (player menu + song menu): [runSongUpdateCore] followed by its own done toast. */
internal suspend fun runSongUpdate(
    binder: PlayerServiceModern.Binder?,
    entry: Song,
    selection: SongUpdateSelection,
    requestProfile: String = getActiveProfile(appContext())
) {
    showSongUpdateReport(runSongUpdateReport(binder, entry, selection, requestProfile))
}

/** The non-local targets of a batch run - local songs carry no YTM id. */
internal fun remoteTargets( targets: List<Song> ): List<Song> = targets.filterNot( Song::isLocal )

/**
 * The Home Songs toolbar batch « Update » action: applies [runSongUpdateCore] sequentially to
 * every non-local target (local songs are skipped silently — they carry no YTM id). YTM
 * fetches are rate-limited with a random 1-5 s gap between requests (same pattern as
 * HomeSyncService), only when a fetch box is ticked; a single summary toast replaces the
 * per-song done toasts.
 *
 * [onProgress] is invoked on the DATA dispatcher after every processed song with the
 * (done, total) counter; the caller publishes it in a Compose state by hopping to the UI
 * thread. Cancellation is cooperative: between songs the batch returns the processed count
 * (no toast), a cancel landing mid-song propagates [CancellationException] to the caller.
 *
 * @param fetchGapMs Gap between two YTM fetches in ms — random 1-5 s by default, injectable
 *        to 0 in unit tests.
 * @return The number of processed songs (the non-local targets); 0 when there is nothing to
 *        update — no fetch, write or toast.
 */
internal suspend fun runSongUpdateBatch(
    binder: PlayerServiceModern.Binder?,
    targets: List<Song>,
    selection: SongUpdateSelection,
    requestProfile: String = getActiveProfile(appContext()),
    fetchGapMs: ( ) -> Long = { (1000L..5000L).random() },
    onProgress: suspend ( done: Int, total: Int ) -> Unit
): Int {
    val songsToUpdate = remoteTargets( targets )
    Timber.tag("Database").d(
        "update batch start count=%d fetch=%b",
        songsToUpdate.size, selection.needsFetch
    )
    if( songsToUpdate.isEmpty() ) {
        Timber.tag("Database").d( "update batch done processed=0" )
        return 0
    }
    val reports = mutableListOf<SongUpdateReport>()
    songsToUpdate.forEachIndexed { index, entry ->
        // Cooperative cancel: the progress dialog's cancel button stops the
        // batch between songs (a cancel landing mid-song propagates
        //
        // CancellationException to the caller instead).
        if( coroutineContext[ Job ]?.isActive == false ) {
            Timber.tag("Database").d( "update batch cancelled processed=%d of %d", index, songsToUpdate.size )
            return index
        }
        // Random 1-5 s gap between YTM fetches (same pattern as
        // HomeSyncService) to stay gentle on the API.
        if( index > 0 && selection.needsFetch ) delay( fetchGapMs() )
        reports += runSongUpdateReport(binder, entry, selection, requestProfile)
        onProgress( index + 1, songsToUpdate.size )
    }
    Timber.tag("Database").d( "update batch done processed=%d", songsToUpdate.size )
    if (coroutineContext[Job]?.isActive != false) showSongUpdateBatchReport(reports)
    return songsToUpdate.size
}
