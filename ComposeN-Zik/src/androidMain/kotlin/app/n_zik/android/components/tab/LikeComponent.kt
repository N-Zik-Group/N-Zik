package app.n_zik.android.components.tab

import app.n_zik.android.core.database.*

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.models.Song
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
        Database.asyncTransaction {
            getSongs().forEach {
                songTable.likeState( it.id, true )
            }

            Toaster.done()
        }

        /*
        This part is explicitly placed outside
        [Database.asyncTransaction] to ensure
        user can continue using the app while songs
        are getting modified
        */
        menuState.hide()
    }
}

