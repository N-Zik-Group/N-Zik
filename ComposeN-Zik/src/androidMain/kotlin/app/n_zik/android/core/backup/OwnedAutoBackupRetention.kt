package app.n_zik.android.core.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.profileStorageIdentity
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/** Only complete, recorded documents from this installation can enter automatic retention. */
internal object OwnedAutoBackupRetention {
    data class Output(val uri: Uri, val kind: AutoBackupKind)

    private data class Receipt(val id: String, val kind: AutoBackupKind, val hash: String, val finishedAt: Long)

    @Synchronized
    fun recordAndPrune(context: Context, owner: ProfileStorageIdentity, tree: Uri, outputs: List<Output>, maximum: Int) {
        if (outputs.isEmpty() || profileStorageIdentity(context, owner.id) != owner) return
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val finishedAt = System.currentTimeMillis()
        val receipts = outputs.map { output ->
            check(output.uri.authority == tree.authority && DocumentsContract.getTreeDocumentId(output.uri) == treeId)
            val hash = checkNotNull(fingerprint(context, output.uri)) { "The completed backup is unreadable" }
            Receipt(DocumentsContract.getDocumentId(output.uri), output.kind, hash, finishedAt)
        }
        val treeKey = MessageDigest.getInstance("SHA-256").digest(tree.toString().encodeToByteArray()).hex()
        val folder = context.noBackupFilesDir.canonicalFile.resolve("owned-auto-backups/${owner.id}/${owner.generation}/$treeKey")
        check(folder.canonicalFile == folder && (folder.isDirectory || folder.mkdirs()))
        receipts.forEach { receipt -> write(folder, receipt) }
        if (maximum < 0) return

        val currentIds = receipts.map { it.id }.toSet()
        val known = folder.listFiles().orEmpty().mapNotNull { file ->
            if (file.extension != "receipt" || !file.isFile || file.canonicalFile != file) return@mapNotNull null
            runCatching { file to read(file) }.getOrNull()
        }.groupBy { (_, receipt) -> receipt.kind }
        known.values.forEach { group ->
            group.sortedWith(
                compareByDescending<Pair<File, Receipt>> { it.second.id in currentIds }
                    .thenByDescending { it.second.finishedAt }
                    .thenBy { it.first.name },
            ).distinctBy { it.second.id }.drop(maximum.coerceAtLeast(1)).forEach { (file, receipt) ->
                    if (profileStorageIdentity(context, owner.id) != owner) return
                    if (receipt.id in currentIds) return@forEach
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, receipt.id)
                    val current = runCatching { fingerprint(context, uri) }.getOrNull()
                    if (current != null && current == receipt.hash && DocumentsContract.deleteDocument(context.contentResolver, uri)) {
                        check(file.delete())
                    }
                }
        }
    }

    private fun fingerprint(context: Context, uri: Uri): String? = context.contentResolver.openInputStream(uri)?.use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytes = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
            bytes += count
        }
        digest.digest().hex().takeIf { bytes > 0 }
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    private fun write(folder: File, receipt: Receipt) {
        val stage = File.createTempFile(".backup-", ".stage", folder)
        try {
            FileOutputStream(stage).use { stream ->
                val output = DataOutputStream(stream.buffered())
                output.writeUTF(receipt.id)
                output.writeUTF(receipt.kind.name)
                output.writeUTF(receipt.hash)
                output.writeLong(receipt.finishedAt)
                output.flush()
                stream.fd.sync()
            }
            check(stage.renameTo(folder.resolve("${UUID.randomUUID()}.receipt")))
        } finally {
            stage.delete()
        }
    }

    private fun read(file: File): Receipt = DataInputStream(file.inputStream().buffered()).use { input ->
        check(file.length() <= 32 * 1024)
        val receipt = Receipt(input.readUTF(), AutoBackupKind.valueOf(input.readUTF()), input.readUTF(), input.readLong())
        check(input.read() == -1 && receipt.id.isNotEmpty() &&
            receipt.hash.length == 64 && receipt.hash.all { it in '0'..'9' || it in 'a'..'f' })
        receipt
    }
}
