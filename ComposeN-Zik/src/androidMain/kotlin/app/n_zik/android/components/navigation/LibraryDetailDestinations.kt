package app.n_zik.android.components.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument

import app.n_zik.android.components.musicbrainz.insights.AlbumInsightsScreen
import app.n_zik.android.components.musicbrainz.insights.ArtistInsightsScreen
import app.n_zik.android.components.ui.screens.album.AlbumScreen
import app.n_zik.android.components.ui.screens.artist.ArtistScreen
import app.n_zik.android.components.ui.screens.home.onDevice.OnDeviceAlbumScreen
import app.n_zik.android.components.ui.screens.home.onDevice.OnDeviceArtistScreen
import app.n_zik.android.core.navigation.RegisterDisplayedPlaylistAlias
import app.n_zik.android.core.ondevice.isDeviceAlbumId
import app.n_zik.android.core.ondevice.isDeviceArtistId
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.ui.screens.localplaylist.LocalPlaylistScreen
import app.n_zik.android.ui.screens.playlist.PlaylistScreen
import app.n_zik.android.ui.screens.podcast.PodcastScreen

@OptIn(
    ExperimentalAnimationApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalTextApi::class,
)
@UnstableApi
internal fun NavGraphBuilder.libraryDetailDestinations(
    navController: NavHostController,
    miniPlayer: @Composable () -> Unit,
) {
    chromeComposable(
        route = "${NavRoutes.artist.name}/{id}",
        arguments = listOf(
            navArgument(
                name = "id",
                builder = { type = NavType.StringType }
            )
        )
    ) { navBackStackEntry ->
        val requestedId = navBackStackEntry.arguments?.getString("id").orEmpty()
        val id = rememberLibraryBrowseDestination(requestedId, LibraryBrowseKind.Artist)?.browseId
            ?: return@chromeComposable
        key(id) {
            if (isDeviceArtistId(id)) {
                OnDeviceArtistScreen(navController, id, miniPlayer)
            } else {
                ArtistScreen(navController = navController, browseId = id, miniPlayer = miniPlayer)
            }
        }
    }

    chromeComposable(
        route = "${NavRoutes.album.name}/{id}",
        arguments = listOf(
            navArgument(
                name = "id",
                builder = { type = NavType.StringType }
            )
        )
    ) { navBackStackEntry ->
        val requestedId = navBackStackEntry.arguments?.getString("id").orEmpty()
        val id = rememberLibraryBrowseDestination(requestedId, LibraryBrowseKind.Album)?.browseId
            ?: return@chromeComposable
        key(id) {
            if (isDeviceAlbumId(id)) {
                OnDeviceAlbumScreen(navController, id, miniPlayer)
            } else {
                AlbumScreen(navController = navController, browseId = id, miniPlayer = miniPlayer)
            }
        }
    }

    chromeComposable(
        route = "artistInsights/{id}",
        arguments = listOf(
            navArgument(
                name = "id",
                builder = { type = NavType.StringType }
            )
        )
    ) { navBackStackEntry ->
        val requestedId = navBackStackEntry.arguments?.getString("id").orEmpty()
        val id = rememberLibraryBrowseDestination(requestedId, LibraryBrowseKind.Artist)?.browseId
            ?: return@chromeComposable
        ArtistInsightsScreen(
            navController = navController,
            artistId = id
        )
    }

    chromeComposable(
        route = "albumInsights/{id}?artistId={artistId}",
        arguments = listOf(
            navArgument(
                name = "id",
                builder = { type = NavType.StringType }
            ),
            navArgument("artistId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { navBackStackEntry ->
        val requestedId = navBackStackEntry.arguments?.getString("id").orEmpty()
        val id = rememberLibraryBrowseDestination(requestedId, LibraryBrowseKind.Album)?.browseId
            ?: return@chromeComposable
        AlbumInsightsScreen(
            navController = navController,
            albumId = id,
            albumArtistId = navBackStackEntry.arguments?.getString("artistId"),
        )
    }

    chromeComposable(
        route = "${NavRoutes.playlist.name}/{id}?params={params}&$COLLECTION_SOURCE_ONLY_ARGUMENT={sourceOnly}",
        arguments = listOf(
            navArgument(
                name = "id",
                builder = {
                    type = NavType.StringType
                }
            ),
            navArgument(
                name = "params",
                builder = {
                    type = NavType.StringType
                    defaultValue = ""
                }
            ),
            navArgument(COLLECTION_SOURCE_ONLY_ARGUMENT) {
                type = NavType.BoolType
                defaultValue = false
            }
        )
    ) { navBackStackEntry ->
        val requestedId = navBackStackEntry.arguments?.getString("id").orEmpty()
        val target = if (navBackStackEntry.arguments?.getBoolean(COLLECTION_SOURCE_ONLY_ARGUMENT) == true) {
            LibraryBrowseDestination(requestedId)
        } else {
            rememberLibraryBrowseDestination(requestedId, LibraryBrowseKind.Playlist) ?: return@chromeComposable
        }
        key(target) {
            if (target.localPlaylistId != null) {
                RegisterDisplayedPlaylistAlias(navBackStackEntry, target.localPlaylistId)
                LocalPlaylistScreen(navController, target.localPlaylistId, miniPlayer = miniPlayer)
            } else {
                PlaylistScreen(
                    navController = navController,
                    browseId = target.browseId,
                    params = navBackStackEntry.arguments?.getString("params").takeIf { target.browseId == requestedId },
                    miniPlayer = miniPlayer,
                )
            }
        }
    }

    chromeComposable(
        route = "${NavRoutes.podcast.name}/{id}?$COLLECTION_SOURCE_ONLY_ARGUMENT={sourceOnly}",
        arguments = listOf(
            navArgument(
                name = "id",
                builder = { type = NavType.StringType }
            ),
            navArgument(COLLECTION_SOURCE_ONLY_ARGUMENT) {
                type = NavType.BoolType
                defaultValue = false
            }
        )
    ) { navBackStackEntry ->
        val requestedId = navBackStackEntry.arguments?.getString("id").orEmpty()
        val target = if (navBackStackEntry.arguments?.getBoolean(COLLECTION_SOURCE_ONLY_ARGUMENT) == true) {
            LibraryBrowseDestination(requestedId)
        } else {
            rememberLibraryBrowseDestination(requestedId, LibraryBrowseKind.Podcast) ?: return@chromeComposable
        }
        key(target) {
            if (target.localPlaylistId != null) {
                RegisterDisplayedPlaylistAlias(navBackStackEntry, target.localPlaylistId)
                LocalPlaylistScreen(navController, target.localPlaylistId, miniPlayer = miniPlayer)
            } else {
                PodcastScreen(
                    navController = navController,
                    browseId = target.browseId,
                    params = null,
                    miniPlayer = miniPlayer,
                )
            }
        }
    }
}
