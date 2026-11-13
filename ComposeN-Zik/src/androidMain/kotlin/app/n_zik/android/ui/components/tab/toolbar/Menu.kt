package app.n_zik.android.ui.components.tab.toolbar

import androidx.compose.runtime.Composable
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.ui.components.MenuState

interface Menu {

    val menuState: MenuState

    var menuStyle:MenuStyle

    @Composable
    fun ListMenu()

    @Composable
    fun GridMenu()

    @Composable
    fun MenuComponent()

    fun openMenu() = menuState.display { MenuComponent() }

    fun closeMenu() = menuState.hide()
}


