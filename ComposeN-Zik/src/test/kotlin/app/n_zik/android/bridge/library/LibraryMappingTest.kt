package app.n_zik.android.bridge.library

import app.it.fast4x.rimusic.enums.DurationInMinutes
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.bridge.PlaylistOrigin
import app.n_zik.android.bridge.state.TrackDownloadState
import app.n_zik.android.bridge.state.TrackLike
import app.n_zik.android.bridge.state.TrackSource
import app.n_zik.android.core.database.PlaylistSongRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryMappingTest {

    private fun song(id: String, likedAt: Long? = null, thumbnailUrl: String? = "https://i.ytimg.com/vi/x/hqdefault.jpg") =
        Song(
            id = id,
            title = "modified:e:Title $id",
            artistsText = "pinned:Artist",
            durationText = "3:32",
            thumbnailUrl = thumbnailUrl,
            likedAt = likedAt,
            totalPlayTimeMs = 5_000L,
        )

    @Test
    fun `disliked songs are hidden, liked and neutral ones are listed`() {
        val songs = listOf(song("neutral"), song("liked", likedAt = 1_000L), song("disliked", likedAt = -1L))

        val listed = LibraryMapping.librarySongs(SongFilter.ALL, songs, TrackSupport())

        assertEquals(listOf("neutral", "liked"), listed.map { it.id })
        assertEquals(5_000L, listed[0].totalPlayTimeMs)
    }

    @Test
    fun `the disliked tab lists its rows as-is, the phone hides them nowhere else`() {
        val songs = listOf(song("neutral"), song("liked", likedAt = 1_000L), song("disliked", likedAt = -1L))

        val listed = LibraryMapping.librarySongs(SongFilter.DISLIKED, songs, TrackSupport())

        assertEquals(listOf("neutral", "liked", "disliked"), listed.map { it.id })
    }

    @Test
    fun `isLiked only for likedAt above zero`() {
        assertTrue(LibraryMapping.track(song("a", likedAt = 1L), TrackSupport()).isLiked)
        assertFalse(LibraryMapping.track(song("b", likedAt = null), TrackSupport()).isLiked)
        assertFalse(LibraryMapping.track(song("c", likedAt = 0L), TrackSupport()).isLiked)
        assertFalse(LibraryMapping.track(song("d", likedAt = -5L), TrackSupport()).isLiked)
    }

    @Test
    fun `track like tri-state follows likedAt`() {
        assertEquals(TrackLike.LIKED, LibraryMapping.track(song("a", likedAt = 1L), TrackSupport()).like)
        assertEquals(TrackLike.NEUTRAL, LibraryMapping.track(song("b", likedAt = null), TrackSupport()).like)
        assertEquals(TrackLike.NEUTRAL, LibraryMapping.track(song("c", likedAt = 0L), TrackSupport()).like)
        assertEquals(TrackLike.DISLIKED, LibraryMapping.track(song("d", likedAt = -5L), TrackSupport()).like)
    }

    @Test
    fun `track carries cleaned texts, duration, download state and artwork presence`() {
        val track = LibraryMapping.track(song("vid"), TrackSupport(downloaded = setOf("vid")))

        assertEquals("Title vid", track.title)
        assertEquals("Artist", track.artists)
        assertEquals(212_000L, track.durationMs)
        assertTrue(track.isDownloaded)
        assertTrue(track.hasArtwork)
        assertEquals(TrackSource.ONLINE, track.source)

        val bare = LibraryMapping.track(song("local:3", thumbnailUrl = null).copy(artistsText = null), TrackSupport(downloaded = setOf("local:3")))
        assertNull(bare.artists)
        assertFalse(bare.hasArtwork)
        assertFalse(bare.isDownloaded)
        assertEquals(TrackSource.LOCAL, bare.source)
        assertFalse(LibraryMapping.track(song("x", thumbnailUrl = "modified:"), TrackSupport()).hasArtwork)
    }

    @Test
    fun `track carries the custom artwork flag from its thumbnail url`() {
        // The phone's online artwork is not custom (its row uses FillHeight)
        val online = LibraryMapping.track(song("vid"), TrackSupport())
        assertFalse(online.isCustomArtwork)

        val local = LibraryMapping.track(song("local:3", thumbnailUrl = "file:///storage/cover.jpg"), TrackSupport())
        assertTrue(local.isCustomArtwork)
        assertTrue(LibraryMapping.track(song("vid").copy(thumbnailUrl = "https://host/app_covers/1.png"), TrackSupport()).isCustomArtwork)
        assertTrue(LibraryMapping.track(song("vid").copy(thumbnailUrl = "modified:abc"), TrackSupport()).isCustomArtwork)
    }

    @Test
    fun `isExplicit follows the explicit prefix of the database title`() {
        assertTrue(LibraryMapping.track(song("a"), TrackSupport()).isExplicit)
        assertTrue(LibraryMapping.track(song("b").copy(title = "pinned:e:Title b"), TrackSupport()).isExplicit)

        val plain = song("c").copy(title = "Title c")
        assertFalse(LibraryMapping.track(plain, TrackSupport()).isExplicit)
        assertEquals("Title c", LibraryMapping.track(plain, TrackSupport()).title)
    }

    @Test
    fun `disliked albums and artists are hidden and names are cleaned`() {
        val albums = LibraryMapping.albums(
            CollectionFilter.LIBRARY,
            listOf(
                Album(id = "a1", title = "modified:Album", authorsText = "pinned:Band", year = "2021", thumbnailUrl = "https://x/y"),
                Album(id = "a2", title = "Gone", dislikedAt = 1L),
                Album(id = "a3", title = null, thumbnailUrl = "ftp://nope"),
            ),
            songCounts = mapOf("a1" to 4),
        )
        assertEquals(listOf("a1", "a3"), albums.map { it.id })
        assertEquals("Album", albums[0].title)
        assertEquals("Band", albums[0].artists)
        assertEquals("2021", albums[0].year)
        assertEquals(4, albums[0].trackCount)
        assertTrue(albums[0].hasArtwork)
        assertEquals("", albums[1].title)
        assertNull(albums[1].artists)
        assertEquals(0, albums[1].trackCount)
        assertFalse(albums[1].hasArtwork)

        val artists = LibraryMapping.artists(
            CollectionFilter.LIBRARY,
            listOf(Artist(id = "u1", name = "pinned:Singer", thumbnailUrl = "content://a/1"), Artist(id = "u2", name = "No", dislikedAt = 3L)),
            songCounts = mapOf("u1" to 2, "u2" to 9),
        )
        assertEquals(listOf("u1"), artists.map { it.id })
        assertEquals("Singer", artists[0].name)
        assertEquals(2, artists[0].trackCount)
        assertTrue(artists[0].hasArtwork)
    }

    @Test
    fun `isBookmarked follows bookmarkedAt for albums and artists`() {
        val albums = LibraryMapping.albums(
            CollectionFilter.LIBRARY,
            listOf(
                Album(id = "bm1", title = "Followed", bookmarkedAt = 1_000L, thumbnailUrl = "https://x/y"),
                Album(id = "bm2", title = "Not followed", thumbnailUrl = "https://x/y"),
            ),
            emptyMap(),
        )
        assertTrue(albums[0].isBookmarked)
        assertFalse(albums[1].isBookmarked)

        val artists = LibraryMapping.artists(
            CollectionFilter.LIBRARY,
            listOf(
                Artist(id = "bm3", name = "Followed", bookmarkedAt = 1_000L, thumbnailUrl = "https://x/y"),
                Artist(id = "bm4", name = "Not followed", thumbnailUrl = "https://x/y"),
            ),
            emptyMap(),
        )
        assertTrue(artists[0].isBookmarked)
        assertFalse(artists[1].isBookmarked)
    }

    @Test
    fun `the disliked tabs list their rows as-is`() {
        val albums = LibraryMapping.albums(
            CollectionFilter.DISLIKED,
            listOf(Album(id = "a1", title = "Gone", dislikedAt = 1L), Album(id = "a2", title = "Kept")),
            emptyMap(),
        )
        assertEquals(listOf("a1", "a2"), albums.map { it.id })

        val artists = LibraryMapping.artists(
            CollectionFilter.DISLIKED,
            listOf(Artist(id = "u1", name = "Gone", dislikedAt = 1L), Artist(id = "u2", name = "Kept")),
            emptyMap(),
        )
        assertEquals(listOf("u1", "u2"), artists.map { it.id })
    }

    @Test
    fun `every playlist is listed with its cleaned display name and first artwork track`() {
        val artwork = LibraryMapping.artworkTracks(
            listOf(PlaylistSongRef(1L, "s2"), PlaylistSongRef(1L, "s5"), PlaylistSongRef(3L, "s9"))
        )
        assertEquals(mapOf(1L to "s2", 3L to "s9"), artwork)

        val playlists = LibraryMapping.playlists(
            listOf(
                PlaylistPreview(Playlist(id = 1L, name = "pinned:Road"), songCount = 2),
                PlaylistPreview(Playlist(id = 2L, name = "rewind-yearly:2025"), songCount = 0),
            ),
            artwork,
        ) { name -> if (name.startsWith("rewind-")) "Rewind 2025" else name }

        assertEquals(listOf("1", "2"), playlists.map { it.id })
        assertEquals("Road", playlists[0].name)
        assertEquals(2, playlists[0].trackCount)
        assertEquals("s2", playlists[0].artworkTrackId)
        assertEquals("Rewind 2025", playlists[1].name)
        assertNull(playlists[1].artworkTrackId)

        // Since 1.7: the pin prefix is a flag, not part of the origin
        assertTrue(playlists[0].isPinned)
        assertEquals(PlaylistOrigin.LOCAL, playlists[0].origin)
        assertFalse(playlists[1].isPinned)
        assertEquals(PlaylistOrigin.REWIND_YEARLY, playlists[1].origin)
        assertFalse(playlists[0].isBookmarked)
        assertFalse(playlists[1].isBookmarked)
    }

    @Test
    fun `playlists carry their origin, pin and YouTube Music bookmark`() {
        val playlists = LibraryMapping.playlists(
            listOf(
                PlaylistPreview(Playlist(id = 1L, name = "pinned:YT list", browseId = "VL123", isYoutubePlaylist = true), songCount = 1),
                PlaylistPreview(Playlist(id = 2L, name = "Spot", browseId = "SPOTIFY_IMPORT"), songCount = 1),
                PlaylistPreview(Playlist(id = 3L, name = "Rip", browseId = "RIPLAY_IMPORT:PL2"), songCount = 1),
                PlaylistPreview(Playlist(id = 4L, name = "My list"), songCount = 1),
            ),
            emptyMap(),
        ) { name -> name }

        val yt = playlists[0]
        assertTrue(yt.isPinned)
        assertTrue(yt.isBookmarked)
        assertEquals(PlaylistOrigin.YTMUSIC, yt.origin)

        assertEquals(PlaylistOrigin.SPOTIFY, playlists[1].origin)
        assertEquals(PlaylistOrigin.RIPLAY, playlists[2].origin)
        assertEquals(PlaylistOrigin.LOCAL, playlists[3].origin)
        assertFalse(playlists[3].isPinned)
        assertFalse(playlists[3].isBookmarked)

        // Since 1.7.2: the raw browse id travels for the client's playlist-menu guards
        assertEquals("VL123", yt.browseId)
        assertEquals("SPOTIFY_IMPORT", playlists[1].browseId)
        assertEquals("RIPLAY_IMPORT:PL2", playlists[2].browseId)
        assertNull(playlists[3].browseId)
    }

    @Test
    fun `originOf mirrors the phone origin icon logic, the pin apart`() {
        // Local: no browseId, or a browseId with no known sentinel
        assertEquals(PlaylistOrigin.LOCAL, LibraryMapping.originOf("My list", null, false))
        assertEquals(PlaylistOrigin.LOCAL, LibraryMapping.originOf("My list", "RDabc", false))
        assertEquals(PlaylistOrigin.LOCAL, LibraryMapping.originOf("pinned:My list", null, false))
        // YouTube Music: the flag or a browseId starting with VL
        assertEquals(PlaylistOrigin.YTMUSIC, LibraryMapping.originOf("YT list", "VL123", false))
        assertEquals(PlaylistOrigin.YTMUSIC, LibraryMapping.originOf("YT list", null, true))
        // Spotify / Ripley sentinels
        assertEquals(PlaylistOrigin.SPOTIFY, LibraryMapping.originOf("Spot", "SPOTIFY_IMPORT", false))
        assertEquals(PlaylistOrigin.SPOTIFY, LibraryMapping.originOf("Spot", "SPOTIFY_IMPORT:PL1", false))
        assertEquals(PlaylistOrigin.RIPLAY, LibraryMapping.originOf("Rip", "RIPLAY_IMPORT:PL2", false))
        // Rewind wins over a possible browseId; every generation keeps its period kind
        assertEquals(PlaylistOrigin.REWIND_MONTHLY, LibraryMapping.originOf("rewind-monthly:202609", "VL123", true))
        assertEquals(PlaylistOrigin.REWIND_YEARLY, LibraryMapping.originOf("rewind-yearly:2025", null, false))
        assertEquals(PlaylistOrigin.REWIND_ALLTIME, LibraryMapping.originOf("rewind-alltime", null, false))
        // The legacy (pre-kind) `monthly:` prefix keeps the plain rewind kind
        assertEquals(PlaylistOrigin.REWIND, LibraryMapping.originOf("monthly:2025", null, false))
    }

    @Test
    fun `track carries the phone row states from its support`() {
        val row = song("vid").copy(totalPlayTimeMs = 120_000L)
        row.playCount = 7
        val support = TrackSupport(
            downloaded = setOf("vid"),
            cached = setOf("vid"),
            states = mapOf("vid" to TrackDownloadState.DOWNLOADING),
            progresses = mapOf("vid" to 0.25f),
        )

        val track = LibraryMapping.track(row, support)

        assertEquals(120_000L, track.totalPlayTimeMs)
        assertEquals(7, track.playCount)
        assertTrue(track.isDownloaded)
        assertTrue(track.isCached)
        assertEquals(TrackDownloadState.DOWNLOADING, track.downloadState)
        assertEquals(0.25f, track.downloadProgress)

        // A queued download carries no progress on the wire
        val queued = LibraryMapping.track(row, support.copy(states = mapOf("vid" to TrackDownloadState.QUEUED)))
        assertEquals(TrackDownloadState.QUEUED, queued.downloadState)
        assertNull(queued.downloadProgress)

        // A settled row keeps the default wire values
        val settled = LibraryMapping.track(row, TrackSupport())
        assertEquals(TrackDownloadState.NONE, settled.downloadState)
        assertNull(settled.downloadProgress)
        assertFalse(settled.isCached)
        assertFalse(settled.isDownloaded)
    }

    @Test
    fun `albums and artists carry origin, dislike and their listening stats`() {
        val albums = LibraryMapping.albums(
            CollectionFilter.LIBRARY,
            listOf(Album(id = "y1", title = "YT", isYoutubeAlbum = true, thumbnailUrl = "https://x/y")),
            songCounts = mapOf("y1" to 3),
            listening = mapOf("y1" to CollectionListening(4, 90_000L)),
        )
        assertEquals(PlaylistOrigin.YTMUSIC, albums[0].origin)
        assertFalse(albums[0].isDisliked)
        assertEquals(4, albums[0].playCount)
        assertEquals(90_000L, albums[0].totalPlayTimeMs)

        // A local album without listening events keeps the defaults
        val local = LibraryMapping.albums(
            CollectionFilter.DISLIKED,
            listOf(Album(id = "l1", title = "Gone", dislikedAt = 1L)),
            emptyMap(),
        )
        assertTrue(local[0].isDisliked)
        assertEquals(PlaylistOrigin.LOCAL, local[0].origin)
        assertEquals(0, local[0].playCount)
        assertEquals(0L, local[0].totalPlayTimeMs)

        val artists = LibraryMapping.artists(
            CollectionFilter.LIBRARY,
            listOf(Artist(id = "u1", name = "YT artist", isYoutubeArtist = true, thumbnailUrl = "https://x/y")),
            songCounts = mapOf("u1" to 5),
            listening = mapOf("u1" to CollectionListening(6, 120_000L)),
        )
        assertEquals(PlaylistOrigin.YTMUSIC, artists[0].origin)
        assertFalse(artists[0].isDisliked)
        assertEquals(6, artists[0].playCount)
        assertEquals(120_000L, artists[0].totalPlayTimeMs)

        assertEquals(
            PlaylistOrigin.LOCAL,
            LibraryMapping.artists(CollectionFilter.LIBRARY, listOf(Artist(id = "u2", name = "Local")), emptyMap())[0].origin,
        )
    }

    @Test
    fun `playlists carry their editability and listening stats`() {
        val playlists = LibraryMapping.playlists(
            listOf(
                PlaylistPreview(Playlist(id = 1L, name = "Locked YT", isYoutubePlaylist = true, isEditable = false), songCount = 2),
                PlaylistPreview(Playlist(id = 2L, name = "Free"), songCount = 1),
            ),
            emptyMap(),
            listening = mapOf(2L to CollectionListening(3, 45_000L)),
        ) { name -> name }

        assertTrue(playlists[0].isBookmarked)
        assertFalse(playlists[0].isEditable)
        assertTrue(playlists[1].isEditable)
        assertEquals(0, playlists[0].playCount)
        assertEquals(0L, playlists[0].totalPlayTimeMs)
        assertEquals(3, playlists[1].playCount)
        assertEquals(45_000L, playlists[1].totalPlayTimeMs)
    }

    @Test
    fun `homeTabSongs applies the phone display filters per chip`() {
        val local = song("local:1")
        val short = song("short").copy(durationText = "3:32")
        val long = song("long").copy(durationText = "6:00")
        val explicit = song("x").copy(title = "e:Explicit")
        val plain = song("y").copy(title = "Plain")

        // The "All" chip keeps its local rows only when its setting includes them
        assertEquals(
            listOf("local:1", "short", "long", "x", "y"),
            LibraryMapping.homeTabSongs(listOf(local, short, long, explicit, plain), SongFilter.ALL, includeLocal = true, durationLimit = DurationInMinutes.Disabled, parentalControl = false).map { it.id },
        )
        assertEquals(
            listOf("short", "long", "x", "y"),
            LibraryMapping.homeTabSongs(listOf(local, short, long, explicit, plain), SongFilter.ALL, includeLocal = false, durationLimit = DurationInMinutes.Disabled, parentalControl = false).map { it.id },
        )

        // The Top chip hides its rows over the duration limit (the other chips never do)
        assertEquals(
            listOf("short"),
            LibraryMapping.homeTabSongs(listOf(short, long), SongFilter.TOP, includeLocal = true, durationLimit = DurationInMinutes.`5`, parentalControl = false).map { it.id },
        )
        assertEquals(
            listOf("short", "long"),
            LibraryMapping.homeTabSongs(listOf(short, long), SongFilter.LIKED, includeLocal = true, durationLimit = DurationInMinutes.`5`, parentalControl = false).map { it.id },
        )

        // The parental control hides the explicit rows of every chip
        assertEquals(
            listOf("y"),
            LibraryMapping.homeTabSongs(listOf(explicit, plain), SongFilter.ALL, includeLocal = true, durationLimit = DurationInMinutes.Disabled, parentalControl = true).map { it.id },
        )
        assertEquals(
            listOf("x", "y"),
            LibraryMapping.homeTabSongs(listOf(explicit, plain), SongFilter.ALL, includeLocal = true, durationLimit = DurationInMinutes.Disabled, parentalControl = false).map { it.id },
        )
    }

    @Test
    fun `detailSongs hides the explicit rows only when the parental control is on`() {
        val explicit = song("x").copy(title = "e:Explicit")
        val plain = song("y").copy(title = "Plain")

        assertEquals(listOf("x", "y"), LibraryMapping.detailSongs(listOf(explicit, plain), parentalControl = false).map { it.id })
        assertEquals(listOf("y"), LibraryMapping.detailSongs(listOf(explicit, plain), parentalControl = true).map { it.id })
    }
}
