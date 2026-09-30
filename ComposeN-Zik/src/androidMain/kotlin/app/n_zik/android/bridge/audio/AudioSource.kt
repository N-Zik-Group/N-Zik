package app.n_zik.android.bridge.audio

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.exoplayer.offline.Download
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.it.fast4x.rimusic.utils.audioQualityFormatKey
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.isConnectionMetered
import app.it.fast4x.rimusic.utils.okHttpDataSourceFactory
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.bridge.AudioQuality
import app.n_zik.android.bridge.BridgeContract
import app.n_zik.android.bridge.state.TrackMapping
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.exceptions.ExplicitContentException
import app.n_zik.android.playback.exceptions.UnmatchedSongException
import app.n_zik.android.playback.services.InnerTubeXPlayer
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.playback.services.audioQualityToInnerTubeX
import app.n_zik.android.playback.services.isInnerTubeSessionChangeCancellation
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality

private const val TAG = "BridgeAudio"
private const val OCTET_STREAM = "application/octet-stream"
private const val SNIFF_BYTES = 12
private const val YOUTUBE_VIDEO_ID_LENGTH = 11

/** Pause before retrying a resolution cancelled by an InnerTube session change (as the player). */
private const val SESSION_CHANGE_RETRY_DELAY_MS = 200L

/** What the forge needs to know about a library track (contract §8.1). */
internal class AudioTrackInfo(val durationMs: Long?)

/** Bytes of one audio answer, read off the main thread. */
internal interface AudioStream : Closeable {
    /** Up to [length] bytes into [buffer]; `-1` once every byte asked for was delivered. */
    suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int
}

/** Every remaining byte of this stream (small reads only: a file head). */
internal suspend fun AudioStream.readFully(): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(SNIFF_BYTES)
    while (true) {
        val read = read(buffer, 0, buffer.size)
        if (read < 0) return out.toByteArray()
        out.write(buffer, 0, read)
    }
}

/** A blocking [InputStream] as an [AudioStream]; the caller runs it on [NzikDispatchers.DATA]. */
internal fun InputStream.asAudioStream(): AudioStream = object : AudioStream {
    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int = this@asAudioStream.read(buffer, offset, length)

    override fun close() = this@asAudioStream.close()
}

/** The file behind a source vanished (local file deleted): `404`, not an upstream failure. */
internal class AudioSourceGoneException(message: String) : IOException(message)

/** The bytes of one track, with their total length and real type (contract §8.2). */
internal interface AudioByteSource {
    val length: Long

    /** Real type without parameters (`audio/webm`, `audio/mp4`…). */
    val contentType: String

    /**
     * Opens the bytes [position] until [position] + [length] − 1, on [NzikDispatchers.DATA].
     * The underlying source is opened before this returns, so a failure is known before any
     * header is sent ([AudioSourceGoneException] when the file vanished); the stream then
     * yields exactly [length] bytes or throws an [IOException].
     */
    suspend fun openStream(position: Long, length: Long): AudioStream
}

internal sealed interface AudioOpenResult {
    class Ready(val source: AudioByteSource) : AudioOpenResult

    /** Unknown track, or local file gone: `404`. */
    data object NotFound : AudioOpenResult

    /** The stream could not be obtained upstream: `502 AUDIO_UPSTREAM_FAILED`. */
    data object UpstreamFailed : AudioOpenResult
}

/** Audio side of the phone for the PC bridge (contract §8). */
internal interface AudioLibrary {
    /** The track when it is in the phone's library, else `null` (forge `404`). */
    suspend fun track(trackId: String): AudioTrackInfo?

    /** Resolves the track to its bytes at [quality] (ignored for local and downloaded tracks). */
    suspend fun open(trackId: String, quality: AudioQuality): AudioOpenResult

    companion object {
        /** No phone behind the server (tests): nothing to serve. */
        val EMPTY: AudioLibrary = object : AudioLibrary {
            override suspend fun track(trackId: String): AudioTrackInfo? = null
            override suspend fun open(trackId: String, quality: AudioQuality): AudioOpenResult = AudioOpenResult.NotFound
        }
    }
}

/** Real audio type of contract §8.2: declared type without its parameters, else sniffed. */
internal object AudioTypes {

    /** `audio/webm; codecs="opus"` → `audio/webm`; `null` when blank or not a type. */
    fun normalize(declared: String?): String? =
        declared?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.contains('/') && it != OCTET_STREAM }

    /** Type from the first bytes of a file; `null` when not recognised. */
    fun sniff(bytes: ByteArray): String? {
        fun ascii(offset: Int, text: String) =
            bytes.size >= offset + text.length && String(bytes, offset, text.length, Charsets.US_ASCII) == text
        return when {
            ascii(0, "fLaC") -> "audio/flac"
            ascii(0, "OggS") -> "audio/ogg"
            bytes.size >= 4 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() &&
                bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte() -> "audio/webm"
            ascii(4, "ftyp") -> "audio/mp4"
            ascii(0, "RIFF") && ascii(8, "WAVE") -> "audio/wav"
            ascii(0, "ID3") -> "audio/mpeg"
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0 -> "audio/mpeg"
            else -> null
        }
    }

    fun resolve(declared: String?, head: ByteArray?): String =
        normalize(declared) ?: head?.let(::sniff) ?: OCTET_STREAM
}

/** `low`/`high` → the phone's equivalent setting, `auto` → the phone's own [phoneSetting] (contract §1.1). */
internal fun AudioQuality.toQualityFormat(phoneSetting: AudioQualityFormat): AudioQualityFormat = when (this) {
    AudioQuality.LOW -> AudioQualityFormat.Low
    AudioQuality.HIGH -> AudioQualityFormat.High
    AudioQuality.AUTO -> phoneSetting
}

/** Reads at most [remaining] bytes of [input]; ending early is an [IOException]. */
internal class BoundedInputStream(private val input: InputStream, private var remaining: Long) : InputStream() {
    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (remaining <= 0) return -1
        val read = input.read(b, off, minOf(len.toLong(), remaining).toInt())
        if (read < 0) throw IOException("Source ended $remaining bytes early")
        remaining -= read
        return read
    }

    override fun close() = input.close()
}

/**
 * Bytes [position] until [position] + [remaining] − 1 over successive requests opened by
 * [openAt]: a request that ends before them (a cache span, a bounded request) is followed by
 * a new one at the next position; one that yields nothing at all is a premature end. The
 * first request is opened on construction. Blocking, like [openAt].
 */
internal class SequentialStream(
    private var position: Long,
    private var remaining: Long,
    private val openAt: (position: Long, remaining: Long) -> UpstreamConnection,
) : AudioStream {
    private var current: UpstreamConnection? = if (remaining > 0) openAt(position, remaining) else null
    private var readSinceOpen = 0L

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining <= 0) return -1
        while (true) {
            val connection = current ?: openAt(position, remaining).also {
                current = it
                readSinceOpen = 0
            }
            val read = connection.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            if (read < 0) {
                if (readSinceOpen == 0L) throw IOException("Source ended $remaining bytes early")
                closeCurrent()
                continue
            }
            position += read
            remaining -= read
            readSinceOpen += read
            return read
        }
    }

    private fun closeCurrent() {
        val connection = current ?: return
        current = null
        runCatching { connection.close() }.onFailure { Timber.tag(TAG).w(it, "Could not close an audio source") }
    }

    override fun close() = closeCurrent()
}

/**
 * Sources opened for the PC, remembered per (track, quality) so that its successive range
 * requests read the same bytes: same length, same type, same upstream format. An entry idle
 * for [idleTtlMs] is dropped, the least recently used beyond [maxEntries] too, and a source
 * whose stream fails is forgotten so the next request opens the track afresh.
 */
internal class AudioSourceMemo(
    private val clock: () -> Long = System::currentTimeMillis,
    private val idleTtlMs: Long = BridgeContract.AUDIO_URL_MAX_TTL_MS,
    private val maxEntries: Int = 8,
) {
    private data class Key(val trackId: String, val quality: AudioQuality)

    private class Entry(val source: AudioByteSource, var lastUsedMs: Long)

    private val entries = object : LinkedHashMap<Key, Entry>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>): Boolean = size > maxEntries
    }

    /** One opening at a time per key: a `HEAD` and a `GET` sent together share one source. */
    private val keyLocks = ConcurrentHashMap<Key, Mutex>()

    /** The remembered source of [trackId] at [quality], or the result of [open], remembered when ready. */
    suspend fun getOrOpen(trackId: String, quality: AudioQuality, open: suspend () -> AudioOpenResult): AudioOpenResult {
        val key = Key(trackId, quality)
        return keyLocks.computeIfAbsent(key) { Mutex() }.withLock {
            cached(key)?.let { return@withLock AudioOpenResult.Ready(it) }
            when (val result = open()) {
                is AudioOpenResult.Ready -> AudioOpenResult.Ready(MemoizedSource(key, result.source).also { store(key, it) })
                else -> result
            }
        }
    }

    private fun cached(key: Key): AudioByteSource? = synchronized(entries) {
        val entry = entries[key] ?: return null
        val now = clock()
        if (now - entry.lastUsedMs > idleTtlMs) {
            entries.remove(key)
            return null
        }
        entry.lastUsedMs = now
        entry.source
    }

    private fun store(key: Key, source: AudioByteSource) = synchronized(entries) {
        entries[key] = Entry(source, clock())
    }

    private fun forget(key: Key, source: AudioByteSource) = synchronized(entries) {
        if (entries[key]?.source === source) entries.remove(key)
    }

    private inner class MemoizedSource(private val key: Key, private val delegate: AudioByteSource) : AudioByteSource {
        override val length: Long get() = delegate.length
        override val contentType: String get() = delegate.contentType

        override suspend fun openStream(position: Long, length: Long): AudioStream {
            val stream = try {
                delegate.openStream(position, length)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                forget(key, this)
                throw e
            }
            return object : AudioStream {
                override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    try {
                        stream.read(buffer, offset, length)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        forget(key, this@MemoizedSource)
                        throw e
                    }

                override fun close() = stream.close()
            }
        }
    }
}

/** A media3 [DataSource] already opened, as one upstream request. */
@OptIn(UnstableApi::class)
private class DataSourceConnection(private val source: DataSource) : UpstreamConnection {
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = source.read(buffer, offset, length)
        return if (read == C.RESULT_END_OF_INPUT) -1 else read
    }

    override fun close() = source.close()
}

@OptIn(UnstableApi::class)
private fun DataSource.openConnection(spec: DataSpec): UpstreamConnection {
    try {
        open(spec)
    } catch (e: Exception) {
        runCatching { close() }
        throw e
    }
    return DataSourceConnection(this)
}

/**
 * [AudioLibrary] of the phone. Strictly read-only towards the phone's player: a local track
 * is read through the ContentResolver, a completed download from the download cache
 * singleton without any write sink, and any other track is resolved by the bridge's own
 * [OnlineAudioResolver] (InnerTubeX directly, never `DataSpec.process`) then relayed from
 * upstream: no playback cache, stream cache, `PlaybackDataStore` or `Format` row is written.
 * Opened sources are remembered per (track, quality) by [AudioSourceMemo]. Every call runs on
 * [NzikDispatchers.DATA]. Neither tokens nor URLs are ever logged.
 */
@OptIn(UnstableApi::class)
internal class PhoneAudioLibrary(context: Context) : AudioLibrary {
    private val appContext = context.applicationContext
    private val memo = AudioSourceMemo()
    private val resolver = OnlineAudioResolver(
        resolveUpstream = ::resolveFromInnerTube,
        refreshAfterRejection = { InnerTubeXPlayer.refreshAfterStreamRejection() },
    )

    /** Upstream requests with the player's HTTP stack (same client and cookie), without any cache. */
    private val upstream = UpstreamOpener { stream, position, length ->
        val spec = DataSpec.Builder()
            .setUri(stream.url.toUri())
            .setHttpRequestHeaders(stream.headers)
            .setPosition(position)
            .setLength(length)
            .build()
        appContext.okHttpDataSourceFactory.createDataSource().openConnection(spec)
    }

    override suspend fun track(trackId: String): AudioTrackInfo? = withContext(NzikDispatchers.DATA) {
        Database.songTable.findByIdDirect(trackId)?.let { AudioTrackInfo(TrackMapping.durationTextToMs(it.durationText)) }
    }

    override suspend fun open(trackId: String, quality: AudioQuality): AudioOpenResult =
        memo.getOrOpen(trackId, quality) {
            withContext(NzikDispatchers.DATA) {
                try {
                    if (trackId.startsWith(LOCAL_KEY_PREFIX)) return@withContext openLocal(trackId)
                    downloaded(trackId)?.let { return@withContext AudioOpenResult.Ready(it) }
                    openOnline(trackId, quality)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Could not open the audio of $trackId")
                    AudioOpenResult.UpstreamFailed
                }
            }
        }

    // --- Local file (contract §1.1: quality ignored) ---

    private fun openLocal(trackId: String): AudioOpenResult {
        val mediaStoreId = trackId.removePrefix(LOCAL_KEY_PREFIX).toLongOrNull() ?: return AudioOpenResult.NotFound
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
        val resolver = appContext.contentResolver
        val length = try {
            resolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        } catch (e: FileNotFoundException) {
            null
        } catch (e: SecurityException) {
            Timber.tag(TAG).w(e, "Local file of $trackId not readable")
            null
        } catch (e: IllegalArgumentException) {
            // Provider rejecting the id (no such media): the file is not there
            Timber.tag(TAG).w(e, "Local file of $trackId unknown to its provider")
            null
        } catch (e: IllegalStateException) {
            Timber.tag(TAG).w(e, "Local file of $trackId unavailable from its provider")
            null
        }
        if (length == null || length < 0) return AudioOpenResult.NotFound
        val head = runCatching { readLocalHead(resolver, uri, length) }.getOrNull()
        return AudioOpenResult.Ready(LocalAudioSource(resolver, uri, length, AudioTypes.resolve(resolver.getType(uri), head)))
    }

    private fun readLocalHead(resolver: ContentResolver, uri: Uri, length: Long): ByteArray =
        openLocalStream(resolver, uri, 0, minOf(SNIFF_BYTES.toLong(), length)).use { it.readBytes() }

    private class LocalAudioSource(
        private val resolver: ContentResolver,
        private val uri: Uri,
        override val length: Long,
        override val contentType: String,
    ) : AudioByteSource {
        override suspend fun openStream(position: Long, length: Long): AudioStream =
            openLocalStream(resolver, uri, position, length).asAudioStream()
    }

    // --- Completed download (served as is, whatever the quality) ---

    /** The completed download of [videoId], `null` when there is none or it cannot be read (upstream then). */
    private suspend fun downloaded(videoId: String): AudioByteSource? = try {
        openDownload(videoId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "Download of $videoId unreadable, relaying it from upstream")
        null
    }

    private suspend fun openDownload(videoId: String): AudioByteSource? {
        // The downloads map is only filled once the download manager exists
        MyDownloadHelper.getDownloadManager(appContext)
        if (MyDownloadHelper.downloads.value[videoId]?.state != Download.STATE_COMPLETED) return null
        val cache = MyDownloadHelper.getDownloadCache(appContext)
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(videoId))
        if (length <= 0 || !cache.isCached(videoId, 0, length)) return null
        // No upstream and no write sink: the download cache is only ever read
        val factory = CacheDataSource.Factory().setCache(cache).setCacheWriteDataSinkFactory(null)
        val uri = "$DOWNLOAD_URI_PREFIX$videoId".toUri()
        val openAt = { position: Long, remaining: Long ->
            val spec = DataSpec.Builder().setUri(uri).setKey(videoId).setPosition(position).setLength(remaining).build()
            factory.createDataSource().openConnection(spec)
        }
        val head = SequentialStream(0, minOf(SNIFF_BYTES.toLong(), length), openAt).use { it.readFully() }
        val declared = Database.formatTable.findBySongIdDirect(videoId)?.mimeType
        // Sniffed first: the stored format may describe a later stream at another quality
        val type = AudioTypes.sniff(head) ?: AudioTypes.resolve(declared, null)
        return object : AudioByteSource {
            override val length: Long = length
            override val contentType: String = type
            override suspend fun openStream(position: Long, length: Long): AudioStream = SequentialStream(position, length, openAt)
        }
    }

    // --- Online track, relayed at the requested quality ---

    private suspend fun openOnline(videoId: String, quality: AudioQuality): AudioOpenResult {
        val phoneSetting = appContext.preferences.getEnum(audioQualityFormatKey, AudioQualityFormat.Auto)
        // Same mapping and metered-network rule as the phone's own playback
        val innerTubeQuality = audioQualityToInnerTubeX(quality.toQualityFormat(phoneSetting), appContext.isConnectionMetered())
        val pinned = resolver.resolve(videoId, innerTubeQuality)
        return AudioOpenResult.Ready(OnlineAudioSource(videoId, innerTubeQuality, pinned, resolver, upstream))
    }

    /** InnerTubeX resolution for the bridge only: nothing of the result is stored for the player. */
    private suspend fun resolveFromInnerTube(videoId: String, quality: InnerTubeXAudioQuality): ResolvedStream {
        // The player's own gates (read-only): unmatched id, parental control
        if (videoId.length != YOUTUBE_VIDEO_ID_LENGTH) throw UnmatchedSongException()
        if (appContext.preferences.getBoolean(parentalControlEnabledKey, false) &&
            Database.songTable.findByIdDirect(videoId)?.title?.startsWith(EXPLICIT_PREFIX, true) == true
        ) {
            throw ExplicitContentException()
        }
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
        val data = playbackData(videoId, quality, connectivity)
        val length = data.format.contentLength?.takeIf { it > 0 }
            ?: throw IOException("Upstream size of $videoId unknown")
        val boundedRequests = (data.requireBoundedRange || data.useRangeChunks) && data.rangeChunkSizeBytes > 0
        return ResolvedStream(
            // As the player: the whole format as a URL range, bytes asked for by the Range header
            url = "${data.streamUrl}&range=0-$length",
            headers = data.streamHeaders,
            itag = data.format.itag,
            contentLength = length,
            mimeType = data.format.mimeType,
            chunkSizeBytes = if (boundedRequests) data.rangeChunkSizeBytes else 0L,
            expiresAtMs = System.currentTimeMillis() + data.streamExpiresInSeconds.coerceAtLeast(0) * 1_000L,
        )
    }

    /** [InnerTubeXPlayer.playerResponseForPlayback], retried once after an InnerTube session change. */
    private suspend fun playbackData(
        videoId: String,
        quality: InnerTubeXAudioQuality,
        connectivity: ConnectivityManager,
    ): InnerTubeXPlayer.PlaybackData {
        try {
            return requestPlaybackData(videoId, quality, connectivity)
        } catch (e: CancellationException) {
            if (!isInnerTubeSessionChangeCancellation(e)) throw e
        }
        delay(SESSION_CHANGE_RETRY_DELAY_MS)
        return try {
            requestPlaybackData(videoId, quality, connectivity)
        } catch (e: CancellationException) {
            if (isInnerTubeSessionChangeCancellation(e)) throw IOException("InnerTube session kept changing", e)
            throw e
        }
    }

    private suspend fun requestPlaybackData(videoId: String, quality: InnerTubeXAudioQuality, connectivity: ConnectivityManager) =
        InnerTubeXPlayer.playerResponseForPlayback(
            videoId = videoId,
            audioQuality = quality,
            connectivityManager = connectivity,
        ).getOrThrow()

    private companion object {
        const val DOWNLOAD_URI_PREFIX = "nzik-download://"
    }
}

/** Bytes [position]..[position] + [length] − 1 of a local file; a vanished file is [AudioSourceGoneException]. */
private fun openLocalStream(resolver: ContentResolver, uri: Uri, position: Long, length: Long): InputStream {
    val descriptor = try {
        resolver.openFileDescriptor(uri, "r")
    } catch (e: FileNotFoundException) {
        throw AudioSourceGoneException("Local file gone")
    } catch (e: SecurityException) {
        throw AudioSourceGoneException("Local file no longer readable")
    } catch (e: IllegalArgumentException) {
        throw AudioSourceGoneException("Local file unknown to its provider")
    } catch (e: IllegalStateException) {
        throw AudioSourceGoneException("Local file unavailable from its provider")
    } ?: throw AudioSourceGoneException("Local file gone")
    val input = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
    try {
        input.channel.position(position)
    } catch (e: IOException) {
        input.close()
        throw e
    }
    return BoundedInputStream(input, length)
}
