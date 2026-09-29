package app.kreate.android.themed.rimusic.component.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.DualIcon
import app.it.fast4x.rimusic.ui.components.tab.toolbar.DynamicColor
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.kreate.android.me.knighthat.utils.Toaster

class PositionLock(
    colorState: MutableState<Boolean>
): MenuIcon, DualIcon, DynamicColor, Descriptive {

    constructor( sortOrder: SortOrder ): this(mutableStateOf( sortOrder == SortOrder.Ascending ))
    constructor(): this(mutableStateOf( true ))

    override val secondIconId: Int = R.drawable.unlocked
    override val iconId: Int = R.drawable.locked
    override val messageId: Int = R.string.info_lock_unlock_reorder_songs
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    // This is inverted, first icon is locked icon
    override var isFirstIcon: Boolean by mutableStateOf( true )
    override var isFirstColor: Boolean by colorState

    // Guest lock (spec-listen-together-guest-lock-hardening): the reorder lock ("cadena") is a
    // host-owned stateful control — a locked guest sees it grayed (disabled) and can never toggle
    // it. The queue keeps it forced-on while locked (see Queue.kt).
    override val isEnabled: Boolean
        get() = !listenTogetherGuestLock.value

    fun isLocked(): Boolean = isFirstIcon

    override fun onShortClick() {
        // Guest lock: the reorder lock cannot be toggled by a guest (defense in depth — the
        // toolbar button is already disabled via [isEnabled]).
        if( listenTogetherGuestLock.value ) return
        if( !isFirstColor )
            Toaster.e( R.string.info_reorder_is_possible_only_in_ascending_sort )
        else
            isFirstIcon = !isFirstIcon
    }
}
