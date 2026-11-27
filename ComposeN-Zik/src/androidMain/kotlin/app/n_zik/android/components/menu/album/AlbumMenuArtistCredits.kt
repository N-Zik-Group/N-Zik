package app.n_zik.android.components.menu.album

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.navigation.NavController
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.R
import app.n_zik.android.cleanPrefix
import app.n_zik.android.components.menu.song.unlinkedTrackMenuArtistNames
import app.n_zik.android.components.menu.isCurrentMenu
import app.n_zik.android.components.menu.navigateToMenuEntity
import app.n_zik.android.components.song.artistMenuIdentity
import app.n_zik.android.components.song.isEditedArtistId
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.ondevice.isDeviceArtistId
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.models.Artist
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.splitArtistNames
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>>?.toAlbumCreditArtists(): List<Artist> =
    orEmpty().mapNotNull { author ->
        val endpoint = author.endpoint ?: return@mapNotNull null
        val browseId = endpoint.browseId?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val name = author.name?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val pageType = endpoint.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
        val isArtistCredit = pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
            (pageType == null || pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL") && browseId.startsWith("UC")
        if (!isArtistCredit) {
            return@mapNotNull null
        }
        Artist(browseId, name)
    }.distinctBy(Artist::id)

internal fun selectAlbumMenuArtists(
    storedCredits: String?,
    headerArtists: List<Artist>,
    knownArtists: List<Artist>,
    previousId: (String) -> String?,
): List<Artist> {
    val onlineArtists = knownArtists.filterNot { isDeviceArtistId(it.id) }
    val hasEditedCredits = storedCredits?.startsWith(MODIFIED_PREFIX, ignoreCase = true) == true
    if (!hasEditedCredits && headerArtists.isNotEmpty()) {
        return headerArtists.map { artist ->
            val manualArtist = onlineArtists.singleOrNull { candidate ->
                isEditedArtistId(candidate.id) &&
                    artistMenuIdentity(candidate.id, previousId) == artistMenuIdentity(artist.id, previousId)
            }
            manualArtist ?: onlineArtists.firstOrNull { it.id == artist.id } ?: artist
        }.distinctBy { artistMenuIdentity(it.id, previousId) }
    }

    return cleanPrefix(storedCredits.orEmpty()).splitArtistNames().mapNotNull { name ->
        val matches = onlineArtists.filter { cleanPrefix(it.name.orEmpty()).trim().equals(name, ignoreCase = true) }
        matches.singleOrNull { isEditedArtistId(it.id) } ?: matches.singleOrNull()
    }.distinctBy { artistMenuIdentity(it.id, previousId) }
}

internal fun albumMenuArtistFallbackNames(
    storedCredits: String?,
    headerArtists: List<Artist>,
    linkedArtists: List<Artist>,
): List<String> {
    val sourceCredits = headerArtists.takeUnless {
        storedCredits?.startsWith(MODIFIED_PREFIX, ignoreCase = true) == true
    }.orEmpty()
    val coveredNames = (linkedArtists + sourceCredits).flatMap { artist ->
        cleanPrefix(artist.name.orEmpty()).splitArtistNames()
    }
    return unlinkedTrackMenuArtistNames(storedCredits.splitArtistNames(), coveredNames).map(::cleanPrefix)
}

@Composable
internal fun rememberAlbumMenuArtists(
    storedCredits: String?,
    headerArtists: List<Artist>?,
    preferences: SharedPreferences,
): State<List<Artist>> = key(storedCredits, headerArtists, preferences) {
    produceState(selectAlbumMenuArtists(storedCredits, headerArtists.orEmpty(), emptyList()) { null }) {
        if (headerArtists == null && storedCredits?.startsWith(MODIFIED_PREFIX, ignoreCase = true) != true) return@produceState
        value = withContext(NzikDispatchers.DATA) {
            resolveAlbumCreditArtists(storedCredits, headerArtists.orEmpty(), preferences)
        }
    }
}

internal suspend fun resolveAlbumCreditArtists(
    storedCredits: String?,
    headerArtists: List<Artist>,
    preferences: SharedPreferences,
): List<Artist> {
    val names = (cleanPrefix(storedCredits.orEmpty()).splitArtistNames() +
        headerArtists.mapNotNull(Artist::name)).distinct()
    val knownArtists = buildList {
        headerArtists.forEach { artist ->
            Database.artistTable.findByIdDirect(artist.id)?.let { add(it) }
        }
        names.forEach { name ->
            addAll(Database.artistTable.allByNameIgnoreCase(name))
            addAll(Database.artistTable.allByNameIgnoreCase("$MODIFIED_PREFIX$name"))
        }
    }.distinctBy(Artist::id)
    return selectAlbumMenuArtists(storedCredits, headerArtists, knownArtists) { id ->
        preferences.getString("old_browse_id_$id", null)
    }
}

/** Recheck manually replaced browse IDs before following an immediately available credit. */
internal fun navigateToAlbumMenuArtist(
    artist: Artist,
    storedCredits: String?,
    headerArtists: List<Artist>,
    preferences: SharedPreferences,
    menuState: MenuState,
    navController: NavController,
    scope: CoroutineScope,
) {
    val menuKey = menuState.transitionKey
    scope.launch {
        val resolved = withContext(NzikDispatchers.DATA) {
            val artists = resolveAlbumCreditArtists(storedCredits, headerArtists, preferences)
            selectRefreshedAlbumMenuArtist(artist.id, artists) { id ->
                preferences.getString("old_browse_id_$id", null)
            }
        }
        if (!menuState.isCurrentMenu(menuKey)) return@launch
        if (resolved == null) {
            Toaster.e(R.string.artist_lookup_failed)
        } else {
            navigateToMenuEntity(navController, menuState, NavRoutes.artist, resolved.id)
        }
    }
}

internal fun selectRefreshedAlbumMenuArtist(
    selectedId: String,
    currentArtists: List<Artist>,
    previousId: (String) -> String?,
): Artist? = currentArtists.singleOrNull { it.id == selectedId }
    ?: currentArtists.singleOrNull {
        artistMenuIdentity(it.id, previousId) == artistMenuIdentity(selectedId, previousId)
    }
