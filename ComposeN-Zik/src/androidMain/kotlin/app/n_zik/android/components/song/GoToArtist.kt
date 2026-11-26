package app.n_zik.android.components.song

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.cleanPrefix
import app.n_zik.android.components.menu.isCurrentMenu
import app.n_zik.android.components.menu.navigateToMenuEntity
import app.n_zik.android.core.database.ArtistMappingReconcile
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.trackBrowseIdOrigin
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.core.ondevice.isDeviceArtistId
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.splitArtistNames
import app.n_zik.android.utils.isGenericReleaseChannel
import app.n_zik.android.utils.usableTrackArtistName
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.requests.nextPage
import app.n_zik.innertube.requests.searchPage
import app.n_zik.innertube.utils.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class GoToArtist(
    private val navController: NavController,
    private val song: Song,
    private val menuState: MenuState,
    private val artistName: String? = null,
    private val artistId: String? = null,
    private val onNavigate: () -> Unit = {},
) : MenuIcon, Descriptive {
    private val displayName = usableTrackArtistName(artistName ?: song.artistsText)
    private val hasKnownId = artistId?.let { isArtistIdForSong(it, song.isLocal) } == true

    override val isEnabled: Boolean
        get() = displayName != null || hasKnownId
    override val iconId: Int = R.drawable.people
    override val messageId: Int = R.string.library_artists_title
    override val menuIconTitle: String
        @Composable
        get() = displayName?.let { stringResource(R.string.artist_more_from_format, it) }
            ?: stringResource(R.string.artist_action_open)

    override fun onShortClick() {
        if (!isEnabled) return
        val menuKey = menuState.transitionKey
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            val route = try {
                resolveArtistRoute {
                    withContext(NzikDispatchers.UI) {
                        if (menuState.isCurrentMenu(menuKey)) Toaster.i(R.string.metadata_link_searching)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.tag("GoToArtist").w(error, "Artist lookup failed for ${song.id}")
                null
            }
            withContext(NzikDispatchers.UI) {
                if (!menuState.isCurrentMenu(menuKey)) return@withContext
                if (route != null) {
                    navigate(route)
                } else if (openTrackMetadataSearch(
                    navController, artistName ?: song.cleanArtistsText(), TrackMetadataSearch.Artist,
                )) {
                    menuState.hide()
                    onNavigate()
                } else {
                    Toaster.e(R.string.artist_lookup_failed)
                }
            }
        }
    }

    private fun navigate(path: String) {
        val id = path.substringBefore("?params=")
        val destination = "${NavRoutes.artist.name}/${Uri.encode(id)}"
        val params = path.substringAfter("?params=", "")
        val route = if (params.isBlank()) destination else "$destination?params=${Uri.encode(params)}"
        navigateToMenuEntity(navController, menuState, NavRoutes.artist, id, route)
        onNavigate()
    }

    private suspend fun resolveArtistRoute(onOnlineLookup: suspend () -> Unit): String? {
        val name = artistName ?: song.cleanArtistsText().splitArtistNames().singleOrNull()
        val mappedArtists = Database.artistTable.findBySongIdDirect(song.id)
            .filter { isArtistIdForSong(it.id, song.isLocal) && !isGenericReleaseChannel(it.name) }
        val knownId = artistId?.takeIf { isArtistIdForSong(it, song.isLocal) }
        if (knownId != null) {
            return mappedArtists.singleOrNull { artist ->
                ArtistMappingReconcile.isOverrideOf(artist.id, knownId) {
                    trackBrowseIdOrigin(song.id, artist.id)
                }
            }?.id ?: knownId
        }
        if (artistName == null) {
            mappedArtists.distinctBy(Artist::id).singleOrNull(::isEditedArtist)?.let { return it.id }
        }
        chooseTrackArtist(mappedArtists, name)?.let { return it.id }
        if (song.isLocal || song.id.length != 11) return null

        val context = appContext()
        if (OfflineMode.enabled(context) || !NetworkQualityHelper.isNetworkConnected(context)) return null

        onOnlineLookup()
        val next = Innertube.nextPage(videoId = song.id)?.getOrNull()?.itemsPage?.items
        selectExactArtistEndpoint(song.id, name, next.orEmpty())?.let { return it.toArtistRoute() }

        return selectExactArtistEndpoint(song.id, name, searchExactTrackSongs(song))?.toArtistRoute()
    }
}

internal fun chooseTrackArtist(artists: List<Artist>, name: String?): Artist? {
    val matches = artists.distinctBy(Artist::id).filter { candidate ->
        name.isNullOrBlank() || candidate.cleanName().trim().equals(cleanPrefix(name).trim(), ignoreCase = true)
    }
    return matches.singleOrNull(::isEditedArtist) ?: matches.singleOrNull()
}

private fun isEditedArtist(artist: Artist): Boolean = isEditedArtistId(artist.id)

internal fun isEditedArtistId(id: String): Boolean = !isDeviceArtistId(id) &&
    (id.startsWith(MODIFIED_PREFIX) || id.startsWith("LOCAL_ARTIST_"))

internal fun artistMenuIdentity(id: String, previousId: (String) -> String?): String =
    if (isEditedArtistId(id)) previousId(id)?.removePrefix(MODIFIED_PREFIX) ?: id.removePrefix(MODIFIED_PREFIX)
    else id

internal suspend fun searchExactTrackSongs(song: Song): List<Innertube.SongItem> {
    val title = song.cleanTitle().trim().takeIf(String::isNotBlank) ?: return emptyList()
    val credit = usableTrackArtistName(song.cleanArtistsText())
    val query = listOfNotNull(title, credit).joinToString(" ")
    return Innertube.searchPage<Innertube.SongItem>(
        query = query,
        params = Innertube.SearchFilter.Song.value,
        fromMusicShelfRendererContent = { Innertube.SongItem.from(it) },
    )?.getOrNull()?.items.orEmpty().filter { it.info?.endpoint?.videoId == song.id }
}

internal fun selectExactArtistEndpoint(
    videoId: String,
    name: String?,
    tracks: List<Innertube.SongItem>,
): NavigationEndpoint.Endpoint.Browse? = tracks.asSequence()
    .filter { it.info?.endpoint?.videoId == videoId }
    .flatMap { it.authors.orEmpty().asSequence() }
    .filterNot { isGenericReleaseChannel(it.name) }
    .filter { name.isNullOrBlank() || cleanPrefix(it.name.orEmpty()).trim().equals(cleanPrefix(name).trim(), true) }
    .mapNotNull { it.endpoint?.takeIf { endpoint -> endpoint.browseId?.let { id -> isArtistIdForSong(id, false) } == true } }
    .distinctBy { it.browseId }
    .singleOrNull()

internal fun isArtistIdForSong(id: String, local: Boolean): Boolean {
    if (local) return isDeviceArtistId(id)
    if (isDeviceArtistId(id)) return false
    if (id.startsWith("LOCAL_ARTIST_")) return true
    val onlineId = id.removePrefix(MODIFIED_PREFIX)
    return onlineArtistId.matches(onlineId)
}

private val onlineArtistId = Regex("UC[A-Za-z0-9_-]{10,}")

private fun NavigationEndpoint.Endpoint.Browse.toArtistRoute(): String =
    if (params.isNullOrBlank()) browseId.orEmpty() else "${browseId.orEmpty()}?params=$params"
