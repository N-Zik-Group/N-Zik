package app.n_zik.android.components.settings.appearance.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.utils.saveLyricsStateKey
import app.n_zik.android.utils.showLyricsStateKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class ThemePreviewDemoTest {
    @Test
    fun previewSeedsTheRememberedLyricsStateFromTheScene() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val appearance = mapOf(showLyricsStateKey to true)

        val lyrics = ThemePreviewContext.create(app, appearance, ThemePreviewScene.LYRICS).previewPreferences
        assertTrue(lyrics.getBoolean(showLyricsStateKey, false))
        assertTrue(lyrics.getBoolean(saveLyricsStateKey, false))

        val player = ThemePreviewContext.create(app, appearance, ThemePreviewScene.PLAYER).previewPreferences
        assertTrue(player.getBoolean(showLyricsStateKey, false))
        assertFalse(player.getBoolean(saveLyricsStateKey, true))
    }

    @Test
    fun galleryScenesHaveStableUniqueNames() {
        assertEquals(
            listOf("player", "queue", "navigation", "home", "search", "search-albums", "search-artists",
                "album", "lyrics", "artist", "playlist", "local-playlist", "player-playing", "miniplayer-playing"),
            ThemePreviewScene.entries.map { it.fileName },
        )
    }

    @Test
    fun demoLibraryUsesItsOwnArtistsPlaylistsAndCovers() {
        val albums = ThemePreviewDemo.albums
        assertEquals(albums.map { "${it.id}-artist" }, ThemePreviewDemo.artists.map { it.id })
        assertEquals(albums.map { it.authorsText }, ThemePreviewDemo.artists.map { it.name })
        assertEquals(albums.map { ThemePreviewDemo.songsForAlbum(it.id).size },
            ThemePreviewDemo.playlists.map { it.songCount })
        assertTrue(ThemePreviewDemo.playlists.all { it.playlist.id < 0 })
        assertEquals(albums.map { it.thumbnailUrl },
            ThemePreviewDemo.playlists.map { ThemePreviewDemo.playlistCover(it.playlist.id) })
    }

    @Test
    fun queueAndSearchOnlyUseBundledSongs() {
        val tracks = ThemePreviewDemo.albums.flatMap { ThemePreviewDemo.songsForAlbum(it.id) }
        assertEquals(tracks.size, tracks.distinctBy { it.id }.size)
        assertTrue(ThemePreviewDemo.queue.all { it in tracks })
        assertTrue(tracks.count { it.title.contains(ThemePreviewDemo.searchQuery, ignoreCase = true) } >= 3)
        assertEquals("Maps of Morning", ThemePreviewDemo.queue.first().title)
        assertTrue(ThemePreviewDemo.positionMs in 1 until ThemePreviewDemo.durationMs)
    }
}
