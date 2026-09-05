package app.it.fast4x.rimusic.utils

import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import android.R.attr.duration
import app.n_zik.android.core.database.*


import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.core.network.utils.isNetworkAvailable
import app.n_zik.android.core.network.utils.isNetworkConnected
import app.it.fast4x.rimusic.utils.canPushToYTM
import app.it.fast4x.rimusic.utils.PlaylistEditThrottle
import app.n_zik.android.core.network.utils.isNetworkAvailableComposable
import android.provider.MediaStore
import android.text.format.DateUtils
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.core.coil.thumbnail
import com.zionhuang.innertube.pages.LibraryPage
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic.addToPlaylist
import it.fast4x.innertube.YtMusic.likeVideoOrSong
import it.fast4x.innertube.YtMusic.removelikeVideoOrSong
import it.fast4x.innertube.requests.playlistPage
import it.fast4x.innertube.requests.playlistPageContinuation
import it.fast4x.kugou.KuGou
import it.fast4x.lrclib.LrcLib
import androidx.media3.session.MediaConstants.EXTRAS_KEY_IS_EXPLICIT
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.hasExplicitPrefix
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.context
import app.it.fast4x.rimusic.models.Album
import app.n_zik.android.models.Lyrics
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.stripExplicitEmoji
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.playback.services.isLocal
import app.it.fast4x.rimusic.ui.components.themed.NewVersionDialog
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import app.kreate.android.me.knighthat.utils.Toaster
import java.io.File
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import timber.log.Timber
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper
import app.n_zik.android.playback.services.ArtworkContentProvider
import android.graphics.Bitmap
import android.media.ExifInterface
import android.content.ContentResolver
import android.graphics.Matrix
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

const val EXPLICIT_BUNDLE_TAG = "is_explicit"

val Innertube.AlbumItem.asAlbum: Album
    get() = Album (
        id = key,
        title = info?.name,
        thumbnailUrl = thumbnail?.url,
        year = year,
        authorsText = authors.parseArtists().joinToString(", "),
        //shareUrl =
    )

val Innertube.Podcast.EpisodeItem.asMediaItem: MediaItem
    @UnstableApi
    get() = MediaItem.Builder()
        .setMediaId(videoId)
        .setUri(videoId)
        .setCustomCacheKey(videoId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(author.toString())
                .setAlbumTitle(title)
                .setArtworkUri(thumbnail.lastOrNull()?.url?.thumbnail(1200)?.toUri())
                .setExtras(
                    bundleOf(
                        //"albumId" to album?.endpoint?.browseId,
                        "durationText" to durationString,
                        "artistNames" to author,
                        EXTRAS_KEY_IS_EXPLICIT to false,
                        //"artistIds" to authors?.mapNotNull { it.endpoint?.browseId },
                    )
                )
                .build()
        )
        .build()

val Innertube.SongItem.asMediaItem: MediaItem
    @UnstableApi
    get() = MediaItem.Builder()
        .setMediaId(key)
        .setUri(key)
        .setCustomCacheKey(key)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(
                    (if (explicit) "\uD83C\uDD74 " else "") + info?.name
                )
                .setArtist(authors.parseArtists().joinToString(", "))
                .setAlbumTitle(album?.name)
                .setArtworkUri(thumbnail?.url?.toUri())
                .setExtras(
                    bundleOf(
                        "albumId" to album?.endpoint?.browseId,
                        "durationText" to durationText,
                        "artistNames" to ArrayList(authors.parseArtists()),
                        "artistIds" to ArrayList(authors?.mapNotNull { it.endpoint?.browseId } ?: emptyList()),
                        EXPLICIT_BUNDLE_TAG to explicit,
                        EXTRAS_KEY_IS_EXPLICIT to explicit,
                        "setVideoId" to setVideoId,
                    )
                )
                .build()
        )
        .build()

/**
 * Parse artist names from authors list, handling "&" and "," separators within single strings.
 * Returns a clean list of individual artist names.
 */
fun List<Innertube.Info<*>?>?.parseArtists(): List<String> {
    if (this == null) return emptyList()
    
    val result = mutableListOf<String>()
    for (author in this) {
        val name = author?.name ?: continue
        // Skip pure separators
        if (name.matches(Regex("\\s*([,&])\\s*"))) continue
        // Skip view/play count patterns like "123,456,789 views" or "1.2M vues"
        if (Regex("\\d.*(view|plays)", RegexOption.IGNORE_CASE).containsMatchIn(name)) continue
        // Check if this name contains multiple artists
        if (name.contains("&") || name.contains(",")) {
            // Split by & or , and add each
            name.split("&", ",").map { it.trim() }.filter { it.isNotBlank() }.forEach { result.add(it) }
        } else {
            result.add(name)
        }
    }
    return result.distinctBy { it.lowercase() }
}

val Innertube.SongItem.asSong: Song
    get() = Song (
        id = key,
        title = (if( explicit ) EXPLICIT_PREFIX else "").plus( info?.name ?: "" ),
        artistsText = authors.parseArtists().joinToString(", "),
        durationText = durationText,
        thumbnailUrl = thumbnail?.url
    )

val Innertube.VideoItem.asMediaItem: MediaItem
    @UnstableApi
    get() = MediaItem.Builder()
        .setMediaId(key)
        .setUri(key)
        .setCustomCacheKey(key)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(info?.name)
                .setArtist(authors.parseArtists().joinToString(", "))
                .setArtworkUri(thumbnail?.url?.toUri())
                .setExtras(
                    bundleOf(
                        "durationText" to durationText,
                        "artistNames" to ArrayList(authors.parseArtists()),
                        "artistIds" to ArrayList(authors?.mapNotNull { it.endpoint?.browseId } ?: emptyList()),
                        "isOfficialMusicVideo" to isOfficialMusicVideo,
                        "isUserGeneratedContent" to isUserGeneratedContent,
                        "isVideo" to true,
                        // "artistNames" to if (isOfficialMusicVideo) authors?.filter { it.endpoint != null }?.mapNotNull { it.name } else null,
                        // "artistIds" to if (isOfficialMusicVideo) authors?.mapNotNull { it.endpoint?.browseId } else null,
                    )
                )
                .build()
        )
        .build()


val Song.asMediaItem: MediaItem
    @UnstableApi
    get() {
        val cleanedTitle = if (title.hasExplicitPrefix()) {
            "\uD83C\uDD74 " + cleanPrefix(title)
        } else {
            cleanPrefix(title)
        }
        val cleanedArtist = cleanPrefix(artistsText ?: "")
        val cleanedThumbnail = thumbnailUrl?.let { cleanPrefix(it) }
        val artworkUri = cleanedThumbnail?.thumbnail(1200)?.toUri() ?: AutoMediaItemMapper.drawableUri(appContext(), R.drawable.ic_launcher_box)

        val safeTitle = if (cleanedTitle == "null") "" else cleanedTitle
        val safeArtist = if (cleanedArtist == "null") "" else cleanedArtist

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(safeTitle)
            .setArtist(safeArtist)
            .setArtworkUri(artworkUri)
            .setExtras(
                bundleOf(
                    "durationText" to durationText,
                    EXPLICIT_BUNDLE_TAG to title.hasExplicitPrefix(),
                    EXTRAS_KEY_IS_EXPLICIT to title.hasExplicitPrefix(),
                    "isCoverModified" to (thumbnailUrl?.startsWith(MODIFIED_PREFIX, true) == true),
                    "isTitleModified" to (title.startsWith(MODIFIED_PREFIX, true) == true),
                    "isArtistModified" to (artistsText?.startsWith(MODIFIED_PREFIX, true) == true)
                )
            )
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)

        // For local file:// URIs, set content:// via ArtworkContentProvider + artworkData for AA
        if (artworkUri.scheme == ContentResolver.SCHEME_FILE) {
            try {
                val path = artworkUri.path
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) {
                        // Set content:// URI via exported ArtworkContentProvider (AA requires exported)
                        val contentUri = Uri.Builder()
                            .scheme(ContentResolver.SCHEME_CONTENT)
                            .authority(ArtworkContentProvider.AUTHORITY)
                            .appendPath(id)
                            .build()
                        metadataBuilder.setArtworkUri(contentUri)
                        // artworkData for AA (queue, list, player)
                        var bitmap = BitmapFactory.decodeFile(path)
                        if (bitmap != null) {
                            // Apply EXIF rotation
                            try {
                                val exif = ExifInterface(path)
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
                            val stream = ByteArrayOutputStream()
                            cropped.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                            cropped.recycle()
                            metadataBuilder.setArtworkData(stream.toByteArray(), MediaMetadata.PICTURE_TYPE_ILLUSTRATION)
                        }
                    } else {
                        metadataBuilder.setArtworkUri(AutoMediaItemMapper.drawableUri(appContext(), R.drawable.ic_launcher_box))
                    }
                }
            } catch (_: Exception) {}
        } else if (artworkUri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                appContext().contentResolver.openInputStream(artworkUri)?.close()
            } catch (e: Exception) {
                metadataBuilder.setArtworkUri(AutoMediaItemMapper.drawableUri(appContext(), R.drawable.ic_launcher_box))
            }
        }

        return MediaItem.Builder()
            .setMediaMetadata(metadataBuilder.build())
            .setMediaId(id)
            .setUri(
                if (isLocal) ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id.substringAfter(LOCAL_KEY_PREFIX).toLong()
                ) else id.toUri()
            )
            .setCustomCacheKey(id)
            .build()
    }

val Innertube.VideoItem.asSong: Song
    get() = Song (
        id = key,
        title = info?.name ?: "",
        artistsText = authors.parseArtists().joinToString(", "),
        durationText = durationText,
        thumbnailUrl = thumbnail?.url
    )

val MediaItem.asSong: Song
    get() = Song (
        id = mediaId.split("/").lastOrNull() ?: mediaId,
        title = (if (mediaMetadata.extras?.getBoolean("isTitleModified") == true) MODIFIED_PREFIX else "") + (if (isExplicit) EXPLICIT_PREFIX else "") + (mediaMetadata.title?.toString()?.let { if (it == "null") "" else it } ?: ""),
        artistsText = (if (mediaMetadata.extras?.getBoolean("isArtistModified") == true) MODIFIED_PREFIX else "") + (mediaMetadata.artist?.toString()?.let { if (it == "null") "" else it }?.stripExplicitEmoji() ?: ""),
        durationText = mediaMetadata.extras?.getString("durationText"),
        thumbnailUrl = mediaMetadata.artworkUri?.toString()?.let { artworkStr ->
            if (artworkStr != "null") {
                (if (mediaMetadata.extras?.getBoolean("isCoverModified") == true) MODIFIED_PREFIX else "") + artworkStr
            } else {
                null
            }
        }
    )

val MediaItem.cleaned: MediaItem
    get() {
        // Add more if needed
        val isTitleModified = mediaMetadata.title?.startsWith( MODIFIED_PREFIX )
        val isArtistModified = mediaMetadata.artist?.startsWith( MODIFIED_PREFIX )

        if( isTitleModified == false && isArtistModified == false )
            // Return as-is if no property is modified
            // Reduce conversion time significantly when
            // some (if not most) of media items are not modified.
            return this

        val newMetadata: MediaMetadata = mediaMetadata.buildUpon()
                                                      .setTitle( cleanPrefix(mediaMetadata.title.toString()) )
                                                      .setArtist( cleanPrefix(mediaMetadata.artist.toString()) )
                                                      .build()
        return buildUpon().setMediaMetadata( newMetadata ).build()
    }

val MediaItem.isVideo: Boolean
    get() = mediaMetadata.extras?.getBoolean("isVideo") == true

val MediaItem.isExplicit: Boolean
    get() {
        val isTitleContain = mediaMetadata.title?.startsWith( EXPLICIT_PREFIX, true )
        val isBundleContain = mediaMetadata.extras?.getBoolean( EXPLICIT_BUNDLE_TAG )
        val isStandardContain = mediaMetadata.extras?.getBoolean("androidx.media3.session.EXTRAS_KEY_IS_EXPLICIT")

        return isTitleContain == true || isBundleContain == true || isStandardContain == true
    }


fun formatAsDuration(millis: Long) = DateUtils.formatElapsedTime(millis / 1000).removePrefix("0")
fun durationToMillis(duration: String): Long {
    val parts = duration.split(":")
    if (parts.size == 3){
        val hours = parts[0].toLong()
        val minutes = parts[1].toLong()
        val seconds = parts[2].toLong()
        return hours * 3600000 + minutes * 60000 + seconds * 1000
    } else {
        val minutes = parts[0].toLong()
        val seconds = parts[1].toLong()
        return minutes * 60000 + seconds * 1000
    }
}

fun durationTextToMillis(duration: String): Long {
    return try {
        durationToMillis(duration)
    } catch (e: Exception) {
        0L
    }
}


fun formatAsTime(millis: Long): String {
    //if (millis == 0L) return ""
    val timePart1 = Duration.ofMillis(millis).toMinutes().minutes
    val timePart2 = Duration.ofMillis(millis).seconds % 60

    return "${timePart1} ${timePart2}s"
}

@JvmName("ResultInnertubeItemsPageCompleted")
suspend fun Result<Innertube.ItemsPage<Innertube.SongItem>?>.completed(
    maxDepth: Int =  Int.MAX_VALUE
): Result<Innertube.ItemsPage<Innertube.SongItem>?> = runCatching {
    val page = getOrThrow()
    val songs = page?.items.orEmpty().toMutableList()
    var continuation = page?.continuation

    var depth = 0
    var continuationsList = arrayOf<String>()
    //continuationsList += continuation.orEmpty()

    Timber.tag("Utils").d("mediaItem playlist completed() continuation? $continuation")

    while (continuation != null && depth++ < maxDepth) {
        val newSongs = Innertube
            .playlistPageContinuation(continuation = continuation)
            ?.getOrNull()
            ?.takeUnless { it.items.isNullOrEmpty() } ?: break

        newSongs.items?.let { songs += it.filter { it !in songs } }
        continuation = newSongs.continuation

        //Timber.d("mediaItem loop $depth continuation founded ${continuationsList.contains(continuation)} $continuation")
        if (continuationsList.contains(continuation)) break

        continuationsList += continuation.orEmpty()
        //Timber.d("mediaItem loop continuationList size ${continuationsList.size}")
    }

    page?.copy(items = songs, continuation = null)
}.onFailure { e -> Timber.tag("Utils").e(e, "Failed to complete items page") }

@JvmName("ResultInnertubePlaylistOrAlbumPageCompleted")
suspend fun Result<Innertube.PlaylistOrAlbumPage>.completed(
    maxDepth: Int =  Int.MAX_VALUE
): Result<Innertube.PlaylistOrAlbumPage> = runCatching {
    val page = getOrThrow()
    val songsPage = runCatching {
        page.songsPage
    }.onFailure {
        Timber.tag("Utils").d("Innertube songsPage PlaylistOrAlbumPage>.completed ${it.stackTraceToString()}")
    }
    val itemsPage = songsPage.completed(maxDepth).getOrThrow()
    page.copy(songsPage = itemsPage)
}.onFailure {
    Timber.tag("Utils").d("Innertube PlaylistOrAlbumPage>.completed ${it.stackTraceToString()}")
}

//@JvmName("completedPlaylist")
suspend fun Result<LibraryPage?>.completed(): Result<LibraryPage> = runCatching {
    val page = getOrThrow()
    val items = page?.items?.toMutableList()
    var continuation = page?.continuation
    while (continuation != null) {
        val continuationPage = Innertube.libraryContinuation(continuation).getOrNull()
        if (continuationPage != null)
            if (items != null) {
                items += continuationPage.items
            }

        continuation = continuationPage?.continuation
    }
    LibraryPage(
        items = items ?: emptyList(),
        continuation = page?.continuation
    )
}


@Composable
fun CheckAvailableNewVersion(
    onDismiss: () -> Unit,
    updateAvailable: (Boolean) -> Unit
) {
    var updatedProductName = ""
    var updatedVersionName = ""
    var updatedVersionCode = 0
    val file = File(LocalContext.current.filesDir, "RiMusicUpdatedVersionCode.ver")
    if (file.exists()) {
        val dataText = file.readText().substring(0, file.readText().length - 1).split("-")
        updatedVersionCode =
            try {
                dataText.first().toInt()
            } catch (e: Exception) {
                0
            }
        updatedVersionName = if(dataText.size == 3) dataText[1] else ""
        updatedProductName =  if(dataText.size == 3) dataText[2] else ""
    }

    if (updatedVersionCode > getVersionCode()) {
        //if (updatedVersionCode > BuildConfig.VERSION_CODE)
        NewVersionDialog(
            updatedVersionName = updatedVersionName,
            updatedVersionCode = updatedVersionCode,
            updatedProductName = updatedProductName,
            onDismiss = onDismiss
        )
        updateAvailable(true)
    } else {
        updateAvailable(false)
        onDismiss()
    }
}

fun isNetworkConnected(context: Context): Boolean = context.isNetworkConnected

@Composable
fun isNetworkAvailableComposable(): Boolean {
    val context = LocalContext.current
    return context.isNetworkAvailableComposable.value
}


@Composable
fun getVersionName(): String {
    val context = LocalContext.current
    try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return pInfo.versionName ?: ""
    } catch (e: PackageManager.NameNotFoundException) {
        Timber.tag("Utils").e(e, "Failed to get version name")
    }
    return ""
}
@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun getLongVersionCode(): Long {
    val context = LocalContext.current
    try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return pInfo.longVersionCode
    } catch (e: PackageManager.NameNotFoundException) {
        Timber.tag("Utils").e(e, "Failed to get long version code")
    }
    return 0L
}


@Composable
fun getVersionCode(): Int {
    val context = LocalContext.current
    try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return pInfo.versionCode
    } catch (e: PackageManager.NameNotFoundException) {
        Timber.tag("Utils").e(e, "Failed to get version code")
    }
    return 0
}


inline val isAtLeastAndroid6
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

inline val isAtLeastAndroid7
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

inline val isAtLeastAndroid8
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

inline val isAtLeastAndroid81
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1

inline val isAtLeastAndroid10
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

inline val isAtLeastAndroid11
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

inline val isAtLeastAndroid12
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

inline val isAtLeastAndroid13
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

inline val isAtLeastAndroid14
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

fun Modifier.conditional(condition : Boolean, modifier : Modifier.() -> Modifier) : Modifier {
    return if (condition) {
        then(modifier(Modifier))
    } else {
        this
    }
}

suspend fun downloadSyncedLyrics( song: Song ) {
    val storedLyrics = Database.lyricsTable.findBySongIdAndType( song.id, LyricsType.Synced.name ).first()
    if( storedLyrics?.data != null ) return

    var fetchedLyrics: Lyrics? = null
    LrcLib.lyrics(
        artist = song.cleanArtistsText(),
        title = song.cleanTitle(),
        duration = durationTextToMillis( song.durationText.orEmpty() ).milliseconds
    )?.onSuccess {
        fetchedLyrics = Lyrics(
            songId = song.id,
            type = LyricsType.Synced.name,
            data = it?.text.orEmpty()
        )
    }?.onFailure {
        // Try out different source for lyrics
        KuGou.lyrics(
            artist = song.cleanArtistsText(),
            title = song.cleanTitle(),
            duration = durationTextToMillis( song.durationText.orEmpty() ) / 1000
        )?.onSuccess {
            fetchedLyrics = Lyrics(
                songId = song.id,
                type = LyricsType.Synced.name,
                data = it?.value.orEmpty()
            )
        }
    }

    if( fetchedLyrics != null )
        Database.asyncTransaction {
            lyricsTable.upsert( fetchedLyrics!! )
        }
}

suspend fun addToYtPlaylist(localPlaylistId: Long, position: Int, ytplaylistId: String, mediaItems: List<MediaItem>){
    val mediaItemsChunks = mediaItems.chunked(50)
    mediaItemsChunks.forEachIndexed { index, items ->
        if (mediaItems.size <= 50) {}
        else if (index == 0) {
            Toaster.i(
                "${mediaItems.size} "+appContext().resources.getString(R.string.songs_adding_in_yt)
            )
        } else {
            delay(2000)
        }
        PlaylistEditThrottle.throttle(ytplaylistId)
        addToPlaylist(ytplaylistId, items.map { it.mediaId })
            .onSuccess {
                Database.playlistTable
                        .findById( localPlaylistId )
                        .first()
                        ?.let {
                            Database.mapIgnore( it, *items.toTypedArray() )
                        }
                if (items.size == 50)
                    Toaster.i(R.string.songs_remaining, formatArgs = arrayOf((mediaItems.size - (index + 1) * 50).toString()))
            }
            .onFailure {
                Timber.tag("Utils").e("YtMusic addToPlaylist (list of size ${items.size}) error: ${it.stackTraceToString()}")
                if(it is ClientRequestException && it.response.status == HttpStatusCode.BadRequest) {
                    Toaster.w( R.string.adding_yt_to_pl_failed )
                    items.forEach { item ->
                        delay(500)
                        PlaylistEditThrottle.throttle(ytplaylistId)
                        addToPlaylist(ytplaylistId, item.mediaId).onFailure {
                            Timber.tag("Utils").e("YtMusic addToPlaylist (list insert backup) error: ${it.stackTraceToString()}")
                                Toaster.e(
                                    appContext().resources.getString(R.string.songs_add_yt_failed)+"${item.mediaMetadata.title} - ${item.mediaMetadata.artist}"
                                )
                        }.onSuccess {
                            Database.playlistTable
                                    .findById( localPlaylistId )
                                    .first()
                                    ?.let { playlist ->
                                        Database.mapIgnore( playlist, *items.toTypedArray() )
                                    }
                            Toaster.n( appContext().resources.getString(R.string.songs_remaining, items.size - (index + 1)) )
                        }
                    }
                }
            }
    }

    Toaster.n(
        "${mediaItems.size} "+ appContext().resources.getString(R.string.songs_added_in_yt)
    )
}

suspend fun addSongToYtPlaylist(localPlaylistId: Long, position: Int, ytplaylistId: String, mediaItem: MediaItem){
    if (isYouTubeSyncEnabled() && canPushToYTM() && isNetworkConnected(appContext())) {
        PlaylistEditThrottle.throttle(ytplaylistId)
        addToPlaylist(ytplaylistId,mediaItem.mediaId)
            .onSuccess {
                Database.playlistTable.findById( localPlaylistId ).first()?.let {
                    Database.mapIgnore( it, mediaItem )
                }
                Toaster.s( R.string.songs_add_yt_success )
            }
            .onFailure {
                Toaster.e( R.string.songs_add_yt_failed )
            }

    }
}


@OptIn(UnstableApi::class)
suspend fun addToYtLikedSongs(mediaItems: List<MediaItem>){
    if( !isYouTubeSyncEnabled() ) return

    mediaItems.forEachIndexed { index, item ->
        delay(1000)

        likeVideoOrSong( item.mediaId )
            .onSuccess {
                Database.asyncTransaction {
                    insertIgnore( item )
                    songTable.likeState( item.mediaId, true )
                    MyDownloadHelper.autoDownloadWhenLiked(
                        context(),
                        item
                    )

                    Toaster.s(
                        "${index + 1}/${mediaItems.size} " + appContext().resources.getString(R.string.songs_liked_yt)
                    )
                }
            }
            .onFailure {
                Toaster.e(R.string.songs_liked_yt_failed_with_index, formatArgs = arrayOf("${index + 1}/${mediaItems.size}", appContext().resources.getString(R.string.songs_liked_yt_failed)))
            }
    }
}

