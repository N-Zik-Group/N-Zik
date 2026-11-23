// Ported from Metrolist (GPL-3.0, https://github.com/MetrolistGroup/Metrolist).

package app.n_zik.android.listentogether

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val SOFT_SYNC_THRESHOLD_MS = 50L

internal class ListenTogetherDriftCorrection(
    private val scope: CoroutineScope,
    private val client: ListenTogetherClient,
    private val isHost: () -> Boolean,
    private val localTrackId: () -> String?,
) {
    private companion object {
        private const val HARD_SYNC_THRESHOLD_MS = 750L
        private const val DRIFT_CORRECTION_SPEED = 0.02f
        private const val DRIFT_CHECK_INTERVAL_MS = 250L
    }

    private var driftCorrectionJob: Job? = null
    private var driftBasePlaybackParameters: PlaybackParameters? = null
    private var driftAppliedPlaybackParameters: PlaybackParameters? = null
    private var driftCorrectedPlayer: Player? = null
    private var driftCorrectionGeneration = 0

    fun cancel() {
        driftCorrectionGeneration++
        driftCorrectionJob?.cancel()
        driftCorrectionJob = null
        restoreDriftCorrectionSpeed()
    }

    private fun restoreDriftCorrectionSpeed() {
        val player = driftCorrectedPlayer
        val parameters = driftBasePlaybackParameters
        val appliedParameters = driftAppliedPlaybackParameters
        if (player != null && parameters != null && appliedParameters != null && player.playbackParameters == appliedParameters) {
            player.setPlaybackParameters(parameters)
        }
        driftBasePlaybackParameters = null
        driftAppliedPlaybackParameters = null
        driftCorrectedPlayer = null
    }

    fun start(
        trackId: String?,
        position: Long,
        effectiveAtServerTime: Long?,
    ) {
        cancel()
        val player = ListenTogetherPlayerBridge.player ?: return
        if (!player.playWhenReady || effectiveAtServerTime == null || client.serverTimeNow() == null) return

        val baseParameters = player.playbackParameters
        driftBasePlaybackParameters = baseParameters
        driftCorrectedPlayer = player
        val generation = driftCorrectionGeneration
        driftCorrectionJob =
            scope.launch {
                while (
                    generation == driftCorrectionGeneration &&
                    driftCorrectedPlayer === ListenTogetherPlayerBridge.player &&
                    !isHost() &&
                    player.playWhenReady &&
                    (trackId == null || localTrackId() == trackId)
                ) {
                    val target = client.positionAtServerTime(position, effectiveAtServerTime, isPlaying = true)
                    val drift = target - player.currentPosition
                    val absoluteDrift = kotlin.math.abs(drift)
                    if (absoluteDrift <= SOFT_SYNC_THRESHOLD_MS) break
                    if (absoluteDrift >= HARD_SYNC_THRESHOLD_MS) {
                        ListenTogetherPlayerBridge.seekTo(target)
                        break
                    }

                    val appliedParameters = driftAppliedPlaybackParameters
                    if (appliedParameters != null && player.playbackParameters != appliedParameters) break
                    val multiplier = if (drift > 0L) 1f + DRIFT_CORRECTION_SPEED else 1f - DRIFT_CORRECTION_SPEED
                    val correctedParameters = baseParameters.withSpeed(baseParameters.speed * multiplier)
                    player.setPlaybackParameters(correctedParameters)
                    driftAppliedPlaybackParameters = correctedParameters
                    delay(DRIFT_CHECK_INTERVAL_MS)
                }

                if (generation == driftCorrectionGeneration) {
                    restoreDriftCorrectionSpeed()
                    driftCorrectionJob = null
                }
            }
    }
}
