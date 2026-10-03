package app.n_zik.android.bridge

import app.n_zik.android.bridge.command.AudioOutputSelector
import app.n_zik.android.bridge.command.CommandResult
import app.n_zik.android.bridge.command.PlayerAccess
import app.n_zik.android.bridge.state.BridgeStateHub
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

private const val TAG = "BridgeAudioOutput"

/**
 * Audio output of one server run (contract §6.2, §8.5). The [hub] holds the revised value
 * (snapshot field and `outputChanged` delta); [onOutputChanged] mirrors it to the phone
 * (mute of the player, output menus). Changes and fallbacks are serialized.
 *
 * - `pc` is only accepted while a WebSocket session is active ([hasActiveSession]).
 * - [fallback] ends a session on `pc`: the phone is paused through the guarded facade
 *   ([player], AD-10), then the output goes back to `phone` — `playbackChanged` then
 *   `outputChanged`, so the phone never sounds again before being paused.
 */
internal class AudioOutputController(
    private val hub: BridgeStateHub,
    private val player: PlayerAccess?,
    private val hasActiveSession: () -> Boolean,
    private val onOutputChanged: (AudioOutput) -> Unit = {},
) : AudioOutputSelector {

    private val mutex = Mutex()

    val current: AudioOutput get() = hub.currentAudioOutput

    override suspend fun select(output: AudioOutput): CommandResult = mutex.withLock {
        if (output == AudioOutput.PC && !hasActiveSession()) return@withLock CommandResult.Rejected
        val delta = hub.setAudioOutput(output) ?: return@withLock CommandResult.Applied(changed = false, revision = hub.currentRevision)
        onOutputChanged(output)
        Timber.tag(TAG).i("Audio output set to $output")
        CommandResult.Applied(changed = true, revision = delta.revision)
    }

    /**
     * Contract §6.2: the active session ended (any reason) or the server stops. Without a
     * session on `pc` nothing happens, so it may be called on every session end.
     */
    suspend fun fallback() = mutex.withLock {
        if (hub.currentAudioOutput != AudioOutput.PC) return@withLock
        // A failed pause must not leave the phone muted for good
        try {
            player?.applyAndSample { current -> current?.pause() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Could not pause the phone on the audio output fallback")
        }
        hub.setAudioOutput(AudioOutput.PHONE)
        onOutputChanged(AudioOutput.PHONE)
        Timber.tag(TAG).i("PC session ended on the PC output: phone paused, output back to the phone")
    }
}
