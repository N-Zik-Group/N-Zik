package app.n_zik.android.components.tab

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.components.shuffleButtonIconRes
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.utils.Shuffler
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState

@UnstableApi
class SongShuffler private constructor(
    private val binder: PlayerServiceModern.Binder?,
    private val menuState: MenuState?,
    private val onShuffle: (PlayerServiceModern.Binder) -> Unit
): MenuIcon, Descriptive {

    companion object {
        @Composable
        operator fun invoke( songs: () -> List<Song> ) =
            SongShuffler(LocalPlayerServiceBinder.current, LocalMenuState.current) { binder ->
                playShuffled(binder, songs())
                binder.triggerShuffleOkFlash()
            }

        @Composable
        operator fun invoke(
            databaseCall: (Int) -> Flow<List<Song>>,
            vararg key: Any?
        ): SongShuffler {
            val songsToShuffle by remember( key ) {
                databaseCall( Int.MAX_VALUE )
            }.collectAsStateWithLifecycle( initialValue = emptyList(), context = NzikDispatchers.DATA )

            return invoke { songsToShuffle }
        }

        @Composable
        internal fun deferred(capture: () -> (suspend () -> List<Song>?)?): SongShuffler {
            val scope = rememberCoroutineScope()
            return SongShuffler(LocalPlayerServiceBinder.current, LocalMenuState.current) { binder ->
                val load = capture()
                if (load != null) scope.launch {
                    val songs = load() ?: return@launch
                    playShuffled(binder, songs)
                    binder.triggerShuffleOkFlash()
                }
            }
        }

        fun playShuffled(
            binder: PlayerServiceModern.Binder,
            songs: List<Song>
        ) {
            if( songs.isEmpty() ) {
                Toaster.i( R.string.player_shuffle_empty_message )
                return
            }
            Shuffler.play( binder, songs )
        }
    }

    // Issue #866 (gh-866): app-wide shuffle confirmation flash — the icon follows the binder's
    // flash state (shuffle_ok ~1 s), triggered in onShortClick below.
    override val iconId: Int get() = shuffleButtonIconRes( binder )
    override val messageId: Int = R.string.player_action_shuffle_hint
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.player_action_shuffle )

    override fun onShortClick() {
        onShuffle(binder ?: return)
        menuState?.hide()
    }
}
