package app.n_zik.android.components.settings.appearance.preview

import android.content.Context
import android.util.AtomicFile
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object ThemePreviewAssets {
    private const val path = "demo/v1/"
    private val names = setOf("atlas.jpg", "signal.jpg", "tide.jpg", "violet.jpg", "silence.mp3")
    private val lock = Mutex()
    private val bannerLock = Mutex()
    private val palettes = listOf("atlas", "signal", "tide", "violet", "ember")
    private val coverNames = palettes.map { "$it.jpg" }.toSet()
    private val bannerNames = palettes.map { "banners/$it.webp" }

    fun file(context: Context, name: String): File =
        File(context.filesDir, "theme-preview-v1/$name")

    suspend fun prepare(context: Context) = lock.withLock {
        runInterruptible(NzikDispatchers.DATA) {
            val index = demoIndex(context, "index.json")
            val manifest = Json.decodeFromString<PreviewAssetsIndex>(index.decodeToString(throwOnInvalidSequence = true))
            require(manifest.schemaVersion == 1 && manifest.assets.size == names.size && manifest.assets.map { it.name }.toSet() == names)
            for (asset in manifest.assets) install(context, asset, 512 * 1024)
            val visuals = demoVisuals(context)
            install(context, visuals.covers.single { it.name == "ember.jpg" }, 512 * 1024)
        }
    }

    suspend fun banner(context: Context, githubId: Long): File = bannerLock.withLock {
        runInterruptible(NzikDispatchers.DATA) {
            val asset = demoVisuals(context).banners.single {
                it.name == bannerNames[Math.floorMod(githubId, bannerNames.size.toLong()).toInt()]
            }
            install(context, asset, 512 * 1024)
        }
    }

    private fun demoVisuals(context: Context): PreviewVisualsIndex {
        val visuals = Json.decodeFromString<PreviewVisualsIndex>(demoIndex(context, "visuals.json").decodeToString(throwOnInvalidSequence = true))
        require(visuals.schemaVersion == 1)
        require(visuals.covers.size == coverNames.size && visuals.covers.map { it.name }.toSet() == coverNames)
        require(visuals.banners.size == bannerNames.size && visuals.banners.map { it.name }.toSet() == bannerNames.toSet())
        return visuals
    }

    private fun demoIndex(context: Context, name: String): ByteArray {
        val index = AtomicFile(file(context, name))
        val cached = index.baseFile.takeIf(File::isFile)?.let { index.openRead().use { it.readThemeBytes(8192) } }
        val bytes = try {
            fetchDemo(context, name, 8192)
        } catch (error: java.io.IOException) {
            return cached ?: throw error
        }
        if (cached?.contentEquals(bytes) != true) writeThemeFile(index, bytes)
        return bytes
    }

    private fun install(context: Context, asset: PreviewAsset, limit: Int): File {
        require(asset.sha256.matches(Regex("[0-9a-f]{64}")))
        val target = AtomicFile(file(context, asset.name))
        if (target.baseFile.isFile && target.openRead().use { it.readThemeBytes(limit) }.sha256() == asset.sha256) return target.baseFile
        val bytes = fetchDemo(context, asset.name, limit)
        require(bytes.sha256() == asset.sha256) { "Demo asset checksum mismatch: ${asset.name}" }
        writeThemeFile(target, bytes)
        return target.baseFile
    }

    private fun fetchDemo(context: Context, asset: String, limit: Int): ByteArray {
        if (app.n_zik.android.core.network.OfflineMode.enabled(context)) throw java.io.IOException("Theme Store is offline")
        return fetchStoreFile(path + asset, limit)
    }
}

@Serializable
private data class PreviewAssetsIndex(val schemaVersion: Int, val assets: List<PreviewAsset>)

@Serializable
private data class PreviewAsset(val name: String, val sha256: String)

@Serializable
private data class PreviewVisualsIndex(val schemaVersion: Int, val covers: List<PreviewAsset>, val banners: List<PreviewAsset>)
