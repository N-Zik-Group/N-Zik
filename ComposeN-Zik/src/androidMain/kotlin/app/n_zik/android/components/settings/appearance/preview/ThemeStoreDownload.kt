package app.n_zik.android.components.settings.appearance.preview

import android.content.Context
import app.n_zik.android.BuildConfig
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

private val automaticDownloadLock = Mutex()

/** Restored Store downloads regain update eligibility only after verifying their original revision. */
internal suspend fun ThemeStoreCatalog.bindRestoredDownloads(
    library: ThemeStoreLibrary,
    themes: List<StoreTheme>,
    mayBind: () -> Boolean,
): Boolean {
    val indexed = themes.associateBy { it.themeId }
    val olderRevisions = mutableListOf<StoreTheme>()
    var successful = true
    for (origin in library.restoredStoreOrigins()) {
        if (!mayBind()) return successful
        val latest = indexed[origin.themeId] ?: continue
        if (origin.revision >= latest.revision) continue
        try {
            olderRevisions += publishedRevision(latest, origin.revision)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("ThemeStore").w(error, "Could not verify restored theme revision")
            successful = false
        }
    }
    withContext(NzikDispatchers.DATA) {
        if (mayBind()) bindRestoredStoreThemes(library, themes + olderRevisions)
    }
    return successful
}

/** Shares update downloads between the Store screen and scheduled checks. */
internal suspend fun ThemeStoreCatalog.downloadAutomaticUpdates(
    context: Context,
    library: ThemeStoreLibrary,
    themes: List<StoreTheme>,
    mayDownload: () -> Boolean,
): Boolean = automaticDownloadLock.withLock {
    var successful = true
    for (theme in library.downloadedThemeUpdates(themes, ThemeUpdateMode.AutoDownload)) {
        if (!mayDownload()) break
        if (theme.compatibility.supportAt(BuildConfig.VERSION_CODE) != StoreSupport.Supported) continue
        try {
            val appearance = downloadForOffline(theme)
            if (mayDownload() && library.downloadedThemeUpdates(listOf(theme), ThemeUpdateMode.AutoDownload).isNotEmpty()) {
                library.save(SavedTheme(theme.title, appearance.document, theme))
                ThemeDownloadNotification(context.applicationContext, theme).show(result = true)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("ThemeStore").w(error, "Could not automatically download theme update")
            successful = false
        }
    }
    successful
}

internal suspend fun ThemeStoreCatalog.downloadForOffline(
    theme: StoreTheme,
    cachedAppearance: StoreAppearance? = null,
): StoreAppearance {
    val verified = cachedAppearance ?: appearance(theme, null)
    for (preview in theme.previews) {
        val file = checkNotNull(preview(theme, preview)) { "Could not verify preview ${preview.path}" }
        kotlinx.coroutines.withContext(app.n_zik.android.utils.coroutines.NzikDispatchers.DATA) {
            val bytes = file.inputStream().use { it.readThemeBytes(SubmissionPreviewLimit) }
            validateThemeArchivePreview(preview, bytes)
        }
    }
    return verified
}
