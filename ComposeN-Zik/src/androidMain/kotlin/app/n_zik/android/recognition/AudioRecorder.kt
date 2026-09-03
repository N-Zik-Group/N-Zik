package app.n_zik.android.recognition

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max

class AudioRecorder(private val scope: CoroutineScope) {

    private var instance: AudioRecord? = null
    private var job: Job? = null
    private val mutex = Mutex()

    private val _active   = MutableStateFlow(false)
    private val _duration = MutableStateFlow(0)
    private val _buffer   = MutableStateFlow(ByteArray(0))

    val active   = _active.asStateFlow()
    val duration = _duration.asStateFlow()
    val buffer   = _buffer.asStateFlow()

    fun start() {
        scope.launch {
            mutex.withLock {
                if (_active.value) return@launch
                runCatching {
                    Timber.tag(TAG).d("Creating microphone recorder")
                    instance = createMicRecorder()
                    val state = instance?.state
                    Timber.tag(TAG).d("AudioRecord state: $state (INITIALIZED=${AudioRecord.STATE_INITIALIZED})")
                    if (state != AudioRecord.STATE_INITIALIZED) {
                        instance?.release()
                        instance = null
                        reset(false)
                        return@launch
                    }
                    instance?.startRecording()
                    Timber.tag(TAG).d("startRecording() called, recordingState=${instance?.recordingState}")
                    reset(true)
                    job = scope.launch(Dispatchers.IO) { loop() }
                }.onFailure {
                    Timber.tag(TAG).e(it, "Failed to start audio recorder")
                    instance?.release()
                    instance = null
                    reset(false)
                }
            }
        }
    }

    fun stop() {
        scope.launch {
            mutex.withLock {
                if (!_active.value) return@withLock
                job?.cancelAndJoin()
                job = null
                runCatching {
                    if (instance?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        instance?.stop()
                    }
                    instance?.release()
                }.onFailure {
                    Timber.tag(TAG).w(it, "Error stopping recorder")
                }
                instance = null
                reset(false)
            }
        }
    }

    private fun createMicRecorder(): AudioRecord {
        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            BUFFER_SIZE
        )
    }

    private suspend fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val out = ByteArrayOutputStream()
        runCatching {
            while (coroutineContext.isActive) {
                val chunk = ByteArray(BUFFER_SIZE)
                val read  = instance?.read(chunk, 0, chunk.size) ?: 0
                if (read > 0) {
                    out.write(chunk, 0, read)
                    val bytes = out.toByteArray()
                    _buffer.emit(bytes)
                    _duration.emit(bytes.size / (SAMPLE_RATE * SAMPLE_WIDTH * CHANNEL_COUNT))
                }
            }
        }.onFailure {
            Timber.tag(TAG).e(it, "Recording loop failed")
            reset(false)
        }
    }

    private suspend fun reset(active: Boolean) {
        _active.emit(active)
        _duration.emit(0)
        _buffer.emit(ByteArray(0))
    }

    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG  = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT    = AudioFormat.ENCODING_PCM_16BIT
        private const val CHANNEL_COUNT   = 1
        private const val SAMPLE_WIDTH    = 2  // bytes per 16-bit sample
        private val BUFFER_SIZE = max(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2,
            SAMPLE_RATE * SAMPLE_WIDTH * CHANNEL_COUNT
        )
    }
}
