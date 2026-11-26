package app.n_zik.android.components.playlist

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.PlaylistLinkKind
import app.n_zik.android.core.database.playlistLinkKind
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.models.Playlist
import app.n_zik.android.utils.canPushToYTM
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.syncPushPlaylistKey
import app.n_zik.innertube.YtMusic

@Composable
internal fun DeletePlaylistConfirmation(playlist: Playlist, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ConfirmationDialog(
        title = stringResource(R.string.playlist_delete_dialog_title),
        text = playlistDeletionConfirmationText(stringResource(R.string.playlist_delete_confirmation), listOf(playlist)),
        confirmText = stringResource(R.string.common_action_delete),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
internal fun playlistDeletionConfirmationText(text: String, playlists: List<Playlist>): String {
    val context = LocalContext.current
    return if (playlists.any { it.youtubeDeletionId() != null } && playlistDeletePushEnabled(context)) {
        text + "\n\n" + stringResource(R.string.playlist_deletion_youtube_confirmation)
    } else text
}

private fun playlistDeletePushEnabled(context: Context): Boolean =
    isYouTubeSyncEnabled() && canPushToYTM() && context.preferences.getBoolean(syncPushPlaylistKey, false)

internal fun Playlist.youtubeDeletionId(): String? {
    if (!isYoutubePlaylist || !isEditable || playlistLinkKind(browseId) != PlaylistLinkKind.YouTubePlaylist) return null
    return browseId?.takeUnless { it.startsWith(MODIFIED_PREFIX) }
        ?.removePrefix("VL")?.takeUnless { it in setOf("LM", "SE") }
}

internal fun launchLocalPlaylistDeletion(
    context: Context,
    confirmedPlaylists: List<Playlist>,
    onDeleted: () -> Unit = {},
) {
    val playlistIds = confirmedPlaylists.map(Playlist::id).distinct()
    if (playlistIds.isEmpty()) return
    val appContext = context.applicationContext
    val confirmedRemoteIds = if (playlistDeletePushEnabled(appContext)) {
        confirmedPlaylists.mapNotNull { playlist -> playlist.youtubeDeletionId()?.let { playlist.id to it } }.toMap()
    } else emptyMap()
    val owner = ProfileWorkOwner.capture(appContext) ?: return Toaster.e(R.string.playlist_deletion_failed)
    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
        try {
            val deleted = Database.awaitWriteBatch {
                if (!ProfileWorkOwner.isBound(appContext, owner)) return@awaitWriteBatch emptyList<Playlist>()
                val deletedRows = mutableListOf<Playlist>()
                transaction {
                    for (id in playlistIds) {
                        val playlist = playlistTable.findById(id).first() ?: continue
                        if (playlistTable.delete(playlist) > 0) deletedRows += playlist
                    }
                }
                deletedRows
            }
            if (!ProfileWorkOwner.isBound(appContext, owner)) return@launch
            if (deleted.isEmpty()) {
                Toaster.e(R.string.playlist_deletion_failed)
                return@launch
            }
            withContext(NzikDispatchers.UI) { onDeleted() }
            if (deleted.size < playlistIds.size) {
                Toaster.e(R.string.playlist_deletion_partial)
            } else {
                Toaster.s(R.string.playlist_deletion_local_success)
            }
            val remoteIds = deleted.mapNotNull { playlist ->
                confirmedRemoteIds[playlist.id]?.takeIf { it == playlist.youtubeDeletionId() }
            }.distinct()
            if (remoteIds.isEmpty() || !playlistDeletePushEnabled(appContext)) return@launch

            var failed = false
            for (id in remoteIds) {
                if (!ProfileWorkOwner.isBound(appContext, owner)) return@launch
                if (!isNetworkConnected(appContext)) {
                    failed = true
                    break
                }
                val response = YtMusic.deletePlaylist(id)
                val error = response.exceptionOrNull()
                if (error is CancellationException) throw error
                if (response.isFailure) failed = true
            }
            if (failed) Toaster.e(R.string.playlist_deletion_remote_failed)
            else Toaster.s(R.string.playlist_deletion_remote_success)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.tag("DeleteLocalPlaylists").e(error, "Playlist deletion failed")
            Toaster.e(R.string.playlist_deletion_failed)
        }
    }
}
