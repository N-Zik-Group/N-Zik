package app.n_zik.android.components.settings.appearance.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreviewDemoTest {
    @Test
    fun galleryScenesHaveStableUniqueNames() {
        assertEquals(
            listOf("player", "queue", "navigation", "home", "search", "search-albums", "search-artists",
                "album", "lyrics", "artist", "playlist", "local-playlist"),
            ThemePreviewScene.entries.map { it.fileName },
        )
    }

    @Test
    fun queueAndSearchOnlyUseBundledSongs() {
        val tracks = ThemePreviewDemo.albums.flatMap { ThemePreviewDemo.songsForAlbum(it.id) }
        assertEquals(tracks.size, tracks.distinctBy { it.id }.size)
        assertTrue(ThemePreviewDemo.queue.all { it in tracks })
        assertTrue(tracks.all { it.thumbnailUrl?.startsWith("file:///android_asset/theme-preview/") == true })
        assertTrue(tracks.count { it.title.contains(ThemePreviewDemo.searchQuery, ignoreCase = true) } >= 3)
        assertEquals("Maps of Morning", ThemePreviewDemo.queue.first().title)
        assertTrue(ThemePreviewDemo.positionMs in 1 until ThemePreviewDemo.durationMs)
    }
}
