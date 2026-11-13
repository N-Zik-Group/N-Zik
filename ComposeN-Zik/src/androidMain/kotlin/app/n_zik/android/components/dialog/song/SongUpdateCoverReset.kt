package app.n_zik.android.components.dialog.song

import java.io.File
import java.io.IOException
import android.content.Context
import android.net.Uri
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.core.coil.ImageCacheFactory
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
        val file = ownedCover(directory, songId)
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
    internal fun ownedCover(directory: File, songId: String): File? {
        if (songId.isBlank() || songId.any { it == '/' || it == '\\' }) return null
        val parent = directory.canonicalFile
        val filename = "cover_$songId.jpg"
        return File(parent, filename).canonicalFile.takeIf { it.parentFile == parent && it.name == filename }
    }
}
