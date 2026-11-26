package app.n_zik.android.components.navigation

import android.app.Application
import app.n_zik.android.enums.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class OnlinePlaylistRouteTest {
    @Test
    fun browseIdentitySelectsTheCollectionRoute() {
        val podcastId = "MPSPPLXK2KwV2tv0jwie5sRfW9Mrdz3gyZp9Vr"
        assertEquals(podcastId, onlinePlaylistBrowseIdFromLink(podcastId))
        assertEquals(NavRoutes.podcast, onlinePlaylistDestination(podcastId))
        assertEquals("podcast/$podcastId", onlinePlaylistRoute(podcastId))

        assertEquals("VLPL123", onlinePlaylistBrowseIdFromLink("PL123"))
        assertEquals("VLPL123", onlinePlaylistBrowseIdFromLink("VLPL123"))
        assertEquals(NavRoutes.playlist, onlinePlaylistDestination("VLPL123"))
        assertEquals("playlist/VLPL123", onlinePlaylistRoute("VLPL123"))
    }

    @Test
    fun collectionMenuBypassesTheLocalAliasForBothSources() {
        assertEquals("playlist/VLPL123?sourceOnly=true", collectionMenuOnlineRoute("VLPL123"))
        assertEquals("podcast/MPSPPL123?sourceOnly=true", collectionMenuOnlineRoute("MPSPPL123"))
        assertEquals(NavRoutes.podcast, onlinePlaylistDestination("modified:MPSPPL123"))
    }

    @Test
    fun opaqueBrowseIdIsEncodedAsOneRouteSegment() {
        assertEquals("playlist/VL%2Fmix%3Fparams%3D2", onlinePlaylistRoute("VL/mix?params=2"))
    }
}
