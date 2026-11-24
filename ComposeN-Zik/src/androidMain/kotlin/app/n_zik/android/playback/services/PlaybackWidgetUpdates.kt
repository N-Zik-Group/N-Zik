package app.n_zik.android.playback.services

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.playback.utils.BitmapProvider
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.widget.NZikWidgetManager
import app.n_zik.android.widget.WidgetAppearance
import app.n_zik.android.widget.WidgetPlaybackSnapshot
import app.n_zik.android.widget.WidgetTimingUpdate

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
    private var timingRender: TimingRender? = null

    private data class TimingRender(
        val mediaId: String,
        val profileId: String,
        val updates: List<WidgetTimingUpdate>,
    )

    fun startWidgetUpdates() {
        if (widgetUpdateJob?.isActive == true) return
        widgetUpdateJob = coroutineScope.launch(NzikDispatchers.UI) {
            while (isActive && player().isPlaying) {
                delay(1000)
                if (player().isPlaying && !isDestroying()) updateTiming()
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
        timingRender = null
        val widgetPlayer = widgetPlayer()
        val currentMediaId = widgetPlayer.currentMediaItem?.mediaId
        if (currentMediaId == null) {
            widgetRenderJob = coroutineScope.launch(NzikDispatchers.DATA) {
                NZikWidgetManager.updateIdleWidgets(context.applicationContext)
            }
            return
        }

        val playback = widgetPlayer.snapshotTiming()
        val metadata = widgetPlayer.mediaMetadata
        val artworkUri = metadata.artworkUri ?: widgetPlayer.currentMediaItem?.mediaMetadata?.artworkUri
        val currentBitmap = bitmapProvider().bitmap.takeIf { bitmapProvider().lastUri == artworkUri }

        widgetRenderJob = coroutineScope.launch(NzikDispatchers.DATA) {
            val app = context.applicationContext
            if (!WidgetAppearance.hasWidgets(app)) return@launch
            val profileId = getActiveProfile(app)
            val songId = currentMediaId.substringAfterLast('/')
            val likeState = Database.songTable.likeState(songId).first()
            val updates = mutableListOf<WidgetTimingUpdate>()

            NZikWidgetManager.updateWidgets(
                context = app,
                title = metadata.title?.toString().orEmpty(),
                artist = metadata.artist?.toString().orEmpty(),
                artworkBitmap = currentBitmap,
                isPlaying = playback.isPlaying,
                likeState = likeState,
                artworkUri = artworkUri?.toString(),
                playback = playback,
                timingUpdates = updates,
            )
            withContext(NzikDispatchers.UI) {
                if (!isDestroying() && updates.isNotEmpty()) {
                    timingRender = TimingRender(currentMediaId, profileId, updates)
                }
            }
        }
    }

    private fun updateTiming() {
        val render = timingRender ?: return
        if (widgetRenderJob?.isActive == true) return
        val widgetPlayer = widgetPlayer()
        if (render.mediaId != widgetPlayer.currentMediaItem?.mediaId) return
        val playback = widgetPlayer.snapshotTiming()
        if (!playback.isPlaying || playback.durationMs <= 0) return

        widgetRenderJob = coroutineScope.launch(NzikDispatchers.DATA) {
            val app = context.applicationContext
            if (getActiveProfile(app) != render.profileId) return@launch
            val manager = AppWidgetManager.getInstance(app)
            render.updates.forEach { it.publish(manager, app.packageName, playback) }
        }
    }

    private fun Player.snapshotTiming() = WidgetPlaybackSnapshot(
        durationMs = duration.coerceAtLeast(0),
        positionMs = currentPosition.coerceAtLeast(0),
        sampledAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
        isPlaying = isPlaying,
        speed = playbackParameters.speed,
    )
}
