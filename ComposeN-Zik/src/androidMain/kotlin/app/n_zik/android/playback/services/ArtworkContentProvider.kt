package app.n_zik.android.playback.services


import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.MediaStore
import android.util.Base64
import android.util.Size
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.core.backup.isLibraryReady
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.boundProcessStorageIdentity
import app.n_zik.android.core.profiles.ScopedTemporaryFiles
import app.n_zik.android.core.profiles.coversDir
import app.n_zik.android.core.profiles.isCurrentProcessStorageProfile
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.playback.services.automotive.models.ArtworkBitmapDecoder
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
import app.n_zik.android.playback.services.automotive.models.SessionArtworkLoader
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getActiveProfile

/** Serves seekable JPEGs from profile-owned local and signed remote sources. */
class ArtworkContentProvider : ContentProvider() {

    companion object {
        val AUTHORITY = "${BuildConfig.APPLICATION_ID}.artwork"

        private const val TAG = "ArtworkProvider"
        private const val OWNER_PARAM = "p"
        private const val GENERATION_PARAM = "g"
        private const val REVISION_PARAM = "r"
        private val songArtworkParams = setOf(OWNER_PARAM, GENERATION_PARAM, REVISION_PARAM)

        /** Longest side served for MediaStore art (AA renders it at card size). */
        private const val MEDIA_STORE_ART_MAX_SIDE = 512

        private data class Source(val profile: ProfileStorageIdentity, val file: File)
        private const val MAX_SOURCES = 1024
        private val sources = LinkedHashMap<String, Source>(MAX_SOURCES, 0.75f, true)

        internal fun uriForSource(ctx: Context, songId: String, file: File): Uri =
            uriForSource(ctx, getActiveProfile(ctx), songId, file) ?: uriFor(songId)

        /** Only in-process mapping can register files; exported requests accept opaque tokens. */
        internal fun uriForSource(ctx: Context, profile: String, songId: String, file: File): Uri? {
            val owner = profileStorageIdentity(ctx, profile) ?: return null
            if (!isCurrentProcessStorageProfile(ctx, owner)) return null
            val canonical = file.canonicalFile
            val expected = File(coversDir(ctx, owner.id), "cover_$songId.jpg").canonicalFile
            val revision = ArtworkRevisions.current(canonical.path, owner.id)
            if (canonical == expected) {
                val uri = uriFor(songId, revision)
                return uri.takeIf { isSongArtworkUri(it, songId) }?.withSongArtworkOwner(owner)
            }
            val source = Source(owner, canonical)
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
                .appendPath("source").appendPath(token)
                .appendArtworkRevision(revision).build()
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

        internal fun remoteSource(uri: Uri, profile: String, now: Long = System.currentTimeMillis()): Pair<String, Boolean>? =
            verifiedRemoteSource(uri, profile, now)

        /** A retained browser row may be renewed after expiry using its verified signature. */
        internal fun retainedRemoteSource(uri: Uri, profile: String): String? =
            verifiedRemoteSource(uri, profile, now = null)?.first

        private fun verifiedRemoteSource(uri: Uri, profile: String, now: Long?): Pair<String, Boolean>? {
            if (uri.authority != AUTHORITY || uri.pathSegments != listOf("remote")) return null
            val owner = uri.getQueryParameter("p") ?: return null
            val crop = uri.getQueryParameter("c")?.toBooleanStrictOrNull() ?: return null
            val revision = uri.getQueryParameter("r")?.toLongOrNull() ?: return null
            val expires = uri.getQueryParameter("e")?.toLongOrNull() ?: return null
            val source = uri.getQueryParameter("u") ?: return null
            val signature = uri.getQueryParameter("s") ?: return null
            if (owner != profile || source.length > 16384) return null
            if (now != null && (expires <= now || expires - now > REMOTE_LIFETIME_MS)) return null
            val parsed = Uri.parse(source)
            if (parsed.scheme !in setOf("http", "https") || parsed.host.isNullOrBlank()) return null
            val expected = remoteSignature("$owner\n$crop\n$revision\n$expires\n$source")
            if (!MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), signature.toByteArray(Charsets.UTF_8))) return null
            return source to crop
        }

        fun uriFor(songId: String): Uri = uriFor(songId, 0L)

        internal fun uriFor(songId: String, revision: Long): Uri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendPath(songId)
            .appendArtworkRevision(revision)
            .build()

        private fun Uri.Builder.appendArtworkRevision(revision: Long): Uri.Builder =
            apply { if (revision != 0L) appendQueryParameter(REVISION_PARAM, revision.toString()) }

        private fun songArtworkId(uri: Uri): String? {
            if (uri.scheme != "content" || uri.encodedAuthority != AUTHORITY) return null
            return uri.pathSegments.singleOrNull()?.takeUnless { it == "source" || it == "remote" }
        }

        internal fun isSongArtworkUri(uri: Uri, songId: String): Boolean {
            if (songArtworkId(uri) != songId || uri.fragment != null || !isSafeSongId(songId) ||
                uri.encodedPath?.count { it == '/' } != 1
            ) return false
            if (uri.queryParameterNames.any { it !in songArtworkParams || uri.getQueryParameters(it).size != 1 }) {
                return false
            }
            val owner = uri.getQueryParameter(OWNER_PARAM)
            val generation = uri.getQueryParameter(GENERATION_PARAM)
            if (owner != null || generation != null) {
                if (owner.isNullOrBlank() || generation.isNullOrBlank()) return false
            }
            val revision = uri.getQueryParameter(REVISION_PARAM)
            return revision == null || revision.toLongOrNull() != null
        }

        internal fun scopedUriFor(context: Context, profile: String, songId: String, revision: Long = 0L): Uri? {
            val uri = uriFor(songId, revision)
            if (!isSongArtworkUri(uri, songId)) return null
            return bindLocalRequestUri(context, profile, uri)
        }

        internal fun bindLocalRequestUri(context: Context, profile: String, uri: Uri): Uri? {
            val songId = songArtworkId(uri) ?: return uri
            if (!isSongArtworkUri(uri, songId)) return null
            val owner = boundProcessStorageIdentity() ?: profileStorageIdentity(context, profile) ?: return null
            if (owner.id != profile) return null
            return bindSongArtworkUri(uri, owner)
        }

        private fun bindSongArtworkUri(uri: Uri, owner: ProfileStorageIdentity): Uri? {
            if (uri.getQueryParameter(OWNER_PARAM) != null) {
                return uri.takeIf { matchesSongArtworkOwner(it, owner) }
            }
            return uri.withSongArtworkOwner(owner)
        }

        private fun Uri.withSongArtworkOwner(owner: ProfileStorageIdentity): Uri = buildUpon()
            .appendQueryParameter(OWNER_PARAM, owner.id)
            .appendQueryParameter(GENERATION_PARAM, owner.generation)
            .build()

        private fun matchesSongArtworkOwner(uri: Uri, owner: ProfileStorageIdentity): Boolean =
            uri.getQueryParameter(OWNER_PARAM) == owner.id && uri.getQueryParameter(GENERATION_PARAM) == owner.generation

        internal fun registeredSourceFile(ctx: Context, profile: String, uri: Uri): File? =
            registeredSource(uri)?.takeIf { it.profile == profileStorageIdentity(ctx, profile) }?.file
                ?.takeIf { it.isFile && it.canRead() }

        private fun registeredSource(owner: ProfileStorageIdentity, uri: Uri): File? =
            registeredSource(uri)?.takeIf { it.profile == owner }?.file

        private fun registeredSource(uri: Uri): Source? {
            if (uri.scheme != "content" || uri.authority != AUTHORITY ||
                uri.pathSegments.size != 2 || uri.pathSegments.first() != "source"
            ) return null
            return synchronized(sources) { sources[uri.lastPathSegment] }
        }

        internal fun customCoverFile(ctx: Context, profile: String, songId: String): File? =
            ownedCoverPath(ctx, profile, songId)?.takeIf(File::isFile)

        private fun isSafeSongId(songId: String): Boolean =
            songId.isNotBlank() && songId.none { it == '/' || it == '\\' || it == '\u0000' }

        private fun ownedCoverPath(ctx: Context, profile: String, songId: String): File? {
            if (!isSafeSongId(songId)) return null
            val directory = ctx.filesDir.canonicalFile.resolve(coversDir(ctx, profile).name)
            if (directory.canonicalFile != directory.absoluteFile) return null
            val file = directory.resolve("cover_$songId.jpg")
            return file.takeIf { it.canonicalFile == it.absoluteFile }
        }

        internal fun refreshLocalUri(ctx: Context, profile: String, uri: Uri): Uri {
            if (uri.scheme != "content" || uri.authority != AUTHORITY) return uri
            val songId = songArtworkId(uri)
            val source = if (songId == null) registeredSource(uri) ?: return uri else null
            val owner = profileStorageIdentity(ctx, profile) ?: return uri
            if (!isCurrentProcessStorageProfile(ctx, owner)) return uri
            val boundUri = if (songId == null) uri else {
                if (!isSongArtworkUri(uri, songId)) return uri
                bindSongArtworkUri(uri, owner) ?: return uri
            }
            // The path still owns its revision after its custom cover is deleted.
            val file = (if (songId == null) source?.takeIf { it.profile == owner }?.file else ownedCoverPath(ctx, profile, songId))
                ?: return boundUri
            val publishedRevision = uri.getQueryParameter(REVISION_PARAM)
            // An absent unversioned cover needs an explicit file edit to change its URI.
            val includeGlobal = songId == null || publishedRevision != null || file.isFile
            val revision = ArtworkRevisions.current(file.path, profile, includeGlobal)
            if (revision == (publishedRevision?.toLongOrNull() ?: 0L)) return boundUri
            if (songId != null) return uriFor(songId, revision).withSongArtworkOwner(owner)
            return uri.buildUpon().clearQuery().appendArtworkRevision(revision).build()
        }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r" || uri.scheme != "content" || uri.authority != AUTHORITY) return null
        val songId = uri.lastPathSegment ?: return null
        val ctx = context ?: return null
        return try {
            if (uri.pathSegments.firstOrNull() == "remote") {
                if (!ctx.isLibraryReady()) return null
                if (!LocalArtworkRules.isTrustedCaller(runCatching { callingPackage }.getOrNull(), ctx.packageName)) return null
                val profile = getActiveProfile(ctx)
                val (source, crop) = remoteSource(uri, profile) ?: return null
                val owner = profileStorageIdentity(ctx, profile) ?: return null
                if (!isCurrentProcessStorageProfile(ctx, owner)) return null
                // ContentProvider's synchronous descriptor API runs on its Binder worker, never the metadata getter.
                val bytes = runBlocking(NzikDispatchers.DATA) {
                    SessionArtworkLoader.load(source, crop, requestProfile = profile)
                } ?: return null
                if (!isCurrentProcessStorageProfile(ctx, owner)) return null
                val descriptor = writeSeekableBytes(privateArtworkDirectory(ctx, owner), bytes)
                if (isCurrentProcessStorageProfile(ctx, owner)) descriptor else {
                    descriptor.close()
                    null
                }
            } else {
                openLocalArtwork(ctx, uri, songId)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to open artwork for song %s", songId)
            null
        }
    }

    private fun openLocalArtwork(ctx: Context, uri: Uri, songId: String): ParcelFileDescriptor? {
        val sourceRequest = uri.pathSegments.firstOrNull() == "source"
        if (!sourceRequest && !isSongArtworkUri(uri, songId)) return null
        val libraryReady = ctx.isLibraryReady()
        if (!libraryReady && sourceRequest) return null
        val caller = runCatching { callingPackage }.getOrNull()
        val sameUid = Binder.getCallingUid() == Process.myUid()
        val trusted = (!sourceRequest && sameUid) || LocalArtworkRules.isTrustedCaller(caller, ctx.packageName)
        val unscopedExternalSong = !sourceRequest && !sameUid && uri.getQueryParameter(OWNER_PARAM) == null
        if (!libraryReady || !trusted || unscopedExternalSong) {
            return placeholder(ctx)?.let { writeSeekableJpeg(ctx, it) }
        }
        val owner = profileStorageIdentity(ctx, getActiveProfile(ctx)) ?: return null
        if (!isCurrentProcessStorageProfile(ctx, owner)) return null
        if (!sourceRequest && uri.getQueryParameter(OWNER_PARAM) != null && !matchesSongArtworkOwner(uri, owner)) {
            return null
        }

        val privateBitmap = if (sourceRequest) {
            registeredSourceBitmap(owner, uri)
        } else {
            customCover(ctx, owner, songId) ?: mediaStoreArtIfTrusted(ctx, songId)
        }
        val bitmap = privateBitmap ?: placeholder(ctx) ?: return null
        return writeSeekableJpeg(ctx, bitmap, owner, privateBitmap != null)
    }

    private fun registeredSourceBitmap(owner: ProfileStorageIdentity, uri: Uri): Bitmap? {
        val file = registeredSource(owner, uri)?.takeIf { it.isFile && it.canRead() } ?: return null
        val bitmap = try {
            ArtworkBitmapDecoder.file(file)
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
    private fun customCover(ctx: Context, owner: ProfileStorageIdentity, songId: String): Bitmap? {
        val file = customCoverFile(ctx, owner.id, songId) ?: return null
        return ArtworkBitmapDecoder.file(file)?.let { ArtworkBitmapDecoder.finish(it, square = true) }
    }

    /** Reads permission-protected album art in-process for trusted media surfaces. */
    private fun mediaStoreArtIfTrusted(ctx: Context, songId: String): Bitmap? {
        val caller = runCatching { callingPackage }.getOrNull()
        if (!LocalArtworkRules.isTrustedCaller(caller, ctx.packageName)) {
            Timber.tag(TAG).d("MediaStore art refused to untrusted caller %s", caller)
            return null
        }
        return mediaStoreArtwork(ctx, songId)
    }

    internal fun mediaStoreArtwork(ctx: Context, songId: String): Bitmap? {
        val audioId = LocalArtworkRules.mediaStoreAudioIdOf(songId) ?: return null
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
    private fun privateArtworkDirectory(context: Context, owner: ProfileStorageIdentity): File =
        ScopedTemporaryFiles.directory(context, ScopedTemporaryFiles.PRIVATE_ARTWORK, owner)

    private fun writeSeekableJpeg(
        context: Context,
        bitmap: Bitmap,
        owner: ProfileStorageIdentity? = null,
        privateArtwork: Boolean = false,
    ): ParcelFileDescriptor? {
        var tempFile: File? = null
        try {
            if (owner != null && !isCurrentProcessStorageProfile(context, owner)) return null
            val directory = if (privateArtwork && owner != null) privateArtworkDirectory(context, owner)
                else ScopedTemporaryFiles.publicArtwork(context)
            val file = File.createTempFile("artwork_", ".jpg", ScopedTemporaryFiles.requireWritableDirectory(directory))
            tempFile = file
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            var current = false
            try {
                current = owner == null || isCurrentProcessStorageProfile(context, owner)
                return descriptor.takeIf { current }
            } finally {
                if (!current) descriptor.close()
            }
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
