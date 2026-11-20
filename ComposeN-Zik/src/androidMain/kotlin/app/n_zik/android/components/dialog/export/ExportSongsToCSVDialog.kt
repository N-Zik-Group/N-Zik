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
import kotlinx.coroutines.launch
import app.n_zik.android.core.utils.TimeDateUtils
import app.n_zik.android.core.utils.csv.SongCSV
import java.io.OutputStream

/**
 * Create a custom CSV file with replicable information of provided songs (shareable
 * between users).
 *
 * Columns: `PlaylistBrowseId | PlaylistName | MediaId | Title | Artists | Duration | ThumbnailUrl`
 */
class ExportSongsToCSVDialog private constructor(
    valueState: MutableState<TextFieldValue>,
    activeState: MutableState<Boolean>,
    launcher: ManagedActivityResultLauncher<String, Uri?>,
    private val prepare: () -> Boolean
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
            playlistBrowseId: String = ""
        ) = rememberExport(playlistName, playlistBrowseId, { { songs() } })

        @Composable
        internal fun deferred(
            playlistName: String,
            capture: () -> (suspend () -> List<Song>?)?,
        ) = rememberExport(playlistName, "", capture, captureOnOpen = true)

        @Composable
        private fun rememberExport(
            playlistName: String,
            playlistBrowseId: String,
            capture: () -> (suspend () -> List<Song>?)?,
            captureOnOpen: Boolean = false,
        ): ExportSongsToCSVDialog {
            val latestCapture = rememberUpdatedState(capture)
            val pending = remember { mutableStateOf<(suspend () -> List<Song>?)?>(null) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
                val captured = pending.value
                pending.value = null
                if (uri == null) return@rememberLauncherForActivityResult
                // Android may deliver the document result to a recreated screen.
                val load = captured ?: latestCapture.value() ?: return@rememberLauncherForActivityResult
                NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                    val songsToWrite = load()?.map { SongCSV(it, playlistBrowseId, playlistName) } ?: return@launch
                    appContext().contentResolver.openOutputStream(uri)?.use { output ->
                        writeToCsvFile(output, songsToWrite)
                    }
                }
            }
            return ExportSongsToCSVDialog(
                remember(playlistName) { mutableStateOf(TextFieldValue(playlistName)) },
                rememberSaveable { mutableStateOf(false) },
                launcher,
            ) {
                if (!captureOnOpen) true else {
                    pending.value = latestCapture.value()
                    pending.value != null
                }
            }
        }
    }

    override val extension: String = "csv"
    override val iconId: Int = R.drawable.export_outline
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

    override fun defaultFileName(): String =
        "${BuildConfig.APP_NAME}_playlist_${TimeDateUtils.localizedDateNoDelimiter()}_${TimeDateUtils.timeNoDelimiter()}"
}


