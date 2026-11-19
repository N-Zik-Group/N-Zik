package app.n_zik.android.components.ui.screens.home.quickpicks

import app.n_zik.innertube.Innertube
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YtmShelfCardTest {
    @Test
    fun `playlist channels and album subtitles determine visible skeleton rows`() {
        val playlist = Innertube.PlaylistItem(
            info = Innertube.Info("Pop Fresh", null), channel = null,
            songCount = null, isEditable = false, thumbnail = null,
        )
        val album = Innertube.AlbumItem(
            info = Innertube.Info("New release", null), authors = null,
            year = "Artist", thumbnail = null,
        )

        assertEquals(
            listOf(YtmShelfCard(YtmShelfPlaceholderKind.Playlist), YtmShelfCard(YtmShelfPlaceholderKind.Album, 1),
                YtmShelfCard(YtmShelfPlaceholderKind.Playlist, 1)),
            ytmShelfCards(listOf(playlist, album, playlist.copy(channel = Innertube.Info("Channel", null)))),
        )
    }

    @Test
    fun `video views are shown only in the wide layout`() {
        val video = Innertube.VideoItem(
            info = null, authors = listOf(Innertube.Info("Artist", null)),
            viewsText = "1M views", durationText = "3:00", thumbnail = null,
        )

        assertEquals(listOf(YtmShelfCard(YtmShelfPlaceholderKind.Video, 2)), ytmShelfCards(listOf(video)))
        assertEquals(listOf(YtmShelfCard(YtmShelfPlaceholderKind.Album, 1)), ytmShelfCards(listOf(video), videosAsAlbums = true))
    }
}
