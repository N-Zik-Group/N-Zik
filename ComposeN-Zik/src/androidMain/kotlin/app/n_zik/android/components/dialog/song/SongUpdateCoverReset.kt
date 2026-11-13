package app.n_zik.android.components.dialog.song

import java.io.File
import java.io.IOException
import android.content.Context
import android.net.Uri
import app.n_zik.android.cleanPrefix
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.components.image.OWNED_COVER_EXTENSION_PATTERN
import app.n_zik.android.core.profiles.coversDir
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.isLegacyStorageShared
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
import timber.log.Timber

/** Removes only the active profile's song-owned cover after a provider cover refresh. */
internal object SongUpdateCoverReset {
    fun reset(context: Context, songId: String, previousUrl: String?, requestProfile: String) {
        if (getActiveProfile(context) != requestProfile) return
        if (isLegacyStorageShared(context, requestProfile, ProfileDataItem.IMAGES)) {
            throw IOException("The historical shared cover directory is still protected")
        }
        val directory = coversDir(context, requestProfile).canonicalFile
        val previous = previousUrl?.let(::cleanPrefix)
        val previousPath = previous?.let { Uri.parse(it).path }
        val previousExtension = previousPath?.let { File(it).extension }
        val file = ownedCover(directory, songId, previousExtension ?: "jpg")
        if (file == null) {
            Timber.tag("Database").w("update cover reset refused invalid song id")
            return
        }
        if (file.exists() && !file.delete()) {
            throw IOException("Could not remove song-owned cover")
        }
        val keys = setOfNotNull(previousUrl?.let(::cleanPrefix), file.absolutePath, Uri.fromFile(file).toString())
        keys.forEach { key ->
            ImageCacheFactory.NetworkQuality.entries.forEach { quality ->
                ImageCacheFactory.clearCacheForKeyOrThrow(key, quality)
            }
        }
        ArtworkRevisions.invalidate()
    }

    // Never treat a URL or an arbitrary stored local path as a deletion target.
    internal fun ownedCover(directory: File, songId: String, extension: String = "jpg"): File? {
        if (songId.isBlank() || songId.any { it == '/' || it == '\\' }) return null
        val suffix = extension.takeIf { it in OWNED_COVER_EXTENSION_PATTERN.split('|') } ?: "jpg"
        val parent = directory.canonicalFile
        val filename = "cover_$songId.$suffix"
        return File(parent, filename).canonicalFile.takeIf { it.parentFile == parent && it.name == filename }
    }
}
