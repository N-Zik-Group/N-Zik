package app.n_zik.android.components.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.listentogether.ListenTogetherMenu
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.listentogether.ListenTogetherManager
import app.n_zik.android.listentogether.RoomRole
import app.n_zik.android.listentogether.TrackInfo
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.utils.durationTextToMillis
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Builds the room actions for an online track from the current room membership. */
@Composable
internal fun rememberListenTogetherTrackMenuActions(song: Song, menuState: MenuState): List<MenuIcon> {
    val offline by OfflineMode.rememberEnabled()
    if (song.isLocal || offline) return emptyList()

    val roomAction = remember(menuState) {
        object : MenuIcon, Descriptive {
            override val iconId: Int = R.drawable.people
            override val messageId: Int = R.string.listen_together_title
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() = menuState.display { ListenTogetherMenu() }
        }
    }
    val manager = ListenTogetherManager.getInstance() ?: return listOf(roomAction)
    val canSuggestFlow = remember(manager) {
        combine(manager.roomState, manager.role) { room, role ->
            room != null && role == RoomRole.GUEST
        }.distinctUntilChanged()
    }
    val canSuggest by canSuggestFlow.collectAsStateWithLifecycle(
        initialValue = manager.isInRoom && manager.role.value == RoomRole.GUEST,
    )
    if (!canSuggest) return listOf(roomAction)

    val context = LocalContext.current
    val suggestAction = remember(context, song, menuState) {
        object : MenuIcon, Descriptive {
            override val iconId: Int = R.drawable.musical_notes
            override val messageId: Int = R.string.listen_together_suggest
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)

            override fun onShortClick() {
                val currentManager = ListenTogetherManager.getInstance() ?: return
                if (OfflineMode.enabled(context) || !currentManager.isInRoom ||
                    currentManager.role.value != RoomRole.GUEST
                ) return
                val duration = song.durationText?.let(::durationTextToMillis)?.takeIf { it > 0 } ?: 180_000L
                currentManager.suggestTrack(
                    TrackInfo(
                        id = song.id,
                        title = song.cleanTitle(),
                        artist = song.cleanArtistsText(),
                        duration = duration,
                        thumbnail = song.thumbnailUrl.orEmpty(),
                    ),
                )
                menuState.hide()
            }
        }
    }
    return listOf(roomAction, suggestAction)
}
