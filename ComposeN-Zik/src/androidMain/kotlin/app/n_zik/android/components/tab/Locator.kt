package app.n_zik.android.components.tab

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.DynamicColor
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.utils.currentMediaItemIdAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@UnstableApi
class Locator private constructor(
    firstColorState: MutableState<Boolean>,
    private val binder: PlayerServiceModern.Binder?,
    private val menuState: MenuState,
    private val scrollableState: ScrollableState,
    private val findIndex: (String?) -> Int,
    private val coroutineScope: CoroutineScope,
    private val listItemOffset: Int,
): MenuIcon, DynamicColor, Descriptive {

    companion object {
        @Composable
        operator fun invoke(
            scrollableState: ScrollableState,
            getSongs: () -> List<Song>
        ): Locator = invoke(scrollableState, listItemOffset = 0, getSongs = getSongs)

        @Composable
        operator fun invoke(
            scrollableState: ScrollableState,
            listItemOffset: Int,
            getSongs: () -> List<Song>,
        ): Locator = create(scrollableState, listItemOffset) { mediaId ->
            getSongs().indexOfFirst { it.id == mediaId }
        }

        @Composable
        internal fun forList(scrollableState: LazyListState, findVisibleIndex: (String?) -> Int): Locator =
            create(scrollableState, 0, findVisibleIndex)

        @Composable
        internal fun forQueue(scrollableState: LazyListState, findVisibleIndex: () -> Int): Locator =
            forList(scrollableState) { findVisibleIndex() }

        @Composable
        private fun create(
            scrollableState: ScrollableState,
            listItemOffset: Int,
            findIndex: (String?) -> Int,
        ): Locator {
            val binder = LocalPlayerServiceBinder.current
            val player = binder?.player
            val mediaId = player?.currentMediaItemIdAsState(player)?.value
            val coroutineScope = rememberCoroutineScope()

            return Locator(
                firstColorState = remember(mediaId) {
                    mutableStateOf(mediaId != null)
                },
                binder = binder,
                menuState = LocalMenuState.current,
                scrollableState = scrollableState,
                findIndex = findIndex,
                coroutineScope = coroutineScope,
                listItemOffset = listItemOffset,
            )
        }
    }

    val position: Int
        get() = findIndex(binder?.player?.currentMediaItem?.mediaId)

    override val iconId: Int = R.drawable.locate
    override val messageId: Int = R.string.player_action_locate_current_song
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var isFirstColor: Boolean by firstColorState

    override fun onShortClick() {
        if( isFirstColor ) {
            val songIndex = position
            Timber.tag("locator").d("Visible item index: $songIndex")

            if( songIndex == -1 )
                Toaster.i( R.string.player_current_song_not_in_list )
            else {
                if (menuState.isDisplayed) menuState.hide()
                coroutineScope.launch {
                    when( scrollableState ) {
                        is LazyListState -> scrollableState.scrollToItem(songIndex + listItemOffset)
                        is LazyGridState -> scrollableState.scrollToItem(songIndex)
                    }
                }
            }
        } else
            Toaster.i( R.string.player_no_current_song )
    }
}
