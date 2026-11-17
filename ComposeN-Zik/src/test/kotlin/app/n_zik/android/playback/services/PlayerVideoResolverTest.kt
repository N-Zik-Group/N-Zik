package app.n_zik.android.playback.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint.Endpoint.Browse
import app.n_zik.innertube.models.NavigationEndpoint.Endpoint.Watch
import app.n_zik.innertube.models.NavigationEndpoint.Endpoint.Watch.WatchEndpointMusicSupportedConfigs
import app.n_zik.innertube.models.NavigationEndpoint.Endpoint.Watch.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig

class PlayerVideoResolverTest {
    @Test
    fun selectsUniqueOfficialClipWithMatchingMetadata() {
        val official = video("abcdefghijk", title = "Artist - The Song (Official Music Video)")
        val results = listOf(
            video("abcdefghijl", type = "MUSIC_VIDEO_TYPE_UGC"),
            video("abcdefghijm", title = "The Song (Live)"),
            video("abcdefghijn", artist = "Another Artist"),
            video("abcdefghijo", duration = "4:26"),
            official,
            official,
        )

        assertEquals("abcdefghijk", selectPlayerVideoCounterpart("the song", listOf("artist"), emptySet(), 212_000, results))
    }

    @Test
    fun ignoresAmbiguousMatches() {
        val results = listOf(video("abcdefghijk"), video("abcdefghijl"))

        assertNull(selectPlayerVideoCounterpart("The Song", listOf("Artist"), emptySet(), 212_000, results))
    }

    @Test
    fun requiresKnownDurations() {
        assertNull(selectPlayerVideoCounterpart("The Song", listOf("Artist"), emptySet(), 0, listOf(video("abcdefghijk"))))
        assertNull(
            selectPlayerVideoCounterpart(
                "The Song", listOf("Artist"), emptySet(), 212_000, listOf(video("abcdefghijk", duration = null)),
            ),
        )
    }

    @Test
    fun linkedArtistIdentityRejectsAnUnrelatedArtistWithTheSameName() {
        val results = listOf(
            video("abcdefghijk", artistId = "different"),
            video("abcdefghijl", artist = "Localized artist name", artistId = "same"),
        )
        assertEquals(
            "abcdefghijl",
            selectPlayerVideoCounterpart("The Song", listOf("Artist"), setOf("same"), 212_000, results),
        )
    }

    @Test
    fun albumLookupSelectsTheVideoVersionOfTheSourceTrack() {
        val source = video("abcdefghijk", type = "MUSIC_VIDEO_TYPE_ATV").asAlbumTrack()
        val clip = video("abcdefghijl", title = "The Song (Music Video)").asAlbumTrack()
        val otherClip = video("abcdefghijm", title = "Different Song").asAlbumTrack()
        assertEquals(
            "abcdefghijl",
            selectAlbumVideoCounterpart("abcdefghijk", "The Song", listOf(source, clip, otherClip)),
        )
        assertNull(selectAlbumVideoCounterpart("abcdefghijk", "The Song", listOf(source)))
    }

    @Test
    fun failedAlbumClipDoesNotMakeAnUnrelatedClipTheSingleTrackRelease() {
        val attempted = video("abcdefghijl").asAlbumTrack()
        val other = video("abcdefghijm", title = "Different Song").asAlbumTrack()

        assertNull(
            selectAlbumVideoCounterpart("abcdefghijk", "The Song", listOf(attempted, other), setOf(attempted.key)),
        )
    }

    @Test
    fun selectsYeshTikvahClipPublishedByItsDistributor() {
        val clip = video(
            "PIPCHjFC9yk",
            title = "[Official Music Video] Benny Friedman - Yesh Tikvah",
            artist = "MostlyMusicVideos",
            duration = "4:28",
            type = "MUSIC_VIDEO_TYPE_UGC",
        )
        assertEquals(
            "PIPCHjFC9yk",
            selectPlayerVideoCounterpart("Yesh Tikvah", listOf("Benny Friedman"), emptySet(), 277_000, listOf(clip)),
        )
        assertNull(selectPlayerVideoCounterpart("Yesh Tikvah", listOf("Another Artist"), emptySet(), 277_000, listOf(clip)))
        assertNull(selectPlayerVideoCounterpart("Yesh Tikvah", listOf("Benny Friedman"), emptySet(), 350_000, listOf(clip)))
    }

    @Test
    fun distributorClipsRequireExplicitArtistCreditAndAnUnambiguousMatch() {
        val clip = video("abcdefghijk", title = "[Official Video] Artist - The Song",
            artist = "Record Label", type = "MUSIC_VIDEO_TYPE_UGC")
        val duplicate = video("abcdefghijl", title = "[Official Video] Artist - The Song",
            artist = "Another Label", type = "MUSIC_VIDEO_TYPE_UGC")
        assertNull(selectPlayerVideoCounterpart("The Song", listOf("Artist"), emptySet(), 212_000, listOf(clip, duplicate)))
        assertNull(selectPlayerVideoCounterpart("The Song", listOf("Artist"), emptySet(), 212_000,
            listOf(video("abcdefghijk", title = "[Official Video] The Song", artist = "Record Label", type = "MUSIC_VIDEO_TYPE_UGC"))))
        val official = video("abcdefghijm")
        assertEquals("abcdefghijm", selectPlayerVideoCounterpart("The Song", listOf("Artist"), emptySet(), 212_000,
            listOf(clip, official)))
    }

    private fun video(
        id: String,
        title: String = "The Song",
        artist: String = "Artist",
        duration: String? = "3:30",
        type: String = "MUSIC_VIDEO_TYPE_OMV",
        artistId: String? = null,
    ) = Innertube.VideoItem(
        info = Innertube.Info(
            name = title,
            endpoint = Watch(
                videoId = id,
                watchEndpointMusicSupportedConfigs = WatchEndpointMusicSupportedConfigs(WatchEndpointMusicConfig(type)),
            ),
        ),
        authors = listOf(Innertube.Info(name = artist, endpoint = artistId?.let { Browse(browseId = it) })),
        viewsText = null,
        durationText = duration,
        thumbnail = null,
    )

    private fun Innertube.VideoItem.asAlbumTrack() = Innertube.SongItem(
        info = info,
        authors = authors,
        album = null,
        durationText = durationText,
        thumbnail = thumbnail,
    )
}
