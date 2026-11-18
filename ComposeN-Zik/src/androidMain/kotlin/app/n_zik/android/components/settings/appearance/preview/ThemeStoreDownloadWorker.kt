package app.n_zik.android.components.settings.appearance.preview

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.n_zik.android.BuildConfig
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class ThemeStoreDownloadWorker internal constructor(
    context: Context,
    parameters: WorkerParameters,
    private val catalog: ThemeStoreCatalog,
) : CoroutineWorker(context, parameters) {
    constructor(context: Context, parameters: WorkerParameters) :
        this(context, parameters, themeStoreCatalog(context, false))

    override suspend fun doWork(): Result = withContext(NzikDispatchers.DATA) {
        val context = applicationContext
        val owner = ProfileWorkOwner.read(inputData) ?: return@withContext Result.failure()
        val themeId = inputData.getString(ThemeId)?.takeIf { id ->
            runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false) && id != BundledThemeId
        } ?: return@withContext Result.failure()
        val revision = inputData.getInt(Revision, 0).takeIf { it > 0 } ?: return@withContext Result.failure()
        if (!ProfileWorkOwner.exists(context, owner)) return@withContext Result.failure()
        if (!ProfileWorkOwner.isBound(context, owner)) return@withContext Result.retry()
        if (OfflineMode.enabled(context)) return@withContext Result.failure()
        val library = ThemeStoreLibrary(profileDataPrefs(context, owner.id))
        val ownerTag = ProfileWorkOwner.name(DownloadTag, owner)
        library.download("$themeId:$revision")?.let { saved ->
            saved.theme?.let { ThemeDownloadNotification(context, it, ownerTag).show(result = true) }
            return@withContext Result.success()
        }

        var notification: ThemeDownloadNotification? = null
        try {
            val latest = catalog.cached()?.firstOrNull { it.themeId == themeId && it.revision >= revision }
                ?: catalog.refresh().firstOrNull { it.themeId == themeId && it.revision >= revision }
                ?: return@withContext Result.failure()
            val theme = catalog.publishedRevision(latest, revision)
            if (!ProfileWorkOwner.isBound(context, owner)) return@withContext Result.retry()
            notification = ThemeDownloadNotification(context, theme, ownerTag)
            notification.show()
            val appearance = catalog.downloadForOffline(theme)
            val result = DatabaseRestoreJournal.withProfileStorageMutation(context) {
                when {
                    !ProfileWorkOwner.exists(context, owner) -> Result.failure()
                    !ProfileWorkOwner.isBound(context, owner) -> Result.retry()
                    OfflineMode.enabled(context) -> Result.failure()
                    library.saveQueuedDownload(SavedTheme(theme.title, appearance.document, theme)) -> Result.success()
                    else -> Result.failure()
                }
            }
            if (result is Result.Retry || !ProfileWorkOwner.isBound(context, owner)) notification.cancel()
            else notification.show(result = result is Result.Success)
            result
        } catch (cancelled: CancellationException) {
            notification?.cancel()
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("ThemeStore").w(error, "Could not download theme %s revision %s", themeId, revision)
            when {
                !ProfileWorkOwner.exists(context, owner) -> {
                    notification?.cancel()
                    Result.failure()
                }
                !ProfileWorkOwner.isBound(context, owner) -> {
                    notification?.cancel()
                    Result.retry()
                }
                error is IOException && error !is StoreFileNotFound && !OfflineMode.enabled(context) && runAttemptCount < 2 -> {
                    notification?.cancel()
                    Result.retry()
                }
                else -> {
                    notification?.show(result = false)
                    Result.failure()
                }
            }
        }
    }

    companion object {
        internal const val ThemeId = "theme_store_download_id"
        internal const val Revision = "theme_store_download_revision"
    }
}

private const val DownloadTag = "ThemeStoreDownload"
private const val ItemTagPrefix = "$DownloadTag:item:"

internal class ThemeStoreDownloadQueue(
    private val context: Context,
    private val owner: ProfileStorageIdentity,
    private val work: WorkManager,
) {
    private val ownerTag = ProfileWorkOwner.name(DownloadTag, owner)

    val activeDownloads = work.getWorkInfosByTagFlow(ownerTag).map { requests ->
        requests.asSequence().filterNot { it.state.isFinished }
            .flatMap { it.tags.asSequence() }
            .filter { it.startsWith(ItemTagPrefix) }
            .map { it.removePrefix(ItemTagPrefix) }.toSet()
    }

    fun enqueue(theme: StoreTheme) {
        if (theme.themeId == BundledThemeId || theme.supportAt(BuildConfig.VERSION_CODE) != StoreSupport.Supported ||
            !ProfileWorkOwner.isBound(context, owner) || OfflineMode.enabled(context)
        ) return
        val id = "${theme.themeId}:${theme.revision}"
        val request = OneTimeWorkRequestBuilder<ThemeStoreDownloadWorker>()
            .setInputData(Data.Builder().putAll(ProfileWorkOwner.input(owner))
                .putString(ThemeStoreDownloadWorker.ThemeId, theme.themeId)
                .putInt(ThemeStoreDownloadWorker.Revision, theme.revision).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(ownerTag)
            .addTag(ItemTagPrefix + id)
            .build()
        work.enqueueUniqueWork("$ownerTag:$id", ExistingWorkPolicy.KEEP, request)
    }
}

@Composable
internal fun rememberThemeStoreDownloadQueue(profileId: String): ThemeStoreDownloadQueue? {
    val context = LocalContext.current.applicationContext
    return remember(context, profileId) {
        val owner = ProfileWorkOwner.capture(context)?.takeIf { it.id == profileId } ?: return@remember null
        try {
            ThemeStoreDownloadQueue(context, owner, WorkManager.getInstance(context))
        } catch (error: IllegalStateException) {
            Timber.tag("ThemeStore").w(error, "Theme download scheduler is unavailable")
            null
        }
    }
}
