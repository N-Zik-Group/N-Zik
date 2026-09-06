package it.fast4x.innertube

import com.zionhuang.innertube.pages.LibraryContinuationPage
import com.zionhuang.innertube.pages.LibraryPage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.compression.brotli
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.protobuf.protobuf
import io.ktor.serialization.kotlinx.xml.xml
import it.fast4x.innertube.clients.YouTubeLocale
import it.fast4x.innertube.models.AccountInfo
import it.fast4x.innertube.models.AccountMenuResponse
import it.fast4x.innertube.models.BrowseResponse
import it.fast4x.innertube.models.Context
import it.fast4x.innertube.models.Context.Companion.DefaultWeb
import it.fast4x.innertube.models.GridRenderer
import it.fast4x.innertube.models.MusicNavigationButtonRenderer
import it.fast4x.innertube.models.MusicShelfRenderer
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.PlaylistPanelVideoRenderer
import it.fast4x.innertube.models.Runs
import it.fast4x.innertube.models.Thumbnail
import it.fast4x.innertube.utils.ProxyPreferences
import it.fast4x.innertube.utils.YoutubePreferences
import it.fast4x.innertube.utils.getProxy
import it.fast4x.innertube.utils.parseCookieString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.XML
import java.net.Proxy
import java.util.Locale
import it.fast4x.innertube.models.SectionListRenderer
import okhttp3.ConnectionPool
import okhttp3.Protocol
import java.io.File


object Innertube {

    private const val YOUTUBE_MUSIC_HOST = "music.youtube.com"
    private const val VISITOR_DATA_PREFIX = "Cgt"
    const val DEFAULT_VISITOR_DATA = "CgtMN0FkbDFaWERfdyi8t4u7BjIKCgJWThIEGgAgWQ%3D%3D"

    @OptIn(ExperimentalSerializationApi::class)
    private fun createClient() = HttpClient(OkHttp) {
        expectSuccess = false

        install(ContentNegotiation) {
            protobuf()
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            })
            xml(
                format =
                XML {
                    xmlDeclMode = XmlDeclMode.Charset
                    autoPolymorphic = true
                },
                contentType = ContentType.Text.Xml,
            )
        }

        install(ContentEncoding) {
            gzip(0.9F)
            deflate(0.8F)
        }

        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }

        val p = proxy ?: ProxyPreferences.preference?.let { getProxy(it) }
        engine {
            config {
                // Enable HTTP/2 for better compatibility with YouTube servers
                protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
                // Retry on connection failure (like Metrolist)
                retryOnConnectionFailure(true)
                // Connection pool for better performance (like Metrolist)
                connectionPool(okhttp3.ConnectionPool(10, 5, java.util.concurrent.TimeUnit.MINUTES))
                // HTTP cache to reduce redundant network calls (like Metrolist)
                cache(okhttp3.Cache(File(System.getProperty("java.io.tmpdir"), "n_zik_http_cache"), 50L * 1024L * 1024L))
                // OkHttp-level timeouts (like Metrolist)
                connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            }
            if (p != null) {
                proxy = p
                // Add proxy authentication if credentials are provided
                proxyAuth?.let { auth ->
                    if (auth.isNotBlank()) {
                        config {
                            proxyAuthenticator(okhttp3.Authenticator { _, response ->
                                // Track failed attempts to prevent infinite retries
                                val request = response.request
                                val retryCount = request.header("X-Proxy-Retry-Count")?.toIntOrNull() ?: 0

                                if (retryCount >= 3) {
                                    println("Innertube: Proxy auth failed after $retryCount attempts, clearing credentials")
                                    proxyAuth = null
                                    return@Authenticator null
                                }

                                val credential = okhttp3.Credentials.basic(
                                    auth.substringBefore(":"),
                                    auth.substringAfter(":")
                                )
                                request.newBuilder()
                                    .header("Proxy-Authorization", credential)
                                    .header("X-Proxy-Retry-Count", (retryCount + 1).toString())
                                    .build()
                            })
                        }
                    }
                }
            }
        }

        defaultRequest {
            url( "https", YOUTUBE_MUSIC_HOST ) {
                headers.append("Accept", "application/json")
                headers.append("Cache-Control", "no-cache")
            }
        }
    }

    var client = createClient()
        private set

    private var innerTubeX = com.metrolist.innertubex.InnerTube(client)
    private var transportGeneration = 0L

    var proxy: Proxy? = null
        set(value) {
            if (field == value) return
            field = value
            recreateTransport()
        }

    var proxyAuth: String? = null
        set(value) {
            if (field == value) return
            field = value
            if (proxy != null) recreateTransport()
        }

    var regionOverrideActive: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            innerTubeX.regionOverrideActive = value
            // Re-apply locale with override if active
            if (value) applyLocale()
        }

    var regionOverride: String = ""
        set(value) {
            if (field == value) return
            field = value
            if (regionOverrideActive) applyLocale()
        }

    var useLoginForBrowse: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            innerTubeX.useLoginForBrowse = value
        }

    @Synchronized
    private fun recreateTransport() {
        val session = innerTubeX.sessionSnapshot()
        innerTubeX.close()
        client.close()
        client = createClient()
        innerTubeX = com.metrolist.innertubex.InnerTube(client).also { replacement ->
            replacement.locale = session.locale
            replacement.replaceSession(
                cookie = session.cookie,
                visitorData = session.visitorData,
                dataSyncId = session.dataSyncId,
                authUser = session.authUser,
                useLoginForBrowse = session.useLoginForBrowse,
            )
            replacement.regionOverrideActive = session.regionOverrideActive
        }
        transportGeneration++
    }

    class ExtractionTransport internal constructor(
        val innerTube: com.metrolist.innertubex.InnerTube,
        val httpClient: HttpClient,
        val generation: Long,
    )

    @Synchronized
    fun extractionTransport(): ExtractionTransport =
        ExtractionTransport(
            innerTube = innerTubeX,
            httpClient = client,
            generation = transportGeneration,
        )

    var locale: YouTubeLocale
        get() = YouTubeLocale(
            gl = innerTubeX.locale.gl,
            hl = innerTubeX.locale.hl
        )
        set(value) {
            innerTubeX.locale = com.metrolist.innertubex.models.YouTubeLocale(
                gl = value.gl,
                hl = value.hl
            )
        }

    var visitorData: String?
        get() = innerTubeX.visitorData
        set(value) { innerTubeX.visitorData = value }

    var dataSyncId: String?
        get() = innerTubeX.dataSyncId
        set(value) {
            innerTubeX.dataSyncId = value?.let {
                it.takeIf { !it.contains("||") }
                    ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
                    ?: it.substringAfter("||")
            }
        }

    var cookie: String?
        get() = innerTubeX.cookie
        set(value) {
            innerTubeX.cookie = value
            cookieMap = if (value == null) emptyMap() else parseCookieString(value)
        }

    var cookieMap = emptyMap<String, String>()

    init {
        // Initialize session from preferences in one batch to avoid multiple session changes
        YoutubePreferences.preference?.let { prefs ->
            val cookieValue = prefs.cookie
            val visitorDataValue = prefs.visitordata.takeIf { !it.isNullOrBlank() }
            val dataSyncIdValue = prefs.dataSyncId
            
            // Set cookieMap locally
            cookieMap = if (cookieValue == null) emptyMap() else parseCookieString(cookieValue)
            
            // Set all session properties at once via replaceSession
            innerTubeX.replaceSession(
                cookie = cookieValue,
                visitorData = visitorDataValue,
                dataSyncId = dataSyncIdValue,
                authUser = "",
                useLoginForBrowse = true,
            )
        }
        innerTubeX.locale = YouTubeLocale(
            gl = Locale.getDefault().country,
            hl = Locale.getDefault().toLanguageTag()
        ).let { com.metrolist.innertubex.models.YouTubeLocale(gl = it.gl, hl = it.hl) }
    }

    private fun applyLocale() {
        val gl = if (regionOverrideActive && regionOverride.isNotBlank()) {
            regionOverride.uppercase()
        } else {
            Locale.getDefault().country
        }
        innerTubeX.locale = com.metrolist.innertubex.models.YouTubeLocale(
            gl = gl,
            hl = Locale.getDefault().toLanguageTag()
        )
    }

    suspend fun ensureVisitorData() {
        if (visitorData.isNullOrBlank() || visitorData == DEFAULT_VISITOR_DATA) {
            runCatching {
                visitorData = innerTubeX.fetchFreshVisitorData()
            }
        }
    }

    @Serializable
    data class Info<T : NavigationEndpoint.Endpoint>(
        val name: String?,
        val endpoint: T?
    ) {
        @Suppress("UNCHECKED_CAST")
        constructor(run: Runs.Run) : this(
            name = run.text,
            endpoint = run.navigationEndpoint?.endpoint as T?
        )
    }

    @JvmInline
    value class SearchFilter(val value: String) {
        companion object {
            val Song = SearchFilter("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            val Video = SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
            val Album = SearchFilter("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D")
            val Artist = SearchFilter("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D")
            val CommunityPlaylist = SearchFilter("EgeKAQQoAEABagoQAxAEEAoQCRAF")
            val FeaturedPlaylist = SearchFilter("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D")
            val Podcast = SearchFilter("EgWKAQJQAWoIEBAQERADEBU%3D")
        }
    }

    @Serializable
    sealed class Item {
        abstract val thumbnail: Thumbnail?
        abstract val key: String
        abstract val title: String?
    }

    @Serializable
    data class Chip(
        val title: String,
        val endpoint: NavigationEndpoint.Endpoint.Browse?,
        val deselectEndpoint: NavigationEndpoint.Endpoint.Browse?,
    ) {
        companion object {
            fun fromChipCloudChipRenderer(renderer: SectionListRenderer.Header.ChipCloudRenderer.Chip): Chip? {
                return Chip(
                    title = renderer.chipCloudChipRenderer.text?.runs?.firstOrNull()?.text ?: return null,
                    endpoint = renderer.chipCloudChipRenderer.navigationEndpoint?.browseEndpoint,
                    deselectEndpoint = renderer.chipCloudChipRenderer.onDeselectedCommand?.browseEndpoint,
                )
            }
        }
    }

    @Serializable
    data class SongItem(
        val info: Info<NavigationEndpoint.Endpoint.Watch>?,
        val authors: List<Info<NavigationEndpoint.Endpoint.Browse>>?,
        val album: Info<NavigationEndpoint.Endpoint.Browse>?,
        val durationText: String?,
        override val thumbnail: Thumbnail?,
        val explicit: Boolean = false,
        val setVideoId: String? = null
    ) : Item() {
        override val key get() = info?.endpoint?.videoId ?: ""
        override val title get() = info?.name

        val isOfficialMusicVideo: Boolean
            get() = info
                ?.endpoint
                ?.watchEndpointMusicSupportedConfigs
                ?.watchEndpointMusicConfig
                ?.musicVideoType == "MUSIC_VIDEO_TYPE_OMV"

        val isUserGeneratedContent: Boolean
            get() = info
                ?.endpoint
                ?.watchEndpointMusicSupportedConfigs
                ?.watchEndpointMusicConfig
                ?.musicVideoType == "MUSIC_VIDEO_TYPE_UGC"

        companion object {

            fun parse( plRenderer: PlaylistPanelVideoRenderer ): SongItem {
                val watchEndpoint = plRenderer.navigationEndpoint?.watchEndpoint
                requireNotNull( watchEndpoint?.videoId )

                //<editor-fold defaultstate="collapsed" desc="Author & album parser">
                val authors = mutableListOf<Info<NavigationEndpoint.Endpoint.Browse>>()
                var album: Info<NavigationEndpoint.Endpoint.Browse>? = null
                plRenderer.longBylineText
                          ?.runs
                          ?.groupBy { run ->
                              run.navigationEndpoint
                                  ?.browseEndpoint
                                  ?.browseEndpointContextSupportedConfigs
                                  ?.browseEndpointContextMusicConfig
                                  ?.pageType
                          }
                          ?.mapNotNull { (pageType, runs) ->
                              when (pageType) {
                                  "MUSIC_PAGE_TYPE_ARTIST" -> authors.addAll( runs.map( ::Info ) )
                                  "MUSIC_PAGE_TYPE_ALBUM"  -> album = runs.firstOrNull()?.let( ::Info )
                                  else -> return@mapNotNull
                              }
                          }
                //</editor-fold>
                
                return SongItem(
                    info = Info(
                        // Mustn't add [EXPLICIT_KEY_PREFIX] here
                        name = plRenderer.title?.text.orEmpty(),
                        endpoint = watchEndpoint
                    ),
                    authors = authors,
                    album = album,
                    durationText = plRenderer.lengthText?.text,
                    thumbnail = plRenderer.thumbnail
                                          ?.thumbnails
                                          ?.maxByOrNull {
                                              it.height ?: 0
                                          },
                    explicit = plRenderer.badges
                                         .any {
                                             it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                                         }
                )
            }
        }
    }

    @Serializable
    data class VideoItem(
        val info: Info<NavigationEndpoint.Endpoint.Watch>?,
        val authors: List<Info<NavigationEndpoint.Endpoint.Browse>>?,
        val viewsText: String?,
        val durationText: String?,
        override val thumbnail: Thumbnail?
    ) : Item() {
        override val key get() = info!!.endpoint!!.videoId!!
        override val title get() = info?.name

        val isOfficialMusicVideo: Boolean
            get() = info
                ?.endpoint
                ?.watchEndpointMusicSupportedConfigs
                ?.watchEndpointMusicConfig
                ?.musicVideoType == "MUSIC_VIDEO_TYPE_OMV"

        val isUserGeneratedContent: Boolean
            get() = info
                ?.endpoint
                ?.watchEndpointMusicSupportedConfigs
                ?.watchEndpointMusicConfig
                ?.musicVideoType == "MUSIC_VIDEO_TYPE_UGC"

        companion object
    }

    @Serializable
    data class AlbumItem(
        val info: Info<NavigationEndpoint.Endpoint.Browse>?,
        val authors: List<Info<NavigationEndpoint.Endpoint.Browse>>?,
        val year: String?,
        val songCount: Int? = null,
        val playlistId: String? = null,
        val description: String? = null,
        override val thumbnail: Thumbnail?
    ) : Item() {
        override val key get() = info!!.endpoint!!.browseId!!
        override val title get() = info?.name

        companion object
    }

    @Serializable
    data class ArtistItem(
        val info: Info<NavigationEndpoint.Endpoint.Browse>?,
        val subscribersCountText: String?,
        val songCount: Int? = null,
        val channelId: String? = null,
        val description: String? = null,
        override val thumbnail: Thumbnail?
    ) : Item() {
        override val key get() = info!!.endpoint!!.browseId!!
        override val title get() = info?.name

        companion object
    }

    @Serializable
    data class PlaylistItem(
        val info: Info<NavigationEndpoint.Endpoint.Browse>?,
        val channel: Info<NavigationEndpoint.Endpoint.Browse>?,
        val songCount: Int?,
        val isEditable: Boolean?,
        val description: String? = null,
        override val thumbnail: Thumbnail?
    ) : Item() {
        override val key get() = info!!.endpoint!!.browseId!!
        override val title get() = info?.name

        companion object
    }

    data class ArtistInfoPage(
        val name: String?,
        val description: String?,
        val subscriberCountText: String?,
        val thumbnail: Thumbnail?,
        val shuffleEndpoint: NavigationEndpoint.Endpoint.Watch?,
        val radioEndpoint: NavigationEndpoint.Endpoint.Watch?,
        val songs: List<SongItem>?,
        val songsEndpoint: NavigationEndpoint.Endpoint.Browse?,
        val albums: List<AlbumItem>?,
        val albumsEndpoint: NavigationEndpoint.Endpoint.Browse?,
        val singles: List<AlbumItem>?,
        val singlesEndpoint: NavigationEndpoint.Endpoint.Browse?,
        val playlists: List<PlaylistItem>?,
    )

    data class PlaylistOrAlbumPage(
        val title: String?,
        val authors: List<Info<NavigationEndpoint.Endpoint.Browse>>?,
        val year: String?,
        val thumbnail: Thumbnail?,
        val url: String?,
        val songsPage: ItemsPage<SongItem>?,
        val otherVersions: List<AlbumItem>?,
        val description: String?,
        val otherInfo: String?
    )

    data class NextPage(
        val itemsPage: ItemsPage<SongItem>?,
        val playlistId: String?,
        val params: String? = null,
        val playlistSetVideoId: String? = null
    )

    @Serializable
    data class RelatedPage(
        val songs: List<SongItem>? = null,
        val playlists: List<PlaylistItem>? = null,
        val albums: List<AlbumItem>? = null,
        val artists: List<ArtistItem>? = null,
    )
    data class RelatedSongs(
        val songs: List<SongItem>? = null
    )

    @Serializable
    data class DiscoverPage(
        val newReleaseAlbums: List<AlbumItem>,
        val moods: List<Mood.Item>
    )

    data class DiscoverPageAlbums(
        val newReleaseAlbums: List<AlbumItem>

    )

    @Serializable
    data class Mood(
        val title: String,
        val items: List<Item>
    ) {
        @Serializable
        data class Item(
            val title: String,
            val stripeColor: Long,
            val endpoint: NavigationEndpoint.Endpoint.Browse
        )
    }

    data class ItemsPage<T : Item>(
        var items: List<T>?,
        val continuation: String?,
        var title: String? = null
    )

    @Serializable
    data class ChartsPage(
        val playlists: List<PlaylistItem>? = null,
        val artists: List<ArtistItem>? = null,
        val videos: List<VideoItem>? = null,
        val songs: List<SongItem>? = null,
        val trending: List<SongItem>? = null
    )

    data class Podcast(
        val title: String,
        val author: String?,
        val authorThumbnail: String?,
        val thumbnail: List<Thumbnail>,
        val description: String?,
        val listEpisode: List<EpisodeItem>
    ) {
        data class EpisodeItem(
            val title: String,
            val author: String?,
            val description: String?,
            val thumbnail: List<Thumbnail>,
            val createdDay: String?,
            val durationString: String?,
            val videoId: String
        )
    }

    data class SearchSuggestions(
        val queries: List<String>,
        val recommendedSong: SongItem?,
        val recommendedAlbum: AlbumItem?,
        val recommendedArtist: ArtistItem?,
        val recommendedPlaylist: PlaylistItem?,
        val recommendedVideo: VideoItem?,
    )

    fun MusicNavigationButtonRenderer.toMood(): Mood.Item? {
        return Mood.Item(
            title = buttonText.runs.firstOrNull()?.text ?: return null,
            stripeColor = solid?.leftStripeColor ?: return null,
            endpoint = clickCommand.browseEndpoint ?: return null
        )
    }

    fun List<Thumbnail>.getBestQuality() =
        maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }

    suspend fun accountInfo(): Result<AccountInfo?> = runCatching {
        accountMenu()
            .body<AccountMenuResponse>()
            .actions?.get(0)?.openPopupAction?.popup?.multiPageMenuRenderer
            ?.header?.activeAccountHeaderRenderer
            ?.toAccountInfo()
    }

    suspend fun accountMenu(): HttpResponse {
        return innerTubeX.accountMenu(com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX).requireSuccess("accountMenu")
    }

    suspend fun next(
        videoId: String?,
        playlistId: String? = null,
        playlistSetVideoId: String? = null,
        index: Int? = null,
        params: String? = null,
        continuation: String? = null,
        hl: String? = null,
        setLogin: Boolean = false,
    ): HttpResponse {
        if (hl != null) {
            val isolated = innerTubeX.createIsolatedSession(includeAccount = setLogin)
            isolated.locale = com.metrolist.innertubex.models.YouTubeLocale(
                gl = innerTubeX.locale.gl, hl = hl
            )
            return try {
                isolated.next(
                    client = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
                    videoId = videoId,
                    playlistId = playlistId,
                    playlistSetVideoId = playlistSetVideoId,
                    index = index,
                    params = params,
                    continuation = continuation,
                )
            } finally {
                isolated.close()
            }
        }
        return innerTubeX.next(
            client = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
            videoId = videoId,
            playlistId = playlistId,
            playlistSetVideoId = playlistSetVideoId,
            index = index,
            params = params,
            continuation = continuation,
        )
    }

    suspend fun search(
        query: String? = null,
        params: String? = null,
        continuation: String? = null,
    ) = innerTubeX.search(
        client = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        query = query,
        params = params,
        continuation = continuation,
    )

    suspend fun getQueue(
        videoIds: List<String>? = null,
        playlistId: String? = null,
    ) = innerTubeX.getQueue(
        client = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        videoIds = videoIds,
        playlistId = playlistId,
    )

    suspend fun getSearchSuggestions(
        input: String,
    ) = innerTubeX.getSearchSuggestions(
        client = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        input = input,
    )

    private suspend fun HttpResponse.requireSuccess(operation: String): HttpResponse {
        if (!status.isSuccess()) {
            // Consume and discard response body to prevent resource leak (like Metrolist)
            runCatching { body<String>() }
            throw IllegalStateException("$operation failed with status ${status.value}")
        }
        return this
    }

    /*******************************************
     * NEW CODE
     */

    suspend fun createPlaylist(
        client: com.metrolist.innertubex.models.YouTubeClient = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        title: String,
    ) = innerTubeX.createPlaylist(client, title).requireSuccess("createPlaylist")

    suspend fun deletePlaylist(
        client: com.metrolist.innertubex.models.YouTubeClient = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        playlistId: String,
    ) = innerTubeX.deletePlaylist(client, playlistId).requireSuccess("deletePlaylist")

    suspend fun renamePlaylist(
        client: com.metrolist.innertubex.models.YouTubeClient = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        playlistId: String,
        name: String,
    ) = innerTubeX.renamePlaylist(client, playlistId, name).requireSuccess("renamePlaylist")

    suspend fun addToPlaylist(
        client: com.metrolist.innertubex.models.YouTubeClient = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        playlistId: String,
        videoId: String,
    ) = addToPlaylist(client, playlistId, listOf(videoId))

    suspend fun addToPlaylist(
        client: com.metrolist.innertubex.models.YouTubeClient = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        playlistId: String,
        videoIds: List<String>,
    ) = videoIds.map { videoId ->
        innerTubeX.addToPlaylist(client, playlistId, videoId).requireSuccess("addToPlaylist")
    }.last()

    suspend fun removeFromPlaylist(
        client: com.metrolist.innertubex.models.YouTubeClient = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        playlistId: String,
        videoId: String,
        setVideoId: String? = null,
    ) = removeFromPlaylist(client, playlistId, videoId, listOf(setVideoId))

    suspend fun removeFromPlaylist(
        client: com.metrolist.innertubex.models.YouTubeClient = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        playlistId: String,
        videoId: String,
        setVideoIds: List<String?>,
    ) = setVideoIds.filterNotNull().map { setVideoId ->
        innerTubeX.removePlaylistSong(client, playlistId, setVideoId, videoId).requireSuccess("removeFromPlaylist")
    }.last()

    suspend fun addPlaylistToPlaylist(
        client: com.metrolist.innertubex.models.YouTubeClient = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        playlistId: String,
        addPlaylistId: String,
    ) = innerTubeX.addPlaylistToPlaylist(client, playlistId, addPlaylistId).requireSuccess("addPlaylistToPlaylist")

    suspend fun subscribeChannel(
        channelId: String,
    ) = innerTubeX.subscribeChannel(com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX, channelId).requireSuccess("subscribeChannel")

    suspend fun unsubscribeChannel(
        channelId: String,
    ) = innerTubeX.unsubscribeChannel(com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX, channelId).requireSuccess("unsubscribeChannel")


    suspend fun likePlaylistOrAlbum(
        playlistId: String,
    ) = innerTubeX.likePlaylist(com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX, playlistId).requireSuccess("likePlaylist")

    suspend fun removelikePlaylistOrAlbum(
        playlistId: String,
    ) = innerTubeX.unlikePlaylist(com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX, playlistId).requireSuccess("unlikePlaylist")

    suspend fun likeVideoOrSong(
        videoId: String,
    ) = innerTubeX.likeVideo(com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX, videoId).requireSuccess("likeVideo")

    suspend fun removelikeVideoOrSong(
        videoId: String,
    ) = innerTubeX.unlikeVideo(com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX, videoId).requireSuccess("unlikeVideo")

    suspend fun browse(
        client: com.metrolist.innertubex.models.YouTubeClient = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
        setLogin: Boolean = false,
        hl: String? = null,
    ): HttpResponse {
        ensureVisitorData()
        if (hl != null) {
            // Use isolated session to avoid changing the shared session locale,
            // which would cancel all in-flight requests via publishSession()
            val isolated = innerTubeX.createIsolatedSession(includeAccount = setLogin)
            isolated.locale = com.metrolist.innertubex.models.YouTubeLocale(
                gl = innerTubeX.locale.gl, hl = hl
            )
            return try {
                isolated.browse(client, browseId, params, continuation, setLogin)
            } finally {
                isolated.close()
            }
        }
        return innerTubeX.browse(client, browseId, params, continuation, setLogin)
    }

    suspend fun library(browseId: String, tabIndex: Int = 0) = runCatching {
        val response = browse(
            browseId = browseId,
            setLogin = true
        ).body<BrowseResponse>()

        val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs

        val contents = if (tabs != null && tabs.size >= tabIndex) {
            tabs[tabIndex].tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
        }
        else {
            null
        }

        when {
            contents?.gridRenderer != null -> {
                contents.gridRenderer.items
                    ?.mapNotNull (GridRenderer.Item::musicTwoRowItemRenderer)
                    ?.mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) }?.let {
                        LibraryPage(
                            items = it,
                            continuation = contents.gridRenderer.continuations?.firstOrNull()?.nextContinuationData?.continuation
                        )
                    }
            }

            else -> {
                val shelfItems = contents?.musicShelfRenderer?.contents
                    ?.mapNotNull (MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                    ?.mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) }
                    ?: emptyList()
                LibraryPage(
                    items = shelfItems,
                    continuation = contents?.musicShelfRenderer?.continuations?.firstOrNull()?.
                    nextContinuationData?.continuation
                )
            }
        }
    }

    suspend fun libraryContinuation(continuation: String) = runCatching {
        val response = browse(
            continuation = continuation,
            setLogin = true
        ).body<BrowseResponse>()

        val contents = response.continuationContents

        when {
            contents?.gridContinuation != null -> {
                contents.gridContinuation.items
                    ?.mapNotNull (GridRenderer.Item::musicTwoRowItemRenderer)
                    ?.mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) }?.let {
                        LibraryContinuationPage(
                            items = it,
                            continuation = contents.gridContinuation.continuations?.firstOrNull()?.nextContinuationData?.continuation
                        )
                    }
            }

            else -> {
                LibraryContinuationPage(
                    items = contents?.musicShelfContinuation?.contents!!
                        .mapNotNull (MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
                    continuation = contents.musicShelfContinuation.continuations?.firstOrNull()?.
                    nextContinuationData?.continuation
                )
            }
        }
    }


    suspend fun registerPlayback(
        url: String,
        cpn: String,
        playlistId: String? = null,
        clientName: String = "WEB_REMIX",
    ): io.ktor.client.statement.HttpResponse {
        val freshCpn = (1..16).map {
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_".random()
        }.joinToString("")
        
        return innerTubeX.registerPlayback(
            client = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
            url = url,
            cpn = freshCpn,
            playlistId = playlistId,
        )
    }

    suspend fun feedback(
        tokens: List<String>,
    ) = innerTubeX.feedback(
        client = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        tokens = tokens,
    )

    suspend fun getTranscript(
        videoId: String,
    ) = innerTubeX.getTranscript(
        client = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        videoId = videoId,
    )

    suspend fun accountsList() = innerTubeX.accountsList(
        client = com.metrolist.innertubex.models.YouTubeClient.WEB,
    )

    suspend fun moveSongPlaylist(
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String,
    ) = innerTubeX.movePlaylistSong(
        client = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        playlistId = playlistId,
        setVideoId = setVideoId,
        successorSetVideoId = successorSetVideoId,
    )

    suspend fun setPlaylistThumbnail(
        playlistId: String,
        image: ByteArray,
    ) = innerTubeX.setPlaylistThumbnail(
        client = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        playlistId = playlistId,
        image = image,
    )

    suspend fun removePlaylistThumbnail(
        playlistId: String,
    ) = innerTubeX.removePlaylistThumbnail(
        client = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        playlistId = playlistId,
    )

    suspend fun deletePrivatelyOwnedEntity(
        entityId: String,
    ) = innerTubeX.deletePrivatelyOwnedEntity(
        client = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        entityId = entityId,
    )

    suspend fun player(
        client: com.metrolist.innertubex.models.YouTubeClient = com.metrolist.innertubex.models.YouTubeClient.WEB_REMIX,
        videoId: String,
        playlistId: String? = null,
        signatureTimestamp: Int? = null,
        poToken: String? = null,
    ) = innerTubeX.player(client, videoId, playlistId, signatureTimestamp, poToken)
}

