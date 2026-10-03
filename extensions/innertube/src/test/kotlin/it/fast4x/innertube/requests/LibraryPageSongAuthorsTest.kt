package it.fast4x.innertube.requests

import com.zionhuang.innertube.pages.LibraryPage
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.MusicResponsiveListItemRenderer
import it.fast4x.innertube.models.Runs
import it.fast4x.innertube.models.Thumbnail
import it.fast4x.innertube.models.ThumbnailRenderer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private typealias FlexColumn = MusicResponsiveListItemRenderer.FlexColumn
private typealias FlexColumnRenderer = MusicResponsiveListItemRenderer.FlexColumn
    .MusicResponsiveListItemFlexColumnRenderer
private typealias PlaylistItemData = MusicResponsiveListItemRenderer.PlaylistItemData

/**
 * `LibraryPage.fromMusicResponsiveListItemRenderer` (the isSong branch) feeds the
 * login-only "Quick picks" shelf and the "Similar to X" carousels. YTM bylines can
 * carry plain-text runs that are not artists (play counts such as "162K plays",
 * timestamps such as "51:03"): only the runs linked to a browse endpoint are
 * artists, and they alone must end up in `authors`.
 */
class LibraryPageSongAuthorsTest {

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
            playlistItemData = PlaylistItemData(playlistSetVideoId = null, videoId = "vid-1"),
        )

    private fun parse(byline: List<Runs.Run>, durationText: String? = "51:03"): Innertube.SongItem =
        (LibraryPage.fromMusicResponsiveListItemRenderer(songRenderer(byline, durationText)) as? Innertube.SongItem)
            ?: error("the renderer must parse as a SongItem")

    @Test
    fun `polluted byline keeps only the artist run with a browse endpoint`() {
        // "Laur, 162K plays" as served by the Quick picks shelf: the byline mixes an
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

        assertEquals(
            emptyList<Innertube.Info<NavigationEndpoint.Endpoint.Browse>>(),
            song.authors,
        )
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
}
