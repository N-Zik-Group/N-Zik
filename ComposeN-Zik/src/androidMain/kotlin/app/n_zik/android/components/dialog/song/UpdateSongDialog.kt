package app.n_zik.android.components.dialog.song

import app.n_zik.android.core.database.*

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.appContext
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.requests.song
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.bold
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.isLocal
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.components.themed.DefaultDialog
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.components.dialog.common.CheckboxDialog
import app.kreate.android.me.knighthat.utils.PropUtils
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import timber.log.Timber
import java.util.Optional

/**
 * The song « Update » action shared by the player menu, the song menu and the
 * Home Songs toolbar (batch flavor): refetch fields from YouTube Music
 * (title / authors / thumbnail), reset the total play time and wipe the caches
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

        /**
         * Memoizes the dialog instance on (song, binder): without this, a
         * recomposition with the dialog open recreates the items (the checkbox
         * selection lives on the Item instances) and resets the boxes to zero.
         * The instance is only rebuilt when the song or the player binder
         * actually changes.
         */
        @Composable
        operator fun invoke( song: Song ): UpdateSongDialog {
            val binder = LocalPlayerServiceBinder.current
            return remember( song, binder ) {
                UpdateSongDialog(
                    mutableStateOf( false ),
                    binder,
                    Optional.of( song )
                )
            }
        }

        /**
         * Batch flavor for the Home Songs toolbar: the targets are read from
         * [batchTargets] at confirmation time (the selected songs, or every
         * song on display when nothing is selected), never at dialog creation.
         * While the batch runs, the dialog switches to a progress mode
         * (spinner + counter) and closes itself once the summary toast is
         * shown.
         *
         * Memoized on the binder only - the provider is deliberately NOT a
         * remember key: a bound method reference gets a fresh identity on
         * every recomposition and keying on it would recreate the instance
         * and reset the checkbox selection (the same incident the song key
         * of the single dialog guards against). The captured provider stays
         * live because it reads remembered state (selection / display list).
         */
        @Composable
        operator fun invoke( batchTargets: () -> List<Song> ): UpdateSongDialog {
            val binder = LocalPlayerServiceBinder.current
            return remember( binder ) {
                UpdateSongDialog(
                    mutableStateOf( false ),
                    binder,
                    Optional.empty(),
                    batchTargets
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
                get() = stringResource( R.string.update_title_from_ytm )
        }
        items.add( updateTitle )

        val updateAuthors = object : Item() {
            override val id: String = AUTHORS_CHECKBOX_ID
            override val menuIconTitle: String
                @Composable
                get() = stringResource( R.string.update_authors_from_ytm )
        }
        items.add( updateAuthors )

        val updateThumbnail = object : Item() {
            override val id: String = THUMBNAIL_CHECKBOX_ID
            override val menuIconTitle: String
                @Composable
                get() = stringResource( R.string.update_thumbnail_from_ytm )
        }
        items.add( updateThumbnail )

        val resetPlaytime = object : Item() {
            override val id: String = PLAYTIME_CHECKBOX_ID
            override val menuIconTitle: String
                @Composable
                get() = stringResource( R.string.title_reset_total_play_time )
        }
        items.add( resetPlaytime )

        val resetCache = object : Item() {
            override val id: String = CACHE_CHECKBOX_ID
            override val menuIconTitle: String
                @Composable
                get() = stringResource( R.string.title_reset_cache )
        }
        items.add( resetCache )

        items.add( Item.SELECT_ALL )
    }

    override val iconId: Int = R.drawable.refresh
    override val messageId: Int = R.string.info_open_update_dialog
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.update )
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
        // cochée » of the spec I/O matrix) - closing without a toast is the
        // only honest feedback for a no-op.
        if( !selection.isAnySelected ) {
            Timber.tag("Database").d( "update skipped: no box ticked" )
            hideDialog()
            return
        }

        if( targets != null ) {
            // Batch flavor: local songs carry no YTM id and are skipped, so
            // compute the remote targets here - with nothing to update the
            // dialog just closes (no progress mode, no toast).
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
                    runSongUpdateBatch( binder, remote, selection ) { done, total ->
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
                // happen from the DATA dispatcher (the cancel path already
                // closed it - the processed songs stay written, no toast).
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
            runSongUpdate( binder, song.get(), selection )
        }
        hideDialog()
    }

    /** Reads the checkbox states out of the Compose items (wiring layer only). */
    private fun readSelection(): SongUpdateSelection = SongUpdateSelection(
        title = items.first { it.id == TITLE_CHECKBOX_ID }.selected,
        authors = items.first { it.id == AUTHORS_CHECKBOX_ID }.selected,
        thumbnail = items.first { it.id == THUMBNAIL_CHECKBOX_ID }.selected,
        playtime = items.first { it.id == PLAYTIME_CHECKBOX_ID }.selected,
        cache = items.first { it.id == CACHE_CHECKBOX_ID }.selected
    )

    /**
     * Progress mode renders the exact design of the cached-songs export
     * progress (the shared `InProgressDialog`: bold label, 48 dp wavy circular
     * indicator, done/total counter) through the same `DefaultDialog` wrapper,
     * plus the fully red cancel button. The standard dialog chrome (checkbox
     * list + confirm buttons) is only rendered while the dialog is idle.
     */
    @OptIn( ExperimentalMaterial3ExpressiveApi::class )
    @Composable
    override fun Render() {
        val progress = batchProgress
        if( progress != null ) {
            val ( done, total ) = progress
            val colorPalette = colorPalette()
            DefaultDialog(
                onDismiss = ::onCancelBatch,
                modifier = Modifier
                    .fillMaxWidth( if( isLandscape ) 0.3f else 0.8f )
            ) {
                BasicText(
                    text = stringResource( R.string.update ),
                    style = TextStyle(
                        textAlign = TextAlign.Center,
                        fontSize = typography().l.bold.fontSize,
                        fontWeight = typography().l.bold.fontWeight,
                        color = colorPalette.text
                    ),
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer( Modifier.height( 16.dp ) )
                val progressFraction = if( total > 0 ) done.toFloat() / total.toFloat() else 0f
                if( total > 0 ) {
                    CircularWavyProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .size( 48.dp )
                            .align( Alignment.CenterHorizontally ),
                        color = colorPalette.accent,
                        trackColor = colorPalette.background2
                    )
                } else {
                    CircularWavyProgressIndicator(
                        modifier = Modifier
                            .size( 48.dp )
                            .align( Alignment.CenterHorizontally ),
                        color = colorPalette.accent,
                        trackColor = colorPalette.background2
                    )
                }
                if( total > 0 ) {
                    Spacer( Modifier.height( 8.dp ) )
                    BasicText(
                        text = "$done / $total",
                        style = TextStyle(
                            textAlign = TextAlign.Center,
                            fontStyle = typography().xs.semiBold.fontStyle,
                            color = colorPalette.text
                        ),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // The cancel keeps the export progress button geometry (shape,
                // width, text style) with a fully red container: cancel stops
                // the in-flight batch between songs; the processed songs stay
                // written and no summary toast is shown.
                Spacer( Modifier.height( 20.dp ) )
                Button(
                    onClick = ::onCancelBatch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorPalette.red,
                        contentColor = colorPalette.text
                    ),
                    shape = uiRoundnessShape(),
                    modifier = Modifier
                        .fillMaxWidth( 0.8f )
                        .align( Alignment.CenterHorizontally )
                ) {
                    BasicText(
                        text = stringResource( R.string.cancel ),
                        style = TextStyle(
                            textAlign = TextAlign.Center,
                            fontStyle = typography().s.semiBold.fontStyle,
                            color = colorPalette.text
                        )
                    )
                }
            }
            return
        }
        super.Render()
    }

    /** Cancels the in-flight batch between songs and leaves the progress UI. */
    private fun onCancelBatch() {
        Timber.tag("Database").d( "update batch cancel requested" )
        batchJob?.cancel()
        batchProgress = null
        hideDialog()
    }
}

/**
 * The ticked checkboxes of [UpdateSongDialog], decoupled from the Compose item
 * list so the update logic stays testable without composition.
 */
internal data class SongUpdateSelection(
    val title: Boolean = false,
    val authors: Boolean = false,
    val thumbnail: Boolean = false,
    val playtime: Boolean = false,
    val cache: Boolean = false
) {
    /** A YTM fetch is needed when any fetched-field checkbox is ticked. */
    val needsFetch: Boolean get() = title || authors || thumbnail

    /** Whether any action is requested at all (all five boxes unticked = no-op). */
    val isAnySelected: Boolean get() = title || authors || thumbnail || playtime || cache
}

/**
 * Applies the checked update actions of [UpdateSongDialog] to [storedRow].
 *
 * A fetched field is copied only when its checkbox is ticked and the fetched
 * value is non-null, through [PropUtils.retainIfModified] - a `modified:` stored
 * value is never clobbered, and a null fetched field keeps the stored value
 * (`retainIfModified` itself returns null for a null fetch, so the per-field
 * null guard is mandatory). A null [fetchedSong] (fetch failed) writes no fetched
 * field at all. Only [Song.totalPlayTimeMs] is reset - `playCount` is never touched.
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
            title = if( selection.title ) PropUtils.retainIfModified( storedRow.title, fetched.title ) ?: storedRow.title else storedRow.title,
            artistsText = if( selection.authors ) PropUtils.retainIfModified( storedRow.artistsText, fetched.artistsText ) ?: storedRow.artistsText else storedRow.artistsText,
            thumbnailUrl = if( selection.thumbnail ) PropUtils.retainIfModified( storedRow.thumbnailUrl, fetched.thumbnailUrl ) ?: storedRow.thumbnailUrl else storedRow.thumbnailUrl
        )
    }
    if( selection.playtime ) {
        updated = updated.copy( totalPlayTimeMs = 0L )
    }
    // Song.copy() only carries constructor parameters: the body property
    // playCount would silently reset to 0 on every update. Restore the stored
    // value - the dialog never touches the per-song play count.
    if( updated !== storedRow ) {
        updated.playCount = storedRow.playCount
    }
    return updated
}

/**
 * Decision gate for the artist-link reconcile of [UpdateSongDialog]: run only
 * when the authors checkbox is ticked, the fresh author list is complete per
 * [ArtistMappingReconcile.isCompleteAuthorList], and the stored artistsText is
 * not `modified:` (the custom value is authoritative - its links are left alone).
 */
internal fun shouldReconcileAuthors(
    authorsChecked: Boolean,
    storedArtistsText: String?,
    parsedNames: List<String>,
    authors: List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>>?
): Boolean =
    authorsChecked &&
        storedArtistsText?.startsWith( MODIFIED_PREFIX, true ) != true &&
        ArtistMappingReconcile.isCompleteAuthorList( parsedNames, authors )

/**
 * The whole per-song update of [UpdateSongDialog]: fetch gate, channel-anchored
 * artist resolution, conditional artist-link reconcile, stored-row rewrite and
 * the conditional cache wipe - everything except the done toast, so the batch
 * action of [runSongUpdateBatch] can reuse it without emitting N toasts.
 * Each step is logged (tag `Database`, level `d`): the requested boxes, the
 * fetch outcome, and the fields actually changed.
 *
 * The stored row is re-read inside the transaction before the merge is applied:
 * the dialog entry may be a 5-field `MediaItem.asSong` snapshot (player menu)
 * whose `likedAt`/`totalPlayTimeMs`/`position`/`isYoutubeSong` carry model
 * defaults (`null`/`0`/`-1`/`false`), so out-of-snapshot fields always come from
 * the stored row, never from the snapshot.
 */
internal suspend fun runSongUpdateCore(
    binder: PlayerServiceModern.Binder?,
    entry: Song,
    selection: SongUpdateSelection
) {
    Timber.tag("Database").d(
        "update start song=%s fetch=%b playtime=%b cache=%b",
        entry.id, selection.needsFetch, selection.playtime, selection.cache
    )

    // Fetch gate: only touch YTM when a fetch checkbox is ticked.
    val fetchedItem: Innertube.SongItem? =
        if( selection.needsFetch ) Innertube.song( entry.id )?.getOrNull() else null
    val fetchedSong = fetchedItem?.asSong
    if( selection.needsFetch ) {
        Timber.tag("Database").d(
            "update fetch song=%s ok=%b title=%s",
            entry.id, fetchedItem != null, fetchedSong?.title
        )
    }

    // Channel-anchored resolution of the fresh author list (mirrors the upsert
    // phase 1): one entry = one artist, an existing channel row keeps its stored
    // name, an entry without a browseId resolves by name.
    val parsedEntries = if( selection.authors && fetchedItem != null ) {
        ArtistMappingReconcile.parseAuthorEntries( fetchedItem.authors )
    } else {
        emptyList()
    }
    val artistDataList = parsedEntries.map { (artistName, browseId) ->
        if( browseId != null ) {
            Triple( artistName, browseId, Database.artistTable.findByIdDirect( browseId ) ?: Artist( id = browseId, name = artistName ) )
        } else {
            Triple( artistName, null, Database.artistTable.findByNameDirect( artistName ) )
        }
    }

    Database.asyncTransaction {
        if( selection.cache ) {
            binder?.cache?.removeResource( entry.id )
            binder?.downloadCache?.removeResource( entry.id )
            WaveformExtractor.deleteWaveform( appContext(), entry.id )
            formatTable.deleteBySongId( entry.id )
            formatTable.updateContentLengthOf( entry.id )
        }

        // Re-read the stored row before writing: the entry may be a 5-field
        // MediaItem snapshot, so out-of-snapshot fields come from the DB.
        val storedRow = songTable.findByIdDirect( entry.id ) ?: entry
        val updatedRow = applySongUpdate( storedRow, fetchedSong, selection )
        songTable.updateReplace( updatedRow )
        Timber.tag("Database").d(
            "update applied song=%s title=%b artistsText=%b thumbnail=%b playtimeReset=%b cacheWiped=%b",
            entry.id,
            updatedRow.title != storedRow.title,
            updatedRow.artistsText != storedRow.artistsText,
            updatedRow.thumbnailUrl != storedRow.thumbnailUrl,
            selection.playtime,
            selection.cache
        )

        // Re-link the artist links from the fresh list (no-op unless the gate
        // passes: authors ticked, complete list, stored value not `modified:`).
        if( shouldReconcileAuthors( selection.authors, storedRow.artistsText, parsedEntries.map { it.first }, fetchedItem?.authors ) ) {
            val dropped = reconcileArtistLinks( entry.id, artistDataList.mapNotNull { it.third } )
            Timber.tag("Database").d(
                "update RECONCILE song=%s dropped=%d latestList=%d artists",
                entry.id, dropped, parsedEntries.size
            )
        }
    }
}

/**
 * The single song « Update » action (player menu + song menu):
 * [runSongUpdateCore] followed by its own done toast.
 */
internal suspend fun runSongUpdate(
    binder: PlayerServiceModern.Binder?,
    entry: Song,
    selection: SongUpdateSelection
) {
    runSongUpdateCore( binder, entry, selection )
    Toaster.done()
}

/** The non-local targets of a batch run - local songs carry no YTM id. */
internal fun remoteTargets( targets: List<Song> ): List<Song> = targets.filterNot( Song::isLocal )

/**
 * The Home Songs toolbar batch « Update » action: applies [runSongUpdateCore]
 * sequentially to every non-local target (local songs are skipped silently -
 * they carry no YTM id). YTM fetches are rate-limited with a random 1-5 s
 * gap between requests (same pattern as HomeSyncService), only when a fetch
 * box is ticked (nothing to throttle otherwise, and the first request needs
 * no leading delay). A single summary
 * toast replaces the per-song done toasts.
 *
 * [onProgress] is invoked on the DATA dispatcher after every processed song
 * with the (done, total) counter; the caller publishes it in a Compose state
 * by hopping to the UI thread. Cancellation is cooperative: between songs the
 * batch returns the processed count (no toast), a cancel landing mid-song
 * propagates [CancellationException] to the caller.
 *
 * @param fetchGapMs gap between two YTM fetches in ms - random 1-5 s by
 * default (same pattern as HomeSyncService), injectable to 0 in unit tests
 * @return the number of processed songs (the non-local targets), 0 when there
 * is nothing to update - in which case no fetch, write or toast happens.
 */
internal suspend fun runSongUpdateBatch(
    binder: PlayerServiceModern.Binder?,
    targets: List<Song>,
    selection: SongUpdateSelection,
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
    songsToUpdate.forEachIndexed { index, entry ->
        // Cooperative cancel: the progress dialog's cancel button stops the
        // batch between songs (a cancel landing mid-song propagates
        // CancellationException to the caller instead).
        if( coroutineContext[ Job ]?.isActive == false ) {
            Timber.tag("Database").d( "update batch cancelled processed=%d of %d", index, songsToUpdate.size )
            return index
        }
        // Random 1-5 s gap between YTM fetches (same pattern as
        // HomeSyncService) to stay gentle on the API.
        if( index > 0 && selection.needsFetch ) delay( fetchGapMs() )
        runSongUpdateCore( binder, entry, selection )
        onProgress( index + 1, songsToUpdate.size )
    }
    Timber.tag("Database").d( "update batch done processed=%d", songsToUpdate.size )
    // toLong() forces the vararg overload: a bare Int second argument would
    // bind to Toaster.s( messageId, duration ) and leave the count unformatted.
    Toaster.s( R.string.updated_songs_count, songsToUpdate.size.toLong() )
    return songsToUpdate.size
}
