package app.n_zik.android.components.settings.appearance.preview

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal fun writeThemeArchive(output: OutputStream, files: Map<String, ByteArray>) {
    ZipOutputStream(output).use { zip ->
        files.forEach { (path, bytes) ->
            zip.putNextEntry(ZipEntry(path))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
}

internal fun readThemeArchive(context: Context, source: InputStream): SavedTheme {
    val archive = File.createTempFile("theme-", ".zip", context.cacheDir)
    try {
        archive.outputStream().use { output ->
            val buffer = ByteArray(32 * 1024)
            var size = 0L
            while (true) {
                val count = source.read(buffer)
                if (count == -1) break
                size += count
                require(size <= 36L * SubmissionPreviewLimit + 2 * 1024 * 1024)
                output.write(buffer, 0, count)
            }
        }
        return ZipFile(archive).use { zip ->
            fun read(path: String, limit: Int) =
                zip.getInputStream(checkNotNull(zip.getEntry(path))).use { it.readThemeBytes(limit) }
            val manifest = read("manifest.json", ThemeMetadataLimit)
            val theme = parseStoreTheme(manifest.decodeToString(throwOnInvalidSequence = true), allowLocalAuthor = true)
            val expected = themeArchivePaths(theme)
            require(zip.size() == expected.size && zip.entries().asSequence().all { !it.isDirectory && it.name in expected })
            val document = validateThemeArchiveDocuments(theme, ::read)
            cacheThemeArchivePreviews(context, theme) { read(it, SubmissionPreviewLimit) }
            // A local package must never overwrite an online download with the same ID.
            val localId = localThemeArchiveId(manifest)
            SavedTheme(theme.title, document, theme.copy(themeId = localId), imported = true)
        }
    } finally {
        archive.delete()
    }
}

internal fun localThemeArchiveId(manifest: ByteArray): String =
    UUID.nameUUIDFromBytes(("nzik-local:" + manifest.sha256()).encodeToByteArray()).toString()

internal fun themeArchivePaths(theme: StoreTheme): Set<String> {
    val slots = theme.previews.map { it.path.substringAfterLast('/').substringBeforeLast('.') }
    require(slots.distinct().size == slots.size && slots.containsAll(requiredPreviews))
    return theme.previews.map { it.path }.toSet() + setOf("manifest.json", "appearance.json", "reviews.json")
}

internal fun validateThemeArchiveDocuments(theme: StoreTheme, read: (String, Int) -> ByteArray): String {
    val appearance = read(theme.appearance.path, ThemeDocumentLimit)
    require(appearance.sha256() == theme.appearance.sha256)
    val document = appearance.decodeToString(throwOnInvalidSequence = true)
    parseThemeV1(document)
    parseStoreReviews(read("reviews.json", ThemeMetadataLimit).decodeToString(throwOnInvalidSequence = true))
    return document
}

internal fun themeArchivePreviewFile(context: Context, preview: StorePreview): File =
    File(File(context.filesDir, "theme-store-catalog"), "${preview.sha256}.${preview.path.substringAfterLast('.')}")

internal fun validateThemeArchivePreview(preview: StorePreview, bytes: ByteArray) {
    require(bytes.size <= SubmissionPreviewLimit && bytes.sha256() == preview.sha256)
    val extension = preview.path.substringAfterLast('.')
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    require(bounds.outMimeType == when (extension) {
        "jpg" -> "image/jpeg"
        else -> "image/$extension"
    })
}

internal fun cacheThemeArchivePreviews(context: Context, theme: StoreTheme, read: (String) -> ByteArray) {
    for (preview in theme.previews) {
        val bytes = read(preview.path)
        validateThemeArchivePreview(preview, bytes)
        val file = themeArchivePreviewFile(context, preview)
        val directory = checkNotNull(file.parentFile)
        check(directory.isDirectory || directory.mkdirs() || directory.isDirectory)
        writeThemeFile(AtomicFile(file), bytes)
    }
}
