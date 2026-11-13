package app.n_zik.android.core.utils

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import app.n_zik.android.modules.FfmpegModule
import com.arthenica.ffmpegkit.FFprobeKit
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.core.net.toUri
import app.n_zik.android.awaitLibraryReady
import app.n_zik.android.components.ui.screens.home.onDevice.localSongRefreshRequests
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.components.ui.screens.home.onDevice.LocalFolderProfileContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.ondevice.DeviceLibrarySnapshot
import app.n_zik.android.core.ondevice.DeviceLibraryResults
import app.n_zik.android.core.ondevice.IndexedDeviceTrack
import app.n_zik.android.core.ondevice.deviceTag
import app.n_zik.android.core.ondevice.indexDeviceTracks
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.ondevice.isDeviceLibraryEnabled
import app.n_zik.android.core.ondevice.hasDeviceAudioPermission
import app.n_zik.android.enums.OnDeviceSongSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.Format
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.utils.isAtLeastAndroid10
import app.n_zik.android.utils.isAtLeastAndroid11
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

private val PROJECTION by lazy {
    var projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.YEAR,
        if (isAtLeastAndroid10) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            MediaStore.Audio.Media.DATA
        },
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.SIZE,
    )
    if (isAtLeastAndroid10)
        projection += arrayOf(MediaStore.Audio.Media.VOLUME_NAME, MediaStore.Audio.Media.DATA)
    if ( isAtLeastAndroid11 )
        projection += arrayOf(MediaStore.Audio.Media.BITRATE, MediaStore.Audio.Media.ALBUM_ARTIST)

    return@lazy projection
}
val ALBUM_URI = "content://media/external/audio/albumart".toUri()

internal val localAudioSelection = run {
    // MediaStore can index a playable track without setting IS_MUSIC.
    val eligible = "(${MediaStore.Audio.Media.IS_MUSIC} > 0" +
        " OR ${MediaStore.Audio.Media.DURATION} > 0" +
        " OR LOWER(${MediaStore.Audio.Media.MIME_TYPE}) LIKE 'audio/%')"
    val exclusions = listOf(
        MediaStore.Audio.Media.IS_ALARM,
        MediaStore.Audio.Media.IS_NOTIFICATION,
        MediaStore.Audio.Media.IS_RINGTONE,
    ).joinToString(" AND ") { "COALESCE($it, 0) = 0" }
    "$eligible AND $exclusions"
}

internal fun localAudioTitle(title: String?, displayName: String?): String? =
    title?.takeIf { it.isNotBlank() } ?: displayName?.takeIf { it.isNotBlank() }

private fun Format.hasSameSourceAs(other: Format): Boolean =
    mimeType == other.mimeType && bitrate == other.bitrate &&
        contentLength == other.contentLength && lastModified == other.lastModified

fun Context.getLocalSongs(
    sortBy: OnDeviceSongSortBy,
    sortOrder: SortOrder,
): Flow<Map<Song, String>> = flow {
    emit(readDeviceLibrary().sortedSongs(sortBy, sortOrder))
}.flowOn(NzikDispatchers.DATA)

internal data class DeviceSongOrder(val album: String?, val dateAdded: Long, val duration: Long)

internal class DeviceScan(
    private val songs: Map<Song, String>,
    val snapshot: DeviceLibrarySnapshot,
    private val order: Map<String, DeviceSongOrder> = emptyMap(),
) {
    fun sortedSongs(sortBy: OnDeviceSongSortBy, sortOrder: SortOrder): Map<Song, String> {
        val comparator = when (sortBy) {
            OnDeviceSongSortBy.Title -> compareBy(String.CASE_INSENSITIVE_ORDER) { song: Song -> song.title }
            OnDeviceSongSortBy.Artist -> compareBy(nullsFirst(String.CASE_INSENSITIVE_ORDER)) { song: Song -> song.artistsText }
            OnDeviceSongSortBy.Album -> compareBy(nullsFirst(String.CASE_INSENSITIVE_ORDER)) { song: Song -> order[song.id]?.album }
            OnDeviceSongSortBy.DateAdded -> compareBy { song: Song -> order[song.id]?.dateAdded }
            OnDeviceSongSortBy.Duration -> compareBy { song: Song -> order[song.id]?.duration }
        }
        val ordered = if (sortOrder == SortOrder.Ascending) comparator else comparator.reversed()
        return songs.keys.sortedWith(ordered).associateWith(songs::getValue)
    }
}

private data class CachedDeviceScan(val owner: ProfileStorageIdentity, val revision: Long, val scan: DeviceScan)
private var cachedDeviceScan: CachedDeviceScan? = null

private val deviceScanMutex = Mutex()

internal suspend fun Context.readDeviceLibrary(): DeviceScan {
    awaitLibraryReady()
    val ownerContext = applicationContext
    val owner = ProfileWorkOwner.capture(ownerContext) ?: return DeviceScan(emptyMap(), DeviceLibrarySnapshot())
    if (this is LocalFolderProfileContext && profile != owner.id) {
        return DeviceScan(emptyMap(), DeviceLibrarySnapshot())
    }
    fun scanAllowed() = ProfileWorkOwner.isBound(ownerContext, owner) && ownerContext.isDeviceLibraryEnabled(owner.id) && ownerContext.hasDeviceAudioPermission()
    return deviceScanMutex.withLock {
        if (!scanAllowed()) return@withLock DeviceScan(emptyMap(), DeviceLibrarySnapshot())
        val revision = localSongRefreshRequests.current(owner.id)
        cachedDeviceScan?.takeIf { it.owner == owner && it.revision == revision }?.let {
            return@withLock it.scan
        }
        val scan = readDeviceLibraryLocked(::scanAllowed)
        if (scanAllowed()) {
            cachedDeviceScan = CachedDeviceScan(owner, revision, scan)
            DeviceLibraryResults.publish(owner.id, scan.snapshot)
            scan
        } else {
            DeviceScan(emptyMap(), DeviceLibrarySnapshot())
        }
    }
}

private suspend fun Context.readDeviceLibraryLocked(
    isCurrentOwner: () -> Boolean,
): DeviceScan {
    if (!isCurrentOwner()) return DeviceScan(emptyMap(), DeviceLibrarySnapshot())
    val results = linkedMapOf<Song, String>()
    val yearsByAlbum = mutableMapOf<Long, String>()
    val installedProbe by lazy { FfmpegModule.loadIfInstalled() }
    val blockedFoldersFile = File(filesDir, "Blacklisted_paths.txt")
    val blockedFolders = if (blockedFoldersFile.isFile) {
        blockedFoldersFile.readLines().mapNotNull(PathUtils::normalizeExclusion).toSet()
    } else {
        emptySet()
    }

    val uri =
        if (isAtLeastAndroid10)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val order = mutableMapOf<String, DeviceSongOrder>()

    val tracks = mutableListOf<IndexedDeviceTrack>()
    val cursor = contentResolver.query(uri, PROJECTION, localAudioSelection, null, null)
        ?: throw IOException("MediaStore returned no audio cursor")
    cursor.use {
        val idColumn = cursor.getColumnIndex( MediaStore.Audio.Media._ID )
        val nameColumn = cursor.getColumnIndex( MediaStore.Audio.Media.DISPLAY_NAME )
        val durationColumn = cursor.getColumnIndex( MediaStore.Audio.Media.DURATION )
        val artistColumn = cursor.getColumnIndex( MediaStore.Audio.Media.ARTIST )
        val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
        val albumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
        val albumArtistColumn = if (isAtLeastAndroid11) cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST) else -1
        val trackColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
        val pathColumn = if (isAtLeastAndroid10) {
            cursor.getColumnIndex( MediaStore.Audio.Media.RELATIVE_PATH )
        } else {
            cursor.getColumnIndex( MediaStore.Audio.Media.DATA )
        }
        val yearColumn = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
        val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
        val titleColumn = cursor.getColumnIndex( MediaStore.Audio.Media.TITLE )
        val volumeColumn = if (isAtLeastAndroid10) cursor.getColumnIndex(MediaStore.Audio.Media.VOLUME_NAME) else -1
        val mimeTypeColumn = cursor.getColumnIndex( MediaStore.Audio.Media.MIME_TYPE )
        val bitrateColumn = if (isAtLeastAndroid11) cursor.getColumnIndex( MediaStore.Audio.Media.BITRATE ) else -1
        val fileSizeColumn = cursor.getColumnIndex( MediaStore.Audio.Media.SIZE )
        val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val dateModifiedColumn = cursor.getColumnIndex( MediaStore.Audio.Media.DATE_MODIFIED )

        while( cursor.moveToNext() ) {
            currentCoroutineContext().ensureActive()
            val relPath = PathUtils.mediaFolder(
                path = if (pathColumn >= 0) cursor.getString(pathColumn) else null,
                isFilePath = !isAtLeastAndroid10,
                volumeName = if (volumeColumn >= 0) cursor.getString(volumeColumn) else null,
            )
            if (blockedFolders.any { PathUtils.isExcluded(relPath, it) }) continue

            // Nullable so SongItem can display "--:--"
            // TODO apply some non-null method
            val durationText =
                cursor.getInt( durationColumn )
                    .takeIf { it > 0 }
                    ?.milliseconds
                    ?.toComponents { hrs, mins, secs, _ ->
                        if( hrs > 0 )
                            "%02d:%02d:%02d".format( hrs, mins, secs )
                        else
                            "%02d:%02d".format( mins, secs )
                    }
            val id = cursor.getLong( idColumn )
            val title = localAudioTitle(cursor.getString(titleColumn), cursor.getString(nameColumn))
                ?: continue
            val artist = cursor.getString( artistColumn )
            val albumId = cursor.getLong(albumIdColumn)
            val albumUri = ContentUris.withAppendedId(ALBUM_URI, albumId)
            val song = Song( "$LOCAL_KEY_PREFIX$id", title, artist, durationText, albumUri.toString() )

            val mimeType = cursor.getString( mimeTypeColumn )
            val bitrate = if( isAtLeastAndroid11 ) cursor.getLong( bitrateColumn ) else 0
            val fileSize = cursor.getLong( fileSizeColumn )
            val dateModified = cursor.getLong( dateModifiedColumn )

            val format = Format( song.id, 0, mimeType, bitrate, fileSize, dateModified,
                sampleRate = null, audioChannels = null, codecs = null )

            val album = deviceTag(cursor.getString(albumColumn))
            val indexedYear = if (yearColumn >= 0) cursor.getInt(yearColumn).takeIf { it in 1000..9999 }?.toString() else null
            val year = indexedYear ?: if (album != null) {
                yearsByAlbum[albumId] ?: readAudioReleaseYear(
                    ContentUris.withAppendedId(uri, id),
                    if (dataColumn >= 0) cursor.getString(dataColumn) else null,
                    { installedProbe },
                )?.also { if (albumId > 0) yearsByAlbum[albumId] = it }
            } else null
            tracks += IndexedDeviceTrack(
                song = song,
                format = format,
                album = album,
                albumArtist = if (albumArtistColumn >= 0) deviceTag(cursor.getString(albumArtistColumn)) else null,
                trackNumber = cursor.getInt(trackColumn).takeIf { it > 0 },
                year = year,
            )

            results[song] = relPath
            order[song.id] = DeviceSongOrder(album, cursor.getLong(dateAddedColumn), cursor.getLong(durationColumn))
        }
    }

    val (snapshot, toProbe) = Database.indexDeviceTracks(tracks, isCurrentOwner)

    // Keep optional codec probing off the scan and browse paths.
    if (toProbe.isNotEmpty()) {
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            for (baseFormat in toProbe) {
                if (!isCurrentOwner()) break
                val id = baseFormat.songId.removePrefix(LOCAL_KEY_PREFIX).toLong()
                var sampleRate: Int? = null
                var audioChannels: Int? = null
                var codec: String? = null
                try {
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(this@readDeviceLibraryLocked, contentUri, null)
                        for (i in 0 until extractor.trackCount) {
                            val trackFormat = extractor.getTrackFormat(i)
                            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                            if (mime?.startsWith("audio/") == true) {
                                sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).takeIf { it > 0 }
                                audioChannels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).takeIf { it > 0 }
                                codec = mime.substringAfter("/", "").takeIf { it.isNotEmpty() }
                                break
                            }
                        }
                    } finally {
                        extractor.release()
                    }
                    if (sampleRate != null || codec != null || audioChannels != null) {
                        Database.asyncTransaction {
                            if (!isCurrentOwner()) return@asyncTransaction
                            val current = formatTable.findBySongIdDirect(baseFormat.songId)
                            if (current != null && current.hasSameSourceAs(baseFormat)) {
                                formatTable.upsert(current.copy(sampleRate = sampleRate, audioChannels = audioChannels, codecs = codec))
                            }
                        }
                        Timber.tag("OnDeviceMedia").d("Probed local:%d → sampleRate=%s, channels=%s, codec=%s", id, sampleRate, audioChannels, codec)
                    }
                } catch (e: Exception) {
                    Timber.tag("OnDeviceMedia").d(e, "Failed to probe audio format for local:$id")
                }
                kotlinx.coroutines.yield() // allow cancellation and don't hog the thread
            }
        }
    }
    return DeviceScan(results, snapshot.copy(folders = results.values.toSet()), order)
}

private val audioReleaseYearPattern = Regex("""^([1-9][0-9]{3})(?=$|[-/T ])""")

private fun audioReleaseYear(value: String?): String? =
    value?.trim()?.let { audioReleaseYearPattern.find(it)?.groupValues?.get(1) }

private fun Context.readAudioReleaseYear(uri: Uri, path: String?, canProbe: () -> Boolean): String? {
    val platformYear = try {
        MediaMetadataRetriever().use { reader ->
            reader.setDataSource(this, uri)
            audioReleaseYear(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR))
                ?: audioReleaseYear(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE))
        }
    } catch (error: Exception) {
        Timber.tag("OnDeviceMedia").d(error, "Could not read embedded audio year")
        null
    }
    if (platformYear != null || path.isNullOrBlank() || !canProbe()) return platformYear

    return try {
        val info = FFprobeKit.getMediaInformation(path).mediaInformation
        val tagSets = listOfNotNull(info?.tags) + info?.streams.orEmpty().mapNotNull { it.tags }
        tagSets.asSequence().flatMap { tags ->
            tags.keys().asSequence()
                .filter { it.equals("date", true) || it.equals("year", true) }
                .mapNotNull { audioReleaseYear(tags.optString(it)) }
        }.firstOrNull()
    } catch (error: Exception) {
        Timber.tag("OnDeviceMedia").d(error, "Could not probe embedded audio year")
        null
    }
}
