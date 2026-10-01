package app.n_zik.android.extensions.audiobar.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaDataSource
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import app.n_zik.android.core.profiles.waveformsDir
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.it.fast4x.rimusic.utils.getActiveProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.media3.common.C
import java.io.File
import java.io.FileOutputStream
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.pow
import java.nio.ByteBuffer
import java.io.IOException
import java.util.UUID

object WaveformExtractor {

    val refreshSignal = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    private val gson = Gson()
    private const val TARGET_SAMPLES = 150 // We want roughly 150 amplitude values for the UI
    private const val TAG = "NZik_AudioBar"

    // 40 x 500ms = 20s. A download reaching a terminal state ALWAYS triggers its own
    // deleteWaveform + refreshSignal (MyDownloadService's TerminalStateNotificationHelper,
    // pre-existing, unrelated to this fix) -- so a manual "Update waveform" click racing that
    // is the normal case, not a rare edge case, and can take several seconds to settle (spans
    // merging, temp files finalizing). 6x500ms (3s) was copied from the seekbar's per-round
    // attempt count without also copying its outer "retry forever while on screen" loop, so it
    // gave up (falling back to the NotReady info toast) well before the cache actually
    // stabilized. Now that this runs on requestUpdateWaveform's own persistent scope instead of
    // the menu's rememberCoroutineScope, there is no lifecycle reason to keep the budget short.
    private const val DEFAULT_RETRY_ATTEMPTS = 40
    private const val DEFAULT_RETRY_DELAY_MS = 500L

    // Owned scope for the manual "Update waveform" menu action (SupervisorJob via
    // NzikDispatchers.fireAndForget: one failed request must never cancel another
    // in-flight one). Deliberately NOT the caller's
    // rememberCoroutineScope: menuState.hide() drives an exit animation that decomposes the
    // menu content within ~300ms, but updateWaveform's bounded retry + native extraction can
    // take anywhere from milliseconds to double-digit seconds -- a caller-scoped coroutine
    // would be cancelled long before its result (and toast) could be delivered.
    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)

    fun deleteWaveform(context: Context, mediaId: String) {
        Timber.tag(TAG).d("DELETE [$mediaId] Start deleteWaveform")
        val waveformDir = waveformsDir(context, getActiveProfile(context))
        val savedFile = File(waveformDir, "$mediaId.json")
        if (savedFile.exists()) {
            val deleted = savedFile.delete()
            Timber.tag(TAG).d("DELETE [$mediaId] JSON file existed, delete result=$deleted")
        } else {
            Timber.tag(TAG).d("DELETE [$mediaId] JSON file did NOT exist, nothing to delete")
        }
        val emitted = refreshSignal.tryEmit(System.currentTimeMillis())
        Timber.tag(TAG).d("DELETE [$mediaId] Emitted refreshSignal, accepted=$emitted")
    }

    /**
     * Classifies an extraction exception as [WaveformResult.NotReady] (transient — the download
     * cache had a hole at read time, expected to resolve once the download finishes) or
     * [WaveformResult.Failed] (a real, non-transient extraction error). Extracted as a pure
     * function so the classification logic is unit-testable without mocking media3 internals.
     */
    internal fun classifyExtractionFailure(e: Exception): WaveformResult {
        return if (e is IOException && e.message?.contains("PlaceholderDataSource") == true) {
            Timber.tag(TAG).d("EXTRACT [classifier] PlaceholderDataSource -> NotReady")
            WaveformResult.NotReady
        } else {
            Timber.tag(TAG).e(e, "EXTRACT [classifier] Extraction FAILED: ${e.message}")
            WaveformResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun getOrExtractWaveform(context: Context, mediaId: String, caches: List<Cache>): WaveformResult {
        Timber.tag(TAG).d("EXTRACT [$mediaId] getOrExtractWaveform called, caches=${caches.size}")
        return withContext(NzikDispatchers.DATA) {
            // We use filesDir instead of cacheDir so it survives a "Clear Cache" by the user —
            // the ACTIVE profile's waveforms folder (spec-profile-data-separation).
            val waveformDir = waveformsDir(context, getActiveProfile(context))
            if (!waveformDir.exists()) {
                waveformDir.mkdirs()
                Timber.tag(TAG).d("EXTRACT [$mediaId] Created waveforms directory")
            }

            val savedFile = File(waveformDir, "$mediaId.json")

            // 1. Return from saved file if it exists
            if (savedFile.exists()) {
                Timber.tag(TAG).d("EXTRACT [$mediaId] Found existing JSON (${savedFile.length()} bytes)")
                try {
                    val type = object : TypeToken<List<Int>>() {}.type
                    val amplitudes: List<Int> = gson.fromJson(savedFile.readText(), type)
                    if (amplitudes.size >= TARGET_SAMPLES - 2) {
                        Timber.tag(TAG).d("EXTRACT [$mediaId] Loaded ${amplitudes.size} samples from JSON -> returning cached")
                        return@withContext WaveformResult.Success(amplitudes)
                    } else {
                        Timber.tag(TAG).w("EXTRACT [$mediaId] JSON had only ${amplitudes.size} samples (need ${TARGET_SAMPLES - 2}), will re-extract")
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e("EXTRACT [$mediaId] Failed to parse JSON: ${e.message}")
                }
            } else {
                Timber.tag(TAG).d("EXTRACT [$mediaId] No JSON file found, will extract from cache")
            }

            // Check if the audio file is fully cached
            var validCache: Cache? = null
            for ((index, cache) in caches.withIndex()) {
                val spans = cache.getCachedSpans(mediaId)
                Timber.tag(TAG).d("EXTRACT [$mediaId] Cache[$index] has ${spans.size} spans")
                if (spans.isNotEmpty()) {
                    validCache = cache
                    break
                }
            }

            if (validCache == null) {
                Timber.tag(TAG).w("EXTRACT [$mediaId] No valid cache found -> NoCache")
                return@withContext WaveformResult.NoCache
            }

            Timber.tag(TAG).d("EXTRACT [$mediaId] Found valid cache, starting extraction...")
            // Unique per-attempt filename: two near-simultaneous extractions for the same
            // mediaId (menu update + seekbar retry) must not share one temp file.
            val tempFile = File(context.cacheDir, "temp_audio_${mediaId}_${UUID.randomUUID()}.tmp")
            try {
                val cacheDataSourceFactory = CacheDataSource.Factory()
                    .setCache(validCache)
                    .setUpstreamDataSourceFactory(null) // Only read from cache

                // Copy cached stream to a single temp file for fast native access
                val ds = cacheDataSourceFactory.createDataSource()
                val spec = DataSpec.Builder()
                    .setUri(Uri.parse("https://fake.uri"))
                    .setKey(mediaId)
                    .build()

                val size = ds.open(spec)
                Timber.tag(TAG).d("EXTRACT [$mediaId] DataSource opened, size=$size bytes")
                if (size == 0L) {
                    ds.close()
                    Timber.tag(TAG).w("EXTRACT [$mediaId] DataSource size is 0 -> NotReady")
                    return@withContext WaveformResult.NotReady
                }

                val startTime = System.currentTimeMillis()
                val fos = FileOutputStream(tempFile)
                val buffer = ByteArray(64 * 1024)
                var totalBytes = 0L
                while (true) {
                    val read = ds.read(buffer, 0, buffer.size)
                    if (read <= 0) break
                    fos.write(buffer, 0, read)
                    totalBytes += read
                }
                fos.close()
                ds.close()
                val copyTime = System.currentTimeMillis() - startTime
                Timber.tag(TAG).d("EXTRACT [$mediaId] Copied $totalBytes bytes to temp file in ${copyTime}ms")

                val extractStartTime = System.currentTimeMillis()
                // Use native PCM decoding and RMS calculation for a beautiful, accurate waveform
                val amplitudes = extractAmplitudesNative(tempFile.absolutePath)
                val extractTime = System.currentTimeMillis() - extractStartTime
                Timber.tag(TAG).d("EXTRACT [$mediaId] Native extraction got ${amplitudes.size} samples in ${extractTime}ms")

                if (amplitudes.size >= TARGET_SAMPLES - 2) {
                    savedFile.writeText(gson.toJson(amplitudes))
                    Timber.tag(TAG).d("EXTRACT [$mediaId] Saved JSON (${savedFile.length()} bytes) -> SUCCESS")
                    return@withContext WaveformResult.Success(amplitudes)
                } else {
                    Timber.tag(TAG).w("EXTRACT [$mediaId] Only got ${amplitudes.size} samples (need ${TARGET_SAMPLES - 2}), extraction incomplete")
                    return@withContext WaveformResult.Failed("Only ${amplitudes.size} samples extracted (need ${TARGET_SAMPLES - 2})")
                }
            } catch (e: Exception) {
                return@withContext classifyExtractionFailure(e)
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                    Timber.tag(TAG).d("EXTRACT [$mediaId] Cleaned up temp file")
                }
            }
        }
    }

    /**
     * Single source of truth for the manual "Update waveform" menu flow: deletes the stale
     * JSON, then retries a bounded number of times while the result is [WaveformResult.NotReady]
     * (transient cache state — spans still merging as a download completes). Mirrors the
     * seekbar's own retry semantics (6 attempts / 500ms) so both call sites treat a transient
     * cache state as "keep trying", not "fatal error". [maxAttempts]/[retryDelayMs] are
     * injectable so tests don't have to wait on real delays.
     */
    suspend fun updateWaveform(
        context: Context,
        mediaId: String,
        caches: List<Cache>,
        maxAttempts: Int = DEFAULT_RETRY_ATTEMPTS,
        retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS
    ): WaveformResult {
        deleteWaveform(context, mediaId)
        return retryUntilReady(maxAttempts, retryDelayMs) {
            getOrExtractWaveform(context, mediaId, caches)
        }
    }

    /**
     * Fire-and-forget entry point for the manual "Update waveform" menu action: runs
     * [updateWaveform] on [scope] (object-owned, outlives the calling composable) and delivers
     * [onResult] on the main thread. Callers must not launch [updateWaveform] on their own
     * `rememberCoroutineScope` -- see the [scope] doc comment for why that silently swallows the
     * result once the menu closes.
     */
    fun requestUpdateWaveform(
        context: Context,
        mediaId: String,
        caches: List<Cache>,
        onResult: (WaveformResult) -> Unit
    ) {
        scope.launch {
            val result = updateWaveform(context, mediaId, caches)
            withContext(NzikDispatchers.UI) {
                onResult(result)
            }
        }
    }

    /**
     * Retry loop extracted as a pure helper (dependency-injected [attempt]) so it is
     * unit-testable without waiting on real delays or mocking the extractor itself.
     */
    internal suspend fun retryUntilReady(
        maxAttempts: Int,
        retryDelayMs: Long,
        attempt: suspend () -> WaveformResult
    ): WaveformResult {
        for (i in 1..maxAttempts) {
            val result = attempt()
            if (result !is WaveformResult.NotReady) {
                return result
            }
            Timber.tag(TAG).d("EXTRACT [retryUntilReady] NotReady, attempt $i/$maxAttempts")
            if (i < maxAttempts) {
                delay(retryDelayMs)
            }
        }
        return WaveformResult.NotReady
    }

    private fun extractAmplitudesNative(filePath: String): MutableList<Int> {
        val amplitudes = mutableListOf<Int>()
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(filePath)

            var format: MediaFormat? = null
            var mime: String? = null
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val trackMime = trackFormat.getString(MediaFormat.KEY_MIME)
                if (trackMime != null && trackMime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    mime = trackMime
                    break
                }
            }

            if (audioTrackIndex == -1 || mime == null) return mutableListOf()

            extractor.selectTrack(audioTrackIndex)
            val durationUs = format?.getLong(MediaFormat.KEY_DURATION) ?: 0L
            if (durationUs <= 0) return mutableListOf()

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val windowDurationUs = durationUs / TARGET_SAMPLES
            val bufferInfo = MediaCodec.BufferInfo()

            for (step in 0 until TARGET_SAMPLES) {
                // Seek to the target point
                val targetTimeUs = step * windowDurationUs
                extractor.seekTo(targetTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                var maxAmplitude = 0
                var foundFrame = false
                var tries = 0

                // Decode a few frames around the seek point to get a reading
                while (!foundFrame && tries < 10) {
                    val inIndex = codec.dequeueInputBuffer(5000L)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)
                        val sampleSize = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }

                    var outIndex = codec.dequeueOutputBuffer(bufferInfo, 5000L)
                    while (outIndex >= 0) {
                        if (bufferInfo.size > 0) {
                            val outBuffer = codec.getOutputBuffer(outIndex)
                            if (outBuffer != null) {
                                outBuffer.position(bufferInfo.offset)
                                outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                
                                var sumSquares = 0.0
                                var count = 0
                                while (outBuffer.remaining() >= 2) {
                                    val low = outBuffer.get().toInt() and 0xFF
                                    val high = outBuffer.get().toInt()
                                    val sample = (high shl 8) or low
                                    val shortSample = sample.toShort().toFloat()
                                    sumSquares += (shortSample * shortSample)
                                    count++
                                }
                                
                                if (count > 0) {
                                    // Gnome Decibels uses Linear RMS. It gets dB from GStreamer and converts it BACK
                                    // to linear amplitude (10^(dB/20)). So we just calculate the Linear RMS directly!
                                    val rms = kotlin.math.sqrt(sumSquares / count)
                                    val scaledAmp = rms.toInt()
                                    
                                    if (scaledAmp > maxAmplitude) maxAmplitude = scaledAmp
                                }
                                foundFrame = true
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (foundFrame) break
                        outIndex = codec.dequeueOutputBuffer(bufferInfo, 5000L)
                    }
                    tries++
                }
                
                // Flush codec to clear buffers for the next seek
                codec.flush()
                
                amplitudes.add(maxAmplitude)
            }
        } catch (e: Exception) {
            Timber.tag("NZik_AudioBar").e(e, "Error during native waveform extraction")
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {}
            extractor.release()
        }

        return amplitudes
    }

    /**
     * Ultra-fast waveform extraction that uses the size of the compressed audio frames 
     * as a heuristic for amplitude. Works very well for VBR formats like Opus and AAC 
     * (which are used by YouTube/Piped). Avoids the massive overhead of MediaCodec.
     */
    private fun extractAmplitudesFast(filePath: String): MutableList<Int> {
        val amplitudes = mutableListOf<Int>()
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(filePath)

            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val trackMime = trackFormat.getString(MediaFormat.KEY_MIME)
                if (trackMime != null && trackMime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) return mutableListOf()

            extractor.selectTrack(audioTrackIndex)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            if (durationUs <= 0) return mutableListOf()

            // Count total frames approximately or just seek through
            val buffer = ByteBuffer.allocate(1024 * 64)
            val intervalUs = durationUs / TARGET_SAMPLES

            for (i in 0 until TARGET_SAMPLES) {
                val targetUs = i * intervalUs
                extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                
                var maxFrameSize = 0
                // Read a few frames around this position to get an average/max energy
                for (j in 0 until 10) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break // EOF
                    if (size > maxFrameSize) maxFrameSize = size
                    extractor.advance()
                }
                
                // Frame sizes usually range from 100 to 1000 bytes.
                // Because compressed audio frames don't map linearly to visual amplitude (they look like a fat block),
                // we apply an exponential curve (power of 2.5). Since the UI normalizes to the maximum value,
                // this mathematically perfectly squashes the quiet parts (S/M)^2.5 and makes it look incredibly dynamic!
                val aestheticAmp = kotlin.math.max(0.0, maxFrameSize.toDouble()).pow(2.5)
                amplitudes.add(aestheticAmp.toInt())
            }

        } catch (e: Exception) {
            Timber.tag("NZik_AudioBar").e(e, "Error during fast native waveform extraction")
        } finally {
            extractor.release()
        }

        return amplitudes
    }

    private class ExoMediaDataSource(
        private val dataSourceFactory: DataSource.Factory,
        private val cacheKey: String
    ) : MediaDataSource() {

        private var dataSource: DataSource? = null
        private var currentPosition: Long = -1
        private var size: Long = -1
        private var readCount = 0

        init {
            val ds = dataSourceFactory.createDataSource()
            try {
                val spec = DataSpec.Builder()
                    .setUri(Uri.parse("https://fake.uri"))
                    .setKey(cacheKey)
                    .build()
                size = ds.open(spec)
            } catch (e: Exception) {
                Timber.tag("NZik_AudioBar").e(e, "ExoMediaDataSource init failed for $cacheKey")
            } finally {
                ds.close()
            }
        }

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, readSize: Int): Int {
            return try {
                var ds = this.dataSource
                if (ds == null || position != currentPosition) {
                    ds?.close()
                    ds = dataSourceFactory.createDataSource()
                    this.dataSource = ds
                    val spec = DataSpec.Builder()
                        .setUri(Uri.parse("https://fake.uri"))
                        .setKey(cacheKey)
                        .setPosition(position)
                        .build()
                    val bytesToRead = ds.open(spec)
                    if (bytesToRead == 0L) {
                        return -1
                    }
                    currentPosition = position
                }
                
                var totalRead = 0
                while (totalRead < readSize) {
                    val bytesRead = ds.read(buffer, offset + totalRead, readSize - totalRead)
                    if (bytesRead <= 0) {
                        break
                    }
                    totalRead += bytesRead
                    currentPosition += bytesRead
                }
                return if (totalRead == 0) -1 else totalRead
            } catch (e: Exception) {
                Timber.tag("NZik_AudioBar").e("ExoMediaDataSource readAt failed at $position for $cacheKey: ${e.message}")
                return -1
            }
        }

        override fun getSize(): Long = size

        override fun close() {
            try {
                dataSource?.close()
            } catch (e: Exception) {}
            dataSource = null
        }
    }
}
