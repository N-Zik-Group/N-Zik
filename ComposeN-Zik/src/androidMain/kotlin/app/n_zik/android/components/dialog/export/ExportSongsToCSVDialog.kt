package app.n_zik.android.components.dialog.export

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import app.n_zik.android.appContext
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import app.n_zik.android.core.utils.TimeDateUtils
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.core.utils.csv.SongCSV
import java.io.OutputStream
import timber.log.Timber

/** Exports songs and their playlist metadata as a shareable CSV. */
class ExportSongsToCSVDialog private constructor(
    valueState: MutableState<TextFieldValue>,
    activeState: MutableState<Boolean>,
    launcher: ManagedActivityResultLauncher<String, Uri?>,
    private val prepare: () -> Boolean,
    private val unavailableMessage: () -> Int?,
): ExportToFileDialog(valueState, activeState, launcher), MenuIcon, Descriptive {

    companion object {
        private fun writeToCsvFile( outputStream: OutputStream, songs: List<SongCSV> ): Unit =
            csvWriter().open( outputStream ) {
                writeRow(       // Write down needed sections
                    "PlaylistBrowseId",
                    "PlaylistName",
                    "MediaId",
                    "Title",
                    "Artists",
                    "Duration",
                    "ThumbnailUrl"
                )
                flush()

                songs.forEach { it.write( this ) }
                close()
            }

        @Composable
        operator fun invoke(
            playlistName: String,
            songs: () -> List<Song>,
            playlistBrowseId: String = "",
            selectionActive: () -> Boolean = { false },
        ) = rememberExport(
            playlistName,
            playlistBrowseId,
            capture = {
                if (selectionActive()) {
                    val selected = songs().toList()
                    if (selected.isEmpty()) null else ({ selected })
                } else ({ songs() })
            },
            selectionActive = selectionActive,
        )

        @Composable
        internal fun deferred(
            playlistName: String,
            capture: () -> (suspend () -> List<Song>?)?,
            selectionActive: () -> Boolean = { false },
            playlistBrowseId: String = "",
            requireCapture: Boolean = false,
            browsingRestoreScope: (() -> String?)? = null,
        ) = rememberExport(
            playlistName, playlistBrowseId, capture, captureOnOpen = true,
            requireCapture = requireCapture, selectionActive = selectionActive,
            browsingRestoreScope = browsingRestoreScope,
        )

        @Composable
        private fun rememberExport(
            playlistName: String,
            playlistBrowseId: String,
            capture: () -> (suspend () -> List<Song>?)?,
            captureOnOpen: Boolean = false,
            requireCapture: Boolean = false,
            selectionActive: () -> Boolean,
            browsingRestoreScope: (() -> String?)? = null,
        ): ExportSongsToCSVDialog {
            val latestCapture = rememberUpdatedState(capture)
            val latestBrowsingScope = rememberUpdatedState(browsingRestoreScope)
            val pending = remember { mutableStateOf<(suspend () -> List<Song>?)?>(null) }
            val selectionAtOpen = rememberSaveable { mutableStateOf(false) }
            val browsingScopeAtLaunch = rememberSaveable { mutableStateOf<String?>(null) }
            val pendingPlaylistName = rememberSaveable { mutableStateOf<String?>(null) }
            val pendingPlaylistBrowseId = rememberSaveable { mutableStateOf<String?>(null) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
                val captured = pending.value
                val selectedExport = selectionAtOpen.value
                val savedBrowsingScope = browsingScopeAtLaunch.value
                val savedPlaylistName = pendingPlaylistName.value ?: playlistName
                val savedPlaylistBrowseId = pendingPlaylistBrowseId.value ?: playlistBrowseId
                pending.value = null
                selectionAtOpen.value = false
                browsingScopeAtLaunch.value = null
                pendingPlaylistName.value = null
                pendingPlaylistBrowseId.value = null
                if (uri == null) return@rememberLauncherForActivityResult
                val restoreBrowsing = !selectedExport && !requireCapture && captured == null &&
                    latestBrowsingScope.value != null
                val load = resolveExportPayload(selectedExport || requireCapture || restoreBrowsing, captured) {
                    latestCapture.value()
                }
                NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                    try {
                        val readyLoad = if (restoreBrowsing && savedBrowsingScope != null) {
                            awaitRestoredExportPayload(savedBrowsingScope, { latestBrowsingScope.value?.invoke() }) {
                                latestCapture.value()
                            }
                        } else load
                        val songs = readyLoad?.invoke()
                        // The picker can return an existing document, so leave it untouched without a valid payload.
                        if (restoreBrowsing && readyLoad != null && savedBrowsingScope != latestBrowsingScope.value?.invoke()) {
                            Toaster.e(R.string.common_load_failed)
                            return@launch
                        }
                        if (songs == null) {
                            if (readyLoad == null) {
                                Toaster.e(if (selectedExport) R.string.export_selection_lost else R.string.common_load_failed)
                            }
                            return@launch
                        }
                        if (songs.isEmpty()) {
                            Toaster.w(R.string.player_no_song_found)
                            return@launch
                        }
                        val songsToWrite = songs.map { SongCSV(it, savedPlaylistBrowseId, savedPlaylistName) }
                        val stream = checkNotNull(appContext().contentResolver.openOutputStream(uri)) {
                            "Document provider did not open the playlist export"
                        }
                        stream.use { output -> writeToCsvFile(output, songsToWrite) }
                        Toaster.done()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Timber.tag("ExportSongsToCSVDialog").e(error, "Could not write the playlist CSV")
                        Toaster.e(R.string.common_error_title)
                    }
                }
            }
            return ExportSongsToCSVDialog(
                remember(playlistName) { mutableStateOf(TextFieldValue(playlistName)) },
                rememberSaveable { mutableStateOf(false) },
                launcher,
                prepare = {
                    selectionAtOpen.value = selectionActive()
                    val captureNow = captureOnOpen || selectionAtOpen.value
                    pending.value = if (captureNow) latestCapture.value() else null
                    browsingScopeAtLaunch.value = latestBrowsingScope.value?.invoke()
                        ?.takeUnless { selectionAtOpen.value || requireCapture }
                    pendingPlaylistName.value = playlistName
                    pendingPlaylistBrowseId.value = playlistBrowseId
                    !captureNow || pending.value != null
                },
                unavailableMessage = {
                    when {
                        pending.value != null -> null
                        selectionAtOpen.value -> R.string.export_selection_lost
                        requireCapture -> R.string.common_load_failed
                        else -> null
                    }
                },
            )
        }
    }

    override val extension: String = "csv"
    override val iconId: Int = R.drawable.export_playlist
    override val messageId: Int = R.string.playlist_action_export
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.playlist_name_input_hint )
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        if (prepare()) showDialog()
    }

    override fun onSet(newValue: String) {
        val unavailable = unavailableMessage()
        if (unavailable != null) {
            Toaster.e(unavailable)
            hideDialog()
            return
        }
        super.onSet(newValue)
    }

    override fun defaultFileName(): String =
        "${BuildConfig.APP_NAME}_playlist_${TimeDateUtils.localizedDateNoDelimiter()}_${TimeDateUtils.timeNoDelimiter()}"
}

/** A pending selection export can only use the payload captured before opening the picker. */
internal fun <T : Any> resolveExportPayload(selected: Boolean, captured: T?, browsing: () -> T?): T? =
    captured ?: if (selected) null else browsing()

internal suspend fun <T : Any> awaitRestoredExportPayload(
    savedScope: String,
    currentScope: () -> String?,
    capture: () -> T?,
): T? = withTimeoutOrNull(10_000L) {
    snapshotFlow {
        val scope = currentScope()
        scope to if (savedScope == scope) capture() else null
    }.first { (scope, payload) -> (scope != null && scope != savedScope) || payload != null }.second
}?.takeIf { currentScope() == savedScope }


