package app.n_zik.android.components.settings.appearance.preview

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.models.Lyrics
import app.n_zik.android.models.Song
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.saveLyricsStateKey

internal object ThemePreviewDemo {
    const val positionMs = 68_000L
    const val durationMs = 300_000L
    const val searchQuery = "tide"
    const val firstAlbumId = "theme-preview-atlas"

    private var assetDirectory: java.io.File? = null

    fun useAssets(context: Context) {
        assetDirectory = ThemePreviewAssets.file(context, "atlas.jpg").parentFile
    }

    private fun cover(name: String) = java.io.File(assetDirectory, "$name.jpg").toURI().toString()

    val albums by lazy { listOf(
        Album(firstAlbumId, "Paper Atlas", cover("atlas"), "2025", "Mira Vale"),
        Album("theme-preview-signal", "Night Signal", cover("signal"), "2025", "Lantern Coast"),
        Album("theme-preview-tide", "Silver Tide", cover("tide"), "2026", "Iona Drift"),
        Album("theme-preview-violet", "Violet Hour", cover("violet"), "2026", "Orchard Echo"),
        Album("theme-preview-ember", "Ember Lines", cover("ember"), "2026", "Sable Loop"),
    ) }

    private val trackTitles = listOf(
        listOf("Maps of Morning", "Glass Harbor", "Fold the Horizon", "The Quiet North"),
        listOf("Night Signal", "Last Train Home", "City Without Clocks"),
        listOf("Silver Tide", "Tidepool Lanterns", "Low Tide Letters"),
        listOf("Violet Hour", "Orchard After Rain", "The Light We Leave"),
        listOf("Ember Lines", "After the Glow", "Last Light Station"),
    )

    private val albumTracks by lazy { albums.zip(trackTitles).associate { (album, titles) ->
        album.id to titles.mapIndexed { index, title ->
            Song("${album.id}-$index", title, album.authorsText, "5:00", album.thumbnailUrl)
        }
    } }

    val artists by lazy { albums.map { album ->
        Artist("${album.id}-artist", album.authorsText, album.thumbnailUrl)
    } }
    val playlists by lazy { albums.mapIndexed { index, album ->
        PlaylistPreview(Playlist(id = -1L - index, name = "${album.title} Mix"), songsForAlbum(album.id).size)
    } }
    fun playlistCover(id: Long): String? =
        playlists.indexOfFirst { it.playlist.id == id }.let { albums.getOrNull(it)?.thumbnailUrl }

    val songs by lazy { albumTracks.values.flatten() }
    val queue by lazy { listOf(songs[0], songs[4], songs[7], songs[10], songs[1], songs[5], songs[8], songs[11], songs[13]) }

    fun songsForAlbum(id: String): List<Song> = albumTracks[id].orEmpty()

    val mediaItems: List<MediaItem> get() = queue.map { song ->
        val album = albums.first { song in songsForAlbum(it.id) }
        val extras = Bundle(song.asMediaItem.mediaMetadata.extras ?: Bundle.EMPTY).apply {
            putString("albumId", album.id)
            putStringArrayList("artistIds", arrayListOf("${album.id}-artist"))
            putStringArrayList("artistNames", arrayListOf(album.authorsText.orEmpty()))
        }
        val metadata = song.asMediaItem.mediaMetadata.buildUpon()
            .setExtras(extras)
            .setAlbumTitle(album.title)
            .setArtworkUri(Uri.parse(album.thumbnailUrl))
            .build()
        MediaItem.Builder().setMediaId(song.id)
            .setUri(Uri.fromFile(java.io.File(checkNotNull(assetDirectory), "silence.mp3")))
            .setMediaMetadata(metadata)
            .build()
    }

    private val lyricLines = listOf(
        "Fold the map along the river",
        "Leave the names out in the rain",
        "Every road remembers daylight",
        "Every window holds a flame",
        "If the morning finds us wandering",
        "We can follow where it goes",
        "Through the towns that never knew us",
        "To the coast the sparrow knows",
        "Write a letter to the weather",
        "Leave it underneath the stone",
    )

    private val syncedLyrics = timedLyrics(withWordTimings = false)
    private val karaokeLyrics = timedLyrics(withWordTimings = true)

    fun lyrics(mediaId: String, mode: LyricsType): Lyrics = when (mode) {
        LyricsType.Unsynced -> Lyrics(mediaId, LyricsType.Unsynced.name, lyricLines.joinToString("\n"))
        LyricsType.Karaoke -> Lyrics(mediaId, LyricsType.Karaoke.name, karaokeLyrics)
        LyricsType.Synced, LyricsType.Auto -> Lyrics(mediaId, LyricsType.Synced.name, syncedLyrics)
    }

    private fun timedLyrics(withWordTimings: Boolean): String = buildString {
        lyricLines.forEachIndexed { lineIndex, line ->
            val startSecond = 43 + lineIndex * 6
            val minutes = (startSecond / 60).toString().padStart(2, '0')
            val seconds = (startSecond % 60).toString().padStart(2, '0')
            appendLine("[$minutes:$seconds.00]$line")
            if (withWordTimings) {
                val words = line.split(' ').mapIndexed { wordIndex, word ->
                    val start = startSecond + wordIndex * 0.5
                    "$word:$start:${start + 0.5}"
                }
                appendLine(words.joinToString("|", "<", ">"))
            }
        }
    }.trimEnd()
}

internal enum class ThemePreviewScene(val fileName: String, val searchTab: Int? = null, val playing: Boolean = false) {
    PLAYER("player"), QUEUE("queue"), NAVIGATION("navigation"), HOME("home"),
    SEARCH("search", 0), SEARCH_ALBUMS("search-albums", 1), SEARCH_ARTISTS("search-artists", 2),
    ALBUM("album"), LYRICS("lyrics"), ARTIST("artist"), PLAYLIST("playlist"), LOCAL_PLAYLIST("local-playlist"),
    PLAYER_PLAYING("player-playing", playing = true), MINIPLAYER_PLAYING("miniplayer-playing", playing = true);
}

internal class ThemePreviewSession(
    val scene: ThemePreviewScene,
    val player: Player,
) {
    val homeTab = mutableStateOf<String?>(null)
    val previewRowsPlaced = mutableStateOf(false)
}

internal val LocalThemePreview = staticCompositionLocalOf<ThemePreviewSession?> { null }

internal class ThemePreviewContext(base: Context, val previewPreferences: SharedPreferences) :
    ContextWrapper(base) {
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
        previewPreferences

    companion object {
        fun create(base: Context, values: Map<String, Any>, scene: ThemePreviewScene): ThemePreviewContext {
            val preferences = base.getSharedPreferences("theme-preview-session", Context.MODE_PRIVATE)
            preferences.edit(commit = true) {
                clear()
                // The player can restore the last overlay state when it opens.
                putBoolean(saveLyricsStateKey, scene == ThemePreviewScene.LYRICS)
                values.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Float -> putFloat(key, value)
                        is String -> putString(key, value)
                        UseThemeDefault -> Unit
                        else -> error("Invalid preview setting $key")
                    }
                }
            }
            return ThemePreviewContext(base, preferences)
        }
    }
}
