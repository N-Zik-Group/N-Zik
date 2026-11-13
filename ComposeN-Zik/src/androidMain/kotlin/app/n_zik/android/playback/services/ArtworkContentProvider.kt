package app.n_zik.android.playback.services

import app.n_zik.android.components.image.OWNED_COVER_EXTENSION_PATTERN
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.Dependencies
import app.n_zik.android.core.database.Database

import app.n_zik.android.core.backup.isLibraryReady

import android.content.ContentProvider
import app.n_zik.android.core.profiles.ScopedTemporaryFiles
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.MediaStore
import android.util.Size
import timber.log.Timber
import app.n_zik.android.R
import app.n_zik.android.core.profiles.coversDir
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.it.fast4x.rimusic.utils.getActiveProfile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import app.n_zik.android.playback.services.automotive.models.ArtworkBitmapDecoder
import android.graphics.Bitmap
import android.util.Base64
import app.n_zik.android.playback.services.automotive.models.SessionArtworkLoader
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking

/** Serves bounded seekable JPEGs with signed profile-owned remote sources. */
class ArtworkContentProvider : ContentProvider() {

    companion object {
        val AUTHORITY = "${app.n_zik.android.BuildConfig.APPLICATION_ID}.artwork"

        private const val TAG = "ArtworkProvider"

        /** Longest side served for MediaStore art (AA renders it at card size). */
        private const val MEDIA_STORE_ART_MAX_SIDE = 512

        private data class Source(val profile: ProfileStorageIdentity, val file: File)
        private const val MAX_SOURCES = 1024
        private val sources = LinkedHashMap<String, Source>(MAX_SOURCES, 0.75f, true)

        /** Only in-process mapping can register files; exported requests accept opaque tokens. */
        internal fun uriForSource(ctx: Context, songId: String, file: File): Uri {
            val profile = profileStorageIdentity(ctx, getActiveProfile(ctx)) ?: return uriFor(songId)
            val canonical = file.canonicalFile
            val expected = File(coversDir(ctx, profile.id), "cover_${songId}.jpg").canonicalFile
            if (canonical == expected) return uriFor(songId)
            val source = Source(profile, canonical)
            val token = synchronized(sources) {
                val existing = sources.entries.firstOrNull { it.value == source }?.key
                if (existing != null) {
                    sources[existing] // Refresh the source registry's LRU order.
                    existing
                } else UUID.randomUUID().toString().also {
                    sources[it] = source
                    while (sources.size > MAX_SOURCES) sources.remove(sources.keys.first())
                }
            }
            return Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath("source").appendPath(token).build()
        }

        private val remoteSigningKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        private const val REMOTE_LIFETIME_MS = 24 * 60 * 60 * 1000L

        private fun remoteSignature(payload: String): String = Base64.encodeToString(
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(remoteSigningKey, "HmacSHA256")) }
                .doFinal(payload.toByteArray(Charsets.UTF_8)), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )

        /** Self-contained signed identity has no queue-size-dependent registration limit. */
        internal fun uriForRemote(originalSource: String, profile: String, square: Boolean, revision: Long,
            expires: Long = System.currentTimeMillis() + REMOTE_LIFETIME_MS): Uri? {
            val source = originalSource
            val parsed = Uri.parse(source)
            if (parsed.scheme !in setOf("http", "https") || parsed.host.isNullOrBlank()) return null
            val payload = "$profile\n$square\n$revision\n$expires\n$source"
            return Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("remote")
                .appendQueryParameter("p", profile).appendQueryParameter("c", square.toString())
                .appendQueryParameter("r", revision.toString()).appendQueryParameter("e", expires.toString())
                .appendQueryParameter("u", source).appendQueryParameter("s", remoteSignature(payload)).build()
        }

        internal fun remoteSource(uri: Uri, profile: String, now: Long = System.currentTimeMillis()): Pair<String, Boolean>? {
            if (uri.authority != AUTHORITY || uri.pathSegments != listOf("remote")) return null
            val owner = uri.getQueryParameter("p") ?: return null
            val crop = uri.getQueryParameter("c")?.toBooleanStrictOrNull() ?: return null
            val revision = uri.getQueryParameter("r")?.toLongOrNull() ?: return null
            val expires = uri.getQueryParameter("e")?.toLongOrNull() ?: return null
            val source = uri.getQueryParameter("u") ?: return null
            val signature = uri.getQueryParameter("s") ?: return null
            if (owner != profile || expires <= now || expires - now > REMOTE_LIFETIME_MS || source.length > 16384) return null
            val parsed = Uri.parse(source)
            if (parsed.scheme !in setOf("http", "https") || parsed.host.isNullOrBlank()) return null
            val expected = remoteSignature("$owner\n$crop\n$revision\n$expires\n$source")
            if (!MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), signature.toByteArray(Charsets.UTF_8))) return null
            return source to crop
        }

        fun uriFor(songId: String): Uri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendPath(songId)
            .build()
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r" || uri.authority != AUTHORITY) return null
        val songId = uri.lastPathSegment ?: return null
        val ctx = context ?: return null
        return try {
            val requestType = uri.pathSegments.firstOrNull()
            if (!ctx.isLibraryReady()) {
                if (requestType == "remote" || requestType == "source") return null
                return placeholder(ctx)?.let { writeSeekableJpeg(ScopedTemporaryFiles.publicArtwork(ctx), it) }
            }
            if (requestType == "remote") {
                if (!LocalArtworkRules.isTrustedCaller(runCatching { callingPackage }.getOrNull(), ctx.packageName)) return null
                val profile = getActiveProfile(ctx)
                val (source, crop) = remoteSource(uri, profile) ?: return null
                // ContentProvider's synchronous descriptor API runs on its Binder worker, never the metadata getter.
                val bytes = runBlocking { SessionArtworkLoader.load(source, crop) } ?: return null
                if (profile != getActiveProfile(ctx)) return null
                return writeSeekableBytes(privateArtworkDirectory(ctx), bytes)
            }
            val profile = getActiveProfile(ctx)
            val sourceRequest = requestType == "source"
            val trusted = Binder.getCallingUid() == Process.myUid() ||
                LocalArtworkRules.isTrustedCaller(runCatching { callingPackage }.getOrNull(), ctx.packageName)
            val privateBitmap = when {
                sourceRequest -> registeredSource(ctx, uri)
                trusted -> customCover(ctx, songId) ?: mediaStoreArtIfTrusted(ctx, songId)
                else -> null
            }
            val bitmap = privateBitmap ?: placeholder(ctx) ?: return null
            if (profile != getActiveProfile(ctx)) {
                bitmap.recycle()
                return null
            }
            val directory = if (privateBitmap == null) ScopedTemporaryFiles.publicArtwork(ctx) else privateArtworkDirectory(ctx)
            writeSeekableJpeg(directory, bitmap)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to open artwork for song %s", songId)
            null
        }
    }

    private fun registeredSource(ctx: Context, uri: Uri): Bitmap? {
        if (uri.pathSegments.size != 2 ||
            !LocalArtworkRules.isTrustedCaller(runCatching { callingPackage }.getOrNull(), ctx.packageName)) return null
        val source = synchronized(sources) { sources[uri.lastPathSegment] } ?: return null
        if (source.profile != profileStorageIdentity(ctx, getActiveProfile(ctx))) return null
        val bitmap = try {
            ArtworkBitmapDecoder.file(source.file)
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "Registered artwork source could not be read")
            null
        } catch (e: SecurityException) {
            Timber.tag(TAG).w(e, "Registered artwork source is no longer accessible")
            null
        }
        return bitmap?.let { ArtworkBitmapDecoder.finish(it, square = true) }
    }

    /** Custom cover with EXIF rotation applied, center-cropped (pre-existing behavior). */
    private fun customCover(ctx: Context, songId: String): Bitmap? {
        if (songId.isBlank() || songId.any { it == '/' || it == '\\' }) return null
        val directory = ctx.filesDir.canonicalFile.resolve(coversDir(ctx, getActiveProfile(ctx)).name)
        if (directory.canonicalFile != directory.absoluteFile) return null
        val url = if (Dependencies.isInitialized) {
            Database.songTable.findByIdDirect(songId)?.thumbnailUrl?.let(::cleanPrefix)
        } else null
        val file = if (url != null) {
            val uri = Uri.parse(url)
            val path = when (uri.scheme) {
                "file" -> uri.path
                null -> url
                else -> null
            } ?: return null
            File(path).takeIf {
                it.parentFile == directory &&
                    it.extension in OWNED_COVER_EXTENSION_PATTERN.split('|') &&
                    it.name.startsWith("cover_")
            } ?: return null
        } else directory.resolve("cover_$songId.jpg")
        if (!file.isFile || file.canonicalFile != file.absoluteFile) return null
        return ArtworkBitmapDecoder.file(file)?.let { ArtworkBitmapDecoder.finish(it, square = true) }
    }

    /** Reads permission-protected album art in-process for trusted media surfaces. */
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
                ArtworkBitmapDecoder.decode { ctx.contentResolver.openInputStream(albumArtUri) }
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
        return art?.let { ArtworkBitmapDecoder.finish(it, square = true) }
    }

    private fun albumIdOf(ctx: Context, audioId: Long): Long? =
        ctx.contentResolver.query(
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId),
            arrayOf(MediaStore.Audio.Media.ALBUM_ID),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    private fun placeholder(ctx: Context): Bitmap? = ArtworkBitmapDecoder.decode {
        ctx.resources.openRawResource(R.drawable.ic_launcher_box)
    }?.let { ArtworkBitmapDecoder.finish(it, square = true) }

    // A regular file avoids ESPIPE in AA; unlinking leaves its open descriptor valid.
    private fun privateArtworkDirectory(context: Context): File = ScopedTemporaryFiles.directory(
        context, ScopedTemporaryFiles.PRIVATE_ARTWORK, ScopedTemporaryFiles.activeOwner(context)
    )

    private fun writeSeekableJpeg(directory: File, bitmap: Bitmap): ParcelFileDescriptor {
        var tempFile: File? = null
        try {
            val file = File.createTempFile("artwork_", ".jpg", ScopedTemporaryFiles.requireWritableDirectory(directory))
            tempFile = file
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally {
            bitmap.recycle()
            tempFile?.delete()
        }
    }

    private fun writeSeekableBytes(directory: File, bytes: ByteArray): ParcelFileDescriptor {
        val file = File.createTempFile("artwork_", ".jpg", ScopedTemporaryFiles.requireWritableDirectory(directory))
        try {
            file.writeBytes(bytes)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally { file.delete() }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String = "image/jpeg"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
