package app.n_zik.android.components.ui.screens.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.putEnum
import app.n_zik.android.core.rewind.RewindPlaylists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class YouTubeLibraryFilterTest {
    private val podcast = Playlist(name = "Podcast",
        browseId = "MPSPPLXK2KwV2tv0jwie5sRfW9Mrdz3gyZp9Vr", isEditable = false)
    private val remotePlaylist = Playlist(name = "Playlist", browseId = "VLPL123", isYoutubePlaylist = true)

    @Test fun `all YouTube content includes saved podcast even with account flag false`() {
        assertFalse(podcast.isYoutubePlaylist)
        assertTrue(matchesYouTubeLibraryFilter(podcast, YouTubeLibraryFilter.All))
        assertTrue(matchesYouTubeLibraryFilter(remotePlaylist, YouTubeLibraryFilter.All))
    }

    @Test fun `playlists and podcasts split by provenance instead of bookmark flags`() {
        assertFalse(matchesYouTubeLibraryFilter(podcast, YouTubeLibraryFilter.Playlists))
        assertTrue(matchesYouTubeLibraryFilter(podcast, YouTubeLibraryFilter.Podcasts))
        assertTrue(matchesYouTubeLibraryFilter(remotePlaylist, YouTubeLibraryFilter.Playlists))
        assertFalse(matchesYouTubeLibraryFilter(remotePlaylist, YouTubeLibraryFilter.Podcasts))
    }

    @Test fun `linked editable imports remain YouTube playlists without account bookmark`() {
        val imported = remotePlaylist.copy(browseId = "PL123", isEditable = true, isYoutubePlaylist = false)
        assertTrue(matchesYouTubeLibraryFilter(imported, YouTubeLibraryFilter.All))
        assertTrue(matchesYouTubeLibraryFilter(imported, YouTubeLibraryFilter.Playlists))
        assertFalse(matchesYouTubeLibraryFilter(imported, YouTubeLibraryFilter.Podcasts))
    }

    @Test fun `local provider and rewind content stay outside every YouTube subfilter`() {
        for (id in listOf(null, "", "local-id", "SPOTIFY_IMPORT", "RIPLAY_IMPORT", "PIPED_IMPORT", "rewind-2026")) {
            val local = Playlist(name = "Local", browseId = id, isYoutubePlaylist = true)
            for (filter in YouTubeLibraryFilter.entries) assertFalse("$id $filter", matchesYouTubeLibraryFilter(local, filter))
        }
    }

    @Test fun `YouTube filter persists separately from Rewind and resets to all`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("youtube-library-filter-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        assertEquals(YouTubeLibraryFilter.All, prefs.getEnum(YOUTUBE_LIBRARY_FILTER_KEY, YouTubeLibraryFilter.All))
        prefs.edit().putEnum(RewindPlaylists.REWIND_PLAYLISTS_FILTER_KEY, RewindPlaylists.Filter.Year)
            .putEnum(YOUTUBE_LIBRARY_FILTER_KEY, YouTubeLibraryFilter.Podcasts).commit()
        assertEquals(YouTubeLibraryFilter.Podcasts, prefs.getEnum(YOUTUBE_LIBRARY_FILTER_KEY, YouTubeLibraryFilter.All))
        assertEquals(RewindPlaylists.Filter.Year,
            prefs.getEnum(RewindPlaylists.REWIND_PLAYLISTS_FILTER_KEY, RewindPlaylists.Filter.All))
        prefs.edit().remove(YOUTUBE_LIBRARY_FILTER_KEY).commit()
        assertEquals(YouTubeLibraryFilter.All, prefs.getEnum(YOUTUBE_LIBRARY_FILTER_KEY, YouTubeLibraryFilter.All))
        assertEquals(RewindPlaylists.Filter.Year,
            prefs.getEnum(RewindPlaylists.REWIND_PLAYLISTS_FILTER_KEY, RewindPlaylists.Filter.All))
        prefs.edit().clear().commit()
    }
}
