package app.n_zik.android.bridge.audio

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality

private const val TAG = "BridgeAudio"

/** A resolved entry is dropped this long before the upstream URL itself expires. */
private const val EXPIRY_MARGIN_MS = 30_000L

/**
 * Shortest time a resolved entry is reused, whatever the upstream lifetime: a URL announced
 * as (nearly) expired would otherwise be resolved again for every chunk.
 */
internal const val MIN_ENTRY_LIFETIME_MS = 10_000L

/** Deepest cause chain walked when looking for an upstream HTTP status. */
private const val MAX_CAUSE_DEPTH = 16

/**
 * One upstream stream as the bridge resolved it: URL, headers, format, size and type.
 * A relayed source pins the first one it got: every later URL must carry the same format.
 */
internal data class ResolvedStream(
    val url: String,
    val headers: Map<String, String>,
    val itag: Int,
    val contentLength: Long,
    /** Declared type, codecs included (`audio/webm; codecs="opus"`); `null` when unknown. */
    val mimeType: String?,
    /** Largest byte count one upstream request may ask for; `0` = no bound. */
    val chunkSizeBytes: Long,
    val expiresAtMs: Long,
) {
    /** Same bytes behind both URLs: same format, same size, same type. */
    fun sameFormatAs(other: ResolvedStream): Boolean =
        itag == other.itag && contentLength == other.contentLength && mimeType == other.mimeType

    /** Bytes one upstream request may cover from a position with [remaining] bytes left. */
    fun requestLength(remaining: Long): Long =
        if (chunkSizeBytes > 0) minOf(remaining, chunkSizeBytes) else remaining

    override fun toString(): String = "ResolvedStream(itag=$itag, contentLength=$contentLength, mimeType=$mimeType)"
}

/** Upstream answered with an HTTP error status [code]. */
internal class UpstreamStatusException(val code: Int, message: String = "Upstream answered $code") : IOException(message)

/** A new resolution returned another format than the one the stream was announced with. */
internal class UpstreamFormatChangedException(message: String) : IOException(message)

/** Upstream refusals that invalidate a resolved stream URL, as the phone's player treats them. */
internal object StreamRejection {
    private val REJECTION_CODES = setOf(401, 403, 410)

    /** `true` when an upstream `401`, `403` or `410` is anywhere in the cause chain of [error]. */
    fun isRejection(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).any { statusOf(it) in REJECTION_CODES }

    @OptIn(UnstableApi::class)
    private fun statusOf(error: Throwable): Int? = when (error) {
        is UpstreamStatusException -> error.code
        is HttpDataSource.InvalidResponseCodeException -> error.responseCode
        else -> null
    }
}

/**
 * The bridge's own resolution of online tracks (contract §8.2), separate from the phone's
 * player: it never touches the player's caches (`streamUrlCache`, `playbackDataCache`,
 * `PlaybackDataStore`, `Format` rows). Resolved streams are remembered per (track, quality)
 * until shortly before their URL expires. After an upstream refusal, [resolveAfterRejection]
 * drops the entry, waits for the InnerTubeX refresh — one refresh shared by every concurrent
 * refusal — then resolves again.
 *
 * @param resolveUpstream resolves a track at a quality (network); throws on failure
 * @param refreshAfterRejection the InnerTubeX refresh to await after an upstream refusal
 */
internal class OnlineAudioResolver(
    private val resolveUpstream: suspend (videoId: String, quality: InnerTubeXAudioQuality) -> ResolvedStream,
    private val refreshAfterRejection: suspend () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Key(val videoId: String, val quality: InnerTubeXAudioQuality)

    /** A resolved stream and the instant it stops being reused. */
    private class Entry(val stream: ResolvedStream, val staleAtMs: Long)

    private val entries = ConcurrentHashMap<Key, Entry>()

    /** One resolution at a time per key: concurrent requests share its result. */
    private val keyLocks = ConcurrentHashMap<Key, Mutex>()

    private val refreshLock = Mutex()
    private var refreshInFlight: CompletableDeferred<Unit>? = null

    /** The remembered stream of [videoId] at [quality], resolved anew once it is near expiry. */
    suspend fun resolve(videoId: String, quality: InnerTubeXAudioQuality): ResolvedStream {
        val key = Key(videoId, quality)
        return keyLocks.computeIfAbsent(key) { Mutex() }.withLock {
            val now = clock()
            // Stale entries hold signed upstream URLs: none is kept past its use
            entries.values.removeIf { now >= it.staleAtMs }
            entries[key]?.stream ?: resolveUpstream(videoId, quality).also { stream ->
                val resolvedAt = clock()
                entries[key] = Entry(stream, maxOf(stream.expiresAtMs - EXPIRY_MARGIN_MS, resolvedAt + MIN_ENTRY_LIFETIME_MS))
            }
        }
    }

    /**
     * After upstream refused [rejected]: forgets it (unless another request already replaced
     * it), waits for the shared InnerTubeX refresh, then resolves again.
     */
    suspend fun resolveAfterRejection(videoId: String, quality: InnerTubeXAudioQuality, rejected: ResolvedStream): ResolvedStream {
        val key = Key(videoId, quality)
        entries.computeIfPresent(key) { _, entry -> entry.takeUnless { it.stream == rejected } }
        awaitRefresh()
        return resolve(videoId, quality)
    }

    /** Joins the refresh under way, or runs one; a failed refresh still lets the caller retry. */
    private suspend fun awaitRefresh() {
        var owner = false
        val refresh = refreshLock.withLock {
            refreshInFlight ?: CompletableDeferred<Unit>().also {
                refreshInFlight = it
                owner = true
            }
        }
        if (!owner) return refresh.await()
        try {
            refreshAfterRejection()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "InnerTubeX refresh after an upstream refusal failed")
        } finally {
            withContext(NonCancellable) {
                refreshLock.withLock { refreshInFlight = null }
                refresh.complete(Unit)
            }
        }
    }
}

/** One open upstream request. Blocking. */
internal interface UpstreamConnection : Closeable {
    /** Up to [length] bytes into [buffer]; `-1` once this request's bytes are exhausted. */
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
}

/** Opens [length] bytes of [stream] from [position]. Blocking; throws when upstream refuses. */
internal fun interface UpstreamOpener {
    fun open(stream: ResolvedStream, position: Long, length: Long): UpstreamConnection
}

/**
 * An online track relayed from upstream, pinned to the stream it was first resolved to:
 * its [length] and [contentType] never change, and a later resolution to another format
 * fails the stream ([UpstreamFormatChangedException]) instead of relaying other bytes.
 */
internal class OnlineAudioSource(
    private val videoId: String,
    private val quality: InnerTubeXAudioQuality,
    private val pinned: ResolvedStream,
    private val resolver: OnlineAudioResolver,
    private val opener: UpstreamOpener,
) : AudioByteSource {
    override val length: Long = pinned.contentLength
    override val contentType: String = AudioTypes.resolve(pinned.mimeType, null)

    override suspend fun openStream(position: Long, length: Long): AudioStream =
        RelayedStream(position, length).also { it.openFirst() }

    private fun ResolvedStream.requirePinnedFormat(): ResolvedStream {
        if (!sameFormatAs(pinned)) {
            throw UpstreamFormatChangedException("Upstream format of $videoId changed from $pinned to $this")
        }
        return this
    }

    /**
     * Bytes [position] until [position] + [remaining] − 1, over as many upstream requests as
     * the stream's chunk size needs. One upstream refusal per stream, at an opening or while
     * reading, is recovered: the resolver refreshes, then the request is made again.
     */
    private inner class RelayedStream(private var position: Long, private var remaining: Long) : AudioStream {
        private var connection: UpstreamConnection? = null
        private var connectionStream: ResolvedStream? = null
        private var readSinceOpen = 0L
        private var retried = false

        suspend fun openFirst() {
            if (remaining > 0) openAtPosition()
        }

        override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (remaining <= 0) return -1
            while (true) {
                val current = connection ?: openAtPosition()
                val read = try {
                    current.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
                } catch (e: IOException) {
                    val rejected = connectionStream ?: throw e
                    closeConnection()
                    connect(recoverFrom(e, rejected))
                    continue
                }
                if (read < 0) {
                    if (readSinceOpen == 0L) throw IOException("Upstream of $videoId ended $remaining bytes early")
                    // End of one bounded upstream request: the next one starts where it stopped
                    closeConnection()
                    continue
                }
                position += read
                remaining -= read
                readSinceOpen += read
                return read
            }
        }

        private suspend fun openAtPosition(): UpstreamConnection {
            val stream = resolver.resolve(videoId, quality).requirePinnedFormat()
            return try {
                connect(stream)
            } catch (e: IOException) {
                connect(recoverFrom(e, stream))
            }
        }

        /** The fresh stream to retry with after [error], or [error] itself when it cannot be recovered. */
        private suspend fun recoverFrom(error: IOException, rejected: ResolvedStream): ResolvedStream {
            if (retried || !StreamRejection.isRejection(error)) throw error
            retried = true
            Timber.tag(TAG).w("Upstream refused the stream of $videoId, resolving it again")
            return resolver.resolveAfterRejection(videoId, quality, rejected).requirePinnedFormat()
        }

        private fun connect(stream: ResolvedStream): UpstreamConnection =
            opener.open(stream, position, stream.requestLength(remaining)).also {
                connection = it
                connectionStream = stream
                readSinceOpen = 0
            }

        private fun closeConnection() {
            val current = connection ?: return
            connection = null
            connectionStream = null
            runCatching { current.close() }.onFailure { Timber.tag(TAG).w(it, "Could not close an upstream request") }
        }

        override fun close() = closeConnection()
    }
}
