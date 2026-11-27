package app.n_zik.android.components.tab


import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.core.sync.YouTubeSync
import app.n_zik.android.core.sync.FavoriteAction
import app.n_zik.android.models.Song
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon

@OptIn(UnstableApi::class)
class LikeComponent private constructor(
    private val menuState: MenuState,
    private val isVisible: () -> Boolean = { true },
    private val getMediaItems: () -> List<MediaItem>
): MenuIcon, Descriptive{

    companion object {
        @Composable
        operator fun invoke( getSongs: () -> List<Song> ) =
            LikeComponent(LocalMenuState.current) { getSongs().map { it.asMediaItem } }

        @Composable
        fun forMediaItem(isVisible: () -> Boolean, getMediaItem: () -> MediaItem) =
            LikeComponent(LocalMenuState.current, isVisible) { listOf(getMediaItem()) }
    }

    override val iconId: Int = R.drawable.heart
    override val messageId: Int = R.string.library_favorites_action_add
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    internal val isMenuVisible: Boolean get() = isVisible()

    @Composable
    override fun ListMenuItem() {
        if (isMenuVisible) super.ListMenuItem()
    }

    @Composable
    override fun GridMenuItem() {
        if (isMenuVisible) super.GridMenuItem()
    }

    override fun onShortClick() {
        val items = getMediaItems().distinctBy(MediaItem::mediaId)
        if (items.isEmpty()) return
        YouTubeSync.launchFavoriteUpdate(appContext(), items, FavoriteAction.Add)
        menuState.hide()
    }
}

/** The header already adds neutral songs; only the disliked state needs a one-tap shortcut. */
internal fun showAddToFavoritesShortcut(likedAt: Long?, dislikesEnabled: Boolean): Boolean =
    dislikesEnabled && likedAt == -1L

