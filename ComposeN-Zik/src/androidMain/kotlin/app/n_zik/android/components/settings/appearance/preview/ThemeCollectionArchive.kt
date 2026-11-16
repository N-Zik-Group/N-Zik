package app.n_zik.android.components.settings.appearance.preview

import android.content.Context
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private const val CollectionIndexPath = "collection.json"
private const val CollectionThemeLimit = 512
private const val CollectionIndexLimit = 256 * 1024
private const val CollectionBytesLimit = 512L * 1024 * 1024
internal const val CollectionZipLimit = CollectionBytesLimit + 8 * 1024 * 1024
private const val CollectionEntryLimit = 1 + CollectionThemeLimit * (36 + 3)
private val collectionIdPattern = Regex("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}:[1-9][0-9]{0,9}")
private val collectionHashPattern = Regex("[0-9a-f]{64}")
private val emptyThemeReviews = """{"schemaVersion":1,"reviews":[]}""".encodeToByteArray()

@Serializable
private data class ThemeCollectionIndex(val schemaVersion: Int, val themes: List<ThemeCollectionRecord>)

@Serializable
private data class ThemeCollectionRecord(
    val id: String,
    val manifestSha256: String,
    val storeOrigin: ThemeStoreOrigin? = null,
) {
    val directory: String get() = "themes/${id.substringBefore(':')}/${id.substringAfter(':')}/"
}

internal data class ThemeCollectionEntry(val id: String, val name: String)
internal data class ThemeCollectionResult(val imported: Int, val skipped: Int)

private data class CollectionExport(
    val source: SavedTheme,
    val record: ThemeCollectionRecord,
    val manifest: ByteArray,
)

internal suspend fun writeThemeCollectionArchive(
    context: Context,
    output: OutputStream,
    themes: List<SavedTheme>,
    catalog: ThemeStoreCatalog? = null,
    allowNetwork: Boolean = false,
) {
    require(themes.size in 1..CollectionThemeLimit)
    val exports = themes.sortedByDescending(SavedTheme::imported).map { saved ->
        val theme = requireNotNull(saved.theme)
        require(theme.themeId != BundledThemeId && collectionIdPattern.matches(saved.id))
        val localTheme = (if (saved.imported) theme else {
            theme.copy(themeId = localThemeArchiveId(Json.encodeToString(theme).encodeToByteArray()))
        }).withMinimumCompatibility()
        val bytes = Json.encodeToString(localTheme).encodeToByteArray()
        require(bytes.size <= ThemeMetadataLimit)
        parseStoreTheme(bytes.decodeToString(), allowLocalAuthor = true)
        themeArchivePaths(localTheme)
        val id = "${localTheme.themeId}:${localTheme.revision}"
        val origin = if (saved.imported) saved.storeOrigin else ThemeStoreOrigin(theme.themeId, theme.revision)
        CollectionExport(saved, ThemeCollectionRecord(id, bytes.sha256(), origin), bytes)
    }.distinctBy { it.record.id }
    val index = ThemeCollectionIndex(1, exports.map { it.record })
    val indexBytes = Json.encodeToString(index).encodeToByteArray()
    require(indexBytes.size <= CollectionIndexLimit)
    var written = 0L
    ZipOutputStream(output).use { zip ->
        fun put(path: String, bytes: ByteArray) {
            written += bytes.size
            require(written <= CollectionBytesLimit) { "Theme collection exceeded its byte limit" }
            zip.putNextEntry(ZipEntry(path))
            zip.write(bytes)
            zip.closeEntry()
        }
        put(CollectionIndexPath, indexBytes)
        for ((saved, record, metadata) in exports) {
            val theme = checkNotNull(saved.theme)
            val appearance = saved.document.encodeToByteArray()
            require(appearance.size <= ThemeDocumentLimit && appearance.sha256() == theme.appearance.sha256)
            parseThemeV1(saved.document)
            put(record.directory + "manifest.json", metadata)
            put(record.directory + theme.appearance.path, appearance)
            put(record.directory + "reviews.json", emptyThemeReviews)
            for (preview in theme.previews) {
                val cached = themeArchivePreviewFile(context, preview).takeIf(File::isFile)?.let {
                    runCatching { it.inputStream().use { input -> input.readThemeBytes(SubmissionPreviewLimit) } }
                        .getOrNull()?.takeIf { bytes -> bytes.sha256() == preview.sha256 }
                }
                val bytes = cached ?: if (allowNetwork && !saved.imported && catalog != null) {
                    checkNotNull(catalog.preview(theme, preview)).inputStream().use {
                        it.readThemeBytes(SubmissionPreviewLimit)
                    }
                } else error("Preview file is not available offline")
                validateThemeArchivePreview(preview, bytes)
                put(record.directory + preview.path, bytes)
            }
        }
    }
}

internal fun readThemeCollectionIndex(context: Context, source: InputStream): List<ThemeCollectionEntry> =
    withThemeCollection(context, source, block = ::readThemeCollectionIndex)

internal fun readThemeCollectionIndex(zip: ZipFile): List<ThemeCollectionEntry> =
    readCollectionThemes(zip).map { entry -> ThemeCollectionEntry(entry.id, entry.theme.title) }

internal fun restoreThemeCollection(
    context: Context,
    source: InputStream,
    library: ThemeStoreLibrary,
    selectedIds: Set<String>,
): ThemeCollectionResult {
    val validated = prepareThemeCollectionRestore(context, source, library, selectedIds)
    val imported = library.restoreImported(validated)
    return ThemeCollectionResult(imported, selectedIds.size - imported)
}

internal fun prepareThemeCollectionRestore(
    context: Context,
    source: InputStream,
    library: ThemeStoreLibrary,
    selectedIds: Set<String>,
): List<SavedTheme> = withThemeCollection(context, source) { zip ->
    prepareThemeCollectionRestore(context, zip, library, selectedIds)
}

internal fun prepareThemeCollectionRestore(
    context: Context,
    zip: ZipFile,
    library: ThemeStoreLibrary,
    selectedIds: Set<String>,
): List<SavedTheme> {
    val available = readCollectionThemes(zip)
    require(available.map(CollectionTheme::id).containsAll(selectedIds)) { "Selected themes are missing from the collection" }
    val pending = available.filter { it.id in selectedIds && library.download(it.id) == null }
    // Validate the complete selection before changing the library or publishing preview files.
    val validated = pending.map { (_, directory, theme, storeOrigin) ->
        val document = validateThemeArchiveDocuments(theme) { path, limit -> zip.read(directory + path, limit) }
        for (preview in theme.previews) {
            validateThemeArchivePreview(preview, zip.read(directory + preview.path, SubmissionPreviewLimit))
        }
        SavedTheme(theme.title, document, theme, imported = true, storeOrigin = storeOrigin)
    }
    pending.forEach { (_, directory, theme) ->
        cacheThemeArchivePreviews(context, theme) { zip.read(directory + it, SubmissionPreviewLimit) }
    }
    return validated
}

/** Only catalog-verified revisions can replace restored local copies with Store downloads. */
internal fun bindRestoredStoreThemes(library: ThemeStoreLibrary, trustedThemes: List<StoreTheme>): Int =
    library.bindTrustedStoreOrigins(trustedThemes)

private data class CollectionTheme(
    val id: String,
    val directory: String,
    val theme: StoreTheme,
    val storeOrigin: ThemeStoreOrigin? = null,
)

private fun readCollectionThemes(zip: ZipFile): List<CollectionTheme> {
    require(File(zip.name).length() <= CollectionZipLimit) { "Theme collection exceeded its byte limit" }
    require(zip.size() in 1..CollectionEntryLimit)
    val seen = mutableSetOf<String>()
    var size = 0L
    zip.entries().asSequence().forEach { entry ->
        require(!entry.isDirectory && entry.size >= 0 && seen.add(entry.name))
        require(entry.size <= CollectionBytesLimit - size) { "Theme collection exceeded its byte limit" }
        size += entry.size
    }
    if (CollectionIndexPath !in seen) {
        val bytes = zip.read("manifest.json", ThemeMetadataLimit)
        val theme = parseStoreTheme(bytes.decodeToString(throwOnInvalidSequence = true), allowLocalAuthor = true)
        require(theme.themeId != BundledThemeId && seen == themeArchivePaths(theme))
        checkCollectionThemeFileSizes(zip, "", theme)
        val local = theme.copy(themeId = localThemeArchiveId(bytes))
        return listOf(CollectionTheme("${local.themeId}:${local.revision}", "", local))
    }
    val index = Json.decodeFromString<ThemeCollectionIndex>(
        zip.read(CollectionIndexPath, CollectionIndexLimit).decodeToString(throwOnInvalidSequence = true)
    )
    require(index.schemaVersion == 1 && index.themes.size in 1..CollectionThemeLimit)
    require(index.themes.distinctBy { it.id }.size == index.themes.size)
    val expected = mutableSetOf(CollectionIndexPath)
    val themes = index.themes.map { record ->
        require(collectionIdPattern.matches(record.id) && collectionHashPattern.matches(record.manifestSha256))
        val bytes = zip.read(record.directory + "manifest.json", ThemeMetadataLimit)
        require(bytes.sha256() == record.manifestSha256)
        val theme = parseStoreTheme(bytes.decodeToString(throwOnInvalidSequence = true), allowLocalAuthor = true)
        require(theme.themeId != BundledThemeId && "${theme.themeId}:${theme.revision}" == record.id)
        for (path in checkCollectionThemeFileSizes(zip, record.directory, theme)) require(expected.add(path))
        record.storeOrigin?.let { origin ->
            require(origin.themeId != BundledThemeId &&
                collectionIdPattern.matches("${origin.themeId}:${origin.revision}"))
        }
        CollectionTheme(record.id, record.directory, theme, record.storeOrigin)
    }
    require(seen == expected)
    return themes
}

private fun checkCollectionThemeFileSizes(zip: ZipFile, directory: String, theme: StoreTheme): List<String> =
    themeArchivePaths(theme).map { path ->
        val entry = directory + path
        val limit = when {
            path.startsWith("previews/") -> SubmissionPreviewLimit
            path == "appearance.json" -> ThemeDocumentLimit
            else -> ThemeMetadataLimit
        }
        require(checkNotNull(zip.getEntry(entry)).size in 1..limit.toLong())
        entry
    }

private fun ZipFile.read(path: String, limit: Int): ByteArray {
    val entry = checkNotNull(getEntry(path))
    require(entry.size in 1..limit.toLong())
    return getInputStream(entry).use { it.readThemeBytes(limit) }.also {
        require(it.size.toLong() == entry.size)
    }
}

internal suspend fun writeStagedThemeArchive(
    context: Context,
    destination: Uri,
    write: suspend (OutputStream) -> Unit,
) {
    val staged = File.createTempFile("theme-export-", ".zip", context.cacheDir)
    try {
        staged.outputStream().use { write(it) }
        checkNotNull(context.contentResolver.openOutputStream(destination, "wt")).use { output ->
            staged.inputStream().use { source -> source.copyTo(output) }
        }
    } finally {
        staged.delete()
    }
}

internal inline fun <T> withThemeCollection(
    context: Context,
    source: InputStream,
    archiveLimit: Long = CollectionZipLimit,
    block: (ZipFile) -> T,
): T {
    val archive = File.createTempFile("theme-collection-", ".zip", context.cacheDir)
    try {
        archive.outputStream().use { output ->
            val buffer = ByteArray(32 * 1024)
            var size = 0L
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                require(count.toLong() <= archiveLimit - size) { "Theme collection exceeded its byte limit" }
                size += count
                output.write(buffer, 0, count)
            }
        }
        return ZipFile(archive).use(block)
    } finally {
        archive.delete()
    }
}
