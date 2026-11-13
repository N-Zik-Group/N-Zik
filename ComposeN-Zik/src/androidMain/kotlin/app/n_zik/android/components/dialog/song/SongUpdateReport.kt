package app.n_zik.android.components.dialog.song

import java.io.IOException
import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import app.n_zik.android.models.Song
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.utils.ToastRelay
import app.n_zik.android.playback.services.PlayerServiceModern

/** The action that could not complete, without exposing provider exception text. */
internal enum class SongUpdateIssue(@StringRes val messageId: Int) {
    METADATA_UNAVAILABLE(R.string.song_update_error_metadata_unavailable),
    NETWORK(R.string.song_update_error_network),
    MISSING_TITLE(R.string.song_update_error_missing_title),
    MISSING_ARTISTS(R.string.song_update_error_missing_artists),
    MISSING_COVER(R.string.song_update_error_missing_cover),
    INCOMPLETE_ARTISTS(R.string.song_update_error_incomplete_artists),
    WRONG_SONG(R.string.song_update_error_wrong_song),
    CACHE_UNAVAILABLE(R.string.song_update_error_cache_unavailable),
    PROFILE_CHANGED(R.string.song_update_error_profile_changed),
    STORAGE(R.string.song_update_error_storage),
    CACHE_CLEAR(R.string.song_update_error_cache_clear),
    DOWNLOAD_REMOVE(R.string.song_update_error_download_remove),
    COVER_RESET(R.string.song_update_error_cover_reset),
    PLAYBACK_REFRESH(R.string.song_update_error_playback_refresh),
}

/** Tracks committed work separately from errors in later file/cache operations. */
internal class SongUpdateProgress {
    val issues = linkedSetOf<SongUpdateIssue>()
    var anyCompleted = false
    var failureIssue = SongUpdateIssue.STORAGE

    fun profileChanged(): Boolean {
        issues += SongUpdateIssue.PROFILE_CHANGED
        return false
    }
}

internal data class SongUpdateReport(
    val completed: Boolean,
    val anyCompleted: Boolean,
    val issues: Set<SongUpdateIssue>,
) {
    val partial: Boolean get() = !completed && anyCompleted
}

/** Only fetch failures can be network failures; HTTP failures do not prove a deleted video. */
internal fun classifySongUpdateFetchFailure(error: Throwable): SongUpdateIssue {
    val causes = mutableListOf<Throwable>()
    var current: Throwable? = error
    while (current != null) {
        val cause = current
        if (causes.any { it === cause }) break
        causes += cause
        current = cause.cause
    }
    causes.filterIsInstance<CancellationException>().firstOrNull()?.let { throw it }
    return when {
        causes.any { it is ResponseException } -> SongUpdateIssue.METADATA_UNAVAILABLE
        causes.any { it is IOException || it is HttpRequestTimeoutException } -> SongUpdateIssue.NETWORK
        else -> SongUpdateIssue.METADATA_UNAVAILABLE
    }
}

/** Shares the Boolean core with existing callers while retaining partial completion and failure phase. */
internal suspend fun runSongUpdateReport(
    binder: PlayerServiceModern.Binder?,
    entry: Song,
    selection: SongUpdateSelection,
    requestProfile: String = getActiveProfile(appContext()),
): SongUpdateReport {
    val progress = SongUpdateProgress()
    val completed = try {
        runSongUpdateCore(binder, entry, selection, requestProfile, progress)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Timber.tag("SongUpdate").e(error, "Update action failed: %s", progress.failureIssue)
        progress.issues += progress.failureIssue
        false
    }
    return SongUpdateReport(completed, progress.anyCompleted, progress.issues.toSet())
}

internal fun SongUpdateReport.message(context: Context): String = context.getString(
    if (partial) R.string.song_update_result_partial else R.string.song_update_result_failed,
    issues.joinToString("\n") { context.getString(it.messageId) },
)

internal fun showSongUpdateReport(report: SongUpdateReport) {
    when {
        report.completed -> Toaster.done()
        report.partial -> Toaster.w(report.message(appContext()), Toast.LENGTH_LONG)
        else -> Toaster.e(report.message(appContext()), Toast.LENGTH_LONG)
    }
}

/** One summary for the whole batch; each reason counts affected songs, never individual operations. */
internal fun songUpdateBatchMessage(context: Context, reports: List<SongUpdateReport>): String {
    val summary = context.getString(
        R.string.song_update_batch_result,
        reports.count { it.completed },
        reports.count { it.partial },
        reports.count { !it.completed && !it.partial },
    )
    val issues = reports.flatMap { it.issues }.groupingBy { it }.eachCount()
    return (listOf(summary) + issues.map { (issue, count) ->
        context.getString(R.string.song_update_batch_reason_count, context.getString(issue.messageId), count)
    }).joinToString("\n")
}

internal fun showSongUpdateBatchReport(reports: List<SongUpdateReport>) {
    if (reports.isEmpty()) return
    if (reports.all { it.completed }) {
        ToastRelay.successWithPlural(R.plurals.song_metadata_updated_song_count, R.string.song_metadata_updated_count, reports.size)
    } else {
        val message = songUpdateBatchMessage(appContext(), reports)
        if (reports.any { it.anyCompleted || it.completed }) Toaster.w(message, Toast.LENGTH_LONG)
        else Toaster.e(message, Toast.LENGTH_LONG)
    }
}
