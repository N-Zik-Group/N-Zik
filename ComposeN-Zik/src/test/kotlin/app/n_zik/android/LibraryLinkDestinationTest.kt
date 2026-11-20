package app.n_zik.android

import android.app.Application
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import app.n_zik.android.intents.libraryLinkDestination
import app.n_zik.android.intents.LibraryLinkDestination
import app.n_zik.android.enums.NavRoutes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LibraryLinkDestinationTest {
    @Test
    fun `theme links are consumed even when their revision or ID is invalid`() {
        val id = "deadbeef-0000-4000-8000-000000000001"
        assertEquals(
            LibraryLinkDestination.Theme("${NavRoutes.themeStoreDetail.name}/$id/4"),
            parse("nzik://theme/$id/4"),
        )
        for (path in listOf("$id/0", "$id/4/extra", "1-1-1-1-1/4", "${id.uppercase()}/4")) {
            assertEquals(LibraryLinkDestination.Theme(null), parse("nzik://theme/$path"))
        }
    }

    @Test
    fun `direct routes preserve query and path handling`() {
        assertEquals(LibraryLinkDestination.Route("${NavRoutes.playlist.name}/VLPL123"),
            parse("https://music.youtube.com/playlist?list=PL123"))
        assertEquals(LibraryLinkDestination.Route("${NavRoutes.artist.name}/artist"),
            parse("https://www.youtube.com/c/artist"))
        assertEquals(LibraryLinkDestination.Route("${NavRoutes.searchResults.name}/a+b"),
            parse("https://music.youtube.com/search?q=a%2Bb"))
        assertEquals(LibraryLinkDestination.Route("${NavRoutes.localPlaylist.name}/123"),
            parse("nzik://library/localPlaylist/123"))
        assertEquals(LibraryLinkDestination.Route("${NavRoutes.album.name}/release"),
            parse("nzik://library/album/release"))
    }

    @Test
    fun `remote and deferred actions keep distinct destinations`() {
        assertEquals(LibraryLinkDestination.RemoteAlbum("VLOLAK5uy_release"),
            parse("https://music.youtube.com/playlist?list=OLAK5uy_release"))
        assertEquals(LibraryLinkDestination.Video("abc"), parse("https://youtu.be/abc"))
        assertEquals(LibraryLinkDestination.Video("xyz"), parse("https://www.youtube.com/watch?v=xyz"))
        assertEquals(LibraryLinkDestination.Favorites, parse("nzik://library/playFavorites"))
        assertEquals(LibraryLinkDestination.Invite("ABCD1234"), parse("https://metrolist.cc/listen?code=abcd1234"))
    }

    @Test
    fun `incomplete links do not become another destination`() {
        for (link in listOf("https://youtu.be/watch", "https://music.youtube.com/playlist",
            "https://music.youtube.com/search", "https://example.com/other", "https://metrolist.cc/listen?code=short")) {
            assertEquals(LibraryLinkDestination.Unsupported, parse(link))
        }
    }

    private fun parse(link: String): LibraryLinkDestination = libraryLinkDestination(Uri.parse(link))
}
