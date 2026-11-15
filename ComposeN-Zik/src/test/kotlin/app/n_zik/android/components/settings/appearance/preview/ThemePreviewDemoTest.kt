package app.n_zik.android.components.settings.appearance.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class ThemePreviewDemoTest {
    @Test
    fun galleryScenesHaveStableUniqueNames() {
        assertEquals(
            listOf("player", "queue", "navigation", "home", "search", "search-albums", "search-artists",
                "album", "lyrics", "artist", "playlist", "local-playlist", "player-playing", "miniplayer-playing"),
            ThemePreviewScene.entries.map { it.fileName },
        )
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
