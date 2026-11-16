package app.n_zik.android.components.settings.appearance.preview

import android.content.Context
import app.n_zik.android.R
import kotlinx.serialization.json.Json

internal const val BundledThemeId = "9ffe92cc-aadd-4bc4-ac32-bbce237ee750"

internal fun defaultThemeAppearanceDocument(): String = encodeThemeV1(emptyMap<String, Any>(), emptyMap<String, Any>())

internal fun bundledDefaultTheme(context: Context): SavedTheme {
    val document = defaultThemeAppearanceDocument()
    val previews = context.assets.open("theme-store-default/previews.json").bufferedReader().use {
        Json.decodeFromString<List<StorePreview>>(it.readText())
    }.sortedBy { preview ->
        automaticPreviews.indexOf(preview.path.substringAfterLast('/').substringBeforeLast('.'))
    }
    val name = context.getString(R.string.theme_store_default_title)
    val theme = StoreTheme(
        schemaVersion = 1,
        themeId = BundledThemeId,
        revision = 1,
        title = name,
        description = context.getString(R.string.theme_store_default_description),
        author = StoreAuthor(292738521, "N-Zik-Group"),
        tags = listOf("stock", "built-in"),
        compatibility = StoreCompatibility(FirstThemeStoreVersionCode),
        appearance = StoreFile("appearance.json", document.encodeToByteArray().sha256()),
        previews = previews,
        localCreatorName = "N-Zik Group",
    )
    return SavedTheme(name, document, theme)
}
