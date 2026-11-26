package app.n_zik.android.components.menu

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.ui.components.themed.Enqueue
import app.n_zik.android.ui.components.themed.PlayNext
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.player.MenuQueueEdits
import app.n_zik.android.utils.ToastRelay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@UnstableApi
@Composable
internal fun collectionQueueActions(
    context: Context,
    menuState: MenuState,
    captureQueueEdit: () -> MenuQueueEdits.Request?,
    songs: List<Song>?,
    loadFailed: Boolean = false,
    mediaItemForQueue: (Song) -> MediaItem,
): Pair<MenuIcon, MenuIcon> {
    var pending by remember { mutableStateOf(false) }

    fun queue(playNext: Boolean) {
        if (pending) return
        if (songs == null) {
            if (loadFailed) Toaster.e(R.string.common_load_failed) else ToastRelay.loadingWarning()
            return
        }
        if (songs.isEmpty()) {
            Toaster.e(R.string.player_no_song_found)
            return
        }
        val queueEdit = captureQueueEdit()
        if (queueEdit == null) {
            Toaster.e(R.string.common_error_title)
            return
        }
        pending = true
        val menuKey = menuState.transitionKey
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            try {
                val mediaItems = withContext(NzikDispatchers.DATA) { songs.map(mediaItemForQueue) }
                val queued = queueEdit.queue(mediaItems, context, playNext)
                if (queued && menuState.isCurrentMenu(menuKey)) menuState.hide()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag("CollectionQueueActions").e(error, "Unable to queue collection")
                if (menuState.isCurrentMenu(menuKey)) Toaster.e(R.string.common_error_title)
            } finally {
                pending = false
            }
        }
    }

    return PlayNext(enabled = !pending, dismissOnClick = false) { queue(playNext = true) } to
        Enqueue(dismissOnClick = false, enabled = !pending) { queue(playNext = false) }
}
