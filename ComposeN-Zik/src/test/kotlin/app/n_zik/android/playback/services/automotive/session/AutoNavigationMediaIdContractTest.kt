package app.n_zik.android.playback.services.automotive.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AutoNavigationMediaIdContractTest {
    @Test
    fun `page round trip retains full nested parent including page-like container text`() {
        listOf("SEARCH_SONGS", "artist/UC_PAGE_7/videos", "album/OLAK-one", "playlist/42").forEach { parent ->
            val parsed = requireNotNull(AutoMediaIdContract.parsePage(AutoMediaIdContract.page(parent, 3)))
            assertEquals(parent, parsed.parentId)
            assertEquals(3, parsed.index)
        }
    }

    @Test
    fun `malformed pages never become a different browse parent`() {
        listOf("", "SEARCH_SONGS", "_PAGE_0", "SEARCH_SONGS_PAGE_", "SEARCH_SONGS_PAGE_-1", "SEARCH_SONGS_PAGE_x", "SEARCH_SONGS_PAGE_2147483648", "AA_PAGE//0", "AA_PAGE/SEARCH_SONGS/-1", "AA_PAGE/SEARCH_SONGS/x", "AA_PAGE/SEARCH_SONGS/2147483648", "AA_PAGE/SEARCH_SONGS/0/extra").forEach {
            assertNull(it, AutoMediaIdContract.parsePage(it))
        }
    }

    @Test
    fun `playlist play all and shuffle are distinct actions for the same playlist`() {
        listOf("42", "OLAKabc", "FAVORITES", "CACHED").forEach { id ->
            val playAll = AutoMediaIdContract.playlistPlayAll(id)
            val shuffle = AutoMediaIdContract.playlistShuffle(id)
            assertEquals(id, AutoMediaIdContract.parsePlaylistPlayAll(playAll))
            assertEquals(id, AutoMediaIdContract.parsePlaylistShuffle(shuffle))
            assertNull(AutoMediaIdContract.parsePlaylistPlayAll(shuffle))
            assertNull(AutoMediaIdContract.parsePlaylistShuffle(playAll))
        }
    }

    @Test
    fun `playlist play all rejects missing and nested container ids`() {
        listOf("", "PLAYLIST_PLAY_ALL", "PLAYLIST_PLAY_ALL/", "PLAYLIST_PLAY_ALL/42/song").forEach {
            assertNull(it, AutoMediaIdContract.parsePlaylistPlayAll(it))
        }
    }
}
