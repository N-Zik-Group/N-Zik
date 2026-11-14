package app.n_zik.android.components.settings.appearance.preview

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.n_zik.android.models.Album
import app.n_zik.android.models.Song
import app.n_zik.android.utils.asMediaItem

internal object ThemePreviewDemo {
    const val positionMs = 68_000L
    const val durationMs = 300_000L
    const val searchQuery = "tide"
    const val firstAlbumId = "theme-preview-atlas"

    private fun cover(name: String) = "file:///android_asset/theme-preview/$name.jpg"

    val albums = listOf(
        Album(firstAlbumId, "Paper Atlas", cover("atlas"), "2025", "Mira Vale"),
        Album("theme-preview-signal", "Night Signal", cover("signal"), "2025", "Lantern Coast"),
        Album("theme-preview-tide", "Silver Tide", cover("tide"), "2026", "Iona Drift"),
        Album("theme-preview-violet", "Violet Hour", cover("violet"), "2026", "Orchard Echo"),
    )

    private val trackTitles = listOf(
        listOf("Maps of Morning", "Glass Harbor", "Fold the Horizon", "The Quiet North"),
        listOf("Night Signal", "Last Train Home", "City Without Clocks"),
        listOf("Silver Tide", "Tidepool Lanterns", "Low Tide Letters"),
        listOf("Violet Hour", "Orchard After Rain", "The Light We Leave"),
    )

    private val albumTracks = albums.zip(trackTitles).associate { (album, titles) ->
        album.id to titles.mapIndexed { index, title ->
            Song("${album.id}-$index", title, album.authorsText, "5:00", album.thumbnailUrl)
        }
    }

    val songs = albumTracks.values.flatten()
    val queue = listOf(songs[0], songs[4], songs[7], songs[10], songs[1], songs[5], songs[8], songs[11])

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
            .setUri(Uri.parse("asset:///theme-preview/silence.mp3"))
            .setMediaMetadata(metadata)
            .build()
    }

    val lyrics = """
        [00:43.00]Fold the map along the river
        [00:49.00]Leave the names out in the rain
        [00:55.00]Every road remembers daylight
        [01:01.00]Every window holds a flame
        [01:07.00]If the morning finds us wandering
        [01:13.00]We can follow where it goes
        [01:19.00]Through the towns that never knew us
        [01:25.00]To the coast the sparrow knows
        [01:31.00]Write a letter to the weather
        [01:37.00]Leave it underneath the stone
    """.trimIndent()
}

internal enum class ThemePreviewScene(val fileName: String, val searchTab: Int? = null) {
    PLAYER("player"), QUEUE("queue"), NAVIGATION("navigation"), HOME("home"),
    SEARCH("search", 0), SEARCH_ALBUMS("search-albums", 1), SEARCH_ARTISTS("search-artists", 2),
    ALBUM("album"), LYRICS("lyrics"), ARTIST("artist"), PLAYLIST("playlist"), LOCAL_PLAYLIST("local-playlist");
}

internal class ThemePreviewSession(
    val scene: ThemePreviewScene,
    val player: Player,
)

internal val LocalThemePreview = staticCompositionLocalOf<ThemePreviewSession?> { null }

internal class ThemePreviewContext(base: Context, val previewPreferences: SharedPreferences) :
    ContextWrapper(base) {
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
        previewPreferences

    companion object {
        fun create(base: Context, values: Map<String, Any>): ThemePreviewContext {
            val preferences = base.getSharedPreferences("theme-preview-session", Context.MODE_PRIVATE)
            preferences.edit(commit = true) {
                clear()
                values.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Float -> putFloat(key, value)
                        is String -> putString(key, value)
                        else -> error("Invalid preview setting $key")
                    }
                }
            }
            return ThemePreviewContext(base, preferences)
        }
    }
}
