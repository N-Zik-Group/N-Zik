package app.n_zik.android.ui.screens.localplaylist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle

import app.n_zik.android.R
import app.n_zik.android.cleanPrefix
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.InProgressDialog
import app.n_zik.android.components.dialog.search.MatchResultsDialog
import app.n_zik.android.core.matching.SongMatch
import app.n_zik.android.core.matching.SongMatchRequest
import app.n_zik.android.core.matching.SongMatchStatus
import app.n_zik.android.core.matching.loadSongMatchFailures
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.isUnmatched
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.utils.coroutines.NzikDispatchers

internal data class LocalPlaylistMatchAction(
    val button: MenuIcon,
    val isRunning: Boolean,
)

@Composable
internal fun localPlaylistSongMatching(
    playlistId: Long,
    inPreview: Boolean,
    onDeviceOnly: Boolean,
    offlineModeState: State<Boolean>,
    membershipEditableState: State<Boolean>,
    items: List<Song>,
    getSongs: () -> List<Song>,
): LocalPlaylistMatchAction {
    val context = LocalContext.current
    val offlineMode by offlineModeState
    val membershipEditable by membershipEditableState
    var hiddenMatchRequestId by remember(playlistId) { mutableStateOf<String?>(null) }

    val matchScopeKey = "local-playlist:$playlistId"
    var matchCandidates by remember(matchScopeKey) { mutableStateOf<List<Song>?>(null) }
    val currentSongMatch by SongMatch.state.collectAsStateWithLifecycle(context = NzikDispatchers.DATA)
    val matchResults by SongMatch.results.collectAsStateWithLifecycle(context = NzikDispatchers.DATA)
    val songMatch = if (inPreview || onDeviceOnly) null else {
        currentSongMatch?.takeIf { it.scopeKey == matchScopeKey } ?: matchResults[matchScopeKey]
    }

    val openMatch by rememberUpdatedState<() -> Unit> {
        val running = SongMatch.state.value?.takeIf {
            it.scopeKey == matchScopeKey && it.status == SongMatchStatus.Running
        }
        if (running != null) {
            hiddenMatchRequestId = null
        } else {
            matchCandidates = getSongs().filter { it.isUnmatched }.distinctBy(Song::id)
                .takeIf { it.isNotEmpty() }
        }
    }

    fun startMatch(songs: List<Song>) {
        val request = SongMatchRequest(matchScopeKey, songs, playlistId)
        if (!SongMatch.start(context, request)) Toaster.e(R.string.song_matching_failed_message)
    }

    DialogPresence(target = matchCandidates) { candidates ->
        ConfirmationDialog(
            title = stringResource(R.string.song_matching_dialog_title),
            confirmText = stringResource(R.string.song_matching_dialog_title),
            text = stringResource(R.string.song_matching_all_confirmation, candidates.size),
            onDismiss = { matchCandidates = null },
            onConfirm = { startMatch(candidates) },
        )
    }

    val runningMatch = songMatch?.takeIf { it.status == SongMatchStatus.Running }
    val showMatchProgress = runningMatch != null && runningMatch.requestId != hiddenMatchRequestId
    DialogPresence(visible = showMatchProgress) {
        InProgressDialog(
            total = songMatch?.total ?: 0,
            done = songMatch?.processed ?: 0,
            text = stringResource(R.string.song_matching_progress_title),
            message = stringResource(R.string.song_matching_background_message),
            onDismiss = { hiddenMatchRequestId = songMatch?.requestId },
            onCancel = { songMatch?.let { SongMatch.cancel(it.requestId) } },
        )
    }

    val matchResult = songMatch?.takeIf {
        it.total > 0 && (
            it.status == SongMatchStatus.Finished ||
                it.status == SongMatchStatus.Cancelled ||
                (it.status == SongMatchStatus.Failed && it.failedIds.isNotEmpty())
            )
    }
    DialogPresence(target = matchResult) { result ->
        key(result.requestId) {
            val failedSongs by produceState(emptyList<Song>(), result.requestId) {
                value = loadSongMatchFailures(result)
            }
            MatchResultsDialog(
                matched = result.matched,
                failed = result.failed,
                merged = result.merged,
                failedSongs = failedSongs,
                cancelled = result.status == SongMatchStatus.Cancelled,
                unprocessed = result.unprocessed,
                onRetry = if (!offlineMode && membershipEditable && failedSongs.isNotEmpty()) {
                    {
                        if (!offlineMode && membershipEditable &&
                            SongMatch.results.value[matchScopeKey]?.requestId == result.requestId
                        ) {
                            startMatch(failedSongs)
                        }
                    }
                } else null,
                onDismiss = { SongMatch.dismiss(result.requestId) },
            )
        }
    }

    val unshownMatch = songMatch?.takeIf {
        (it.status == SongMatchStatus.Failed && it.failedIds.isEmpty()) ||
            (it.total == 0 && (it.status == SongMatchStatus.Finished || it.status == SongMatchStatus.Cancelled))
    }
    LaunchedEffect(unshownMatch?.requestId) {
        if (unshownMatch != null) {
            if (unshownMatch.status == SongMatchStatus.Failed) {
                Toaster.e(R.string.song_matching_failed_message)
            }
            SongMatch.dismiss(unshownMatch.requestId)
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val matchRunning = currentSongMatch?.status == SongMatchStatus.Running ||
        currentSongMatch?.status == SongMatchStatus.Cancelling
    val canStartAutomaticMatch = !inPreview && !onDeviceOnly && !offlineMode && membershipEditable &&
        matchCandidates == null && !matchRunning && songMatch == null
    val automaticMatchSongs = remember(canStartAutomaticMatch, items) {
        if (!canStartAutomaticMatch) emptyList()
        else items.filter { song ->
            val importedId = (cleanPrefix(song.title) + song.artistsText.orEmpty()).filter { it.isLetterOrDigit() }
            song.id == importedId
        }
    }
    LaunchedEffect(lifecycle, automaticMatchSongs, matchScopeKey) {
        if (automaticMatchSongs.isEmpty()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            SongMatch.start(
                context,
                SongMatchRequest(matchScopeKey, automaticMatchSongs, playlistId, automatic = true),
            )
        }
    }

    val matchAlbumButton = remember {
        object : MenuIcon, Descriptive {
            override val iconId: Int = R.drawable.alert
            override val messageId: Int = R.string.song_matching_action_open
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() { openMatch() }
            override fun onLongClick() {}
        }
    }

    return LocalPlaylistMatchAction(button = matchAlbumButton, isRunning = runningMatch != null)
}
