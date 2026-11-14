package app.n_zik.android.components.settings.appearance.preview

import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint.Endpoint.Browse
import app.n_zik.innertube.models.NavigationEndpoint.Endpoint.Watch
import app.n_zik.innertube.models.Thumbnail
import app.n_zik.innertube.requests.HomePage
import app.n_zik.innertube.requests.ArtistPage
import app.n_zik.innertube.requests.ArtistSection
import app.n_zik.innertube.requests.PlaylistPage
import app.n_zik.android.models.Song

private fun demoOnlineSong(song: Song) = Innertube.SongItem(
    info = Innertube.Info(song.title, Watch(videoId = song.id)),
    authors = listOf(Innertube.Info(song.artistsText, null)),
    album = null,
    durationText = song.durationText,
    thumbnail = song.thumbnailUrl?.let { Thumbnail(it, 600, 600) },
)

internal fun themePreviewArtistPage(): ArtistPage {
    val artist = themePreviewSearchPage(2, null).items.orEmpty().filterIsInstance<Innertube.ArtistItem>().first()
    val albums = themePreviewSearchPage(1, null).items.orEmpty().take(1)
    val songs = ThemePreviewDemo.songsForAlbum(ThemePreviewDemo.firstAlbumId).map(::demoOnlineSong)
    return ArtistPage(artist, listOf(ArtistSection("Songs", songs, null), ArtistSection("Albums", albums, null)),
        "Mira Vale makes dreamy indie pop and late-night instrumentals.", null, null, null, null)
}

internal fun themePreviewOnlinePlaylist(): PlaylistPage {
    val playlist = themePreviewSearchPage(4, null).items.orEmpty().filterIsInstance<Innertube.PlaylistItem>().first()
    return PlaylistPage(playlist, "Indie tracks for a quiet morning.", ThemePreviewDemo.queue.map(::demoOnlineSong), null, null)
}

internal fun themePreviewQuickPicksPage(): HomePage {
    val playlists = themePreviewSearchPage(4, null).items.orEmpty().filterIsInstance<Innertube.PlaylistItem>()
    val mixes = listOf("Late Night Transit", "Quiet Indie Mornings", "Silver Coast Radio", "After Hours Mix")
    val community = listOf("Songs for Rainy Windows", "Night Bus Diaries", "Sunday Headphones", "Slow City Sessions")
    fun shelf(title: String, items: List<Innertube.Item>) =
        HomePage.Section(title, label = null, thumbnail = null, endpoint = null, items = items)

    return HomePage(
        sections = listOf(
            shelf("Mixed for you", playlists.zip(mixes) { item, name -> item.copy(info = item.info?.copy(name = name)) }),
            shelf("Trending community playlists", playlists.zip(community) { item, name -> item.copy(info = item.info?.copy(name = name)) }),
            shelf("Albums for you", themePreviewSearchPage(1, null).items.orEmpty()),
            shelf("Music videos for you", themePreviewSearchPage(3, null).items.orEmpty()),
        ),
        chips = null,
    )
}

/** The search surface consumes the same result types as the online provider. */
internal fun themePreviewSearchPage(tab: Int, continuation: String?): Innertube.ItemsPage<Innertube.Item> {
    if (continuation != null) return Innertube.ItemsPage(emptyList(), null)
    val albums = ThemePreviewDemo.albums
    val items: List<Innertube.Item> = when (tab) {
        0 -> ThemePreviewDemo.songs.filter { it.title.contains("tide", ignoreCase = true) }.map { song ->
            Innertube.SongItem(
                info = Innertube.Info(song.title, Watch(videoId = song.id)),
                authors = listOf(Innertube.Info(song.artistsText, null)),
                album = null,
                durationText = song.durationText,
                thumbnail = song.thumbnailUrl?.let { Thumbnail(it, 600, 600) },
            )
        }
        1 -> albums.map { album ->
            Innertube.AlbumItem(
                info = Innertube.Info(album.title, Browse(browseId = album.id)),
                authors = listOf(Innertube.Info(album.authorsText, null)),
                year = album.year,
                songCount = ThemePreviewDemo.songsForAlbum(album.id).size,
                thumbnail = album.thumbnailUrl?.let { Thumbnail(it, 600, 600) },
            )
        }
        2 -> albums.map { album ->
            Innertube.ArtistItem(
                info = Innertube.Info(album.authorsText, Browse(browseId = "${album.id}-artist")),
                subscribersCountText = null,
                thumbnail = album.thumbnailUrl?.let { Thumbnail(it, 600, 600) },
            )
        }
        3 -> ThemePreviewDemo.songs.take(3).map { song ->
            Innertube.VideoItem(
                info = Innertube.Info("${song.title} — live session", Watch(videoId = "${song.id}-video")),
                authors = listOf(Innertube.Info(song.artistsText, null)),
                viewsText = null,
                durationText = song.durationText,
                thumbnail = song.thumbnailUrl?.let { Thumbnail(it, 600, 600) },
            )
        }
        4, 5, 6 -> albums.map { album ->
            val title = when (tab) {
                4 -> "${album.title} mix"
                5 -> "${album.title} radio"
                else -> "${album.title} sessions"
            }
            Innertube.PlaylistItem(
                info = Innertube.Info(title, Browse(browseId = "${album.id}-collection-$tab")),
                channel = Innertube.Info(album.authorsText, null),
                songCount = ThemePreviewDemo.songsForAlbum(album.id).size,
                isEditable = false,
                thumbnail = album.thumbnailUrl?.let { Thumbnail(it, 600, 600) },
            )
        }
        else -> emptyList()
    }
    return Innertube.ItemsPage(items, null)
}
