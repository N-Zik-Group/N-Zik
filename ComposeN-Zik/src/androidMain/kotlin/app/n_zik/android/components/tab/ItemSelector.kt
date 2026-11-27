package app.n_zik.android.components.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import app.n_zik.android.R
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.navigation.header.TabToolBar
import app.n_zik.android.ui.components.tab.toolbar.Clickable
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon

/** Selection state shared by the rows and the toolbar. Exiting selection mode clears the list. */
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
            remember { mutableStateOf(false) },
            // Existing click callbacks and recomposed rows read the same state.
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
    override val menuToggleState: Boolean get() = isActive
    override val messageId: Int
        get() = if ( isActive ) R.string.playlist_selection_action_deselect else R.string.common_action_select
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )
    override val size: Int
        get() = selected.size

    /** Keeps checked rows selected when their display data is refreshed. */
    fun <K> isSelected(item: E, key: (E) -> K): Boolean {
        val itemKey = key(item)
        return selected.any { key(it) == itemKey }
    }

    /** Toggles a row using the same identity as its collection actions. */
    fun <K> toggle(item: E, key: (E) -> K) {
        val itemKey = key(item)
        val updated = ArrayList(selected)
        if (!updated.removeAll { key(it) == itemKey }) updated.add(item)
        selectedState.value = updated
    }

    /** Uses the current collection in browsing mode and checks eligibility in selection mode. */
    fun actionTargets(items: List<E>): List<E> = actionTargets(items, { it }, { it })

    /** Matches refreshed rows by stable keys and lets actions keep their existing target order. */
    fun <T, K> actionTargets(
        items: List<T>,
        selectionKey: (E) -> K,
        itemKey: (T) -> K,
        selectionOrder: Boolean = false,
    ): List<T> = selectionActionTargets(isActive, selected, items, selectionKey, itemKey, selectionOrder)

    internal fun captureSelectionCompletion(): () -> Unit {
        val wasActive = isActive
        val openingSelection = selected
        return {
            if (wasActive && isActive && selected === openingSelection) isActive = false
        }
    }

    /** Guards default toolbar icons and their overflow entries with the same live predicate. */
    fun guard(action: MenuIcon, hasEligibleSelection: () -> Boolean): MenuIcon {
        val selectionAllowsAction = { !isActive || hasEligibleSelection() }
        return if (action is Clickable) {
            object : SelectionGuardedMenuIcon(action, selectionAllowsAction), Clickable {
                override fun onLongClick() {
                    if (isEnabled) action.onLongClick()
                }
            }
        } else {
            SelectionGuardedMenuIcon(action, selectionAllowsAction)
        }
    }

    @Composable
    override fun ToolBarButton() {
        TabToolBar.Icon(
            icon,
            color,
            TabToolBar.TOOLBAR_ICON_SIZE,
            true,
            Modifier,
            contentDescription = menuIconTitle,
            onClick = this::onShortClick,
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

private open class SelectionGuardedMenuIcon(
    private val action: MenuIcon,
    private val selectionAllowsAction: () -> Boolean,
) : MenuIcon {
    override val iconId: Int get() = action.iconId
    override val color: Color
        @Composable get() = action.color
    override val icon: Painter
        @Composable get() = action.icon
    override val menuIconTitle: String
        @Composable get() = action.menuIconTitle
    override val sizeDp: Dp get() = action.sizeDp
    override val modifier: Modifier get() = action.modifier
    override val menuToggleState: Boolean? get() = action.menuToggleState
    override val menuStateDescription: String?
        @Composable get() = action.menuStateDescription
    override val isEnabled: Boolean get() = action.isEnabled && selectionAllowsAction()
    override val menuLongClick: (() -> Unit)?
        get() = action.menuLongClick?.let { onLongClick -> { if (isEnabled) onLongClick() } }
    override val menuLongClickLabel: String?
        @Composable get() = action.menuLongClickLabel

    override fun onShortClick() {
        if (isEnabled) action.onShortClick()
    }
}

internal fun <S, T, K> selectionActionTargets(
    isActive: Boolean,
    selected: List<S>,
    items: List<T>,
    selectionKey: (S) -> K,
    itemKey: (T) -> K,
    selectionOrder: Boolean = false,
): List<T> {
    if (!isActive) return items
    if (selected.isEmpty()) return emptyList()
    val selectedKeys = selected.mapTo(LinkedHashSet(), selectionKey)
    val eligible = items.filter { itemKey(it) in selectedKeys }
    if (!selectionOrder) return eligible
    val eligibleByKey = eligible.groupBy(itemKey)
    return selectedKeys.flatMap { eligibleByKey[it].orEmpty() }
}

