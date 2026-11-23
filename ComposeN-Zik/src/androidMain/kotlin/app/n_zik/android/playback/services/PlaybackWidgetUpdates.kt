package app.n_zik.android.playback.services

import android.content.Context
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.playback.utils.BitmapProvider
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.widget.NZikWidgetManager
import app.n_zik.android.widget.WidgetAppearance
import app.n_zik.android.widget.WidgetPlaybackSnapshot

/** Owns widget jobs for one playback service lifecycle. */
@UnstableApi
internal class PlaybackWidgetUpdates(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val player: () -> Player,
    private val widgetPlayer: () -> Player,
    private val bitmapProvider: () -> BitmapProvider,
    private val isDestroying: () -> Boolean,
    private val requestRender: () -> Unit,
) {
    private var widgetUpdateJob: Job? = null
    private var widgetRenderJob: Job? = null
    private var widgetArtworkJob: Job? = null

    fun startWidgetUpdates() {
        if (widgetUpdateJob?.isActive == true) return
        widgetUpdateJob = coroutineScope.launch(NzikDispatchers.UI) {
            while (isActive && player().isPlaying) {
                delay(1000)
                if (player().isPlaying && widgetRenderJob?.isActive != true) {
                    requestRender()
                }
            }
        }
    }

    fun stopWidgetUpdates() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = null
    }

    fun refreshArtworkWidgets() {
        widgetArtworkJob?.cancel()
        widgetArtworkJob = coroutineScope.launch(NzikDispatchers.DATA) artworkJob@{
            if (!WidgetAppearance.hasWidgets(context.applicationContext)) return@artworkJob
            withContext(NzikDispatchers.UI) {
                if (isDestroying()) return@withContext
                val widgetPlayer = widgetPlayer()
                val artwork = widgetPlayer.mediaMetadata.artworkUri ?: widgetPlayer.currentMediaItem?.mediaMetadata?.artworkUri
                bitmapProvider().load(artwork) { requestRender() }
            }
        }
    }

    @MainThread
    fun updateWidgets() {
        widgetRenderJob?.cancel()
        val widgetPlayer = widgetPlayer()
        val currentMediaId = widgetPlayer.currentMediaItem?.mediaId
        if (currentMediaId == null) {
            widgetRenderJob = coroutineScope.launch(NzikDispatchers.DATA) {
                NZikWidgetManager.updateIdleWidgets(context.applicationContext)
            }
            return
        }

        val playback = WidgetPlaybackSnapshot(
            durationMs = widgetPlayer.duration.coerceAtLeast(0),
            positionMs = widgetPlayer.currentPosition.coerceAtLeast(0),
            sampledAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            isPlaying = widgetPlayer.isPlaying,
            speed = widgetPlayer.playbackParameters.speed,
        )
        val metadata = widgetPlayer.mediaMetadata
        val artworkUri = metadata.artworkUri ?: widgetPlayer.currentMediaItem?.mediaMetadata?.artworkUri
        val currentBitmap = bitmapProvider().bitmap.takeIf { bitmapProvider().lastUri == artworkUri }

        widgetRenderJob = coroutineScope.launch(NzikDispatchers.DATA) {
            if (!WidgetAppearance.hasWidgets(context.applicationContext)) return@launch
            val songId = currentMediaId.substringAfterLast('/')
            val currentSong = Database.songTable.findByIdDirect(songId)
            val isLiked = currentSong?.likedAt != null

            NZikWidgetManager.updateWidgets(
                context = context.applicationContext,
                title = metadata.title?.toString().orEmpty(),
                artist = metadata.artist?.toString().orEmpty(),
                artworkBitmap = currentBitmap,
                isPlaying = playback.isPlaying,
                isLiked = isLiked,
                artworkUri = artworkUri?.toString(),
                playback = playback,
            )
        }
    }
}
