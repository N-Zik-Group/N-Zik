package app.n_zik.android.components.settings.appearance.preview

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
