package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class ThemeStoreDownloadTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun downloadRequiresEveryPreviewAndCachesThemForOfflineAccess() = runTest {
        val appearance = encodeThemeAppearance(
            emptyMap<String, Any>(), emptyMap<String, Any>(), schemaVersion = ThemeAppearanceV2,
        ).encodeToByteArray()
        fun png(color: Int): ByteArray = java.io.ByteArrayOutputStream().use { output ->
            val bitmap = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(color)
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            } finally {
                bitmap.recycle()
            }
        }
        val cover = png(android.graphics.Color.RED)
        val queue = png(android.graphics.Color.BLUE)
        val theme = StoreTheme(
            2, "99edc843-6a9b-5753-8e76-cc11fe9b3295", 2, "Theme", "Theme",
            StoreAuthor(1, "Creator"), emptyList(), StoreCompatibility(99),
            StoreFile("appearance.json", appearance.sha256()),
            listOf(
                StorePreview("previews/player-normal.png", "player", cover.sha256()),
                StorePreview("previews/queue-normal.png", "queue", queue.sha256()),
            ),
        )
        var queueResponse = "corrupt".encodeToByteArray()
        var connectionFailure: IOException? = null
        val onlineStore = ThemeStoreCatalog(folder.root) { path, _ ->
            when (path.substringBefore('?').removePrefix(theme.directory)) {
                theme.appearance.path -> appearance
                theme.previews[0].path -> cover
                theme.previews[1].path -> connectionFailure?.let { throw it } ?: queueResponse
                else -> error("Unexpected path ${path}")
            }
        }
        val disconnected = IOException("Connection interrupted")
        connectionFailure = disconnected
        val interrupted = runCatching { onlineStore.downloadForOffline(theme) }.exceptionOrNull() as? IOException
        assertEquals(disconnected.message, interrupted?.message)

        connectionFailure = null
        val rejected = runCatching { onlineStore.downloadForOffline(theme) }.exceptionOrNull()
        assertTrue("Corrupt preview must fail without a network retry, got $rejected", rejected != null && rejected !is IOException)

        queueResponse = queue
        assertEquals(appearance.decodeToString(), onlineStore.downloadForOffline(theme).document)
        val offlineStore = ThemeStoreCatalog(folder.root) { _, _ -> throw IOException("Offline") }
        val assets = theme.previews.map { preview -> offlineStore.preview(theme, preview)?.readBytes()?.sha256() }
        assertEquals(theme.previews.map(StorePreview::sha256), assets)
    }
}
