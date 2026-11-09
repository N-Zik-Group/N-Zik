package app.n_zik.android.core.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import timber.log.Timber

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!applicationContext.awaitLibraryReadyForWork()) return Result.retry()
        Timber.tag("AutoBackup").d("AutoBackupWorker started")
        val uriString = applicationContext.getSharedPreferences("preferences", Context.MODE_PRIVATE)
            .getString(BackupManager.PREF_URI, "") ?: ""
            
        if (uriString.isEmpty()) {
            Timber.tag("AutoBackup").w("AutoBackupWorker failed: URI not set")
            return Result.failure()
        }

        val success = BackupManager.executeBackup(applicationContext, uriString)
        
        // Reschedule the next exact occurrence
        val interval = applicationContext.getSharedPreferences("preferences", Context.MODE_PRIVATE)
            .getInt(BackupManager.PREF_INTERVAL, BackupManager.INTERVAL_NONE)
        Timber.tag("AutoBackup").d("AutoBackupWorker interval after backup: $interval")
        
        if (interval != BackupManager.INTERVAL_NONE) {
            BackupManager.scheduleBackup(applicationContext, interval)
        }
        
        Timber.tag("AutoBackup").i("AutoBackupWorker finished with success: $success")
        return if (success) Result.success() else Result.failure()
    }
}

