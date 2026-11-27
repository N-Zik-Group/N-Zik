package app.n_zik.android.utils

import app.n_zik.android.appTextResources
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import android.R.attr.duration
import app.n_zik.android.core.database.artistEntryNames
import app.n_zik.innertube.models.ArtistConjunctions


import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import app.n_zik.android.core.database.ArtistMappingReconcile
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.core.network.utils.isNetworkAvailable
import app.n_zik.android.core.network.utils.isNetworkConnected
import app.n_zik.android.utils.canPushToYTM
import app.n_zik.android.utils.PlaylistEditThrottle
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
import app.n_zik.innertube.requests.LibraryPage
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.YtMusic.addToPlaylist
import app.n_zik.innertube.requests.playlistPage
import app.n_zik.innertube.requests.playlistPageContinuation
import app.n_zik.kugou.KuGou
import app.n_zik.lrclib.LrcLib
import androidx.media3.session.MediaConstants.EXTRAS_KEY_IS_EXPLICIT
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.podcastSourceBrowseId
import app.n_zik.android.core.database.PlaylistAddResult
import app.n_zik.android.core.database.PlaylistLinkKind
import app.n_zik.android.core.database.addTracksToStoredPlaylist
import app.n_zik.android.core.database.canEditPlaylistMembership
import app.n_zik.android.core.database.canAddTracksToPlaylist
import app.n_zik.android.core.database.playlistLinkKind
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.hasExplicitPrefix
import app.n_zik.android.appContext
import app.n_zik.android.cleanPrefix
import app.n_zik.android.context
import app.n_zik.android.models.Album
import app.n_zik.android.models.Lyrics
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.Song
import app.n_zik.android.stripExplicitEmoji
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.n_zik.android.core.utils.Toaster
import kotlin.time.Duration.Companion.milliseconds
import timber.log.Timber
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper

const val EXPLICIT_BUNDLE_TAG = "is_explicit"

private fun Bundle.putTrackArtists(authors: List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>>?) {
    val entries = ArtistMappingReconcile.parseAuthorEntries(authors, includeIdOnly = true)
    val linked = entries.filterNot { it.second.isNullOrBlank() }
    putStringArrayList("artistNames", ArrayList(entries.mapNotNull { it.first.takeIf(String::isNotBlank) }))
    putStringArrayList("artistIds", ArrayList(linked.mapNotNull { it.second }))
    putStringArrayList(ARTIST_LINKED_NAMES_EXTRA, ArrayList(linked.map { it.first }))
}

val Innertube.AlbumItem.asAlbum: Album
    get() = Album (
        id = key,
        title = info?.name,
        thumbnailUrl = thumbnail?.url,
        year = year,
        // One YTM author entry = one artist: keep entry names whole (no
        // parseArtists split) so the display matches the stored row name.
        authorsText = authors.artistEntryNames().joinToString(", "),
        //shareUrl =
    )

private const val PODCAST_BROWSE_ID_EXTRA = "podcastBrowseId"

internal val MediaItem.podcastBrowseId: String?
    get() = podcastSourceBrowseId(mediaMetadata.extras?.getString(PODCAST_BROWSE_ID_EXTRA))

/** Carry an exact source across the conversion from stored playlist rows to playback items. */
internal fun MediaItem.withPodcastSource(browseId: String?): MediaItem {
    val source = podcastSourceBrowseId(browseId) ?: return this
    val metadata = mediaMetadata
    val extras = (metadata.extras?.let(::Bundle) ?: Bundle()).apply {
        putString(PODCAST_BROWSE_ID_EXTRA, source)
    }
    return buildUpon().setMediaMetadata(metadata.buildUpon().setExtras(extras).build()).build()
}

@UnstableApi
internal fun Innertube.Podcast.EpisodeItem.asPodcastMediaItem(browseId: String): MediaItem =
    MediaItem.Builder()
        .setMediaId(videoId)
        .setUri(videoId)
        .setCustomCacheKey(videoId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(author)
                .setArtworkUri(thumbnail.lastOrNull()?.url?.thumbnail(1200)?.toUri())
                .setExtras(
                    bundleOf(
                        PODCAST_BROWSE_ID_EXTRA to browseId,
                        "durationText" to durationString,
                        "artistNames" to author?.takeIf(String::isNotBlank)?.let { arrayListOf(it) },
                        EXTRAS_KEY_IS_EXPLICIT to false,
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
                .setArtist(authors.artistEntryNames().joinToString(", "))
                .setAlbumTitle(album?.name)
                .setArtworkUri(thumbnail?.url?.toUri())
                .setExtras(
                    bundleOf(
                        "albumId" to album?.endpoint?.browseId,
                        "durationText" to durationText,
                        EXPLICIT_BUNDLE_TAG to explicit,
                        EXTRAS_KEY_IS_EXPLICIT to explicit,
                        "setVideoId" to setVideoId,
                    ).apply { putTrackArtists(authors) }
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
    
    val conjunctions = ArtistConjunctions.conjunctions
    val conjunctionPattern = if (conjunctions.isNotEmpty()) {
        Regex(" (${conjunctions.joinToString("|") { Regex.escape(it) }}) | & | , ", RegexOption.IGNORE_CASE)
    } else {
        Regex(" & | , ", RegexOption.IGNORE_CASE)
    }
    
    val result = mutableListOf<String>()
    for (author in this) {
        val name = author?.name ?: continue
        val trimmed = name.trim().replace('\u00a0', ' ')
        // Skip pure separators
        if (trimmed.matches(Regex("([,&])"))) continue
        // Skip standalone conjunction words
        if (conjunctions.any { trimmed.equals(it, ignoreCase = true) }) continue
        // Skip standalone bullet
        if (trimmed == "•") continue
        // Skip view/play/subscriber/likes count patterns
        if (Regex("\\d[\\d.,]*[kmb]?\\s*(?:views?|plays?|likes?|subscribers?|monthly audience)", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) continue
        // Skip duration patterns like "45:38" or "1:23:45"
        if (Regex("^\\d{1,2}[:.,]\\d{2}([:. ,]\\d{2})?$").matches(trimmed)) continue
        // Skip year patterns like "2024"
        if (Regex("^(?:19|20)\\d{2}$").matches(trimmed)) continue
        // Skip type labels
        if (trimmed.lowercase() in setOf("song", "video", "single", "album", "episode", "playlist", "podcast")) continue
        // Check if this name contains multiple artists
        if (name.contains(conjunctionPattern)) {
            name.split(conjunctionPattern).map { it.trim() }.filter { it.isNotBlank() }.forEach { result.add(it) }
        } else {
            result.add(name)
        }
    }
    return result.distinctBy { it.lowercase() }
}

/**
 * Split a stored artistsText string into individual artist names.
 * Handles ",", "&", and locale-aware conjunctions (e.g. "et", "and").
 *
 * The localized conjunction word is matched as a whole word (\b boundaries):
 * it may appear as a substring inside a name ("and" inside "Grand", "et"
 * inside "Petit"), and a boundary-less split destroys legitimate names into
 * junk fragments (device capture 2026-09-26: "Grand Corps Malade" ->
 * ["G", "r", "Corps Malade"]). "&" and "," are delimiters, not words, and
 * stay boundary-less.
 */
fun String?.splitArtistNames(): List<String> {
    if (this.isNullOrBlank()) return emptyList()
    val conjunctions = ArtistConjunctions.conjunctions
    val pattern = if (conjunctions.isNotEmpty()) {
        Regex("\\s*(\\b(?:" + conjunctions.joinToString("|") { Regex.escape(it) } + ")\\b|&|,)\\s*", RegexOption.IGNORE_CASE)
    } else {
        Regex("\\s*(&|,)\\s*", RegexOption.IGNORE_CASE)
    }
    return this.split(pattern).map { it.trim() }.filter { it.isNotBlank() }
}

val Innertube.SongItem.asSong: Song
    get() = Song (
        id = key,
        title = (if( explicit ) EXPLICIT_PREFIX else "").plus( info?.name ?: "" ),
        // One YTM author entry = one artist: keep entry names whole (no
        // parseArtists split) so the live display matches the stored row name.
        artistsText = authors.artistEntryNames().joinToString(", "),
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
                .setArtist(authors.artistEntryNames().joinToString(", "))
                .setArtworkUri(thumbnail?.url?.toUri())
                .setExtras(
                    bundleOf(
                        "durationText" to durationText,
                        "isOfficialMusicVideo" to isOfficialMusicVideo,
                        "isUserGeneratedContent" to isUserGeneratedContent,
                        "isVideo" to true,
                    ).apply { putTrackArtists(authors) }
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

        // Keep canonical artwork for in-app rendering. The media session publishes its own bounded payload.

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
        // One YTM author entry = one artist: keep entry names whole (no
        // parseArtists split) so the live display matches the stored row name.
        artistsText = authors.artistEntryNames().joinToString(", "),
        durationText = durationText,
        thumbnailUrl = thumbnail?.url
    )

internal val MediaItem.normalizedSongId: String
    get() = mediaId.substringAfterLast('/')

val MediaItem.asSong: Song
    get() = Song (
        id = normalizedSongId,
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

    val lyrics = fetchedLyrics ?: return
    Database.asyncTransaction {
        lyricsTable.upsert(lyrics)
    }
}

private const val MAX_YOUTUBE_PLAYLIST_SONGS = 5_000
private val playlistAddMutex = Mutex()

internal fun shouldPushPlaylistChanges(syncEnabled: Boolean, canPush: Boolean, pushPlaylists: Boolean): Boolean =
    syncEnabled && canPush && pushPlaylists

private fun playlistPushAllowed(context: Context) = shouldPushPlaylistChanges(
    isYouTubeSyncEnabled(), canPushToYTM(), context.preferences.getBoolean(syncPushPlaylistKey, false),
)

/** Returns null after reporting a partial or failed remote edit. */
internal suspend fun addPlaylistItemsFromMenu(
    confirmedPlaylist: Playlist,
    mediaItems: List<MediaItem>,
    localDestinationsOnly: Boolean = false,
): PlaylistAddResult? {
    if (mediaItems.isEmpty()) return PlaylistAddResult.Empty
    val context = appContext()
    val owner = ProfileWorkOwner.capture(context) ?: return PlaylistAddResult.Unavailable
    val playlistId = confirmedPlaylist.id
    return playlistAddMutex.withLock {
        if (!ProfileWorkOwner.isBound(context, owner)) return@withLock PlaylistAddResult.Unavailable
        val playlist = Database.playlistTable.findById(playlistId).first() ?: return@withLock PlaylistAddResult.Unavailable
        val browseId = playlist.browseId
        if (browseId != confirmedPlaylist.browseId || playlist.isYoutubePlaylist != confirmedPlaylist.isYoutubePlaylist) {
            return@withLock PlaylistAddResult.Unavailable
        }
        if (!canAddTracksToPlaylist(playlist, localDestinationsOnly || mediaItems.any(MediaItem::isLocal))) {
            return@withLock PlaylistAddResult.Unavailable
        }
        val items = mediaItems.distinctBy { it.asSong.id }

        suspend fun store(itemsToAdd: List<MediaItem>): PlaylistAddResult = Database.awaitWriteBatch {
            var result = PlaylistAddResult.Unavailable
            transaction {
                if (!ProfileWorkOwner.isBound(context, owner)) return@transaction
                val latest = playlistTable.findById(playlistId).first() ?: return@transaction
                if (latest.browseId != browseId || latest.isYoutubePlaylist != playlist.isYoutubePlaylist) return@transaction
                result = addTracksToStoredPlaylist(playlistId, itemsToAdd.map(MediaItem::asSong), localDestinationsOnly)
                if (result == PlaylistAddResult.Added) itemsToAdd.forEach { insertIgnore(it) }
            }
            result
        }

        val remoteId = browseId?.takeIf {
            playlist.isYoutubePlaylist && !it.startsWith(MODIFIED_PREFIX) &&
                playlistLinkKind(it) == PlaylistLinkKind.YouTubePlaylist
        }?.removePrefix("VL")
        if (remoteId == null || !playlistPushAllowed(context)) return@withLock store(items)

        val existingIds = Database.songPlaylistMapTable.songIdsInPlaylist(playlistId).toHashSet()
        val pending = items.filterNot { it.asSong.id in existingIds }
        if (pending.isEmpty()) return@withLock PlaylistAddResult.AlreadyPresent
        if (pending.size > MAX_YOUTUBE_PLAYLIST_SONGS - existingIds.size) {
            Toaster.e(R.string.playlist_youtube_song_limit_message)
            return@withLock null
        }
        var confirmedCount = 0
        val resources = appTextResources()
        val progressInterval = 50
        if (pending.size > progressInterval) {
            Toaster.i(resources.getQuantityString(R.plurals.sync_youtube_playlist_songs_adding, pending.size, pending.size))
        }
        for ((index, item) in pending.withIndex()) {
            PlaylistEditThrottle.throttle(remoteId)
            val current = Database.playlistTable.findById(playlistId).first() ?: break
            if (!ProfileWorkOwner.isBound(context, owner) || !playlistPushAllowed(context) ||
                !isNetworkConnected(context) || current.browseId != browseId ||
                current.isYoutubePlaylist != playlist.isYoutubePlaylist || !canEditPlaylistMembership(current)) break
            val error = addToPlaylist(remoteId, item.asSong.id).exceptionOrNull()
            if (error is CancellationException) throw error
            if (error == null) {
                val stored = store(listOf(item))
                if (stored == PlaylistAddResult.Unavailable) break
                confirmedCount++
            } else {
                Timber.tag("Utils").w(error, "Failed to add a song to the YouTube playlist")
            }
            if ((index + 1) % progressInterval == 0 && index < pending.lastIndex) {
                Toaster.i(R.string.sync_youtube_songs_remaining, formatArgs = arrayOf((pending.size - index - 1).toString()))
            }
        }
        if (confirmedCount != pending.size) {
            if (confirmedCount > 0) {
                Toaster.n(resources.getQuantityString(R.plurals.sync_youtube_playlist_songs_added, confirmedCount, confirmedCount))
            }
            Toaster.e(R.string.sync_youtube_playlist_add_failed)
            null
        } else PlaylistAddResult.Added
    }
}

suspend fun addToYtPlaylist(localPlaylistId: Long, position: Int, ytplaylistId: String, mediaItems: List<MediaItem>) {
    val confirmed = Database.playlistTable.findById(localPlaylistId).first()
        ?.takeIf { it.browseId?.let(::cleanPrefix) == cleanPrefix(ytplaylistId) }
    val result = if (confirmed != null) addPlaylistItemsFromMenu(confirmed, mediaItems)
        else PlaylistAddResult.Unavailable
    when (result) {
        PlaylistAddResult.Added -> Toaster.done()
        PlaylistAddResult.AlreadyPresent -> Toaster.i(R.string.playlist_contains_song)
        PlaylistAddResult.Empty -> Toaster.w(R.string.player_no_song_found)
        PlaylistAddResult.Unavailable -> Toaster.w(R.string.playlist_add_destination_unavailable)
        null -> Unit
    }
}
