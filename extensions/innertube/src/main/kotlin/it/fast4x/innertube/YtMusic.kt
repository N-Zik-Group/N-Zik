package it.fast4x.innertube

import io.ktor.client.call.body
import it.fast4x.innertube.Innertube.getBestQuality
import it.fast4x.innertube.models.BrowseEndpoint
import it.fast4x.innertube.models.BrowseResponse
import it.fast4x.innertube.models.CreatePlaylistResponse
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.MusicShelfRenderer
import it.fast4x.innertube.models.getContinuation
import it.fast4x.innertube.models.oddElements
import it.fast4x.innertube.requests.AlbumPage
import it.fast4x.innertube.requests.ArtistItemsContinuationPage
import it.fast4x.innertube.requests.ArtistItemsPage
import it.fast4x.innertube.requests.ArtistPage
import it.fast4x.innertube.requests.HistoryPage
import it.fast4x.innertube.requests.HomePage
import it.fast4x.innertube.requests.NewReleaseAlbumPage
import it.fast4x.innertube.requests.PlaylistContinuationPage
import it.fast4x.innertube.requests.PlaylistPage


object YtMusic {

    const val PLAYLIST_SIZE_LIMIT = 5000

    suspend fun createPlaylist(title: String) = runCatching {
        Innertube.createPlaylist(title = title).body<CreatePlaylistResponse>().playlistId
    }.onFailure {
        println("YtMusic: createPlaylist error: ${it.stackTraceToString()}")
    }

    suspend fun deletePlaylist(playlistId: String) = runCatching {
        Innertube.deletePlaylist(playlistId = playlistId)
    }.onFailure {
        println("YtMusic: deletePlaylist error: ${it.stackTraceToString()}")
    }

    suspend fun renamePlaylist(playlistId: String, name: String) = runCatching {
        Innertube.renamePlaylist(playlistId = playlistId, name = name)
    }.onFailure {
        println("YtMusic: renamePlaylist error: ${it.stackTraceToString()}")
    }

    suspend fun addToPlaylist(playlistId: String, videoId: String) = runCatching {
        Innertube.addToPlaylist(playlistId = playlistId, videoId = videoId)
    }.onFailure {
        println("YtMusic: addToPlaylist(single) error: ${it.stackTraceToString()}")
    }

    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>) = runCatching {
        val requestedVideoIds = videoIds.take(PLAYLIST_SIZE_LIMIT)
        val difference = videoIds.size - requestedVideoIds.size
        if (difference > 0) {
            println("YtMusic: addToPlaylist warning: only adding (at most) $PLAYLIST_SIZE_LIMIT ids, (surpassed limit by $difference)")
        }
        Innertube.addToPlaylist(playlistId = playlistId, videoIds = requestedVideoIds)
    }.onFailure {
        println("YtMusic: addToPlaylist (list of size ${videoIds.size}) error: ${it.stackTraceToString()}")
    }

    suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String? = null) = runCatching {
        println("YtMusic: removeFromPlaylist params: playlistId: $playlistId, videoId: $videoId, setVideoId: $setVideoId")
            Innertube.removeFromPlaylist(playlistId = playlistId, videoId = videoId, setVideoId = setVideoId)
        }.onFailure {
            println("YtMusic: removeFromPlaylist error: ${it.stackTraceToString()}")
        }

    suspend fun addPlaylistToPlaylist(playlistId: String, videoId: String) = runCatching {
        Innertube.addPlaylistToPlaylist(playlistId = playlistId, addPlaylistId = videoId)
    }.onFailure {
        println("YtMusic: addPlaylistToPlaylist error: ${it.stackTraceToString()}")
    }

    suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoIds: List<String?>) = runCatching {
        Innertube.removeFromPlaylist(playlistId = playlistId, videoId = videoId, setVideoIds = setVideoIds)
    }.onFailure {
        println("YtMusic: removeFromPlaylist (list of size ${setVideoIds.size}) error: ${it.stackTraceToString()}")
    }

    suspend fun subscribeChannel(channelId: String) = runCatching {
        println("YtMusic: subscribeChannel channelId: $channelId")
        Innertube.subscribeChannel(channelId)
    }.onFailure {
        println("YtMusic: subscribeChannel error: ${it.stackTraceToString()}")
    }

    suspend fun unsubscribeChannel(channelId: String) = runCatching {
        println("YtMusic: unsubscribeChannel channelId: $channelId")
        Innertube.unsubscribeChannel(channelId)
    }.onFailure {
        println("YtMusic: unsubscribeChannel error: ${it.stackTraceToString()}")
    }

    suspend fun likePlaylistOrAlbum(playlistId: String) = runCatching {
        println("YtMusic: likePlaylistOrAlbum playlistId: $playlistId")
        Innertube.likePlaylistOrAlbum(playlistId)
    }.onFailure {
        println("YtMusic: likePlaylistOrAlbum error: ${it.stackTraceToString()}")
    }

    suspend fun removelikePlaylistOrAlbum(playlistId: String) = runCatching {
        println("YtMusic: removelikePlaylistOrAlbum playlistId: $playlistId")
        Innertube.removelikePlaylistOrAlbum(playlistId)
    }.onFailure {
        println("YtMusic: removelikePlaylistOrAlbum error: ${it.stackTraceToString()}")
    }

    suspend fun likeVideoOrSong(VideoId: String) = runCatching {
        println("YtMusic: likeVideoOrSong VideoId: $VideoId")
        Innertube.likeVideoOrSong(VideoId)
    }.onFailure {
        println("YtMusic: likeVideoOrSong error: ${it.stackTraceToString()}")
    }

    suspend fun removelikeVideoOrSong(VideoId: String) = runCatching {
        println("YtMusic: removelikeVideoOrSong playlistIdId: $VideoId")
        Innertube.removelikeVideoOrSong(VideoId)
    }.onFailure {
        println("YtMusic: removelikeVideoOrSong error: ${it.stackTraceToString()}")
    }

    suspend fun getHomePage(setLogin: Boolean = false): Result<HomePage> = runCatching {

        val hl = "en" // Force English to keep section matching simple in HomeQuickPicks
        var response = Innertube.browse(browseId = "FEmusic_home", setLogin = setLogin, hl = hl).body<BrowseResponse>()

        val sectionListRender = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer

        var continuation = sectionListRender?.continuations?.getContinuation()

        val chips = sectionListRender?.header?.chipCloudRenderer?.chips?.mapNotNull {
            Innertube.Chip.fromChipCloudChipRenderer(it)
        }

        val sections = sectionListRender?.contents!!
            .mapNotNull { it.musicCarouselShelfRenderer }
            .mapNotNull {
                HomePage.Section.fromMusicCarouselShelfRenderer(it)
            }.toMutableList()
        while (continuation != null) {
            response = Innertube.browse(continuation = continuation, setLogin = setLogin, hl = "en").body<BrowseResponse>()
            continuation = response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()

            sections += response.continuationContents?.sectionListContinuation?.contents
                ?.mapNotNull { it.musicCarouselShelfRenderer }
                ?.mapNotNull {
                    HomePage.Section.fromMusicCarouselShelfRenderer(it)
                }.orEmpty()

        }
        HomePage( sections = sections, chips = chips )
    }

    suspend fun getQuickPicks(setLogin: Boolean = false): Result<List<Innertube.SongItem>> = runCatching {
        val response = Innertube.browse(browseId = "FEmusic_home", setLogin = setLogin, hl = "en").body<BrowseResponse>()

        val sectionListRender = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer

        sectionListRender?.contents
            ?.mapNotNull { it.musicCarouselShelfRenderer }
            ?.mapNotNull { HomePage.Section.fromMusicCarouselShelfRenderer(it) }
            ?.firstOrNull { it.title.contains("Quick picks", ignoreCase = true) }
            ?.items
            ?.filterIsInstance<Innertube.SongItem>()
            .orEmpty()
    }

    suspend fun getHistory(setLogin: Boolean = false): Result<HistoryPage> = runCatching {

        val response = Innertube.browse(browseId = "FEmusic_history", setLogin = setLogin)
            .body<BrowseResponse>()

        println("getHistory() response sections: ${response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
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
        println("YtMusic: getArtistItemsPage() error: ${it.stackTraceToString()}")
    }

    suspend fun getPlaylist(playlistId: String): Result<PlaylistPage> = runCatching {
        val playlistIdChecked = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        println("YtMusic: getPlaylist playlistId: $playlistId Checked: $playlistIdChecked")
        val response = Innertube.browse(
            browseId = playlistIdChecked,
            setLogin = true
        ).body<BrowseResponse>()


        if (response.header != null)
            getPlaylistPreviousMode(playlistIdChecked, response)
        else
            getPlaylistNewMode(playlistIdChecked, response)
    }.onFailure {
        println("YtMusic: getPlaylist error: ${it.stackTraceToString()}")
    }

    private fun getPlaylistPreviousMode(playlistId: String, response: BrowseResponse): PlaylistPage {
        val header = response.header?.musicDetailHeaderRenderer ?:
            response.header?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicDetailHeaderRenderer


        val editable = response.header?.musicEditablePlaylistDetailHeaderRenderer != null

        val songs = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.musicPlaylistShelfRenderer?.contents?.mapNotNull {
                it.musicResponsiveListItemRenderer?.let { it1 ->
                    PlaylistPage.fromMusicResponsiveListItemRenderer(
                        it1
                    )
                }
            }!!
        val songsContinuation = response.contents.singleColumnBrowseResultsRenderer.tabs.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.musicPlaylistShelfRenderer?.continuations?.getContinuation()

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
                channel = null,
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
            continuation = response.contents.singleColumnBrowseResultsRenderer.tabs.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.continuations?.getContinuation()
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

        println("getPlaylist new mode editable: $isEditable")

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
                channel = null,
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

        println("YtMusic: getPlaylistContinuation fetching next page")

        val mainContents: List<MusicShelfRenderer.Content> = response.continuationContents
            ?.sectionListContinuation?.contents
            ?.mapNotNull { content ->
                content.musicPlaylistShelfRenderer?.contents
                    ?: content.musicShelfRenderer?.contents
            }?.flatten().orEmpty()

        val shelfContents: List<MusicShelfRenderer.Content> = response.continuationContents
            ?.musicPlaylistShelfContinuation?.contents.orEmpty()

        val musicShelfContents: List<MusicShelfRenderer.Content> = response.continuationContents
            ?.musicShelfContinuation?.contents.orEmpty()

        val allShelfContents = mainContents + shelfContents + musicShelfContents

        val songs = allShelfContents.mapNotNull { it.musicResponsiveListItemRenderer }
            .mapNotNull { renderer -> PlaylistPage.fromMusicResponsiveListItemRenderer(renderer) }.toMutableList()

        val appendedItems = response.onResponseReceivedActions
            ?.firstOrNull()?.appendContinuationItemsAction
            ?.continuationItems.orEmpty()
        songs += appendedItems.mapNotNull { it.musicResponsiveListItemRenderer }
            .mapNotNull { renderer -> PlaylistPage.fromMusicResponsiveListItemRenderer(renderer) }

        val nextContinuation = if (songs.isEmpty()) null else
            response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
                ?: response.continuationContents?.musicPlaylistShelfContinuation?.continuations?.getContinuation()
                ?: response.continuationContents?.musicShelfContinuation?.continuations?.getContinuation()
                ?: appendedItems.lastOrNull()?.continuationItemRenderer
                    ?.continuationEndpoint?.continuationCommand?.token

        PlaylistContinuationPage(
            songs = songs,
            continuation = nextContinuation
        )

    }.onFailure {
        println("YtMusic: getPlaylistContinuation error: ${it.stackTraceToString()}")
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
        println("YtMusic: getArtistItemsContinuation error: ${it.stackTraceToString()}")
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

        println("getAlbumSongs: Loaded ${songs.size} songs for playlist $playlistId")
        songs
    }

}