package app.n_zik.android.download.utils

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.system.Os
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.VersionTable
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import app.n_zik.android.core.backup.DatabaseRestoreJournal
import app.n_zik.android.core.profiles.LegacyMediaAccess
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.isProcessStorageProfile
import app.n_zik.android.core.profiles.resolveProfileDataDirNames
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.core.profiles.profileSharedItems
import app.n_zik.android.core.rescue.ProfileResetSnapshot
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

/** Cleans a confirmed closed Media3 category while preserving the other category's index. */
@UnstableApi
internal object InactiveDownloadCleanup {
    private const val DIRECTORY = ".profile-download-cleanup"
    private const val PENDING = "pending"
    private const val CLEAN_INDEX = "clean.db"
    private const val ABSENT = "-"
    private val sideFiles = listOf("", "-wal", "-shm", "-journal")
    private val uidName = Regex("([0-9a-fA-F]+)\\.uid")

    enum class Kind(val item: ProfileDataItem) {
        DOWNLOADS(ProfileDataItem.DOWNLOADS),
        STREAMING(ProfileDataItem.MEDIA_CACHE),
    }

    data class Root(val path: File, val device: String, val inode: String, val hash: String)

    data class Target(
        val identity: ProfileStorageIdentity,
        val roots: List<Root>,
        val index: File,
        val indexHashes: List<String>,
        val displayedFiles: List<String>,
        val kind: Kind,
    )

    private data class Operation(val id: UUID, val target: Target, val cleanHash: String?)

    /** Metadata-only probe for Settings. An empty UID marker or shared index does not make a cache nonempty. */
    fun hasData(context: Context, identity: ProfileStorageIdentity, kind: Kind): Boolean {
        if (profileStorageIdentity(context, identity.id) != identity) return false
        val names = resolveProfileDataDirNames(identity.id, context.profileSharedItems(identity.id))
        val name = if (kind == Kind.DOWNLOADS) names.downloadsDir else names.mediaCacheDir
        fun File.isSpan(): Boolean = extension == "exo" && isFile && canonicalFile == absoluteFile
        if (baseRoots(context).any { base ->
            val root = base.resolve(name)
            root.canonicalFile == root && root.listFiles().orEmpty().any { entry ->
                entry.isSpan() || (entry.name.toIntOrNull() != null && entry.canonicalFile == entry &&
                    entry.listFiles().orEmpty().any { it.isSpan() })
            }
        }) return true
        if (kind != Kind.DOWNLOADS) return false
        val index = context.getDatabasePath(names.downloadsIndexDb).let { file ->
            checkNotNull(file.parentFile).canonicalFile.resolve(file.name)
        }
        if (!index.isFile || index.canonicalFile != index) return false

        // An attached live WAL may create SHM even on a nominally read-only open. Query a private copy in that case.
        val recoveryFiles = indexFiles(index).filter { it.name.endsWith("-wal") || it.name.endsWith("-journal") }
        val stage = if (recoveryFiles.any(File::exists)) {
            context.cacheDir.canonicalFile.resolve(".download-probe-${UUID.randomUUID()}").apply { check(mkdir()) }
        } else null
        return try {
            val source = if (stage == null) index else {
                val copy = stage.resolve("index.db")
                (listOf(index) + recoveryFiles).forEach { file ->
                    ProfileResetSnapshot.copyTree(file, stage.resolve(copy.name + file.name.removePrefix(index.name)))
                }
                copy
            }
            SQLiteDatabase.openDatabase(
                source.path, null, if (stage == null) SQLiteDatabase.OPEN_READONLY else SQLiteDatabase.OPEN_READWRITE,
            ).use { db ->
                val exists = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='ExoPlayerDownloads'", null)
                    .use { it.moveToFirst() }
                exists && db.rawQuery("SELECT 1 FROM ExoPlayerDownloads LIMIT 1", null).use { it.moveToFirst() }
            }
        } finally {
            stage?.let(ProfileResetSnapshot::deleteVerifiedTree)
        }
    }

    fun capture(
        context: Context,
        identity: ProfileStorageIdentity,
        kind: Kind = Kind.DOWNLOADS,
        allowSelected: Boolean = false,
    ): Target? {
        if (identity != profileStorageIdentity(context, identity.id) ||
            (!allowSelected && isProcessStorageProfile(context, identity.id)) ||
            LegacyMediaAccess.isProtected(context, identity.id, kind.item)
        ) return null
        val roots = mediaRoots(context, identity.id, kind).map(::snapshotRoot)
        val index = indexFile(context, identity.id)
        val hashes = indexFiles(index).map(ProfileResetSnapshot::fingerprint)
        val files = roots.flatMap { root ->
            if (root.hash == ABSENT) emptyList()
            else root.path.walkTopDown().filter(File::isFile).map {
                "${root.path.parentFile.name}/${it.relativeTo(root.path).invariantSeparatorsPath}"
            }.toList()
        } + indexFiles(index).zip(hashes).mapNotNull { (file, hash) -> file.name.takeUnless { hash == ABSENT } }
        return Target(identity, roots, index, hashes, files.sorted(), kind)
    }

    fun confirmationFingerprint(target: Target): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        fun field(value: String) {
            val bytes = value.encodeToByteArray()
            digest.update("${bytes.size}:".encodeToByteArray())
            digest.update(bytes)
        }
        field(target.identity.id)
        field(target.identity.generation)
        field(target.kind.name)
        target.roots.forEach {
            field(it.path.path)
            field(it.device)
            field(it.inode)
            field(it.hash)
        }
        field(target.index.path)
        target.indexHashes.forEach(::field)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun hasPendingMutation(context: Context): Boolean = directory(context).resolve(PENDING).exists()

    /** Returns false if the store changed after confirmation. */
    fun execute(
        context: Context,
        confirmed: Target,
        allowSelected: Boolean = false,
        afterIndexPublished: () -> Unit = {},
    ): Boolean =
        DatabaseRestoreJournal.withProfileStorageMutation(context) {
            val current = capture(context, confirmed.identity, confirmed.kind, allowSelected)
            if (current != confirmed) return@withProfileStorageMutation false
            val directory = directory(context)
            check(!directory.resolve(PENDING).exists()) { "Another download cleanup is pending" }
            ProfileResetSnapshot.deleteVerifiedTree(directory)
            check(directory.mkdir())
            val clean = directory.resolve(CLEAN_INDEX)
            val replaceIndex = prepareCleanIndex(context, confirmed, clean)
            if (capture(context, confirmed.identity, confirmed.kind, allowSelected) != confirmed) {
                ProfileResetSnapshot.deleteVerifiedTree(directory)
                return@withProfileStorageMutation false
            }
            val operation = Operation(UUID.randomUUID(), confirmed, if (replaceIndex) ProfileResetSnapshot.fingerprint(clean) else null)
            writeOperation(directory, operation)
            apply(context, directory, operation, afterIndexPublished)
            true
        }

    /** Runs under the library startup lock, before any profile cache can open. */
    fun resume(context: Context) {
        val directory = directory(context)
        val pending = directory.resolve(PENDING)
        if (!pending.isFile) return
        ProfileResetSnapshot.fingerprint(pending)
        val operation = DataInputStream(pending.inputStream().buffered()).use { input ->
            val id = UUID.fromString(input.readUTF())
            val identity = ProfileStorageIdentity(input.readUTF(), input.readUTF())
            val kind = Kind.valueOf(input.readUTF())
            val roots = mediaRoots(context, identity.id, kind)
            check(input.readInt() == roots.size) { "The number of download roots changed" }
            val confirmed = roots.map { path ->
                check(path.path == input.readUTF()) { "The download root changed" }
                Root(path, input.readUTF(), input.readUTF(), input.readUTF())
            }
            val index = indexFile(context, identity.id)
            check(index.path == input.readUTF()) { "The download index path changed" }
            val before = List(sideFiles.size) { input.readUTF() }
            Operation(id, Target(identity, confirmed, index, before, emptyList(), kind), input.readUTF().ifEmpty { null })
        }
        apply(context, directory, operation)
    }

    private fun apply(context: Context, directory: File, operation: Operation, afterIndexPublished: () -> Unit = {}) {
        val target = operation.target
        check(target.identity == profileStorageIdentity(context, target.identity.id) &&
            !LegacyMediaAccess.isProtected(context, target.identity.id, target.kind.item)
        ) { "The confirmed download profile changed" }
        target.roots.forEach { root ->
            val current = snapshotRoot(root.path)
            check(current.hash == ABSENT || current == root) { "A download root changed after confirmation" }
        }
        val indexFiles = indexFiles(target.index)
        val actualHashes = indexFiles.map(ProfileResetSnapshot::fingerprint)
        val cleanedHash = operation.cleanHash
        if (cleanedHash == null) {
            check(actualHashes == target.indexHashes) { "The Media3 index changed after confirmation" }
        } else {
            val cleanedHashes = listOf(cleanedHash) + List(sideFiles.size - 1) { ABSENT }
            if (actualHashes != cleanedHashes) {
                check(actualHashes == target.indexHashes) { "The Media3 index changed after confirmation" }
                val clean = directory.resolve(CLEAN_INDEX)
                check(ProfileResetSnapshot.fingerprint(clean) == cleanedHash && clean.isFile)
                val replacement = checkNotNull(target.index.parentFile).resolve(".download-cleanup-${operation.id}.db")
                ProfileResetSnapshot.deleteVerifiedTree(replacement)
                ProfileResetSnapshot.copyTree(clean, replacement)
                check(ProfileResetSnapshot.fingerprint(replacement) == cleanedHash)
                DatabaseRestoreJournal.install(
                    context.filesDir,
                    target.index,
                    indexFiles.associateWith { if (it == target.index) replacement else null },
                )
                check(indexFiles.map(ProfileResetSnapshot::fingerprint) == cleanedHashes)
            }
        }
        afterIndexPublished()

        target.roots.forEachIndexed { index, root ->
            val retired = checkNotNull(root.path.parentFile).resolve(".download-cleanup-${operation.id}-$index")
            val current = snapshotRoot(root.path)
            if (current.hash != ABSENT) {
                check(current == root && !retired.exists()) { "A download root changed after confirmation" }
                check(root.path.renameTo(retired)) { "Could not retire the confirmed download files" }
            }
            ProfileResetSnapshot.deleteVerifiedTree(retired)
        }
        check(directory.resolve(PENDING).delete()) { "Could not finish the download cleanup" }
        ProfileResetSnapshot.deleteVerifiedTree(directory)
    }

    private fun prepareCleanIndex(context: Context, target: Target, clean: File): Boolean {
        if (!target.index.isFile) {
            check(target.indexHashes.all { it == ABSENT }) { "The Media3 index has unverified side files" }
            return false
        }
        val selectedUids = target.roots.flatMap { root -> cacheUids(root.path) }.toSet()
        if (target.kind == Kind.STREAMING && selectedUids.isEmpty()) return false
        val otherKind = if (target.kind == Kind.DOWNLOADS) Kind.STREAMING else Kind.DOWNLOADS
        val otherUids = mediaRoots(context, target.identity.id, otherKind).flatMap(::cacheUids).toSet()
        check(selectedUids.intersect(otherUids).isEmpty()) { "The two Media3 caches use the same UID" }
        indexFiles(target.index).zip(indexFiles(clean)).forEach { (source, destination) ->
            if (!source.name.endsWith("-shm")) ProfileResetSnapshot.copyTree(source, destination)
        }

        SQLiteDatabase.openDatabase(clean.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA quick_check", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext())
            }
            val exists = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='ExoPlayerDownloads'", null)
                .use { it.moveToFirst() }
            db.beginTransaction()
            try {
                if (exists && target.kind == Kind.DOWNLOADS) {
                    check(VersionTable.getVersion(db, VersionTable.FEATURE_OFFLINE, "") == 3) {
                        "The download index format is unverified"
                    }
                    val provider = object : DatabaseProvider {
                        override fun getReadableDatabase(): SQLiteDatabase = db
                        override fun getWritableDatabase(): SQLiteDatabase = db
                    }
                    val index = DefaultDownloadIndex(provider)
                    val ids = index.getDownloads().use { rows ->
                        buildList { while (rows.moveToNext()) add(rows.download.request.id) }
                    }
                    ids.forEach(index::removeDownload)
                    index.getDownloads().use { check(!it.moveToNext()) }
                }
                selectedUids.forEach { uid ->
                    db.execSQL("DROP TABLE IF EXISTS ExoPlayerCacheIndex$uid")
                    db.execSQL("DROP TABLE IF EXISTS ExoPlayerCacheFileMetadata$uid")
                    VersionTable.removeVersion(db, VersionTable.FEATURE_CACHE_CONTENT_METADATA, uid)
                    VersionTable.removeVersion(db, VersionTable.FEATURE_CACHE_FILE_METADATA, uid)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            db.rawQuery("PRAGMA journal_mode=DELETE", null).use {
                check(it.moveToFirst() && it.getString(0).equals("delete", ignoreCase = true))
            }
        }
        check(indexFiles(clean).drop(1).none(File::exists)) { "The cleaned index is incomplete" }
        return true
    }

    private fun writeOperation(directory: File, operation: Operation) {
        val temporary = directory.resolve("pending.stage")
        FileOutputStream(temporary).use { stream ->
            val data = DataOutputStream(stream.buffered())
            data.writeUTF(operation.id.toString())
            val target = operation.target
            data.writeUTF(target.identity.id)
            data.writeUTF(target.identity.generation)
            data.writeUTF(target.kind.name)
            data.writeInt(target.roots.size)
            target.roots.forEach { root ->
                data.writeUTF(root.path.path)
                data.writeUTF(root.device)
                data.writeUTF(root.inode)
                data.writeUTF(root.hash)
            }
            data.writeUTF(target.index.path)
            target.indexHashes.forEach(data::writeUTF)
            data.writeUTF(operation.cleanHash.orEmpty())
            data.flush()
            stream.fd.sync()
        }
        check(temporary.renameTo(directory.resolve(PENDING)))
    }

    private fun directory(context: Context): File = context.filesDir.canonicalFile.resolve(DIRECTORY)

    private fun baseRoots(context: Context): List<File> =
        listOf(context.cacheDir.canonicalFile, context.filesDir.canonicalFile).distinct()

    private fun mediaRoots(context: Context, id: String, kind: Kind): List<File> {
        val names = resolveProfileDataDirNames(id, emptySet())
        val name = if (kind == Kind.DOWNLOADS) names.downloadsDir else names.mediaCacheDir
        return baseRoots(context).map { it.resolve(name) }
    }

    private fun indexFile(context: Context, id: String): File {
        val file = context.getDatabasePath(resolveProfileDataDirNames(id, emptySet()).downloadsIndexDb)
        return checkNotNull(file.parentFile).canonicalFile.resolve(file.name)
    }

    private fun indexFiles(index: File): List<File> = sideFiles.map { File(index.path + it) }

    private fun cacheUids(directory: File): List<String> {
        if (!directory.exists()) return emptyList()
        check(directory.canonicalFile == directory.absoluteFile && directory.isDirectory)
        return checkNotNull(directory.listFiles()).mapNotNull { uidName.matchEntire(it.name)?.groupValues?.get(1) }
            .map { it.lowercase().trimStart('0').ifEmpty { "0" } }
            .also { check(it.size <= 1) { "The cache has more than one UID" } }
    }

    private fun snapshotRoot(path: File): Root {
        val hash = ProfileResetSnapshot.fingerprint(path)
        if (hash == ABSENT) return Root(path, "", "", hash)
        check(path.isDirectory) { "The download root is not a directory" }
        if (File.separatorChar == '\\') {
            val inode = Files.readAttributes(path.toPath(), BasicFileAttributes::class.java).fileKey()?.toString()
                ?: path.canonicalPath
            return Root(path, Files.getFileStore(path.toPath()).name(), inode, hash)
        }
        val stat = Os.lstat(path.path)
        val parent = Os.lstat(checkNotNull(path.parentFile).path)
        check(stat.st_dev == parent.st_dev) { "The download root volume changed" }
        return Root(path, stat.st_dev.toString(), stat.st_ino.toString(), hash)
    }
}
