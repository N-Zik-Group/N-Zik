package app.n_zik.android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.n_zik.android.core.backup.awaitLibraryReadyForWork
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext

/** Refreshes installed playlist widgets after a startup recovery, even across process restarts. */
internal class PlaylistWidgetRefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!context.awaitLibraryReadyForWork() || Database.isClosed) return Result.retry()

        return withContext(NzikDispatchers.DATA) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlaylistWidgetReceiver::class.java))
            PlaylistWidgetManager.refreshIdleWidgets(context, manager, ids)
            Result.success()
        }
    }

    companion object {
        internal const val WORK_NAME = "playlist-widget-refresh-after-library-startup"

        /** Coalesces broadcasts and reloads the current widget sizes when the job runs. */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<PlaylistWidgetRefreshWorker>().build(),
            )
        }
    }
}
