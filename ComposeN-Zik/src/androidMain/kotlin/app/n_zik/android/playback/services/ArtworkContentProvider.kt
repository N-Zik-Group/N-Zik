package app.n_zik.android.playback.services

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Size
import timber.log.Timber
import app.n_zik.android.R
import app.n_zik.android.core.profiles.coversDir
import app.it.fast4x.rimusic.utils.getActiveProfile
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap

/**
 * Signed artwork channel for local tracks: `content://{appId}.artwork/{songId}`.
 *
 * Exported because the Android Auto process must open it (see [LocalArtworkRules]).
 * Always answers with a seekable square JPEG, resolved in order:
 *  1. the song's custom cover (active profile covers folder) — any caller, as before;
 *  2. the MediaStore album art, read in-process with the app's media permission —
 *     trusted callers only ([LocalArtworkRules.isTrustedCaller]);
 *  3. the app placeholder, so an art-less track still renders on the head unit.
 */
class ArtworkContentProvider : ContentProvider() {

    companion object {
        val AUTHORITY = "${app.n_zik.android.BuildConfig.APPLICATION_ID}.artwork"

        private const val TAG = "ArtworkProvider"

        /** Longest side served for MediaStore art (AA renders it at card size). */
        private const val MEDIA_STORE_ART_MAX_SIDE = 512

        fun uriFor(songId: String): Uri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendPath(songId)
            .build()
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val songId = uri.lastPathSegment ?: return null
        val ctx = context ?: return null
        return try {
            val bitmap = customCover(ctx, songId)
                ?: mediaStoreArtIfTrusted(ctx, songId)
                ?: placeholder(ctx)
                ?: return null
            writeSeekableJpeg(ctx, bitmap)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to open artwork for song %s", songId)
            null
        }
    }

    /** Custom cover with EXIF rotation applied, center-cropped (pre-existing behavior). */
    private fun customCover(ctx: Context, songId: String): Bitmap? {
        // Resolved per request (spec-profile-data-separation): the ACTIVE profile's custom
        // covers folder, so a profile set by a launcher shortcut before init still reads its own.
        val file = File(coversDir(ctx, getActiveProfile(ctx)), "cover_${songId}.jpg")
        if (!file.exists()) return null
        var bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
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
        return centerCrop(bitmap)
    }

    /**
     * MediaStore album art of a local song, decoded in-process (the app holds the media
     * permission, the Android Auto process does not). Album art entry first, then the
     * platform audio thumbnail (embedded art) on Q+; null when the track has no art.
     */
    private fun mediaStoreArtIfTrusted(ctx: Context, songId: String): Bitmap? {
        val audioId = LocalArtworkRules.mediaStoreAudioIdOf(songId) ?: return null
        val caller = runCatching { callingPackage }.getOrNull()
        if (!LocalArtworkRules.isTrustedCaller(caller, ctx.packageName)) {
            Timber.tag(TAG).d("MediaStore art refused to untrusted caller %s", caller)
            return null
        }
        val albumArt = albumIdOf(ctx, audioId)?.let { albumId ->
            runCatching {
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )
                ctx.contentResolver.openInputStream(albumArtUri)?.use { it.readBytes() }
                    ?.let(::decodeDownsampled)
            }.getOrNull()
        }
        val art = albumArt ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                ctx.contentResolver.loadThumbnail(
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId),
                    Size(MEDIA_STORE_ART_MAX_SIDE, MEDIA_STORE_ART_MAX_SIDE),
                    null
                )
            }.getOrNull()
        } else null
        return art?.let(::centerCrop)
    }

    private fun albumIdOf(ctx: Context, audioId: Long): Long? =
        ctx.contentResolver.query(
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId),
            arrayOf(MediaStore.Audio.Media.ALBUM_ID),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    private fun decodeDownsampled(data: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeByteArray(
            data,
            0,
            data.size,
            BitmapFactory.Options().apply {
                inSampleSize = app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
                    .computeInSampleSize(bounds.outWidth, bounds.outHeight, MEDIA_STORE_ART_MAX_SIDE)
            }
        )
    }

    /** App placeholder, downscaled (the source drawable is 1200px). */
    private fun placeholder(ctx: Context): Bitmap? {
        val full = BitmapFactory.decodeResource(ctx.resources, R.drawable.ic_launcher_box) ?: return null
        if (minOf(full.width, full.height) <= MEDIA_STORE_ART_MAX_SIDE) return full
        val scaled = Bitmap.createScaledBitmap(full, MEDIA_STORE_ART_MAX_SIDE, MEDIA_STORE_ART_MAX_SIDE, true)
        if (scaled !== full) full.recycle()
        return scaled
    }

    private fun centerCrop(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        val cropped = Bitmap.createBitmap(bitmap, x, y, size, size)
        if (cropped !== bitmap) bitmap.recycle()
        return cropped
    }

    /**
     * Writes [bitmap] to a regular temp file and returns a read-only descriptor on it: a
     * file descriptor is seekable, unlike the pipe MediaStore hands out for embedded art
     * (which Android Auto's decoder rejects with ESPIPE). The file is unlinked right
     * after opening — the descriptor stays valid and the cache dir does not grow.
     */
    private fun writeSeekableJpeg(ctx: Context, bitmap: Bitmap): ParcelFileDescriptor {
        val tempFile = File.createTempFile("artwork_", ".jpg", ctx.cacheDir)
        try {
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
            return ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally {
            tempFile.delete()
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String = "image/jpeg"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
