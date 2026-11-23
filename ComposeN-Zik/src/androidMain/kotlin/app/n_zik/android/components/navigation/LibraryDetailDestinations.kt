package app.n_zik.android.components.navigation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
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
import app.n_zik.android.core.ondevice.isDeviceAlbumId
import app.n_zik.android.core.ondevice.isDeviceArtistId
import app.n_zik.android.enums.NavRoutes
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
        val id = navBackStackEntry.arguments?.getString("id") ?: ""
        if (isDeviceArtistId(id)) {
            OnDeviceArtistScreen(navController, id, miniPlayer)
        } else {
            ArtistScreen(navController = navController, browseId = id, miniPlayer = miniPlayer)
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
        val id = navBackStackEntry.arguments?.getString("id") ?: ""
        if (isDeviceAlbumId(id)) {
            OnDeviceAlbumScreen(navController, id, miniPlayer)
        } else {
            AlbumScreen(navController = navController, browseId = id, miniPlayer = miniPlayer)
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
        val id = navBackStackEntry.arguments?.getString("id") ?: ""
        ArtistInsightsScreen(
            navController = navController,
            artistId = id
        )
    }

    chromeComposable(
        route = "albumInsights/{id}",
        arguments = listOf(
            navArgument(
                name = "id",
                builder = { type = NavType.StringType }
            )
        )
    ) { navBackStackEntry ->
        val id = navBackStackEntry.arguments?.getString("id") ?: ""
        AlbumInsightsScreen(
            navController = navController,
            albumId = id
        )
    }

    chromeComposable(
        route = "${NavRoutes.playlist.name}/{id}?params={params}",
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
            )
        )
    ) { navBackStackEntry ->
        val id = navBackStackEntry.arguments?.getString("id") ?: ""
        val params = navBackStackEntry.arguments?.getString( "params" )
        PlaylistScreen(
            navController = navController,
            browseId = id,
            params = params,
            miniPlayer = miniPlayer,
        )
    }

    chromeComposable(
        route = "${NavRoutes.podcast.name}/{id}",
        arguments = listOf(
            navArgument(
                name = "id",
                builder = { type = NavType.StringType }
            )
        )
    ) { navBackStackEntry ->
        val id = navBackStackEntry.arguments?.getString("id") ?: ""
        PodcastScreen(
            navController = navController,
            browseId = id,
            params = null,
            miniPlayer = miniPlayer,
        )
    }
}
