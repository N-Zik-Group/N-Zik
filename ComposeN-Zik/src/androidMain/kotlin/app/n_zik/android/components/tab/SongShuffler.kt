package app.n_zik.android.components.tab

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.utils.Shuffler
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.kreate.android.me.knighthat.utils.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState

@UnstableApi
class SongShuffler private constructor(
    private val binder: PlayerServiceModern.Binder?,
    private val menuState: MenuState?,
    private val songs: () -> List<Song>
): MenuIcon, Descriptive {

    companion object {
        @Composable
        operator fun invoke( songs: () -> List<Song> ) =
            SongShuffler( LocalPlayerServiceBinder.current, LocalMenuState.current, songs )

        @Composable
        operator fun invoke(
            databaseCall: (Int) -> Flow<List<Song>>,
            vararg key: Any?
        ): SongShuffler {
            val songsToShuffle by remember( key ) {
                databaseCall( Int.MAX_VALUE )
            }.collectAsStateWithLifecycle( initialValue = emptyList(), context = Dispatchers.IO )

            return SongShuffler( LocalPlayerServiceBinder.current, LocalMenuState.current ) { songsToShuffle }
        }

        fun playShuffled(
            binder: PlayerServiceModern.Binder,
            songs: List<Song>
        ) {
            if( songs.isEmpty() ) {
                Toaster.i( R.string.no_song_to_shuffle )
                return
            }
            Shuffler.play( binder, songs )
        }
    }

    override val iconId: Int = R.drawable.shuffle
    override val messageId: Int = R.string.info_shuffle
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.shuffle )

    override fun onShortClick() {
        playShuffled(
            this.binder ?: return,
            this.songs()
        )
        menuState?.hide()
    }
}
