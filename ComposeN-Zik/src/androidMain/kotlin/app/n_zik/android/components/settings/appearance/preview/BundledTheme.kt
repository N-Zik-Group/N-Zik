package app.n_zik.android.components.settings.appearance.preview

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.n_zik.android.R
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

internal const val BundledThemeId = "9ffe92cc-aadd-4bc4-ac32-bbce237ee750"
internal const val StockPreviewDirectory = "demo/stock/"
private const val StockPreviewIndexLimit = 64 * 1024
private val stockPreviewIndexLock = Mutex()

internal fun defaultThemeAppearanceDocument(): String = encodeThemeAppearance(emptyMap<String, Any>(), emptyMap<String, Any>(), schemaVersion = ThemeAppearanceV2)

internal fun bundledDefaultTheme(context: Context, previewMetadata: List<StorePreview>? = null): SavedTheme {
    val document = defaultThemeAppearanceDocument()
    val previews = (previewMetadata ?: context.assets.open("theme-store-default/previews.json").bufferedReader().use {
        Json.decodeFromString<List<StorePreview>>(it.readText())
    }).sortedBy { preview ->
        automaticPreviews.indexOf(preview.path.substringAfterLast('/').substringBeforeLast('.'))
    }
    val name = context.getString(R.string.theme_store_default_title)
    val theme = StoreTheme(
        schemaVersion = 2,
        themeId = BundledThemeId,
        revision = 2,
        title = name,
        description = context.getString(R.string.theme_store_default_description),
        author = StoreAuthor(292738521, "N-Zik-Group"),
        tags = listOf("stock", "built-in"),
        compatibility = StoreCompatibility(ThemeAppearanceV2MinimumVersionCode),
        appearance = StoreFile("appearance.json", document.encodeToByteArray().sha256()),
        previews = previews,
        localCreatorName = "N-Zik Group",
    )
    return SavedTheme(name, document, theme)
}

/** Keeps Stock appearance local while refreshing its independently published preview metadata. */
@Composable
internal fun rememberBundledDefaultTheme(refreshVersion: Int, enabled: Boolean = true): SavedTheme? {
    val context = LocalContext.current
    val offline by OfflineMode.rememberEnabled()
    val fallback = remember(context, enabled) { if (enabled) bundledDefaultTheme(context, emptyList()) else null }
    val index = remember(context) {
        BundledThemePreviewIndex(File(context.filesDir, "theme-store-catalog")) { path, limit ->
            if (OfflineMode.enabled(context)) throw IOException("Theme Store is offline")
            fetchStoreFile(path, limit)
        }
    }
    val theme by produceState(fallback, fallback, index, offline, refreshVersion) {
        val local = fallback
        if (local == null) {
            value = null
            return@produceState
        }
        fun update(previews: List<StorePreview>) {
            value = local.copy(theme = checkNotNull(local.theme).copy(previews = previews))
        }
        val cached = index.cached()
        val initial = cached ?: runInterruptible(NzikDispatchers.DATA) { bundledDefaultTheme(context).theme?.previews.orEmpty() }
        update(initial)
        if (!offline) {
            try {
                update(index.refresh())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag("ThemeStore").w(error, "Could not refresh Stock previews")
            }
        }
    }
    return theme.takeIf { enabled }
}

internal class BundledThemePreviewIndex(
    directory: File,
    private val fetch: (String, Int) -> ByteArray,
) {
    private val disk = AtomicFile(File(directory, "stock-previews.json"))

    suspend fun cached(): List<StorePreview>? = stockPreviewIndexLock.withLock {
        runInterruptible(NzikDispatchers.DATA) {
            if (!disk.baseFile.isFile) return@runInterruptible null
            try {
                parse(disk.openRead().use { it.readThemeBytes(StockPreviewIndexLimit) })
            } catch (error: Exception) {
                Timber.tag("ThemeStore").w(error, "Could not read cached Stock previews")
                null
            }
        }
    }

    suspend fun refresh(): List<StorePreview> = stockPreviewIndexLock.withLock {
        runInterruptible(NzikDispatchers.DATA) {
            val bytes = fetch(StockPreviewDirectory + "index.json", StockPreviewIndexLimit)
            require(bytes.size <= StockPreviewIndexLimit)
            val previews = parse(bytes)
            writeThemeFile(disk, bytes)
            previews
        }
    }

    private fun parse(bytes: ByteArray): List<StorePreview> {
        val index = Json.decodeFromString<StockPreviewIndex>(bytes.decodeToString(throwOnInvalidSequence = true))
        val expected = previewSlotsForMode(requiredPreviews + optionalAutomaticPreviews, followsSystem = true)
            .map { "previews/$it.webp" }.toSet()
        require(index.schemaVersion == 1 && index.previews.size == expected.size)
        require(index.previews.map { it.path }.toSet() == expected)
        require(index.previews.all { preview ->
            val scene = preview.path.substringAfter('/').substringBeforeLast('-').substringBeforeLast('-')
            val role = if (scene in setOf("player", "queue", "navigation")) scene else "other"
            preview.sha256.matches(Regex("[0-9a-f]{64}")) &&
                preview.role == role && preview.caption?.themeTextLength?.let { it in 1..160 } != false
        })
        return index.previews.sortedBy { automaticPreviews.indexOf(it.path.substringAfterLast('/').substringBeforeLast('.')) }
    }
}

@Serializable
private data class StockPreviewIndex(val schemaVersion: Int, val previews: List<StorePreview>)
