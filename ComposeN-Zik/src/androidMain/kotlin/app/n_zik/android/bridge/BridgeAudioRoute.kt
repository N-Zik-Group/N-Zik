package app.n_zik.android.bridge

import app.n_zik.android.bridge.audio.AudioLibrary
import app.n_zik.android.bridge.audio.AudioOpenResult
import app.n_zik.android.bridge.audio.AudioSourceGoneException
import app.n_zik.android.bridge.audio.AudioStream
import app.n_zik.android.bridge.audio.AudioTokenCheck
import app.n_zik.android.bridge.audio.AudioTokens
import app.n_zik.android.bridge.audio.ByteRange
import app.n_zik.android.bridge.audio.ByteRanges
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException
import timber.log.Timber

/** Contract §8.2: the token is checked on every request, before anything is opened. */
internal suspend fun ApplicationCall.handleBridgeAudio(
    audioLibrary: AudioLibrary,
    audioTokens: AudioTokens,
    isDevicePaired: (String) -> Boolean,
    headOnly: Boolean,
) {
    val trackId = parameters["trackId"].orEmpty()
    val token = request.queryParameters[BridgeContract.AUDIO_TOKEN_PARAM]
    when (val check = audioTokens.check(token, trackId, isDevicePaired)) {
        AudioTokenCheck.Invalid ->
            respondAudioError(headOnly, HttpStatusCode.Forbidden, BridgeErrorCode.AUDIO_URL_INVALID, "Invalid audio URL")
        AudioTokenCheck.Revoked ->
            respondAudioError(headOnly, HttpStatusCode.Unauthorized, BridgeErrorCode.DEVICE_REVOKED, "Unknown or revoked device")
        AudioTokenCheck.Expired ->
            respondAudioError(headOnly, HttpStatusCode.Forbidden, BridgeErrorCode.AUDIO_URL_EXPIRED, "Audio URL expired")
        is AudioTokenCheck.Valid -> serveAudio(audioLibrary, trackId, check.quality, headOnly)
    }
}

/**
 * Whole file (`200`) or one range (`206`), with the real type; `HEAD` gets the same headers
 * only. The range headers are set once the source is open, so an error never carries them.
 */
private suspend fun ApplicationCall.serveAudio(
    audioLibrary: AudioLibrary,
    trackId: String,
    quality: AudioQuality,
    headOnly: Boolean,
) {
    val source = when (val opened = audioLibrary.open(trackId, quality)) {
        is AudioOpenResult.Ready -> opened.source
        AudioOpenResult.NotFound -> return respondAudioNotFound(headOnly)
        AudioOpenResult.UpstreamFailed -> return respondUpstreamFailed(headOnly)
    }
    val total = source.length
    val range = ByteRanges.resolve(request.headers[HttpHeaders.Range], total)
    val (status, start, length) = when (range) {
        ByteRange.Full -> Triple(HttpStatusCode.OK, 0L, total)
        is ByteRange.Partial -> Triple(HttpStatusCode.PartialContent, range.start, range.length)
        ByteRange.Unsatisfiable -> {
            response.header(HttpHeaders.AcceptRanges, ByteRanges.BYTES_UNIT)
            response.header(HttpHeaders.ContentRange, ByteRanges.unsatisfiedRange(total))
            return respondAudioError(
                headOnly,
                HttpStatusCode.RequestedRangeNotSatisfiable,
                BridgeErrorCode.RANGE_NOT_SATISFIABLE,
                "Range outside the file",
            )
        }
    }
    val type = runCatching { ContentType.parse(source.contentType) }.getOrDefault(ContentType.Application.OctetStream)
    if (headOnly) {
        setRangeHeaders(range, total)
        return respond(AudioHeadContent(status, type, length))
    }
    // Opened before any header leaves: a source that cannot be reached is still a clean error
    val stream = try {
        withContext(NzikDispatchers.DATA) { source.openStream(start, length) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: AudioSourceGoneException) {
        Timber.tag(BRIDGE_SERVER_TAG).w(e, "Audio file of $trackId gone")
        return respondAudioNotFound(headOnly = false)
    } catch (e: Exception) {
        Timber.tag(BRIDGE_SERVER_TAG).w(e, "Audio source of $trackId unavailable")
        return respondUpstreamFailed(headOnly = false)
    }
    try {
        setRangeHeaders(range, total)
        respond(AudioBodyContent(stream, status, type, length))
    } finally {
        withContext(NonCancellable + NzikDispatchers.DATA) {
            runCatching { stream.close() }.onFailure { Timber.tag(BRIDGE_SERVER_TAG).w(it, "Could not close an audio source") }
        }
    }
}

/** `Accept-Ranges` always, `Content-Range` on a `206` (contract §8.2). */
private fun ApplicationCall.setRangeHeaders(range: ByteRange, total: Long) {
    response.header(HttpHeaders.AcceptRanges, ByteRanges.BYTES_UNIT)
    if (range is ByteRange.Partial) response.header(HttpHeaders.ContentRange, ByteRanges.contentRange(range, total))
}

private suspend fun ApplicationCall.respondAudioNotFound(headOnly: Boolean) =
    respondAudioError(headOnly, HttpStatusCode.NotFound, BridgeErrorCode.NOT_FOUND, "Unknown track")

private suspend fun ApplicationCall.respondUpstreamFailed(headOnly: Boolean) =
    respondAudioError(headOnly, HttpStatusCode.BadGateway, BridgeErrorCode.AUDIO_UPSTREAM_FAILED, "The audio could not be obtained")

/** The error model of contract §3; a `HEAD` answer never carries a body. */
private suspend fun ApplicationCall.respondAudioError(headOnly: Boolean, status: HttpStatusCode, code: String, message: String) {
    if (headOnly) respond(AudioHeadContent(status, null, null)) else respondError(status, code, message)
}

private const val AUDIO_COPY_BUFFER_BYTES = 64 * 1_024

/** Headers of an audio answer without its body (`HEAD`). */
private class AudioHeadContent(
    override val status: HttpStatusCode,
    override val contentType: ContentType?,
    override val contentLength: Long?,
) : OutgoingContent.NoContent()

/**
 * Audio body of exactly [contentLength] bytes, copied from [stream] off the main thread
 * through a fixed buffer: the track is never held in memory. The expiry of the URL is not
 * checked again here, so a response under way is never cut (contract §8.2). A source that
 * ends early fails the body instead of completing it; the caller closes [stream].
 */
internal class AudioBodyContent(
    private val stream: AudioStream,
    override val status: HttpStatusCode,
    override val contentType: ContentType,
    override val contentLength: Long,
) : OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) = withContext(NzikDispatchers.DATA) {
        val buffer = ByteArray(AUDIO_COPY_BUFFER_BYTES)
        var written = 0L
        while (written < contentLength) {
            val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), contentLength - written).toInt())
            if (read < 0) throw IOException("Audio source ended ${contentLength - written} bytes early")
            channel.writeFully(buffer, 0, read)
            written += read
        }
    }
}
