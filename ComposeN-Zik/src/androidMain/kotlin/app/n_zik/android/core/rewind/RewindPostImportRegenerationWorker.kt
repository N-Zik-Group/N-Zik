package app.n_zik.android.core.rewind

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.n_zik.android.core.database.Database
import timber.log.Timber

/**
 * One-shot post-import regeneration job (spec 2): after a successful listening-data
 * import (the database file was replaced), recomputes every EXISTING `rewind-*`
 * playlist — monthly, yearly and all-time — from the imported history, with the window
 * derived from each playlist's database name.
 *
 * Deliberately different from the cycle workers:
 * - no self-reschedule, no jitter, no notification (silent — Timber only);
 * - no gate on the creation toggles: a toggle off means "do not create NEW playlists",
 *   but an existing playlist whose source data was just replaced is stale, and
 *   consistency wins (frozen spec 2);
 * - deleted playlists stay deleted: only the existing ones are regenerated (the same
 *   "the auto never recreates" rule as the workers).
 */
internal class RewindPostImportRegenerationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RewindPostImportRegeneration"
        private const val WORK_NAME = "RewindPostImportRegenerationWorker"

        /**
         * Enqueues (or replaces) the one-shot regeneration after a successful import.
         * REPLACE keeps exactly one pending run: two back-to-back imports must not pile
         * up two full recomputations.
         */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<RewindPostImportRegenerationWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Timber.tag(TAG).i("Post-import rewind regeneration scheduled")
        }
    }

    override suspend fun doWork(): Result {
        if (Database.isClosed) {
            // The import closes the database before the app restarts with the imported
            // file: a live run would hit the dead Room singleton. The work is persisted,
            // so retrying lets it survive the restart and run once the database is open
            // again (deviation from the frozen spec, noted in the implementation report).
            Timber.tag(TAG).i("Database is closed after the import: retrying so the run survives the restart")
            return Result.retry()
        }
        val existing = Database.playlistTable.getAll().filter { RewindPlaylists.isRewind(it.name) }
        existing.forEach { playlist ->
            val window = RewindPlaylists.windowFor(playlist.name)
            if (window == null) {
                Timber.tag(TAG).w("No listening window derivable for \"${playlist.name}\": skipping")
                return@forEach
            }
            val count = generateRewindPlaylist(
                playlist.name,
                window.first,
                window.second,
                GenerateMode.Regenerate
            )
            Timber.tag(TAG).i("Regenerated \"${playlist.name}\" with $count songs")
        }
        return Result.success()
    }
}
