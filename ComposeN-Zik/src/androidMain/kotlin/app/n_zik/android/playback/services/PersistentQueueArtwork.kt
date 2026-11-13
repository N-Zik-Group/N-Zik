package app.n_zik.android.playback.services

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.R
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import app.n_zik.android.playback.services.automotive.models.ArtworkBitmapDecoder
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper.ORIGINAL_ARTWORK_SOURCE_EXTRA
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.CancellationException
import timber.log.Timber

@UnstableApi
internal object PersistentQueueArtwork {
    fun durableUri(source: String?, songId: String? = null): Uri? {
        val clean = source?.let(::cleanPrefix)?.takeIf(String::isNotBlank) ?: return null
        val uri = if (clean.startsWith('/')) Uri.fromFile(File(clean)) else Uri.parse(clean)
        return uri.takeIf {
            when (uri.scheme) {
                "http", "https" -> !uri.host.isNullOrBlank()
                "file" -> !uri.path.isNullOrBlank()
                "content" -> !uri.authority.isNullOrBlank() &&
                    (uri.authority != ArtworkContentProvider.AUTHORITY ||
                        (songId != null && isSongArtworkUri(uri, songId)))
                "android.resource" -> !uri.authority.isNullOrBlank() &&
                    uri.lastPathSegment != R.drawable.ic_launcher_box.toString() &&
                    uri.pathSegments != listOf("drawable", "ic_launcher_box")
                else -> false
            }
        }
    }

    fun originalSource(item: MediaItem): String? =
        item.mediaMetadata.extras?.getString(ORIGINAL_ARTWORK_SOURCE_EXTRA)

    private fun isSongArtworkUri(uri: Uri, songId: String): Boolean =
        songId != "source" && songId != "remote" && uri == ArtworkContentProvider.uriFor(songId)

    fun readableUri(source: String?, context: () -> Context): Uri? =
        durableUri(source)?.takeIf { uri ->
            when (uri.scheme) {
                "file" -> validated {
                    val file = try {
                        File(URI(uri.toString()))
                    } catch (_: IllegalArgumentException) {
                        File(uri.path.orEmpty())
                    }
                    decodesFile(file)
                }
                "content" -> validated {
                    ArtworkBitmapDecoder.decode { context().contentResolver.openInputStream(uri) }
                        ?.let { it.recycle(); true } == true
                }
                "android.resource" -> validated {
                    val resources = context().packageManager.getResourcesForApplication(requireNotNull(uri.authority))
                    val id = when (uri.pathSegments.size) {
                        1 -> uri.lastPathSegment?.toIntOrNull() ?: 0
                        2 -> resources.getIdentifier(uri.pathSegments[1], uri.pathSegments[0], uri.authority)
                        else -> 0
                    }
                    id != 0 && resources.getDrawable(id, null) != null
                }
                else -> true
            }
        }

    private inline fun validated(check: () -> Boolean): Boolean = try {
        check()
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: Resources.NotFoundException) {
        false
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: URISyntaxException) {
        false
    }

    private fun decodesFile(file: File): Boolean =
        ArtworkBitmapDecoder.file(file)?.let { it.recycle(); true } == true

    fun backingCoverFile(context: Context, profile: String, source: String?, songId: String): File? =
        durableUri(source, songId)?.takeIf { isSongArtworkUri(it, songId) }?.let {
            var cover: File? = null
            validated {
                cover = ArtworkContentProvider.customCoverFile(context, profile, songId)?.takeIf(::decodesFile)
                cover != null
            }
            cover
        }

    private fun backingCoverUri(context: Context, profile: String, source: String?, songId: String): Uri? =
        durableUri(source, songId)?.takeIf { uri ->
            isSongArtworkUri(uri, songId) && (backingCoverFile(context, profile, source, songId) != null ||
                validated { ArtworkContentProvider().mediaStoreArtwork(context, songId)?.let { it.recycle(); true } == true })
        }

    fun preserveSnapshotSource(context: Context, profile: String, item: MediaItem): MediaItem {
        val artwork = item.mediaMetadata.artworkUri?.takeIf { it.scheme == "content" } ?: return item
        if (durableUri(originalSource(item)) != null) return item
        val source = try {
            ArtworkContentProvider.remoteSource(artwork, profile)?.first
                ?: ArtworkContentProvider.registeredSourceFile(context, profile, artwork)?.let { Uri.fromFile(it).toString() }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Timber.tag("PersistentQueueArtwork").w(exception, "Could not preserve queue artwork for %s", item.mediaId)
            null
        } ?: return item
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply {
            val prefix = if (getBoolean("isCoverModified")) MODIFIED_PREFIX else ""
            putString(ORIGINAL_ARTWORK_SOURCE_EXTRA, prefix + source)
        }
        return item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build()
    }

    fun restore(
        context: Context,
        profile: String,
        item: MediaItem,
        libraryArtwork: (String) -> String?
    ): MediaItem {
        val metadata = item.mediaMetadata
        val saved = metadata.artworkUri
        val songId = LocalArtworkRules.songIdOf(item.mediaId)
        val customCoverUri = backingCoverUri(context, profile, saved?.toString(), songId)
        val savedModified = metadata.extras?.getBoolean("isCoverModified") == true
        val original = originalSource(item)
        val originalUri = readableUri(original) { context }
        val source = readableUri(saved?.toString()) { context }?.let { it to savedModified }
            ?: customCoverUri?.let { it to savedModified }
            ?: originalUri?.let { it to (original?.startsWith(MODIFIED_PREFIX, true) == true) }
            ?: saved?.let { ArtworkContentProvider.registeredSourceFile(context, profile, it) }
                ?.takeIf { validated { decodesFile(it) } }
                ?.let { Uri.fromFile(it) to savedModified }
            ?: saved?.takeIf { it.scheme == "content" }?.let {
                // The provider authenticates the embedded source and rejects tokens from prior processes.
                ArtworkContentProvider.remoteSource(it, profile)?.first?.let { url -> Uri.parse(url) to savedModified }
            }
            ?: try {
                libraryArtwork(songId)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.tag("PersistentQueueArtwork").w(exception, "Artwork lookup failed for %s", songId)
                null
            }?.let { library ->
                val uri = readableUri(library) { context } ?: backingCoverUri(context, profile, library, songId)
                uri?.let { it to library.startsWith(MODIFIED_PREFIX, true) }
            }
        val artwork = source?.first ?: drawableUri(context, R.drawable.ic_launcher_box)
        val extras = Bundle(metadata.extras ?: Bundle()).apply {
            putBoolean("isFromPersistentQueue", true)
            val modified = source?.second == true
            putBoolean("isCoverModified", modified)
            if (originalUri != null) {
                putString(ORIGINAL_ARTWORK_SOURCE_EXTRA, original)
            } else if (source != null && !isSongArtworkUri(source.first, songId)) {
                putString(ORIGINAL_ARTWORK_SOURCE_EXTRA, (if (modified) MODIFIED_PREFIX else "") + source.first)
            }
            else remove(ORIGINAL_ARTWORK_SOURCE_EXTRA)
        }
        return item.buildUpon().setUri(item.mediaId).setCustomCacheKey(item.mediaId)
            .setMediaMetadata(metadata.buildUpon().setArtworkUri(artwork).setExtras(extras).build())
            .build()
    }
}
