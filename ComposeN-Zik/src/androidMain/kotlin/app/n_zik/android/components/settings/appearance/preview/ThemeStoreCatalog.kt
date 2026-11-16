package app.n_zik.android.components.settings.appearance.preview

import android.util.AtomicFile
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import app.n_zik.android.utils.coroutines.NzikDispatchers

private const val CatalogBase = "https://raw.githubusercontent.com/N-Zik-Group/N-Zik-Theme-Store/refs/heads/main/"
internal const val ThemeMetadataLimit = 512 * 1024
private const val CatalogSnapshotLimit = 16 * 1024 * 1024
private val catalogSnapshotLock = Any()
internal const val ThemeDocumentLimit = 128 * 1024
private const val PreviewLimit = 4 * 1024 * 1024

@Serializable
private data class CatalogIndex(val schemaVersion: Int, val themes: List<CatalogEntry>)

@Serializable
private data class CatalogEntry(val themeId: String, val revision: Int, val manifestSha256: String)

@Serializable
private data class VerifiedCreators(val schemaVersion: Int, val githubIds: List<Long>)

@Serializable
private data class CreatorIndex(val schemaVersion: Int, val creators: List<CreatorIndexEntry>)

@Serializable
private data class CreatorIndexEntry(val githubId: Long, val profileSha256: String)

@Serializable
internal data class CreatorMedia(val source: String, val asset: StoreFile? = null)

@Serializable
internal data class CreatorProfile(
    val schemaVersion: Int,
    val githubId: Long,
    val login: String,
    val displayName: String,
    val bio: String,
    val avatar: CreatorMedia,
    val banner: CreatorMedia,
)

@Serializable
internal data class StoreAuthor(val githubId: Long, val login: String)

@Serializable
internal data class StoreCompatibility(val minVersionCode: Int?)

@Serializable
internal data class StoreFile(val path: String, val sha256: String)

@Serializable
internal data class StorePreview(val path: String, val role: String, val sha256: String, val caption: String? = null)

@Serializable
internal data class StoreTheme(
    val schemaVersion: Int,
    val themeId: String,
    val revision: Int,
    val title: String,
    val description: String,
    val author: StoreAuthor,
    val tags: List<String>,
    val compatibility: StoreCompatibility,
    val appearance: StoreFile,
    val previews: List<StorePreview>,
    val publishedOn: String? = null,
    val localCreatorName: String? = null,
) {
    val directory: String get() = "catalog/themes/$themeId/$revision/"

    fun playerPreview(glass: Boolean): StorePreview? {
        val suffix = if (glass) "player-glass" else "player-normal"
        return previews.firstOrNull { it.role == "player" && it.path.substringBeforeLast('.') == "previews/$suffix" }
            ?: previews.firstOrNull { it.role == "player" }
            ?: previews.firstOrNull()
    }

    fun galleryPreviews(glass: Boolean?): List<StorePreview> =
        previews.filter { it.glassVariant == null || it.glassVariant == glass }
}

internal val StorePreview.glassVariant: Boolean?
    get() = when {
        path.substringBeforeLast('.').endsWith("-glass") -> true
        path.substringBeforeLast('.').endsWith("-normal") -> false
        else -> null
    }

private val tagMarks = Regex("\\p{M}+")
private val tagPunctuation = Regex("[^\\p{L}\\p{N}]+")
private val tagSeparators = Regex("[\\s_-]+")

internal fun canonicalStoreTag(tag: String): String {
    val normalized = java.text.Normalizer.normalize(tag.trim(), java.text.Normalizer.Form.NFKD)
        .replace(tagMarks, "").lowercase(java.util.Locale.ROOT)
        .replace(tagSeparators, "-").trim('-')
    return when (normalized.replace(tagPunctuation, "")) {
        "stock" -> "stock"
        "builtin" -> "built-in"
        "audiowaves" -> "audio-waves"
        "animatedgradient" -> "animated-gradient"
        "darkmode" -> "dark"
        "glasseffect" -> "glass"
        "minimalistic" -> "minimal"
        else -> normalized
    }
}

internal val reservedStoreTags = setOf("stock", "built-in")

internal fun Iterable<String>.canonicalStoreTags(): List<String> =
    map(::canonicalStoreTag).filter(String::isNotEmpty).distinct()

/** The index appends newly published or revised entries, breaking ties within a publication day. */
internal fun List<StoreTheme>.mostRecentlyPublishedFirst(): List<StoreTheme> =
    asReversed().sortedByDescending(StoreTheme::publishedOn)

internal fun List<StoreTheme>.filterStoreThemes(query: String, tag: String?): List<StoreTheme> {
    val term = query.trim()
    return filter { theme ->
        (tag == null || canonicalStoreTag(tag) in theme.tags.canonicalStoreTags()) &&
            (theme.title.contains(term, ignoreCase = true) || theme.author.login.contains(term, ignoreCase = true))
    }
}

@Serializable
internal data class StoreReviewDocument(val schemaVersion: Int, val reviews: List<StoreReview>)

@Serializable
internal data class StoreReview(val author: StoreAuthor, val kind: String, val rating: Int, val comment: String)

internal data class StoreReviewSummary(val comments: List<StoreReview>) {
    val average: Double = comments.map { it.rating }.average().takeIf { it.isFinite() } ?: 0.0
    val count: Int get() = comments.size
}

internal fun parseStoreReviews(source: String): StoreReviewSummary {
    val document = Json.decodeFromString<StoreReviewDocument>(source)
    require(document.schemaVersion == 1)
    require(document.reviews.distinctBy { it.author.githubId }.size == document.reviews.size)
    require(document.reviews.all {
        it.author.githubId > 0 && login.matches(it.author.login) && it.kind in setOf("maintainer", "community") &&
            it.rating in 1..5 && it.comment.isNotBlank() && it.comment.themeTextLength <= 2000
    })
    return StoreReviewSummary(document.reviews)
}

@Serializable
private data class CatalogSnapshot(val index: String, val manifests: List<String>, val syncedAt: Long? = null)

internal data class ThemeCatalogState(val themes: List<StoreTheme>, val syncedAt: Long?)

private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
private val hash = Regex("[0-9a-f]{64}")
private val login = Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}")
private val creatorAssetPath = Regex("[a-z0-9][a-z0-9_-]{0,63}[.](webp|png|jpg)")
private val previewPath = Regex("previews/[a-z0-9][a-z0-9_-]{0,79}[.](jpg|png|webp|gif)")

internal val String.themeTextLength: Int get() = codePointCount(0, length)

internal fun String.takeThemeText(limit: Int): String =
    substring(0, offsetByCodePoints(0, minOf(limit, themeTextLength)))

private fun parseCatalogIndex(source: String): CatalogIndex =
    Json.decodeFromString<CatalogIndex>(source).also { index ->
        require(index.schemaVersion == 1)
        require(index.themes.distinctBy { it.themeId }.size == index.themes.size)
        require(index.themes.all { uuid.matches(it.themeId) && it.revision > 0 && hash.matches(it.manifestSha256) })
    }

private fun validateCatalog(snapshot: CatalogSnapshot): List<StoreTheme> {
    val entries = parseCatalogIndex(snapshot.index).themes
    require(entries.size == snapshot.manifests.size)
    return entries.zip(snapshot.manifests) { entry, manifest ->
        require(manifest.encodeToByteArray().sha256() == entry.manifestSha256)
        parseStoreTheme(manifest).also { theme ->
            require(theme.themeId == entry.themeId && theme.revision == entry.revision)
        }
    }
}

internal fun parseStoreTheme(manifest: String, allowLocalAuthor: Boolean = false): StoreTheme =
    Json.decodeFromJsonElement(StoreTheme.serializer(), JsonObject(Json.parseToJsonElement(manifest).jsonObject - "license")).also { theme ->
        require(theme.schemaVersion == 1 && uuid.matches(theme.themeId) && theme.revision > 0)
        require(theme.title.themeTextLength in 1..100 && theme.description.themeTextLength in 1..1000)
        require(theme.localCreatorName == null || allowLocalAuthor && theme.localCreatorName.themeTextLength in 1..100)
        require((theme.author.githubId > 0 || allowLocalAuthor && theme.author == StoreAuthor(0, "local")) &&
            login.matches(theme.author.login))
        require(theme.tags.size <= 8 && theme.tags.distinct() == theme.tags && theme.tags.all { it.themeTextLength in 1..30 })
        require(theme.compatibility.minVersionCode?.let { it > 0 } != false)
        require(theme.appearance.path == "appearance.json" && hash.matches(theme.appearance.sha256))
        require(theme.previews.size <= 36 && theme.previews.distinctBy { it.path }.size == theme.previews.size)
        require(theme.previews.all { preview ->
            previewPath.matches(preview.path) && hash.matches(preview.sha256) &&
                preview.role in setOf("player", "queue", "navigation", "other") &&
                preview.caption?.themeTextLength?.let { it in 1..160 } != false
        })
    }.withMinimumCompatibility()

internal class ThemeStoreCatalog(
    private val directory: File,
    private val fetch: (String, Int) -> ByteArray = ::fetchStoreFile,
) {
    private val snapshot = AtomicFile(File(directory, "catalog-v1.json"))
    internal val previewMetadata = android.util.LruCache<String, StoreCardPreview>(128)

    suspend fun cached(): List<StoreTheme>? = cachedState()?.themes

    suspend fun cachedState(): ThemeCatalogState? = withContext(NzikDispatchers.DATA) {
        try {
            val stored = synchronized(catalogSnapshotLock) {
                snapshot.openRead().use { it.readThemeBytes(CatalogSnapshotLimit) }
            }.decodeToString().let { Json.decodeFromString<CatalogSnapshot>(it) }
            ThemeCatalogState(validateCatalog(stored), stored.syncedAt)
        } catch (error: java.io.FileNotFoundException) {
            null
        } catch (error: Exception) {
            Timber.tag("ThemeStore").w(error, "Could not read the cached catalog")
            null
        }
    }

    suspend fun refresh(): List<StoreTheme> = refreshState().themes

    suspend fun firstPublicationDate(theme: StoreTheme): String? =
        publishedRevision(theme, 1).publishedOn

    internal suspend fun publishedRevision(theme: StoreTheme, revision: Int): StoreTheme =
        runInterruptible(NzikDispatchers.DATA) {
            require(revision in 1..theme.revision)
            if (revision == theme.revision) return@runInterruptible theme
            val manifest = fetch("catalog/themes/${theme.themeId}/$revision/manifest.json", ThemeMetadataLimit)
            val previous = parseStoreTheme(manifest.decodeToString(throwOnInvalidSequence = true))
            require(previous.themeId == theme.themeId && previous.revision == revision)
            previous
        }

    suspend fun refreshState(): ThemeCatalogState = runInterruptible(NzikDispatchers.DATA) {
        val indexBytes = fetch("catalog/themes/index.json", ThemeMetadataLimit)
        val index = indexBytes.decodeToString(throwOnInvalidSequence = true)
        val entries = parseCatalogIndex(index).themes
        var remainingBytes = CatalogSnapshotLimit - indexBytes.size
        val manifests = entries.map { entry ->
            // Moderation can update a manifest's publication date within the same revision.
            val path = "catalog/themes/${entry.themeId}/${entry.revision}/manifest.json?sha=${entry.manifestSha256}"
            val bytes = fetch(path, ThemeMetadataLimit)
            require(bytes.size <= remainingBytes) { "Catalog manifests exceeded the total byte limit" }
            remainingBytes -= bytes.size
            bytes.decodeToString(throwOnInvalidSequence = true)
        }
        val downloaded = CatalogSnapshot(index, manifests)
        val themes = validateCatalog(downloaded)
        val refreshed = downloaded.copy(syncedAt = System.currentTimeMillis())
        val encoded = Json.encodeToString(refreshed).encodeToByteArray()
        require(encoded.size <= CatalogSnapshotLimit) { "Catalog snapshot exceeded its byte limit" }
        synchronized(catalogSnapshotLock) {
            check(directory.isDirectory || directory.mkdirs() || directory.isDirectory)
            writeThemeFile(snapshot, encoded)
        }
        ThemeCatalogState(themes, refreshed.syncedAt)
    }

    suspend fun preview(theme: StoreTheme, glass: Boolean): File? {
        val preview = theme.playerPreview(glass) ?: return null
        return preview(theme, preview)
    }

    suspend fun preview(theme: StoreTheme, preview: StorePreview): File? {
        require(preview in theme.previews)
        val lock = catalogPreviewLocks[preview.sha256.take(2).toInt(16) % catalogPreviewLocks.size]
        return lock.withLock {
            runInterruptible(NzikDispatchers.DATA) { previewFile(theme, preview) }
        }
    }

    private fun previewFile(theme: StoreTheme, preview: StorePreview): File? {
        val file = File(directory, "${preview.sha256}.${preview.path.substringAfterLast('.')}")
        return try {
            val cached = if (file.isFile) file.inputStream().use { it.readThemeBytes(PreviewLimit) } else null
            if (cached?.sha256() != preview.sha256) {
                val bytes = fetch("${theme.directory}${preview.path}?sha=${preview.sha256}", PreviewLimit)
                require(bytes.sha256() == preview.sha256)
                check(directory.isDirectory || directory.mkdirs() || directory.isDirectory)
                writeThemeFile(AtomicFile(file), bytes)
            }
            file
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("ThemeStore").w(error, "Could not load preview for %s", theme.themeId)
            null
        }
    }

    suspend fun verifiedCreatorIds(): Set<Long> = runInterruptible(NzikDispatchers.DATA) {
        val disk = AtomicFile(File(directory, "verified-creators.json"))
        fun validated(bytes: ByteArray): Set<Long> {
            val document = Json.decodeFromString<VerifiedCreators>(bytes.decodeToString(throwOnInvalidSequence = true))
            require(document.schemaVersion == 1 && document.githubIds.all { it > 0 })
            return document.githubIds.toSet().also { require(it.size == document.githubIds.size) }
        }
        val bytes = try {
            fetch("catalog/creators/verified.json", ThemeMetadataLimit)
        } catch (error: java.io.IOException) {
            if (!disk.baseFile.isFile) throw error
            return@runInterruptible validated(disk.openRead().use { it.readThemeBytes(ThemeMetadataLimit) })
        }
        validated(bytes).also {
            if (!disk.baseFile.isFile || !disk.readFully().contentEquals(bytes)) writeThemeFile(disk, bytes)
        }
    }

    suspend fun creators(): List<CreatorProfile> = runInterruptible(NzikDispatchers.DATA) {
        val index = Json.decodeFromString<CreatorIndex>(
            fetch("catalog/creators/index.json", ThemeMetadataLimit).decodeToString(throwOnInvalidSequence = true),
        )
        require(index.schemaVersion == 1 && index.creators.distinctBy { it.githubId }.size == index.creators.size)
        index.creators.map { entry ->
            require(entry.githubId > 0 && hash.matches(entry.profileSha256))
            val bytes = fetch("catalog/creators/${entry.githubId}/profile.json?sha=${entry.profileSha256}", ThemeMetadataLimit)
            require(bytes.sha256() == entry.profileSha256)
            Json.decodeFromString<CreatorProfile>(bytes.decodeToString(throwOnInvalidSequence = true)).also {
                require(it.schemaVersion == 1 && it.githubId == entry.githubId && login.matches(it.login))
                require(it.displayName.isNotBlank() && it.displayName.themeTextLength <= 60 && it.bio.themeTextLength <= 280)
            }
        }
    }

    suspend fun creatorImage(profile: CreatorProfile, media: CreatorMedia): Any? {
        if (media.source == "github") return "https://avatars.githubusercontent.com/u/${profile.githubId}"
        val asset = media.asset ?: return null
        if (media.source != "custom" || !creatorAssetPath.matches(asset.path) || !hash.matches(asset.sha256)) return null
        return runInterruptible(NzikDispatchers.DATA) {
            val file = File(directory, "${asset.sha256}.${asset.path.substringAfterLast('.')}")
            if (file.isFile && file.inputStream().use { it.readThemeBytes(PreviewLimit) }.sha256() == asset.sha256) return@runInterruptible file
            val bytes = fetch("catalog/creators/${profile.githubId}/${asset.path}?sha=${asset.sha256}", PreviewLimit)
            require(bytes.sha256() == asset.sha256)
            check(directory.isDirectory || directory.mkdirs() || directory.isDirectory)
            writeThemeFile(AtomicFile(file), bytes)
            file
        }
    }

    suspend fun reviews(theme: StoreTheme): StoreReviewSummary? = try {
        runInterruptible(NzikDispatchers.DATA) {
            val bytes = fetch(theme.directory + "reviews.json", ThemeMetadataLimit)
            parseStoreReviews(bytes.decodeToString(throwOnInvalidSequence = true))
        }
    } catch (_: StoreFileNotFound) {
        StoreReviewSummary(emptyList())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Timber.tag("ThemeStore").w(error, "Could not load reviews for %s", theme.themeId)
        null
    }

    suspend fun appearanceSize(theme: StoreTheme): Int? = try {
        runInterruptible(NzikDispatchers.DATA) { verifiedAppearance(theme).size }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Timber.tag("ThemeStore").w(error, "Could not check theme size for %s", theme.themeId)
        null
    }

    suspend fun download(theme: StoreTheme): String = runInterruptible(NzikDispatchers.DATA) {
        verifiedAppearance(theme).decodeToString(throwOnInvalidSequence = true).also { parseThemeV1(it) }
    }

    suspend fun appearance(theme: StoreTheme, saved: SavedTheme?): StoreAppearance =
        runInterruptible(NzikDispatchers.DATA) {
            val bytes = saved?.document?.encodeToByteArray()
                ?.takeIf { it.size <= ThemeDocumentLimit && it.sha256() == theme.appearance.sha256 }
                ?: verifiedAppearance(theme)
            val document = bytes.decodeToString(throwOnInvalidSequence = true)
            StoreAppearance(document, bytes.size, parseThemeV1(document).toStoreGroups())
        }

    private fun verifiedAppearance(theme: StoreTheme): ByteArray {
        val bytes = fetch(theme.directory + theme.appearance.path, ThemeDocumentLimit)
        require(bytes.sha256() == theme.appearance.sha256)
        return bytes
    }
}

internal fun themeStoreCatalog(context: android.content.Context, offline: Boolean) =
    ThemeStoreCatalog(File(context.filesDir, "theme-store-catalog")) { path, limit ->
        val bundledPrefix = "catalog/themes/$BundledThemeId/1/"
        if (path.startsWith(bundledPrefix)) {
            context.assets.open("theme-store-default/" + path.substringBefore('?').removePrefix(bundledPrefix)).use { it.readThemeBytes(limit) }
        } else {
            if (offline || app.n_zik.android.core.network.OfflineMode.enabled(context)) {
                throw java.io.IOException("Theme Store is offline")
            }
            fetchStoreFile(path, limit)
        }
    }

private val catalogPreviewLocks = List(8) { Mutex() }
private data class CachedStoreFile(val bytes: ByteArray?, val loadedAt: Long)
private val storeReads = object : android.util.LruCache<String, CachedStoreFile>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: CachedStoreFile): Int =
        maxOf(64 * 1024, 256 + key.length * 2 + (value.bytes?.size ?: 0))
}
private val storeReadLocks = List(16) { Any() }
private const val StoreReadTtlMs = 5 * 60 * 1000L

internal fun expireThemeStoreIndex() {
    storeReads.remove("catalog/themes/index.json")
}

internal fun expireThemeStoreReads() {
    storeReads.evictAll()
}

private val catalogClient = OkHttpClient.Builder()
    .followRedirects(false)
    .callTimeout(10, TimeUnit.SECONDS)
    .build()

private class StoreFileNotFound : java.io.IOException()

internal fun fetchStoreFile(path: String, limit: Int): ByteArray =
    synchronized(storeReadLocks[(path.hashCode() and Int.MAX_VALUE) % storeReadLocks.size]) {
        val now = android.os.SystemClock.elapsedRealtime()
        storeReads.get(path)?.takeIf { now - it.loadedAt < StoreReadTtlMs }?.let {
            return@synchronized it.bytes?.also { bytes -> require(bytes.size <= limit) } ?: throw StoreFileNotFound()
        }
        val expectedHash = path.substringAfter("?sha=", "").takeIf { hash.matches(it) }
        try {
            fetchStoreFileFromNetwork(path, limit).also { bytes ->
                require(expectedHash == null || bytes.sha256() == expectedHash)
                storeReads.put(path, CachedStoreFile(bytes, android.os.SystemClock.elapsedRealtime()))
            }
        } catch (missing: StoreFileNotFound) {
            if (expectedHash == null) {
                storeReads.put(path, CachedStoreFile(null, android.os.SystemClock.elapsedRealtime()))
            }
            throw missing
        }
    }

private fun fetchStoreFileFromNetwork(path: String, limit: Int): ByteArray {
    val request = Request.Builder().url(CatalogBase + path)
        .header("Cache-Control", "no-cache")
        .build()
    return catalogClient.newCall(request).execute().use { response ->
        if (response.code == 404) throw StoreFileNotFound()
        check(response.isSuccessful) { "Theme Store HTTP ${response.code}" }
        checkNotNull(response.body).byteStream().use { it.readThemeBytes(limit) }
    }
}

internal fun InputStream.readThemeBytes(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var count = read(buffer)
    while (count >= 0) {
        require(output.size() + count <= limit) { "Theme file exceeded its byte limit" }
        output.write(buffer, 0, count)
        count = read(buffer)
    }
    return output.toByteArray()
}

internal fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

internal fun writeThemeFile(file: AtomicFile, bytes: ByteArray) {
    val output = file.startWrite()
    try {
        output.write(bytes)
        file.finishWrite(output)
    } catch (error: Exception) {
        file.failWrite(output)
        throw error
    }
}
