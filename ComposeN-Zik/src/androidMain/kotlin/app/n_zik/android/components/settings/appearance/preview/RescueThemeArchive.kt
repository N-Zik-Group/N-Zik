package app.n_zik.android.components.settings.appearance.preview

import android.content.Context
import app.n_zik.android.utils.isProfileIdSafe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private const val RescueThemeIndexPath = "rescue-themes.json"
private const val RescueThemeProfileLimit = 128
private const val RescueThemeBytesLimit = 1024L * 1024 * 1024
private const val RescueThemeZipLimit = RescueThemeBytesLimit + 2 * 1024 * 1024

@Serializable
private data class RescueThemeIndex(val schemaVersion: Int, val profiles: List<String>)

private fun rescueThemeCollectionPath(index: Int): String = "profiles/$index.zip"

internal suspend fun writeRescueThemeArchive(
    context: Context,
    output: OutputStream,
    themesByProfile: Map<String, List<SavedTheme>>,
    catalog: ThemeStoreCatalog? = null,
    networkProfiles: Set<String> = emptySet(),
) {
    val export = themesByProfile.toSortedMap().mapValues { (_, themes) ->
        themes.filter { it.theme != null && it.theme.themeId != BundledThemeId }
    }.filterValues { it.isNotEmpty() }
    checkRescueThemeProfiles(export.keys.toList())
    val index = Json.encodeToString(RescueThemeIndex(1, export.keys.toList())).encodeToByteArray()

    ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry(RescueThemeIndexPath))
        zip.write(index)
        zip.closeEntry()
        var totalSize = index.size.toLong()
        for ((position, group) in export.entries.withIndex()) {
            val (profile, themes) = group
            // A nested collection has its own ZIP writer and size limits. Stage it on disk
            // so these potentially large previews never need a second copy in memory.
            val collection = File.createTempFile("rescue-themes-", ".zip", context.cacheDir)
            try {
                collection.outputStream().use {
                    writeThemeCollectionArchive(context, it, themes, catalog, allowNetwork = profile in networkProfiles)
                }
                require(collection.length() in 1..CollectionZipLimit)
                totalSize += collection.length()
                require(totalSize <= RescueThemeBytesLimit) { "Theme backup exceeded its byte limit" }
                zip.putNextEntry(ZipEntry(rescueThemeCollectionPath(position)))
                collection.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            } finally {
                collection.delete()
            }
        }
    }
}

internal fun readRescueThemeArchive(
    context: Context,
    source: InputStream,
    ordinaryTarget: String? = null,
): Map<String, List<ThemeCollectionEntry>> = withThemeCollection(context, source, RescueThemeZipLimit) { zip ->
    if (zip.getEntry(RescueThemeIndexPath) == null) {
        val target = requireNotNull(ordinaryTarget) { "Select one profile for an unscoped theme archive" }
        mapOf(target to readThemeCollectionIndex(zip))
    } else {
        readRescueThemeCollections(zip).mapValues { (_, entry) ->
            zip.getInputStream(entry).use { readThemeCollectionIndex(context, it) }
        }
    }
}

internal fun restoreRescueThemeArchive(
    context: Context,
    source: InputStream,
    libraries: Map<String, ThemeStoreLibrary>,
    selected: Map<String, Set<String>>,
): ThemeCollectionResult = withThemeCollection(context, source, RescueThemeZipLimit) { zip ->
    val selection = selected.filterValues { it.isNotEmpty() }
    require(libraries.keys.containsAll(selection.keys)) { "Theme backup selection contains an unknown profile" }

    val prepared = if (zip.getEntry(RescueThemeIndexPath) == null) {
        val target = requireNotNull(libraries.keys.singleOrNull()) { "Select one profile for an unscoped theme archive" }
        val ids = selection[target].orEmpty()
        mapOf(target to prepareThemeCollectionRestore(context, zip, libraries.getValue(target), ids))
    } else {
        val collections = readRescueThemeCollections(zip)
        require(collections.keys.containsAll(selection.keys)) { "Theme backup selection contains an unknown profile" }
        // Validate every chosen profile before saving anything to profile preferences.
        selection.mapValues { (profile, ids) ->
            zip.getInputStream(collections.getValue(profile)).use {
                prepareThemeCollectionRestore(context, it, libraries.getValue(profile), ids)
            }
        }
    }
    val imported = prepared.entries.sumOf { (profile, themes) ->
        libraries.getValue(profile).restoreImported(themes)
    }
    ThemeCollectionResult(imported, selection.values.sumOf { it.size } - imported)
}

private fun readRescueThemeCollections(zip: ZipFile): Map<String, ZipEntry> {
    require(zip.size() in 2..(RescueThemeProfileLimit + 1))
    val indexEntry = requireNotNull(zip.getEntry(RescueThemeIndexPath))
    require(indexEntry.size in 1..ThemeDocumentLimit.toLong())
    val index = zip.getInputStream(indexEntry).use { input ->
        val bytes = input.readThemeBytes(ThemeDocumentLimit)
        require(bytes.size.toLong() == indexEntry.size)
        Json.decodeFromString<RescueThemeIndex>(bytes.decodeToString(throwOnInvalidSequence = true))
    }
    require(index.schemaVersion == 1)
    checkRescueThemeProfiles(index.profiles)
    val expectedPaths = index.profiles.indices.map(::rescueThemeCollectionPath).toSet() + RescueThemeIndexPath
    val seen = mutableSetOf<String>()
    var totalSize = 0L
    for (entry in zip.entries().asSequence()) {
        require(!entry.isDirectory && entry.name in expectedPaths && seen.add(entry.name))
        require(entry.size in 1..CollectionZipLimit)
        totalSize += entry.size
        require(totalSize <= RescueThemeBytesLimit) { "Theme backup exceeded its byte limit" }
    }
    require(seen == expectedPaths)
    return index.profiles.mapIndexed { position, id ->
        id to requireNotNull(zip.getEntry(rescueThemeCollectionPath(position)))
    }.toMap()
}

private fun checkRescueThemeProfiles(profiles: List<String>) {
    require(profiles.size in 1..RescueThemeProfileLimit)
    require(profiles.distinct().size == profiles.size && profiles.all { id ->
        id.isNotBlank() && id.length <= 256 && isProfileIdSafe(id) && id.none(Char::isISOControl)
    })
}
