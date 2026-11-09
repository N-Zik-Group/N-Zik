package app.n_zik.android.components.dialog.song

import android.net.Uri
import app.it.fast4x.rimusic.models.Song
import java.io.File

/** Immutable identity and metadata captured when the user confirms Save. */
internal data class MetadataSaveRequest(
    val song: Song,
    val profileId: String,
    val generation: Int,
    val path: String,
    val fields: List<EditMetadataDialog.EditableField>,
    val coverBytes: ByteArray?,
) {
    fun matches(active: Boolean, currentGeneration: Int, songId: String?, profile: String): Boolean =
        active && generation == currentGeneration && song.id == songId && profileId == profile
}

internal data class PendingMetadataWrite(val request: MetadataSaveRequest, val file: File, val uri: Uri)

/** Taking a pending write transfers cleanup responsibility exclusively to the IO worker. */
internal class PendingMetadataWriteStore {
    private var pending: PendingMetadataWrite? = null

    @Synchronized fun hasPending(): Boolean = pending != null

    @Synchronized fun put(write: PendingMetadataWrite) {
        check(pending == null)
        pending = write
    }

    @Synchronized fun take(): PendingMetadataWrite? = pending.also { pending = null }

    fun discard() { take()?.file?.delete() }
}
