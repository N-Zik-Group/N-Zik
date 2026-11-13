package app.n_zik.android.core.profiles

import android.database.sqlite.SQLiteDatabase
import android.system.Os
import android.system.OsConstants
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.DefaultContentMetadata
import app.n_zik.android.core.rescue.ProfileResetSnapshot
import java.io.DataInputStream
import java.io.File
import java.util.NavigableSet
import java.util.TreeSet
import java.util.UUID

/** Reads legacy Media3 spans without opening SimpleCache or SQLite on the source. */
@UnstableApi
internal class ReadOnlyProfileCache private constructor(
    private val uid: Long,
    private val spans: Map<String, NavigableSet<CacheSpan>>,
    private val metadata: Map<String, ContentMetadata>,
) : Cache {
    private val bytes = spans.values.sumOf { entries -> entries.sumOf { it.length } }

    override fun getUid(): Long = uid
    override fun getCacheSpace(): Long = bytes
    override fun getKeys(): Set<String> = spans.keys.toSet()
    override fun getContentMetadata(key: String): ContentMetadata = metadata[key] ?: DefaultContentMetadata.EMPTY
    override fun getCachedSpans(key: String): NavigableSet<CacheSpan> = TreeSet(spans[key].orEmpty())
    override fun addListener(key: String, listener: Cache.Listener): NavigableSet<CacheSpan> = getCachedSpans(key)
    override fun removeListener(key: String, listener: Cache.Listener) = Unit
    override fun release() = Unit

    override fun startReadWrite(key: String, position: Long, length: Long): CacheSpan =
        startReadWriteNonBlocking(key, position, length)

    override fun startReadWriteNonBlocking(key: String, position: Long, length: Long): CacheSpan {
        val entries = spans[key]
        val query = CacheSpan(key, position, 0)
        val preceding = entries?.floor(query)
        if (preceding != null && preceding.length > position - preceding.position) return preceding
        val untilNext = entries?.ceiling(query)?.let { it.position - position }
        val holeLength = when {
            untilNext == null -> length
            length == C.LENGTH_UNSET.toLong() -> untilNext
            else -> minOf(length, untilNext)
        }
        return CacheSpan(key, position, holeLength)
    }

    override fun isCached(key: String, position: Long, length: Long): Boolean =
        getCachedLength(key, position, length) >= length

    override fun getCachedLength(key: String, position: Long, length: Long): Long {
        val remaining = if (length == C.LENGTH_UNSET.toLong()) Long.MAX_VALUE else length
        val first = startReadWriteNonBlocking(key, position, remaining)
        if (!first.isCached) return -first.length
        var end = first.position + first.length
        for (span in spans.getValue(key).tailSet(first, false)) {
            if (span.position > end || end - position >= remaining) break
            end = maxOf(end, span.position + span.length)
        }
        return minOf(remaining, end - position)
    }

    override fun getCachedBytes(key: String, position: Long, length: Long): Long {
        val end = if (length == C.LENGTH_UNSET.toLong()) Long.MAX_VALUE
            else position + minOf(length, Long.MAX_VALUE - position)
        var cursor = position
        var cached = 0L
        while (cursor < end) {
            val block = getCachedLength(key, cursor, end - cursor)
            if (block > 0) cached += block
            cursor += if (block < 0) -block else block
        }
        return cached
    }

    override fun startFile(key: String, position: Long, length: Long): File = throw readOnly()
    override fun commitFile(file: File, length: Long) { throw readOnly() }
    override fun applyContentMetadataMutations(key: String, mutations: ContentMetadataMutations) { throw readOnly() }
    override fun releaseHoleSpan(holeSpan: CacheSpan) = Unit
    override fun removeResource(key: String) = Unit
    override fun removeSpan(span: CacheSpan) = Unit

    private fun readOnly() = Cache.CacheException("Legacy profile storage is read-only while separation is pending")

    private data class Content(val key: String, val metadata: DefaultContentMetadata)

    companion object {
        private val uidName = Regex("([0-9a-fA-F]+)\\.uid")
        private val currentSpanName = Regex("(\\d+)\\.(\\d+)\\.(\\d+)\\.v3\\.exo")
        private val oldSpanName = Regex("(.+)\\.(\\d+)\\.(\\d+)\\.v([12])\\.exo")

        fun empty(): ReadOnlyProfileCache = ReadOnlyProfileCache(Cache.UID_UNSET, emptyMap(), emptyMap())

        fun open(directory: File, database: File, scratchRoot: File): ReadOnlyProfileCache {
            if (!directory.exists()) return empty()
            val root = directory.absoluteFile
            check(root.canonicalFile == root && root.isDirectory) { "Unexpected legacy cache path" }
            val top = checkNotNull(root.listFiles()) { "Cannot read the legacy cache directory" }
            val uid = top.mapNotNull { uidName.matchEntire(it.name)?.groupValues?.get(1)?.toLongOrNull(16) }
                .singleOrNull() ?: Cache.UID_UNSET
            val records = if (uid != Cache.UID_UNSET && database.isFile) {
                readIndex(database, uid, scratchRoot)
            } else emptyMap()
            val spans = mutableMapOf<String, NavigableSet<CacheSpan>>()
            fun add(file: File) {
                if (file.canonicalFile != file.absoluteFile) return
                val current = currentSpanName.matchEntire(file.name)
                val old = if (current == null) oldSpanName.matchEntire(file.name) else null
                val key = when {
                    current != null -> current.groupValues[1].toIntOrNull()?.let(records::get)?.key
                    old?.groupValues?.get(4) == "1" -> old.groupValues[1]
                    old != null -> Util.unescapeFileName(old.groupValues[1])
                    else -> null
                } ?: return
                val fields = checkNotNull(current ?: old).groupValues
                val position = fields[2].toLongOrNull() ?: return
                val touched = fields[3].toLongOrNull() ?: return
                val length = if (File.separatorChar == '\\') {
                    if (!file.isFile) return
                    file.length()
                } else {
                    val stat = Os.lstat(file.path)
                    if (!OsConstants.S_ISREG(stat.st_mode)) return
                    stat.st_size
                }
                if (length <= 0 || position > Long.MAX_VALUE - length) return
                spans.getOrPut(key) { TreeSet() }.add(CacheSpan(key, position, length, touched, file))
            }
            top.sortedBy { it.name }.forEach { child ->
                if (child.isDirectory && child.canonicalFile == child.absoluteFile && child.name.toIntOrNull() != null) {
                    checkNotNull(child.listFiles()).sortedBy { it.name }.forEach(::add)
                } else add(child)
            }
            return ReadOnlyProfileCache(uid, spans, records.values.associate { it.key to it.metadata })
        }

        private fun readIndex(database: File, uid: Long, scratchRoot: File): Map<Int, Content> {
            val scratch = scratchRoot.canonicalFile.resolve(".legacy-index-${UUID.randomUUID()}")
            check(scratch.mkdir()) { "Could not open a private Media3 index snapshot" }
            try {
                val sourceDir = checkNotNull(database.parentFile).canonicalFile
                val staged = scratch.resolve(database.name)
                // SQLite may write SHM during a nominally read-only open, so open a private copy.
                listOf("", "-wal", "-journal").forEach { suffix ->
                    val name = database.name + suffix
                    ProfileResetSnapshot.copyTree(sourceDir.resolve(name), scratch.resolve(name))
                }
                return SQLiteDatabase.openDatabase(staged.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                    val table = "ExoPlayerCacheIndex${uid.toString(16)}"
                    db.rawQuery("SELECT name FROM sqlite_master WHERE name = ?", arrayOf(table)).use query@{
                        if (!it.moveToFirst()) return@query emptyMap()
                        db.rawQuery("SELECT id, key, metadata FROM $table", null).use { cursor ->
                            buildMap {
                                while (cursor.moveToNext()) {
                                    put(cursor.getInt(0), Content(cursor.getString(1), decodeMetadata(cursor.getBlob(2))))
                                }
                            }
                        }
                    }
                }
            } finally {
                ProfileResetSnapshot.deleteVerifiedTree(scratch)
            }
        }

        private fun decodeMetadata(bytes: ByteArray): DefaultContentMetadata =
            DataInputStream(bytes.inputStream()).use { input ->
                val count = input.readInt()
                require(count in 0..1_024)
                val entries = buildMap {
                    repeat(count) {
                        val key = input.readUTF()
                        val length = input.readInt()
                        require(length in 0..input.available())
                        put(key, ByteArray(length).also(input::readFully))
                    }
                }
                require(input.available() == 0)
                DefaultContentMetadata(entries)
            }
    }
}
