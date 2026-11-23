package app.n_zik.android.core.rewind

import java.time.ZonedDateTime
import android.content.Context
import androidx.work.WorkerParameters

/** Keeps the persisted WorkManager entry point for the monthly Rewind playlist. */
internal class RewindMonthlyPlaylistWorker(
    context: Context,
    params: WorkerParameters,
) : ScheduledRewindPlaylistWorker(
    context,
    params,
    RewindPlaylistSchedule.Monthly,
) {
    companion object {
        fun schedule(context: Context) = RewindPlaylistSchedule.Monthly.schedule(context)
        internal fun isMonthlyPlaylistEnabled(context: Context) = RewindPlaylistSchedule.Monthly.isEnabled(context)
        internal fun isMonthlyPlaylistNotificationEnabled(context: Context) =
            RewindPlaylistSchedule.Monthly.isNotificationEnabled(context)

        internal fun msUntilNextMonthStart(now: ZonedDateTime = ZonedDateTime.now()) =
            RewindPlaylistSchedule.Monthly.delayUntilNextRun(now)

        internal fun applyJitter(delayMs: Long, deltaMs: Long) = addRewindScheduleJitter(delayMs, deltaMs)
    }
}
