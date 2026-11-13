package app.n_zik.android.core.rescue

import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest

/** A published reset can resume wiping only the files recorded in its verified snapshot. */
internal class ProfileResetSnapshot(private val backup: File) {
    private val parent = checkNotNull(backup.parentFile)
    private val stage = parent.resolve(".reset-${backup.name}")
    private val previous = parent.resolve(".previous-${backup.name}")
    private val pending get() = backup.resolve(PENDING).isFile
    private val rollback = parent.resolve(".restore-${backup.name}")
    private val restored = parent.resolve(".restored-${backup.name}")

    fun reset(paths: Map<String, File>, faceEntries: () -> String, finish: () -> Unit) {
        check(!rollback.exists() && !restored.exists()) { "Finish the pending profile restore before resetting" }
        if (!pending) {
            if (!stage.resolve(PENDING).isFile) {
                deleteVerifiedTree(stage)
                check(stage.mkdirs()) { "Could not prepare reset snapshot" }
                val fingerprints = paths.mapValuesTo(linkedMapOf()) { (key, source) ->
                    val fingerprint = fingerprint(source)
                    val copy = stage.resolve(key)
                    copyTree(source, copy)
                    check(fingerprint(copy) == fingerprint && fingerprint(source) == fingerprint) {
                        "Reset source changed while preparing the snapshot"
                    }
                    fingerprint
                }
                val face = stage.resolve(FACE_ENTRIES)
                writeSynced(face, faceEntries())
                fingerprints[FACE_ENTRIES] = fingerprint(face)
                writeSynced(stage.resolve(MANIFEST), fingerprints.entries.joinToString("\n") { (key, hash) -> "$hash $key" })
                writeSynced(stage.resolve(PENDING), "")
            }
            verify(stage, paths)
            if (backup.exists()) {
                deleteVerifiedTree(previous)
                check(backup.renameTo(previous)) { "Could not preserve the previous reset snapshot" }
            }
            check(stage.renameTo(backup)) { "Could not publish the reset snapshot" }
        }

        val included = verify(backup, paths).keys
        val confirmed = paths.filterKeys { it in included }
        confirmed.forEach { (key, source) -> verifySubset(source, backup.resolve(key)) }
        confirmed.values.forEach { deleteVerifiedTree(it) }
        finish()
        check(backup.resolve(PENDING).delete()) { "Could not finish the profile reset" }
        deleteVerifiedTree(previous)
    }

    fun restore(paths: Map<String, File>, faceEntries: () -> String, finish: (File) -> Unit) {
        check(!pending && !stage.resolve(PENDING).isFile) { "Finish the pending profile reset before restoring" }
        val ready = rollback.resolve(".restore-ready")
        if (restored.exists()) {
            if (!backup.exists()) {
                verify(rollback, paths, allowSubset = true)
                check(rollback.renameTo(backup)) { "Could not publish the restore rollback snapshot" }
            }
            deleteVerifiedTree(restored)
            return
        }
        val included = verify(backup, paths, allowSubset = true).keys
        val confirmed = paths.filterKeys { it in included }
        if (!ready.isFile) {
            ProfileResetSnapshot(rollback).reset(confirmed, faceEntries) { writeSynced(ready, "") }
        }
        confirmed.forEach { (key, target) ->
            val source = backup.resolve(key)
            verifySubset(target, source)
            val fingerprint = fingerprint(source)
            if (fingerprint(target) != fingerprint) {
                val stage = checkNotNull(target.parentFile).resolve(".nzik-rescue-restore-${target.name}")
                deleteVerifiedTree(stage)
                copyTree(source, stage)
                check(fingerprint(stage) == fingerprint) { "Restore verification failed" }
                deleteVerifiedTree(target)
                check(!stage.exists() || stage.renameTo(target)) { "Could not publish the restored file" }
            }
        }
        finish(backup.resolve(FACE_ENTRIES))
        check(!rollback.resolve(PENDING).exists() || rollback.resolve(PENDING).delete()) {
            "Could not complete the restore rollback snapshot"
        }
        check(backup.renameTo(restored)) { "Could not retire the original snapshot" }
        check(rollback.renameTo(backup)) { "Could not publish the restore rollback snapshot" }
        deleteVerifiedTree(restored)
    }

    companion object {
        private const val MANIFEST = ".verified-reset"
        private const val PENDING = ".reset-pending"
        private const val ABSENT = "-"
        private const val FACE_ENTRIES = "face_entries.txt"

        fun isVerifiedSnapshot(backup: File): Boolean = backup.resolve(MANIFEST).isFile

        fun hasInterruptedRestore(backup: File): Boolean =
            backup.parentFile?.resolve(".restored-${backup.name}")?.exists() == true

        fun hasPendingOperation(directory: File): Boolean =
            directory.listFiles().orEmpty().any { entry ->
                entry.resolve(PENDING).isFile || entry.name.startsWith(".restored-") ||
                    entry.resolve(".restore-ready").isFile && entry.name.startsWith(".restore-")
            }

        fun verify(backup: File, paths: Map<String, File>, allowSubset: Boolean = false): Map<String, String> {
            val records = backup.resolve(MANIFEST).readLines().associate { line ->
                line.substringAfter(' ') to line.substringBefore(' ')
            }
            val expected = paths.keys + FACE_ENTRIES
            check(FACE_ENTRIES in records &&
                (allowSubset || (expected - records.keys).all { it == "coil" || it == "coil-private" }) &&
                records.keys.all { it in expected }
            ) { "Reset storage layout changed" }
            check(backup.canonicalFile == backup.absoluteFile) { "Unexpected snapshot path" }
            records.forEach { (key, expected) ->
                check(fingerprint(backup.resolve(key)) == expected) { "Reset snapshot verification failed" }
            }
            return records
        }

        private fun writeSynced(file: File, text: String) {
            FileOutputStream(file).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
        }

        fun copyTree(source: File, destination: File) {
            checkPath(source)
            checkPath(destination)
            if (!source.exists()) return
            if (source.isDirectory) {
                check(destination.mkdirs()) { "Could not copy reset directory" }
                children(source).forEach { child -> copyTree(child, destination.resolve(child.name)) }
                return
            }
            check(source.isFile) { "Unexpected reset file type" }
            destination.parentFile?.mkdirs()
            source.inputStream().use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
        }

        fun fingerprint(file: File): String {
            checkPath(file)
            if (!file.exists()) return ABSENT
            if (file.isDirectory) {
                return fingerprintDirectoryEntries(children(file).associate { it.name to fingerprint(it) })
            }
            val digest = MessageDigest.getInstance("SHA-256")
            check(file.isFile) { "Unexpected reset file type" }
            digest.update(2)
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        /** Uses the same directory fingerprint for an exact, previously confirmed set of children. */
        fun fingerprintDirectoryEntries(entries: Map<String, String>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(1)
            for ((entry, fingerprint) in entries.toSortedMap()) {
                val name = entry.toByteArray(Charsets.UTF_8)
                digest.update("${name.size}:".toByteArray())
                digest.update(name)
                digest.update(fingerprint.toByteArray())
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun verifySubset(source: File, snapshot: File) {
            checkPath(source)
            if (!source.exists()) return
            if (source.isDirectory) {
                check(snapshot.isDirectory) { "Unexpected reset directory" }
                children(source).forEach { child -> verifySubset(child, snapshot.resolve(child.name)) }
            } else {
                check(fingerprint(source) == fingerprint(snapshot)) { "Reset source changed after publication" }
            }
        }

        private fun children(directory: File): List<File> =
            checkNotNull(directory.listFiles()) { "Could not enumerate reset files" }.sortedBy { it.name }

        fun deleteVerifiedTree(file: File): Int {
            checkPath(file)
            if (!file.exists()) return 0
            val childrenDeleted = if (file.isDirectory) children(file).sumOf(::deleteVerifiedTree) else 0
            check(file.delete()) { "Could not remove the reset file" }
            return childrenDeleted + 1
        }

        fun validateTree(file: File) {
            checkPath(file)
            if (file.isDirectory) children(file).forEach(::validateTree)
        }

        private fun checkPath(file: File) {
            check(file.canonicalFile == file.absoluteFile) { "Unexpected reset file path" }
            if (File.separatorChar == '\\') {
                check(!Files.isSymbolicLink(file.toPath())) { "Unexpected reset symbolic link" }
            } else if (Build.VERSION.SDK_INT > 0) {
                val stat = try {
                    Os.lstat(file.path)
                } catch (error: ErrnoException) {
                    if (error.errno == OsConstants.ENOENT) return
                    throw error
                }
                check(OsConstants.S_ISDIR(stat.st_mode) ||
                    OsConstants.S_ISREG(stat.st_mode) && stat.st_nlink == 1L
                ) { "Unexpected reset file type or linked file" }
            }
        }
    }
}
