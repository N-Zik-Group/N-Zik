package app.n_zik.android.components.tab

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

@UnstableApi
class Radio private constructor(
    binder: PlayerServiceModern.Binder?,
    menuState: MenuState,
    songs: () -> List<Song>
): StatefulMenuIcon(
    menuState = menuState,
    messageId = R.string.start_radio,
    readActive = { binder?.isRadioActive == true },
    readIcon = { radioButtonIconRes(binder) },
    readTitle = { binder?.radioActionTextRes ?: R.string.start_radio },
    performAction = { binder?.startRadio(songs().random(), false, null, true) },
    closeMenuAfterAction = true,
) {

    companion object {
        @Composable
        operator fun invoke( songs: () -> List<Song> ): Radio =
            Radio(
                LocalPlayerServiceBinder.current,
                LocalMenuState.current,
                songs
            )
    }

    // Guest lock (spec-listen-together-guest-lock-hardening): the radio is a host-owned stateful
    // control — a locked guest sees this menu item grayed (the tap is answered by the shared
//
    // blocked toast via the central startRadio() guard), consistent with the other locked sites
    // (MiniPlayer, ActionBar, PlaylistSongList).
    override val modifier: Modifier
        get() = Modifier.alpha(guestLockedAlpha(listenTogetherGuestLock.value))
}

