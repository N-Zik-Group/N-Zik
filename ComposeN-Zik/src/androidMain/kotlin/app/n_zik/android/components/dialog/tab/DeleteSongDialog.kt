package app.n_zik.android.components.dialog.tab

import android.content.ContentUris
import android.provider.MediaStore
import app.n_zik.android.core.database.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.isLocal
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.themed.DeleteDialog
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import java.io.File
import java.util.Optional
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.appContext
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
open class DeleteSongDialog(
    activeState: MutableState<Boolean>,
    menuState: MenuState,
    private val binder: PlayerServiceModern.Binder?
) : DeleteDialog(activeState, menuState) {

    companion object {
        @Composable
        operator fun invoke() = DeleteSongDialog(
            remember { mutableStateOf(false) },
            LocalMenuState.current,
            LocalPlayerServiceBinder.current
        )
    }

    var song = Optional.empty<Song>()

    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.delete_song )

    override fun onDismiss() {
        song = Optional.empty()
        super.onDismiss()
    }

    override fun onConfirm() {
        song.ifPresent { s ->
            Database.asyncTransaction {
                menuState.hide()
                binder?.cache?.removeResource( s.id )
                binder?.downloadCache?.removeResource( s.id )
                songPlaylistMapTable.deleteBySongId( s.id )
                formatTable.deleteBySongId( s.id )
                songTable.delete( s )
                WaveformExtractor.deleteWaveform(appContext(), s.id)
            }

            // File/MediaStore I/O must not run on Main; Toast only after it completes.
            // This class has no Compose scope, so the launch uses a fire-and-forget UI scope:
            //
            // allocated per call, never cancelled (delete + toast must run to completion).
            NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                deleteSongFiles( s )
                Toaster.i( R.string.deleted )
            }
        }

        onDismiss()
    }
}

internal fun deleteLocalSongFile(song: Song) {
    try {
        val mediaStoreId = song.id.substringAfter(LOCAL_KEY_PREFIX).toLongOrNull() ?: return
        val context = appContext()

        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)

        var resolvedPath: String? = null
        context.contentResolver.query(
            uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                resolvedPath = cursor.getString(0)
            }
        }

        val path = resolvedPath
        if (path != null) {
            val deleted = File(path).delete()
            Timber.tag("DeleteSongDialog").d("File.delete(%s) = %s", path, deleted)
        }

        val rowsDeleted = context.contentResolver.delete(uri, null, null)
        Timber.tag("DeleteSongDialog").d("MediaStore.delete rows=%d for: %s", rowsDeleted, song.title)
    } catch (e: Exception) {
        Timber.tag("DeleteSongDialog").e(e, "Failed to delete local file: %s", song.title)
    }
}

/**
 * Deletes the backing file of a local [song] on [NzikDispatchers.DATA]; no-op for non-local songs.
 * [delete] is injectable so the dispatch can be verified without MediaStore.
 */
internal suspend fun deleteSongFiles(song: Song, delete: (Song) -> Unit = ::deleteLocalSongFile) {
    if (song.isLocal) {
        withContext( NzikDispatchers.DATA ) { delete( song ) }
    }
}
