package app.n_zik.android.core.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.profiles.profileDataPrefs
import timber.log.Timber

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val owner = ProfileWorkOwner.read(inputData) ?: return Result.success()
        if (!applicationContext.awaitLibraryReadyForWork()) return Result.retry()
        if (!ProfileWorkOwner.exists(applicationContext, owner)) return Result.success()
        if (!ProfileWorkOwner.isBound(applicationContext, owner)) return Result.retry()
        val preferences = profileDataPrefs(applicationContext, owner.id)
        Timber.tag("AutoBackup").d("AutoBackupWorker started")
        val uriString = preferences.getString(BackupManager.PREF_URI, "") ?: ""
            
        if (uriString.isEmpty()) {
            Timber.tag("AutoBackup").w("AutoBackupWorker failed: URI not set")
            return Result.failure()
        }

        val success = BackupManager.executeBackup(applicationContext, uriString, owner)
        
        // Reschedule the next exact occurrence
        val interval = preferences.getInt(BackupManager.PREF_INTERVAL, BackupManager.INTERVAL_NONE)
        Timber.tag("AutoBackup").d("AutoBackupWorker interval after backup: $interval")
        
        if (interval != BackupManager.INTERVAL_NONE) {
            BackupManager.scheduleBackup(applicationContext, interval, owner)
        }
        
        Timber.tag("AutoBackup").i("AutoBackupWorker finished with success: $success")
        return if (success) Result.success() else Result.failure()
    }
}

