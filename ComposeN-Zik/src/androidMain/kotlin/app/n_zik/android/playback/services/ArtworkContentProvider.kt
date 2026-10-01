package app.n_zik.android.playback.services

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import timber.log.Timber
import app.n_zik.android.core.profiles.coversDir
import app.it.fast4x.rimusic.utils.getActiveProfile
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap

class ArtworkContentProvider : ContentProvider() {

    companion object {
        val AUTHORITY = "${app.n_zik.android.BuildConfig.APPLICATION_ID}.artwork"
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val songId = uri.lastPathSegment ?: return null
        val ctx = context ?: return null
        // Resolved per request (spec-profile-data-separation): the ACTIVE profile's custom
        // covers folder, so a profile set by a launcher shortcut before init still reads its own.
        val coversDir = coversDir(ctx, getActiveProfile(ctx))
        val file = File(coversDir, "cover_${songId}.jpg")
        if (!file.exists()) return null

        // Apply EXIF rotation and center crop, write to temp file
        return try {
            var bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null

            // Apply EXIF rotation
            try {
                val exif = ExifInterface(file.absolutePath)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                val rotation = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (rotation != 0f) {
                    val matrix = Matrix().apply { postRotate(rotation) }
                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    if (rotated !== bitmap) { bitmap.recycle(); bitmap = rotated }
                }
            } catch (_: Exception) {}

            // Center crop to square
            val size = minOf(bitmap.width, bitmap.height)
            val x = (bitmap.width - size) / 2
            val y = (bitmap.height - size) / 2
            val cropped = Bitmap.createBitmap(bitmap, x, y, size, size)
            if (cropped !== bitmap) bitmap.recycle()

            val tempFile = File.createTempFile("artwork_", ".jpg", context?.cacheDir)
            tempFile.deleteOnExit()
            FileOutputStream(tempFile).use { out ->
                cropped.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            cropped.recycle()

            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            Timber.tag("ArtworkProvider").e(e, "Failed to open artwork for song %s", songId)
            null
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String = "image/jpeg"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
