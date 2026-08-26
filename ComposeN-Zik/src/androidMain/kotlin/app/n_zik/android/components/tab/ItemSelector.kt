package app.n_zik.android.components.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar

/**
 * Behaves exactly like a [MutableList] but with extra functions on top.
 *
 * When [isActive] is set to `false`, all selected items will be cleared
 */
class ItemSelector<E> private constructor(
    private val menuState: MenuState,
    private val activeState: MutableState<Boolean>,
    private val selectedState: MutableState<ArrayList<E>>,
): AbstractMutableList<E>(), MenuIcon, Descriptive {

    companion object {
        @JvmStatic
        @Composable
        operator fun <T> invoke() = ItemSelector<T>(
            LocalMenuState.current,
            // Use remember to let list cleared when screen rotates
            remember { mutableStateOf(false) },
            // Use remember so selected items survive recomposition
            // This ensures toolbar buttons (even stale ones) read the same list
            // that SongItem checkboxes write to
            remember { mutableStateOf(ArrayList()) }
        )
    }

    private val selected: ArrayList<E>
        get() = selectedState.value

    var isActive: Boolean
        get() = activeState.value
        set(value) {
            activeState.value = value

            if( !value )
                selectedState.value = ArrayList()
        }

    override val iconId: Int
        get() = if( isActive ) R.drawable.checked_filled else R.drawable.unchecked_outline
    override val messageId: Int
        get() = if ( isActive ) R.string.item_deselect else R.string.item_select
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )
    override val size: Int
        // Use getter to make sure the value is up-to-date
        get() = selected.size

    @Composable
    override fun ToolBarButton() {
        TabToolBar.Icon(
            icon,
            color,
            TabToolBar.TOOLBAR_ICON_SIZE,
            true,
            androidx.compose.ui.Modifier,
            this::onShortClick
        )
    }

    override fun onShortClick() {
        menuState.hide()
        isActive = !isActive
    }

    override fun get(index: Int): E = selected[index]

    override fun removeAt(index: Int): E {
        val newList = ArrayList( selected )
        val removed = newList.removeAt( index )
        selectedState.value = newList
        return removed
    }

    override fun add(index: Int, element: E) {
        val newList = ArrayList( selected )
        newList.add( index, element )
        selectedState.value = newList
    }

    override fun set(index: Int, element: E): E {
        val newList = ArrayList( selected )
        val old = newList.set( index, element )
        selectedState.value = newList
        return old
    }
}

