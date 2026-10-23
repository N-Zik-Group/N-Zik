package app.n_zik.android.extensions.musicbrainz.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.n_zik.android.core.database.Database
import app.n_zik.android.extensions.musicbrainz.MBMetadataHelper
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Backfills MusicBrainz metadata for followed / in-library artists and albums
 * that still have no MB data. Runs in small batches and reschedules itself
 * daily while items remain.
 */
class MbBackfillWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MbBackfillWorker"
        private const val WORK_NAME = "MbBackfillWorker"
        private const val MAX_ITEMS_PER_RUN = 5
        private const val INITIAL_DELAY_MS = 60L * 60 * 1000
        private const val RESCHEDULE_DELAY_MS = 24L * 60 * 60 * 1000

        // MusicBrainz explicitly asks integrators to avoid synchronized requests across
        // distributed installations (e.g. every install of a new app version hitting MB
//
        // at the same fixed offset). +/-15min of jitter spreads the fleet out.
        private const val JITTER_MS = 15L * 60 * 1000

        private fun withJitter(baseDelayMs: Long) =
            (baseDelayMs + (-JITTER_MS..JITTER_MS).random()).coerceAtLeast(0)

        /**
         * Schedules the first backfill run after a delay so the app
         * startup stays fast. KEEP avoids piling up concurrent runs.
         */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<MbBackfillWorker>()
                .setInitialDelay(withJitter(INITIAL_DELAY_MS), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    // Shares MBMetadataHelper.Default's MusicBrainz client (and its rate-limiting mutex)
    // so this background backfill can't burst past 1 req/sec together with foreground
//
    // fetches triggered by browsing artist/album pages.
    private val helper = MBMetadataHelper.Default

    override suspend fun doWork(): Result {
        Timber.tag(TAG).i("Starting MusicBrainz metadata backfill")

        Database.artistTable
            .artistsWithoutMbData(MAX_ITEMS_PER_RUN)
            .forEach { helper.onArtistViewed(it.id) }

        Database.albumTable
            .albumsWithoutMbData(MAX_ITEMS_PER_RUN)
            .forEach { helper.onAlbumViewed(it.id) }

        val moreArtists = Database.artistTable.artistsWithoutMbData(1)
        val moreAlbums = Database.albumTable.albumsWithoutMbData(1)

        return if (moreArtists.isNotEmpty() || moreAlbums.isNotEmpty()) {
            Timber.tag(TAG).i("Work remaining, rescheduling in 24h")
            reschedule()
        } else {
            Timber.tag(TAG).i("Backfill complete")
            Result.success()
        }
    }

    private fun reschedule(): Result {
        val request = OneTimeWorkRequestBuilder<MbBackfillWorker>()
            .setInitialDelay(withJitter(RESCHEDULE_DELAY_MS), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        return Result.success()
    }
}
