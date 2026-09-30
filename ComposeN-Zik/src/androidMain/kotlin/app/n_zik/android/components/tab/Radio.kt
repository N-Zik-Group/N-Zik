package app.n_zik.android.components.tab

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.components.radioButtonIconRes
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.listentogether.guestLockedAlpha
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.colorPalette

@UnstableApi
class Radio private constructor(
    private val binder: PlayerServiceModern.Binder?,
    private val menuState: MenuState,
    private val songs: () -> List<Song>
): MenuIcon, Descriptive {

    companion object {
        @Composable
        operator fun invoke( songs: () -> List<Song> ): Radio =
            Radio(
                LocalPlayerServiceBinder.current,
                LocalMenuState.current,
                songs
            )
    }

    // Issue #866 (gh-866): state icon — radio_stop while the radio is active (the state
    // change is the press feedback).
    override val iconId: Int get() = radioButtonIconRes(binder)
    // Guest lock (spec-listen-together-guest-lock-hardening): the radio is a host-owned stateful
    // control — a locked guest sees this menu item grayed (the tap is answered by the shared
    // blocked toast via the central startRadio() guard), consistent with the other locked sites
    // (MiniPlayer, ActionBar, PlaylistSongList).
    override val modifier: Modifier
        get() = Modifier.alpha(guestLockedAlpha(listenTogetherGuestLock.value))
    override val color: androidx.compose.ui.graphics.Color
        @Composable
        get() = if (binder?.isRadioActive == true) colorPalette().accent else colorPalette().text
    override val messageId: Int = R.string.start_radio
    override val menuIconTitle: String
        @Composable
        get() = stringResource(binder?.radioActionTextRes ?: messageId)

    override fun onShortClick() {
        binder?.startRadio( songs().random(), false, null, true )

        menuState.hide()
    }
}

