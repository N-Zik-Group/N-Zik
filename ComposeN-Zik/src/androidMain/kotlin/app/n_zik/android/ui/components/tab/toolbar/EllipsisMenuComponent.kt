package app.n_zik.android.ui.components.tab.toolbar

import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.rememberPreference

class EllipsisMenuComponent private constructor(
    private val buttons: () -> List<Button>,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>
) : Menu, Icon {

    companion object {
        @JvmStatic
        @Composable
        fun init( items: () -> List<Button> ) = EllipsisMenuComponent(
            items,
            LocalMenuState.current,
            rememberPreference( menuStyleKey, MenuStyle.List )
        )
    }

    override val iconId: Int = R.drawable.ellipsis_horizontal

    override var menuStyle: MenuStyle by styleState

    override fun onShortClick() = openMenu()

    @Composable
    override fun ListMenu() {
        ListMenu.Menu(title = "") {
            buttons().forEach {
                if( it is MenuIcon)
                    it.ListMenuItem()
            }
        }
    }

    @Composable
    override fun GridMenu() {
        GridMenu.Menu(title = "") {
            items( buttons(), Button::hashCode ) {
                if( it is MenuIcon)
                    it.GridMenuItem()
            }
        }
    }

    @Composable
    override fun MenuComponent() {
        if( menuStyle == MenuStyle.Grid )
            GridMenu()
        else
            ListMenu()
    }
}


