package app.n_zik.android.components.dialog.tab

import android.content.ContentUris
import android.provider.MediaStore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.themed.DeleteDialog
import app.n_zik.android.core.utils.Toaster
import timber.log.Timber
import java.io.File
import java.util.Optional
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.appContext
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
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

    override val confirmationTitle: String
        @Composable get() = stringResource(R.string.song_delete_dialog_title)
    override val confirmationAction: String
        @Composable get() = stringResource(R.string.common_action_delete)

    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.song_delete_confirmation )

    override fun onDismiss() {
        song = Optional.empty()
        super.onDismiss()
    }

    override fun onConfirm() {
        song.ifPresent { s ->
            menuState.hide()
            NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                try {
                    Database.awaitWriteBatch(retries = 1) {
                        evictSongCache { binder?.cache?.removeResource(s.id) }
                        evictSongCache { binder?.downloadCache?.removeResource(s.id) }
                        awaitWriteBatch {
                            transaction {
                                songPlaylistMapTable.deleteBySongId(s.id)
                                formatTable.deleteBySongId(s.id)
                                songTable.delete(s)
                            }
                        }
                        WaveformExtractor.deleteWaveform(appContext(), s.id)
                        deleteSongFiles(s)
                    }
                    Toaster.i(R.string.common_delete_success)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.tag("DeleteSongDialog").w("Unable to delete song (%s)", error.javaClass.simpleName)
                    Toaster.e(R.string.common_error_title)
                }
            }
        }

        onDismiss()
    }
}

internal fun evictSongCache(evict: () -> Unit) {
    try {
        evict()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Timber.tag("DeleteSongDialog").w("Unable to evict cached audio (%s)", error.javaClass.simpleName)
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
            Timber.tag("DeleteSongDialog").d("Local audio file deleted: %s", deleted)
        }

        val rowsDeleted = context.contentResolver.delete(uri, null, null)
        Timber.tag("DeleteSongDialog").d("MediaStore rows deleted: %d", rowsDeleted)
    } catch (e: Exception) {
        Timber.tag("DeleteSongDialog").w("Unable to delete local audio file (%s)", e.javaClass.simpleName)
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
