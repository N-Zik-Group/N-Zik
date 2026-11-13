package app.n_zik.android.core.coil

import coil3.disk.DiskCache
import java.io.File
import java.io.IOException
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import timber.log.Timber

/** Uses committed Coil 3 snapshots while the legacy cache is protected from all writes. */
internal class ReadOnlyCoilCache(
    source: File,
    override val maxSize: Long,
) : DiskCache {
    private val root = source.absoluteFile
    override val directory = root.toPathForCoil()
    override val fileSystem = FileSystem.SYSTEM
    private val entries by lazy {
        runCatching { committedEntries() }
            .onFailure { Timber.tag("ReadOnlyCoilCache").w(it, "Could not read the protected image cache journal") }
            .getOrDefault(emptyMap())
    }

    override val size: Long get() = entries.values.sumOf { (metadata, image) -> metadata + image }

    override fun openSnapshot(key: String): DiskCache.Snapshot? {
        val hash = key.encodeUtf8().sha256().hex()
        val (metadataLength, dataLength) = entries[hash] ?: return null
        val metadataFile = root.resolve("$hash.0")
        val dataFile = root.resolve("$hash.1")
        if (!isVerifiedFile(metadataFile, metadataLength) || !isVerifiedFile(dataFile, dataLength)) return null
        return object : DiskCache.Snapshot {
            override val metadata = metadataFile.toPathForCoil()
            override val data = dataFile.toPathForCoil()
            override fun close() = Unit
            override fun closeAndOpenEditor(): DiskCache.Editor? = null
        }
    }

    override fun openEditor(key: String): DiskCache.Editor? = null
    override fun remove(key: String): Boolean = false
    override fun clear() { throw IOException("The shared image cache is still protected") }
    override fun shutdown() = Unit

    fun verifyReadableJournal() {
        val hasJournal = root.resolve("journal").isFile || root.resolve("journal.bkp").isFile
        if (!hasJournal) {
            check(root.listFiles().orEmpty().none { cacheKey.matches(it.name.substringBefore('.')) }) {
                "The historical Coil data has no readable journal"
            }
        }
        committedEntries().forEach { (hash, lengths) ->
            check(isVerifiedFile(root.resolve("$hash.0"), lengths.first) &&
                isVerifiedFile(root.resolve("$hash.1"), lengths.second)
            ) { "A committed historical image is incomplete" }
        }
    }

    private fun committedEntries(): Map<String, Pair<Long, Long>> {
        check(root.canonicalFile == root)
        val journal = root.resolve("journal").takeIf(File::isFile)
            ?: root.resolve("journal.bkp").takeIf(File::isFile)
            ?: return emptyMap()
        check(journal.canonicalFile == journal.absoluteFile && journal.length() <= 16L * 1024 * 1024)
        return journal.bufferedReader().use { reader ->
            check(List(5) { reader.readLine() } == listOf("libcore.io.DiskLruCache", "1", "3", "2", "")) {
                "Unknown Coil cache journal header"
            }
            buildMap {
                reader.lineSequence().forEach { line ->
                    val parts = line.split(' ', limit = 5)
                    val operation = parts.first()
                    val hash = parts.getOrNull(1) ?: return@forEach
                    if (!cacheKey.matches(hash)) return@forEach
                    when (operation) {
                        "CLEAN" -> {
                            val metadata = parts.getOrNull(2)?.toLongOrNull()
                            val data = parts.getOrNull(3)?.toLongOrNull()
                            if (parts.size == 4 && metadata != null && metadata >= 0 && data != null && data >= 0) {
                                put(hash, metadata to data)
                            }
                        }
                        "DIRTY", "REMOVE" -> remove(hash)
                    }
                }
            }
        }
    }

    private fun isVerifiedFile(file: File, length: Long): Boolean =
        file.canonicalFile == file.absoluteFile && file.isFile && file.length() == length

    private fun File.toPathForCoil(): Path = absolutePath.toPath()

    private companion object {
        val cacheKey = Regex("[0-9a-f]{64}")
    }
}
