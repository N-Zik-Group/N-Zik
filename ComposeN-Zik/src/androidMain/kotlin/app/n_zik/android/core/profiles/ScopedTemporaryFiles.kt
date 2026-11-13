package app.n_zik.android.core.profiles

import android.content.Context
import app.n_zik.android.utils.getActiveProfile
import java.io.File
import java.util.concurrent.TimeUnit

internal object ScopedTemporaryFiles {
    const val IMAGE_EDITOR = "image_edit"
    const val REWIND_CAPTURE = "shared_rewind"
    const val PRIVATE_ARTWORK = "nzik-profile-artwork"
    const val PUBLIC_ARTWORK = "nzik-installation-artwork"

    private val artworkName = Regex("artwork_[-0-9]+\\.jpg")
    private val maximumAge = TimeUnit.HOURS.toMillis(24)

    fun activeOwner(context: Context): ProfileStorageIdentity =
        checkNotNull(profileStorageIdentity(context, getActiveProfile(context))) { "The profile storage is no longer available" }

    fun directory(context: Context, namespace: String, owner: ProfileStorageIdentity): File =
        context.cacheDir.canonicalFile.resolve("$namespace/${owner.id}/${owner.generation}")

    fun publicArtwork(context: Context): File =
        context.cacheDir.canonicalFile.resolve(PUBLIC_ARTWORK)

    fun isExpiredArtwork(file: File, now: Long = System.currentTimeMillis()): Boolean =
        artworkName.matches(file.name) && file.lastModified() in 1L until now - maximumAge

    fun requireWritableDirectory(directory: File): File {
        check(directory.canonicalFile == directory.absoluteFile) { "Unexpected temporary storage path" }
        check(directory.isDirectory || directory.mkdirs()) { "Could not create temporary storage" }
        return directory
    }
}
