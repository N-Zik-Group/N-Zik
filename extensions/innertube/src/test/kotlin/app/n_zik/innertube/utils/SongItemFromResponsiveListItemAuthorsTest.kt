package app.n_zik.innertube.utils

import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.MusicResponsiveListItemRenderer
import app.n_zik.innertube.models.MusicShelfRenderer
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.models.Runs
import app.n_zik.innertube.models.SectionListRenderer
import app.n_zik.innertube.models.Thumbnail
import app.n_zik.innertube.models.ThumbnailRenderer
import app.n_zik.innertube.requests.ArtistItemsPage
import app.n_zik.innertube.requests.ArtistPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

private typealias FlexColumn = MusicResponsiveListItemRenderer.FlexColumn
private typealias FlexColumnRenderer = MusicResponsiveListItemRenderer.FlexColumn
    .MusicResponsiveListItemFlexColumnRenderer

/**
 * `Innertube.SongItem.Companion.from` (the related-songs parser) builds the
 * `SongItem`s behind the Quick Picks "related songs". YTM bylines can carry
 * plain-text runs that are not artists (play counts such as "162K plays",
 * timestamps such as "51:03"): only the runs linked to a browse endpoint are
 * artists, and they alone must end up in `authors`.
 */
class SongItemFromResponsiveListItemAuthorsTest {

    private fun browseEndpoint(browseId: String) = NavigationEndpoint(
        watchEndpoint = null,
        watchPlaylistEndpoint = null,
        browseEndpoint = NavigationEndpoint.Endpoint.Browse(browseId = browseId),
        searchEndpoint = null,
    )

    private fun run(text: String, browseId: String? = null) =
        Runs.Run(text = text, navigationEndpoint = browseId?.let(::browseEndpoint))

    private fun watchRun(text: String, videoId: String) = Runs.Run(
        text = text,
        navigationEndpoint = NavigationEndpoint(
            watchEndpoint = NavigationEndpoint.Endpoint.Watch(videoId = videoId),
            watchPlaylistEndpoint = null,
            browseEndpoint = null,
            searchEndpoint = null,
        ),
    )

    private fun flexColumn(vararg runs: Runs.Run) =
        FlexColumn(FlexColumnRenderer(Runs(runs.toList())))

    private fun songRenderer(byline: List<Runs.Run>, durationText: String?): MusicResponsiveListItemRenderer =
        MusicResponsiveListItemRenderer(
            fixedColumns = durationText?.let {
                listOf(FlexColumn(FlexColumnRenderer(Runs(listOf(Runs.Run(text = it, navigationEndpoint = null))))))
            },
            flexColumns = listOf(
                flexColumn(
                    Runs.Run(
                        text = "Some Song",
                        navigationEndpoint = NavigationEndpoint(
                            watchEndpoint = NavigationEndpoint.Endpoint.Watch(videoId = "vid-1"),
                            watchPlaylistEndpoint = null,
                            browseEndpoint = null,
                            searchEndpoint = null,
                        ),
                    ),
                ),
                flexColumn(*byline.toTypedArray()),
                flexColumn(run("Some Album", browseId = "album-1")),
            ),
            thumbnail = ThumbnailRenderer(
                musicThumbnailRenderer = ThumbnailRenderer.MusicThumbnailRenderer(
                    thumbnail = ThumbnailRenderer.MusicThumbnailRenderer.Thumbnail(
                        thumbnails = listOf(
                            Thumbnail(url = "https://i.ytimg.com/vi/vid-1/hqdefault.jpg", height = 96, width = 96),
                        ),
                    ),
                    thumbnailCrop = null,
                    thumbnailScale = null,
                ),
                croppedSquareThumbnailRenderer = null,
            ),
            navigationEndpoint = NavigationEndpoint(
                watchEndpoint = NavigationEndpoint.Endpoint.Watch(videoId = "vid-1"),
                watchPlaylistEndpoint = null,
                browseEndpoint = null,
                searchEndpoint = null,
            ),
            badges = null,
            playlistItemData = MusicResponsiveListItemRenderer.PlaylistItemData(
                playlistSetVideoId = null,
                videoId = "vid-1",
            ),
        )

    private fun parse(byline: List<Runs.Run>, durationText: String? = "51:03"): Innertube.SongItem =
        Innertube.SongItem.from(songRenderer(byline, durationText))
            ?: error("the renderer must parse as a SongItem")

    @Test
    fun `polluted byline keeps only the artist run with a browse endpoint`() {
        // "Laur, 162K plays" as served by the related-songs page: the byline mixes an
        // artist run with a plain-text play count.
        val song = parse(
            listOf(
                run("Laur", browseId = "artist-1"),
                run("•"),
                run("162K plays"),
            )
        )

        assertEquals(listOf("Laur"), song.authors?.map { it.name })
        assertEquals("artist-1", song.authors?.firstOrNull()?.endpoint?.browseId)
    }

    @Test
    fun `multi-artist byline keeps every artist run`() {
        val song = parse(
            listOf(
                run("Artist A", browseId = "artist-a"),
                run("•"),
                run("Artist B", browseId = "artist-b"),
            )
        )

        assertEquals(listOf("Artist A", "Artist B"), song.authors?.map { it.name })
        assertEquals(listOf("artist-a", "artist-b"), song.authors?.map { it.endpoint?.browseId })
    }

    @Test
    fun `byline without browse endpoints yields no authors`() {
        val song = parse(
            listOf(
                run("162K plays"),
                run("•"),
                run("51:03"),
            )
        )

        assertNull(song.authors)
    }

    @Test
    fun `byline run with a watch endpoint is excluded from authors`() {
        // The filter keeps browse endpoints only: a run that navigates to a watch
        // endpoint (browseEndpoint = null) is not an artist and must be dropped,
        // even though it does carry a navigation endpoint.
        val song = parse(
            listOf(
                run("Laur", browseId = "artist-1"),
                run("•"),
                watchRun("162K plays", "vid-plays"),
            )
        )

        assertEquals(listOf("Laur"), song.authors?.map { it.name })
    }

    @Test
    fun `fixedColumns duration is preserved alongside the authors filter`() {
        val song = parse(
            listOf(
                run("Laur", browseId = "artist-1"),
                run("•"),
                run("162K plays"),
            ),
            durationText = "51:03",
        )

        assertEquals("51:03", song.durationText)
        assertEquals("vid-1", song.key)
        assertEquals("Some Song", song.title)
    }

    @Test
    fun `artist song rows preserve supplied durations and allow missing durations`() {
        val byline = listOf(run("Laur", browseId = "artist-1"))
        for (duration in listOf("51:03", null)) {
            val renderer = songRenderer(byline, durationText = duration)
            val section = requireNotNull(
                ArtistPage.fromSectionListRendererContent(
                    SectionListRenderer.Content(
                        musicCarouselShelfRenderer = null,
                        musicShelfRenderer = MusicShelfRenderer(
                            bottomEndpoint = null,
                            contents = listOf(MusicShelfRenderer.Content(renderer, null, null)),
                            continuations = null,
                            title = Runs(listOf(run("Songs"))),
                        ),
                        gridRenderer = null,
                        musicDescriptionShelfRenderer = null,
                        musicPlaylistShelfRenderer = null,
                        musicEditablePlaylistDetailHeaderRenderer = null,
                        musicResponsiveHeaderRenderer = null,
                    ),
                ),
            )
            val overview = assertInstanceOf(Innertube.SongItem::class.java, section.items.single())
            val allSongs = requireNotNull(ArtistItemsPage.fromMusicResponsiveListItemRenderer(renderer))

            assertEquals("vid-1", overview.key)
            assertEquals(duration, overview.durationText)
            assertEquals(overview, allSongs)
        }
    }
}
