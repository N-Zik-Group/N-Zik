package app.n_zik.android.components.tab


import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.models.Song
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.core.utils.Toaster

class LikeComponent private constructor(
    private val menuState: MenuState,
    private val getSongs: () -> List<Song>
): MenuIcon, Descriptive{

    companion object {
        @Composable
        operator fun invoke( getSongs: () -> List<Song> ) =
            LikeComponent(LocalMenuState.current, getSongs)
    }

    override val iconId: Int = R.drawable.heart
    override val messageId: Int = R.string.library_favorites_action_add
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        val songs = getSongs().toList()
        Database.asyncTransaction {
            songs.forEach { song ->
                if (!songTable.isLikedDirect(song.id) && songTable.likeState(song.id, true) > 0) {
                    MyDownloadHelper.downloadNewlyLiked(appContext(), song.asMediaItem)
                }
            }

            Toaster.done()
        }

        // Close the menu while the queued database write runs.
        menuState.hide()
    }
}

