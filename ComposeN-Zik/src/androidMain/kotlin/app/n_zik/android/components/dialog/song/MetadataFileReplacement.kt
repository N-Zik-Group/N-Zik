package app.n_zik.android.components.dialog.song

import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/** The destination keeps its original bytes until the fully written sibling replaces it. */
internal fun replaceMetadataFile(
    original: File,
    encoded: File,
    beforeReplace: () -> Unit,
    replace: (File, File) -> Unit = { staged, destination ->
        Os.chmod(staged.path, Os.stat(destination.path).st_mode and 0x1FF)
        Os.rename(staged.path, destination.path)
    },
) {
    val destination = original.canonicalFile
    val parent = requireNotNull(destination.parentFile)
    val staged = File.createTempFile(".nzik-metadata-", ".tmp", parent)
    try {
        staged.outputStream().use { output ->
            encoded.inputStream().use { input -> input.copyTo(output) }
            output.fd.sync()
        }
        beforeReplace()
        replace(staged, destination)
    } finally {
        staged.delete()
    }
}

internal class MetadataRestoreException(
    val recoveryFile: File,
    writeFailure: Exception,
    restoreFailure: Exception,
) : IOException("MediaStore recovery copy retained at ${recoveryFile.absolutePath}", writeFailure) {
    init {
        addSuppressed(restoreFailure)
    }
}

/** The writable stream must be opened without truncating the destination. */
internal fun writeMetadataWithRecovery(
    encoded: File,
    backupDirectory: File,
    backupPrefix: String,
    openOriginal: () -> InputStream,
    openWritable: () -> FileOutputStream,
    syncBackupDirectory: () -> Unit,
    beforeWrite: () -> Unit,
) {
    val backup = File.createTempFile(backupPrefix, ".backup", backupDirectory)
    var retainBackup = false
    try {
        backup.outputStream().use { output ->
            openOriginal().use { input -> input.copyTo(output) }
            output.fd.sync()
        }
        syncBackupDirectory()
        beforeWrite()

        try {
            openWritable().use { output ->
                beforeWrite()
                retainBackup = true
                copyMetadataContents(encoded, output)
            }
            retainBackup = false
        } catch (writeFailure: Exception) {
            if (retainBackup) {
                try {
                    openWritable().use { output -> copyMetadataContents(backup, output) }
                    retainBackup = false
                } catch (restoreFailure: Exception) {
                    throw MetadataRestoreException(backup, writeFailure, restoreFailure)
                }
            }
            throw writeFailure
        }
    } finally {
        if (!retainBackup) backup.delete()
    }
}

private fun copyMetadataContents(source: File, destination: FileOutputStream) {
    source.inputStream().use { input ->
        destination.channel.truncate(0L)
        destination.channel.position(0L)
        input.copyTo(destination)
        destination.fd.sync()
    }
}
