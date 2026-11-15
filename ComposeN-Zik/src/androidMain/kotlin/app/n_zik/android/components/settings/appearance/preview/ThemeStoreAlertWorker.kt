package app.n_zik.android.components.settings.appearance.preview

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.n_zik.android.components.settings.accounts.GitHubAccount
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.profiles.profileDataPrefs
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

internal class ThemeStoreAlertWorker internal constructor(
    context: Context,
    parameters: WorkerParameters,
    private val catalog: ThemeStoreCatalog,
) :
    CoroutineWorker(context, parameters) {

    constructor(context: Context, parameters: WorkerParameters) :
        this(context, parameters, themeStoreCatalog(context, false))

    override suspend fun doWork(): Result = checkLock.withLock {
        val context = applicationContext
        val owner = ProfileWorkOwner.capture(context) ?: return@withLock Result.success()
        fun mayCheck() = ProfileWorkOwner.isBound(context, owner) && !OfflineMode.enabled(context)
        if (!mayCheck()) return@withLock Result.success()
        val preferences = profileDataPrefs(context, owner.id)
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val id = GitHubAccount.userId(context).takeIf { notificationsEnabled }
        val library = ThemeStoreLibrary(preferences)
        val needsCatalog = library.hasAutomaticDownloads() || library.hasRestoredStoreUpdates(notificationsEnabled) ||
            (notificationsEnabled && library.hasNotificationSubscriptions()) || id != null
        val hasPending = id != null && hasPendingCreatorDecisions(context, id)
        val now = System.currentTimeMillis()
        val lastCheck = preferences.getLong(lastCheckKey, 0L)
        if (!needsCatalog || now - lastCheck in 0 until minimumCheckSpacingMs) {
            return@withLock Result.success()
        }

        var failed = false
        suspend fun attempt(name: String, check: suspend () -> Unit) {
            try {
                check()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag("ThemeStore").w(error, "Could not check %s for notifications", name)
                failed = true
            }
        }
        attempt("catalog") {
            expireThemeStoreIndex()
            val themes = catalog.refresh()
            if (mayCheck()) {
                if (!catalog.bindRestoredDownloads(library, themes, ::mayCheck)) failed = true
                if (!catalog.downloadAutomaticUpdates(context, library, themes, ::mayCheck)) failed = true
                if (notificationsEnabled && mayCheck()) notifyThemeStoreAlerts(context, library, themes)
            }
        }
        if (hasPending && mayCheck()) attempt("creator submissions") {
            val login = GitHubAccount.accountLogin(context) ?: return@attempt
            val submissions = creatorSubmissions(id, login, GitHubAccount.tokenFor(context, id))
            if (mayCheck()) {
                notifyCreatorDecisions(context, id, submissions)
            }
        }
        if (failed) Result.retry() else {
            if (mayCheck()) preferences.edit { putLong(lastCheckKey, now) }
            Result.success()
        }
    }

    companion object {
        private const val lastCheckKey = "themeStore.alerts.lastCheck"
        private const val minimumCheckSpacingMs = 2 * 60 * 1000L
        private val checkLock = Mutex()
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val work = try {
                WorkManager.getInstance(context)
            } catch (error: IllegalStateException) {
                Timber.tag("ThemeStore").w(error, "Theme Store notification scheduler is unavailable")
                return
            }
            work.enqueueUniqueWork(
                "ThemeStoreAlertCheck",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ThemeStoreAlertWorker>().setConstraints(network).build(),
            )
            work.enqueueUniquePeriodicWork(
                "ThemeStoreAlertPeriodicCheck",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ThemeStoreAlertWorker>(15, TimeUnit.MINUTES).setConstraints(network).build(),
            )
        }
    }
}
