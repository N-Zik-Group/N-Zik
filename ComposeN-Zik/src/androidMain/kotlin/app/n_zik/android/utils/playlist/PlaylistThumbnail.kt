package app.n_zik.android.utils.playlist

import android.content.Context
import android.net.Uri
import app.it.fast4x.rimusic.utils.saveImageToInternalStorage
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext

/**
 * Saves the picked playlist thumbnail into the app's `thumbnail` directory on
 * [NzikDispatchers.DATA] (issue #606 M2).
 *
 * `saveImageToInternalStorage` decodes, scales, compresses and writes the image, and it used to
 * run synchronously in the `ActivityResultLauncher` callback, i.e. on Main. The [save] seam only
 * exists so the dispatch can be unit-tested without touching the file system.
 *
 * @return the saved image [Uri], or `null` when the image could not be saved
 */
internal suspend fun savePlaylistThumbnail(
    context: Context,
    uri: Uri,
    thumbnailName: String,
    save: (Context, Uri, String, String) -> Uri? = ::saveImageToInternalStorage,
): Uri? = withContext(NzikDispatchers.DATA) {
    try {
        save(context, uri, "thumbnail", thumbnailName)
    } finally {
        ArtworkRevisions.invalidate()
    }
}
