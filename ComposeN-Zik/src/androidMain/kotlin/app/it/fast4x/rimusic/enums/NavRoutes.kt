package app.it.fast4x.rimusic.enums

import androidx.annotation.AnyThread
import androidx.navigation.NavController
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch

enum class NavRoutes {
    videoOrSongInfo,
    home,
    album,
    artist,
    games,
    gamePacman,
    gameSnake,
    history,
    localPlaylist,
    mood,
    playlist,
    queue,
    search,
    searchResults,
    settings,
    statistics,
    rewind,
    rewindHome,
    newAlbums,
    moodsPage,
    podcast,
    artistAlbums,
    artistVideos,
    artistPlaylists,
    listenTogether,
    updater;

    companion object {

        fun current( navController: NavController ) = navController.currentBackStackEntry?.destination?.route
    }

    fun isHere( navController: NavController ) = current( navController )?.startsWith( this.name ) ?: false

    fun isNotHere( navController: NavController ) = !isHere( navController )

    /**
     * Launch a non-blocking task and take user to currently selected route.
     *
     * **NOTE:** This function ensures [NavController.navigate] is run on
     * main thread, so you can safely call it from other threads
     */
    @AnyThread
    fun navigateHere( navController: NavController, path: String = "" ) {
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            if( path.isBlank() )
                navController.navigate( name )
            else
                navController.navigate( "$name/$path" )
        }
    }
}



