package app.n_zik.android.core.rewind

import java.time.ZonedDateTime
import android.content.Context
import androidx.work.WorkerParameters

/** Keeps the persisted WorkManager entry point for the yearly Rewind playlist. */
internal class RewindYearlyPlaylistWorker(
    context: Context,
    params: WorkerParameters,
) : ScheduledRewindPlaylistWorker(
    context,
    params,
    RewindPlaylistSchedule.Yearly,
) {
    companion object {
        fun schedule(context: Context) = RewindPlaylistSchedule.Yearly.schedule(context)
        internal fun isYearlyPlaylistEnabled(context: Context) = RewindPlaylistSchedule.Yearly.isEnabled(context)
        internal fun isYearlyPlaylistNotificationEnabled(context: Context) =
            RewindPlaylistSchedule.Yearly.isNotificationEnabled(context)

        internal fun msUntilNextYearStart(now: ZonedDateTime = ZonedDateTime.now()) =
            RewindPlaylistSchedule.Yearly.delayUntilNextRun(now)

        internal fun applyJitter(delayMs: Long, deltaMs: Long) = addRewindScheduleJitter(delayMs, deltaMs)
    }
}
