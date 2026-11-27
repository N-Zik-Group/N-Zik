package app.n_zik.innertube

import io.ktor.client.call.body
import app.n_zik.innertube.Innertube.getBestQuality
import app.n_zik.innertube.utils.InnertubeLogger
import app.n_zik.innertube.models.BrowseEndpoint
import app.n_zik.innertube.models.BrowseResponse
import app.n_zik.innertube.models.CreatePlaylistResponse
import app.n_zik.innertube.models.MusicResponsiveHeaderRenderer
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.models.MusicShelfRenderer
import app.n_zik.innertube.models.Runs
import app.n_zik.innertube.models.SectionListRenderer
import app.n_zik.innertube.models.getContinuation
import app.n_zik.innertube.models.oddElements
import app.n_zik.innertube.requests.AlbumPage
import app.n_zik.innertube.requests.ArtistItemsContinuationPage
import app.n_zik.innertube.requests.ArtistItemsPage
import app.n_zik.innertube.requests.ArtistPage
import app.n_zik.innertube.requests.HistoryPage
import app.n_zik.innertube.requests.HomePage
import app.n_zik.innertube.requests.NewReleaseAlbumPage
import app.n_zik.innertube.requests.PlaylistContinuationPage
import app.n_zik.innertube.requests.PlaylistPage


object YtMusic {

    const val PLAYLIST_SIZE_LIMIT = 5000

    suspend fun createPlaylist(title: String) = runCatching {
        Innertube.createPlaylist(title = title).body<CreatePlaylistResponse>().playlistId
    }.onFailure {
        InnertubeLogger.e("YtMusic", "createPlaylist error", it)
    }

    suspend fun deletePlaylist(playlistId: String) = runCatching {
        Innertube.deletePlaylist(playlistId = playlistId)
    }.onFailure {
        InnertubeLogger.e("YtMusic", "deletePlaylist error", it)
    }

    suspend fun renamePlaylist(playlistId: String, name: String) = runCatching {
        Innertube.renamePlaylist(playlistId = playlistId, name = name)
    }.onFailure {
        InnertubeLogger.e("YtMusic", "renamePlaylist error", it)
    }

    suspend fun addToPlaylist(playlistId: String, videoId: String) = runCatching {
        Innertube.addToPlaylist(playlistId = playlistId, videoId = videoId)
    }.onFailure {
        InnertubeLogger.e("YtMusic", "addToPlaylist(single) error", it)
    }

    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>) = runCatching {
        val requestedVideoIds = videoIds.take(PLAYLIST_SIZE_LIMIT)
        val difference = videoIds.size - requestedVideoIds.size
        if (difference > 0) {
            InnertubeLogger.w("YtMusic", "addToPlaylist warning: only adding (at most) $PLAYLIST_SIZE_LIMIT ids, (surpassed limit by $difference)")
        }
        Innertube.addToPlaylist(playlistId = playlistId, videoIds = requestedVideoIds)
    }.onFailure {
        InnertubeLogger.e("YtMusic", "addToPlaylist (list of size ${videoIds.size}) error", it)
    }

    suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String? = null) = runCatching {
        InnertubeLogger.d("YtMusic", "removeFromPlaylist params: playlistId: $playlistId, videoId: $videoId, setVideoId: $setVideoId")
            Innertube.removeFromPlaylist(playlistId = playlistId, videoId = videoId, setVideoId = setVideoId)
        }.onFailure {
            InnertubeLogger.e("YtMusic", "removeFromPlaylist error", it)
        }

    suspend fun addPlaylistToPlaylist(playlistId: String, videoId: String) = runCatching {
        Innertube.addPlaylistToPlaylist(playlistId = playlistId, addPlaylistId = videoId)
    }.onFailure {
        InnertubeLogger.e("YtMusic", "addPlaylistToPlaylist error", it)
    }

    suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoIds: List<String?>) = runCatching {
        Innertube.removeFromPlaylist(playlistId = playlistId, videoId = videoId, setVideoIds = setVideoIds)
    }.onFailure {
        InnertubeLogger.e("YtMusic", "removeFromPlaylist (list of size ${setVideoIds.size}) error", it)
    }

    suspend fun subscribeChannel(channelId: String) = runCatching {
        InnertubeLogger.d("YtMusic", "subscribeChannel channelId: $channelId")
        Innertube.subscribeChannel(channelId)
    }.onFailure {
        InnertubeLogger.e("YtMusic", "subscribeChannel error", it)
    }

    suspend fun unsubscribeChannel(channelId: String) = runCatching {
        InnertubeLogger.d("YtMusic", "unsubscribeChannel channelId: $channelId")
        Innertube.unsubscribeChannel(channelId)
    }.onFailure {
        InnertubeLogger.e("YtMusic", "unsubscribeChannel error", it)
    }

    suspend fun likePlaylistOrAlbum(playlistId: String) = runCatching {
        InnertubeLogger.d("YtMusic", "likePlaylistOrAlbum playlistId: $playlistId")
        Innertube.likePlaylistOrAlbum(playlistId)
    }.onFailure {
        InnertubeLogger.e("YtMusic", "likePlaylistOrAlbum error", it)
    }

    suspend fun removelikePlaylistOrAlbum(playlistId: String) = runCatching {
        InnertubeLogger.d("YtMusic", "removelikePlaylistOrAlbum playlistId: $playlistId")
        Innertube.removelikePlaylistOrAlbum(playlistId)
    }.onFailure {
        InnertubeLogger.e("YtMusic", "removelikePlaylistOrAlbum error", it)
    }

    suspend fun likeVideoOrSong(VideoId: String) = runCatching {
        InnertubeLogger.d("YtMusic", "likeVideoOrSong VideoId: $VideoId")
        Innertube.likeVideoOrSong(VideoId)
    }.onFailure {
        InnertubeLogger.e("YtMusic", "likeVideoOrSong error", it)
    }

    suspend fun removelikeVideoOrSong(VideoId: String) = runCatching {
        InnertubeLogger.d("YtMusic", "removelikeVideoOrSong playlistIdId: $VideoId")
        Innertube.removelikeVideoOrSong(VideoId)
    }.onFailure {
        InnertubeLogger.e("YtMusic", "removelikeVideoOrSong error", it)
    }

    class HomeFirstPage internal constructor(
        internal val scope: Innertube.HomeBrowseScope,
        internal val sections: List<HomePage.Section>?,
        internal val chips: List<Innertube.Chip>?,
        internal val continuation: String?,
    ) {
        val quickPicks: List<Innertube.SongItem>
            get() = sections?.firstOrNull { it.title.contains("Quick picks", ignoreCase = true) }
                ?.items?.filterIsInstance<Innertube.SongItem>().orEmpty()
    }

    private fun List<SectionListRenderer.Content>.homeSections(): List<HomePage.Section> =
        mapNotNull { it.musicCarouselShelfRenderer }.mapNotNull(HomePage.Section::fromMusicCarouselShelfRenderer)

    private suspend fun fetchHomeFirstPage(scope: Innertube.HomeBrowseScope): HomeFirstPage {
        val renderer = Innertube.browseHome(scope).contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer
        return HomeFirstPage(
            scope = scope,
            sections = renderer?.contents?.homeSections(),
            chips = renderer?.header?.chipCloudRenderer?.chips?.mapNotNull(Innertube.Chip::fromChipCloudChipRenderer),
            continuation = renderer?.continuations?.getContinuation(),
        )
    }

    suspend fun getHomeFirstPage(setLogin: Boolean = false): Result<HomeFirstPage> = runCatching {
        fetchHomeFirstPage(Innertube.homeBrowseScope(setLogin))
    }

    suspend fun getHomePage(setLogin: Boolean = false, firstPage: HomeFirstPage? = null): Result<HomePage> = runCatching {
        val scope = Innertube.homeBrowseScope(setLogin)
        val page = firstPage?.takeIf { it.scope == scope && it.sections != null } ?: fetchHomeFirstPage(scope)
        val sections = checkNotNull(page.sections) { "YouTube Home sections are missing" }.toMutableList()
        var continuation = page.continuation
        while (continuation != null) {
            val next = Innertube.browseHome(scope, continuation).continuationContents?.sectionListContinuation
            continuation = next?.continuations?.getContinuation()
            sections += next?.contents?.homeSections().orEmpty()
        }
        HomePage(sections = sections, chips = page.chips)
    }

    suspend fun getQuickPicks(setLogin: Boolean = false): Result<List<Innertube.SongItem>> =
        getHomeFirstPage(setLogin).map { it.quickPicks }

    suspend fun getHistory(setLogin: Boolean = false): Result<HistoryPage> = runCatching {

        val response = Innertube.browse(browseId = "FEmusic_history", setLogin = setLogin)
            .body<BrowseResponse>()

        InnertubeLogger.d("YtMusic", "getHistory() response sections: ${response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents}")

        HistoryPage(
            sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents
                ?.mapNotNull {
                    it.musicShelfRenderer?.let { musicShelfRenderer ->
                        HistoryPage.fromMusicShelfRenderer(musicShelfRenderer)
                    }
                }
        )

    }

    suspend fun getArtistPage(browseId: String, setLogin: Boolean = false): Result<ArtistPage> = runCatching {
        val response = Innertube.browse(browseId = browseId, setLogin = setLogin).body<BrowseResponse>()
        val sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?.mapNotNull(ArtistPage::fromSectionListRendererContent)
            ?: throw IllegalStateException("ArtistPage: sections not found for browseId=$browseId")

        val artistName = response.header?.musicImmersiveHeaderRenderer?.title?.runs?.firstOrNull()?.text
            ?: response.header?.musicVisualHeaderRenderer?.title?.runs?.firstOrNull()?.text
            ?: response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text
            ?: throw IllegalStateException("ArtistPage: artist name not found for browseId=$browseId")

        ArtistPage(
            artist = Innertube.ArtistItem(
                info = Innertube.Info(
                    name = artistName,
                    endpoint = NavigationEndpoint.Endpoint.Browse(
                        browseId = browseId,
                        params = response.header?.musicImmersiveHeaderRenderer?.title?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.params
                    )
                ),
                thumbnail = response.header?.musicImmersiveHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()
                    ?: response.header?.musicVisualHeaderRenderer?.foregroundThumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()
                    ?: response.header?.musicDetailHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull(),
                channelId = response.header?.musicImmersiveHeaderRenderer?.subscriptionButton?.subscribeButtonRenderer?.channelId,
                subscribersCountText = response.header?.musicImmersiveHeaderRenderer?.subscriptionButton?.subscribeButtonRenderer?.subscriberCountText?.runs?.firstOrNull()?.text,
            ),
            sections = sections,
            description = response.header?.musicImmersiveHeaderRenderer?.description?.runs?.firstOrNull()?.text,
            subscribers = response.header?.musicImmersiveHeaderRenderer?.subscriptionButton?.subscribeButtonRenderer?.longSubscriberCountText?.text,
            listeners = response.header?.musicImmersiveHeaderRenderer?.monthlyListenerCount?.runs?.firstOrNull()?.text
                ?: response.header?.musicVisualHeaderRenderer?.monthlyListenerCount?.runs?.firstOrNull()?.text,
            shuffleEndpoint = response.header?.musicImmersiveHeaderRenderer?.playButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint,
            radioEndpoint = response.header?.musicImmersiveHeaderRenderer?.startRadioButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint,
        )
    }

    suspend fun getArtistItemsPage(endpoint: BrowseEndpoint): Result<ArtistItemsPage> = runCatching {
        var response = Innertube.browse(browseId = endpoint.browseId, params = endpoint.params).body<BrowseResponse>()

        var contents = (response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?: response.contents?.sectionListRenderer?.contents
            ?: emptyList())

        if (contents.isEmpty()) {
            val tabEndpoint = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.endpoint?.browseEndpoint
            if (tabEndpoint != null) {
                response = Innertube.browse(browseId = tabEndpoint.browseId ?: endpoint.browseId, params = tabEndpoint.params).body<BrowseResponse>()
                contents = (response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                    ?.tabRenderer?.content?.sectionListRenderer?.contents
                    ?: response.contents?.sectionListRenderer?.contents
                    ?: emptyList())
            }
        }

        val matchingCarousel = contents.firstNotNullOfOrNull { content ->
            content.musicCarouselShelfRenderer?.takeIf { carousel ->
                val moreParams = carousel.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.params
                val requestedParams = endpoint.params
                moreParams != null && requestedParams != null && (
                    moreParams == requestedParams ||
                    (moreParams.length > 50 && requestedParams.length > 50 && moreParams.takeLast(50) == requestedParams.takeLast(50))
                )
            }
        }

        if (matchingCarousel != null) {
            return@runCatching ArtistItemsPage(
                title = matchingCarousel.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text.orEmpty(),
                items = matchingCarousel.contents.mapNotNull {
                    it.musicTwoRowItemRenderer?.let { renderer ->
                        ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                    } ?: it.musicResponsiveListItemRenderer?.let { renderer ->
                        ArtistItemsPage.fromMusicResponsiveListItemRenderer(renderer)
                    }
                },
                continuation = null
            )
        }

        val gridRenderer = contents.firstNotNullOfOrNull { it.gridRenderer }
        val musicShelfRenderer = contents.firstNotNullOfOrNull { it.musicShelfRenderer }
        val musicPlaylistShelfRenderer = contents.firstNotNullOfOrNull { it.musicPlaylistShelfRenderer }

        when {
            gridRenderer != null -> {
                ArtistItemsPage(
                    title = gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text.orEmpty(),
                    items = gridRenderer.items!!.mapNotNull {
                        it.musicTwoRowItemRenderer?.let { renderer ->
                            ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                        }
                    },
                    continuation = gridRenderer.continuations?.getContinuation()
                )
            }
            musicShelfRenderer != null -> {
                val headerTitle = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text
                ArtistItemsPage.fromMusicShelfRenderer(musicShelfRenderer, headerTitle)!!
            }
            musicPlaylistShelfRenderer != null -> {
                ArtistItemsPage(
                    title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text.orEmpty(),
                    items = musicPlaylistShelfRenderer.contents?.mapNotNull {
                        it.musicResponsiveListItemRenderer?.let { it1 ->
                            ArtistItemsPage.fromMusicResponsiveListItemRenderer(it1)
                        }
                    }!!,
                    continuation = musicPlaylistShelfRenderer.contents?.lastOrNull()
                        ?.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
                )
            }
            else -> {
                // Fallback or empty
                ArtistItemsPage(
                    title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text.orEmpty(),
                    items = emptyList(),
                    continuation = null
                )
            }
        }
    }.onFailure {
        InnertubeLogger.e("YtMusic", "getArtistItemsPage() error", it)
    }

    suspend fun getPlaylist(playlistId: String): Result<PlaylistPage> = runCatching {
        val playlistIdChecked = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        InnertubeLogger.d("YtMusic", "getPlaylist playlistId: $playlistId Checked: $playlistIdChecked")
        val response = Innertube.browse(
            browseId = playlistIdChecked,
            setLogin = true
        ).body<BrowseResponse>()


        parsePlaylistFirstPage(playlistIdChecked, response)
    }.onFailure {
        InnertubeLogger.e("YtMusic", "getPlaylist error", it)
    }

    internal fun parsePlaylistFirstPage(playlistId: String, response: BrowseResponse): PlaylistPage =
        if (response.header != null) getPlaylistPreviousMode(playlistId, response)
        else getPlaylistNewMode(playlistId, response)

    internal fun playlistChannel(header: Runs?): Innertube.Info<NavigationEndpoint.Endpoint.Browse>? =
        header?.runs.orEmpty().mapNotNull { run ->
            val endpoint = run.navigationEndpoint?.browseEndpoint ?: return@mapNotNull null
            val browseId = endpoint.browseId?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val pageType = endpoint.type
            val name = run.text?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            if (pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                browseId.startsWith("UC") && (pageType == null || pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL")
            ) Innertube.Info(name, endpoint) else null
        }.distinctBy { it.endpoint?.browseId }.singleOrNull()

    internal fun playlistChannel(header: MusicResponsiveHeaderRenderer): Innertube.Info<NavigationEndpoint.Endpoint.Browse>? {
        val owner = header.facepile?.avatarStackViewModel
        val endpoint = owner?.rendererContext?.commandContext?.onTap?.innertubeCommand
        val ownerRun = Runs.Run(text = owner?.text?.content, navigationEndpoint = endpoint)
        return playlistChannel(Runs(listOf(ownerRun)))
            ?: playlistChannel(header.straplineTextOne)
            ?: playlistChannel(header.subtitle)
    }

    private fun getPlaylistPreviousMode(playlistId: String, response: BrowseResponse): PlaylistPage {
        val header = response.header?.musicDetailHeaderRenderer ?:
            response.header?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicDetailHeaderRenderer


        val editable = response.header?.musicEditablePlaylistDetailHeaderRenderer != null

        val section = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer
        val shelf = requireNotNull(section?.contents?.firstOrNull()?.musicPlaylistShelfRenderer)
        val shelfContents = requireNotNull(shelf.contents)
        val songs = shelfContents.mapNotNull { it.musicResponsiveListItemRenderer }
            .mapNotNull { PlaylistPage.fromMusicResponsiveListItemRenderer(it) }
        val songsContinuation = shelf.continuations?.getContinuation() ?: shelfContents.getContinuation()

        return PlaylistPage(
            playlist = Innertube.PlaylistItem(
                info = Innertube.Info(
                    name = header?.title?.runs?.firstOrNull()?.text!!,
                    endpoint = NavigationEndpoint.Endpoint.Browse(
                        browseId = playlistId,
                    )
                ),
                songCount = 0, //header.secondSubtitle.runs?.firstOrNull()?.text,
                thumbnail = header.thumbnail.croppedSquareThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality(),
                channel = playlistChannel(header.subtitle),
                isEditable = editable,
//                playEndpoint = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
//                    ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
//                    ?.musicPlaylistShelfRenderer?.contents?.firstOrNull()?.musicResponsiveListItemRenderer
//                    ?.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint,
//                shuffleEndpoint = header.menu.menuRenderer.topLevelButtons?.firstOrNull()?.buttonRenderer?.navigationEndpoint?.watchPlaylistEndpoint!!,
//                radioEndpoint = header.menu.menuRenderer.items?.find {
//                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
//                }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint!!,

            ),
            description = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer
                ?.description?.musicDescriptionShelfRenderer?.description?.text,
            songs = songs,
            songsContinuation = songsContinuation,
            continuation = section?.continuations?.getContinuation()
        )
    }

    private fun getPlaylistNewMode(playlistId: String, response: BrowseResponse): PlaylistPage {
        val header = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer
            ?: response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                ?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer

        val isEditable = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.musicEditablePlaylistDetailHeaderRenderer != null

        InnertubeLogger.d("YtMusic", "getPlaylist new mode editable: $isEditable")

        val songsContinuation = run {
            val shelf = response.contents?.twoColumnBrowseResultsRenderer
                ?.secondaryContents?.sectionListRenderer?.contents?.firstOrNull()
            val continuations = shelf?.musicPlaylistShelfRenderer?.continuations
                ?: shelf?.musicShelfRenderer?.continuations
            val contents = shelf?.musicPlaylistShelfRenderer?.contents
                ?: shelf?.musicShelfRenderer?.contents
            continuations?.getContinuation() ?: contents?.getContinuation()
        }

        return PlaylistPage(
            playlist = Innertube.PlaylistItem(
                info = Innertube.Info(
                    name = header?.title?.runs?.firstOrNull()?.text!!,
                    endpoint = NavigationEndpoint.Endpoint.Browse(
                        browseId = playlistId,
                    )
                ),
                songCount = 0,//header.secondSubtitle?.runs?.firstOrNull()?.text,
                thumbnail = response.background?.musicThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality(),
                channel = playlistChannel(header),
                isEditable = isEditable,
//                playEndpoint = header.buttons.getOrNull(1)?.musicPlayButtonRenderer
//                    ?.playNavigationEndpoint?.watchEndpoint,
//                shuffleEndpoint = header.buttons.getOrNull(2)?.menuRenderer?.items?.find {
//                    it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
//                }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
//                radioEndpoint = header.buttons.getOrNull(2)?.menuRenderer?.items?.find {
//                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
//                }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
            ),
            description = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer
                ?.description?.musicDescriptionShelfRenderer?.description?.text,
            songs = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
                ?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.contents?.mapNotNull {
                    it.musicResponsiveListItemRenderer?.let { it1 ->
                        PlaylistPage.fromMusicResponsiveListItemRenderer(
                            it1
                        )
                    }
                }!!,
            songsContinuation = songsContinuation,
            continuation = response.contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer
                .continuations?.getContinuation(),
            isEditable = isEditable
        )
    }

    suspend fun getPlaylistContinuation(continuation: String) = runCatching {
        val response = Innertube.browse(
            continuation = continuation,
            setLogin = true
        ).body<BrowseResponse>()

        InnertubeLogger.d("YtMusic", "getPlaylistContinuation fetching next page")
        parsePlaylistContinuation(response)
    }.onFailure {
        InnertubeLogger.e("YtMusic", "getPlaylistContinuation error", it)
    }

    internal fun parsePlaylistContinuation(response: BrowseResponse): PlaylistContinuationPage {
        val continuationContents = response.continuationContents
        val section = continuationContents?.sectionListContinuation
        val playlistShelf = continuationContents?.musicPlaylistShelfContinuation
        val musicShelf = continuationContents?.musicShelfContinuation
        val nestedShelfContinuation = section?.contents?.firstNotNullOfOrNull { content ->
            content.musicPlaylistShelfRenderer?.continuations?.getContinuation()
                ?: content.musicShelfRenderer?.continuations?.getContinuation()
        }
        val mainContents: List<MusicShelfRenderer.Content> = section?.contents
            ?.mapNotNull { content ->
                content.musicPlaylistShelfRenderer?.contents
                    ?: content.musicShelfRenderer?.contents
            }?.flatten().orEmpty()

        val allShelfContents = mainContents + playlistShelf?.contents.orEmpty() + musicShelf?.contents.orEmpty()

        val songs = allShelfContents.mapNotNull { it.musicResponsiveListItemRenderer }
            .mapNotNull { renderer -> PlaylistPage.fromMusicResponsiveListItemRenderer(renderer) }.toMutableList()

        val appendBatches = response.onResponseReceivedActions.orEmpty()
            .mapNotNull { it.appendContinuationItemsAction?.continuationItems }
        val appendedItems = appendBatches.flatten()
        songs += appendedItems.mapNotNull { it.musicResponsiveListItemRenderer }
            .mapNotNull { renderer -> PlaylistPage.fromMusicResponsiveListItemRenderer(renderer) }

        val appendedContinuation = appendedItems.lastOrNull { it.continuationItemRenderer != null }
        val nextContinuation = section?.continuations?.getContinuation()
            ?: playlistShelf?.continuations?.getContinuation()
            ?: musicShelf?.continuations?.getContinuation()
            ?: nestedShelfContinuation
            ?: allShelfContents.getContinuation()
            ?: appendedContinuation?.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
        require(nextContinuation == null || nextContinuation.isNotBlank()) { "Empty playlist continuation token" }
        require(appendedContinuation == null || nextContinuation != null) { "Missing playlist continuation token" }
        require(
            section?.contents != null || playlistShelf?.contents != null || musicShelf?.contents != null ||
                appendBatches.isNotEmpty() || nextContinuation != null
        ) { "Missing playlist continuation contents" }

        return PlaylistContinuationPage(
            songs = songs,
            continuation = nextContinuation
        )
    }

    suspend fun getArtistItemsContinuation(continuation: String) = runCatching {
        val response = Innertube.browse(
            continuation = continuation,
            setLogin = true
        ).body<BrowseResponse>()

        response.onResponseReceivedActions?.map {
            it.appendContinuationItemsAction?.continuationItems?.mapNotNull { it1 ->
                it1.musicResponsiveListItemRenderer?.let { it2 ->
                    ArtistItemsPage.fromMusicResponsiveListItemRenderer(
                        it2
                    )
                }
            }
        }?.let {
            it.firstOrNull()?.let { it1 ->
                ArtistItemsContinuationPage(
                    items = it1,
                    continuation = response.onResponseReceivedActions.firstOrNull()
                        ?.appendContinuationItemsAction?.continuationItems?.lastOrNull()
                        ?.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
                )
            }
        }

    }.onFailure {
        InnertubeLogger.e("YtMusic", "getArtistItemsContinuation error", it)
    }

    suspend fun getAlbum(browseId: String, withSongs: Boolean = true, onProgress: ((loaded: Int) -> Unit)? = null): Result<AlbumPage> = runCatching {
        val response = Innertube.browse(browseId = browseId).body<BrowseResponse>()
        val playlistId = response.microformat?.microformatDataRenderer?.urlCanonical?.substringAfterLast('=')
            ?: throw IllegalStateException("AlbumPage: playlistId not found for browseId=$browseId")

        val headerContent = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.musicResponsiveHeaderRenderer
            ?: throw IllegalStateException("AlbumPage: header not found for browseId=$browseId")

        AlbumPage(
            album = Innertube.AlbumItem(
                playlistId = playlistId,
                info = Innertube.Info(
                    name = headerContent.title?.runs?.firstOrNull()?.text
                        ?: throw IllegalStateException("AlbumPage: album title not found for browseId=$browseId"),
                    endpoint = NavigationEndpoint.Endpoint.Browse(
                        browseId = browseId,
                    )
                ),
                authors = headerContent.straplineTextOne?.runs?.oddElements()
                    ?.map {
                        Innertube.Info(
                            name = it.text,
                            endpoint = it.navigationEndpoint?.browseEndpoint,
                        )
                    }.orEmpty(),
                year = headerContent.subtitle?.runs?.lastOrNull()?.text,
                thumbnail = headerContent.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull(),
            ),
            songs = if (withSongs) getAlbumSongs(playlistId, onProgress).getOrThrow() else emptyList(),
            otherVersions = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer?.contents?.getOrNull(
                1
            )?.musicCarouselShelfRenderer?.contents
                ?.mapNotNull { it.musicTwoRowItemRenderer }
                ?.map(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                .orEmpty(),
            url = response.microformat?.microformatDataRenderer?.urlCanonical,
            description = response.contents?.twoColumnBrowseResultsRenderer?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?.contents
                ?.firstOrNull()
                ?.musicResponsiveHeaderRenderer
                ?.description
                ?.musicDescriptionShelfRenderer
                ?.description?.text,
        )
    }

    suspend fun getAlbumSongs(playlistId: String, onProgress: ((loaded: Int) -> Unit)? = null): Result<List<Innertube.SongItem>> = runCatching {
        val response = Innertube.browse(browseId = "VL$playlistId").body<BrowseResponse>()

        val shelf = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?: response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
 ?.contents?.firstOrNull()

        val contents = shelf?.musicPlaylistShelfRenderer?.contents
            ?: shelf?.musicShelfRenderer?.contents

        val songs = contents?.mapNotNull {
            it.musicResponsiveListItemRenderer?.let { it1 -> AlbumPage.getSong(it1) }
        }?.toMutableList() ?: mutableListOf()

        onProgress?.invoke(songs.size)

        // Extract continuation token (same pattern as PlaylistSongList)
        var continuation = shelf?.musicPlaylistShelfRenderer?.continuations?.getContinuation()
            ?: shelf?.musicShelfRenderer?.continuations?.getContinuation()
            ?: contents?.getContinuation()

        // Load all continuation pages (max 50 to prevent infinite loops)
        var maxPages = 50
        while (continuation != null && maxPages-- > 0) {
            val nextPage = getPlaylistContinuation(continuation).getOrNull()
            if (nextPage != null) {
                songs += nextPage.songs
                onProgress?.invoke(songs.size)
                continuation = nextPage.continuation
            } else {
                continuation = null
            }
        }

        InnertubeLogger.d("YtMusic", "getAlbumSongs: Loaded ${songs.size} songs for playlist $playlistId")
        songs
    }

}
