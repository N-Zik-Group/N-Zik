package app.n_zik.android.playback.services

import android.content.Context
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.cache.Cache
import app.n_zik.android.R
import app.n_zik.android.core.maintenance.MaintenanceDownloadSelfHealState
import app.n_zik.android.core.maintenance.MaintenanceStateStore
import app.n_zik.android.download.utils.DownloadValidator
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.core.utils.Toaster
import timber.log.Timber
import java.io.IOException
import app.n_zik.android.playback.exceptions.ExplicitContentException
import app.n_zik.android.playback.exceptions.LoginRequiredException
import app.n_zik.android.playback.exceptions.PlayableFormatNonSupported
import app.n_zik.android.playback.exceptions.StreamResolutionOfflineException
import app.n_zik.android.playback.exceptions.UnmatchedSongException
import app.n_zik.android.playback.exceptions.UnplayableException
import app.n_zik.android.playback.exceptions.VideoIdMismatchException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

fun Throwable?.isFatalCustomException(): Boolean {
    return generateSequence<Throwable>(this) { it.cause }
        .any {
            it is ExplicitContentException ||
            it is LoginRequiredException ||
            it is PlayableFormatNonSupported ||
            it is UnplayableException ||
            it is VideoIdMismatchException ||
            it is UnmatchedSongException
        }
}

/** Issue #881 (gh-881): hard bound on the root-cause walk — a malformed cyclic cause chain must never spin the retry thread that calls [diagnosticExceptionSummary]. */
private const val DIAGNOSTIC_CAUSE_HOP_LIMIT = 16

/** Issue #881 (gh-881): `scheme://…` blobs embedded in exception messages (stream URLs, hosts) — scrubbed so the exported log file, which is shared publicly, never carries the stream URL (spec S3 / M4 confidentiality rule). */
private val DIAGNOSTIC_URL_PATTERN = Regex("""(?i)(?:https?|ftp)://\S+""")

/**
 * Issue #881 (gh-881): `ClassName: message` of the ROOT cause of a load failure —
 * class + truncated message only. Lifted so the scrubbing and the bounded cause walk are
 * unit-testable without the service (review gh-881).
 */
internal fun diagnosticExceptionSummary(exception: Throwable): String {
    var root = exception
    var hops = 0
    while (hops < DIAGNOSTIC_CAUSE_HOP_LIMIT) {
        val cause = root.cause
        if (cause == null || cause === root) break
        root = cause
        hops++
    }
    val message = root.message
        ?.replace(DIAGNOSTIC_URL_PATTERN, "<url>")
        ?.take(200)
        ?: "-"
    return "${root.javaClass.simpleName}: $message"
}

/**
 * S5 (Phase 1 download fix, 2026-10-21): true when [error]'s cause chain contains a DNS
 * failure — [UnknownHostException] or [UnresolvedAddressException] — at ANY depth.
 *
 * Offline playback of a downloaded track still resolves the stream URL, and an expired
 * local URL forces a network fetch that dies at DNS — ExoPlayer wraps that loader
 * failure several levels deep (the real offline log shows
 * `UnexpectedLoaderException -> PlaybackException -> UnknownHostException`), so the
 * previous depth-1/2 check in `onPlayerError` missed the offline case and fell into the
 * retry storm. A DNS failure in the chain is sufficient evidence that the network is
 * unusable for the failing host, whatever the connectivity monitor reports.
 *
 * Pure so the gate predicate is unit-testable without a running [PlayerServiceModern]
 * (same pattern as [diagnosticExceptionSummary], incl. the bounded hop guard against
 * malformed cyclic cause chains).
 */
internal fun isDnsFailure(error: Throwable): Boolean {
    var current: Throwable? = error
    var hops = 0
    while (current != null && hops < DIAGNOSTIC_CAUSE_HOP_LIMIT) {
        if (current is UnknownHostException || current is UnresolvedAddressException) return true
        current = current.cause
        hops++
    }
    return false
}

/** S5 (offline gate, 2026-10-22): true when the chain carries the offline fast-fail marker [StreamResolutionOfflineException] at any depth — the marker plugs the stale-monitor hole. */
internal fun isStreamResolutionOffline(error: Throwable): Boolean {
    var current: Throwable? = error
    var hops = 0
    while (current != null && hops < DIAGNOSTIC_CAUSE_HOP_LIMIT) {
        if (current is StreamResolutionOfflineException) return true
        current = current.cause
        hops++
    }
    return false
}

/** A3 (offline audit, 2026-10-22): true when the chain carries any [IOException] at any depth (only the [StreamResolutionOfflineException] marker is excluded) — deliberately broader than a DNS-only check, socket failures qualify too; the paired fresh network check decides. */
internal fun isRawConnectionFailure(error: Throwable): Boolean {
    var current: Throwable? = error
    var hops = 0
    while (current != null && hops < DIAGNOSTIC_CAUSE_HOP_LIMIT) {
        if (current is IOException && current !is StreamResolutionOfflineException) return true
        current = current.cause
        hops++
    }
    return false
}

/**
 * S7 (offline gate, 2026-10-21/22): true when a playback error should wait for the network
 * instead of the retry path — monitor offline, nested connection error/timeout, a DNS
 * failure anywhere in the chain, or the offline fast-fail marker. Pure: unit-testable.
 */
internal fun shouldWaitForNetwork(error: PlaybackException, networkAvailable: Boolean): Boolean {
    val connectionErrorCodes = listOf(
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, // primary error code to manage
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    )
    val isConnectionError = error.cause?.cause is PlaybackException &&
        (error.cause?.cause as PlaybackException).errorCode in connectionErrorCodes
    return !networkAvailable || isConnectionError || isDnsFailure(error) || isStreamResolutionOffline(error)
}

/**
 * S5 (offline gate at the load level, 2026-10-22): true when a load error cannot succeed
 * offline — monitor offline, a DNS failure in the chain, or the exception IS the fast-fail
 * marker (unconditional against a stale monitor). Pure: unit-testable.
 */
internal fun isOfflineLoadFailure(exception: IOException, networkAvailable: Boolean): Boolean =
    !networkAvailable || isDnsFailure(exception) || exception is StreamResolutionOfflineException

/**
 * S2 (Phase 1 download fix, 2026-10-21): the pre-playback self-heal — when
 * [isStaleDownloadEntry] finds the files gone, the stale index entry is removed,
 * the re-download toast is shown and the healed track is persisted for the
 * Maintenance sheet (guarded — a failed write never breaks the transition). The
 * transition still proceeds after the call: online the track streams, and offline
 * the S5 gate turns the guaranteed failure into a clean "no internet" toast instead
 * of the old retry storm. One synchronized index read + one file stat per span —
 * cheap enough for the transition callback thread.
 *
 * @return true when the entry was stale and the self-heal ran.
 */
internal fun selfHealStaleDownloadEntry(context: Context, item: MediaItem, downloadCache: Cache): Boolean {
    if (!isStaleDownloadEntry(item.mediaId, downloadCache)) return false
    Timber.tag("PlayerServiceModern").w("Download re-validation (pre-playback): ${item.mediaId} is stale, removing the index entry")
    MyDownloadHelper.removeDownload(context, item)
    Toaster.w(
        R.string.downloads_stale_removed,
        formatArgs = arrayOf(item.mediaMetadata.title ?: ""),
        duration = Toast.LENGTH_LONG
    )
    // Log the self-heal so the Maintenance sheet can show the healed track;
    // guarded — a failed write never breaks the transition
    runCatching {
        MaintenanceStateStore.saveDownloadSelfHeal(
            context,
            MaintenanceDownloadSelfHealState(
                timestamp = System.currentTimeMillis(),
                mediaId = item.mediaId,
                title = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: item.mediaId,
            ),
        )
    }.onFailure { Timber.tag("PlayerServiceModern").w(it, "Failed to save the download self-heal state") }
    return true
}

/**
 * S2 (Phase 1 download fix, 2026-10-21): true when the download index claims the
 * track is downloaded but its span files are gone (OS/OEM wipe) — the stale-entry
 * case that the pre-playback self-heal in [selfHealStaleDownloadEntry] removes.
 *
 * Pure so the compound decision is unit-testable without a running
 * [PlayerServiceModern] (same pattern as [isDnsFailure]).
 */
internal fun isStaleDownloadEntry(mediaId: String, downloadCache: Cache): Boolean =
    MyDownloadHelper.isSongDownloaded(mediaId) && !DownloadValidator.hasValidDownload(downloadCache, mediaId)
