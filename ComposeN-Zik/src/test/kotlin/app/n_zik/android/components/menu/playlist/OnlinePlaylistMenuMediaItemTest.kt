package app.n_zik.android.components.menu.playlist

import android.app.Application
import androidx.media3.common.util.UnstableApi
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.requests.PlaylistPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OnlinePlaylistMenuMediaItemTest {
    @Test
    fun `partial and complete playlist entries retain provider identity with edited metadata`() {
        val artistId = "UC0000000000000000000000"
        val albumId = "MPREbSource"
        val track = Innertube.SongItem(
            info = Innertube.Info("Provider track", NavigationEndpoint.Endpoint.Watch(videoId = "abcdefghijk")),
            authors = listOf(Innertube.Info("Provider artist", NavigationEndpoint.Endpoint.Browse(browseId = artistId))),
            album = Innertube.Info("Provider album", NavigationEndpoint.Endpoint.Browse(browseId = albumId)),
            durationText = "2:00",
            thumbnail = null,
            explicit = false,
        )
        val partial = PlaylistPage(
            playlist = Innertube.PlaylistItem(info = null, channel = null, songCount = null, isEditable = null, thumbnail = null),
            description = null,
            songs = listOf(track),
            songsContinuation = "nextPage",
            continuation = null,
        )
        assertNull(partial.toOnlinePlaylistMenuContent().songs)

        for (page in listOf(partial, partial.copy(songsContinuation = null))) {
            val content = page.toOnlinePlaylistMenuContent()
            val edited = requireNotNull(content.availableSongs).single().copy(title = "Edited title")
            val item = onlinePlaylistMenuMediaItem(edited, content, "VLplaylist")
            assertEquals(track.key, item.mediaId)
            assertEquals("Edited title", item.mediaMetadata.title)
            assertEquals("Provider album", item.mediaMetadata.albumTitle)
            assertEquals(albumId, item.mediaMetadata.extras?.getString("albumId"))
            assertEquals(listOf(artistId), item.mediaMetadata.extras?.getStringArrayList("artistIds"))
        }
    }
}
